/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/18/22, 3:05 AM
 */

package by.enrollie.data_classes

import by.enrollie.serializers.LocalDateTimeSerializer
import kotlinx.serialization.Serializable
import java.time.LocalDateTime

@Serializable
data class AuthenticationToken(
    val token: String, val userID: UserID,
    @Serializable(with = LocalDateTimeSerializer::class)
    val issueDateTime: LocalDateTime
)
