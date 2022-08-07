/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 8/7/22, 3:49 AM
 */

package by.enrollie.util

import java.text.SimpleDateFormat
import java.time.LocalDate

val DATE_FORMATTER = SimpleDateFormat("yyyy-MM-dd")

fun String.parseDate(): LocalDate? = try {
    LocalDate.parse(this)
} catch (e: Exception) {
    null
}
