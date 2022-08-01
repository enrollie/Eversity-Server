/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 8/1/22, 9:24 PM
 */

package by.enrollie.data_classes

import by.enrollie.serializers.LocalDateSerializer
import java.time.LocalDate

typealias LessonID = Long // Who knows how many lessons there will be in the future :)
typealias JournalID = Int

@kotlinx.serialization.Serializable
data class Lesson(
    val id: LessonID,
    val title: String,
    @kotlinx.serialization.Serializable(with = LocalDateSerializer::class)
    val date: LocalDate,
    val placeInTimetable: TimetablePlace,
    val teachers: UserID,
    val classID: ClassID,
    val journalID: JournalID,
    val subgroupID: SubgroupID?
)
