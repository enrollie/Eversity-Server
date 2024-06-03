/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 8/24/22, 7:50 PM
 */

package by.enrollie.routes

import by.enrollie.data_classes.*
import by.enrollie.extensions.isBetweenOrEqual
import by.enrollie.impl.AuthorizationProviderImpl
import by.enrollie.impl.ProvidersCatalog
import by.enrollie.plugins.UserPrincipal
import by.enrollie.plugins.jwtProvider
import by.enrollie.privateProviders.EnvironmentInterface
import by.enrollie.providers.DatabaseRolesProviderInterface
import com.neitex.AuthorizationUnsuccessful
import com.neitex.SchoolsByParser
import com.neitex.SchoolsByUnavailable
import io.ktor.client.plugins.*
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.LocalDateTime

private fun Route.login() {
    post("/login") {
        @Serializable
        data class LoginRequest(val username: String, val password: String)

        @Serializable
        data class LoginResponse(val userId: UserID, val token: String)
        if (!ProvidersCatalog.schoolsByStatus.isAvailable) {
            call.response.headers.append(
                HttpHeaders.RetryAfter,
                Duration.ofMillis(ProvidersCatalog.schoolsByStatus.untilNextCheck).toSeconds().toString()
            )
            return@post call.respond(HttpStatusCode.ServiceUnavailable)
        }
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
                    ProvidersCatalog.databaseProvider.customCredentialsProvider.setCredentials(
                        userID, "schools-csrfToken", schCredentials.csrfToken
                    )
                    ProvidersCatalog.databaseProvider.customCredentialsProvider.setCredentials(
                        userID, "schools-sessionID", schCredentials.sessionID
                    )
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

                    is SchoolsByUnavailable, is HttpRequestTimeoutException -> {
                        ProvidersCatalog.schoolsByStatus.forceRecheck()
                        call.response.headers.append(
                            HttpHeaders.RetryAfter,
                            Duration.ofMillis(ProvidersCatalog.schoolsByStatus.untilNextCheck).toSeconds().toString()
                        )
                        call.respond(HttpStatusCode.ServiceUnavailable)
                    }

                    else -> {
                        LoggerFactory.getLogger("qq").error("Unknown error", result.exceptionOrNull())
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

@Serializable
private data class UserRoleCreationRequest(val role: String, val additionalInfo: RoleInformationHolder)

private fun Route.userRoles() {
    authenticate("jwt") {
        route("{id}/roles") {
            get {
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
            put {
                val logger = LoggerFactory.getLogger("by.enrollie.Routes.User.putUserRole")
                val user = call.authentication.principal<UserPrincipal>()?.getUserFromDB() ?: return@put call.respond(
                    HttpStatusCode.Unauthorized
                )
                val id = call.parameters["id"]?.toIntOrNull() ?: return@put call.respond(HttpStatusCode.BadRequest)
                val targetUser = ProvidersCatalog.databaseProvider.usersProvider.getUser(id) ?: return@put call.respond(
                    HttpStatusCode.NotFound
                )
                call.attributes.put(AttributeKey("userID"), user.id)
                call.attributes.put(AttributeKey("targetUserID"), targetUser.id)
                val roleCreationRequest = call.receive<UserRoleCreationRequest>()
                val role = Roles.getRoleByID(roleCreationRequest.role)
                if (role == null) {
                    if (ProvidersCatalog.environment.environmentType == EnvironmentInterface.EnvironmentType.DEVELOPMENT) {
                        logger.debug("Role ${roleCreationRequest.role} not found (call id: ${call.callId})")
                    }
                    return@put call.respond(HttpStatusCode.BadRequest)
                }
                val createdRole = when (role) {
                    Roles.CLASS.ABSENCE_PROVIDER -> {
                        val classID =
                            roleCreationRequest.additionalInfo[Roles.CLASS.ABSENCE_PROVIDER.classID] as? ClassID
                                ?: return@put call.respond(
                                    HttpStatusCode.BadRequest
                                )
                        ProvidersCatalog.authorization.authorize(
                            user, "edit_roles", ProvidersCatalog.databaseProvider.classesProvider.getClass(
                                classID as? ClassID ?: return@put call.respond(
                                    HttpStatusCode.BadRequest
                                )
                            ) ?: return@put call.respond(
                                HttpStatusCode.BadRequest
                            )
                        )
                        val existingRole = ProvidersCatalog.databaseProvider.rolesProvider.getRolesForUser(id).find {
                            it.role == role && LocalDateTime.now()
                                .isBetweenOrEqual(it.roleGrantedDateTime, it.roleRevokedDateTime ?: LocalDateTime.MAX)
                        }
                        if (existingRole != null) {
                            if (ProvidersCatalog.environment.environmentType == EnvironmentInterface.EnvironmentType.DEVELOPMENT) {
                                logger.debug(
                                    "Role {} already exists: {} (call id: {})",
                                    roleCreationRequest.role,
                                    existingRole,
                                    call.callId
                                )
                            }
                            return@put call.respond(existingRole)
                        }
                        ProvidersCatalog.databaseProvider.rolesProvider.appendRoleToUser(
                            id, DatabaseRolesProviderInterface.RoleCreationData(
                                id, role, RoleInformationHolder(
                                    Roles.CLASS.ABSENCE_PROVIDER.classID to classID,
                                    Roles.CLASS.ABSENCE_PROVIDER.delegatedBy to user.id
                                )
                            )
                        )
                    }

                    Roles.SCHOOL.SOCIAL_TEACHER -> {
                        ProvidersCatalog.authorization.authorize(
                            user, "edit_roles", AuthorizationProviderImpl.school
                        )
                        val existingRole = ProvidersCatalog.databaseProvider.rolesProvider.getRolesForUser(id).find {
                            it.role == role && LocalDateTime.now()
                                .isBetweenOrEqual(it.roleGrantedDateTime, it.roleRevokedDateTime ?: LocalDateTime.MAX)
                        }
                        if (existingRole != null) {
                            if (ProvidersCatalog.environment.environmentType == EnvironmentInterface.EnvironmentType.DEVELOPMENT) {
                                logger.debug(
                                    "Role {} already exists: {} (call id: {})",
                                    roleCreationRequest.role,
                                    existingRole,
                                    call.callId
                                )
                            }
                            return@put call.respond(existingRole)
                        }
                        ProvidersCatalog.databaseProvider.rolesProvider.appendRoleToUser(
                            id, DatabaseRolesProviderInterface.RoleCreationData(id, role, RoleInformationHolder())
                        )
                    }

                    else -> return@put call.respond(HttpStatusCode.Forbidden)
                }
                return@put call.respond(createdRole)
            }
            delete {
                val user =
                    call.authentication.principal<UserPrincipal>()?.getUserFromDB() ?: return@delete call.respond(
                        HttpStatusCode.Unauthorized
                    )
                val id = call.parameters["id"]?.toIntOrNull() ?: return@delete call.respond(HttpStatusCode.BadRequest)
                val targetUser =
                    ProvidersCatalog.databaseProvider.usersProvider.getUser(id) ?: return@delete call.respond(
                        HttpStatusCode.NotFound
                    )
                call.attributes.put(AttributeKey("userID"), user.id)
                call.attributes.put(AttributeKey("targetUserID"), targetUser.id)
                val roleID = call.receive<Map<String, String>>()["uniqueId"] ?: return@delete call.respond(
                    HttpStatusCode.BadRequest
                )
                val role = ProvidersCatalog.databaseProvider.rolesProvider.getAllRolesByMatch { it.uniqueID == roleID }
                    .firstOrNull()
                when (role?.role) {
                    Roles.CLASS.ABSENCE_PROVIDER -> ProvidersCatalog.authorization.authorize(
                        user,
                        "edit_roles",
                        ProvidersCatalog.databaseProvider.classesProvider.getClass(
                            role.getField(Roles.CLASS.ABSENCE_PROVIDER.classID)
                                ?: throw IllegalArgumentException("Role $role doesn't have 'classID' field")
                        )
                            ?: throw IllegalStateException("Class mentioned in role $roleID doesn't exist")
                    )

                    null -> return@delete call.respond(HttpStatusCode.BadRequest)
                    else -> ProvidersCatalog.authorization.authorize(
                        user,
                        "edit_all_roles",
                        AuthorizationProviderImpl.school
                    )
                }
                ProvidersCatalog.databaseProvider.rolesProvider.revokeRole(roleID, LocalDateTime.now())
                return@delete call.respond(HttpStatusCode.OK)
            }
        }
    }
}

private fun Route.userToken() {
    authenticate("jwt") {
        route("/token") {
            delete {
                val user = call.authentication.principal<UserPrincipal>() ?: return@delete call.respond(
                    HttpStatusCode.Unauthorized
                )
                if (call.request.queryParameters["all"]?.toBooleanStrictOrNull() == true) {
                    ProvidersCatalog.databaseProvider.runInSingleTransaction { database ->
                        database.authenticationDataProvider.getUserTokens(user.userID).forEach {
                            database.authenticationDataProvider.revokeToken(it)
                        }
                    }
                } else ProvidersCatalog.databaseProvider.authenticationDataProvider.revokeToken(
                    AuthenticationToken(
                        user.token ?: return@delete call.respond(HttpStatusCode.Unauthorized),
                        user.userID,
                        LocalDateTime.now()
                    )
                )
                return@delete call.respond(HttpStatusCode.OK)
            }
        }
    }
}

private fun Route.getCurrUser() {
    authenticate("jwt") {
        get {
            val user = call.authentication.principal<UserPrincipal>()?.getUserFromDB() ?: call.respond(
                HttpStatusCode.Unauthorized
            )
            call.respond(user)
        }
    }
}

internal fun Route.user() {
    route("/user") {
        login()
        getUserByID()
        userRoles()
        userToken()
        getCurrUser()
    }
}
