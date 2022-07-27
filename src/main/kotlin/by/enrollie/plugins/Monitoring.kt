/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/28/22, 12:01 AM
 */
@file:Suppress("DEPRECATION") // New Relic Registry will throw that one deprecated exception

package by.enrollie.plugins

import com.newrelic.telemetry.micrometer.NewRelicRegistry
import com.newrelic.telemetry.micrometer.NewRelicRegistryConfig
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.request.*
import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.config.MissingRequiredConfigurationException
import io.micrometer.jmx.JmxConfig
import io.micrometer.jmx.JmxMeterRegistry
import org.slf4j.event.Level

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
            NewRelicRegistry.builder(object : NewRelicRegistryConfig {
                override fun apiKey(): String? = System.getenv("NEW_RELIC_API_KEY")
                override fun serviceName(): String = "Eversity"
                override fun get(key: String): String? = null
            }).build()
        } catch (_: MissingRequiredConfigurationException) {
            JmxMeterRegistry(JmxConfig.DEFAULT, Clock.SYSTEM) // Fallback to JMX if New Relic is not set up
        } catch (_: IllegalArgumentException) {
            JmxMeterRegistry(JmxConfig.DEFAULT, Clock.SYSTEM) // Fallback to JMX if New Relic is not set up
        }
    }
}
