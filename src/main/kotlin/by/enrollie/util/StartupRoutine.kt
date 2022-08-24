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
import by.enrollie.impl.ProvidersCatalog
import by.enrollie.privateProviders.EventSchedulerInterface
import com.neitex.SchoolsByParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.time.LocalTime

@UnsafeAPI
object StartupRoutine {
    private const val DEFAULT_DELAY = 1_000L
    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    private val logger = LoggerFactory.getLogger(this::class.java)
    fun schedule(schedulerInterface: EventSchedulerInterface) {
        schedulerInterface.scheduleOnce(DEFAULT_DELAY) {
            coroutineScope.launch {
                updateBellsTimetable()
            }
            //TODO: sync data with schools.by
        }.also {
            logger.debug("Startup routine scheduled, id=$it")
        }
    }

    private suspend fun updateBellsTimetable() {
        logger.debug("Beginning to update bells timetable")
        val bellsResult = SchoolsByParser.SCHOOL.getBells()
        if (bellsResult.isFailure) {
            logger.error("Failed to get bells timetable", bellsResult.exceptionOrNull())
            return
        }
        val firstShift = bellsResult.getOrThrow().first.map {
            TimetableCell(it.place, it.constraints.let {
                EventConstraints(
                    LocalTime.of(it.startHour.toInt(), it.startMinute.toInt()),
                    LocalTime.of(it.endHour.toInt(), it.endMinute.toInt())
                )
            })
        }
        val secondShift = bellsResult.getOrThrow().second.map {
            TimetableCell(it.place, it.constraints.let {
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
