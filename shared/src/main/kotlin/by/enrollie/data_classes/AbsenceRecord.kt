/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/17/22, 10:28 PM
 */

package by.enrollie.data_classes

import java.time.LocalDate
import java.time.LocalDateTime

data class AbsenceRecord(
    /**
     * Absent student ID
     * **NOTE:** User ID MUST be -1 if given absence record is a dummy one
     */
    val studentID: UserID,
    val absenceDate: LocalDate,
    val classID: ClassID,
    val absenceType: AbsenceType,
    val lessonsList: List<TimetablePlace>,
    val createdBy: UserID,
    val created: LocalDateTime,
    val lastUpdatedBy: UserID?,
    val lastUpdated: LocalDateTime?
)

fun createMatchableAbsenceRecord(
    studentData: UserID, absenceDate: LocalDate, classID: ClassID
): AbsenceRecord = AbsenceRecord(
    studentData, absenceDate, classID, AbsenceType.OTHER, emptyList(), -1, LocalDateTime.now(), null, null
)

