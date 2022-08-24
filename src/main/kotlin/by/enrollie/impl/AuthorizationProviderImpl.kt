/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 8/12/22, 3:18 AM
 */

package by.enrollie.impl

import by.enrollie.annotations.UnsafeAPI
import by.enrollie.data_classes.*
import by.enrollie.providers.AuthorizationInterface
import com.osohq.oso.Oso
import java.time.LocalDate
import java.time.LocalDateTime

class AuthorizationProviderImpl : AuthorizationInterface {
    class School
    class LessonsProvider {
        @Suppress("unused")
        fun getTodayLessons(user: User): List<Lesson> =
            ProvidersCatalog.databaseProvider.lessonsProvider.getLessonsForTeacher(user.id, LocalDate.now())
    }

    class TimeValidator {
        @Suppress("unused")
        fun isCurrentLesson(lesson: Lesson): Boolean {
            val schoolClass = ProvidersCatalog.databaseProvider.classesProvider.getClass(lesson.classID) ?: return false
            val currentLessonPlace =
                ProvidersCatalog.databaseProvider.timetablePlacingProvider.getTimetablePlaces().getPlace(
                    LocalDateTime.now()
                ).let {
                    if (schoolClass.shift == TeachingShift.FIRST) it.first else it.second
                }
            return lesson.placeInTimetable == currentLessonPlace
        }
    }

    class RolesProvider {
        @Suppress("unused")
        fun roles(user: User) = ProvidersCatalog.databaseProvider.rolesProvider.getRolesForUser(user.id)

        @OptIn(UnsafeAPI::class)
        fun rolesInClass(user: User, schoolClass: SchoolClass) =
            ProvidersCatalog.databaseProvider.rolesProvider.getRolesForUser(user.id).filter {
                if (it.role is Roles.CLASS.ClassTeacher || it.role is Roles.CLASS.AbsenceProvider || it.role is Roles.CLASS.Student) {
                    (it.unsafeGetField(Roles.CLASS.STUDENT.classID) == schoolClass.id || it.unsafeGetField(Roles.CLASS.CLASS_TEACHER.classID) == schoolClass.id || it.unsafeGetField(
                        Roles.CLASS.ABSENCE_PROVIDER.classID
                    ) == schoolClass.id) && (it.roleRevokedDateTime == null || it.roleRevokedDateTime!!.isAfter(
                        LocalDateTime.now()
                    ))
                } else {
                    false
                }
            }
    }

    private val oso: Oso = Oso()

    init {
        oso.registerClass(User::class.java, "User")
        oso.registerClass(School::class.java, "School")
        oso.registerConstant(School(), "School")
        oso.registerClass(SchoolClass::class.java, "SchoolClass")
        oso.registerClass(Lesson::class.java, "Lesson")
        oso.registerClass(Unit::class.java, "Unit")
        oso.registerConstant(LessonsProvider(), "LessonsProvider")
        oso.registerConstant(TimeValidator(), "TimeValidator")
        oso.registerConstant(RolesProvider(), "RolesProvider")
        oso.loadStr(
            (Unit::class as Any).javaClass.classLoader.getResourceAsStream("rules.polar")!!.readBytes().decodeToString()
        )
    }

    override fun authorize(actor: Any, action: String, resource: Any) {
        oso.authorize(actor, action, resource)
    }

    override fun <T> filterAllowed(actor: Any, action: String, resources: List<T>): List<T> = resources.filter {
        oso.isAllowed(actor, action, it)
    }
}
