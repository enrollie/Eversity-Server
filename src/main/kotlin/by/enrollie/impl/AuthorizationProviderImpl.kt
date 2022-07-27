/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/28/22, 12:01 AM
 */

package by.enrollie.impl

import by.enrollie.data_classes.Lesson
import by.enrollie.data_classes.SchoolClass
import by.enrollie.data_classes.TeachingShift
import by.enrollie.data_classes.User
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
                ProvidersCatalog.databaseProvider.timetablePlacingProvider.getTimetablePlaces().getCurrentPlace(
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
    }

    private val oso: Oso = Oso()

    init {
        oso.registerClass(User::class.java, "User")
        oso.registerClass(School::class.java, "School")
        oso.registerClass(SchoolClass::class.java, "Class")
        oso.registerClass(Lesson::class.java, "Lesson")
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
}
