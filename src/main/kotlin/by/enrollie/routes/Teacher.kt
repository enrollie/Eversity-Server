/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 8/24/22, 7:50 PM
 */

package by.enrollie.routes

import by.enrollie.data_classes.Roles
import by.enrollie.impl.ProvidersCatalog
import by.enrollie.plugins.UserPrincipal
import by.enrollie.util.parseDate
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.LocalDate

private fun Route.TeacherUserIdLessons() {
    get("/{userId}/lessons") {
        val user = call.principal<UserPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
        val targetUser = call.parameters["userId"]?.toIntOrNull()?.let {
            ProvidersCatalog.databaseProvider.usersProvider.getUser(it)
                ?: return@get call.respond(HttpStatusCode.NotFound)
        } ?: return@get call.respond(HttpStatusCode.BadRequest)
        ProvidersCatalog.authorization.authorize(user.getUserFromDB(), "read_lessons", targetUser)
        val date = call.parameters["date"]?.let {
            it.parseDate() ?: return@get call.respond(HttpStatusCode.BadRequest)
        } ?: LocalDate.now()
        if (ProvidersCatalog.databaseProvider.rolesProvider.getRolesForUser(targetUser.id)
                .none { it.role == Roles.CLASS.TEACHER }
        ) {
            return@get call.respond(HttpStatusCode.BadRequest)
        }
        val lessons = ProvidersCatalog.databaseProvider.lessonsProvider.getLessonsForTeacher(targetUser.id, date)
        call.respond(lessons)
    }
}

internal fun Route.teacher() {
    authenticate("jwt") {
        route("/teacher") {
            TeacherUserIdLessons()
        }
    }
}
