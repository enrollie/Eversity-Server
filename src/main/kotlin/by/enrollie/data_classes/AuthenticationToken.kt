/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/10/22, 11:16 PM
 */

package by.enrollie.data_classes

import org.joda.time.DateTime

data class AuthenticationToken(
    val token: String, val userID: UserID, val issueDateTime: DateTime
)
