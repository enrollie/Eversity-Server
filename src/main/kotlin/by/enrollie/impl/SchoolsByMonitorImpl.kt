/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 8/24/22, 7:50 PM
 */

package by.enrollie.impl

import by.enrollie.providers.SchoolsByMonitorInterface
import com.neitex.SchoolsByParser
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicLong

class SchoolsByMonitorImpl : SchoolsByMonitorInterface {
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
        ProvidersCatalog.eventScheduler.cancelEvent(eventID)
        nextCheckScheduled = System.currentTimeMillis() + 5
        eventID = ProvidersCatalog.eventScheduler.scheduleRepeating(
            5, if (isAvailable) {
                ProvidersCatalog.configuration.schoolsByConfiguration.recheckInterval
            } else {
                ProvidersCatalog.configuration.schoolsByConfiguration.recheckOnDownInterval
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
            nextCheckScheduled =
                System.currentTimeMillis() + ProvidersCatalog.configuration.schoolsByConfiguration.recheckOnDownInterval
            ProvidersCatalog.eventScheduler.cancelEvent(eventID)
            eventID = ProvidersCatalog.eventScheduler.scheduleRepeating(
                ProvidersCatalog.configuration.schoolsByConfiguration.recheckOnDownInterval,
                ProvidersCatalog.configuration.schoolsByConfiguration.recheckOnDownInterval
            ) { this::check.invoke().invoke(true) }
        } else if (!result && afterFail) {
            nextCheckScheduled =
                System.currentTimeMillis() + ProvidersCatalog.configuration.schoolsByConfiguration.recheckOnDownInterval
        } else if (result && afterFail) {
            ProvidersCatalog.eventScheduler.cancelEvent(eventID)
            nextCheckScheduled =
                System.currentTimeMillis() + ProvidersCatalog.configuration.schoolsByConfiguration.recheckInterval
            eventID = ProvidersCatalog.eventScheduler.scheduleRepeating(
                ProvidersCatalog.configuration.schoolsByConfiguration.recheckInterval,
                ProvidersCatalog.configuration.schoolsByConfiguration.recheckInterval
            ) { this::check.invoke().invoke(false) }
        } else {
            nextCheckScheduled =
                System.currentTimeMillis() + ProvidersCatalog.configuration.schoolsByConfiguration.recheckInterval
        }
    }

    override fun init() {
        eventID = ProvidersCatalog.eventScheduler.scheduleRepeating(
            0, ProvidersCatalog.configuration.schoolsByConfiguration.recheckInterval
        ) {
            this.check(false)
        }
    }
}
