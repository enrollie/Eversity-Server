/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 8/24/22, 7:50 PM
 */

package by.enrollie

import io.ktor.server.application.Application
import by.enrollie.annotations.UnsafeAPI
import by.enrollie.impl.*
import by.enrollie.plugins.configureKtorPlugins
import by.enrollie.plugins.configureStartStopListener
import by.enrollie.privateProviders.*
import by.enrollie.providers.*
import by.enrollie.routes.registerAllRoutes
import by.enrollie.util.StartupRoutine
import by.enrollie.util.generateServerName
import by.enrollie.util.getBootstrapText
import by.enrollie.util.getServices
import com.neitex.SchoolsByParser
import com.neitex.SchoolsByUnavailable
import io.github.z4kn4fein.semver.Version
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.sentry.Sentry
import io.sentry.SentryOptions
import kotlinx.coroutines.*
import org.joda.time.DateTime
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import org.kodein.di.instance
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.module.ModuleFinder
import java.net.InetAddress
import java.util.*
import kotlin.io.path.Path

fun main() {
    val logger = LoggerFactory.getLogger("BOOTSTRAP")
    val environment = when (System.getenv()["EVERSITY_ENV"]?.lowercase()) {
        "prod" -> EnvironmentInterface.EnvironmentType.PRODUCTION
        "dev" -> EnvironmentInterface.EnvironmentType.DEVELOPMENT
        "test" -> EnvironmentInterface.EnvironmentType.TESTING
        null -> EnvironmentInterface.EnvironmentType.DEVELOPMENT
        else -> throw IllegalArgumentException("Unknown environment type: ${System.getenv()["EVERSITY_ENV"]}")
    }
    logger.info("Starting application in \'$environment\' environment...")
    val (metadata, schoolsByParserVersion) = run {
        val selfProperties =
            (Unit::class as Any).javaClass.classLoader.getResourceAsStream("selfInfo.properties")?.use {
                Properties().apply {
                    load(it)
                }
            } ?: Properties()
        (object : EnvironmentInterface {
            override val environmentType: EnvironmentInterface.EnvironmentType = environment
            override val systemName: String = InetAddress.getLocalHost().hostName ?: System.getProperty("os.name")
            override val serverName: String = System.getenv()["EVERSITY_SERVER_NAME"] ?: generateServerName()
            override val startTime: Long = DateTime.now().millis
            override val serverVersion: String = selfProperties.getProperty("version", "unknown")
            override val serverApiVersion: String = selfProperties.getProperty("apiVersion", "unknown")
            override val serverBuildTimestamp: Long = selfProperties.getProperty("buildTimestamp", "0").toLong()
            override val serverBuildID: String = selfProperties.getProperty("buildID", "unknown")
            override val environmentVariables: Map<String, String> = System.getenv()
        } to selfProperties.getProperty("schoolsByParserVersion"))
    }
    logger.info(
        "Starting Eversity Server ${metadata.serverVersion}#${metadata.serverBuildID} (built on: ${
            DateTime(
                metadata.serverBuildTimestamp * 1000
            )
        }) on ${metadata.systemName} (server name: ${metadata.serverName})..."
    )
    logger.debug("Server API version: ${metadata.serverApiVersion}")
    logger.debug("SchoolsBy parser version: $schoolsByParserVersion")
    println(getBootstrapText(metadata))
    val pluginsCoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    runBlocking { // Configure providers
        val layer = run { // Configure layer
            File("plugins").mkdir()
            val pluginsFinder = ModuleFinder.of(Path("plugins"))
            val pluginNames = pluginsFinder.findAll().map { it.descriptor().name() }
            val pluginsConfiguration =
                ModuleLayer.boot().configuration().resolve(pluginsFinder, ModuleFinder.of(), pluginNames)
            ModuleLayer.boot().defineModulesWithOneLoader(pluginsConfiguration, ClassLoader.getSystemClassLoader())
        }
        val plugins = getServices<PluginMetadataInterface>(layer)
        val addedPlugins = mutableSetOf<String>()
        val loadAnyVersion = System.getenv()["EVERSITY_LOAD_EVERY_PLUGIN"]?.toBooleanStrictOrNull() ?: false
        if (loadAnyVersion) {
            logger.warn("EVERSITY_LOAD_EVERY_PLUGIN is set to true. All plugins will be loaded regardless of API version they were built against.")
        }
        plugins.map {
            pluginsCoroutineScope.async {
                if (it.name in addedPlugins) {
                    throw IllegalStateException("Found another plugin with the same title: ${it.name} (its version is ${it.version})")
                }
                val serverApiVersion = Version.parse(metadata.serverApiVersion)
                val pluginApiVersion = Version.parse(it.pluginApiVersion, strict = false)
                if (serverApiVersion < pluginApiVersion) {
                    logger.warn("Plugin ${it.name} (version ${it.version}) was built against newer API version (${it.pluginApiVersion}) than the server (${metadata.serverApiVersion}).")
                    if (!loadAnyVersion) {
                        logger.warn("Skipping plugin ${it.name} (version ${it.version})...")
                        return@async
                    }
                } else if (serverApiVersion != pluginApiVersion && !loadAnyVersion) {
                    logger.error("Plugin ${it.name} (version ${it.version}) was built for plugin API version ${it.pluginApiVersion}, but this server implements version ${metadata.serverApiVersion}. This plugin won't be loaded.")
                    return@async
                } else if (serverApiVersion != pluginApiVersion && loadAnyVersion) {
                    logger.warn("Plugin ${it.name} (version ${it.version}) was built for plugin API version ${it.pluginApiVersion}, but this server implements version ${metadata.serverApiVersion}. This plugin will be loaded, but it might not work as expected.")
                }
                logger.info("Loading plugin ${it.name} ${it.version} (author: ${it.author})")
                it.onLoad()
                logger.info("Loaded plugin ${it.name} (version: ${it.version})")
                addedPlugins += it.name
            }
        }.also {
            val list = awaitAll(*it.toTypedArray())
            if (list.size != it.size) {
                throw IllegalStateException("Some plugins failed to load, stopping server...")
            }
        }

        val database = getServices<DatabaseProviderInterface>(layer).let {
            require(it.size == 1) { "Exactly one database provider must be registered, however ${it.size} are found (IDs: ${it.map { provider -> provider.databasePluginID }})" }
            it.first()
        }
        if (database.databasePluginID !in addedPlugins) {
            throw IllegalStateException("Database provider ${database.databasePluginID} is not loaded")
        }
        logger.info("Using database: ${database.databasePluginID}")
        val configuration = getServices<ConfigurationInterface>(layer).let {
            require(it.size == 1) { "Exactly one configuration provider must be registered, however ${it.size} are found (IDs: ${it.map { provider -> provider.configurationPluginID }})" }
            it.first()
        }
        if (configuration.configurationPluginID !in addedPlugins) {
            throw IllegalStateException("Configuration storage ${configuration.configurationPluginID} is not loaded")
        }
        logger.info("Using configuration storage: ${configuration.configurationPluginID}")
        if (!configuration.isConfigured) {
            logger.error("Configuration storage plugin reported that configuration is not present. Please, configure it accordingly to its manual.")
            throw IllegalStateException("Configuration storage plugin reported that configuration is not present.")
        }


        val dependencies = DI {
            bindSingleton { configuration }
            bindSingleton { database }
            bindSingleton<EnvironmentInterface> { metadata }
            bindSingleton<CommandLineInterface> { CommandLine.instance }
            bindSingleton<AuthorizationInterface> {
                AuthorizationProviderImpl(instance())
            }
            bindSingleton<PluginsProviderInterface> { PluginsProviderImpl(plugins) }
            bindSingleton<EventSchedulerInterface> { EventSchedulerImpl() }
            bindSingleton<SchoolsByMonitorInterface> { SchoolsByMonitorImpl(instance(), instance(), instance()) }
            bindSingleton<TemplatingEngineInterface> { TemplatingEngineImpl() }
            bindSingleton<ExpiringFilesServerInterface> { ExpiringFilesServerImpl(instance()) }
            bindSingleton<DataSourceCommunicatorInterface> { DataSourceCommunicatorImpl(instance(), instance()) }
        }
        @OptIn(UnsafeAPI::class) setProvidersCatalog(ProvidersCatalogImpl(dependencies))
    }
    // Configure dependencies
    SchoolsByParser.setSubdomain(ProvidersCatalog.configuration.schoolsByConfiguration.baseUrl)
    logger.debug("SchoolsByParser subdomain: ${SchoolsByParser.schoolSubdomain}")
    ProvidersCatalog.schoolsByStatus.init()
    logger.debug("Initialized Schools.by status monitor")
    System.getenv()["SENTRY_DSN"]?.let {
        Sentry.init(SentryOptions().apply {
            dsn = it
            tracesSampleRate =
                if (metadata.environmentType == EnvironmentInterface.EnvironmentType.DEVELOPMENT || metadata.environmentType == EnvironmentInterface.EnvironmentType.TESTING) 0.8 else 1.0
            isPrintUncaughtStackTrace = true
            this.environment = metadata.environmentType.name.lowercase()
            release = metadata.serverVersion
            val disableSending =
                System.getenv()["EVERSITY_DO_NOT_SEND_EXCEPTIONS_TO_SENTRY"]?.toBooleanStrictOrNull() == true
            val sentryLogger = LoggerFactory.getLogger("Sentry")
            if (disableSending) sentryLogger.warn("EVERSITY_DO_NOT_SEND_EXCEPTIONS_TO_SENTRY is set to true. All exceptions will be logged, but not sent to Sentry.")
            setBeforeSend { event, hint ->
                if (disableSending) return@setBeforeSend null
                if (event.throwable is SchoolsByUnavailable) {
                    sentryLogger.warn("Schools.by is unavailable, discarding sentry exception")
                    return@setBeforeSend null
                }
                event.transaction
                return@setBeforeSend event
            }
        })
        logger.debug("Sentry initialized")
    }
    Sentry.configureScope {
        it.setTag("schools-by-subdomain", SchoolsByParser.schoolSubdomain)
        it.setTag("schools-by-parser-version", schoolsByParserVersion)
        it.setTag("version", metadata.serverVersion)
        it.setTag("environment", metadata.environmentType.name)
        it.setTag("server-api-version", metadata.serverApiVersion)
    }
    logger.debug("Starting server...")
    with(ProvidersCatalog) {
        @OptIn(UnsafeAPI::class) StartupRoutine.initialize(
            eventScheduler, commandLine, configuration, databaseProvider, registrarProvider
        )
    }
    Runtime.getRuntime().addShutdownHook(by.enrollie.util.ShutdownHook())
    val port = ProvidersCatalog.environment.environmentVariables["EVERSITY_PORT"]?.toIntOrNull() ?: 8080

    embeddedServer(Netty, port = port, host = "0.0.0.0", module = Application::appModule).start(true)
}

fun Application.appModule() {
    configureKtorPlugins()
    configureStartStopListener(ProvidersCatalog.plugins.list)
    routing {
        registerAllRoutes()
    }
}
