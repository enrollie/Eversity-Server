/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/15/22, 1:25 AM
 */

package by.enrollie.providers

import by.enrollie.data_classes.Declensions

/**
 * Service configuration data class supplied by configuration plugin.
 */
interface ConfigurationInterface {
    interface JwtConfigurationInterface {
        val secret: String
        val audience: String
    }

    val jwtConfiguration: JwtConfigurationInterface

    interface SchoolConfigurationInterface {
        val title: Declensions
    }

    val schoolConfiguration: SchoolConfigurationInterface

    interface SchoolsByConfigurationInterface {
        val baseUrl: String
        val recheckInterval: Long
        val recheckOnDownInterval: Long
    }

    val schoolsByConfiguration: SchoolsByConfigurationInterface

    interface ServerConfigurationInterface {
        val baseHttpUrl: String
        val baseWebsocketUrl: String
    }

    val serverConfiguration: ServerConfigurationInterface

    /**
     * Must answer the question: "Is everything configured correctly?"
     * @return true if everything is configured correctly, false otherwise.
     */
    val isConfigured: Boolean
}

