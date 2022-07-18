/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/18/22, 3:03 AM
 */

package by.enrollie.data_classes

typealias ClassID = Int

typealias SubgroupID = Int

@kotlinx.serialization.Serializable
enum class TeachingShift {
    FIRST,
    SECOND
}

@kotlinx.serialization.Serializable
data class SchoolClass(
    val id: ClassID,
    val title: String,
    val shift: TeachingShift,
)
