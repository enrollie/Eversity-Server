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
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor

class EventSchedulerImpl : EventSchedulerInterface {
    private val executor = ScheduledThreadPoolExecutor(16)
    private val tasks = mutableMapOf<String, ScheduledFuture<*>>()
    override fun scheduleOnce(delay: Long, action: () -> Unit): String {
        require(delay >= 0) { "Delay must be positive" }
        val runnable = Runnable { action() }
        val id = UUID.randomUUID().toString()
        tasks[id] = executor.schedule(runnable, delay, java.util.concurrent.TimeUnit.MILLISECONDS)
        return id
    }

    override fun scheduleRepeating(delay: Long, period: Long, action: () -> Unit): String {
        require(period > 0) { "Period must be positive" }
        require(delay >= 0) { "Delay must be positive" }
        val runnable = object : TimerTask() {
            override fun run() {
                try {
                    action()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        val id = UUID.randomUUID().toString()
        tasks[id] = executor.scheduleAtFixedRate(runnable, delay, period, java.util.concurrent.TimeUnit.MILLISECONDS)
        return id
    }

    override fun cancelEvent(id: String) {
        tasks[id]?.cancel(false)
        tasks.remove(id)
    }

    init {
        executor.executeExistingDelayedTasksAfterShutdownPolicy = false
        executor.removeOnCancelPolicy = true
        Runtime.getRuntime().addShutdownHook(Thread {
            executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)
            executor.shutdownNow()
        })
    }
}
