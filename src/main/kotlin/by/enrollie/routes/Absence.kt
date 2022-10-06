/*
* Copyright © 2021 - 2022
* Author: Pavel Matusevich
* Licensed under GNU AGPLv3
* All rights are reserved.
* Last updated: 8/24/22, 7:50 PM
*/

package by.enrollie.routes

import by.enrollie.data_classes.*
import by.enrollie.exceptions.AbsenceRecordsConflictException
import by.enrollie.extensions.isBetweenOrEqual
import by.enrollie.impl.ProvidersCatalog
import by.enrollie.plugins.UserPrincipal
import by.enrollie.providers.DatabaseAbsenceProviderInterface
import by.enrollie.providers.DatabaseProviderInterface
import by.enrollie.serializers.LocalDateSerializer
import by.enrollie.util.RoleUtil
import by.enrollie.util.parseDate
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import org.slf4j.LoggerFactory
import java.time.DayOfWeek
import java.time.LocalDate

@kotlinx.serialization.Serializable
internal data class AbsencesResponse(val absences: List<AbsenceRecord>, val noDataDates: List<String>)

private fun Route.absenceClassIDGet() {
    get("/{classID}") {
        val user = call.principal<UserPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
        val classID = call.parameters["classID"]?.toIntOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest)
        ProvidersCatalog.authorization.authorize(
            user.getUserFromDB(), "read_absence", SchoolClass(classID, "", TeachingShift.FIRST)
        )
        val date = call.parameters["date"]?.let {
            it.parseDate() ?: return@get call.respond(HttpStatusCode.BadRequest)
        }
        val startDate = call.parameters["startDate"]?.let {
            it.parseDate() ?: return@get call.respond(HttpStatusCode.BadRequest)
        }
        val endDate = call.parameters["endDate"]?.let {
            it.parseDate() ?: return@get call.respond(HttpStatusCode.BadRequest)
        }.let {
            if (startDate != null && it == null) LocalDate.now()
            else it
        }
        if (date != null && (startDate != null || endDate != null)) {
            return@get call.respond(HttpStatusCode.BadRequest)
        }
        if (endDate != null && startDate == null) {
            return@get call.respond(HttpStatusCode.BadRequest)
        }
        if ((endDate != null) && (startDate != null) && (endDate < startDate)) {
            return@get call.respond(HttpStatusCode.BadRequest)
        }
        val (absences, datesWithoutInfo) = ProvidersCatalog.databaseProvider.runInSingleTransaction {
            val absences = when {
                date != null -> {
                    ProvidersCatalog.databaseProvider.absenceProvider.getAbsencesForClass(classID, date)
                }

                startDate != null && endDate != null -> {
                    ProvidersCatalog.databaseProvider.absenceProvider.getAbsencesForClass(classID, startDate to endDate)
                }

                startDate == null -> {
                    ProvidersCatalog.databaseProvider.absenceProvider.getAbsencesForClass(
                        classID, LocalDate.of(2020, 8, 9) to LocalDate.of(100_000_000, 1, 1)
                    )
                }

                else -> {
                    return@runInSingleTransaction Result.failure(IllegalArgumentException())
                }
            }
            val datesWithoutInfo = when {
                date != null -> {
                    ProvidersCatalog.databaseProvider.absenceProvider.getDatesWithoutAbsenceInfo(classID, date to date)
                }

                startDate != null && endDate != null -> {
                    ProvidersCatalog.databaseProvider.absenceProvider.getDatesWithoutAbsenceInfo(
                        classID, startDate to endDate
                    )
                }

                else -> {
                    val datesWithLessons =
                        ProvidersCatalog.databaseProvider.lessonsProvider.getLessonsForClass(classID).map { it.date }
                            .distinct().filter { it.dayOfWeek != DayOfWeek.SUNDAY }
                    ProvidersCatalog.databaseProvider.absenceProvider.getDatesWithoutAbsenceInfo(classID,
                        (absences.minOfOrNull { it.absenceDate } ?: LocalDate.now().minusMonths(9)) to LocalDate.now())
                        .filter { it !in datesWithLessons }
                }
            }
            Result.success(absences to datesWithoutInfo)
        }.fold(
            onSuccess = { it },
            onFailure = {
                if (it is IllegalArgumentException)
                    return@get call.respond(HttpStatusCode.BadRequest)
                else
                    return@get call.respond(HttpStatusCode.InternalServerError)
            }
        )
        call.respond(AbsencesResponse(absences, datesWithoutInfo.map { it.toString() }))
    }
}

