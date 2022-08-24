/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 8/24/22, 7:50 PM
 */
@file:Suppress("DEPRECATION") // New Relic Registry will throw that one deprecated exception

package by.enrollie.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.request.*
import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.config.MissingRequiredConfigurationException
import io.micrometer.datadog.DatadogConfig
import io.micrometer.datadog.DatadogMeterRegistry
import io.micrometer.jmx.JmxConfig
import io.micrometer.jmx.JmxMeterRegistry
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.time.Duration

internal fun Application.configureMonitoring() {
    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path().startsWith("/") }
        callIdMdc("call-id")
    }
    install(CallId) {
        header(HttpHeaders.XRequestId)
        verify { callId: String ->
            callId.isNotEmpty()
        }
        generate {
            it.request.headers[HttpHeaders.XRequestId] ?: (it.request.path()
                .hashCode() + it.request.headers.hashCode()).toString()
        }
    }
    install(MicrometerMetrics) {
        registry = try {
            DatadogMeterRegistry.builder(object : DatadogConfig {
                override fun apiKey(): String =
                    System.getenv()["DATADOG_API_KEY"] ?: throw IllegalArgumentException("DATADOG_API_KEY is not set")

                override fun uri(): String =
                    System.getenv()["DATADOG_API_URL"] ?: throw IllegalArgumentException("DATADOG_API_URL is not set")

                override fun step(): Duration = Duration.ofSeconds(10)
                override fun get(key: String): String? = null
            }).build()
        } catch (e: MissingRequiredConfigurationException) {
            LoggerFactory.getLogger("Monitoring").warn("Datadog is not configured", e)
            JmxMeterRegistry(JmxConfig.DEFAULT, Clock.SYSTEM) // Fallback to JMX if Datadog is not set up
        } catch (e: IllegalArgumentException) {
            LoggerFactory.getLogger("Monitoring").warn("Datadog is not configured", e)
            JmxMeterRegistry(JmxConfig.DEFAULT, Clock.SYSTEM) // Fallback to JMX if Datadog is not set up
        }
    }
}
