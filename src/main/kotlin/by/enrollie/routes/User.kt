/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/11/22, 12:25 AM
 */

package by.enrollie.routes

import by.enrollie.data_classes.UserID
import by.enrollie.impl.ProvidersCatalog
import by.enrollie.plugins.jwtProvider
import com.neitex.SchoolsByParser
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import java.util.*
import java.util.concurrent.ConcurrentHashMap

val usersMap = ConcurrentHashMap<String, UserID>(100)

private fun Route.Login() { // TODO: make a proper login system
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
                        HttpStatusCode.Continue,
                        LoginResponse(userID, jwtProvider.signToken(it, newToken.token))
                    )
                }
                val jobID = UUID.randomUUID().toString()
                usersMap[jobID] = userID
                call.response.headers.append(HttpHeaders.Location, "/")
                call.respond(HttpStatusCode.SeeOther)
            }
            false -> {

            }
        }
    }
}
