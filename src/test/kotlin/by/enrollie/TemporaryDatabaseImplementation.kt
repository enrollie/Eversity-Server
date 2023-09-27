/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 10/30/22, 7:57 PM
 */

package by.enrollie

import by.enrollie.annotations.UnsafeAPI
import by.enrollie.data_classes.*
import by.enrollie.providers.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

interface TemporaryDatabaseModule {
    fun clearDatabase()
}

class TemporaryDatabaseImplementation : DatabaseProviderInterface {
    override val databasePluginID: String = "by.enrollie.database.temporary"
    override val usersProvider: DatabaseUserProviderInterface = TemporaryDatabaseUserProvider()
    override val rolesProvider: DatabaseRolesProviderInterface = TemporaryDatabaseRolesProvider()
    override val classesProvider: DatabaseClassesProviderInterface = TemporaryDatabaseClassesProvider()
    override val timetablePlacingProvider: DatabaseTimetablePlacingProviderInterface =
        TemporaryDatabaseTimetablePlacingProvider()
    override val authenticationDataProvider: DatabaseAuthenticationDataProviderInterface =
        TemporaryDatabaseAuthenticationDataProvider()
    override val lessonsProvider: DatabaseLessonsProviderInterface = TemporaryDatabaseLessonsProvider()
    override val absenceProvider: DatabaseAbsenceProviderInterface = TemporaryDatabaseAbsenceProvider(this)
    override val customCredentialsProvider: DatabaseCustomCredentialsProviderInterface =
        TemporaryDatabaseCustomCredentialsProvider()

    override fun <T> runInSingleTransaction(block: (database: DatabaseProviderInterface) -> T): T {
        return block(this)
    }

    override suspend fun <T> runInSingleTransactionAsync(block: suspend (database: DatabaseProviderInterface) -> T): T {
        return block(this)
    }

    fun clear() {
        (customCredentialsProvider as TemporaryDatabaseModule).clearDatabase()
        (absenceProvider as TemporaryDatabaseModule).clearDatabase()
        (lessonsProvider as TemporaryDatabaseModule).clearDatabase()
        (authenticationDataProvider as TemporaryDatabaseModule).clearDatabase()
        (timetablePlacingProvider as TemporaryDatabaseModule).clearDatabase()
        (classesProvider as TemporaryDatabaseModule).clearDatabase()
        (rolesProvider as TemporaryDatabaseModule).clearDatabase()
        (usersProvider as TemporaryDatabaseModule).clearDatabase()
    }
}

class TemporaryDatabaseCustomCredentialsProvider : DatabaseCustomCredentialsProviderInterface, TemporaryDatabaseModule {
    private val credentials = mutableMapOf<String, String>()
    override fun clearDatabase() {
        credentials.clear()
    }

    override fun getCredentials(userID: UserID, credentialsType: String): String? {
        return credentials["$userID:$credentialsType"]
    }

    override fun setCredentials(userID: UserID, credentialsType: String, credentials: String) {
        this.credentials["$userID:$credentialsType"] = credentials
    }

    override fun clearCredentials(userID: UserID, credentialsType: String) {
        credentials.remove("$userID:$credentialsType")
    }

}

