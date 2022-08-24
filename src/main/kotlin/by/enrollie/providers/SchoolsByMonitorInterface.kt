/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 8/11/22, 9:32 PM
 */

package by.enrollie.providers

interface SchoolsByMonitorInterface {
    /**
     * Returns current status of Schools.by
     */
    val isAvailable: Boolean

    /**
     * Returns time in milliseconds until next check of Schools.by availability
     */
    val untilNextCheck: Long

    /**
     * Forces to recheck availability of Schools.by as soon as possible
     */
    fun forceRecheck()

    /**
     * Initialize Schools.by availability monitor. Guaranteed to be called only once and before any other method or property.
     */
    fun init()
}