@kotlinx.serialization.Serializable
internal data class PutAbsenceRequestBody(
    @SerialName("studentId") val studentID: UserID,
    @kotlinx.serialization.Serializable(with = LocalDateSerializer::class) val date: LocalDate,
    val absenceType: AbsenceType,
    val lessonsList: List<TimetablePlace>
)

private fun validateLessonsList(
    lessonsList: List<TimetablePlace>, classID: ClassID, date: LocalDate, database: DatabaseProviderInterface
): Boolean {
    val lessons = database.lessonsProvider.getLessonsForClass(classID, date).map { it.placeInTimetable }.distinct()
    return lessonsList.none { it !in lessons }
}

private val logger = LoggerFactory.getLogger("AbsenceRoute")

private data class AbsenceConflictException(val existingSubject: AbsenceRecord) : Exception()

private fun Route.absenceClassIDPut() {
    put("/{classID}") {
        val user = call.principal<UserPrincipal>() ?: return@put call.respond(HttpStatusCode.Unauthorized)
        val classID = call.parameters["classID"]?.toIntOrNull() ?: return@put call.respond(HttpStatusCode.BadRequest)
        ProvidersCatalog.authorization.authorize(
            user.getUserFromDB(), "edit_absence", SchoolClass(classID, "", TeachingShift.FIRST)
        )
        val body = call.receive<PutAbsenceRequestBody>()
        if (!body.date.isBetweenOrEqual(LocalDate.now().minusDays(3), LocalDate.now().plusDays(3))) {
            return@put call.respond(HttpStatusCode.BadRequest)
        }
        val result: Result<AbsenceRecord> = ProvidersCatalog.databaseProvider.runInSingleTransaction { database ->
            if (!validateLessonsList(body.lessonsList, classID, body.date, database)) {
                logger.debug("Request ${call.callId} does not have valid lessons list")
                return@runInSingleTransaction Result.failure(IllegalArgumentException())
            }
            database.rolesProvider.getAllRolesWithMatchingEntries(Roles.CLASS.STUDENT.classID to classID).firstOrNull {
                it.userID == body.studentID && body.date.isBetweenOrEqual(
                    it.roleGrantedDateTime.toLocalDate(),
                    it.roleRevokedDateTime?.toLocalDate() ?: LocalDate.now().plusYears(1)
                )
            } ?: return@runInSingleTransaction Result.failure<AbsenceRecord>(NoSuchElementException()).also {
                logger.debug("Request ${call.callId} returned 404 because subject student does not match the predicate")
            }
            val fittingRole = RoleUtil.findRoleToWriteAbsence(
                ProvidersCatalog.databaseProvider.rolesProvider.getRolesForUser(user.userID), classID
            ) ?: return@runInSingleTransaction Result.failure(IllegalAccessException())
            return@runInSingleTransaction runCatching {
                ProvidersCatalog.databaseProvider.absenceProvider.createAbsence(
                    DatabaseAbsenceProviderInterface.NewAbsenceRecord(
                        fittingRole.uniqueID,
                        classID,
                        body.studentID,
                        body.absenceType,
                        body.date,
                        body.lessonsList.distinct()
                    )
                )
            }.fold({ Result.success(it) }, { throwable ->
                return@fold if (throwable is AbsenceRecordsConflictException) {
                    val existingRecord =
                        ProvidersCatalog.databaseProvider.absenceProvider.getAbsencesForClass(classID, body.date)
                            .first { it.student.id == body.studentID }
                    Result.failure(AbsenceConflictException(existingRecord))
                } else Result.failure(throwable)
            })
        }
        if (result.isSuccess) {
            call.respond(HttpStatusCode.OK, result.getOrThrow())
        } else {
            when (val exception = result.exceptionOrNull()) {
                is AbsenceConflictException -> {
                    call.respond(HttpStatusCode.Conflict, exception.existingSubject)
                }

                is IllegalArgumentException -> {
                    call.respond(HttpStatusCode.BadRequest)
                }

                is IllegalAccessException -> {
                    call.respond(HttpStatusCode.Forbidden)
                }

                is NoSuchElementException -> {
                    call.respond(HttpStatusCode.NotFound)
                }

                null -> {
                    throw IllegalStateException("World seems to be broken, because result is not successful but exception is null")
                }

                else -> {
                    throw exception
                }
            }
        }
    }
}

