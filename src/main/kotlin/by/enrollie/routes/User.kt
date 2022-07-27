/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/28/22, 12:01 AM
 */

package by.enrollie.routes

import by.enrollie.data_classes.UserID
import by.enrollie.impl.ProvidersCatalog
import by.enrollie.plugins.UserPrincipal
import by.enrollie.plugins.jwtProvider
import com.neitex.AuthorizationUnsuccessful
import com.neitex.SchoolsByParser
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

private fun Route.login() {
    post("/login") {
        @kotlinx.serialization.Serializable
        data class LoginRequest(val username: String, val password: String)

        @kotlinx.serialization.Serializable
        data class LoginResponse(val userId: UserID, val token: String)
        val (username, password) = Json.decodeFromString<LoginRequest>(call.receiveText())
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
                        HttpStatusCode.OK, LoginResponse(userID, jwtProvider.signToken(it, newToken.token))
                    )
                }
                val jobID = ProvidersCatalog.registrarProvider.addToRegister(userID, schCredentials)
                call.response.headers.append(
                    HttpHeaders.Location,
                    "${ProvidersCatalog.configuration.serverConfiguration.baseWebsocketUrl}/register/$jobID"
                )
                call.respond(HttpStatusCode.Accepted)
            }
            false -> {
                when (result.exceptionOrNull()) {
                    is AuthorizationUnsuccessful -> {
                        call.respond(HttpStatusCode.Unauthorized)
                    }
                    else -> {
                        Sentry.setTag("callId", call.callId.toString())
                        Sentry.captureException(result.exceptionOrNull()!!)
                        call.respond(HttpStatusCode.InternalServerError)
                    }
                }
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
            call.attributes.put(AttributeKey("userID"), user.id)
            call.attributes.put(AttributeKey("targetUserID"), targetUser.id)
            ProvidersCatalog.authorization.authorize(user, "read_roles", targetUser)
            ProvidersCatalog.databaseProvider.rolesProvider.getRolesForUser(id).let {
                return@get call.respond(it)
            }
        }
    }
}

internal fun Route.user() {
    route("/user") {
        login()
        getUserByID()
        getUserRoles()
    }
}
