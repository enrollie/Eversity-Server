/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/15/22, 1:25 AM
 */

package by.enrollie.impl

import by.enrollie.data_classes.*
import by.enrollie.providers.*
import org.joda.time.DateTime
import java.time.LocalTime
import java.util.*

/**
 * Stub implementation of [DatabaseProviderInterface] just to make server compile while plugins discovery is not implemented.
 * Expect many requests to fail.
 */
class StubDatabaseImplementation : DatabaseProviderInterface {
    override val usersProvider: DatabaseUserProviderInterface = object : DatabaseUserProviderInterface {
        override fun getUser(userID: UserID): User? {
            return when (userID) {
                0 -> User(
                    userID, Name("John", null, "Doe")
                )
                1 -> User(
                    userID, Name("Jane", null, "Doe")
                )
                else -> null
            }
        }

        override fun getUsers(): List<User> {
            return listOf(
                User(
                    0, Name("John", null, "Doe")
                ), User(
                    1, Name("Jane", null, "Doe")
                )
            )
        }

        override fun createUser(user: User) {
            // do nothing
        }

        override fun batchCreateUsers(users: List<User>) {
            // do nothing
        }

        override fun <T : Any> updateUser(userID: UserID, field: Field<T>, value: T) {
            // do nothing
        }

        override fun deleteUser(userID: UserID) {
            // do nothing
        }

    }
    override val rolesProvider: DatabaseRolesProviderInterface = object : DatabaseRolesProviderInterface {
        override fun getRolesForUser(userID: UserID): List<RoleData<*>> {
            return when (userID) {
                0 -> listOf(
                    RoleData(0, Roles.SCHOOL.ADMINISTRATION, RoleInformationHolder(), DateTime.now(), null), RoleData(
                        0,
                        Roles.CLASS.CLASS_TEACHER,
                        RoleInformationHolder(Roles.CLASS.CLASS_TEACHER.classID to 1),
                        DateTime.now(),
                        null
                    ), RoleData(
                        0,
                        Roles.CLASS.CLASS_TEACHER,
                        RoleInformationHolder(Roles.CLASS.CLASS_TEACHER.classID to 2),
                        DateTime.parse("2020-02-20"),
                        DateTime.parse("2021-05-31")
                    )
                )
                1 -> listOf(
                    RoleData(
                        1,
                        Roles.CLASS.STUDENT,
                        RoleInformationHolder(Roles.CLASS.STUDENT.classID to 1),
                        DateTime.now(),
                        null
                    ),
                    RoleData(
                        1, Roles.CLASS.ABSENCE_PROVIDER, RoleInformationHolder(
                            Roles.CLASS.ABSENCE_PROVIDER.classID to 1, Roles.CLASS.ABSENCE_PROVIDER.delegatedBy to 0
                        ), DateTime.now(), null
                    )
                )
                else -> emptyList()
            }
        }

        override fun appendRoleToUser(userID: UserID, role: RoleData<*>) {
            // do nothing
        }

        override fun batchAppendRolesToUsers(users: List<UserID>, roleGenerator: (UserID) -> RoleData<*>) {
            // do nothing
        }

        override fun getAllRolesByType(type: Roles.Role): List<RoleData<*>> {
            return when (type) {
                Roles.CLASS.STUDENT -> listOf(
                    RoleData(
                        1,
                        Roles.CLASS.STUDENT,
                        RoleInformationHolder(Roles.CLASS.STUDENT.classID to 1),
                        DateTime.now(),
                        null
                    )
                )
                Roles.CLASS.ABSENCE_PROVIDER -> listOf(
                    RoleData(
                        1, Roles.CLASS.ABSENCE_PROVIDER, RoleInformationHolder(
                            Roles.CLASS.ABSENCE_PROVIDER.classID to 1, Roles.CLASS.ABSENCE_PROVIDER.delegatedBy to 0
                        ), DateTime.now(), null
                    )
                )
                Roles.SCHOOL.ADMINISTRATION -> listOf(
                    RoleData(0, Roles.SCHOOL.ADMINISTRATION, RoleInformationHolder(), DateTime.now(), null)
                )
                Roles.CLASS.CLASS_TEACHER -> listOf(
                    RoleData(
                        0,
                        Roles.CLASS.CLASS_TEACHER,
                        RoleInformationHolder(Roles.CLASS.CLASS_TEACHER.classID to 1),
                        DateTime.now(),
                        null
                    ), RoleData(
                        0,
                        Roles.CLASS.CLASS_TEACHER,
                        RoleInformationHolder(Roles.CLASS.CLASS_TEACHER.classID to 2),
                        DateTime.parse("2020-02-20"),
                        DateTime.parse("2021-05-31")
                    )
                )
                else -> emptyList()
            }
        }

        override fun getAllRolesByMatch(match: (RoleData<*>) -> Boolean): List<RoleData<*>> {
            return listOf() // too lazy to implement
        }

        override fun getAllRolesWithMatchingEntries(vararg entries: Pair<Roles.Role.Field<*>, Any?>): List<RoleData<*>> {
            TODO("Not yet implemented")
        }

        override fun <T : Any> updateRole(role: RoleData<*>, field: Roles.Role.Field<T>, value: T) {
            TODO("Not yet implemented")
        }

        override fun revokeRoleFromUser(userID: UserID, role: RoleData<*>, revokeDateTime: DateTime?) {
            // do nothing
        }

        override fun deleteRole(userID: UserID, role: RoleData<*>) {
            // do nothing
        }

    }
    override val classesProvider: DatabaseClassesProviderInterface = object : DatabaseClassesProviderInterface {
        override fun getClass(classID: ClassID): SchoolClass? {
            return when (classID) {
                1 -> SchoolClass(
                    classID, "10 A", TeachingShift.FIRST
                )
                else -> null
            }
        }

        override fun getClasses(): List<SchoolClass> {
            return listOf(
                SchoolClass(
                    1, "10 A", TeachingShift.FIRST
                )
            )
        }

        override fun createClass(classData: SchoolClass) {
            // do nothing
        }

        override fun batchCreateClasses(classes: List<SchoolClass>) {
            // do nothing
        }

        override fun <T : Any> updateClass(classID: ClassID, field: Field<T>, value: T) {
            // do nothing
        }

        override fun getPupilsOrdering(classID: ClassID): List<Pair<UserID, Int>> {
            return listOf()
        }

        override fun setPupilsOrdering(classID: ClassID, pupilsOrdering: List<Pair<UserID, Int>>) {
            TODO("Not yet implemented")
        }

    }
    override val timetableProvider: DatabaseTimetableProviderInterface = object : DatabaseTimetableProviderInterface {
        override fun getCurrentTimetableForClass(classID: ClassID): Timetable? {
            return when (classID) {
                1 -> Timetable(
                    monday = listOf(
                        TimetableLessonCell(1, ""), TimetableLessonCell(2, ""), TimetableLessonCell(3, "")
                    ), tuesday = listOf(
                        TimetableLessonCell(1, ""), TimetableLessonCell(2, ""), TimetableLessonCell(3, "")
                    ), wednesday = listOf(
                        TimetableLessonCell(1, ""), TimetableLessonCell(2, ""), TimetableLessonCell(3, "")
                    ), thursday = listOf(
                        TimetableLessonCell(1, ""), TimetableLessonCell(2, ""), TimetableLessonCell(3, "")
                    ), friday = listOf(
                        TimetableLessonCell(1, ""), TimetableLessonCell(2, ""), TimetableLessonCell(3, "")
                    ), saturday = listOf(), sunday = listOf(), DateTime.now(), null
                )
                else -> null
            }
        }

        override fun getArchivedTimetableForClass(classID: ClassID, dateTime: DateTime): Timetable? {
            return null // too lazy to implement
        }

        override fun changeTimetableForClass(classID: ClassID, timetable: Timetable) {
            // do nothing
        }

        override fun setTimetableForClass(classID: ClassID, timetable: Timetable) {
            // do nothing
        }

        override fun deleteCurrentTimetableForClass(classID: ClassID) {
            // do nothing
        }

        override fun getTimetablePlaces(): TimetablePlaces {
            return TimetablePlaces(
                mapOf(
                    1 to EventConstraints(LocalTime.of(9, 0), LocalTime.of(9, 45)),
                    2 to EventConstraints(LocalTime.of(10, 0), LocalTime.of(10, 45)),
                    3 to EventConstraints(LocalTime.of(11, 0), LocalTime.of(11, 45)),
                    4 to EventConstraints(LocalTime.of(12, 0), LocalTime.of(12, 45)),
                    5 to EventConstraints(LocalTime.of(13, 0), LocalTime.of(13, 45)),
                    6 to EventConstraints(LocalTime.of(13, 55), LocalTime.of(14, 40)),
                    7 to EventConstraints(LocalTime.of(14, 50), LocalTime.of(15, 35))
                ), mapOf(
                    1 to EventConstraints(LocalTime.of(12, 0), LocalTime.of(12, 45)),
                    2 to EventConstraints(LocalTime.of(13, 0), LocalTime.of(13, 45)),
                    3 to EventConstraints(LocalTime.of(14, 0), LocalTime.of(14, 45)),
                    4 to EventConstraints(LocalTime.of(15, 0), LocalTime.of(15, 45)),
                    5 to EventConstraints(LocalTime.of(16, 0), LocalTime.of(16, 45)),
                )
            )
        }

        override fun setTimetablePlaces(timetablePlaces: TimetablePlaces) {
            // do nothing
        }

        override fun getTimetableForTeacher(teacherID: UserID): Timetable? {
            TODO("Not yet implemented")
        }

        override fun setTimetableForTeacher(teacherID: UserID, timetables: Pair<Timetable, Timetable>) {
            TODO("Not yet implemented")
        }

    }
    override val authenticationDataProvider: DatabaseAuthenticationDataProviderInterface =
        object : DatabaseAuthenticationDataProviderInterface {
            override fun getUserTokens(userID: UserID): List<AuthenticationToken> {
                return listOf() // too lazy to implement
            }

            override fun generateNewToken(userID: UserID): AuthenticationToken {
                return AuthenticationToken(
                    UUID.randomUUID().toString(), userID, DateTime.now()
                )
            }

            override fun revokeToken(token: AuthenticationToken) {
                // do nothing
            }

            override fun getUserByToken(token: String): UserID? {
                return null // too lazy to implement
            }

            override fun checkToken(token: String, userID: UserID): Boolean {
                return false // too lazy to implement
            }

        }
    override val lessonsProvider: DatabaseLessonsProviderInterface
        get() = TODO("Not yet implemented")

}
