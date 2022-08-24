/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 8/11/22, 3:10 AM
 */

package by.enrollie.data_classes

@kotlinx.serialization.Serializable
data class AuthorizedChangeAuthor(
    val user: User,
    val roleData: RoleData
)
