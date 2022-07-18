/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/18/22, 3:05 AM
 */

package by.enrollie.data_classes

import by.enrollie.serializers.LocalDateSerializer
import by.enrollie.serializers.LocalDateTimeSerializer
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalDateTime

@Serializable
data class AbsenceRecord(
    /**
     * Absent student ID
     * **NOTE:** User ID MUST be -1 if given absence record is a dummy one
     */
    val studentID: UserID,
    @Serializable(with = LocalDateSerializer::class)
    val absenceDate: LocalDate,
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

fun createMatchableAbsenceRecord(
    studentData: UserID, absenceDate: LocalDate, classID: ClassID
): AbsenceRecord = AbsenceRecord(
    studentData, absenceDate, classID, AbsenceType.OTHER, emptyList(), -1, LocalDateTime.now(), null, null
)

