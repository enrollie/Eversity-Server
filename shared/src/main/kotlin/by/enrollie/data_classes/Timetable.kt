/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/17/22, 10:22 PM
 */

package by.enrollie.data_classes

import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

typealias TimetablePlace = Int

@Serializable
data class TimetableLessonCell(
    val place: TimetablePlace, val title: String
)

data class Timetable(
    val monday: List<TimetableLessonCell>,
    val tuesday: List<TimetableLessonCell>,
    val wednesday: List<TimetableLessonCell>,
    val thursday: List<TimetableLessonCell>,
    val friday: List<TimetableLessonCell>,
    val saturday: List<TimetableLessonCell>,
    val sunday: List<TimetableLessonCell>,
    val effectiveSince: LocalDateTime,
    val effectiveUntil: LocalDateTime?
) {
    operator fun get(day: DayOfWeek): List<TimetableLessonCell> = when (day) {
        DayOfWeek.MONDAY -> monday
        DayOfWeek.TUESDAY -> tuesday
        DayOfWeek.WEDNESDAY -> wednesday
        DayOfWeek.THURSDAY -> thursday
        DayOfWeek.FRIDAY -> friday
        DayOfWeek.SATURDAY -> saturday
        DayOfWeek.SUNDAY -> sunday
    }

    operator fun get(date: LocalDate): List<TimetableLessonCell> = when (val day = date.dayOfWeek) {
        DayOfWeek.MONDAY -> monday
        DayOfWeek.TUESDAY -> tuesday
        DayOfWeek.WEDNESDAY -> wednesday
        DayOfWeek.THURSDAY -> thursday
        DayOfWeek.FRIDAY -> friday
        DayOfWeek.SATURDAY -> saturday
        DayOfWeek.SUNDAY -> sunday
        else -> throw IllegalArgumentException("Invalid day of week: $day")
    }

    companion object
}
