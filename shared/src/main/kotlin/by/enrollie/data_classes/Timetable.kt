/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/11/22, 5:00 PM
 */

package by.enrollie.data_classes

import kotlinx.serialization.Serializable
import org.joda.time.DateTime
import java.time.DayOfWeek
import java.util.*

typealias TimetablePlace = Int

@Serializable
data class TimetableLessonCell(
    val place: TimetablePlace,
    val title: String
)

data class Timetable(
    val monday: List<TimetableLessonCell>,
    val tuesday: List<TimetableLessonCell>,
    val wednesday: List<TimetableLessonCell>,
    val thursday: List<TimetableLessonCell>,
    val friday: List<TimetableLessonCell>,
    val saturday: List<TimetableLessonCell>,
    val sunday: List<TimetableLessonCell>,
    val effectiveSince: DateTime,
    val effectiveUntil: DateTime?
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

    operator fun get(date: Date): List<TimetableLessonCell> = when (val day = DateTime(date).dayOfWeek) {
        DayOfWeek.MONDAY.value -> monday
        DayOfWeek.TUESDAY.value -> tuesday
        DayOfWeek.WEDNESDAY.value -> wednesday
        DayOfWeek.THURSDAY.value -> thursday
        DayOfWeek.FRIDAY.value -> friday
        DayOfWeek.SATURDAY.value -> saturday
        DayOfWeek.SUNDAY.value -> sunday
        else -> throw IllegalArgumentException("Invalid day of week: $day")
    }

    companion object
}
