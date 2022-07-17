/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/17/22, 10:22 PM
 */

package by.enrollie.data_classes

import java.time.LocalDateTime

data class AuthenticationToken(
    val token: String, val userID: UserID, val issueDateTime: LocalDateTime
)