class TemporaryDatabaseAbsenceProvider(private val database: DatabaseProviderInterface) :
    DatabaseAbsenceProviderInterface, TemporaryDatabaseModule {
    private val _eventsFlow = MutableSharedFlow<DatabaseAbsenceProviderInterface.AbsenceEvent>()
    override val eventsFlow: SharedFlow<DatabaseAbsenceProviderInterface.AbsenceEvent>
        get() = _eventsFlow
    private val absences = mutableListOf<AbsenceRecord>()
    private val dataRich = mutableListOf<Pair<LocalDate, ClassID>>()
    override fun clearDatabase() {
        absences.clear()
        dataRich.clear()
        nextAbsenceID.set(0)
    }

    override fun getAbsence(absenceID: AbsenceID): AbsenceRecord? {
        return absences.find { it.id == absenceID }
    }

    override fun getAbsencesForUser(userID: UserID, datesRange: Pair<LocalDate, LocalDate>): List<AbsenceRecord> {
        return absences.filter { it.student.id == userID && it.absenceDate in datesRange.first..datesRange.second }
    }

    override fun getAbsencesForClass(classID: ClassID, datesRange: Pair<LocalDate, LocalDate>): List<AbsenceRecord> {
        return absences.filter { it.classID == classID && it.absenceDate in datesRange.first..datesRange.second }
    }

    override fun getAbsencesForClass(classID: ClassID, date: LocalDate): List<AbsenceRecord> {
        return absences.filter { it.classID == classID && it.absenceDate == date }
    }

    override fun getClassesWithoutAbsenceInfo(date: LocalDate): List<ClassID> {
        return database.classesProvider.getClasses().map { it.id }.filter { classID ->
            absences.none { it.classID == classID && it.absenceDate == date } && dataRich.none { it.first == date && it.second == classID }
        }
    }

    override fun getClassesWithoutAbsenceInfo(datesRange: Pair<LocalDate, LocalDate>): List<ClassID> {
        return database.classesProvider.getClasses().map { it.id }.filter { classID ->
            absences.none { it.classID == classID && it.absenceDate in datesRange.first..datesRange.second } && dataRich.none { it.first in datesRange.first..datesRange.second && it.second == classID }
        }
    }

    override fun getDatesWithoutAbsenceInfo(classID: ClassID, datesRange: Pair<LocalDate, LocalDate>): List<LocalDate> {
        return (datesRange.first.datesUntil(datesRange.second).toList() + datesRange.second).filter { date ->
            absences.none { it.classID == classID && it.absenceDate == date } && dataRich.none { it.first == date && it.second == classID }
        }
    }

    override fun <T : Any> updateAbsence(updatedByRole: String, absenceID: AbsenceID, field: Field<T>, value: T) {
        val absence = absences.find { it.id == absenceID } ?: return
        val role = database.rolesProvider.getAllRolesByMatch { it.uniqueID == updatedByRole }.first()
        val updatedAbsence = when (field) {
            Field(AbsenceRecord::absenceType) -> absence.copy(absenceType = value as AbsenceType)
            Field(AbsenceRecord::lessonsList) -> absence.copy(lessonsList = value as List<TimetablePlace>)
            else -> throw IllegalArgumentException("Field $field is not supported")
        }.copy(
            lastUpdatedBy = AuthorizedChangeAuthor(database.usersProvider.getUser(role.userID)!!, role),
            lastUpdated = LocalDateTime.now()
        )
        absences.remove(absence)
        absences.add(updatedAbsence)
        CoroutineScope(Dispatchers.IO).launch {
            _eventsFlow.emit(
                DatabaseAbsenceProviderInterface.AbsenceEvent(
                    Event.EventType.UPDATED,
                    absence,
                    updatedAbsence
                )
            )
        }
    }

    private val nextAbsenceID = AtomicLong(0)

    override fun createAbsence(record: DatabaseAbsenceProviderInterface.NewAbsenceRecord): AbsenceRecord {
        val creatorRole = database.rolesProvider.getAllRolesByMatch { it.uniqueID == record.creatorRoleID }.first()
        val creatorUser = database.usersProvider.getUser(creatorRole.userID)!!
        val absence = AbsenceRecord(
            id = nextAbsenceID.addAndGet(1),
            classID = record.classID,
            student = database.usersProvider.getUser(record.studentUserID)!!,
            absenceDate = record.absenceDate,
            absenceType = record.absenceType,
            lessonsList = record.skippedLessons,
            created = LocalDateTime.now(),
            createdBy = AuthorizedChangeAuthor(creatorUser, creatorRole),
            lastUpdatedBy = null, lastUpdated = null
        )
        absences.add(absence)
        dataRich.removeIf { it.first == absence.absenceDate && it.second == absence.classID }
        CoroutineScope(Dispatchers.IO).launch {
            _eventsFlow.emit(DatabaseAbsenceProviderInterface.AbsenceEvent(Event.EventType.CREATED, absence, null))
        }
        return absence
    }

    override fun createAbsences(absences: List<DatabaseAbsenceProviderInterface.NewAbsenceRecord>): List<AbsenceRecord> {
        return absences.map { createAbsence(it) }
    }

    override fun markClassAsDataRich(sentByRole: String, classID: ClassID, date: LocalDate) {
        if (dataRich.none { it.first == date && it.second == classID }) {
            dataRich.add(date to classID)
        }
    }

    override fun getAllAbsences(): List<AbsenceRecord> {
        return absences
    }

    override fun getAbsences(datesRange: Pair<LocalDate, LocalDate>): List<AbsenceRecord> {
        return absences.filter { it.absenceDate in datesRange.first..datesRange.second }
    }

    override fun getAbsences(date: LocalDate): List<AbsenceRecord> {
        return absences.filter { it.absenceDate == date }
    }

}

