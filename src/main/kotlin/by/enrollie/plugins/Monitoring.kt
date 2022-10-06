/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 8/24/22, 7:50 PM
 */
@file:Suppress("DEPRECATION") // New Relic Registry will throw that one deprecated exception

package by.enrollie.plugins

import by.enrollie.data_classes.UserID
import com.github.ajalt.mordant.rendering.TextColors
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.request.*
import io.ktor.util.*
import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.config.MissingRequiredConfigurationException
import io.micrometer.datadog.DatadogConfig
import io.micrometer.datadog.DatadogMeterRegistry
import io.micrometer.jmx.JmxConfig
import io.micrometer.jmx.JmxMeterRegistry
import org.apache.commons.lang3.StringUtils
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toKotlinDuration

internal fun Application.configureMonitoring() {
    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path().startsWith("/") }
        callIdMdc("call-id")
        this.format { call ->
            val callId = call.callId ?: "N/A"
            val method = call.request.httpMethod.value.let {
                when (it) {
                    "GET" -> TextColors.green(it)
                    "POST" -> TextColors.brightBlue(it)
                    "PUT", "PATCH" -> TextColors.rgb("#ffa500")(it)
                    "DELETE" -> TextColors.red(it)
                    else -> it
                }
            }
            val path = StringUtils.abbreviateMiddle(call.request.path(), "....", 64)
            val status = call.response.status()?.value?.let {
                when (it) {
                    in 100..199 -> TextColors.gray(it.toString())
                    in 200..299 -> TextColors.green(it.toString())
                    in 300..399 -> TextColors.cyan(it.toString())
                    in 400..499 -> TextColors.yellow(it.toString())
                    in 500..599 -> TextColors.red(it.toString())
                    else -> TextColors.magenta(it.toString())
                }
            } ?: "N/A"
            val startTime = call.attributes.getOrNull(RequestStartTimeAttributeKey)
            val endTime = call.attributes.getOrNull(RequestEndTimeAttributeKey) ?: System.currentTimeMillis()
            val duration = if (startTime != null) Duration.ofMillis(endTime - startTime) else null
            val user = (call.attributes.getOrNull(AttributeKey("userID")) as? UserID?) ?: "N/A"

            "$method @ $path (Call ID: $callId, Duration: ${
                duration?.toKotlinDuration()?.toString(DurationUnit.MILLISECONDS) ?: "N/A"
            }, UserID: $user): $status"
        }
    }
    install(CallId) {
        val dictionary = String((('a'..'z') + ('A'..'Z') + ('0'..'9') + listOf('-', '_', '#')).toCharArray())
        header(HttpHeaders.XRequestId)
        generate(15, dictionary)
        verify(dictionary)
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
