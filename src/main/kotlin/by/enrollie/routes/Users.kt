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

private fun Route.UsersGet() {
    authenticate("jwt") {
        get {
            val user =
                call.authentication.principal<UserPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
            val users: List<User> = try {
                val userIDs: List<UserID> = Json.decodeFromString(call.receiveText())
                ProvidersCatalog.databaseProvider.usersProvider.getUsers().filter { it.id in userIDs }.also {
                    if (it.size != userIDs.size) {
                        return@get call.respond(HttpStatusCode.NotFound)
                    }
                }
            } catch (e: SerializationException) {
                ProvidersCatalog.databaseProvider.usersProvider.getUsers()
            } catch (e: Exception) {
                Sentry.addBreadcrumb(Breadcrumb.error("Exception besides SerializationException: ${e.message}"))
                ProvidersCatalog.databaseProvider.usersProvider.getUsers()
            }
            users.forEach {
                ProvidersCatalog.authorization.authorize(user.getUserFromDB(), "read", it)
            }
            return@get call.respond(users)
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
private fun Route.UsersByRoleGet() {
    authenticate("jwt") {
        get("/byRole") {
            val user =
                call.authentication.principal<UserPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
            ProvidersCatalog.authorization.authorize(user.getUserFromDB(), "read_all_roles", Unit)
            val query: ByRoleQuery = Json.decodeFromString(call.receiveText())
            val userIDs: List<UserID> = ProvidersCatalog.databaseProvider.rolesProvider.getAllRolesByType(
                Roles.getRoleByID(query.roleID) ?: return@get call.respond(HttpStatusCode.BadRequest)
            ).let {
                if (query.additionalData != null) {
                    query.additionalData.getAsMap().toList().fold(it) { acc, (key, value) ->
                        acc.filter { it.unsafeGetField(key) == value }
                    }
                } else it
            }.let {
                if (query.validOn != null) {
                    it.filter {
                        query.validOn.isAfter(it.roleGrantedDateTime) && (it.roleRevokedDateTime?.isAfter(query.validOn)
                            ?: true)
                    }
                } else it
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
        UsersGet()
        UsersByRoleGet()
    }
}
