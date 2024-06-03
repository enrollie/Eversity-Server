/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 8/24/22, 7:50 PM
 */

package by.enrollie.util

import by.enrollie.annotations.UnsafeAPI
import by.enrollie.data_classes.EventConstraints
import by.enrollie.data_classes.TimetableCell
import by.enrollie.data_classes.TimetablePlaces
import by.enrollie.exceptions.RateLimitException
import by.enrollie.privateProviders.CommandLineInterface
import by.enrollie.privateProviders.EventSchedulerInterface
import by.enrollie.providers.ConfigurationInterface
import by.enrollie.providers.DataSourceCommunicatorInterface
import by.enrollie.providers.DatabaseProviderInterface
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.neitex.SchoolsByParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import kotlin.time.Duration.Companion.seconds

@UnsafeAPI
object StartupRoutine {
    private const val DEFAULT_DELAY = 1_000L
    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    private val logger = LoggerFactory.getLogger(this::class.java)
    fun initialize(
        schedulerInterface: EventSchedulerInterface,
        commandLine: CommandLineInterface,
        configurationInterface: ConfigurationInterface,
        databaseProviderInterface: DatabaseProviderInterface,
        dataSourceCommunicator: DataSourceCommunicatorInterface
    ) {
        commandLine.registerCommand(LiteralArgumentBuilder.literal<Unit?>("exit").executes {
            logger.info("Shutting down...")
            Runtime.getRuntime().exit(0)
            0
        })
        schedulerInterface.scheduleOnce(DEFAULT_DELAY) {
            coroutineScope.launch {
                updateBellsTimetable(databaseProviderInterface)
            }
        }.also {
            logger.debug("Startup routine scheduled, id=$it")
        }
        commandLine.registerCommand(LiteralArgumentBuilder.literal<Unit?>("resyncData").executes {
            logger.info("Forcing data resync now...")
            coroutineScope.launch {
                synchronizationMaintenance(
                    databaseProviderInterface, configurationInterface, null, dataSourceCommunicator
                )
            }
            0
        })
        val runTime = LocalDateTime.now().let {
            val currDaySync =
                it.toLocalDate().atStartOfDay().plusSeconds(configurationInterface.schoolsByConfiguration.resyncDelay)
            if (it.isAfter(currDaySync)) currDaySync.plusDays(1)
            else currDaySync
        }
        if (System.getenv()["EVERSITY_ENABLE_SYNC"] != null) {
            logger.warn("EVERSITY_ENABLE_SYNC is set. Automatic synchronization will be performed according to configuration. Use at your own risk.")
            schedulerInterface.scheduleOnce(ChronoUnit.MILLIS.between(LocalDateTime.now(), runTime)) {
                coroutineScope.launch {
                    synchronizationMaintenance(
                        databaseProviderInterface, configurationInterface, schedulerInterface, dataSourceCommunicator
                    )
                }
            }
        }
    }

    private suspend fun synchronizationMaintenance(
        databaseProvider: DatabaseProviderInterface,
        configuration: ConfigurationInterface,
        scheduler: EventSchedulerInterface?,
        dataSourceCommunicator: DataSourceCommunicatorInterface
    ) {
        try {
            updateBellsTimetable(databaseProvider)
        } catch (e: Exception) {
            logger.error("Exception thrown in updateBellsTemplate()", e)
        }
        logger.debug("Beginning database synchronization maintenance")
        try {
            databaseProvider.classesProvider.getClasses().forEach {
                try {
                    val syncID = dataSourceCommunicator.addClassToSyncQueue(it.id)
                    logger.debug("Class ${it.id} (${it.title}) has been added to the sync queue (syncID=$syncID)")
                } catch (e: RateLimitException) {
                    logger.debug("Class ${it.id} (${it.title}) was synced recently, skipping...")
                }
                delay(60.seconds.inWholeMilliseconds)
            }
        } catch (e: Exception) {
            logger.error("An exception was thrown during classes sync scheduling", e)
        }
        if (scheduler != null) {
            val nextRunDateTime =
                LocalDate.now().plusDays(1).atStartOfDay().plusSeconds(configuration.schoolsByConfiguration.resyncDelay)
            val nextRunID = scheduler.scheduleOnce(ChronoUnit.MILLIS.between(LocalDateTime.now(), nextRunDateTime)) {
                coroutineScope.launch {
                    synchronizationMaintenance(databaseProvider, configuration, scheduler, dataSourceCommunicator)
                }
            }
            logger.trace("Next synchronization maintenance job was scheduled with ID $nextRunID")
        }
    }

    private suspend fun updateBellsTimetable(databaseProvider: DatabaseProviderInterface) {
        logger.debug("Beginning to update bells timetable")
        val bellsResult = SchoolsByParser.SCHOOL.getBells()
        if (bellsResult.isFailure) {
            logger.error("Failed to get bells timetable", bellsResult.exceptionOrNull())
            return
        }
        val firstShift = bellsResult.getOrThrow().first.map { timetablePlace ->
            TimetableCell(timetablePlace.place, timetablePlace.constraints.let {
                EventConstraints(
                    LocalTime.of(it.startHour.toInt(), it.startMinute.toInt()),
                    LocalTime.of(it.endHour.toInt(), it.endMinute.toInt())
                )
            })
        }
        val secondShift = bellsResult.getOrThrow().second.map { timetablePlace ->
            TimetableCell(timetablePlace.place, timetablePlace.constraints.let {
                EventConstraints(
                    LocalTime.of(it.startHour.toInt(), it.startMinute.toInt()),
                    LocalTime.of(it.endHour.toInt(), it.endMinute.toInt())
                )
            })
        }
        val timetable = TimetablePlaces(firstShift, secondShift)
        val prevHashcode = try {
            databaseProvider.timetablePlacingProvider.getTimetablePlaces().hashCode()
        } catch (e: Exception) {
            logger.debug("Database seems to have no timetable, updating forcefully")
            null
        }
        if (prevHashcode != timetable.hashCode()) {
            databaseProvider.timetablePlacingProvider.updateTimetablePlaces(timetable)
            logger.debug("Timetable updated")
        } else {
            logger.debug("Timetable is up to date")
        }
    }
}
