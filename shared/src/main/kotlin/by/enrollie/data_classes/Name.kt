/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/18/22, 3:03 AM
 */

package by.enrollie.data_classes

@kotlinx.serialization.Serializable
data class Name(
    val first: String, val middle: String? = null, val last: String
) {
    override fun toString(): String {
        return "$first${middle?.let { " $it" } ?: ""} $last"
    }

    val shortForm = "$first ${middle ?: last}"

    companion object
}
