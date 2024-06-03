/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 8/24/22, 7:50 PM
 */

package by.enrollie.impl

import by.enrollie.data_classes.*
import by.enrollie.data_classes.Lesson
import by.enrollie.data_classes.Name
import by.enrollie.data_classes.SchoolClass
import by.enrollie.data_classes.Subgroup
import by.enrollie.data_classes.User
import by.enrollie.exceptions.RateLimitException
import by.enrollie.extensions.fromParserName
import by.enrollie.plugins.jwtProvider
import by.enrollie.privateProviders.EnvironmentInterface
import by.enrollie.providers.DataSourceCommunicatorInterface
import by.enrollie.providers.DatabaseProviderInterface
import by.enrollie.providers.DatabaseRolesProviderInterface
import by.enrollie.util.getTimedWelcome
import com.neitex.*
import io.ktor.util.collections.*
import io.sentry.Breadcrumb
import io.sentry.Hint
import io.sentry.Sentry
import io.sentry.kotlin.SentryContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.slf4j.MDCContext
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class DataSourceCommunicatorImpl(
    private val database: DatabaseProviderInterface, private val environment: EnvironmentInterface
) : DataSourceCommunicatorInterface {
    private val registerUUIDs = ConcurrentHashMap<String, Pair<UserID, Credentials>>(100)
    private val usersJobsBroadcaster = MutableSharedFlow<Triple<UserID, Credentials, String>>(0, 5)
    private val processingUsers = ConcurrentSet<UserID>()

    private val syncUUIDs = ConcurrentHashMap<String, Pair<ClassID, Credentials?>>(55)
    private val processingClasses = ConcurrentSet<ClassID>()
    private val classSyncJobsBroadcaster = MutableSharedFlow<Triple<ClassID, Credentials?, String>>(0, 5)
    private val rateLimitClassList = ConcurrentSet<ClassID>()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val scope = CoroutineScope(Dispatchers.IO.limitedParallelism(1))
    private val broadcaster = MutableSharedFlow<DataSourceCommunicatorInterface.Message>(15, 500)
    private val logger = LoggerFactory.getLogger(DataSourceCommunicatorImpl::class.java)

    override val messagesBroadcast: SharedFlow<DataSourceCommunicatorInterface.Message>
        get() = broadcaster

    override fun authenticateObserver(uuid: String): Boolean =
        registerUUIDs.containsKey(uuid) || syncUUIDs.containsKey(uuid)

    override fun addToRegister(userID: UserID, schoolsByCredentials: Credentials): String {
        if (processingUsers.contains(userID)) {
            return registerUUIDs.entries.first { it.value.first == userID }.key
        }
        require(database.usersProvider.getUser(userID) == null) { "User is already registered" }
        processingUsers.add(userID)
        val uuid = "u-${UUID.randomUUID()}"
        registerUUIDs[uuid] = Pair(userID, schoolsByCredentials)
        scope.launch {
            usersJobsBroadcaster.emit(Triple(userID, schoolsByCredentials, uuid))
        }
        logger.debug("Added user $userID to register queue")
        return uuid
    }

    private fun rateLimitClass(classID: ClassID): Boolean {
        if (rateLimitClassList.contains(classID)) return false
        rateLimitClassList.add(classID)
        scope.launch {
            delay(900000L) // 15 minutes
            rateLimitClassList.remove(classID)
        }
        return true
    }

    override fun addClassToSyncQueue(classID: ClassID, schoolsByCredentials: Credentials?): String {
        if (processingClasses.contains(classID)) {
            return syncUUIDs.entries.first { it.value.first == classID }.key
        }
        if (!rateLimitClass(classID)) throw RateLimitException("Class has already been synced recently")
        processingClasses.add(classID)
        val uuid = "c-${UUID.randomUUID()}"
        syncUUIDs[uuid] = Pair(classID, schoolsByCredentials)
        scope.launch {
            classSyncJobsBroadcaster.emit(Triple(classID, schoolsByCredentials, uuid))
        }
        logger.debug("Added class $classID to sync queue")
        return uuid
    }

    private fun errorMessage(uuid: String, message: String? = null, exception: Throwable? = null) =
        DataSourceCommunicatorInterface.Message(
            uuid,
            DataSourceCommunicatorInterface.MessageType.FAILURE,
            message
                ?: "Что-то пошло не так :(${if (environment.environmentType.verboseLogging()) "\nОшибка: ${exception.toString()}" else ""}",
            1,
            1,
            mapOf()
        )

    init {
        val handler = CoroutineExceptionHandler { _, throwable ->
            Sentry.captureException(throwable, Hint())
            logger.error("Seems like coroutine was cancelled", throwable)
        } + SupervisorJob()
        (scope + handler).launch {
            usersJobsBroadcaster.collect {
                delay(500)
                launch(handler + SentryContext() + MDCContext(mapOf("uuid" to it.third, "userID" to it.first.toString()))) {
                    try {
                        registerUser(it.first, it.second, it.third)
                    } catch (e: Exception) {
                        logger.error("Error while registering user ${it.first} (uncaught)", e)
                        Sentry.captureException(e, Hint())
                        broadcaster.emit(errorMessage(it.third, exception = e))
                    } finally {
                        registerUUIDs.remove(it.third)
                        processingUsers.remove(it.first)
                    }
                }
            }
        }
        (scope + handler).launch {
            classSyncJobsBroadcaster.collect {
                launch(
                    handler + SentryContext() + MDCContext(
                        mapOf(
                            "uuid" to it.third, "classID" to it.first.toString()
                        )
                    )
                ) {
                    logger.debug("Syncing class ${it.first}")
                    try {
                        syncClass(it.first, it.second, it.third)
                        logger.debug("Class ${it.first} has been synced")
                    } catch (e: Exception) {
                        logger.error("Error while syncing class ${it.first} (uuid=${it.third}, uncaught)", e)
                        Sentry.captureException(e, Hint())
                        broadcaster.emit(errorMessage(it.third, exception = e))
                        rateLimitClassList.remove(it.first)
                    } finally {
                        syncUUIDs.remove(it.third)
                        processingClasses.remove(it.first)
                    }
                }
                delay(200)
            }
        }
    }

    private suspend fun getAppropriateCredentials(
        classID: ClassID, ignoredCredentials: List<Credentials>
    ): Credentials? {
        val classTeacherRoles = database.rolesProvider.getAllRolesByMatch {
            it.role == Roles.CLASS.CLASS_TEACHER && it.getField(Roles.CLASS.CLASS_TEACHER.classID) == classID && it.roleRevokedDateTime == null
        }
        for (roleData in classTeacherRoles) {
            val cookies = database.customCredentialsProvider.getCredentials(
                roleData.userID, "schools-csrfToken"
            ) to database.customCredentialsProvider.getCredentials(
                roleData.userID, "schools-sessionID"
            )
            if (cookies.first != null && cookies.second != null) {
                val credentials = Credentials(cookies.first!!, cookies.second!!)
                if (credentials in ignoredCredentials) {
                    continue
                }
                SchoolsByParser.AUTH.checkCookies(credentials).fold({ valid ->
                    if (valid) return credentials else {
                        database.runInSingleTransaction { database ->
                            database.customCredentialsProvider.clearCredentials(
                                roleData.userID, "schools-csrfToken"
                            )
                            database.customCredentialsProvider.clearCredentials(
                                roleData.userID, "schools-sessionID"
                            )
                        }
                    }
                }, {
                    logger.error(
                        "Error while checking cookies for class teacher ${roleData.userID} of class $classID", it
                    )
                    if (it !is SchoolsByUnavailable) Sentry.captureException(it, Hint())
                })
            }
        }
        val administrationRoles = database.rolesProvider.getAllRolesByMatch {
            it.role == Roles.SCHOOL.ADMINISTRATION && it.roleRevokedDateTime == null
        }
        for (roleData in administrationRoles) {
            val cookies = database.runInSingleTransaction { database ->
                database.customCredentialsProvider.getCredentials(
                    roleData.userID, "schools-csrfToken"
                ) to database.customCredentialsProvider.getCredentials(
                    roleData.userID, "schools-sessionID"
                )
            }
            if (cookies.first != null && cookies.second != null) {
                val credentials = Credentials(cookies.first!!, cookies.second!!)
                if (credentials in ignoredCredentials) {
                    continue
                }
                SchoolsByParser.AUTH.checkCookies(credentials).fold({ valid ->
                    if (valid) return credentials else {
                        logger.debug("Credentials {} are not valid", credentials)
                        database.customCredentialsProvider.clearCredentials(
                            roleData.userID, "schools-csrfToken"
                        )
                        database.customCredentialsProvider.clearCredentials(
                            roleData.userID, "schools-sessionID"
                        )
                    }
                }, {
                    logger.error(
                        "Error while checking cookies for administrator ${roleData.userID}", it
                    )
                    if (it !is SchoolsByUnavailable) Sentry.captureException(it, Hint())
                })
            }
        }
        return null
    }

    private fun emitBroadcastMessage(message: DataSourceCommunicatorInterface.Message) = scope.launch {
        broadcaster.emit(message)
    }

    private fun invalidateCredentialsForClassTeachers(classID: ClassID) {
        database.runInSingleTransaction { database ->
            database.rolesProvider.getAllRolesByMatch {
                it.role == Roles.CLASS.CLASS_TEACHER && it.getField(Roles.CLASS.CLASS_TEACHER.classID) == classID && it.roleRevokedDateTime == null
            }.map { it.userID }.forEach { user ->
                database.authenticationDataProvider.revokeAllTokens(user)
            }
        }
    }

    private suspend fun syncClass(classID: ClassID, optionalCredentials: Credentials?, uuid: String) {
        logger.debug("Syncing class $classID (uuid=$uuid)")
        val badCredentialsMessage = "Учётные данные не подошли, пытаемся найти другие..."
        val attemptedCredentials = mutableListOf<Credentials>()
        val totalSteps = 6
        while (true) {
            var step = 1
            fun <T : Any?> errorHandler(
                userMsg: String, errorMsgFmt: String, vararg fmt: Any
            ): (Throwable) -> T? {
                return f@{
                    if (it is BadSchoolsByCredentials) {
                        logger.warn("Bad credentials for class $classID")
                        emitBroadcastMessage(
                            DataSourceCommunicatorInterface.Message(
                                uuid,
                                DataSourceCommunicatorInterface.MessageType.INFORMATION,
                                badCredentialsMessage,
                                step,
                                totalSteps,
                                mapOf()
                            )
                        )
                        return@f null
                    }
                    logger.error(errorMsgFmt.format(fmt), it)
                    emitBroadcastMessage(errorMessage(uuid, userMsg, it))
                    null
                }
            }
            delay(500)
            MDC.put("step", step.toString())
            MDC.put("totalSteps", totalSteps.toString())
            broadcaster.emit(
                DataSourceCommunicatorInterface.Message(
                    uuid,
                    DataSourceCommunicatorInterface.MessageType.INFORMATION,
                    "Ищем подходящие учётные данные...",
                    step,
                    totalSteps,
                    mapOf()
                )
            )
            val credentials =
                if (optionalCredentials != null && optionalCredentials !in attemptedCredentials) optionalCredentials else getAppropriateCredentials(
                    classID, attemptedCredentials
                )
            if (credentials == null) {
                logger.error("No credentials found")
                broadcaster.emit(
                    errorMessage(
                        uuid, "Не удалось найти подходящие учетные данные для синхронизации класса"
                    )
                )
                invalidateCredentialsForClassTeachers(classID)
                return
            }
            attemptedCredentials.add(credentials)
            val clazz = database.classesProvider.getClass(classID) ?: throw IllegalArgumentException(
                "Class $classID not found"
            )
            broadcaster.emit(
                DataSourceCommunicatorInterface.Message(
                    uuid,
                    DataSourceCommunicatorInterface.MessageType.INFORMATION,
                    "Получение основной информации о классе...",
                    step++,
                    totalSteps,
                    mapOf()
                )
            )
            val newClassData = SchoolsByParser.CLASS.getClassData(classID, credentials).fold({
                    if (it.classTitle != clazz.title) database.classesProvider.updateClass(
                        classID, Field(SchoolClass::title), it.classTitle
                    )
                    // We are replacing class teachers later (near the end of this function)
                    it
                }, errorHandler(
                    "Не удалось получить данные о классе. Попробуйте связаться с администратором системы.",
                    "Failed to get class"
                )
            ) ?: continue
            SchoolsByParser.CLASS.getClassShift(classID, credentials).fold({
                if (it) TeachingShift.SECOND else TeachingShift.FIRST
            }, errorHandler("Не удалось получить смену класса", "Failed to get teaching shift"))?.also {
                step++
                database.classesProvider.updateClass(classID, Field(SchoolClass::shift), it)
            } ?: continue
            broadcaster.emit(
                DataSourceCommunicatorInterface.Message(
                    uuid,
                    DataSourceCommunicatorInterface.MessageType.INFORMATION,
                    "Получение нового списка учеников...",
                    step++,
                    totalSteps,
                    mapOf()
                )
            )
            val subgroups = SchoolsByParser.CLASS.getSubgroups(classID, credentials).fold({ list ->
                database.classesProvider.getSubgroups(classID).let { existing ->
                    val mappedExisting = existing.associateBy { it.id }
                    list.filter { mappedExisting.containsKey(it.subgroupID) && mappedExisting[it.subgroupID]!!.title != it.title }
                        .takeIf { it.isNotEmpty() }?.forEach {
                            database.lessonsProvider.updateSubgroup(
                                it.subgroupID, Field(Subgroup::title), it.title
                            )
                        }
                    val new = list.filter { !mappedExisting.containsKey(it.subgroupID) }.takeIf { it.isNotEmpty() }
                        ?.let { subgroupList ->
                            database.lessonsProvider.createSubgroups(subgroupList.map {
                                Subgroup(
                                    it.subgroupID, it.title, classID, listOf()
                                )
                            })
                            subgroupList
                        }
                    val mappedNew = list.associateBy { it.subgroupID }
                    val mappedDeleted = existing.filter { !mappedNew.containsKey(it.id) }.takeIf { it.isNotEmpty() }
                        ?.let { subgroupList ->
                            subgroupList.forEach {
                                database.lessonsProvider.deleteSubgroup(it.id)
                            }
                            subgroupList.map { it.id }.toSet()
                        } ?: setOf()
                    existing.filter { it.id !in mappedDeleted }.plus(new ?: listOf())
                }
                list
            }, errorHandler("Не удалось получить данные подгрупп.", "Failed to get subgroups"))
                ?.distinctBy { it.subgroupID } ?: continue
            SchoolsByParser.CLASS.getPupilsList(classID, credentials).fold({ pupilsList ->
                val existingUsers = database.usersProvider.getUsers().map { it.id }.toHashSet()
                pupilsList.filterNot { it.id in existingUsers }.takeIf { it.isNotEmpty() }?.let { pupilList ->
                    database.usersProvider.batchCreateUsers(pupilList.map {
                        User(
                            it.id, Name.fromParserName(it.name)
                        )
                    })
                }
                val existingRoles = database.rolesProvider.getAllRolesByMatch {
                    it.role == Roles.CLASS.STUDENT && it.getField(Roles.CLASS.STUDENT.classID) == classID && it.roleRevokedDateTime == null
                }.map { it.userID }
                pupilsList.filterNot { it.id in existingRoles }.takeIf { it.isNotEmpty() }?.let {
                    database.rolesProvider.batchAppendRolesToUsers(it.map { it.id }) { id ->
                        DatabaseRolesProviderInterface.RoleCreationData(
                            id, Roles.CLASS.STUDENT, RoleInformationHolder(Roles.CLASS.STUDENT.classID to classID)
                        )
                    }
                }
                val mapped = pupilsList.map { it.id }.toHashSet()
                database.rolesProvider.getAllRolesByMatch {
                    it.role == Roles.CLASS.STUDENT && it.getField(Roles.CLASS.STUDENT.classID) == classID && it.roleRevokedDateTime == null && it.userID !in mapped
                }.forEach {
                    database.rolesProvider.revokeRole(it.uniqueID, LocalDateTime.now())
                }
            }, errorHandler("Не удалось получить список учащихся", "Failed to get students list")) ?: continue
            database.classesProvider.getSubgroups(classID).let { subgroupList ->
                subgroupList.forEach { subgroup ->
                    if (subgroups.first { it.subgroupID == subgroup.id }.pupils != subgroup.members) {
                        database.lessonsProvider.updateSubgroup(
                            subgroup.id,
                            Field(Subgroup::members),
                            subgroups.first { it.subgroupID == subgroup.id }.pupils.distinct()
                        )
                    }
                }
            }
            broadcaster.emit(
                DataSourceCommunicatorInterface.Message(
                    uuid,
                    DataSourceCommunicatorInterface.MessageType.INFORMATION,
                    "Получение нового списка уроков...",
                    step++,
                    totalSteps,
                    mapOf()
                )
            )
            SchoolsByParser.CLASS.getAllLessons(
                classID, subgroups.associate { it.subgroupID to it.title }, credentials
            ).fold({ list ->
                val mappedIDs = list.filterNot { it.teacher == null }.map { it.lessonID.toULong() }.toHashSet()
                database.lessonsProvider.createOrUpdateLessons(list.filterNot { it.teacher == null }.map {
                    Lesson(
                        it.lessonID.toULong(),
                        it.title,
                        it.date,
                        it.place,
                        it.teacher!!,
                        classID,
                        it.journalID!!,
                        it.subgroup
                    )
                })
                database.lessonsProvider.getLessonsForClass(classID).filter { it.id !in mappedIDs }
                    .also { deletedLessons ->
                        database.lessonsProvider.deleteLessons(deletedLessons.map { it.id })
                    }
                list
            }, errorHandler("Не удалось получить список уроков.", "Failed to get lessons list")) ?: continue
            SchoolsByParser.CLASS.getPupilsOrdering(classID, credentials).fold(
                { ordering ->
                    database.classesProvider.setPupilsOrdering(classID, ordering.map { it.first to it.second.toInt() })
                }, errorHandler(
                    "Не удалось получить порядок учащихся в классе. Убедитесь, что он существует и он правильный",
                    "Failed to get students ordering"
                )
            ) ?: continue
            broadcaster.emit(
                DataSourceCommunicatorInterface.Message(
                    uuid,
                    DataSourceCommunicatorInterface.MessageType.INFORMATION,
                    "Почти готово...",
                    step++,
                    totalSteps,
                    mapOf()
                )
            )
            database.rolesProvider.getAllRolesByMatch {
                it.role == Roles.CLASS.CLASS_TEACHER && it.getField(Roles.CLASS.CLASS_TEACHER.classID) == classID && it.roleRevokedDateTime == null
            }.also { rolesList ->
                if (rolesList.none { it.userID == newClassData.classTeacherID }) {
                    rolesList.forEach {
                        database.rolesProvider.revokeRole(it.uniqueID, LocalDateTime.now())
                    }
                    database.rolesProvider.appendRoleToUser(
                        newClassData.classTeacherID, DatabaseRolesProviderInterface.RoleCreationData(
                            newClassData.classTeacherID,
                            Roles.CLASS.CLASS_TEACHER,
                            RoleInformationHolder(Roles.CLASS.CLASS_TEACHER.classID to classID)
                        )
                    )
                } else if (rolesList.size > 1) {
                    rolesList.filter { it.userID != newClassData.classTeacherID }.forEach {
                        database.rolesProvider.revokeRole(it.uniqueID, LocalDateTime.now())
                    }
                }
            }
            broadcaster.emit(
                DataSourceCommunicatorInterface.Message(
                    uuid, DataSourceCommunicatorInterface.MessageType.SUCCESS, "Готово!", step++, totalSteps, mapOf()
                )
            )
            break
        }
        MDC.clear()
    }

    private suspend fun registerUser(userID: UserID, credentials: Credentials, uuid: String) {
        Sentry.addBreadcrumb(Breadcrumb.debug("Beginning registration with ID $uuid"))
        logger.debug("Beginning registration")
        val user = SchoolsByParser.USER.getBasicUserInfo(userID, credentials).fold({
            broadcaster.emit(
                DataSourceCommunicatorInterface.Message(
                    uuid,
                    DataSourceCommunicatorInterface.MessageType.INFORMATION,
                    getTimedWelcome(Name.fromParserName(it.name).shortForm),
                    1,
                    2,
                    mapOf(
                        "firstName" to it.name.firstName,
                        "middleName" to it.name.middleName,
                        "lastName" to it.name.lastName
                    )
                )
            )
            Sentry.addBreadcrumb(Breadcrumb.info("User with ID $userID is ${it.type} with name ${it.name}"))
            it
        }, {
            Sentry.captureException(it)
            broadcaster.emit(errorMessage(uuid, exception = it))
            logger.error("Error while getting user info (uuid=$uuid)", it)
            return
        })
        MDC.put("userType", user.type.name)
        when (user.type) {
            SchoolsByUserType.PARENT -> {
                broadcaster.emit(
                    errorMessage(
                        uuid,
                        "Простите, но сервис не имеет функционала для родителей",
                        Exception("User $userID is parent")
                    )
                )
                return
            }

            SchoolsByUserType.PUPIL -> {
                broadcaster.emit(
                    errorMessage(
                        uuid,
                        "Пожалуйста, попросите вашего классного руководителя синхронизировать данные класса и попробуйте снова.",
                        Exception("Pupil $userID tried to register")
                    )
                )
            }

            SchoolsByUserType.TEACHER, SchoolsByUserType.ADMINISTRATION, SchoolsByUserType.DIRECTOR -> {
                val totalSteps = 9
                var currentStep = 2
                broadcaster.emit(
                    DataSourceCommunicatorInterface.Message(
                        uuid, DataSourceCommunicatorInterface.MessageType.INFORMATION, "Судя по всему, вы ${
                            when (user.type) {
                                SchoolsByUserType.TEACHER -> "учитель"
                                SchoolsByUserType.ADMINISTRATION -> "часть администрации"
                                SchoolsByUserType.DIRECTOR -> "директор"
                                else -> throw IllegalStateException("The world is broken, unfortunately")
                            }
                        }. Ищем ваш класс...", currentStep, totalSteps, mapOf()
                    )
                )
                val schoolClass = SchoolsByParser.TEACHER.getClassForTeacher(userID, credentials, user.type).fold({
                    it
                }, {
                    Sentry.captureException(it)
                    broadcaster.emit(errorMessage(uuid, exception = it))
                    logger.error("Error while getting class info (uuid=$uuid)", it)
                    return
                })
                if (schoolClass != null && database.classesProvider.getClass(schoolClass.id) == null) {
                    currentStep++
                    Sentry.addBreadcrumb(Breadcrumb.info("User with ID $userID is teacher in class $schoolClass"))
                    broadcaster.emit(
                        DataSourceCommunicatorInterface.Message(
                            uuid,
                            DataSourceCommunicatorInterface.MessageType.INFORMATION,
                            "Нашли ваш ${schoolClass.classTitle} класс, узнаём смену класса...",
                            currentStep,
                            totalSteps,
                            mapOf()
                        )
                    )
                    val shift = SchoolsByParser.CLASS.getClassShift(schoolClass.id, credentials).fold({
                        if (it) TeachingShift.SECOND else TeachingShift.FIRST
                    }, {
                        Sentry.captureException(it)
                        broadcaster.emit(errorMessage(uuid, exception = it))
                        logger.error("Error while getting class shift (uuid=$uuid, classID=${schoolClass.id})", it)
                        return
                    })
                    currentStep++
                    Sentry.addBreadcrumb(Breadcrumb.info("Found shift of class ${schoolClass.id}: $shift"))
                    broadcaster.emit(
                        DataSourceCommunicatorInterface.Message(
                            uuid,
                            DataSourceCommunicatorInterface.MessageType.INFORMATION,
                            "${schoolClass.classTitle} класс, учится в${if (shift == TeachingShift.SECOND) "о второй смене" else " первой смене"}. Ищем учеников...",
                            currentStep,
                            totalSteps,
                            mapOf()
                        )
                    )
                    val pupils = SchoolsByParser.CLASS.getPupilsList(schoolClass.id, credentials).fold({
                        it
                    }, {
                        Sentry.captureException(it)
                        broadcaster.emit(errorMessage(uuid, exception = it))
                        logger.error("Error while getting pupils list (uuid=$uuid, classID=${schoolClass.id})", it)
                        return
                    })
                    currentStep++
                    Sentry.addBreadcrumb(Breadcrumb.info("There are ${pupils.size} pupils in class ${schoolClass.id}"))
                    broadcaster.emit(
                        DataSourceCommunicatorInterface.Message(
                            uuid,
                            DataSourceCommunicatorInterface.MessageType.INFORMATION,
                            "Нашли ${pupils.size} учеников, ищем их расположение в списке...",
                            currentStep,
                            totalSteps,
                            mapOf()
                        )
                    )
                    val ordering = SchoolsByParser.CLASS.getPupilsOrdering(schoolClass.id, credentials).fold({
                        it
                    }, {
                        Sentry.captureException(it)
                        broadcaster.emit(errorMessage(uuid, exception = it))
                        logger.error("Error while getting pupils ordering (uuid=$uuid, classID=${schoolClass.id})", it)
                        return
                    })
                    currentStep++
                    Sentry.addBreadcrumb(Breadcrumb.info("Found ordering of class ${schoolClass.id}"))
                    broadcaster.emit(
                        DataSourceCommunicatorInterface.Message(
                            uuid,
                            DataSourceCommunicatorInterface.MessageType.INFORMATION,
                            "Нашли расположение учеников в списке. Ищем расписание класса...",
                            currentStep,
                            totalSteps,
                            mapOf()
                        )
                    )
                    val subgroups = SchoolsByParser.CLASS.getSubgroups(schoolClass.id, credentials).fold({
                        it
                    }, {
                        Sentry.captureException(it)
                        broadcaster.emit(errorMessage(uuid, exception = it))
                        logger.error("Error while getting subgroups (uuid=$uuid, classID=${schoolClass.id})", it)
                        return
                    })
                    val lessons = SchoolsByParser.CLASS.getAllLessons(
                        schoolClass.id, subgroups.associate { it.subgroupID to it.title }, credentials
                    ).fold({ lessonList ->
                        lessonList.filterNot { it.journalID == null || it.teacher == null }.map {
                            Lesson(
                                it.lessonID.toULong(),
                                it.title,
                                it.date,
                                it.place,
                                it.teacher!!,
                                schoolClass.id,
                                it.journalID!!,
                                it.subgroup
                            )
                        }
                    }, {
                        Sentry.captureException(it)
                        broadcaster.emit(errorMessage(uuid, exception = it))
                        logger.error("Error while getting lessons (uuid=$uuid, classID=${schoolClass.id})", it)
                        return
                    })
                    val journalTitles = lessons.associate { it.journalID to it.title }
                    currentStep++
                    Sentry.addBreadcrumb(Breadcrumb.info("Found timetable for class ${schoolClass.id}"))
                    broadcaster.emit(
                        DataSourceCommunicatorInterface.Message(
                            uuid,
                            DataSourceCommunicatorInterface.MessageType.INFORMATION,
                            "Нашли расписание класса, ищем историю перемещений учеников между классами...",
                            currentStep,
                            totalSteps,
                            mapOf()
                        )
                    )
                    val transfers =
                        SchoolsByParser.CLASS.getTransfers(schoolClass.id, credentials).fold({ transfersMap ->
                            transfersMap.filter { it.key in pupils.map { pupil -> pupil.id } }
                                .map { Pair(it.key, it.value.last()) }.toMap()
                        }, {
                            Sentry.captureException(it)
                            broadcaster.emit(errorMessage(uuid, exception = it))
                            logger.error("Error while getting transfers (uuid=$uuid, classID=${schoolClass.id})", it)
                            return
                        })
                    Sentry.addBreadcrumb(Breadcrumb.info("Found transfers for class ${schoolClass.id}"))
                    broadcaster.emit(
                        DataSourceCommunicatorInterface.Message(
                            uuid,
                            DataSourceCommunicatorInterface.MessageType.INFORMATION,
                            "Нашли историю перемещений учеников между классами, регистрируем класс...",
                            currentStep,
                            totalSteps,
                            mapOf()
                        )
                    )
                    database.runInSingleTransaction { database ->
                        database.classesProvider.apply {
                            createClass(
                                SchoolClass(
                                    schoolClass.id, schoolClass.classTitle, shift
                                )
                            )
                            setPupilsOrdering(schoolClass.id, ordering.map { it.first to it.second.toInt() })
                        }
                        database.usersProvider.batchCreateUsers(pupils.map {
                            User(
                                it.id, Name.fromParserName(it.name)
                            )
                        } + User(userID, Name.fromParserName(user.name)))
                        database.rolesProvider.batchAppendRolesToUsers(pupils.map { it.id }) { pupil ->
                            DatabaseRolesProviderInterface.RoleCreationData(
                                pupil, Roles.CLASS.STUDENT, RoleInformationHolder(
                                    Roles.CLASS.STUDENT.classID to schoolClass.id
                                ), transfers[pupil]?.second?.atStartOfDay() ?: LocalDateTime.now()
                            )
                        }
                        if (database.rolesProvider.getAllRolesWithMatchingEntries(Roles.CLASS.CLASS_TEACHER.classID to schoolClass.id)
                                .firstOrNull { it.userID == userID && it.roleRevokedDateTime == null } == null
                        ) database.rolesProvider.appendRoleToUser(
                            userID, DatabaseRolesProviderInterface.RoleCreationData(
                                userID,
                                Roles.CLASS.CLASS_TEACHER,
                                RoleInformationHolder(Roles.CLASS.CLASS_TEACHER.classID to schoolClass.id)
                            )
                        )
                        database.lessonsProvider.apply {
                            setJournalTitles(journalTitles)
                            createSubgroups(subgroups.map {
                                Subgroup(
                                    it.subgroupID, it.title, schoolClass.id, it.pupils
                                )
                            })
                            createOrUpdateLessons(lessons)
                        }
                    }
                } else if (schoolClass != null && database.classesProvider.getClass(schoolClass.id) != null) {
                    currentStep = 8
                    Sentry.addBreadcrumb(Breadcrumb.info("Class ${schoolClass.id} already exists"))
                    broadcaster.emit(
                        DataSourceCommunicatorInterface.Message(
                            uuid,
                            DataSourceCommunicatorInterface.MessageType.INFORMATION,
                            "Ваш ${schoolClass.classTitle} уже зарегистрирован, назначаем вас руководителем...",
                            currentStep,
                            totalSteps,
                            mapOf()
                        )
                    )
                    database.runInSingleTransaction { database ->
                        database.usersProvider.createUser(
                            User(
                                userID, Name.fromParserName(user.name)
                            )
                        )
                        database.rolesProvider.appendRoleToUser(
                            userID, DatabaseRolesProviderInterface.RoleCreationData(
                                userID,
                                Roles.CLASS.CLASS_TEACHER,
                                RoleInformationHolder(Roles.CLASS.CLASS_TEACHER.classID to schoolClass.id)
                            )
                        )
                    }
                } else database.usersProvider.createUser(
                    User(
                        userID, Name.fromParserName(user.name)
                    )
                ) // Create only the user without creating anything else
                broadcaster.emit(
                    DataSourceCommunicatorInterface.Message(
                        uuid,
                        DataSourceCommunicatorInterface.MessageType.INFORMATION,
                        "Завершаем регистрацию...",
                        totalSteps,
                        totalSteps,
                        mapOf()
                    )
                )
                if (user.type == SchoolsByUserType.ADMINISTRATION || user.type == SchoolsByUserType.DIRECTOR) {
                    Sentry.addBreadcrumb(Breadcrumb.info("User is a part of school administration"))
                    if (environment.environmentType.verboseLogging()) logger.debug("User is a part of school administration (uuid=$uuid, userID=$userID), creating administration role")
                    database.rolesProvider.appendRoleToUser(
                        userID, DatabaseRolesProviderInterface.RoleCreationData(
                            userID, Roles.SCHOOL.ADMINISTRATION, RoleInformationHolder()
                        )
                    )
                }
                val token = database.runInSingleTransaction {
                    database.customCredentialsProvider.setCredentials(
                        userID, "schools-csrfToken", credentials.csrfToken
                    )
                    database.customCredentialsProvider.setCredentials(
                        userID, "schools-sessionID", credentials.sessionID
                    )
                    database.authenticationDataProvider.generateNewToken(userID)
                }
                broadcaster.emit(
                    DataSourceCommunicatorInterface.Message(
                        uuid,
                        DataSourceCommunicatorInterface.MessageType.AUTHENTICATION,
                        "Регистрация завершена, добро пожаловать!",
                        totalSteps,
                        totalSteps,
                        mapOf(
                            "userId" to userID.toString(), "token" to jwtProvider.signToken(userID, token.token)
                        )
                    )
                )
                logger.info("Registration completed for user ${user.id} (uuid: $uuid)")
                return
            }
        }
    }
}
