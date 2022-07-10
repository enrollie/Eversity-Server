/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/10/22, 11:16 PM
 */

package by.enrollie.providers

import by.enrollie.data_classes.UserID
import com.neitex.Credentials
import kotlinx.coroutines.flow.SharedFlow

interface DataRegistrarProviderInterface {
    @kotlinx.serialization.Serializable
    enum class MessageType {
        INFORMATION, FAILURE, AUTHENTICATION, NAME
    }

    @kotlinx.serialization.Serializable
    data class Message(
        val uuid: String,
        val type: MessageType,
        val message: String,
        val step: Int,
        val totalSteps: Int,
        val additionalData: Map<String, String?>
    )

    val messagesBroadcast: SharedFlow<Message>

    /**
     * Check that observer that asks for [uuid] observation may join it.
     */
    fun authenticateObserver(uuid: String): Boolean

    /**
     * Puts registration request to the queue and returns UUID that will be used to identify request.
     */
    fun addToRegister(userID: UserID, schoolsByCredentials: Credentials): String
}
