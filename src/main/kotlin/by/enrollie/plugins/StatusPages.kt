/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/15/22, 3:38 AM
 */

package by.enrollie.plugins

import com.osohq.oso.Exceptions.ForbiddenException
import com.osohq.oso.Exceptions.NotFoundException
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*

internal fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<NotFoundException> { call, _ ->
            call.respond(HttpStatusCode.NotFound)
        }
        exception<ForbiddenException> { call, _ ->
            call.respond(HttpStatusCode.Forbidden)
        }
        exception<Throwable> { call, cause ->
            call.respond(HttpStatusCode.InternalServerError)

        }
    }
}
