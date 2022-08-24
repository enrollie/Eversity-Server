/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 8/11/22, 7:39 PM
 */

package by.enrollie.data_classes

import java.time.LocalDateTime

typealias TimetablePlace = Int

@kotlinx.serialization.Serializable
data class TimetableCell(
    val place: TimetablePlace,
    val timeConstraints: EventConstraints
)

/**
 * Data class representing timetable of bells in a school day.
 */
@kotlinx.serialization.Serializable
data class TimetablePlaces(
    val firstShift: List<TimetableCell>, val secondShift: List<TimetableCell>
) {
    operator fun get(shift: TeachingShift, place: TimetablePlace): TimetableCell? = when (shift) {
        TeachingShift.FIRST -> firstShift.firstOrNull { it.place == place }
        TeachingShift.SECOND -> secondShift.firstOrNull { it.place == place }
    }

    fun getPlace(dateTime: LocalDateTime): Pair<TimetablePlace?, TimetablePlace?> {
        return Pair(
            firstShift.firstOrNull { it.timeConstraints.isInTimeConstraints(dateTime.toLocalTime()) }?.place,
            secondShift.firstOrNull { it.timeConstraints.isInTimeConstraints(dateTime.toLocalTime()) }?.place
        )
    }
}
