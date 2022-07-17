/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/18/22, 2:18 AM
 */

package by.enrollie.data_classes

import java.time.LocalDateTime

typealias TimetablePlace = Int

data class TimetablePlaces(
    val firstShift: Map<TimetablePlace, EventConstraints>, val secondShift: Map<TimetablePlace, EventConstraints>
) {
    operator fun get(shift: TeachingShift, place: TimetablePlace): EventConstraints? = when (shift) {
        TeachingShift.FIRST -> firstShift[place]
        TeachingShift.SECOND -> secondShift[place]
    }

    fun getCurrentPlace(dateTime: LocalDateTime): Pair<TimetablePlace?, TimetablePlace?> {
        return Pair(
            firstShift.entries.firstOrNull { it.value.isInTimeConstraints(dateTime.toLocalTime()) }?.key,
            secondShift.entries.firstOrNull { it.value.isInTimeConstraints(dateTime.toLocalTime()) }?.key
        )
    }
}
