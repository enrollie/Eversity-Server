/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 8/16/22, 12:14 AM
 */

package by.enrollie.plugins

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
import kotlinx.serialization.SerializationException
import org.slf4j.LoggerFactory

internal fun Application.configureStatusPages() {
    val logger by lazy {
        LoggerFactory.getLogger("StatusPages")
    }
    install(StatusPages) {
        exception<NotFoundException> { call, _ ->
            logger.debug("User ${call.attributes.getOrNull(AttributeKey("userID"))} (callID ${call.callId}) was restricted from reading ${call.request.path()}")
            call.respond(HttpStatusCode.NotFound)
        }
        exception<ForbiddenException> { call, _ ->
            logger.debug("User ${call.attributes.getOrNull(AttributeKey("userID"))} (callID ${call.callId}) was restricted from acting on ${call.request.path()}")
            call.respond(HttpStatusCode.Forbidden)
        }
        exception<SerializationException> { call, e ->
            logger.debug(
                "User ${call.attributes.getOrNull(AttributeKey("userID"))} (callID ${call.callId}) sent bad body to ${call.request.path()} (message: ${e.message})"
            )
            call.respond(HttpStatusCode.BadRequest)
        }
        exception<BadRequestException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest)
        }

        exception<Throwable> { call, cause ->
            logger.debug("CallID: ${call.callId}", cause)
            call.respond(HttpStatusCode.InternalServerError)
        }
    }
}
