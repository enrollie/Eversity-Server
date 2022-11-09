/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/15/22, 1:44 AM
 */

package by.enrollie.plugins

import by.enrollie.impl.ProvidersCatalog
import by.enrollie.privateProviders.EnvironmentInterface
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.forwardedheaders.*

internal fun Application.configureHTTP() {
    install(DefaultHeaders) {
        if (ProvidersCatalog.environment.environmentType == EnvironmentInterface.EnvironmentType.DEVELOPMENT || ProvidersCatalog.environment.environmentType == EnvironmentInterface.EnvironmentType.TESTING) {
            header("Access-Control-Allow-Origin", "*")
            header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, PATCH, OPTIONS")
            header("Access-Control-Allow-Headers", "Content-Type, Authorization")
            header("X-Version", ProvidersCatalog.environment.serverVersion)
            header("X-Api-Version", ProvidersCatalog.environment.serverApiVersion)
            header("X-Server-Name", ProvidersCatalog.environment.serverName)
            header("X-Server-System", ProvidersCatalog.environment.systemName)
            header("X-Environment", ProvidersCatalog.environment.environmentType.fullName)
        }
        header(HttpHeaders.Server, "EversityServer/${ProvidersCatalog.environment.serverVersion}")
    }
    install(ForwardedHeaders) // Since Eversity is designed to be behind a reverse proxy, we need to enable those.
    install(XForwardedHeaders)
}
