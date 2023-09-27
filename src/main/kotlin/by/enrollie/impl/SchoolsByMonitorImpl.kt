/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 8/24/22, 7:50 PM
 */

package by.enrollie.impl

import by.enrollie.privateProviders.CommandLineInterface
import by.enrollie.privateProviders.EventSchedulerInterface
import by.enrollie.providers.ConfigurationInterface
import by.enrollie.providers.SchoolsByMonitorInterface
import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.neitex.SchoolsByParser
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicLong

class SchoolsByMonitorImpl(
    private val configuration: ConfigurationInterface,
    private val eventScheduler: EventSchedulerInterface,
    private val commandLine: CommandLineInterface
) : SchoolsByMonitorInterface {
    @Volatile
    private var _isAvailable = true
    override val isAvailable: Boolean
        get() = _isAvailable

    @Volatile
    private var nextCheckScheduled = 0L
    override val untilNextCheck: Long
        get() = nextCheckScheduled - System.currentTimeMillis()

    @Volatile
    private var eventID: String = ""
    private var client = HttpClient(CIO) {
        engine {
            requestTimeout = 5000
        }
    }
    private val logger = LoggerFactory.getLogger("SchoolsByMonitor")
    private val checkIDs = AtomicLong(0)

    override fun forceRecheck() {
        logger.debug("Forcing recheck...")
        eventScheduler.cancelEvent(eventID)
        nextCheckScheduled = System.currentTimeMillis() + 5
        eventID = eventScheduler.scheduleRepeating(
            5, if (isAvailable) {
                configuration.schoolsByConfiguration.recheckInterval
            } else {
                configuration.schoolsByConfiguration.recheckOnDownInterval
            }
        ) {
            check(!isAvailable)
        }
    }

    private fun checkSchoolsBy() = runBlocking {
        kotlin.runCatching { client.get(SchoolsByParser.schoolSubdomain) }.let {
            if (it.isFailure) return@runBlocking false
            if (it.getOrNull()?.status != HttpStatusCode.OK) return@runBlocking false
            return@runBlocking true
        }
    }

    private val check: (Boolean) -> Unit = { afterFail ->
        val checkID = checkIDs.incrementAndGet()
        logger.debug("Starting check with ID $checkID...")
        val result = checkSchoolsBy()
        _isAvailable = result
        logger.debug("Check $checkID finished with result $result")
        if (!result && !afterFail) {
            nextCheckScheduled = System.currentTimeMillis() + configuration.schoolsByConfiguration.recheckOnDownInterval
            eventScheduler.cancelEvent(eventID)
            eventID = eventScheduler.scheduleRepeating(
                configuration.schoolsByConfiguration.recheckOnDownInterval,
                configuration.schoolsByConfiguration.recheckOnDownInterval
            ) { this::check.invoke().invoke(true) }
        } else if (!result && afterFail) {
            nextCheckScheduled = System.currentTimeMillis() + configuration.schoolsByConfiguration.recheckOnDownInterval
        } else if (result && afterFail) {
            eventScheduler.cancelEvent(eventID)
            nextCheckScheduled = System.currentTimeMillis() + configuration.schoolsByConfiguration.recheckInterval
            eventID = eventScheduler.scheduleRepeating(
                configuration.schoolsByConfiguration.recheckInterval,
                configuration.schoolsByConfiguration.recheckInterval
            ) { this::check.invoke().invoke(false) }
        } else {
            nextCheckScheduled = System.currentTimeMillis() + configuration.schoolsByConfiguration.recheckInterval
        }
    }

    override fun init() {
        eventID = eventScheduler.scheduleRepeating(
            0, configuration.schoolsByConfiguration.recheckInterval
        ) {
            this.check(false)
        }
        commandLine.registerCommand(LiteralArgumentBuilder.literal<Unit?>("schoolsByStatus").executes {
            commandLine.writeMessage(
                "Schools.by is ${if (isAvailable) "available" else "unavailable"}" + " (next check in $untilNextCheck ms (${untilNextCheck / 1000} s))"
            )
            Command.SINGLE_SUCCESS
        })
        commandLine.registerCommand(LiteralArgumentBuilder.literal<Unit?>("schoolsByForceRecheck").executes {
            forceRecheck()
            commandLine.writeMessage("Recheck scheduled")
            Command.SINGLE_SUCCESS
        })
        commandLine.registerCommand(
            LiteralArgumentBuilder.literal<Unit?>("schoolsByForce")
                .then(RequiredArgumentBuilder.argument<Unit?, Boolean?>("status", BoolArgumentType.bool()).executes {
                    _isAvailable = it.getArgument("status", Boolean::class.java)
                    commandLine.writeMessage("Forced status to ${if (isAvailable) "available" else "unavailable"}")
                    Command.SINGLE_SUCCESS
                })
        )
    }
}
