/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 8/24/22, 7:50 PM
 */

package by.enrollie.routes

import by.enrollie.data_classes.Roles
import by.enrollie.exceptions.RateLimitException
import by.enrollie.extensions.isBetweenOrEqual
import by.enrollie.impl.ProvidersCatalog
import by.enrollie.plugins.UserPrincipal
import by.enrollie.util.parseDate
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.LocalDateTime

private fun Route.getClassId() {
    get("{classId}") {
        val user =
            call.authentication.principal<UserPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
        val classID = call.parameters["classId"]?.toIntOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest)
        val schoolClass =
            ProvidersCatalog.databaseProvider.classesProvider.getClass(classID) ?: return@get call.respond(
                HttpStatusCode.NotFound
            )
        ProvidersCatalog.authorization.authorize(user.getUserFromDB(), "read", schoolClass)
        call.respond(schoolClass)
    }
}

private fun Route.getClassIdClassTeachers() {
    get("{classId}/classTeachers") {
        val user =
            call.authentication.principal<UserPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
        val classID = call.parameters["classId"]?.toIntOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest)
        val date = call.parameters["date"]?.let {
            it.parseDate()?.atStartOfDay() ?: return@get call.respond(HttpStatusCode.BadRequest)
        } ?: LocalDateTime.now()
        val schoolClass =
            ProvidersCatalog.databaseProvider.classesProvider.getClass(classID) ?: return@get call.respond(
                HttpStatusCode.NotFound
            )
        ProvidersCatalog.authorization.authorize(user.getUserFromDB(), "read", schoolClass)
        call.respond(ProvidersCatalog.databaseProvider.rolesProvider.getAllRolesByMatch {
            it.role == Roles.CLASS.CLASS_TEACHER && it.getField(
                Roles.CLASS.CLASS_TEACHER.classID
            ) == classID && date.isBetweenOrEqual(
                it.roleGrantedDateTime, it.roleRevokedDateTime ?: date.plusYears(1)
            )
        }.let { roleDataList ->
            roleDataList.map { ProvidersCatalog.databaseProvider.usersProvider.getUser(it.userID) }
        })
    }
}

private fun Route.getClassIdStudents() {
    get("{classId}/students") {
        val user =
            call.authentication.principal<UserPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
        val classID = call.parameters["classId"]?.toIntOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest)
        val schoolClass =
            ProvidersCatalog.databaseProvider.classesProvider.getClass(classID) ?: return@get call.respond(
                HttpStatusCode.NotFound
            )
        val queryAll = call.request.queryParameters["queryAll"]?.toBooleanStrictOrNull()
        val studiedOn = call.request.queryParameters["studiedOn"]?.parseDate()
        if (queryAll != null && studiedOn != null) return@get call.respond(HttpStatusCode.BadRequest)
        ProvidersCatalog.authorization.authorize(user.getUserFromDB(), "read_students", schoolClass)
        val roles = if (queryAll == true) {
            ProvidersCatalog.databaseProvider.rolesProvider.getAllRolesByMatch {
                it.getField(Roles.CLASS.STUDENT.classID) == classID
            }
        } else if (studiedOn != null) {
            ProvidersCatalog.databaseProvider.rolesProvider.getAllRolesByMatch {
                it.getField(Roles.CLASS.STUDENT.classID) == classID && (it.roleGrantedDateTime.isBefore(studiedOn.atStartOfDay()) || it.roleGrantedDateTime.toLocalDate().isEqual(
                    studiedOn
                )) && (it.roleRevokedDateTime == null || it.roleRevokedDateTime?.isAfter(studiedOn.atStartOfDay()) == true)
            }
        } else {
            val currentTime = LocalDateTime.now()
            ProvidersCatalog.databaseProvider.rolesProvider.getAllRolesByMatch {
                it.getField(Roles.CLASS.STUDENT.classID) == classID && (it.roleGrantedDateTime.isBefore(currentTime) || it.roleGrantedDateTime.isEqual(
                    currentTime
                )) && (it.roleRevokedDateTime == null || it.roleRevokedDateTime?.isAfter(
                    currentTime
                ) == true)
            }
        }
        val users = roles.map { it.userID }.distinct().let { usersList ->
            ProvidersCatalog.databaseProvider.usersProvider.getUsers().filter { it.id in usersList }
        }
        call.respond(users)
    }
}

private fun Route.getClassIdSubgroups() {
    get("/{classId}/subgroups") {
        val user =
            call.authentication.principal<UserPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
        val classID = call.parameters["classId"]?.toIntOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest)
        val schoolClass =
            ProvidersCatalog.databaseProvider.classesProvider.getClass(classID) ?: return@get call.respond(
                HttpStatusCode.NotFound
            )
        ProvidersCatalog.authorization.authorize(user.getUserFromDB(), "read_students", schoolClass)
        return@get call.respond(ProvidersCatalog.databaseProvider.classesProvider.getSubgroups(schoolClass.id))
    }
}

private fun Route.getClassIdStudentsOrdering() {
    get("{classId}/students/ordering") {
        val user =
            call.authentication.principal<UserPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
        val classID = call.parameters["classId"]?.toIntOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest)
        val schoolClass =
            ProvidersCatalog.databaseProvider.classesProvider.getClass(classID) ?: return@get call.respond(
                HttpStatusCode.NotFound
            )
        ProvidersCatalog.authorization.authorize(user.getUserFromDB(), "read_students", schoolClass)
        call.respond(ProvidersCatalog.databaseProvider.classesProvider.getPupilsOrdering(classID))
    }
}

private fun Route.getClassIdLessons() {
    get("{classId}/lessons") {
        val user =
            call.authentication.principal<UserPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
        val classID = call.parameters["classId"]?.toIntOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest)
        val schoolClass =
            ProvidersCatalog.databaseProvider.classesProvider.getClass(classID) ?: return@get call.respond(
                HttpStatusCode.NotFound
            )
        val date =
            call.request.queryParameters["date"]?.parseDate() ?: return@get call.respond(HttpStatusCode.BadRequest)
        ProvidersCatalog.authorization.authorize(user.getUserFromDB(), "read_lessons", schoolClass)
        call.respond(ProvidersCatalog.databaseProvider.lessonsProvider.getLessonsForClass(classID, date))
    }
}

private fun Route.getClassIdSync() {
    get("{classId}/sync") {
        val user =
            call.authentication.principal<UserPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
        val classID = call.parameters["classId"]?.toIntOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest)
        val schoolClass =
            ProvidersCatalog.databaseProvider.classesProvider.getClass(classID) ?: return@get call.respond(
                HttpStatusCode.NotFound
            )
        ProvidersCatalog.authorization.authorize(user.getUserFromDB(), "request_sync", schoolClass)
        val syncID = try {
            ProvidersCatalog.registrarProvider.addClassToSyncQueue(classID) // We are not supplying credentials because data provider will find them by itself
        } catch (e: RateLimitException) {
            return@get call.respond(HttpStatusCode.TooManyRequests)
        }
        call.response.headers.append(
            HttpHeaders.Location,
            "${ProvidersCatalog.configuration.serverConfiguration.baseWebsocketUrl}/classSync/$syncID"
        )
        call.respond(HttpStatusCode.Accepted)
    }
}

internal fun Route.Class() {
    authenticate("jwt") {
        route("/class") {
            getClassId()
            getClassIdClassTeachers()
            getClassIdSubgroups()
            getClassIdStudents()
            getClassIdStudentsOrdering()
            getClassIdLessons()
            getClassIdSync()
        }
    }
}
