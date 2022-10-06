/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 9/13/22, 9:15 PM
 */

package by.enrollie.routes

import by.enrollie.data_classes.Declensions
import by.enrollie.impl.ProvidersCatalog
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
private data class SchoolData(val title: Declensions, val schoolsBySubdomain: String)

internal fun Route.getSchool() {
    get("/school") {
        call.respond(
            SchoolData(
                ProvidersCatalog.configuration.schoolConfiguration.title,
                ProvidersCatalog.configuration.schoolsByConfiguration.baseUrl
            )
        )
    }
}
