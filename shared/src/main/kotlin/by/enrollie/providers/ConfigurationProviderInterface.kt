/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/10/22, 11:37 PM
 */

package by.enrollie.providers

import by.enrollie.data_classes.Declensions
import by.enrollie.data_classes.Field

/**
 * Service configuration data class.
 */
class Configuration(
    val JwtConfiguration: JwtConfigurationClass,
    val SchoolConfiguration: SchoolConfigurationClass,
    val SchoolsByConfiguration: SchoolsByConfigurationClass,
    val ServerConfiguration: ServerConfigurationClass,
) {
    class JwtConfigurationClass(
        val secret: String, val audience: String
    )

    class SchoolConfigurationClass(
        val title: Declensions,
    )

    class SchoolsByConfigurationClass(
        val baseUrl: String,
    )

    class ServerConfigurationClass
}

interface ConfigurationProviderInterface {
    fun getConfiguration(): Configuration
    fun <T : Any> updateConfiguration(field: Field<T>, newValue: T): Configuration
}
