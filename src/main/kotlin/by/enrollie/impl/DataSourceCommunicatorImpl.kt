/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 8/24/22, 7:50 PM
 */

package by.enrollie.impl

import by.enrollie.data_classes.*
import by.enrollie.exceptions.RateLimitException
import by.enrollie.extensions.fromParserName
import by.enrollie.plugins.jwtProvider
import by.enrollie.providers.DataSourceCommunicatorInterface
import by.enrollie.providers.DatabaseRolesProviderInterface
import by.enrollie.util.getTimedWelcome
import com.neitex.BadSchoolsByCredentials
import com.neitex.Credentials
import com.neitex.SchoolsByParser
import com.neitex.SchoolsByUserType
import io.ktor.util.collections.*
import io.sentry.Breadcrumb
import io.sentry.Hint
import io.sentry.Sentry
import io.sentry.kotlin.SentryContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class DataSourceCommunicatorImpl : DataSourceCommunicatorInterface {
    private val registerUUIDs = ConcurrentHashMap<String, Pair<UserID, Credentials>>(100)
    private val usersJobsBroadcaster = MutableSharedFlow<Triple<UserID, Credentials, String>>(0, 5)
    private val processingUsers = ConcurrentSet<UserID>()

    private val syncUUIDs = ConcurrentHashMap<String, Pair<ClassID, Credentials?>>(55)
    private val processingClasses = ConcurrentSet<ClassID>()
    private val classSyncJobsBroadcaster = MutableSharedFlow<Triple<ClassID, Credentials?, String>>(0, 5)
    private val rateLimitClassList = ConcurrentSet<ClassID>()

    private val scope = CoroutineScope(Dispatchers.IO)
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
        require(ProvidersCatalog.databaseProvider.usersProvider.getUser(userID) == null) { "User is already registered" }
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
        if (rateLimitClassList.contains(classID))
            return false
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
        if (!rateLimitClass(classID))
            throw RateLimitException("Class has already been synced recently")
        processingClasses.add(classID)
        val uuid = "c-${UUID.randomUUID()}"
        syncUUIDs[uuid] = Pair(classID, schoolsByCredentials)
        scope.launch {
            classSyncJobsBroadcaster.emit(Triple(classID, schoolsByCredentials, uuid))
        }
        logger.debug("Added class $classID to sync queue")
        return uuid
    }

    private fun errorMessage(uuid: String, message: String? = null) = DataSourceCommunicatorInterface.Message(
        uuid, DataSourceCommunicatorInterface.MessageType.FAILURE, message ?: "Что-то пошло не так :(", 1, 1, mapOf()
    )

    init {
        val handler = CoroutineExceptionHandler { _, throwable ->
            Sentry.captureException(throwable, Hint())
            logger.error("Seems like coroutine was cancelled", throwable)
        } + SupervisorJob()
        (scope + handler).launch {
            usersJobsBroadcaster.collect {
                delay(500)
                launch(handler + SentryContext()) {
                    try {
                        registerUser(it.first, it.second, it.third)
                    } catch (e: Exception) {
                        logger.error("Error while registering user ${it.first} (uuid=${it.third}, uncaught)", e)
                        Sentry.captureException(e, Hint())
                        broadcaster.emit(errorMessage(it.third))
                    } finally {
                        registerUUIDs.remove(it.third)
                        processingUsers.remove(it.first)
                    }
                }
            }
        }
        (scope + handler).launch {
            classSyncJobsBroadcaster.collect {
                logger.debug("Syncing class ${it.first} (uuid=${it.third})")
                delay(500)
                launch(handler + SentryContext()) {
                    try {
                        syncClass(it.first, it.second, it.third)
                    } catch (e: Exception) {
                        logger.error("Error while syncing class ${it.first} (uuid=${it.third}, uncaught)", e)
                        Sentry.captureException(e, Hint())
                        broadcaster.emit(errorMessage(it.third))
                        rateLimitClassList.remove(it.first)
                    } finally {
                        logger.debug("Class ${it.first} has been synced")
                        syncUUIDs.remove(it.third)
                        processingClasses.remove(it.first)
                    }
                }
            }
        }
    }

    private suspend fun getAppropriateCredentials(
        classID: ClassID, ignoredCredentials: List<Credentials>
    ): Credentials? {
        val classTeacherRoles = ProvidersCatalog.databaseProvider.rolesProvider.getAllRolesByMatch {
            it.role == Roles.CLASS.CLASS_TEACHER && it.getField(Roles.CLASS.CLASS_TEACHER.classID) == classID && it.roleRevokedDateTime == null
        }
        for (roleData in classTeacherRoles) {
            val cookies = ProvidersCatalog.databaseProvider.customCredentialsProvider.getCredentials(
                roleData.userID, "schools-csrfToken"
            ) to ProvidersCatalog.databaseProvider.customCredentialsProvider.getCredentials(
                roleData.userID, "schools-sessionID"
            )
            if (cookies.first != null && cookies.second != null) {
                val credentials = Credentials(cookies.first!!, cookies.second!!)
                if (credentials in ignoredCredentials) {
                    continue
                }
                SchoolsByParser.AUTH.checkCookies(credentials).fold({ valid ->
                    if (valid) return credentials else {
                        ProvidersCatalog.databaseProvider.customCredentialsProvider.clearCredentials(
                            roleData.userID, "schools-csrfToken"
                        )
                        ProvidersCatalog.databaseProvider.customCredentialsProvider.clearCredentials(
                            roleData.userID, "schools-sessionID"
                        )
                    }
                }, {
                    logger.error(
                        "Error while checking cookies for class teacher ${roleData.userID} of class $classID", it
                    )
                    Sentry.captureException(it, Hint())
                })
            }
        }
        for (roleData in ProvidersCatalog.databaseProvider.rolesProvider.getAllRolesByMatch {
            it.role == Roles.SCHOOL.ADMINISTRATION && it.roleRevokedDateTime == null
        }) {
            val cookies = ProvidersCatalog.databaseProvider.customCredentialsProvider.getCredentials(
                roleData.userID, "schools-csrfToken"
            ) to ProvidersCatalog.databaseProvider.customCredentialsProvider.getCredentials(
                roleData.userID, "schools-sessionID"
            )
            if (cookies.first != null && cookies.second != null) {
                val credentials = Credentials(cookies.first!!, cookies.second!!)
                if (credentials in ignoredCredentials) {
                    continue
                }
                SchoolsByParser.AUTH.checkCookies(credentials).fold({ valid ->
                    if (valid) return credentials else {
                        logger.debug("Credentials $credentials are not valid")
                        ProvidersCatalog.databaseProvider.customCredentialsProvider.clearCredentials(
                            roleData.userID, "schools-csrfToken"
                        )
                        ProvidersCatalog.databaseProvider.customCredentialsProvider.clearCredentials(
                            roleData.userID, "schools-sessionID"
                        )
                    }
                }, {
                    logger.error(
                        "Error while checking cookies for administrator ${roleData.userID}", it
                    )
                    Sentry.captureException(it, Hint())
                })
            }
        }
        return null
    }

    private fun invalidateCredentialsForClassTeachers(classID: ClassID) {
        ProvidersCatalog.databaseProvider.rolesProvider.getAllRolesByMatch {
            it.role == Roles.CLASS.CLASS_TEACHER && it.getField(Roles.CLASS.CLASS_TEACHER.classID) == classID && it.roleRevokedDateTime == null
        }.map { it.userID }.forEach { user ->
            ProvidersCatalog.databaseProvider.authenticationDataProvider.getUserTokens(user).forEach {
                ProvidersCatalog.databaseProvider.authenticationDataProvider.revokeToken(it)
            }
        }
    }


    private suspend fun syncClass(classID: ClassID, optionalCredentials: Credentials?, uuid: String) {
        logger.debug("Syncing class $classID (uuid=$uuid)")
        val badCredentialsMessage = "Учётные данные не подошли, пытаемся найти другие..."
        val attemptedCredentials = mutableListOf<Credentials>()
        val totalSteps = 6
        while (true) {
            delay(500)
            var step = 1
            logger.debug("Syncing class $classID (uuid=$uuid): step $step/$totalSteps")
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
                logger.error("No credentials found for class $classID")
                broadcaster.emit(
                    errorMessage(
                        uuid, "Не удалось найти подходящие учетные данные для синхронизации класса"
                    )
                )
                invalidateCredentialsForClassTeachers(classID)
                return
            }
            attemptedCredentials.add(credentials)
            val clazz =
                ProvidersCatalog.databaseProvider.classesProvider.getClass(classID) ?: throw IllegalArgumentException(
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
                if (it.classTitle != clazz.title) ProvidersCatalog.databaseProvider.classesProvider.updateClass(
                    classID, Field(SchoolClass::title), it.classTitle
                )
                it
            }, {
                if (it is BadSchoolsByCredentials) {
                    logger.error("Bad credentials for class $classID")
                    broadcaster.emit(errorMessage(uuid, badCredentialsMessage))
                    return@fold null
                }
                logger.error("Error while getting class data for class $classID", it)
                broadcaster.emit(
                    errorMessage(
                        uuid, "Ошибка при получении основной информации о классе"
                    )
                )
                null
            }) ?: continue
            SchoolsByParser.CLASS.getClassShift(classID, credentials).fold({
                if (it) TeachingShift.SECOND else TeachingShift.FIRST
            }, {
                if (it is BadSchoolsByCredentials) {
                    logger.error("Bad credentials for class $classID")
                    broadcaster.emit(errorMessage(uuid, badCredentialsMessage))
                    invalidateCredentialsForClassTeachers(classID)
                    return@fold null
                }
                Sentry.captureException(it)
                broadcaster.emit(errorMessage(uuid))
                logger.error("Error while getting class shift (uuid=$uuid, classID=${classID})", it)
                return@fold null
            })?.also {
                step++
                ProvidersCatalog.databaseProvider.classesProvider.updateClass(classID, Field(SchoolClass::shift), it)
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
                ProvidersCatalog.databaseProvider.classesProvider.getSubgroups(classID).let { existing ->
                    val mappedExisting = existing.associateBy { it.id }
                    list.filter { mappedExisting.containsKey(it.subgroupID) && mappedExisting[it.subgroupID]!!.title != it.title }
                        .takeIf { it.isNotEmpty() }?.forEach {
                            ProvidersCatalog.databaseProvider.lessonsProvider.updateSubgroup(
                                it.subgroupID,
                                Field(Subgroup::title),
                                it.title
                            )
                        }
                    val new = list.filter { !mappedExisting.containsKey(it.subgroupID) }.takeIf { it.isNotEmpty() }
                        ?.let { subgroupList ->
                            ProvidersCatalog.databaseProvider.lessonsProvider.createSubgroups(subgroupList.map {
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
                                ProvidersCatalog.databaseProvider.lessonsProvider.deleteSubgroup(it.id)
                            }
                            subgroupList.map { it.id }.toSet()
                        } ?: setOf()
                    existing.filter { it.id !in mappedDeleted }.plus(new ?: listOf())
                }
                list
            }, {
                if (it is BadSchoolsByCredentials) {
                    logger.error("Bad credentials for class $classID")
                    broadcaster.emit(errorMessage(uuid, badCredentialsMessage))
                    invalidateCredentialsForClassTeachers(classID)
                    return@fold null
                }
                Sentry.captureException(it)
                broadcaster.emit(errorMessage(uuid))
                logger.error("Error while getting subgroups (uuid=$uuid, classID=${classID})", it)
                null
            })?.distinctBy { it.subgroupID } ?: continue
            SchoolsByParser.CLASS.getPupilsList(classID, credentials).fold({ pupilsList ->
                val existingUsers = ProvidersCatalog.databaseProvider.usersProvider.getUsers().map { it.id }.toHashSet()
                pupilsList.filterNot { it.id in existingUsers }.takeIf { it.isNotEmpty() }?.let { pupilList ->
                    ProvidersCatalog.databaseProvider.usersProvider.batchCreateUsers(pupilList.map {
                        User(
                            it.id, Name.fromParserName(it.name)
                        )
                    })
                }
                val existingRoles = ProvidersCatalog.databaseProvider.rolesProvider.getAllRolesByMatch {
                    it.role == Roles.CLASS.STUDENT && it.getField(Roles.CLASS.STUDENT.classID) == classID && it.roleRevokedDateTime == null
                }.map { it.userID }
                pupilsList.filterNot { it.id in existingRoles }.takeIf { it.isNotEmpty() }?.let {
                    ProvidersCatalog.databaseProvider.rolesProvider.batchAppendRolesToUsers(it.map { it.id }) { id ->
                        DatabaseRolesProviderInterface.RoleCreationData(
                            id, Roles.CLASS.STUDENT, RoleInformationHolder(Roles.CLASS.STUDENT.classID to classID)
                        )
                    }
                }
                val mapped = pupilsList.map { it.id }.toHashSet()
                ProvidersCatalog.databaseProvider.rolesProvider.getAllRolesByMatch {
                    it.role == Roles.CLASS.STUDENT && it.getField(Roles.CLASS.STUDENT.classID) == classID && it.roleRevokedDateTime == null && it.userID !in mapped
                }.forEach {
                    ProvidersCatalog.databaseProvider.rolesProvider.revokeRole(it.uniqueID, LocalDateTime.now())
                }
            }, {
                if (it is BadSchoolsByCredentials) {
                    logger.error("Bad credentials for class $classID")
                    broadcaster.emit(errorMessage(uuid, badCredentialsMessage))
                    invalidateCredentialsForClassTeachers(classID)
                    return@fold null
                }
                Sentry.captureException(it)
                broadcaster.emit(errorMessage(uuid))
                logger.error("Error while getting pupils list (uuid=$uuid, classID=${classID})", it)
                null
            }) ?: continue
            ProvidersCatalog.databaseProvider.classesProvider.getSubgroups(classID).let { subgroupList ->
                subgroupList.forEach { subgroup ->
                    if (subgroups.first { it.subgroupID == subgroup.id }.pupils != subgroup.members) {
                        ProvidersCatalog.databaseProvider.lessonsProvider.updateSubgroup(
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
                ProvidersCatalog.databaseProvider.lessonsProvider.createOrUpdateLessons(list.map {
                    Lesson(
                        it.lessonID.toULong(),
                        it.title,
                        it.date,
                        it.place,
                        it.teacher,
                        classID,
                        it.journalID!!,
                        it.subgroup
                    )
                })
                list
            }, {
                if (it is BadSchoolsByCredentials) {
                    logger.error("Bad credentials for class $classID")
                    broadcaster.emit(errorMessage(uuid, badCredentialsMessage))
                    invalidateCredentialsForClassTeachers(classID)
                    return@fold null
                }
                Sentry.captureException(it)
                broadcaster.emit(errorMessage(uuid))
                logger.error("Error while getting lessons (uuid=$uuid, classID=${classID})", it)
                null
            }) ?: continue
            SchoolsByParser.CLASS.getPupilsOrdering(classID, credentials).fold({ ordering ->
                ProvidersCatalog.databaseProvider.classesProvider.setPupilsOrdering(classID,
                    ordering.map { it.first to it.second.toInt() })
            }, {
                if (it is BadSchoolsByCredentials) {
                    logger.error("Bad credentials for class $classID")
                    broadcaster.emit(errorMessage(uuid, badCredentialsMessage))
                    invalidateCredentialsForClassTeachers(classID)
                    return@fold null
                }
                Sentry.captureException(it)
                broadcaster.emit(errorMessage(uuid))
                logger.error("Error while getting pupils ordering (uuid=$uuid, classID=${classID})", it)
                null
            }) ?: continue
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
            ProvidersCatalog.databaseProvider.rolesProvider.getAllRolesByMatch {
                it.role == Roles.CLASS.CLASS_TEACHER && it.getField(Roles.CLASS.CLASS_TEACHER.classID) == classID && it.roleRevokedDateTime == null
            }.also { rolesList ->
                if (rolesList.none { it.userID == newClassData.classTeacherID }) {
                    rolesList.forEach {
                        ProvidersCatalog.databaseProvider.rolesProvider.revokeRole(it.uniqueID, LocalDateTime.now())
                    }
                    ProvidersCatalog.databaseProvider.rolesProvider.appendRoleToUser(
                        newClassData.classTeacherID, DatabaseRolesProviderInterface.RoleCreationData(
                            newClassData.classTeacherID,
                            Roles.CLASS.CLASS_TEACHER,
                            RoleInformationHolder(Roles.CLASS.CLASS_TEACHER.classID to classID)
                        )
                    )
                } else if (rolesList.size > 1) {
                    rolesList.filter { it.userID != newClassData.classTeacherID }.forEach {
                        ProvidersCatalog.databaseProvider.rolesProvider.revokeRole(it.uniqueID, LocalDateTime.now())
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
    }

    private suspend fun registerUser(userID: UserID, credentials: Credentials, uuid: String) {
        Sentry.addBreadcrumb(Breadcrumb.debug("Beginning registration with ID $uuid"))
        logger.debug("Beginning registration with ID $uuid")

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
            broadcaster.emit(errorMessage(uuid))
            logger.error("Error while getting user info (uuid=$uuid)", it)
            return
        })
        when (user.type) {
            SchoolsByUserType.PARENT -> {
                broadcaster.emit(errorMessage(uuid, "Простите, но в этой системе нет функциональности для родителей"))
                return
            }

            SchoolsByUserType.PUPIL -> {
                broadcaster.emit(
                    errorMessage(
                        uuid,
                        "Пожалуйста, попросите вашего классного руководителя синхронизировать данные класса и попробуйте снова."
                    )
                )
            }

            SchoolsByUserType.TEACHER, SchoolsByUserType.ADMINISTRATION -> {
                val totalTeacherSteps = 9
                var currentStep = 2
                broadcaster.emit(
                    DataSourceCommunicatorInterface.Message(
                        uuid,
                        DataSourceCommunicatorInterface.MessageType.INFORMATION,
                        "Судя по всему, вы учитель. Ищем ваш класс...",
                        currentStep,
                        totalTeacherSteps,
                        mapOf()
                    )
                )
                val schoolClass = SchoolsByParser.TEACHER.getClassForTeacher(userID, credentials).fold({
                    it
                }, {
                    Sentry.captureException(it)
                    broadcaster.emit(errorMessage(uuid))
                    logger.error("Error while getting class info (uuid=$uuid)", it)
                    return
                })
                if (schoolClass != null && ProvidersCatalog.databaseProvider.classesProvider.getClass(schoolClass.id) == null) {
                    currentStep++
                    Sentry.addBreadcrumb(Breadcrumb.info("User with ID $userID is teacher in class $schoolClass"))
                    broadcaster.emit(
                        DataSourceCommunicatorInterface.Message(
                            uuid,
                            DataSourceCommunicatorInterface.MessageType.INFORMATION,
                            "Нашли ваш ${schoolClass.classTitle} класс, узнаём смену класса...",
                            currentStep,
                            totalTeacherSteps,
                            mapOf()
                        )
                    )
                    val shift = SchoolsByParser.CLASS.getClassShift(schoolClass.id, credentials).fold({
                        if (it) TeachingShift.SECOND else TeachingShift.FIRST
                    }, {
                        Sentry.captureException(it)
                        broadcaster.emit(errorMessage(uuid))
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
                            totalTeacherSteps,
                            mapOf()
                        )
                    )
                    val pupils = SchoolsByParser.CLASS.getPupilsList(schoolClass.id, credentials).fold({
                        it
                    }, {
                        Sentry.captureException(it)
                        broadcaster.emit(errorMessage(uuid))
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
                            totalTeacherSteps,
                            mapOf()
                        )
                    )
                    val ordering = SchoolsByParser.CLASS.getPupilsOrdering(schoolClass.id, credentials).fold({
                        it
                    }, {
                        Sentry.captureException(it)
                        broadcaster.emit(errorMessage(uuid))
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
                            totalTeacherSteps,
                            mapOf()
                        )
                    )
                    val subgroups = SchoolsByParser.CLASS.getSubgroups(schoolClass.id, credentials).fold({
                        it
                    }, {
                        Sentry.captureException(it)
                        broadcaster.emit(errorMessage(uuid))
                        logger.error("Error while getting subgroups (uuid=$uuid, classID=${schoolClass.id})", it)
                        return
                    })
                    val lessons = SchoolsByParser.CLASS.getAllLessons(
                        schoolClass.id, subgroups.associate { it.subgroupID to it.title }, credentials
                    ).fold({ lessonList ->
                        lessonList.filterNot { it.journalID == null }.map {
                            Lesson(
                                it.lessonID.toULong(),
                                it.title,
                                it.date,
                                it.place,
                                it.teacher,
                                schoolClass.id,
                                it.journalID!!,
                                it.subgroup
                            )
                        }
                    }, {
                        Sentry.captureException(it)
                        broadcaster.emit(
                            errorMessage(uuid)
                        )
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
                            totalTeacherSteps,
                            mapOf()
                        )
                    )
                    val transfers =
                        SchoolsByParser.CLASS.getTransfers(schoolClass.id, credentials).fold({ transfersMap ->
                            transfersMap.filter { it.key in pupils.map { pupil -> pupil.id } }
                                .map { Pair(it.key, it.value.last()) }.toMap()
                        }, {
                            Sentry.captureException(it)
                            broadcaster.emit(errorMessage(uuid))
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
                            totalTeacherSteps,
                            mapOf()
                        )
                    )
                    ProvidersCatalog.databaseProvider.classesProvider.apply {
                        createClass(
                            SchoolClass(
                                schoolClass.id, schoolClass.classTitle, shift
                            )
                        )
                        setPupilsOrdering(schoolClass.id, ordering.map { it.first to it.second.toInt() })
                    }
                    ProvidersCatalog.databaseProvider.usersProvider.batchCreateUsers(pupils.map {
                        User(
                            it.id, Name.fromParserName(it.name)
                        )
                    } + User(userID, Name.fromParserName(user.name)))
                    ProvidersCatalog.databaseProvider.rolesProvider.batchAppendRolesToUsers(pupils.map { it.id }) { pupil ->
                        DatabaseRolesProviderInterface.RoleCreationData(
                            pupil, Roles.CLASS.STUDENT, RoleInformationHolder(
                                Roles.CLASS.STUDENT.classID to schoolClass.id
                            ), transfers[pupil]?.second?.atStartOfDay() ?: LocalDateTime.now()
                        )
                    }
                    if (ProvidersCatalog.databaseProvider.rolesProvider.getAllRolesWithMatchingEntries(Roles.CLASS.CLASS_TEACHER.classID to schoolClass.id)
                            .firstOrNull { it.userID == userID && it.roleRevokedDateTime == null } == null
                    ) ProvidersCatalog.databaseProvider.rolesProvider.appendRoleToUser(
                        userID, DatabaseRolesProviderInterface.RoleCreationData(
                            userID,
                            Roles.CLASS.CLASS_TEACHER,
                            RoleInformationHolder(Roles.CLASS.CLASS_TEACHER.classID to schoolClass.id)
                        )
                    )
                    ProvidersCatalog.databaseProvider.lessonsProvider.apply {
                        setJournalTitles(journalTitles)
                        createSubgroups(subgroups.map { Subgroup(it.subgroupID, it.title, schoolClass.id, it.pupils) })
                        createOrUpdateLessons(lessons)
                    }
                } else if (schoolClass != null && ProvidersCatalog.databaseProvider.classesProvider.getClass(schoolClass.id) != null) {
                    currentStep = 8
                    Sentry.addBreadcrumb(Breadcrumb.info("Class ${schoolClass.id} already exists"))
                    broadcaster.emit(
                        DataSourceCommunicatorInterface.Message(
                            uuid,
                            DataSourceCommunicatorInterface.MessageType.INFORMATION,
                            "Ваш ${schoolClass.classTitle} уже зарегистрирован, назначаем вас руководителем...",
                            currentStep,
                            totalTeacherSteps,
                            mapOf()
                        )
                    )
                    ProvidersCatalog.databaseProvider.usersProvider.createUser(
                        User(
                            userID, Name.fromParserName(user.name)
                        )
                    )
                    ProvidersCatalog.databaseProvider.rolesProvider.appendRoleToUser(
                        userID, DatabaseRolesProviderInterface.RoleCreationData(
                            userID,
                            Roles.CLASS.CLASS_TEACHER,
                            RoleInformationHolder(Roles.CLASS.CLASS_TEACHER.classID to schoolClass.id)
                        )
                    )
                } else ProvidersCatalog.databaseProvider.usersProvider.createUser(
                    User(
                        userID, Name.fromParserName(user.name)
                    )
                ) // Create only the user without creating anything else
                broadcaster.emit(
                    DataSourceCommunicatorInterface.Message(
                        uuid,
                        DataSourceCommunicatorInterface.MessageType.INFORMATION,
                        "Завершаем регистрацию...",
                        totalTeacherSteps,
                        totalTeacherSteps,
                        mapOf()
                    )
                )
                if (user.type == SchoolsByUserType.ADMINISTRATION) {
                    Sentry.addBreadcrumb(Breadcrumb.info("User is a part of school administration"))
                    ProvidersCatalog.databaseProvider.rolesProvider.appendRoleToUser(
                        userID, DatabaseRolesProviderInterface.RoleCreationData(
                            userID, Roles.SCHOOL.ADMINISTRATION, RoleInformationHolder()
                        )
                    )
                }
                ProvidersCatalog.databaseProvider.customCredentialsProvider.setCredentials(
                    userID, "schools-csrfToken", credentials.csrfToken
                )
                ProvidersCatalog.databaseProvider.customCredentialsProvider.setCredentials(
                    userID, "schools-sessionID", credentials.sessionID
                )
                val token = ProvidersCatalog.databaseProvider.authenticationDataProvider.generateNewToken(userID)
                broadcaster.emit(
                    DataSourceCommunicatorInterface.Message(
                        uuid,
                        DataSourceCommunicatorInterface.MessageType.AUTHENTICATION,
                        "Регистрация завершена, добро пожаловать!",
                        totalTeacherSteps,
                        totalTeacherSteps,
                        mapOf(
                            "userId" to userID.toString(),
                            "token" to jwtProvider.signToken(User(userID, Name.fromParserName(user.name)), token.token)
                        )
                    )
                )
                logger.info("Registration completed for user ${user.id} (uuid: $uuid)")
                return
            }
        }
    }
}
