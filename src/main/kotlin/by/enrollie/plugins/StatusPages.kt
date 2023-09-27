/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 8/16/22, 12:14 AM
 */

package by.enrollie.plugins

import by.enrollie.impl.ProvidersCatalog
import by.enrollie.privateProviders.EnvironmentInterface
import com.osohq.oso.Exceptions.ForbiddenException
import com.osohq.oso.Exceptions.NotFoundException
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.*
import io.sentry.Sentry
import kotlinx.serialization.SerializationException
import org.slf4j.LoggerFactory

internal fun Application.configureStatusPages() {
    val logger by lazy {
        LoggerFactory.getLogger("StatusPages")
    }
    install(StatusPages) {
        exception<NotFoundException> { call, _ ->
            if (ProvidersCatalog.environment.environmentType == EnvironmentInterface.EnvironmentType.DEVELOPMENT)
                logger.debug("User ${call.attributes.getOrNull(AttributeKey("userID"))} (callID ${call.callId}) was restricted from reading ${call.request.path()}")
            call.respond(HttpStatusCode.NotFound)
        }
        exception<ForbiddenException> { call, _ ->
            if (ProvidersCatalog.environment.environmentType == EnvironmentInterface.EnvironmentType.DEVELOPMENT)
                logger.debug("User ${call.attributes.getOrNull(AttributeKey("userID"))} (callID ${call.callId}) was restricted from acting on ${call.request.path()}")
            call.respond(HttpStatusCode.Forbidden)
        }
        exception<SerializationException> { call, e ->
            if (ProvidersCatalog.environment.environmentType == EnvironmentInterface.EnvironmentType.DEVELOPMENT)
                logger.debug(
                    "User ${call.attributes.getOrNull(AttributeKey("userID"))} (callID ${call.callId}) sent bad body to ${call.request.path()} (message: ${e.message})"
                )
            call.respond(HttpStatusCode.BadRequest)
        }
        exception<BadRequestException> { call, cause ->
            if (ProvidersCatalog.environment.environmentType == EnvironmentInterface.EnvironmentType.DEVELOPMENT)
                logger.debug("BadRequest: User ${call.attributes.getOrNull(AttributeKey("userID"))} (callID ${call.callId}) sent bad body to ${call.request.path()} (message: ${cause.message})")
            call.respond(HttpStatusCode.BadRequest)
        }

        exception<Throwable> { call, cause ->
            logger.error("CallID: ${call.callId}; user: ${call.attributes.getOrNull(AttributeKey("userID"))}", cause)
            Sentry.addBreadcrumb("CallID: ${call.callId}")
            Sentry.captureException(cause)
            call.respond(HttpStatusCode.InternalServerError)
        }
    }
}
