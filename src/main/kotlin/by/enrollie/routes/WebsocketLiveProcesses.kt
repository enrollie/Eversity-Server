/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/28/22, 12:01 AM
 */

package by.enrollie.routes

import by.enrollie.impl.ProvidersCatalog
import by.enrollie.providers.DataSourceCommunicatorInterface
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.cancel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private suspend fun handler(session: DefaultWebSocketServerSession) {
    val uuid = session.call.parameters["uuid"] ?: return session.close(
        CloseReason(
            CloseReason.Codes.VIOLATED_POLICY, "No UUID provided"
        )
    )
    if (!ProvidersCatalog.registrarProvider.authenticateObserver(uuid)) return session.close(
        CloseReason(
            CloseReason.Codes.VIOLATED_POLICY, "UUID is not registered"
        )
    )
    ProvidersCatalog.registrarProvider.messagesBroadcast.collect { message ->
        if (message.uuid == uuid) {
            session.send(Json.encodeToString(message))
            if (message.type != DataSourceCommunicatorInterface.MessageType.INFORMATION) {
                session.close(
                    CloseReason(CloseReason.Codes.NORMAL, "Bye.")
                )
                session.cancel("Bye.")
            }
        }
    }
}

internal fun Route.wsRegister() {
    webSocket("/register/{uuid}", handler = ::handler)
    webSocket("/classSync/{uuid}", handler = ::handler)
}
