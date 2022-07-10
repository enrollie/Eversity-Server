/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/10/22, 11:16 PM
 */

package by.enrollie.data_classes

import java.time.LocalDateTime

data class TimetablePlaces(
    private val firstShift: Map<TimetablePlace, EventConstraints>,
    private val secondShift: Map<TimetablePlace, EventConstraints>
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
