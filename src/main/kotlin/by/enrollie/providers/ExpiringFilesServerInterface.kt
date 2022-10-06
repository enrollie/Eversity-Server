/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 10/6/22, 10:33 PM
 */

package by.enrollie.providers

import io.ktor.server.routing.*
import java.io.File

interface ExpiringFilesServerInterface {
    /**
     * Registers new temporary file to serve. After file is served, that file is deleted
     * @param expireIn Timeout until file expires
     * @return ID of a file to serve
     */
    fun registerNewFile(file: File, expireIn: Long?): String

    /**
     * Delete file and unregister it from server
     */
    fun deleteFile(id: String)

    /**
     * Unregister file without deleting it
     */
    fun unregisterFile(id: String)

    /**
     * Register server in the Ktor [Route]
     */
    fun registerInRoute(route: Route)
}
