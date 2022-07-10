/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/10/22, 11:18 PM
 */

package by.enrollie

import by.enrollie.data_classes.*
import com.osohq.oso.Exceptions.ForbiddenException
import com.osohq.oso.Oso
import org.joda.time.DateTime
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertFailsWith

class AuthenticationTest {

    @Test
    @Suppress("UNUSED", "UNUSED_VARIABLE", "UNUSED_PARAMETER")
    fun osoTest() {
        class RolesProvider {
            fun roles(user: User): List<RoleData<*>> {
                if (user.id == 0) return listOf(
                    RoleData(
                        0,
                        Roles.CLASS.CLASS_TEACHER,
                        mapOf(Roles.CLASS.CLASS_TEACHER.classID to 1),
                        DateTime.now(),
                        null
                    ), RoleData(0, Roles.SCHOOL.PRINCIPAL, mapOf(), DateTime.now(), null)
                )
                if (user.id == 1) return listOf(
                    RoleData(1, Roles.CLASS.STUDENT, mapOf(Roles.CLASS.STUDENT.classID to 1), DateTime.now(), null),
                    RoleData(
                        1,
                        Roles.CLASS.ABSENCE_PROVIDER,
                        mapOf(Roles.CLASS.ABSENCE_PROVIDER.classID to 1, Roles.CLASS.ABSENCE_PROVIDER.delegatedBy to 0),
                        DateTime.now(),
                        null
                    )
                )
                if (user.id == 2) return listOf(
                    RoleData(
                        2, Roles.CLASS.STUDENT, mapOf(Roles.CLASS.STUDENT.classID to 1), DateTime.now(), null
                    )
                )
                return emptyList()
            }

            fun rolesInClass(user: User, schoolClass: SchoolClass): List<RoleData<*>> = roles(user)

            fun lessonRoles(user: User): List<Lesson> {
                if (user.id == 0) {
                    return listOf(
                        Lesson(1, "Белорусский язык", LocalDate.now(), 1, TeachingShift.FIRST, 1, 1, null),
                        Lesson(1, "Белорусский язык", LocalDate.now(), 2, TeachingShift.FIRST, 2, 1, null)
                    )
                }
                if (user.id == 1) {
                    return listOf(
                        Lesson(1, "Белорусский язык", LocalDate.now(), 1, TeachingShift.FIRST, 1, 1, null)
                    )
                }
                if (user.id == 2) {
                    return listOf(
                        Lesson(1, "Белорусский язык", LocalDate.now(), 1, TeachingShift.SECOND, 3, 1, null)
                    )
                }
                return emptyList()
            }
        }

        class TimeValidator {
            fun isCurrentLesson(lesson: Lesson, clazz: SchoolClass): Boolean = true
        }

        class School

        val oso = Oso()
        oso.registerClass(User::class.java, "User")
        oso.registerClass(School::class.java, "School")
        oso.registerConstant(School(), "school")
        oso.registerClass(Lesson::class.java, "Lesson")
        oso.registerClass(SchoolClass::class.java, "Class")
        oso.registerConstant(RolesProvider(), "RolesProvider")
        oso.registerConstant(TimeValidator(), "TimeValidator")
        oso.loadFiles(arrayOf("src/main/resources/rules.polar"))

        oso.authorize(
            User(0, Name("Павел", "Андреевич", "Матусевич")),
            "read_absence",
            SchoolClass(1, "Class", TeachingShift.FIRST)
        )
        oso.authorize(
            User(1, Name("Павел", "Андреевич", "Матусевич")),
            "read_absence",
            SchoolClass(1, "Class", TeachingShift.FIRST)
        )
        println(oso.getAllowedActions(User(0, Name("Павел", "Андреевич", "Матусевич")), School()))
        assertFailsWith(ForbiddenException::class) {
            oso.authorize(
                User(2, Name("Павел", "Андреевич", "Матусевич")),
                "read_absence",
                SchoolClass(1, "Class", TeachingShift.FIRST)
            )
        }
    }
}
