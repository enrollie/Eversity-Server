/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 8/7/22, 3:49 AM
 */

package by.enrollie.routes

import by.enrollie.annotations.UnsafeAPI
import by.enrollie.data_classes.RoleInformationHolder
import by.enrollie.data_classes.Roles
import by.enrollie.data_classes.User
import by.enrollie.data_classes.UserID
import by.enrollie.impl.ProvidersCatalog
import by.enrollie.plugins.UserPrincipal
import by.enrollie.serializers.LocalDateTimeSerializer
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.sentry.Breadcrumb
import io.sentry.Sentry
import kotlinx.serialization.SerialName
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.time.LocalDateTime

private fun Route.usersGet() {
    authenticate("jwt") {
        post {
            val user =
                call.authentication.principal<UserPrincipal>() ?: return@post call.respond(HttpStatusCode.Unauthorized)
            val users: List<User> = try {
                val userIDs: List<UserID> = Json.decodeFromString(call.receiveText())
                ProvidersCatalog.databaseProvider.usersProvider.getUsers().filter { it.id in userIDs }.also {
                    if (it.size != userIDs.size) {
                        return@post call.respond(HttpStatusCode.NotFound)
                    }
                }
            } catch (e: SerializationException) {
                ProvidersCatalog.databaseProvider.usersProvider.getUsers()
            } catch (e: Exception) {
                Sentry.addBreadcrumb(Breadcrumb.error("Exception besides SerializationException: ${e.message}"))
                ProvidersCatalog.databaseProvider.usersProvider.getUsers()
            }
            ProvidersCatalog.authorization.filterAllowed(user.getUserFromDB(), "read", users).also {
                if (it.size != users.size)
                    return@post call.respond(HttpStatusCode.Forbidden)
            }
            return@post call.respond(users)
        }
    }
}

@kotlinx.serialization.Serializable
internal data class ByRoleQuery(
    @SerialName("roleId") val roleID: String,
    val additionalData: RoleInformationHolder? = null,
    @kotlinx.serialization.Serializable(with = LocalDateTimeSerializer::class) val validOn: LocalDateTime? = null
)

@OptIn(UnsafeAPI::class)
private fun Route.usersByRoleGet() {
    authenticate("jwt") {
        post("/byRole") {
            val user =
                call.authentication.principal<UserPrincipal>() ?: return@post call.respond(HttpStatusCode.Unauthorized)
            val query: ByRoleQuery = Json.decodeFromString(call.receiveText())
            val role = Roles.getRoleByID(query.roleID) ?: return@post call.respond(HttpStatusCode.BadRequest)
            when (role) {
                Roles.CLASS.STUDENT -> {
                    if (query.additionalData?.get(Roles.CLASS.STUDENT.classID) == null) return@post call.respond(
                        HttpStatusCode.BadRequest
                    )
                    ProvidersCatalog.authorization.authorize(
                        user.getUserFromDB(),
                        "read_students",
                        ProvidersCatalog.databaseProvider.classesProvider.getClass(
                            query.additionalData.typedGet(Roles.CLASS.STUDENT.classID)!!
                        ) ?: return@post call.respond(HttpStatusCode.BadRequest)
                    )
                }

                Roles.CLASS.ABSENCE_PROVIDER -> {
                    if (query.additionalData?.get(Roles.CLASS.ABSENCE_PROVIDER.classID) == null) return@post call.respond(
                        HttpStatusCode.BadRequest
                    )
                    ProvidersCatalog.authorization.authorize(
                        user.getUserFromDB(),
                        "edit_roles",
                        ProvidersCatalog.databaseProvider.classesProvider.getClass(
                            query.additionalData.typedGet(Roles.CLASS.ABSENCE_PROVIDER.classID)!!
                        ) ?: return@post call.respond(HttpStatusCode.BadRequest)
                    )
                }

                Roles.CLASS.TEACHER -> {
                    if (query.additionalData?.get(Roles.CLASS.TEACHER.classID) == null) return@post call.respond(
                        HttpStatusCode.BadRequest
                    )
                    ProvidersCatalog.authorization.authorize(
                        user.getUserFromDB(),
                        "read_lessons",
                        ProvidersCatalog.databaseProvider.classesProvider.getClass(
                            query.additionalData.typedGet(Roles.CLASS.TEACHER.classID)!!
                        ) ?: return@post call.respond(HttpStatusCode.BadRequest)
                    )
                }

                else -> ProvidersCatalog.authorization.authorize(user.getUserFromDB(), "read_all_roles", Unit)
            }
            val userIDs: List<UserID> = ProvidersCatalog.databaseProvider.rolesProvider.getAllRolesByType(
                role
            ).let { rolesList ->
                if (query.additionalData != null) {
                    query.additionalData.getAsMap().toList().fold(rolesList) { acc, (key, value) ->
                        acc.filter { it.unsafeGetField(key) == value }
                    }
                } else rolesList
            }.let { rolesList ->
                if (query.validOn != null) {
                    rolesList.filter {
                        query.validOn.isAfter(it.roleGrantedDateTime) && (it.roleRevokedDateTime?.isAfter(query.validOn)
                            ?: true)
                    }
                } else rolesList
            }.map { it.userID }.distinct()
            val users: List<User> =
                if (userIDs.isEmpty()) emptyList() else ProvidersCatalog.databaseProvider.usersProvider.getUsers()
                    .filter { it.id in userIDs }
            call.respond(users)
        }
    }
}

internal fun Route.users() {
    route("/users") {
        usersGet()
        usersByRoleGet()
    }
}
