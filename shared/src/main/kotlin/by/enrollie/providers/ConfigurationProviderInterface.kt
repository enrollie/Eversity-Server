/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/25/22, 2:45 PM
 */

package by.enrollie.providers

import by.enrollie.data_classes.Declensions

/**
 * Service configuration data class supplied by configuration plugin.
 */
interface ConfigurationInterface {
    /**
     * Must be equal to the plugin ID.
     */
    val configurationPluginID: String

    interface JwtConfigurationInterface {
        val secret: String
        val audience: String
    }

    val jwtConfiguration: JwtConfigurationInterface

    interface SchoolConfigurationInterface {
        /**
         * Declensions (in russian) of the institute
         */
        val title: Declensions
    }

    val schoolConfiguration: SchoolConfigurationInterface

    interface SchoolsByConfigurationInterface {
        /**
         * Base Schools.by URL
         */
        val baseUrl: String

        /**
         * Interval between Schools.by availability checks
         */
        val recheckInterval: Long

        /**
         * Interval between Schools.by availability checks when last check reported that Schools.by is down
         */
        val recheckOnDownInterval: Long

        /**
         * Number of seconds since the start of the day to wait before synchronizing service database with Schools.by
         *
         * For example, to sync at 00:01:01 (12:01:01 AM), the value must be 61 (so, server should wait 61 second after 12 AM to sync database).
         */
        val resyncDelay: Long
    }

    val schoolsByConfiguration: SchoolsByConfigurationInterface

    interface ServerConfigurationInterface {
        /**
         * Base server HTTP(S) URL to serve to client (i.e. "http://localhost:3000" or "https://eversity.sch001.by/srv")
         *
         * Must not contain trailing slashes
         */
        val baseHttpUrl: String

        /**
         * Base Websocket (secure) URL to serve to client (i.e. "ws://localhost:8080" or "wss://eversity.sch001.by/srv/ws")
         *
         * Most not contain trailing slashes
         */
        val baseWebsocketUrl: String

        /**
         * Time-To-Live of temporary files served to users (i.e. filled reports) in milliseconds
         */
        val tempFileTTL: Long
    }

    val serverConfiguration: ServerConfigurationInterface

    /**
     * Must answer the question: "Is everything configured correctly?"
     * @return true if everything is configured correctly, false otherwise.
     */
    val isConfigured: Boolean
}