@kotlinx.serialization.Serializable
internal data class AbsencePatchRequest(
    @SerialName("absenceId") val absenceID: AbsenceID,
    val absenceType: AbsenceType? = null,
    val lessonsList: List<TimetablePlace>? = null
)

private fun Route.absenceClassIDPatch() {
    patch("/{classID}") {
        val user = call.principal<UserPrincipal>() ?: return@patch call.respond(HttpStatusCode.Unauthorized)
        val schoolClass = call.parameters["classID"]?.toIntOrNull()?.let {
            ProvidersCatalog.databaseProvider.classesProvider.getClass(it)
                ?: return@patch call.respond(HttpStatusCode.NotFound)
        } ?: return@patch call.respond(HttpStatusCode.BadRequest)
        ProvidersCatalog.authorization.authorize(
            user.getUserFromDB(), "edit_absence", schoolClass
        )
        val fittingRole = RoleUtil.findRoleToWriteAbsence(
            ProvidersCatalog.databaseProvider.rolesProvider.getRolesForUser(user.userID), schoolClass.id
        ) ?: return@patch call.respond(HttpStatusCode.Forbidden)
        val body = call.receive<AbsencePatchRequest>()
        val responseAbsence = kotlin.runCatching {
            ProvidersCatalog.databaseProvider.runInSingleTransaction { database ->
                val absence = database.absenceProvider.getAbsence(body.absenceID)
                    ?: throw NoSuchElementException("No absence with ID ${body.absenceID} found")
                if (body.lessonsList == null && body.absenceType == null) {
                    return@runInSingleTransaction absence
                }
                if (body.lessonsList != null && body.lessonsList != absence.lessonsList) {
                    if (!validateLessonsList(body.lessonsList, schoolClass.id, absence.absenceDate, database)) {
                        throw IllegalArgumentException("Invalid lessons list")
                    }
                    database.absenceProvider.updateAbsence(
                        fittingRole.uniqueID, absence.id, Field(AbsenceRecord::lessonsList), body.lessonsList
                    )
                }
                if (body.absenceType != null && body.absenceType != absence.absenceType) {
                    kotlin.runCatching {
                        database.absenceProvider.updateAbsence(
                            fittingRole.uniqueID, body.absenceID, Field(AbsenceRecord::absenceType), body.absenceType
                        )
                    }.fold({}, {
                        throw it
                    })
                }
                return@runInSingleTransaction database.absenceProvider.getAbsence(body.absenceID)!!
            }
        }.fold({ it }, {
            when (it) {
                is NoSuchElementException -> {
                    return@patch call.respond(HttpStatusCode.NotFound)
                }

                is IllegalArgumentException -> {
                    return@patch call.respond(HttpStatusCode.BadRequest)
                }

                else -> {
                    throw it
                }
            }
        })
        call.respond(responseAbsence)
    }
}

private fun Route.absenceClassIDEmptyData() {
    post("/{classId}/emptyData") {
        val user = call.principal<UserPrincipal>() ?: return@post call.respond(HttpStatusCode.Unauthorized)
        val schoolClass = call.parameters["classId"]?.toIntOrNull()?.let {
            ProvidersCatalog.databaseProvider.classesProvider.getClass(it)
                ?: return@post call.respond(HttpStatusCode.NotFound)
        } ?: return@post call.respond(HttpStatusCode.BadRequest)
        ProvidersCatalog.authorization.authorize(
            user.getUserFromDB(), "edit_absence", schoolClass
        )
        val date = call.receive<Map<String, String>>()["date"]?.parseDate()
            ?: return@post call.respond(HttpStatusCode.BadRequest)
        if (!date.isBetweenOrEqual(LocalDate.now().minusDays(3), LocalDate.now().plusDays(3))) {
            return@post call.respond(HttpStatusCode.BadRequest)
        }
        val fittingRole = RoleUtil.findRoleToWriteAbsence(
            ProvidersCatalog.databaseProvider.rolesProvider.getRolesForUser(user.userID), schoolClass.id
        ) ?: return@post call.respond(HttpStatusCode.Forbidden)
        ProvidersCatalog.databaseProvider.absenceProvider.markClassAsDataRich(
            fittingRole.uniqueID, schoolClass.id, date
        )
        call.respond(HttpStatusCode.OK)
    }
}

internal fun Route.absence() {
    authenticate("jwt") {
        route("/absence") {
            absenceClassIDGet()
            absenceClassIDPut()
            absenceClassIDPatch()
            absenceClassIDEmptyData()
        }
    }
}
