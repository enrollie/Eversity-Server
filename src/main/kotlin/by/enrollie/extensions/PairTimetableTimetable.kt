/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/15/22, 1:25 AM
 */

package by.enrollie.extensions

import by.enrollie.data_classes.Timetable
import com.neitex.TwoShiftsTimetable
import org.joda.time.DateTime

fun TwoShiftsTimetable.toPairOfTimetables(): Pair<Timetable, Timetable> {
    return Pair(
        Timetable(
            monday.first.map { it.toTimetableLessonCell() },
            tuesday.first.map { it.toTimetableLessonCell() },
            wednesday.first.map { it.toTimetableLessonCell() },
            thursday.first.map { it.toTimetableLessonCell() },
            friday.first.map { it.toTimetableLessonCell() },
            saturday.first.map { it.toTimetableLessonCell() },
            listOf(),
            DateTime.now(),
            null
        ),
        Timetable(
            monday.second.map { it.toTimetableLessonCell() },
            tuesday.second.map { it.toTimetableLessonCell() },
            wednesday.second.map { it.toTimetableLessonCell() },
            thursday.second.map { it.toTimetableLessonCell() },
            friday.second.map { it.toTimetableLessonCell() },
            saturday.second.map { it.toTimetableLessonCell() },
            listOf(),
            DateTime.now(),
            null
        )
    )
}
