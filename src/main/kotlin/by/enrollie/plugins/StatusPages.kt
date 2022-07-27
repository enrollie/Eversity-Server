/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/27/22, 11:23 PM
 */

package by.enrollie.plugins

import com.osohq.oso.Exceptions.ForbiddenException
import com.osohq.oso.Exceptions.NotFoundException
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.*
import org.slf4j.LoggerFactory

internal fun Application.configureStatusPages() {
    val logger by lazy {
        LoggerFactory.getLogger("StatusPages")
    }
    install(StatusPages) {
        exception<NotFoundException> { call, _ ->
            logger.debug("User ${call.attributes[AttributeKey("userID")]} was restricted from reading ${call.request.path()}")
            call.respond(HttpStatusCode.NotFound)
        }
        exception<ForbiddenException> { call, _ ->
            logger.debug("User ${call.attributes[AttributeKey("userID")]} was restricted from acting on ${call.request.path()}")
            call.respond(HttpStatusCode.Forbidden)
        }
        exception<Throwable> { call, cause ->
            logger.debug("CallID: ${call.callId}", cause)
            call.respond(HttpStatusCode.InternalServerError)
        }
    }
}
