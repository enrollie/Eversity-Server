/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/10/22, 11:16 PM
 */

package by.enrollie.plugins

import io.ktor.server.application.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.forwardedheaders.*

fun Application.configureHTTP() {
    install(DefaultHeaders) {
        header("X-Powered-By", "Ktor") // Honour Ktor
    }
    install(ForwardedHeaders) // Since Eversity is designed to be behind a reverse proxy, we need to enable those.
    install(XForwardedHeaders)
}
