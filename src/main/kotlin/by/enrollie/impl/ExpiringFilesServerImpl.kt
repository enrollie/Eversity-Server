/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 10/6/22, 10:38 PM
 */

package by.enrollie.impl

import by.enrollie.providers.ExpiringFilesServerInterface
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class ExpiringFilesServerImpl : ExpiringFilesServerInterface {
    private val filesMap = ConcurrentHashMap<String, File>(10)
    private val logger = LoggerFactory.getLogger("ExpiringFilesServer")

    override fun registerNewFile(file: File, expireIn: Long?): String {
        val id = "${file.hashCode()}-${UUID.randomUUID()}.${file.extension}"
        filesMap[id] = file
        if (expireIn != null) {
            ProvidersCatalog.eventScheduler.scheduleOnce(expireIn) {
                filesMap.remove(id)?.also {
                    it.delete()
                    logger.debug("Removed file $id (path: ${it.path}) due to a timeout")
                }
            }
        }
        return id
    }

    override fun deleteFile(id: String) {
        filesMap.remove(id)?.also {
            it.delete()
            logger.debug("Removed file $id (path: ${it.path}) due to a manual request")
        }
    }

    override fun unregisterFile(id: String) {
        filesMap.remove(id)?.also {
            logger.debug("Unregistered file $id (path: ${it.path}) without deleting it")
        }
    }

    override fun registerInRoute(route: Route) {
        route.get("/{id}") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.NotFound)
            val file = filesMap[id] ?: return@get call.respond(HttpStatusCode.NotFound)
            filesMap.remove(id) // To avoid any possibility of deleting file while it's uploading
            runCatching {
                call.respondFile(file)
            }.fold({
                file.delete()
                logger.debug("Deleted file $id (path: ${file.path}) after successfully responding with it")
            }, {
                logger.error("Error while responding with file $id (path: ${file.path})", it)
                file.delete()
                logger.debug("Deleted file $id (path: ${file.path}) after failing to respond with it")
            })
        }
    }

}
