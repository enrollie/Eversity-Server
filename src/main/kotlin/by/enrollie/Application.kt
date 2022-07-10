/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/11/22, 12:25 AM
 */

package by.enrollie

import by.enrollie.impl.*
import by.enrollie.plugins.*
import by.enrollie.providers.ConfigurationProviderInterface
import by.enrollie.providers.DatabaseProviderInterface
import com.neitex.SchoolsByParser
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.sentry.Sentry
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import java.util.*

fun main() {
    val selfProperties = (Unit::class as Any).javaClass.classLoader.getResourceAsStream("selfInfo.properties")?.use {
        Properties().apply {
            load(it)
        }
    } ?: Properties()
    println("Starting Eversity Server (${selfProperties.getProperty("version")})...")
    // TODO: Make an initial configuration wizard
    run { // Configure providers
        val dependencies = DI {
            bindSingleton<ConfigurationProviderInterface> { DummyConfigurationProvider() } // TODO: Replace with real providers
            bindSingleton<DatabaseProviderInterface> { DummyDatabaseImplemetation() }
        }
        setProvidersCatalog(ProvidersCatalogImpl(dependencies))
    }
    // Configure dependencies
    SchoolsByParser.setSubdomain(ProvidersCatalog.configurationProvider.getConfiguration().SchoolsByConfiguration.baseUrl)
    System.getenv()["SENTRY_DSN"]?.let {
        Sentry.init(it)
    }
    Sentry.configureScope {
        it.setTag("schools-by-subdomain", SchoolsByParser.schoolSubdomain)
        it.setTag("schools-by-parser-version", selfProperties["schoolsByParserVersion"].toString())
        it.setTag("version", selfProperties["version"].toString())
    }

    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        configurePlugins()
    }// .start(wait = true)
}

fun Application.configurePlugins() {
    configureStatusPages()
    configureSecurity()
    configureHTTP()
    configureMonitoring()
    configureSerialization()
    configureSockets()
}
