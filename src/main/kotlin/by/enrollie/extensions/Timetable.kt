/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/15/22, 1:25 AM
 */

package by.enrollie.extensions

import by.enrollie.data_classes.Timetable
import by.enrollie.data_classes.TimetableLessonCell
import org.joda.time.DateTime

fun Timetable.Companion.fromParserTimetable(timetable: com.neitex.Timetable) = Timetable(
    timetable.monday.map { TimetableLessonCell(it.place.toInt(), it.title) },
    timetable.tuesday.map { TimetableLessonCell(it.place.toInt(), it.title) },
    timetable.wednesday.map { TimetableLessonCell(it.place.toInt(), it.title) },
    timetable.thursday.map { TimetableLessonCell(it.place.toInt(), it.title) },
    timetable.friday.map { TimetableLessonCell(it.place.toInt(), it.title) },
    timetable.saturday.map { TimetableLessonCell(it.place.toInt(), it.title) },
    listOf(),
    DateTime.now(),
    null
)
