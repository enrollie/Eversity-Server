/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/15/22, 3:17 AM
 */

package by.enrollie.privateProviders

import by.enrollie.data_classes.AbsenceRecord
import by.enrollie.data_classes.ClassID
import kotlinx.coroutines.flow.SharedFlow
import java.time.LocalDate

interface AbsenceManagerInterface {
    val absencesFlow: SharedFlow<AbsenceRecord>

    fun addOrUpdateAbsence(absence: AbsenceRecord)
    fun deleteAbsence(absence: AbsenceRecord)
    fun addDummyAbsence(classID: ClassID)
    fun getAbsencesForClass(classID: ClassID, date: LocalDate): List<AbsenceRecord>
    fun getAbsencesForClass(classID: ClassID, firstDate: LocalDate, lastDate: LocalDate): List<AbsenceRecord>
    fun getDatesWithoutAbsences(classID: ClassID, firstDate: LocalDate, lastDate: LocalDate): List<LocalDate>
}