class TemporaryDatabaseLessonsProvider : DatabaseLessonsProviderInterface, TemporaryDatabaseModule {
    private val _eventsFlow = MutableSharedFlow<DatabaseLessonsProviderInterface.LessonEvent>()
    override val eventsFlow: SharedFlow<DatabaseLessonsProviderInterface.LessonEvent>
        get() = _eventsFlow
    private val lessons = mutableListOf<Lesson>()
    private val subgroups = mutableListOf<Subgroup>()
    override fun clearDatabase() {
        lessons.clear()
        subgroups.clear()
        journalIDs.clear()
    }

    override fun getLesson(lessonID: LessonID): Lesson? {
        return lessons.find { it.id == lessonID }
    }

    override fun getLessonsForClass(classID: ClassID): List<Lesson> {
        return lessons.filter { it.classID == classID }
    }

    override fun getLessonsForClass(classID: ClassID, datesRange: Pair<LocalDate, LocalDate>): List<Lesson> {
        return lessons.filter { it.classID == classID && it.date in datesRange.first..datesRange.second }
    }

    override fun getLessonsForClass(classID: ClassID, date: LocalDate): List<Lesson> {
        return lessons.filter { it.classID == classID && it.date == date }
    }

    override fun getLessonsForTeacher(teacherID: UserID): List<Lesson> {
        return lessons.filter { it.teacher == teacherID }
    }

    override fun getLessonsForTeacher(teacherID: UserID, datesRange: Pair<LocalDate, LocalDate>): List<Lesson> {
        return lessons.filter { it.teacher == teacherID && it.date in datesRange.first..datesRange.second }
    }

    override fun getLessonsForTeacher(teacherID: UserID, date: LocalDate): List<Lesson> {
        return lessons.filter { it.teacher == teacherID && it.date == date }
    }

    override fun createLesson(lesson: Lesson) {
        lessons.add(lesson)
        CoroutineScope(Dispatchers.IO).launch {
            _eventsFlow.emit(DatabaseLessonsProviderInterface.LessonEvent(Event.EventType.CREATED, lesson, null))
        }
    }

    override fun createOrUpdateLessons(lessons: List<Lesson>) {
        lessons.forEach { lesson ->
            val oldLesson = this.lessons.find { it.id == lesson.id }
            if (oldLesson != null) {
                this.lessons.remove(oldLesson)
                this.lessons.add(lesson)
                CoroutineScope(Dispatchers.IO).launch {
                    _eventsFlow.emit(
                        DatabaseLessonsProviderInterface.LessonEvent(
                            Event.EventType.UPDATED,
                            lesson,
                            oldLesson
                        )
                    )
                }
            } else {
                this.lessons.add(lesson)
                CoroutineScope(Dispatchers.IO).launch {
                    _eventsFlow.emit(
                        DatabaseLessonsProviderInterface.LessonEvent(
                            Event.EventType.CREATED,
                            lesson,
                            null
                        )
                    )
                }
            }
        }
    }

    override fun deleteLesson(lessonID: LessonID) {
        val lesson = lessons.find { it.id == lessonID }
        if (lesson != null) {
            lessons.remove(lesson)
            CoroutineScope(Dispatchers.IO).launch {
                _eventsFlow.emit(DatabaseLessonsProviderInterface.LessonEvent(Event.EventType.DELETED, lesson, null))
            }
        }
    }

