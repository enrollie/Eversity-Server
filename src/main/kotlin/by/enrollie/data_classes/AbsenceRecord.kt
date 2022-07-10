/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/10/22, 11:16 PM
 */

package by.enrollie.data_classes

import org.joda.time.DateTime
import org.joda.time.LocalDate

data class AbsenceRecord(
    val studentData: RoleData<Roles.CLASS.Student>,
    val absenceDate: LocalDate,
    val classID: ClassID,
    val absenceType: AbsenceType,
    val lessonsList: List<TimetablePlace>,
    val createdBy: RoleData<*>,
    val created: DateTime,
    val lastUpdatedBy: RoleData<*>?,
    val lastUpdated: DateTime?
)

fun createMatchableAbsenceRecord(
    studentData: RoleData<Roles.CLASS.Student>, absenceDate: LocalDate, classID: ClassID
): AbsenceRecord = AbsenceRecord(
    studentData,
    absenceDate,
    classID,
    AbsenceType.OTHER,
    emptyList(),
    RoleData(0, Roles.SERVICE.ADMINISTRATOR, mapOf(), DateTime.now(), null),
    DateTime.now(),
    null,
    null
)

