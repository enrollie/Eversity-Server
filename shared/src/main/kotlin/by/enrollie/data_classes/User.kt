/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/10/22, 11:19 PM
 */

package by.enrollie.data_classes

typealias UserID = Int

data class User(
    val id: UserID,
    val name: Name
)
