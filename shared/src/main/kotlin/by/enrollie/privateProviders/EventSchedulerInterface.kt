/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 8/12/22, 3:26 AM
 */

package by.enrollie.privateProviders

interface EventSchedulerInterface {
    /**
     * Schedules an event to be executed after a given delay.
     * @param delay The delay in milliseconds.
     * @return The id of the scheduled event.
     */
    fun scheduleOnce(delay: Long, action: () -> Unit): String

    /**
     * Schedules an event to be executed periodically.
     * @param delay The delay in milliseconds.
     * @param period The period in milliseconds.
     * @return The id of the scheduled event.
     */
    fun scheduleRepeating(delay: Long, period: Long, action: () -> Unit): String

    /**
     * Cancels a scheduled event. If it is not scheduled, nothing happens.
     */
    fun cancelEvent(id: String)
}
