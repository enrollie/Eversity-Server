/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 8/7/22, 3:49 AM
 */

package by.enrollie.extensions

import java.time.LocalDate
import java.time.LocalDateTime

fun LocalDate.isBetweenOrEqual(start: LocalDate, end: LocalDate): Boolean {
    return (this.isAfter(start) || this.isEqual(start)) && (this.isBefore(end) || this.isEqual(end))
}

fun LocalDateTime.isBetweenOrEqual(start: LocalDateTime, end: LocalDateTime): Boolean {
    return (this.isAfter(start) || this.isEqual(start)) && (this.isBefore(end) || this.isEqual(end))
}
