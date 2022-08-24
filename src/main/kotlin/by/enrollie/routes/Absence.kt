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
import by.enrollie.serializers.LocalDateSerializer
import by.enrollie.util.RoleUtil
import by.enrollie.util.parseDate
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import java.time.LocalDate

@kotlinx.serialization.Serializable
internal data class AbsencesResponse(val absences: List<AbsenceRecord>, val noDataDates: List<String>)

private fun Route.AbsenceClassIDGet() {
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
                return@get call.respond(HttpStatusCode.BadRequest)
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
                        .distinct()
                ProvidersCatalog.databaseProvider.absenceProvider.getDatesWithoutAbsenceInfo(classID,
                    (absences.minOfOrNull { it.absenceDate } ?: LocalDate.now().minusMonths(9)) to LocalDate.now())
                    .filter { it !in datesWithLessons }
            }
        }
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

private fun validateLessonsList(lessonsList: List<TimetablePlace>, classID: ClassID, date: LocalDate): Boolean {
    val lessons =
        ProvidersCatalog.databaseProvider.lessonsProvider.getLessonsForClass(classID, date).map { it.placeInTimetable }
            .distinct()
    return lessonsList.none { it !in lessons }
}

private fun Route.AbsenceClassIDPut() {
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
        if (!validateLessonsList(body.lessonsList, classID, body.date)) {
            return@put call.respond(HttpStatusCode.BadRequest)
        }
        ProvidersCatalog.databaseProvider.rolesProvider.getAllRolesWithMatchingEntries(Roles.CLASS.STUDENT.classID to classID)
            .firstOrNull {
                it.userID == body.studentID && body.date.isBetweenOrEqual(
                    it.roleGrantedDateTime.toLocalDate(),
                    it.roleRevokedDateTime?.toLocalDate() ?: LocalDate.now().plusYears(1)
                )
            } ?: return@put call.respond(HttpStatusCode.NotFound)
        val fittingRole = RoleUtil.findRoleToWriteAbsence(
            ProvidersCatalog.databaseProvider.rolesProvider.getRolesForUser(user.userID), classID
        ) ?: return@put call.respond(HttpStatusCode.Forbidden)
        val result = kotlin.runCatching {
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
        }
        if (result.isFailure) {
            if (result.exceptionOrNull() is AbsenceRecordsConflictException) {
                val existingRecord =
                    ProvidersCatalog.databaseProvider.absenceProvider.getAbsencesForClass(classID, body.date)
                        .first { it.student.id == body.studentID }
                return@put call.respond(HttpStatusCode.Conflict, existingRecord)
            } else {
                throw result.exceptionOrNull()!!
            }
        } else call.respond(result.getOrThrow())
    }
}

@kotlinx.serialization.Serializable
internal data class AbsencePatchRequest(
    @SerialName("absenceId") val absenceID: AbsenceID,
    val absenceType: AbsenceType? = null,
    val lessonsList: List<TimetablePlace>? = null
)

private fun Route.AbsenceClassIDPatch() {
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
        val absence =
            ProvidersCatalog.databaseProvider.absenceProvider.getAbsence(body.absenceID) ?: return@patch call.respond(
                HttpStatusCode.NotFound
            )
        if (body.lessonsList == null && body.absenceType == null) {
            return@patch call.respond(HttpStatusCode.OK, absence) // lol
        }
        if (body.lessonsList != null) {
            if (!validateLessonsList(body.lessonsList, schoolClass.id, absence.absenceDate)) {
                return@patch call.respond(HttpStatusCode.BadRequest)
            }
            ProvidersCatalog.databaseProvider.absenceProvider.updateAbsence(
                fittingRole.uniqueID, absence.id, Field(AbsenceRecord::lessonsList), body.lessonsList
            )
        }
        if (body.absenceType != null) {
            kotlin.runCatching {
                ProvidersCatalog.databaseProvider.absenceProvider.updateAbsence(
                    fittingRole.uniqueID, body.absenceID, Field(AbsenceRecord::absenceType), body.absenceType
                )
            }.fold({}, {
                throw it
            })
        }
        call.respond(
            ProvidersCatalog.databaseProvider.absenceProvider.getAbsence(body.absenceID) // 5 database calls in best case and 8 in worst case :(
                ?: return@patch call.respond(HttpStatusCode.NotFound)
        )
    }
}

private fun Route.AbsenceClassIDEmptyData() {
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
            fittingRole.uniqueID,
            schoolClass.id,
            date
        )
        call.respond(HttpStatusCode.OK)
    }
}

internal fun Route.absence() {
    authenticate("jwt") {
        route("/absence") {
            AbsenceClassIDGet()
            AbsenceClassIDPut()
            AbsenceClassIDPatch()
            AbsenceClassIDEmptyData()
        }
    }
}
