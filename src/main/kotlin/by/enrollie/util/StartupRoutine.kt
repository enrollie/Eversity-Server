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
import by.enrollie.impl.ProvidersCatalog
import by.enrollie.privateProviders.CommandLineInterface
import by.enrollie.privateProviders.EventSchedulerInterface
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

@UnsafeAPI
object StartupRoutine {
    private const val DEFAULT_DELAY = 1_000L
    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    private val logger = LoggerFactory.getLogger(this::class.java)
    fun initialize(schedulerInterface: EventSchedulerInterface, commandLine: CommandLineInterface) {
        commandLine.registerCommand(LiteralArgumentBuilder.literal<Unit?>("exit").executes {
            logger.info("Shutting down...")
            Runtime.getRuntime().exit(0)
            0
        })
        schedulerInterface.scheduleOnce(DEFAULT_DELAY) {
            coroutineScope.launch {
                updateBellsTimetable()
            }
        }.also {
            logger.debug("Startup routine scheduled, id=$it")
        }
        val runTime = LocalDate.now().plusDays(1).atStartOfDay()
        schedulerInterface.scheduleOnce(ChronoUnit.MILLIS.between(LocalDateTime.now(), runTime)) {
            coroutineScope.launch {
                launch { updateBellsTimetable() }
                ProvidersCatalog.databaseProvider.classesProvider.getClasses().forEach {
                    logger.debug("Scheduling class ${it.id} to sync with schools.by")
                    try {
                        ProvidersCatalog.registrarProvider.addClassToSyncQueue(it.id)
                    } catch (e: RateLimitException) {
                        logger.warn("Rate limit for class ${it.id} exceeded, skipping")
                    }
                    delay(DEFAULT_DELAY)
                }

            }
        }
    }

    private suspend fun updateBellsTimetable() {
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
            ProvidersCatalog.databaseProvider.timetablePlacingProvider.getTimetablePlaces().hashCode()
        } catch (e: Exception) {
            logger.debug("Database seems to have no timetable, updating forcefully")
            null
        }
        if (prevHashcode != timetable.hashCode()) {
            ProvidersCatalog.databaseProvider.timetablePlacingProvider.updateTimetablePlaces(timetable)
            logger.debug("Timetable updated")
        } else {
            logger.debug("Timetable is up to date")
        }
    }
}
