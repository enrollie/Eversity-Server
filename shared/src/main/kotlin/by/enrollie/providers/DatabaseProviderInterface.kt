/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/27/22, 10:55 PM
 */
@file:Suppress("UNUSED")

package by.enrollie.providers

import by.enrollie.data_classes.*
import by.enrollie.exceptions.*
import java.time.LocalDate
import java.time.LocalDateTime

interface DatabaseProviderInterface {
    /**
     * Must be equal to the plugin ID.
     */
    val databasePluginID: String

    val usersProvider: DatabaseUserProviderInterface
    val rolesProvider: DatabaseRolesProviderInterface
    val classesProvider: DatabaseClassesProviderInterface
    val timetablePlacingProvider: DatabaseTimetablePlacingProviderInterface
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
    fun getRolesForUser(userID: UserID): List<RoleData>

    /**
     * Adds role to user.
     * @throws UserDoesNotExistException if user does not exist
     */
    fun appendRoleToUser(userID: UserID, role: RoleData)

    /**
     * Appends generated roles to users.
     * @throws UserDoesNotExistException if any user does not exist
     */
    fun batchAppendRolesToUsers(users: List<UserID>, roleGenerator: (UserID) -> RoleData)

    /**
     * Returns list of all roles with [type].
     */
    fun getAllRolesByType(type: Roles.Role): List<RoleData>

    /**
     * Returns list of all matching roles in database.
     */
    fun getAllRolesByMatch(match: (RoleData) -> Boolean): List<RoleData>

    /**
     * Returns list of all roles where all entries matching.
     */
    fun getAllRolesWithMatchingEntries(vararg entries: Pair<Roles.Role.Field<*>, Any?>): List<RoleData>

    /**
     * Updates role's entry.
     * @throws ProtectedFieldEditException if [field] is protected and cannot be updated
     * @throws IllegalArgumentException if [field] is not valid for this class
     */
    fun <T : Any> updateRole(role: RoleData, field: Roles.Role.Field<T>, value: T)

    /**
     * Revokes role (sets roleRevokedDateTime to [revokeDateTime] or, if null, to current time) from user.
     * @throws UserDoesNotExistException if user does not exist
     */
    fun revokeRoleFromUser(userID: UserID, role: RoleData, revokeDateTime: LocalDateTime?)
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
     * @throws IllegalArgumentException if [field] is not valid for this class
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
     * @throws UserDoesNotExistException if any pupil does not exist
     * @throws IllegalArgumentException if [pupilsOrdering] is not a permutation of all pupils in class or if sorted values are not consecutive, or if values are not unique.
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
     * @throws SchoolClassDoesNotExistException if class does not exist
     */
    fun getLessonsForClass(classID: ClassID): List<Lesson>

    /**
     * Returns list of all lessons for given [classID] in [datesRange]
     * @throws SchoolClassDoesNotExistException if class does not exist
     */
    fun getLessonsForClass(classID: ClassID, datesRange: Pair<LocalDate, LocalDate>): List<Lesson>

    /**
     * Returns list of all lessons for given [classID] for given [date]
     * @throws SchoolClassDoesNotExistException if class does not exist
     */
    fun getLessonsForClass(classID: ClassID, date: LocalDate): List<Lesson>

    /**
     * Returns list of all lessons for user with given [teacherID] and Teacher role
     */
    fun getLessonsForTeacher(teacherID: UserID): List<Lesson>

    /**
     * Returns list of lessons for given [teacherID] and Teacher role in [datesRange]
     */
    fun getLessonsForTeacher(teacherID: UserID, datesRange: Pair<LocalDate, LocalDate>): List<Lesson>

    /**
     * Returns list of lessons for given [teacherID] and Teacher role for given [date]
     */
    fun getLessonsForTeacher(teacherID: UserID, date: LocalDate): List<Lesson>

    /**
     * Creates new lesson.
     * @throws LessonIDConflictException if lesson with same ID already exists
     */
    fun createLesson(lesson: Lesson)

    /**
     * Updates existing lessons with same ID or creates new ones. Use this method to update or create multiple lessons at once.
     * @throws SchoolClassDoesNotExistException if class in one of the lessons does not exist
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
     * @see createOrUpdateLessons
     */
    fun setJournalTitles(mappedTitles: Map<JournalID, String>)
}

interface DatabaseTimetablePlacingProviderInterface {
    /**
     * Returns [TimetablePlaces] effective now.
     * @throws IllegalStateException if timetable is not set.
     */
    fun getTimetablePlaces(): TimetablePlaces

    /**
     * Get [TimetablePlaces] effective on given [date].
     */
    fun getTimetablePlaces(date: LocalDate): TimetablePlaces?

    /**
     * Sets [TimetablePlaces] and changes previous (if any) timetablePlaces revoking date to current one.
     */
    fun updateTimetablePlaces(timetablePlaces: TimetablePlaces)
}

interface DatabaseAbsenceProviderInterface {
    /**
     * Returns a list of all absence records for given [userID] in given [datesRange]
     * @throws UserDoesNotExistException if user does not exist
     */
    fun getAbsencesForUser(userID: UserID, datesRange: Pair<LocalDate, LocalDate>): List<AbsenceRecord>

    /**
     * Returns a list of all absence records for given [classID] in given [datesRange]
     * @throws SchoolClassDoesNotExistException if class does not exist
     */
    fun getAbsencesForClass(classID: ClassID, datesRange: Pair<LocalDate, LocalDate>): List<AbsenceRecord>

    /**
     * Returns a list of all absence records for given [classID] for given [date]
     * @throws SchoolClassDoesNotExistException if class does not exist
     */
    fun getAbsencesForClass(classID: ClassID, date: LocalDate): List<AbsenceRecord>

    /**
     * Returns list of class ids for which no absence was recorded at the given [date]
     */
    fun getClassesWithoutAbsenceInfo(date: LocalDate): List<ClassID>

    /**
     * Updates an absence record.
     * @throws UserDoesNotExistException if user does not exist
     * @throws NoSuchElementException if absence record does not exist
     * @throws ProtectedFieldEditException if [field] is protected and cannot be updated
     */
    fun <T : Any> updateAbsence(
        userID: UserID, studentData: RoleData, classID: ClassID, field: Field<T>, value: T
    )

    /**
     * Creates an [absence]. If similar absence already exists, it is updated.
     * @throws UserDoesNotExistException if user does not exist
     */
    fun createAbsence(userID: UserID, absence: AbsenceRecord)

    /**
     * Creates all given [absences] in an optimized way. If similar single absence record already exists, it is updated.
     * @throws UserDoesNotExistException if user does not exist
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
     * @throws UserDoesNotExistException if user does not exist
     */
    fun getUserTokens(userID: UserID): List<AuthenticationToken>

    /**
     * Creates a new unique token for [userID].
     * @throws UserDoesNotExistException if user does not exist
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
