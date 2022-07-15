/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/15/22, 3:27 AM
 */

package by.enrollie.plugins

import io.ktor.server.application.*


fun Application.configureKtorPlugins() {
    configureStatusPages()
    configureSecurity()
    configureHTTP()
    configureMonitoring()
    configureSerialization()
    configureSockets()
}
