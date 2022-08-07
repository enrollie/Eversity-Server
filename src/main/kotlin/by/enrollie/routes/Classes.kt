/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 8/7/22, 3:49 AM
 */

package by.enrollie.routes

import by.enrollie.data_classes.TeachingShift
import by.enrollie.impl.ProvidersCatalog
import by.enrollie.plugins.UserPrincipal
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

private fun Route.ClassesGet() {
    get {
        val user = call.principal<UserPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
        val classes = ProvidersCatalog.databaseProvider.classesProvider.getClasses()
        val filter = call.request.queryParameters["shift"]?.runCatching {
            TeachingShift.valueOf(this)
        }?.getOrElse {
            return@get call.respond(HttpStatusCode.BadRequest)
        }
        call.respond(HttpStatusCode.OK, classes.let {
            if (filter != null) it.filter { it.shift == filter } else it
        })
    }
}

internal fun Route.classes() {
    authenticate("jwt") {
        route("/classes") { ClassesGet() }
    }
}
