/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/25/22, 2:58 PM
 */

package by.enrollie.routes

import by.enrollie.data_classes.UserID
import by.enrollie.impl.ProvidersCatalog
import by.enrollie.plugins.UserPrincipal
import by.enrollie.plugins.jwtProvider
import com.neitex.SchoolsByParser
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel

private fun Route.login() {
    post("/login") {
        data class LoginRequest(val username: String, val password: String)
        data class LoginResponse(val userId: UserID, val token: String)
        val (username, password) = call.receive<LoginRequest>()
        val result = SchoolsByParser.AUTH.getLoginCookies(username, password)
        when (result.isSuccess) {
            true -> {
                Sentry.addBreadcrumb(Breadcrumb().apply {
                    category = "auth"
                    message = "Schools.by accepted credentials"
                    this.level = SentryLevel.DEBUG
                })
                val schCredentials = result.getOrThrow()
                val user = SchoolsByParser.USER.getUserIDFromCredentials(schCredentials)
                if (user.isFailure) {
                    Sentry.captureException(user.exceptionOrNull()!!)
                    call.respond(HttpStatusCode.InternalServerError)
                    return@post
                }
                val userID = user.getOrThrow()
                ProvidersCatalog.databaseProvider.usersProvider.getUser(userID)?.let {
                    val newToken = ProvidersCatalog.databaseProvider.authenticationDataProvider.generateNewToken(userID)
                    return@post call.respond(
                        HttpStatusCode.Continue, LoginResponse(userID, jwtProvider.signToken(it, newToken.token))
                    )
                }
                val jobID = ProvidersCatalog.registrarProvider.addToRegister(userID, schCredentials)
                call.response.headers.append(
                    HttpHeaders.Location,
                    "${ProvidersCatalog.configuration.serverConfiguration.baseWebsocketUrl}/register/$jobID"
                )
                call.respond(HttpStatusCode.SeeOther)
            }
            false -> {
                Sentry.captureException(result.exceptionOrNull()!!)
                return@post call.respond(HttpStatusCode.InternalServerError) // TODO: Add Schools.by availability checker
            }
        }
    }
}

private fun Route.getUserByID() {
    authenticate("jwt") {
        get("{id}") {
            val id = call.parameters["id"]?.toIntOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest)
            ProvidersCatalog.databaseProvider.usersProvider.getUser(id)?.let {
                return@get call.respond(it)
            }
            return@get call.respond(HttpStatusCode.NotFound)
        }
    }
}

private fun Route.getUserRoles() {
    authenticate("jwt") {
        get("{id}/roles") {
            val user = call.authentication.principal<UserPrincipal>()?.getUserFromDB() ?: return@get call.respond(
                HttpStatusCode.Unauthorized
            )
            val id = call.parameters["id"]?.toIntOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest)
            val targetUser = ProvidersCatalog.databaseProvider.usersProvider.getUser(id) ?: return@get call.respond(
                HttpStatusCode.NotFound
            )
            ProvidersCatalog.authorization.authorize(user, "read_roles", targetUser)
            ProvidersCatalog.databaseProvider.rolesProvider.getRolesForUser(id).let {
                return@get call.respond(it)
            }
        }
    }
}

internal fun Route.user() {
    login()
    getUserByID()
    getUserRoles()
}
