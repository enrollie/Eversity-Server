/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 10/9/22, 9:53 PM
 */

package by.enrollie

import by.enrollie.annotations.UnsafeAPI
import by.enrollie.data_classes.Declensions
import by.enrollie.impl.*
import by.enrollie.privateProviders.CommandLineInterface
import by.enrollie.privateProviders.EnvironmentInterface
import by.enrollie.privateProviders.EventSchedulerInterface
import by.enrollie.privateProviders.TemplatingEngineInterface
import by.enrollie.providers.*
import io.ktor.server.application.*
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import org.kodein.di.instance

object Defaults {
    val configuration: ConfigurationInterface = object : ConfigurationInterface {
        override val configurationPluginID: String = "JUNIT"
        override val jwtConfiguration: ConfigurationInterface.JwtConfigurationInterface = object :
            ConfigurationInterface.JwtConfigurationInterface {
            override val secret: String = "secret"
            override val audience: String = "audience"

        }
        override val schoolConfiguration: ConfigurationInterface.SchoolConfigurationInterface = object :
            ConfigurationInterface.SchoolConfigurationInterface {
            override val title: Declensions = Declensions(
                "Школа",
                "Школы",
                "Школе",
                "Школу",
                "Школой",
                "Школе"
            )
        }
        override val schoolsByConfiguration: ConfigurationInterface.SchoolsByConfigurationInterface = object :
            ConfigurationInterface.SchoolsByConfigurationInterface {
            override val baseUrl: String = "https://demo.schools.by"
            override val recheckInterval: Long = 1000
            override val recheckOnDownInterval: Long = 1000
        }
        override val serverConfiguration: ConfigurationInterface.ServerConfigurationInterface = object :
            ConfigurationInterface.ServerConfigurationInterface {
            override val baseHttpUrl: String = "http://localhost:8080"
            override val baseWebsocketUrl: String = "ws://localhost:8080"
            override val tempFileTTL: Long = 1000
        }
        override val isConfigured: Boolean = true
    }

    val dependencyInjection = DI {
        bindSingleton<ConfigurationInterface> { configuration }
        bindSingleton<DatabaseProviderInterface> { TemporaryDatabaseImplementation() }
        bindSingleton<DataSourceCommunicatorInterface> { DataSourceCommunicatorImpl() }
        bindSingleton<CommandLineInterface> { CommandLine.instance }
        bindSingleton<AuthorizationInterface> { AuthorizationProviderImpl() }
        bindSingleton<PluginsProviderInterface> { PluginsProviderImpl(listOf()) }
        bindSingleton<EventSchedulerInterface> { EventSchedulerImpl() }
        bindSingleton<SchoolsByMonitorInterface> { SchoolsByMonitorImpl() }
        bindSingleton<TemplatingEngineInterface> { TemplatingEngineImpl() }
        bindSingleton<ExpiringFilesServerInterface> { ExpiringFilesServerImpl() }
        bindSingleton<EnvironmentInterface> { EnvironmentImpl() }
    }

    class EnvironmentImpl: EnvironmentInterface {
        override val environmentType: EnvironmentInterface.EnvironmentType = EnvironmentInterface.EnvironmentType.TESTING
        override val systemName: String = "JUNIT"
        override val serverName: String = "JUNIT"
        override val startTime: Long = System.currentTimeMillis()
        override val serverVersion: String = "UNKNOWN"
        override val serverApiVersion: String = "UNKNOWN"
        override val serverBuildTimestamp: Long = System.currentTimeMillis()
        override val serverBuildID: String = "UNKNOWN"
        override val environmentVariables: Map<String, String> = mapOf()
    }

    val providersCatalog: ProvidersCatalogImpl
        get() = ProvidersCatalogImpl(dependencyInjection)
}

@OptIn(UnsafeAPI::class)
val providersCatalogPlugin = createApplicationPlugin("ProvidersCatalogPlugin", { }) {
    setProvidersCatalog(Defaults.providersCatalog)
}
