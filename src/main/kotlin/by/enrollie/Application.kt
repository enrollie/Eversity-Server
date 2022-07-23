/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/23/22, 3:48 AM
 */

package by.enrollie

import by.enrollie.annotations.UnsafeAPI
import by.enrollie.impl.*
import by.enrollie.plugins.configureKtorPlugins
import by.enrollie.plugins.configureStartStopListener
import by.enrollie.privateProviders.ApplicationMetadata
import by.enrollie.providers.*
import by.enrollie.util.getServices
import com.neitex.SchoolsByParser
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.sentry.Sentry
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
    val plugins = run { // Configure providers
        val layer = run { // Configure layer
            File("plugins").mkdir()
            val pluginsFinder = ModuleFinder.of(Path("plugins"))
            val pluginNames = pluginsFinder.findAll().map { it.descriptor().name() }
            val pluginsConfiguration =
                ModuleLayer.boot().configuration().resolve(pluginsFinder, ModuleFinder.of(), pluginNames)
            ModuleLayer.boot().defineModulesWithOneLoader(pluginsConfiguration, ClassLoader.getSystemClassLoader())
        }

        val database = getServices<DatabaseProviderInterface>(layer).let {
            require(it.size == 1) { "Exactly one database provider must be registered, however ${it.size} are found (IDs: ${it.map { it.databaseID }})" }
            it.first()
        }
        logger.info("Using database: ${database.databaseID}")
        val configuration = getServices<ConfigurationInterface>(layer).let {
            require(it.size == 1) { "Exactly one configuration provider must be registered, however ${it.size} are found (IDs: ${it.map { it.configurationID }})" }
            it.first()
        }
        logger.info("Using configuration: ${configuration.configurationID}")
        val plugins = getServices<PluginMetadataInterface>(layer)
        val addedPlugins = setOf<String>()
        plugins.forEach {
            if (it.title in addedPlugins) {
                throw IllegalStateException("Found another plugin with the same title: ${it.title} (its version is ${it.version})")
            }
            logger.info("Loading plugin ${it.title} v${it.version} (author: ${it.author})")
            it.onLoad()
            logger.info("Loaded plugin ${it.title} v${it.version}")
        }

        val dependencies = DI {
            bindSingleton { configuration }
            bindSingleton { database }
            bindSingleton<DataRegistrarProviderInterface> { DataRegistrarImpl() }
            bindSingleton<CommandLineInterface> { CommandLine.instance }
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
    logger.debug("Starting embedded server...")

    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        configureKtorPlugins()
        configureStartStopListener(plugins)
    }

    plugins.forEach {
        it.onUnload()
    }
}
