/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 8/24/22, 7:50 PM
 */

package by.enrollie.routes

import by.enrollie.impl.ProvidersCatalog
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

internal fun Route.bellsTimetable() {
    authenticate("jwt") {
        route("/bellsTimetable") {
            get {
                call.respond(ProvidersCatalog.databaseProvider.timetablePlacingProvider.getTimetablePlaces())
            }
        }
    }
}
