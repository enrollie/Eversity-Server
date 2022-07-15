/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/15/22, 2:40 AM
 */

package by.enrollie.providers

import by.enrollie.data_classes.*
import by.enrollie.exceptions.*
import org.joda.time.DateTime

interface DatabaseProviderInterface {
    /**
     * Usually plugin ID
     */
    val databaseID: String

    val usersProvider: DatabaseUserProviderInterface
    val rolesProvider: DatabaseRolesProviderInterface
    val classesProvider: DatabaseClassesProviderInterface
    val timetableProvider: DatabaseTimetableProviderInterface
    val authenticationDataProvider: DatabaseAuthenticationDataProviderInterface
    val lessonsProvider: DatabaseLessonsProviderInterface
}

interface DatabaseUserProviderInterface {
    /**
     * Returns user by id (or null if user does not exist)
     */
    fun getUser(userID: UserID): User?

    /**
     * Returns all users
     */
    fun getUsers(): List<User>

    /**
     * Creates new user.
     * @throws UserIDConflictException if user with same ID already exists
     */
    fun createUser(user: User)

    /**
     * Creates all given [users] in an optimized way. If an exception is thrown, the whole batch is aborted.
     * @throws UserIDConflictException if user with same ID already exists
     */
    fun batchCreateUsers(users: List<User>)

    /**
     * Updates user.
     * @throws UserDoesNotExistException if user does not exist
     * @throws ProtectedFieldEditException if [field] is protected and cannot be updated
     */
    fun <T : Any> updateUser(userID: UserID, field: Field<T>, value: T)

    /**
     * Deletes user and all related data in a cascade manner.
     * **IMPORTANT:** This method is destructive and cannot be undone, so use it with care and only after explicit administrator's approval.
     */
    fun deleteUser(userID: UserID)
}

interface DatabaseRolesProviderInterface {
    /**
     * Returns list of all user roles
     * @throws UserDoesNotExistException if user does not exist
     */
    fun getRolesForUser(userID: UserID): List<RoleData<*>>

    /**
     * Adds role to user.
     * @throws UserDoesNotExistException if user does not exist
     */
    fun appendRoleToUser(userID: UserID, role: RoleData<*>)

    /**
     * Appends generated roles to users.
     */
    fun batchAppendRolesToUsers(users: List<UserID>, roleGenerator: (UserID) -> RoleData<*>)

    /**
     * Returns list of all roles with [type].
     */
    fun getAllRolesByType(type: Roles.Role): List<RoleData<*>>

    /**
     * Returns list of all matching roles in database.
     */
    fun getAllRolesByMatch(match: (RoleData<*>) -> Boolean): List<RoleData<*>>

    /**
     * Returns list of all roles where all entries matching.
     */
    fun getAllRolesWithMatchingEntries(vararg entries: Pair<Roles.Role.Field<*>, Any?>): List<RoleData<*>>

    /**
     * Updates role's entry.
     * @throws ProtectedFieldEditException if [field] is protected and cannot be updated
     */
    fun <T : Any> updateRole(role: RoleData<*>, field: Roles.Role.Field<T>, value: T)

    /**
     * Revokes role (sets roleRevokedDateTime to [revokeDateTime] or, if null, to current time) from user.
     * @throws UserDoesNotExistException if user does not exist
     */
    fun revokeRoleFromUser(userID: UserID, role: RoleData<*>, revokeDateTime: DateTime?)

    /**
     * Deletes role from user.
     * **IMPORTANT:** This method is destructive and cannot be undone.
     * @throws UserDoesNotExistException if user does not exist
     */
    fun deleteRole(userID: UserID, role: RoleData<*>)
}

interface DatabaseClassesProviderInterface {
    /**
     * Returns class by id (or null if class does not exist)
     */
    fun getClass(classID: ClassID): SchoolClass?

    /**
     * Returns list of all classes
     */
    fun getClasses(): List<SchoolClass>

    /**
     * Creates new class.
     * @throws SchoolClassIDConflictException if class with same ID already exists
     */
    fun createClass(classData: SchoolClass)

    /**
     * Creates all given [classes] in an optimized way. If an exception is thrown, the whole batch is aborted.
     * @throws SchoolClassIDConflictException if class with same ID already exists
     */
    fun batchCreateClasses(classes: List<SchoolClass>)

    /**
     * Updates class.
     * @throws SchoolClassDoesNotExistException if class does not exist
     * @throws ProtectedFieldEditException if [field] is protected and cannot be updated
     */
    fun <T : Any> updateClass(classID: ClassID, field: Field<T>, value: T)

    /**
     * Returns ordering of pupils in class.
     * @throws SchoolClassDoesNotExistException if class does not exist
     */
    fun getPupilsOrdering(classID: ClassID): List<Pair<UserID, Int>>

    /**
     * Sets ordering of pupils in class.
     * @throws SchoolClassDoesNotExistException if class does not exist
     */
    fun setPupilsOrdering(classID: ClassID, pupilsOrdering: List<Pair<UserID, Int>>)
}

interface DatabaseLessonsProviderInterface {
    /**
     * Returns lesson by id (or null if lesson does not exist)
     */
    fun getLesson(lessonID: LessonID): Lesson?