    private val journalIDs = mutableMapOf<JournalID, String>()

    override fun getJournalTitles(journals: List<JournalID>): Map<JournalID, String?> {
        return journals.associateWith { journalIDs[it] }
    }

    override fun setJournalTitles(mappedTitles: Map<JournalID, String>) {
        journalIDs.putAll(mappedTitles)
    }

    override fun getAllLessons(): List<Lesson> {
        return lessons
    }

    override fun getLessons(date: LocalDate): List<Lesson> {
        return lessons.filter { it.date == date }
    }

    override fun getLessons(datesRange: Pair<LocalDate, LocalDate>): List<Lesson> {
        return lessons.filter { it.date in datesRange.first..datesRange.second }
    }

    override fun createSubgroup(
        subgroupID: SubgroupID,
        schoolClassID: ClassID,
        subgroupName: String,
        members: List<UserID>?
    ) {
        subgroups.add(Subgroup(subgroupID, subgroupName, schoolClassID, members ?: listOf()))
    }

    override fun createSubgroups(subgroupsList: List<Subgroup>) {
        subgroups.addAll(subgroupsList)
    }

    override fun getSubgroup(subgroupID: SubgroupID): Subgroup? {
        return subgroups.find { it.id == subgroupID }
    }

    override fun <T : Any> updateSubgroup(subgroupID: SubgroupID, field: Field<T>, value: T) {
        val subgroup = subgroups.find { it.id == subgroupID }
            ?: throw IllegalArgumentException("Subgroup with id $subgroupID not found")
        val newSubgroup = when (field) {
            Field(Subgroup::title) -> subgroup.copy(title = value as String)
            Field(Subgroup::members) -> subgroup.copy(members = value as List<UserID>)
            else -> throw IllegalArgumentException("Field $field is not supported")
        }
        subgroups.remove(subgroup)
        subgroups.add(newSubgroup)
    }

    override fun deleteSubgroup(subgroupID: SubgroupID) {
        subgroups.removeIf { it.id == subgroupID }
    }
}

class TemporaryDatabaseAuthenticationDataProvider : DatabaseAuthenticationDataProviderInterface,
    TemporaryDatabaseModule {
    private val _eventsFlow = MutableSharedFlow<DatabaseAuthenticationDataProviderInterface.AuthenticationDataEvent>()
    override val eventsFlow: SharedFlow<DatabaseAuthenticationDataProviderInterface.AuthenticationDataEvent>
        get() = _eventsFlow
    private val tokens = mutableListOf<AuthenticationToken>()
    override fun clearDatabase() {
        tokens.clear()
    }

    override fun getUserTokens(userID: UserID): List<AuthenticationToken> {
        return tokens.filter { it.userID == userID }
    }

    override fun generateNewToken(userID: UserID): AuthenticationToken {
        val token = AuthenticationToken(UUID.randomUUID().toString(), userID, LocalDateTime.now())
        tokens.add(token)
        CoroutineScope(Dispatchers.IO).launch {
            _eventsFlow.emit(
                DatabaseAuthenticationDataProviderInterface.AuthenticationDataEvent(
                    Event.EventType.CREATED,
                    token,
                    null
                )
            )
        }
        return token
    }

    override fun revokeToken(token: AuthenticationToken) {
        tokens.remove(token)
        CoroutineScope(Dispatchers.IO).launch {
            _eventsFlow.emit(
                DatabaseAuthenticationDataProviderInterface.AuthenticationDataEvent(
                    Event.EventType.DELETED,
                    token,
                    null
                )
            )
        }
    }

    override fun getUserByToken(token: String): UserID? {
        return tokens.find { it.token == token }?.userID
    }

    override fun checkToken(token: String, userID: UserID): Boolean {
        return tokens.find { it.token == token && it.userID == userID } != null
    }

}

class TemporaryDatabaseTimetablePlacingProvider : DatabaseTimetablePlacingProviderInterface, TemporaryDatabaseModule {
    private val timetablePlacing = mutableListOf<Pair<LocalDateTime, TimetablePlaces>>()
    override fun clearDatabase() {
        timetablePlacing.clear()
    }

