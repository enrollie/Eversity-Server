/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/18/22, 2:14 AM
 */

package by.enrollie.util

import org.joda.time.DateTime
import java.time.LocalDateTime

fun LocalDateTime.toJodaDateTime() = DateTime(
    year, month.value, dayOfMonth, hour, minute, second, nano / 1_000_000
)

fun DateTime.toJavaLocalDateTime() =
    LocalDateTime.of(
        year,
        monthOfYear,
        dayOfMonth,
        hourOfDay,
        minuteOfHour,
        secondOfMinute,
        millisOfSecond * 1_000_000
    )!!
