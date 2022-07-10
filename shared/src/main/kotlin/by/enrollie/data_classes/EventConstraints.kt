/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/10/22, 11:16 PM
 */

package by.enrollie.data_classes

import java.time.LocalTime

data class EventConstraints(
    val startTime: LocalTime,
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
