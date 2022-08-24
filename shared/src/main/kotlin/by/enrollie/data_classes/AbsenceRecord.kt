/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 8/11/22, 3:10 AM
 */

package by.enrollie.data_classes

import by.enrollie.serializers.LocalDateSerializer
import by.enrollie.serializers.LocalDateTimeSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalDateTime

typealias AbsenceID = Long
@Serializable
data class AbsenceRecord(
    val id: AbsenceID,
    val student: User,
    @Serializable(with = LocalDateSerializer::class)
    val absenceDate: LocalDate,
    @SerialName("classId")
    val classID: ClassID,
    val absenceType: AbsenceType,
    val lessonsList: List<TimetablePlace>,
    val createdBy: AuthorizedChangeAuthor,
    @Serializable(with = LocalDateTimeSerializer::class)
    val created: LocalDateTime,
    val lastUpdatedBy: AuthorizedChangeAuthor?,
    @Serializable(with = LocalDateTimeSerializer::class)
    val lastUpdated: LocalDateTime?
)
