/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/18/22, 3:01 AM
 */

package by.enrollie.data_classes

import by.enrollie.serializers.LocalTimeSerializer
import java.time.LocalTime

@kotlinx.serialization.Serializable
data class EventConstraints(
    @kotlinx.serialization.Serializable(with = LocalTimeSerializer::class)
    val startTime: LocalTime,
    @kotlinx.serialization.Serializable(with = LocalTimeSerializer::class)
    val endTime: LocalTime
) {
    // Checks if event is in time constraints
    fun isInTimeConstraints(eventTime: LocalTime): Boolean {
        return eventTime.isAfter(startTime) && eventTime.isBefore(endTime)
    }

    init {
        require(startTime.isBefore(endTime)) {
            "Start time must be before end time (initialized values: startTime: $startTime, endTime: $endTime)"
        }
    }
}
