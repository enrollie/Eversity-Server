/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 8/3/22, 10:03 PM
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
    val studentRole: RoleData,
    @Serializable(with = LocalDateSerializer::class)
    val absenceDate: LocalDate,
    @SerialName("classId")
    val classID: ClassID,
    val absenceType: AbsenceType,
    val lessonsList: List<TimetablePlace>,
    val createdBy: UserID,
    @Serializable(with = LocalDateTimeSerializer::class)
    val created: LocalDateTime,
    val lastUpdatedBy: UserID?,
    @Serializable(with = LocalDateTimeSerializer::class)
    val lastUpdated: LocalDateTime?
)
