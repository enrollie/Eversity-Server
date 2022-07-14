/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/13/22, 5:43 PM
 */

package by.enrollie.extensions

import by.enrollie.data_classes.TimetableLessonCell
import com.neitex.TimetableLesson

fun TimetableLesson.toTimetableLessonCell() = TimetableLessonCell(place.toInt(), title)
