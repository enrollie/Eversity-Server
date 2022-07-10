/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/10/22, 11:16 PM
 */

package by.enrollie.impl

import by.enrollie.data_classes.*
import by.enrollie.providers.DataRegistrarProviderInterface
import by.enrollie.providers.ProvidersCatalog
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
import org.joda.time.DateTime
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class DataRegistrarImpl : DataRegistrarProviderInterface {
    private val uuidsMap = ConcurrentHashMap<String, Pair<UserID, Credentials>>()
    private val processingUsers = ConcurrentSet<UserID>()
    private val broadcaster = MutableSharedFlow<DataRegistrarProviderInterface.Message>(5, 500)
    private val scope = CoroutineScope(Dispatchers.IO)
    private val jobsBroadcaster = MutableSharedFlow<Triple<UserID, Credentials, String>>(0, 5)

    override val messagesBroadcast: SharedFlow<DataRegistrarProviderInterface.Message>
        get() = broadcaster

    override fun authenticateObserver(uuid: String): Boolean = uuidsMap.containsKey(uuid)

    override fun addToRegister(userID: UserID, schoolsByCredentials: Credentials): String {
        require(!processingUsers.contains(userID)) { "User is already being processed" }
        require(ProvidersCatalog.databaseProvider.usersProvider.getUser(userID) == null) { "User is already registered" }
        processingUsers.add(userID)
        val uuid = UUID.randomUUID().toString()
        uuidsMap[uuid] = Pair(userID, schoolsByCredentials)
        return uuid
    }

    private fun errorMessage(uuid: String, message: String? = null) = DataRegistrarProviderInterface.Message(
        uuid, DataRegistrarProviderInterface.MessageType.FAILURE, message ?: "Что-то пошло не так :(", 1, 1, mapOf()
    )

    init {
        val supervisor = SupervisorJob()
        val handler = CoroutineExceptionHandler { coroutineContext, throwable ->
            Sentry.captureException(throwable, Hint())
        }
        CoroutineScope(Dispatchers.IO + supervisor).launch {
            jobsBroadcaster.collect {
                launch(handler + SentryContext()) {
                    registerUser(it.first, it.second, it.third)
                }
            }
        }
    }

    private suspend fun registerUser(userID: UserID, credentials: Credentials, uuid: String) {
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
            SchoolsByUserType.TEACHER -> {
                broadcaster.emit(
                    DataRegistrarProviderInterface.Message(
                        uuid,
                        DataRegistrarProviderInterface.MessageType.INFORMATION,
                        "Судя по всему, вы учитель. Ищем ваш класс...",
                        2,
                        9,
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
                if (schoolClass != null) {
                    Sentry.addBreadcrumb(Breadcrumb.info("User with ID $userID is teacher in class $schoolClass"))
                    broadcaster.emit(
                        DataRegistrarProviderInterface.Message(
                            uuid,
                            DataRegistrarProviderInterface.MessageType.INFORMATION,
                            "Нашли ваш ${schoolClass.classTitle} класс, узнаём смену класса...",
                            3,
                            9,
                            mapOf()
                        )
                    )
                    val shift = SchoolsByParser.CLASS.getClassShift(schoolClass.id, credentials).fold({
                        it
                    }, {
                        Sentry.captureException(it)
                        broadcaster.emit(errorMessage(uuid))
                        return
                    })
                    Sentry.addBreadcrumb(Breadcrumb.info("Found shift of class ${schoolClass.id}: $shift"))
                    broadcaster.emit(
                        DataRegistrarProviderInterface.Message(
                            uuid,
                            DataRegistrarProviderInterface.MessageType.INFORMATION,
                            "${schoolClass.classTitle} класс, учится в${if (shift) "о второй смене" else " первой смене"}. Ищем учеников...",
                            4,
                            9,
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
                    Sentry.addBreadcrumb(Breadcrumb.info("There are ${pupils.size} pupils in class ${schoolClass.id}"))
                    broadcaster.emit(
                        DataRegistrarProviderInterface.Message(
                            uuid,
                            DataRegistrarProviderInterface.MessageType.INFORMATION,
                            "Нашли ${pupils.size} учеников, ищем их расположение в списке...",
                            5,
                            9,
                            mapOf()
                        )
                    )
                    val placing = SchoolsByParser.CLASS.getPupilsOrdering(schoolClass.id, credentials).fold({
                        it
                    }, {
                        Sentry.captureException(it)
                        broadcaster.emit(errorMessage(uuid))
                        return
                    })
                    broadcaster.emit(
                        DataRegistrarProviderInterface.Message(
                            uuid,
                            DataRegistrarProviderInterface.MessageType.INFORMATION,
                            "Нашли расположение учеников в списке. Ищем расписание класса...",
                            6,
                            9,
                            mapOf()
                        )
                    )
                    val timetable = SchoolsByParser.CLASS.getTimetable(schoolClass.id, credentials, true).fold({
                        it
                    }, {
                        Sentry.captureException(it)
                        broadcaster.emit(
                            errorMessage(uuid)
                        )
                        return
                    })
                    Sentry.addBreadcrumb(Breadcrumb.info("Found timetable for class ${schoolClass.id}"))
                    broadcaster.emit(
                        DataRegistrarProviderInterface.Message(
                            uuid,
                            DataRegistrarProviderInterface.MessageType.INFORMATION,
                            "Нашли расписание класса, регистрируем класс...",
                            7,
                            9,
                            mapOf()
                        )
                    )

                    ProvidersCatalog.databaseProvider.classesProvider.apply {
                        createClass(
                            SchoolClass(
                                schoolClass.id,
                                schoolClass.classTitle,
                                if (shift) TeachingShift.SECOND else TeachingShift.FIRST
                            )
                        )
                    }
                    ProvidersCatalog.databaseProvider.usersProvider.batchCreateUsers(pupils.map {
                        User(
                            it.id,
                            Name.fromParserName(it.name)
                        )
                    })
                    ProvidersCatalog.databaseProvider.rolesProvider.batchAppendRolesToUsers(pupils.map { it.id }) {
                        RoleData(
                            it,
                            Roles.CLASS.STUDENT,
                            mapOf(Roles.CLASS.STUDENT.classID to schoolClass.id),
                            DateTime.now(),
                            null
                        )
                    }
                }
            }
            SchoolsByUserType.ADMINISTRATION -> TODO()
        }
    }
}