    /**
     * Returns list of all lessons for given [classID]
     */
    fun getLessonsForClass(classID: ClassID): List<Lesson>

    /**
     * Creates new lesson.
     * @throws LessonIDConflictException if lesson with same ID already exists
     */
    fun createLesson(lesson: Lesson)

    /**
     * Updates existing lessons with same ID or creates new ones. Use this method to update or create multiple lessons at once.
     */
    fun createOrUpdateLessons(lessons: List<Lesson>)

    /**
     * Deletes lesson.
     * **IMPORTANT:** This method is destructive and cannot be undone.
     * @throws LessonDoesNotExistException if lesson does not exist
     */
    fun deleteLesson(lessonID: LessonID)

    /**
     * Returns a map of journalID corresponding to its title (or to null, if there is no such journal).
     */
    fun getJournalTitles(journals: List<JournalID>): Map<JournalID, String?>

    /**
     * Sets journal titles.
     * @see batchCreateLessons
     */
    fun setJournalTitles(mappedTitles: Map<JournalID, String>)
}

interface DatabaseTimetableProviderInterface {
    /**
     * Returns timetable for given [classID]
     */
    fun getCurrentTimetableForClass(classID: ClassID): Timetable?

    /**
     * Searches for timetable for given [classID] that was in effect at given [dateTime]
     */
    fun getArchivedTimetableForClass(classID: ClassID, dateTime: DateTime): Timetable?

    /**
     * Sets previous timetable (if any) as outdated and replaces it with [timetable]
     */
    fun changeTimetableForClass(classID: ClassID, timetable: Timetable)

    /**
     * Replaces current timetable with [timetable] without making current timetable (if any) outdated.
     */
    fun setTimetableForClass(classID: ClassID, timetable: Timetable)

    /**
     * Deletes timetable for given [classID] without making current timetable (if any) outdated.
     */
    fun deleteCurrentTimetableForClass(classID: ClassID)

    /**
     * Returns [TimetablePlaces].
     * @throws IllegalStateException if timetable is not set.
     */
    fun getTimetablePlaces(): TimetablePlaces

    /**
     * Sets [TimetablePlaces] to the [timetablePlaces]
     */
    fun setTimetablePlaces(timetablePlaces: TimetablePlaces)

    /**
     * Returns [Timetable] (if any) of user with [teacherID] and active Teacher role
     * @throws UserDoesNotExistException if user does not exist
     * @thrown NoMatchingRoleException if user does not have Teacher role
     */
    fun getTimetableForTeacher(teacherID: UserID): Timetable?

    /**
     * Sets [Timetable]s for user with [teacherID] and active Teacher role
     * @throws UserDoesNotExistException if user does not exist
     * @throws NoMatchingRoleException if user does not have Teacher role
     */
    fun setTimetableForTeacher(teacherID: UserID, timetables: Pair<Timetable, Timetable>)
}

interface DatabaseAbsenceProviderInterface {
    /**
     * Returns a list of all absence records for given [userID] in given [dateRange]
     */
    fun getAbsencesForUser(userID: UserID, dateRange: Pair<DateTime, DateTime>): List<AbsenceRecord>

    /**
     * Returns a list of all absence records for given [classID] in given [dateRange]
     */
    fun getAbsencesForClass(classID: ClassID, dateRange: Pair<DateTime, DateTime>): List<AbsenceRecord>

    /**
     * Returns list of class ids for which no absence was recorded at the given [dateTime]
     */
    fun getClassesWithoutAbsenceInfo(dateTime: DateTime): List<ClassID>

    /**
     * Updates an absence record.
     * @throws NoSuchElementException if absence record does not exist
     * @throws ProtectedFieldEditException if [field] is protected and cannot be updated
     */
    fun <T : Any> updateAbsence(
        userID: UserID, studentData: RoleData<Roles.CLASS.Student>, classID: ClassID, field: Field<T>, value: T
    )

    /**
     * Creates an [absence]. If similar absence already exists, it is updated.
     */
    fun createAbsence(userID: UserID, absence: AbsenceRecord)

    /**
     * Creates all given [absences] in an optimized way. If similar single absence record already exists, it is updated.
     */
    fun createAbsences(userID: UserID, absences: List<AbsenceRecord>)

    /**
     * Deletes [absence] by finding similar one and deleting it.
     */
    fun deleteAbsence(absence: AbsenceRecord)
}

interface DatabaseAuthenticationDataProviderInterface {
    /**
     * Returns all user tokens
     */
    fun getUserTokens(userID: UserID): List<AuthenticationToken>

    /**
     * Creates a new unique token for [userID].
     */
    fun generateNewToken(userID: UserID): AuthenticationToken

    /**
     * Revokes given token
     */
    fun revokeToken(token: AuthenticationToken)

    /**
     * Tries to find token and returns user associated with it (null else).
     */
    fun getUserByToken(token: String): UserID?

    /**
     * Checks if token is valid, is associated with a user and is not expired and returns true if so.
     */
    fun checkToken(token: String, userID: UserID): Boolean
}

interface Database
