/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/10/22, 11:19 PM
 */

package by.enrollie.data_classes

import java.time.LocalDate

typealias LessonID = Long // Who knows how many lessons there will be in the future :)

data class Lesson(
    val id: LessonID,
    val title: String,
    val date: LocalDate,
    val placeInTimetable: TimetablePlace,
    val shift: TeachingShift,
    val classID: ClassID,
    val subjectID: SubjectID,
    val subgroupID: SubgroupID?
)
