/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 8/3/22, 11:57 PM
 */
@file:Suppress("UNUSED")

package by.enrollie.providers

import by.enrollie.data_classes.*
import by.enrollie.exceptions.*
import by.enrollie.serializers.LocalDateSerializer
import by.enrollie.serializers.LocalDateTimeSerializer
import kotlinx.coroutines.flow.SharedFlow
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
    val absenceProvider: DatabaseAbsenceProviderInterface
    val customCredentialsProvider: DatabaseCustomCredentialsProviderInterface
}

interface Event<T> {
    val eventType: EventType

    /**
     * When [eventType] is [EventType.CREATED], this is the new object.
     *
     * When [eventType] is [EventType.UPDATED], this is object's new data.
     *
     * When [eventType] is [EventType.DELETED], this is the deleted object.
     */
    val eventSubject: T

    /**
     * When [eventType] is [EventType.UPDATED], this is object's old data snapshot.
     */
    val subjectPrevState: T?

    enum class EventType {
        CREATED,
        UPDATED,
        DELETED
    }
}

interface DatabaseUserProviderInterface {
    val eventsFlow: SharedFlow<UserEvent>

    data class UserEvent internal constructor(
        override val eventType: Event.EventType,
        override val eventSubject: User,
        override val subjectPrevState: User?
    ) : Event<User>

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
    val eventsFlow: SharedFlow<RoleEvent>

    data class RoleEvent internal constructor(
        override val eventType: Event.EventType,
        override val eventSubject: RoleData,
        override val subjectPrevState: RoleData?
    ) : Event<RoleData>

    @kotlinx.serialization.Serializable
    data class RoleCreationData(
        val userID: UserID,
        val role: Roles.Role,
        val informationHolder: RoleInformationHolder,
        @kotlinx.serialization.Serializable(with = LocalDateTimeSerializer::class)
        val creationDate: LocalDateTime = LocalDateTime.now(),
        @kotlinx.serialization.Serializable(with = LocalDateTimeSerializer::class)
        val expirationDate: LocalDateTime? = null
    )

    /**
     * Returns list of all user roles
     * @throws UserDoesNotExistException if user does not exist
     */
    fun getRolesForUser(userID: UserID): List<RoleData>

    /**
     * Adds role to user.
     * @throws UserDoesNotExistException if user does not exist
     */
    fun appendRoleToUser(userID: UserID, role: RoleCreationData): RoleData

    /**
     * Appends generated roles to users.
     * @throws UserDoesNotExistException if any user does not exist
     */
    fun batchAppendRolesToUsers(users: List<UserID>, roleGenerator: (UserID) -> RoleCreationData)

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
     * @throws NoSuchElementException if role does not exist
     */
    fun <T : Any> updateRole(roleID: String, field: Roles.Role.Field<T>, value: T)

    /**
     * Revokes role (sets roleRevokedDateTime to [revokeDateTime] or, if null, to current time) from user.
     * @throws UserDoesNotExistException if user does not exist
     * @throws NoSuchElementException if role does not exist
     */
    fun revokeRoleFromUser(userID: UserID, roleID: String, revokeDateTime: LocalDateTime?)
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
    val eventsFlow: SharedFlow<LessonEvent>

    data class LessonEvent internal constructor(
        override val eventType: Event.EventType,
        override val eventSubject: Lesson,
        override val subjectPrevState: Lesson?
    ) : Event<Lesson>

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
     * Flow of absence change events.
     *
     * Anything that wants to be notified of any absence change should subscribe to this flow.
     */
    val eventsFlow: SharedFlow<AbsenceEvent>

    data class AbsenceEvent internal constructor(
        override val eventType: Event.EventType,
        /**
         * When [eventType] is [Event.EventType.CREATED], this is the absence that was created.
         *
         * When [eventType] is [Event.EventType.UPDATED], this is the new absence record data.
         */
        override val eventSubject: AbsenceRecord,
        /**
         * If [eventType] is [Event.EventType.UPDATED], this is the previous absence record snapshot.
         */
        override val subjectPrevState: AbsenceRecord?
    ) : Event<AbsenceRecord>

