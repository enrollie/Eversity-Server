/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 9/14/22, 9:04 PM
 */

package by.enrollie.privateProviders

import by.enrollie.data_classes.UserID
import by.enrollie.providers.PluginMetadataInterface
import io.ktor.server.routing.*
import io.ktor.util.*

interface RoutingInterface {
    /**
     * Creates a route beginning with /plugin/{pluginName}.
     * Server guarantees that [userIDAttributeKey] and [tokenAttributeKey] are present in the call.
     */
    fun createRoute(pluginMetadata: PluginMetadataInterface, routeHandler: Route.() -> Unit)

    companion object {
        /**
         * Attribute key for user ID
         */
        val userIDAttributeKey: AttributeKey<UserID>
            get() = AttributeKey("userID")

        /**
         * Attribute key for user token
         */
        val tokenAttributeKey: AttributeKey<String>
            get() = AttributeKey("token")
    }
}
