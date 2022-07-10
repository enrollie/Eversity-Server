/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/10/22, 11:16 PM
 */

package by.enrollie.data_classes

import org.joda.time.DateTime
import java.time.DayOfWeek
import java.util.*

typealias TimetablePlace = Int

data class Timetable(
    val classID: ClassID,
    val monday: List<Pair<Subject, TimetablePlace>>,
    val tuesday: List<Pair<Subject, TimetablePlace>>,
    val wednesday: List<Pair<Subject, TimetablePlace>>,
    val thursday: List<Pair<Subject, TimetablePlace>>,
    val friday: List<Pair<Subject, TimetablePlace>>,
    val saturday: List<Pair<Subject, TimetablePlace>>,
    val sunday: List<Pair<Subject, TimetablePlace>>,
    val effectiveSince: DateTime,
    val effectiveUntil: DateTime?
) {
    operator fun get(day: DayOfWeek): List<Pair<Subject, TimetablePlace>> = when (day) {
        DayOfWeek.MONDAY -> monday
        DayOfWeek.TUESDAY -> tuesday
        DayOfWeek.WEDNESDAY -> wednesday
        DayOfWeek.THURSDAY -> thursday
        DayOfWeek.FRIDAY -> friday
        DayOfWeek.SATURDAY -> saturday
        DayOfWeek.SUNDAY -> sunday
    }

    operator fun get(date: Date): List<Pair<Subject, TimetablePlace>> = when (val day = DateTime(date).dayOfWeek) {
        DayOfWeek.MONDAY.value -> monday
        DayOfWeek.TUESDAY.value -> tuesday
        DayOfWeek.WEDNESDAY.value -> wednesday
        DayOfWeek.THURSDAY.value -> thursday
        DayOfWeek.FRIDAY.value -> friday
        DayOfWeek.SATURDAY.value -> saturday
        DayOfWeek.SUNDAY.value -> sunday
        else -> throw IllegalArgumentException("Invalid day of week: $day")
    }
}
