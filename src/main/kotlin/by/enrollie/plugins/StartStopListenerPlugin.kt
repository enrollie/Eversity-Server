/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 8/7/22, 3:49 AM
 */

package by.enrollie.plugins

import by.enrollie.APPLICATION_METADATA
import by.enrollie.annotations.UnsafeAPI
import by.enrollie.data_classes.User
import by.enrollie.impl.ProvidersCatalog
import by.enrollie.privateProviders.ApplicationMetadata
import by.enrollie.privateProviders.ApplicationProvider
import by.enrollie.privateProviders.TokenSignerProvider
import by.enrollie.providers.ConfigurationInterface
import by.enrollie.providers.DatabaseProviderInterface
import by.enrollie.providers.PluginMetadataInterface
import io.ktor.server.application.*
import io.ktor.server.engine.*
import org.slf4j.LoggerFactory

object StartStopListenerPlugin {
    private var stopHook: Thread? = null

    fun issueStopCommand() {
        stopHook?.start()
    }

    /**
     * This function sets stop hook to [thread] by [configureStartStopListener] function. DO NOT CALL THIS FUNCTION FROM OUTSIDE.
     */
    @UnsafeAPI
    internal fun setStopHook(thread: Thread) {
        stopHook = thread
    }
}

internal fun Application.configureStartStopListener(plugins: List<PluginMetadataInterface>) {
    val logger = LoggerFactory.getLogger("StartStopListener")
    @OptIn(UnsafeAPI::class) StartStopListenerPlugin.setStopHook(object : Thread() {
        override fun run() {
            environment.monitor.raise(ApplicationStopPreparing, environment)
            if (environment is ApplicationEngineEnvironment) {
                (environment as ApplicationEngineEnvironment).stop()
            } else this@configureStartStopListener.dispose()
        }
    })
    environment.monitor.subscribe(ApplicationStarted) {
        val applicationProvider = object : ApplicationProvider {
            override val metadata: ApplicationMetadata = APPLICATION_METADATA
            override val configuration: ConfigurationInterface = ProvidersCatalog.configuration
            override val database: DatabaseProviderInterface = ProvidersCatalog.databaseProvider
            override val tokenSigner: TokenSignerProvider = object : TokenSignerProvider {
                override fun signToken(user: User, token: String): String = jwtProvider.signToken(user, token)
            }
        }
        plugins.forEach {
            logger.debug("Calling onStart() on ${it.name}")
            it.onStart(applicationProvider)
        }
    }
    environment.monitor.subscribe(ApplicationStopping) {
        plugins.forEach {
            try {
                it.onStop()
            } catch (e: Exception) {
                logger.error("Exception while calling onStop() on ${it.name}", e)
            } catch (e: Error) {
                logger.error("Error while calling onStop() on ${it.name}", e)
            }
        }
    }
}
