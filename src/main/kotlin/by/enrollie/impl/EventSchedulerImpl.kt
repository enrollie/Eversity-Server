/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 8/24/22, 7:50 PM
 */

package by.enrollie.impl

import by.enrollie.privateProviders.EventSchedulerInterface
import java.util.*

class EventSchedulerImpl : EventSchedulerInterface {
    private val timer = Timer("EventScheduler", true)
    private val tasks = mutableMapOf<String, TimerTask>()
    override fun scheduleOnce(delay: Long, action: () -> Unit): String {
        val task = object : TimerTask() {
            override fun run() {
                action()
            }
        }
        val id = UUID.randomUUID().toString()
        tasks[id] = task
        timer.schedule(task, delay)
        return id
    }

    override fun scheduleRepeating(delay: Long, period: Long, action: () -> Unit): String {
        val task = object : TimerTask() {
            override fun run() {
                action()
            }
        }
        val id = UUID.randomUUID().toString()
        tasks[id] = task
        timer.scheduleAtFixedRate(task, delay, period)
        return id
    }

    override fun cancelEvent(id: String) {
        tasks[id]?.cancel()
        tasks.remove(id)
    }
}
