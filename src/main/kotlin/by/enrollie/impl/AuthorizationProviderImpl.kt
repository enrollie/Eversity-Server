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
import by.enrollie.extensions.filterValid
import by.enrollie.providers.AuthorizationInterface
import by.enrollie.providers.DatabaseProviderInterface
import com.osohq.oso.Oso
import java.time.LocalDate
import java.time.LocalDateTime

class AuthorizationProviderImpl(private val database: DatabaseProviderInterface) : AuthorizationInterface {
    class School internal constructor()

    companion object{
        val school = School()
    }

    class LessonsProvider(private val database: DatabaseProviderInterface) {
        @Suppress("unused")
        fun getTodayLessons(user: User): List<Lesson> =
            database.lessonsProvider.getLessonsForTeacher(user.id, LocalDate.now())
    }

    class TimeValidator(private val database: DatabaseProviderInterface) {
        @Suppress("unused")
        fun isCurrentLesson(lesson: Lesson): Boolean {
            val schoolClass = database.classesProvider.getClass(lesson.classID) ?: return false
            val currentLessonPlace =
                database.timetablePlacingProvider.getTimetablePlaces().getPlace(
                    LocalDateTime.now()
                ).let {
                    if (schoolClass.shift == TeachingShift.FIRST) it.first else it.second
                }
            return lesson.placeInTimetable == currentLessonPlace
        }
    }

    class RolesProvider(private val database: DatabaseProviderInterface) {
        @Suppress("unused")
        fun roles(user: User) = database.rolesProvider.getRolesForUser(user.id).filterValid() // We don't need to let people access sensitive data after they've lost a role

        @OptIn(UnsafeAPI::class)
        @Suppress("unused") // Used in Oso
        fun rolesInClass(user: User, schoolClass: SchoolClass) =
            database.rolesProvider.getRolesForUser(user.id).filter {
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
        oso.registerConstant(school, "School")
        oso.registerClass(SchoolClass::class.java, "SchoolClass")
        oso.registerClass(Lesson::class.java, "Lesson")
        oso.registerClass(Unit::class.java, "Unit")
        oso.registerConstant(LessonsProvider(database), "LessonsProvider")
        oso.registerConstant(TimeValidator(database), "TimeValidator")
        oso.registerConstant(RolesProvider(database), "RolesProvider")
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
