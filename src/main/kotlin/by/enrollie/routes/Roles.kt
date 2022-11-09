/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 10/30/22, 5:28 PM
 */

package by.enrollie.routes

import by.enrollie.annotations.UnsafeAPI
import by.enrollie.data_classes.Roles
import by.enrollie.extensions.isBetweenOrEqual
import by.enrollie.impl.AuthorizationProviderImpl
import by.enrollie.impl.ProvidersCatalog
import by.enrollie.plugins.UserPrincipal
import by.enrollie.privateProviders.EnvironmentInterface
import by.enrollie.serializers.LocalDateTimeSerializer
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

private fun Route.queryRoles() {
    get {
        val user = call.principal<UserPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
        ProvidersCatalog.authorization.authorize(
            user.getUserFromDB(),
            "read_all_roles",
            AuthorizationProviderImpl.school
        )
        val logger = LoggerFactory.getLogger("Routes.Roles.queryRoles")
        val role = Roles.getRoleByID(call.parameters["roleId"] ?: return@get call.respond(HttpStatusCode.BadRequest))
        if (role == null) {
            if (ProvidersCatalog.environment.environmentType == EnvironmentInterface.EnvironmentType.DEVELOPMENT) {
                logger.debug("Role with id ${call.parameters["roleId"]} not found")
            }
            return@get call.respond(HttpStatusCode.NotFound)
        }
        val validOn = call.parameters["validOn"]?.let {
            kotlin.runCatching {LocalDateTimeSerializer.parse(it) }.getOrNull().let { dateTime ->
                if (dateTime == null){
                    if (ProvidersCatalog.environment.environmentType == EnvironmentInterface.EnvironmentType.DEVELOPMENT) {
                        logger.debug("Invalid date format: $it")
                    }
                    return@get call.respond(HttpStatusCode.BadRequest)
                } else dateTime
            }
        }
        val additionalRoleData = call.parameters.filter { key, _ ->
            key != "roleId" && key != "validOn"
        }.entries().map {
            val foundRole = Roles.getRoleByID(it.key)?.fieldByID(it.key)
            if (foundRole == null) {
                if (ProvidersCatalog.environment.environmentType == EnvironmentInterface.EnvironmentType.DEVELOPMENT) {
                    logger.debug("Invalid role ID: ${it.key} (value: ${it.value}, call ID: ${call.callId})")
                }
                return@get call.respond(HttpStatusCode.BadRequest)
            }
            foundRole to it.value.first()
        }
        val roles = ProvidersCatalog.databaseProvider.rolesProvider.getAllRolesByMatch {
            (it.role == role) && ((validOn == null) || validOn.isBetweenOrEqual(
                it.roleGrantedDateTime,
                it.roleRevokedDateTime ?: LocalDateTime.MAX
            )) && additionalRoleData.all { (role, value) ->
                @OptIn(UnsafeAPI::class)
                it.unsafeGetField(role)?.toString() == value
            }
        }
        if (ProvidersCatalog.environment.environmentType == EnvironmentInterface.EnvironmentType.DEVELOPMENT) {
            logger.debug("Found roles: ${roles.joinToString { it.toString() }} (call ID: ${call.callId})")
        }
        call.respond(roles)
    }
}

internal fun Route.roles() {
    authenticate("jwt") {
        route("/roles") {
            queryRoles()
        }
    }
}