    override fun getTimetablePlaces(): TimetablePlaces {
        return timetablePlacing.minBy { it.first.toEpochSecond(ZoneOffset.UTC) }.second
    }

    override fun getTimetablePlaces(date: LocalDate): TimetablePlaces? {
        return timetablePlacing.firstOrNull { it.first.toLocalDate() == date }?.second
    }

    override fun updateTimetablePlaces(timetablePlaces: TimetablePlaces) {
        timetablePlacing.add(LocalDateTime.now() to timetablePlaces)
    }

}

class TemporaryDatabaseClassesProvider : DatabaseClassesProviderInterface, TemporaryDatabaseModule {
    private val classes = mutableListOf<SchoolClass>()
    private val pupilsOrdering = mutableMapOf<ClassID, List<Pair<UserID, Int>>>()
    private val subgroups = mutableMapOf<ClassID, List<Subgroup>>()
    override fun clearDatabase() {
        classes.clear()
        pupilsOrdering.clear()
        subgroups.clear()
    }

    override fun getClass(classID: ClassID): SchoolClass? {
        return classes.find { it.id == classID }
    }

    override fun getClasses(): List<SchoolClass> {
        return classes
    }

    override fun createClass(classData: SchoolClass) {
        classes.add(classData)
    }

    override fun batchCreateClasses(classes: List<SchoolClass>) {
        this.classes.addAll(classes)
    }

    override fun <T : Any> updateClass(classID: ClassID, field: Field<T>, value: T) {
        val snapshot = classes.find { it.id == classID } ?: return
        val newClass = when (field) {
            Field(SchoolClass::title) -> snapshot.copy(title = value as String)
            Field(SchoolClass::shift) -> snapshot.copy(shift = value as TeachingShift)
            else -> throw IllegalArgumentException("Field $field is not supported")
        }
        classes.remove(snapshot)
        classes.add(newClass)
    }

    override fun getPupilsOrdering(classID: ClassID): List<Pair<UserID, Int>> {
        return pupilsOrdering[classID] ?: emptyList()
    }

    override fun setPupilsOrdering(classID: ClassID, pupilsOrdering: List<Pair<UserID, Int>>) {
        this.pupilsOrdering[classID] = pupilsOrdering
    }

    override fun getSubgroups(classID: ClassID): List<Subgroup> {
        return subgroups[classID] ?: emptyList()
    }

}

class TemporaryDatabaseRolesProvider : DatabaseRolesProviderInterface, TemporaryDatabaseModule {
    private val _eventsFlow = MutableSharedFlow<DatabaseRolesProviderInterface.RoleEvent>()
    override val eventsFlow: SharedFlow<DatabaseRolesProviderInterface.RoleEvent>
        get() = _eventsFlow
    private val roles = mutableListOf<RoleData>()
    private val scope = CoroutineScope(Dispatchers.IO)
    override fun clearDatabase() {
        roles.clear()
    }

    override fun getRolesForUser(userID: UserID): List<RoleData> {
        return roles.filter { it.userID == userID }
    }

    override fun appendRoleToUser(userID: UserID, role: DatabaseRolesProviderInterface.RoleCreationData): RoleData {
        val roleData = RoleData(
            role = role.role,
            userID = userID,
            roleGrantedDateTime = role.creationDate,
            roleRevokedDateTime = role.expirationDate,
            additionalInformation = role.informationHolder,
            uniqueID = UUID.randomUUID().toString()
        )
        roles.add(roleData)
        scope.launch {
            _eventsFlow.emit(DatabaseRolesProviderInterface.RoleEvent(Event.EventType.CREATED, roleData, null))
        }
        return roleData
    }

    override fun batchAppendRolesToUsers(
        users: List<UserID>, roleGenerator: (UserID) -> DatabaseRolesProviderInterface.RoleCreationData
    ) {
        val roles = users.map { appendRoleToUser(it, roleGenerator(it)) }
        scope.launch {
            roles.forEach {
                _eventsFlow.emit(DatabaseRolesProviderInterface.RoleEvent(Event.EventType.CREATED, it, null))
            }
        }
    }

