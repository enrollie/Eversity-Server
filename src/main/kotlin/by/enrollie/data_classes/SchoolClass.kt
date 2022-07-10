/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/10/22, 11:16 PM
 */

package by.enrollie.data_classes

typealias ClassID = Int

typealias SubgroupID = Int

enum class TeachingShift {
    FIRST,
    SECOND
}

data class SchoolClass(
    val id: ClassID,
    val title: String,
    val shift: TeachingShift,
)
