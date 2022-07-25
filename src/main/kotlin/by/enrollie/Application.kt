/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/25/22, 2:58 PM
 */

package by.enrollie

import by.enrollie.annotations.UnsafeAPI
import by.enrollie.impl.*
import by.enrollie.plugins.configureKtorPlugins
import by.enrollie.plugins.configureStartStopListener
import by.enrollie.privateProviders.ApplicationMetadata
import by.enrollie.providers.*
import by.enrollie.routes.registerAllRoutes
import by.enrollie.util.getServices
import com.neitex.SchoolsByParser
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.sentry.Sentry
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
        plugins.forEach {
            pluginsCoroutineScope.launch {
                if (it.title in addedPlugins) {
                    throw IllegalStateException("Found another plugin with the same title: ${it.title} (its version is ${it.version})")
                }
                if (it.pluginApiVersion != APPLICATION_METADATA.version) {
                    logger.error("Plugin ${it.title} (version ${it.version}) was built for plugin API version ${it.pluginApiVersion}, but this server is running version ${APPLICATION_METADATA.version}. This plugin won't be loaded.")
                    return@launch
                }
                logger.info("Loading plugin ${it.title} v${it.version} (author: ${it.author})")
                it.onLoad()
                logger.info("Loaded plugin ${it.title} v${it.version}")
                addedPlugins += it.title
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
        }


        val dependencies = DI {
            bindSingleton { configuration }
            bindSingleton { database }
            bindSingleton<DataRegistrarProviderInterface> { DataRegistrarImpl() }
            bindSingleton<CommandLineInterface> { CommandLine.instance }
            bindSingleton<AuthorizationInterface> { AuthorizationProviderImpl() }
        }
        @OptIn(UnsafeAPI::class) setProvidersCatalog(ProvidersCatalogImpl(dependencies))
        plugins
    }
    // Configure dependencies
    SchoolsByParser.setSubdomain(ProvidersCatalog.configuration.schoolsByConfiguration.baseUrl)
    logger.debug("SchoolsByParser subdomain: ${SchoolsByParser.schoolSubdomain}")
    System.getenv()["SENTRY_DSN"]?.let {
        Sentry.init(it)
        logger.debug("Sentry initialized")
    }
    Sentry.configureScope {
        it.setTag("schools-by-subdomain", SchoolsByParser.schoolSubdomain)
        it.setTag("schools-by-parser-version", schoolsByParserVersion)
        it.setTag("version", metadata.version)
    }
    logger.debug("Starting server...")

    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        configureKtorPlugins()
        configureStartStopListener(plugins)
        routing {
            registerAllRoutes()
        }
    }

    plugins.forEach {
        it.onUnload()
    }
}