    override fun getAllRolesByType(type: Roles.Role): List<RoleData> {
        return roles.filter { it.role == type }
    }

    override fun getAllRolesByMatch(match: (RoleData) -> Boolean): List<RoleData> {
        return roles.filter(match)
    }

    override fun getAllRolesWithMatchingEntries(vararg entries: Pair<Roles.Role.Field<*>, Any?>): List<RoleData> {
        return roles.filter { role ->
            entries.all {
                @OptIn(UnsafeAPI::class)
                role.unsafeGetField(it.first) == it.second
            }
        }
    }

    override fun <T : Any> updateRole(roleID: String, field: Field<T>, value: T) {
        val role = roles.find { it.uniqueID == roleID } ?: return
        scope.launch {
            _eventsFlow.emit(DatabaseRolesProviderInterface.RoleEvent(Event.EventType.UPDATED, role, role))
        }
    }

    override fun revokeRole(roleID: String, revokeDateTime: LocalDateTime?) {
        val role = roles.find { it.uniqueID == roleID } ?: return
        roles.remove(role)
        val newRole = RoleData(
            role = role.role,
            userID = role.userID,
            roleGrantedDateTime = role.roleGrantedDateTime,
            roleRevokedDateTime = revokeDateTime ?: LocalDateTime.now(),
            additionalInformation = @OptIn(UnsafeAPI::class) role.getRoleInformationHolder(),
            uniqueID = role.uniqueID
        )
        roles.add(newRole)
        scope.launch {
            _eventsFlow.emit(DatabaseRolesProviderInterface.RoleEvent(Event.EventType.UPDATED, newRole, role))
        }
    }

    override fun triggerRolesUpdate() {
        //Do nothing
    }

}

class TemporaryDatabaseUserProvider : DatabaseUserProviderInterface, TemporaryDatabaseModule {
    private val users = mutableListOf<User>()
    override val eventsFlow: SharedFlow<DatabaseUserProviderInterface.UserEvent>
        get() = _eventsFlow
    private val _eventsFlow = MutableSharedFlow<DatabaseUserProviderInterface.UserEvent>()
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    override fun clearDatabase() {
        users.clear()
    }

    override fun getUser(userID: UserID): User? {
        return users.find { it.id == userID }
    }

    override fun getUsers(): List<User> {
        return users
    }

    override fun createUser(user: User) {
        users.add(user)
        coroutineScope.launch {
            _eventsFlow.tryEmit(
                DatabaseUserProviderInterface.UserEvent(
                    Event.EventType.CREATED, user, null
                )
            )
        }
    }

    override fun batchCreateUsers(users: List<User>) {
        this.users.addAll(users)
        coroutineScope.launch {
            users.forEach {
                _eventsFlow.tryEmit(
                    DatabaseUserProviderInterface.UserEvent(
                        Event.EventType.CREATED, it, null
                    )
                )
            }
        }
    }

    override fun <T : Any> updateUser(userID: UserID, field: Field<T>, value: T) {
        val user = users.find { it.id == userID } ?: throw IllegalArgumentException("User with id $userID not found")
        val oldUser = user.copy()
        when (field) {
            Field(User::name) -> {
                users.remove(user)
                users.add(user.copy(name = value as Name))
            }

            else -> throw IllegalArgumentException("Field $field is not supported")
        }
        coroutineScope.launch {
            _eventsFlow.tryEmit(
                DatabaseUserProviderInterface.UserEvent(
                    Event.EventType.UPDATED, user, oldUser
                )
            )
        }
    }

    override fun deleteUser(userID: UserID) {
        val user = users.find { it.id == userID } ?: throw IllegalArgumentException("User with id $userID not found")
        users.remove(user)
        coroutineScope.launch {
            _eventsFlow.tryEmit(
                DatabaseUserProviderInterface.UserEvent(
                    Event.EventType.DELETED, user, null
                )
            )
        }
    }

}
