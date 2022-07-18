/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/18/22, 3:03 AM
 */

package by.enrollie.data_classes

typealias UserID = Int

@kotlinx.serialization.Serializable
data class User(
    val id: UserID,
    val name: Name
)
