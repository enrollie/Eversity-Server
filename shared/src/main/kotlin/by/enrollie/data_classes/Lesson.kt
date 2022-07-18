/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/18/22, 3:03 AM
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
    val teachers: Set<UserID>, // Teachers may not be registered at the moment of lesson creation, so we can't use RoleData here
    val classID: ClassID,
    val journalID: JournalID,
    val subgroupID: SubgroupID?
)
