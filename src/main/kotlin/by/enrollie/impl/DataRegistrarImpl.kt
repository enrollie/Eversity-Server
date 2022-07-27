/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/25/22, 11:31 PM
 */

package by.enrollie.impl

import by.enrollie.data_classes.*
import by.enrollie.extensions.fromParserName
import by.enrollie.plugins.jwtProvider
import by.enrollie.providers.DataRegistrarProviderInterface
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
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class DataRegistrarImpl : DataRegistrarProviderInterface {
    private val uuidsMap = ConcurrentHashMap<String, Pair<UserID, Credentials>>(100)
    private val processingUsers = ConcurrentSet<UserID>()
    private val broadcaster = MutableSharedFlow<DataRegistrarProviderInterface.Message>(15, 500)
    private val scope = CoroutineScope(Dispatchers.IO)
    private val jobsBroadcaster = MutableSharedFlow<Triple<UserID, Credentials, String>>(0, 5)

    override val messagesBroadcast: SharedFlow<DataRegistrarProviderInterface.Message>
        get() = broadcaster

    override fun authenticateObserver(uuid: String): Boolean = uuidsMap.containsKey(uuid)

    override fun addToRegister(userID: UserID, schoolsByCredentials: Credentials): String {
        if (processingUsers.contains(userID)) {
            return uuidsMap.entries.first { it.value.first == userID }.key
        }
        require(ProvidersCatalog.databaseProvider.usersProvider.getUser(userID) == null) { "User is already registered" }
        processingUsers.add(userID)
        val uuid = UUID.randomUUID().toString()
        uuidsMap[uuid] = Pair(userID, schoolsByCredentials)
        scope.launch {
            jobsBroadcaster.emit(Triple(userID, schoolsByCredentials, uuid))
        }
        return uuid
    }

    private fun errorMessage(uuid: String, message: String? = null) = DataRegistrarProviderInterface.Message(
        uuid, DataRegistrarProviderInterface.MessageType.FAILURE, message ?: "Что-то пошло не так :(", 1, 1, mapOf()
    )

    init {
        val supervisor = SupervisorJob()
        val handler = CoroutineExceptionHandler { _, throwable ->
            Sentry.captureException(throwable, Hint())
        }
        (scope + supervisor).launch {
            jobsBroadcaster.collect {
                launch(handler + SentryContext()) {
                    registerUser(it.first, it.second, it.third)
                }
            }
        }
    }

    private suspend fun registerUser(userID: UserID, credentials: Credentials, uuid: String) {
        Sentry.addBreadcrumb(Breadcrumb.debug("Beginning registration with ID $uuid"))
        val user = SchoolsByParser.USER.getBasicUserInfo(userID, credentials).fold({
            broadcaster.emit(
                DataRegistrarProviderInterface.Message(
                    uuid,
                    DataRegistrarProviderInterface.MessageType.NAME,
                    "Вы - ${Name.fromParserName(it.name).shortForm}",
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
                    DataRegistrarProviderInterface.Message(
                        uuid,
                        DataRegistrarProviderInterface.MessageType.INFORMATION,
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
                    return
                })
                if (schoolClass != null && ProvidersCatalog.databaseProvider.classesProvider.getClass(schoolClass.id) == null) {
                    currentStep++
                    Sentry.addBreadcrumb(Breadcrumb.info("User with ID $userID is teacher in class $schoolClass"))
                    broadcaster.emit(
                        DataRegistrarProviderInterface.Message(
                            uuid,
                            DataRegistrarProviderInterface.MessageType.INFORMATION,
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
                        return
                    })
                    currentStep++
                    Sentry.addBreadcrumb(Breadcrumb.info("Found shift of class ${schoolClass.id}: $shift"))
                    broadcaster.emit(
                        DataRegistrarProviderInterface.Message(
                            uuid,
                            DataRegistrarProviderInterface.MessageType.INFORMATION,
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
                        return
                    })
                    currentStep++
                    Sentry.addBreadcrumb(Breadcrumb.info("There are ${pupils.size} pupils in class ${schoolClass.id}"))
                    broadcaster.emit(
                        DataRegistrarProviderInterface.Message(
                            uuid,
                            DataRegistrarProviderInterface.MessageType.INFORMATION,
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
                        return
                    })
                    currentStep++
                    Sentry.addBreadcrumb(Breadcrumb.info("Found ordering of class ${schoolClass.id}"))
                    broadcaster.emit(
                        DataRegistrarProviderInterface.Message(
                            uuid,
                            DataRegistrarProviderInterface.MessageType.INFORMATION,
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
                        return
                    })
                    val lessons = SchoolsByParser.CLASS.getAllLessons(
                        schoolClass.id, subgroups.associate { it.subgroupID to it.title }, credentials
                    ).fold({
                        it.filterNot { it.journalID == null }.map {
                            Lesson(
                                it.lessonID,
                                it.title,
                                it.date,
                                it.place,
                                setOf(),
                                schoolClass.id,
                                it.journalID!!,
                                null // TODO: Add teachers and subgroups
                            )
                        }
                    }, {
                        Sentry.captureException(it)
                        broadcaster.emit(errorMessage(uuid))
                        return
                    })
                    val journalTitles = lessons.associate { it.journalID to it.title }
                    currentStep++
                    Sentry.addBreadcrumb(Breadcrumb.info("Found timetable for class ${schoolClass.id}"))
                    broadcaster.emit(
                        DataRegistrarProviderInterface.Message(
                            uuid,
                            DataRegistrarProviderInterface.MessageType.INFORMATION,
                            "Нашли расписание класса, ищем историю перемещений учеников между классами...",
                            currentStep,
                            totalTeacherSteps,
                            mapOf()
                        )
                    )
                    val transfers = SchoolsByParser.CLASS.getTransfers(schoolClass.id, credentials).fold({
                        it.filter { it.key in pupils.map { it.id } }.map { Pair(it.key, it.value.last()) }.toMap()
                    }, {
                        Sentry.captureException(it)
                        broadcaster.emit(errorMessage(uuid))
                        return
                    })
                    Sentry.addBreadcrumb(Breadcrumb.info("Found transfers for class ${schoolClass.id}"))
                    broadcaster.emit(
                        DataRegistrarProviderInterface.Message(
                            uuid,
                            DataRegistrarProviderInterface.MessageType.INFORMATION,
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
                        RoleData(pupil,
                            Roles.CLASS.STUDENT,
                            RoleInformationHolder(Roles.CLASS.STUDENT.classID to schoolClass.id,
                                Roles.CLASS.STUDENT.subgroups to subgroups.filter { it.pupils.contains(pupil) }
                                    .map { it.subgroupID }.toList()
                            ),
                            transfers[pupil]?.second?.atStartOfDay() ?: LocalDateTime.now(),
                            null
                        )
                    }
                    ProvidersCatalog.databaseProvider.rolesProvider.appendRoleToUser(
                        userID, RoleData(
                            userID,
                            Roles.CLASS.CLASS_TEACHER,
                            RoleInformationHolder(Roles.CLASS.CLASS_TEACHER.classID to schoolClass.id),
                            LocalDateTime.now(),
                            null
                        )
                    )
                    ProvidersCatalog.databaseProvider.lessonsProvider.apply {
                        setJournalTitles(journalTitles)
                        createOrUpdateLessons(lessons)
                    }
                } else if (schoolClass != null && ProvidersCatalog.databaseProvider.classesProvider.getClass(schoolClass.id) != null) {
                    currentStep = 8
                    Sentry.addBreadcrumb(Breadcrumb.info("Class ${schoolClass.id} already exists"))
                    broadcaster.emit(
                        DataRegistrarProviderInterface.Message(
                            uuid,
                            DataRegistrarProviderInterface.MessageType.INFORMATION,
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
                        userID, RoleData(
                            userID,
                            Roles.CLASS.CLASS_TEACHER,
                            RoleInformationHolder(Roles.CLASS.CLASS_TEACHER.classID to schoolClass.id),
                            LocalDateTime.now(),
                            null
                        )
                    )
                } else ProvidersCatalog.databaseProvider.usersProvider.createUser(
                    User(
                        userID, Name.fromParserName(user.name)
                    )
                ) // Create only the user without creating anything else
                broadcaster.emit(
                    DataRegistrarProviderInterface.Message(
                        uuid,
                        DataRegistrarProviderInterface.MessageType.INFORMATION,
                        "Завершаем регистрацию...",
                        totalTeacherSteps,
                        totalTeacherSteps,
                        mapOf()
                    )
                )
                if (user.type == SchoolsByUserType.ADMINISTRATION) {
                    Sentry.addBreadcrumb(Breadcrumb.info("User is a part of school administration"))
                    ProvidersCatalog.databaseProvider.rolesProvider.appendRoleToUser(
                        userID,
                        RoleData(
                            userID,
                            Roles.SCHOOL.ADMINISTRATION,
                            RoleInformationHolder(),
                            LocalDateTime.now(),
                            null
                        )
                    )
                }
                val token = ProvidersCatalog.databaseProvider.authenticationDataProvider.generateNewToken(userID)
                broadcaster.emit(
                    DataRegistrarProviderInterface.Message(
                        uuid,
                        DataRegistrarProviderInterface.MessageType.AUTHENTICATION,
                        "Регистрация завершена, добро пожаловать!",
                        totalTeacherSteps,
                        totalTeacherSteps,
                        mapOf(
                            "userId" to userID.toString(),
                            "token" to jwtProvider.signToken(User(userID, Name.fromParserName(user.name)), token.token)
                        )
                    )
                )
                return
            }
        }
    }
}
