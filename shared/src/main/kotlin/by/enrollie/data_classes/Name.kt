/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/10/22, 11:47 PM
 */

package by.enrollie.data_classes

data class Name(
    val first: String, val middle: String? = null, val last: String
) {
    override fun toString(): String {
        return "$first${middle?.let { " $it" } ?: ""} $last"
    }

    val shortForm = "$first ${middle ?: last}"

    companion object
}
