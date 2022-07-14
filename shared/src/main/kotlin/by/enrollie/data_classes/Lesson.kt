/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/13/22, 6:11 PM
 */

package by.enrollie.data_classes

import java.time.LocalDate

typealias LessonID = Long // Who knows how many lessons there will be in the future :)
typealias JournalID = Int

data class Lesson(
    val id: LessonID,
    val title: String,
    val date: LocalDate,
    val placeInTimetable: TimetablePlace,
    val teachers: Set<UserID>, // Teachers may not be registered at the moment of lesson creation, so we can't use RoleData here
    val classID: ClassID,
    val journalID: JournalID,
    val subgroupID: SubgroupID?
)