    /**
     * Template for new absence record creation.
     */
    @kotlinx.serialization.Serializable
    data class NewAbsenceRecord(
        val creatorID: UserID,
        val classID: ClassID,
        val studentRoleID: String,
        val absenceType: AbsenceType,
        @kotlinx.serialization.Serializable(with = LocalDateSerializer::class)
        val absenceDate: LocalDate,
        val skippedLessons: List<TimetablePlace>
    )

    fun getAbsence(absenceID: AbsenceID): AbsenceRecord?

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
     * @throws IllegalArgumentException if [date] is before [classID] was created
     */
    fun getAbsencesForClass(classID: ClassID, date: LocalDate): List<AbsenceRecord>

    /**
     * Returns list of class ids for which no absence was recorded at the given [date]
     */
    fun getClassesWithoutAbsenceInfo(date: LocalDate): List<ClassID>

    /**
     * Returns list of classes without absence records in given [datesRange]
     */
    fun getClassesWithoutAbsenceInfo(datesRange: Pair<LocalDate, LocalDate>): List<ClassID>

    /**
     * Returns list of dates on which there are no absence records for given [classID]
     */
    fun getDatesWithoutAbsenceInfo(classID: ClassID, datesRange: Pair<LocalDate, LocalDate>): List<LocalDate>

    /**
     * Updates an absence record.
     * @throws UserDoesNotExistException if user does not exist
     * @throws NoSuchElementException if absence record does not exist
     * @throws ProtectedFieldEditException if [field] is protected and cannot be updated
     */
    fun <T : Any> updateAbsence(
        absenceID: AbsenceID, field: Field<T>, value: T
    )

    /**
     * Creates a new absence record.
     * @throws UserDoesNotExistException if user does not exist
     * @throws AbsenceRecordsConflictException if absence record with same classID, studentRoleID and absenceDate already exists
     */
    fun createAbsence(record: NewAbsenceRecord): AbsenceRecord

    /**
     * Creates all given [absences] in an optimized way. If any of records already exists, [AbsenceRecordsConflictException] is thrown.
     * @throws UserDoesNotExistException if user does not exist
     * @throws AbsenceRecordsConflictException if absence record with same classID, studentRoleID and absenceDate already exists
     */
    fun createAbsences(absences: List<NewAbsenceRecord>): List<AbsenceRecord>

    /**
     * Marks [classID] as having no absence records for given [date], so that server will not assume that there is no data for that class at that date.
     *
     * Note: calling this method implies that there is no absence record for given [classID] and [date] and it will change those records lessonsList to empty list.
     * @throws SchoolClassDoesNotExistException if class does not exist
     * @see getClassesWithoutAbsenceInfo
     */
    fun markClassAsDataRich(sentByID: UserID, classID: ClassID, date: LocalDate)
}

interface DatabaseAuthenticationDataProviderInterface {
    val eventsFlow: SharedFlow<AuthenticationDataEvent>

    data class AuthenticationDataEvent internal constructor(
        override val eventType: Event.EventType,
        override val eventSubject: AuthenticationToken,
        /**
         * Always null since [AuthenticationToken] does not have editable fields.
         */
        override val subjectPrevState: AuthenticationToken?
    ) : Event<AuthenticationToken>

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

interface DatabaseCustomCredentialsProviderInterface {
    /**
     * Returns custom credentials for given [userID]
     * @throws UserDoesNotExistException if user does not exist
     */
    fun getCredentials(userID: UserID, credentialsType: String): String?

    /**
     * Sets custom credentials for given [userID]
     * @throws UserDoesNotExistException if user does not exist
     */
    fun setCredentials(userID: UserID, credentialsType: String, credentials: String)

    /**
     * Removes custom credentials for given [userID]
     * @throws UserDoesNotExistException if user does not exist
     */
    fun clearCredentials(userID: UserID, credentialsType: String)
}
