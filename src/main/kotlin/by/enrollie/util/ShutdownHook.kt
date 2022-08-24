/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 8/15/22, 10:24 PM
 */

package by.enrollie.util

import by.enrollie.impl.ProvidersCatalog
import by.enrollie.plugins.StartStopListenerPlugin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

class ShutdownHook : Thread() {
    override fun run() {
        runBlocking {
            val logger = LoggerFactory.getLogger("ShutdownHook")
            logger.debug("Shutdown hook is running")
            StartStopListenerPlugin.issueStopCommand()
            delay(2000)
            ProvidersCatalog.plugins.list.forEach {
                logger.debug("Stopping plugin: ${it.name}")
                try {
                    it.onUnload()
                } catch (e: Exception) {
                    logger.error("Exception while unloading plugin: ${it.name}", e)
                } catch (e: Error) {
                    logger.error("Error while unloading plugin: ${it.name}", e)
                }
            }
            delay(2000)
            logger.info("ShutdownHook finished. Goodbye and have a nice day! :)")
        }
    }
}
