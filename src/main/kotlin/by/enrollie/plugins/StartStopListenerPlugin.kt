/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 8/13/22, 11:53 PM
 */

package by.enrollie.plugins

import by.enrollie.annotations.UnsafeAPI
import by.enrollie.data_classes.User
import by.enrollie.data_classes.UserID
import by.enrollie.impl.ProvidersCatalog
import by.enrollie.privateProviders.*
import by.enrollie.providers.ConfigurationInterface
import by.enrollie.providers.DatabaseProviderInterface
import by.enrollie.providers.PluginMetadataInterface
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.engine.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
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
            @Deprecated(
                "Use DatabaseProviderInterface instead. Will be removed in API v1.0",
                replaceWith = ReplaceWith("environment"),
                level = DeprecationLevel.WARNING
            )
            @Suppress("DEPRECATION")
            override val metadata: ApplicationMetadata = object : ApplicationMetadata {
                override val title: String = "Eversity Server"
                override val version: String = ProvidersCatalog.environment.serverVersion
                override val buildTimestamp: Long = ProvidersCatalog.environment.serverBuildTimestamp
            }
            override val configuration: ConfigurationInterface = ProvidersCatalog.configuration
            override val environment: EnvironmentInterface = ProvidersCatalog.environment
            override val database: DatabaseProviderInterface = ProvidersCatalog.databaseProvider
            override val tokenSigner: TokenSignerProvider = object : TokenSignerProvider {
                override fun signToken(user: User, token: String): String = jwtProvider.signToken(user, token)
                override fun signToken(userID: UserID, token: String): String = jwtProvider.signToken(userID, token)

                override fun getSignedTokenUser(token: String): User? =
                    runCatching { jwtProvider.getJwtVerifier().verify(token) }.fold({ it }, { return null })
                        .getClaim("user")?.asInt()?.let { userID ->
                        ProvidersCatalog.databaseProvider.usersProvider.getUser(userID)
                    }

                override fun verifySignedToken(token: String): Boolean {
                    runCatching { jwtProvider.getJwtVerifier().verify(token).getClaim("token")?.asString() }.fold({ it }, { return false })?.let {
                        return ProvidersCatalog.databaseProvider.authenticationDataProvider.getUserByToken(it) != null
                    } ?: return false
                }

            }
            override val eventScheduler: EventSchedulerInterface = ProvidersCatalog.eventScheduler
            override val templatingEngine: TemplatingEngineInterface = ProvidersCatalog.templatingEngine
            override val commandLine: CommandLineInterface = ProvidersCatalog.commandLine
            override val routing: RoutingInterface = object : RoutingInterface {
                override fun createRoute(pluginMetadata: PluginMetadataInterface, routeHandler: Route.() -> Unit) {
                    routing {
                        route("/plugin/${pluginMetadata.name}") {
                            authenticate("jwt") {
                                routeHandler()
                            }
                        }
                    }
                }

            }
        }
        plugins.forEach {
            logger.debug("Calling onStart() on ${it.name}")
            it.onStart(applicationProvider)
        }
    }
    val pluginNamesMap = plugins.associateBy { it.name }
    createApplicationPlugin("PluginHelper") {
        onCallRespond { call ->
            if (call.request.uri.startsWith("/plugin/")) {
                val pluginName = call.request.uri.removeSuffix("/plugin/").substringBefore("/")
                call.response.headers.append("X-Served-By",
                    pluginNamesMap[pluginName]?.let { "${it.name}-${it.version}" } ?: "UnknownPlugin")
            }
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
