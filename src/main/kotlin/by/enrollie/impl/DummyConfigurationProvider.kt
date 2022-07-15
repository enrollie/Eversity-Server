/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/15/22, 3:31 AM
 */

package by.enrollie.impl

import by.enrollie.data_classes.Declensions
import by.enrollie.providers.ConfigurationInterface

class DummyConfigurationProvider : ConfigurationInterface {
    override val configurationID: String = "Dummy"
    override val jwtConfiguration: ConfigurationInterface.JwtConfigurationInterface =
        object : ConfigurationInterface.JwtConfigurationInterface {
            override val secret: String = "secret"
            override val audience: String = "audience"
        }
    override val schoolConfiguration: ConfigurationInterface.SchoolConfigurationInterface =
        object : ConfigurationInterface.SchoolConfigurationInterface {
            override val title: Declensions
                get() = TODO("Not yet implemented")
        }
    override val schoolsByConfiguration: ConfigurationInterface.SchoolsByConfigurationInterface =
        object : ConfigurationInterface.SchoolsByConfigurationInterface {
            override val baseUrl: String = "baseUrl"
            override val recheckInterval: Long = 100000
            override val recheckOnDownInterval: Long = 50000
        }
    override val serverConfiguration: ConfigurationInterface.ServerConfigurationInterface =
        object : ConfigurationInterface.ServerConfigurationInterface {
            override val baseHttpUrl: String = "https://localhost:8080"
            override val baseWebsocketUrl: String = "wss://localhost:8080"
        }


    override val isConfigured: Boolean = true

}
