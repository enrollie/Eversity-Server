/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/28/22, 12:01 AM
 */

package by.enrollie.routes

import by.enrollie.impl.ProvidersCatalog
import by.enrollie.providers.DataRegistrarProviderInterface
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.produceIn
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal fun Route.wsRegister() {
    webSocket("/register/{uuid}") {
        val uuid = call.parameters["uuid"] ?: return@webSocket close(
            CloseReason(
                CloseReason.Codes.VIOLATED_POLICY,
                "No UUID provided"
            )
        )
        if (!ProvidersCatalog.registrarProvider.authenticateObserver(uuid)) return@webSocket close(
            CloseReason(
                CloseReason.Codes.VIOLATED_POLICY,
                "UUID is not registered"
            )
        )
        for (message in ProvidersCatalog.registrarProvider.messagesBroadcast.produceIn(CoroutineScope(coroutineContext))) {
            if (message.uuid == uuid) send(Json.encodeToString(message))
            if (message.type == DataRegistrarProviderInterface.MessageType.FAILURE || message.type == DataRegistrarProviderInterface.MessageType.AUTHENTICATION) return@webSocket close(
                CloseReason(CloseReason.Codes.NORMAL, "Bye.")
            )
        }
    }
}
