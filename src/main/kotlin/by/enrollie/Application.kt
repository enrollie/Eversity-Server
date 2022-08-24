/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 8/24/22, 7:50 PM
 */

package by.enrollie

import by.enrollie.annotations.UnsafeAPI
import by.enrollie.impl.*
import by.enrollie.plugins.configureKtorPlugins
import by.enrollie.plugins.configureStartStopListener
import by.enrollie.privateProviders.ApplicationMetadata
import by.enrollie.privateProviders.EventSchedulerInterface
import by.enrollie.privateProviders.TemplatingEngineInterface
import by.enrollie.providers.*
import by.enrollie.routes.registerAllRoutes
import by.enrollie.util.StartupRoutine
import by.enrollie.util.getServices
import com.neitex.SchoolsByParser
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.sentry.Sentry
import io.sentry.SentryOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.joda.time.DateTime
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.module.ModuleFinder
import java.util.*
import kotlin.io.path.Path


lateinit var APPLICATION_METADATA: ApplicationMetadata
fun main() {
    val logger = LoggerFactory.getLogger("BOOTSTRAP")
    val environment = System.getenv()["EVERSITY_ENV"] ?: "prod"
    require(environment == "prod" || environment == "dev") { "Environment must be either 'prod' or 'dev'" }
    logger.info("Starting application in \'$environment\' environment...")
    val (metadata, schoolsByParserVersion) = run {
        val selfProperties =
            (Unit::class as Any).javaClass.classLoader.getResourceAsStream("selfInfo.properties")?.use {
                Properties().apply {
                    load(it)
                }
            } ?: Properties()
        (object : ApplicationMetadata {
            override val title: String = "Eversity Server v${selfProperties.getProperty("version", "0.1.0")}"
            override val version: String = selfProperties.getProperty("version", "0.1.0")
            override val buildTimestamp: Long = selfProperties.getProperty("buildTimestamp", "0").toLong()
        } to selfProperties.getProperty("schoolsByParserVersion"))
    }
    APPLICATION_METADATA = metadata

    logger.info("Starting ${metadata.title} (built on: ${DateTime(metadata.buildTimestamp * 1000)})...")
    val pluginsCoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    val plugins = run { // Configure providers
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
        plugins.forEach {
            pluginsCoroutineScope.launch {
                if (it.name in addedPlugins) {
                    throw IllegalStateException("Found another plugin with the same title: ${it.name} (its version is ${it.version})")
                }
                if (it.pluginApiVersion != APPLICATION_METADATA.version && !loadAnyVersion) {
                    logger.error("Plugin ${it.name} (version ${it.version}) was built for plugin API version ${it.pluginApiVersion}, but this server is running version ${APPLICATION_METADATA.version}. This plugin won't be loaded.")
                    return@launch
                } else if (it.pluginApiVersion != APPLICATION_METADATA.version && loadAnyVersion) {
                    logger.warn("Plugin ${it.name} (version ${it.version}) was built for plugin API version ${it.pluginApiVersion}, but this server is running version ${APPLICATION_METADATA.version}. This plugin will be loaded, but it might not work as expected.")
                }
                logger.info("Loading plugin ${it.name} v${it.version} (author: ${it.author})")
                it.onLoad()
                logger.info("Loaded plugin ${it.name} v${it.version}")
                addedPlugins += it.name
            }
        }

        val database = getServices<DatabaseProviderInterface>(layer).let {
            require(it.size == 1) { "Exactly one database provider must be registered, however ${it.size} are found (IDs: ${it.map { it.databasePluginID }})" }
            it.first()
        }
        if (database.databasePluginID !in addedPlugins) {
            throw IllegalStateException("Database provider ${database.databasePluginID} is not loaded")
        }
        logger.info("Using database: ${database.databasePluginID}")
        val configuration = getServices<ConfigurationInterface>(layer).let {
            require(it.size == 1) { "Exactly one configuration provider must be registered, however ${it.size} are found (IDs: ${it.map { it.configurationPluginID }})" }
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
            bindSingleton<DataRegistrarProviderInterface> { DataRegistrarImpl() }
            bindSingleton<CommandLineInterface> { CommandLine.instance }
            bindSingleton<AuthorizationInterface> { AuthorizationProviderImpl() }
            bindSingleton<PluginsProviderInterface> { PluginsProviderImpl(plugins) }
            bindSingleton<EventSchedulerInterface> { EventSchedulerImpl() }
            bindSingleton<SchoolsByMonitorInterface> { SchoolsByMonitorImpl() }
            bindSingleton<TemplatingEngineInterface> { TemplatingEngineImpl() }
        }
        @OptIn(UnsafeAPI::class) setProvidersCatalog(ProvidersCatalogImpl(dependencies))
        plugins
    }
    // Configure dependencies
    SchoolsByParser.setSubdomain(ProvidersCatalog.configuration.schoolsByConfiguration.baseUrl)
    logger.debug("SchoolsByParser subdomain: ${SchoolsByParser.schoolSubdomain}")
    logger.debug("Initialized Schools.by status monitor")
    ProvidersCatalog.schoolsByStatus.init()
    System.getenv()["SENTRY_DSN"]?.let {
        Sentry.init(SentryOptions().apply {
            dsn = it
            tracesSampleRate = if (environment == "prod") 0.8 else 1.0
            isPrintUncaughtStackTrace = true
            if (System.getenv()["EVERSITY_DO_NOT_SEND_EXCEPTIONS_TO_SENTRY"]?.toBooleanStrictOrNull() == true) {
                logger.warn("EVERSITY_DO_NOT_SEND_EXCEPTIONS_TO_SENTRY is set to true. All exceptions will be logged, but not sent to Sentry.")
                this.setBeforeSend { _, _ -> null }
            }
        })
        logger.debug("Sentry initialized")
    }
    Sentry.configureScope {
        it.setTag("schools-by-subdomain", SchoolsByParser.schoolSubdomain)
        it.setTag("schools-by-parser-version", schoolsByParserVersion)
        it.setTag("version", metadata.version)
        it.setTag("environment", environment)
    }
    if (System.getenv()["EVERSITY_STRESS_TEST_MODE"]?.toBooleanStrictOrNull() == true) {
        logger.warn("EVERSITY_STRESS_TEST_MODE is set to true. This server will be running in stress test mode. Communications with outside services using user-supplied data will be minimized. (though, some plugins may not respect this mode)")
    }
    logger.debug("Starting server...")
    @OptIn(UnsafeAPI::class) StartupRoutine.schedule(ProvidersCatalog.eventScheduler)
    Runtime.getRuntime().addShutdownHook(by.enrollie.util.ShutdownHook())

    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        configureKtorPlugins()
        configureStartStopListener(plugins)
        routing {
            registerAllRoutes()
        }
    }.start(true)
    logger.info("Server is shut down, unloading plugins...")
    plugins.forEach {
        it.onUnload()
    }
    logger.info("Everything is unloaded. Goodbye and have a nice day! :)")
}
