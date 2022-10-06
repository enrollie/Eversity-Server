/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 9/9/22, 5:42 PM
 */

package by.enrollie.util

import java.time.LocalDateTime

fun getTimedWelcome(name: String): String = when (LocalDateTime.now().hour) {
    in 6..11 -> "Доброе утро, $name!"
    in 12..17 -> "Добрый день, $name!"
    in 18..23 -> "Добрый вечер, $name!"
    else -> "Доброй ночи, $name!"
}
