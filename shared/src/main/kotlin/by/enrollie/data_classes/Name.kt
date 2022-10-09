/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/18/22, 3:03 AM
 */

package by.enrollie.data_classes

import kotlinx.serialization.SerialName

@kotlinx.serialization.Serializable
data class Name(
    @SerialName("firstName")
    val first: String,
    @SerialName("middleName")
    val middle: String? = null,
    @SerialName("lastName")
    val last: String
) {
    override fun toString(): String {
        return "$last $first${middle?.let { " $it" } ?: ""}"
    }

    val shortForm = "$first ${middle ?: last}"

    companion object
}
