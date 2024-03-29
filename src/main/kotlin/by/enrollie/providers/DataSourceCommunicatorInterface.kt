/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/15/22, 2:13 AM
 */

package by.enrollie.providers

import by.enrollie.data_classes.ClassID
import by.enrollie.data_classes.UserID
import by.enrollie.exceptions.RateLimitException
import com.neitex.Credentials
import kotlinx.coroutines.flow.SharedFlow

interface DataSourceCommunicatorInterface {
    @kotlinx.serialization.Serializable
    enum class MessageType {
        INFORMATION, FAILURE, AUTHENTICATION, SUCCESS
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

    /**
     * Puts registration request to the queue and returns UUID that will be used to identify request.
     * @param classID Class ID to sync
     * @param schoolsByCredentials Optional credentials to use during sync (if not provided, communicator will make an attempt to find sufficient credentials)
     * @throws RateLimitException if rate limit for class is exceeded
     */
    fun addClassToSyncQueue(classID: ClassID, schoolsByCredentials: Credentials? = null): String
}
