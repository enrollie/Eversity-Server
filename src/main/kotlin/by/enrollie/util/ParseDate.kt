/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 8/14/22, 12:52 AM
 */

package by.enrollie.util

import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter

val DATE_FORMATTER = SimpleDateFormat("yyyy-MM-dd")
val FILENAME_DATE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd--HH-mm-ss")

fun String.parseDate(): LocalDate? = try {
    LocalDate.parse(this)
} catch (e: Exception) {
    null
}
