/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/10/22, 11:16 PM
 */

package by.enrollie.impl

import by.enrollie.data_classes.Declensions
import by.enrollie.data_classes.Field
import by.enrollie.providers.Configuration
import by.enrollie.providers.ConfigurationProviderInterface

class DummyConfigurationProvider : ConfigurationProviderInterface { //TODO: remove
    override fun getConfiguration(): Configuration = Configuration(
        Configuration.JwtConfigurationClass("secret", "Bearer"),
        Configuration.SchoolConfigurationClass(
            Declensions(
                "группа", "группы", "групп", "группам", "группам", "группах"
            )
        ),
        Configuration.SchoolsByConfigurationClass("https://demo.schools.by"),
        Configuration.ServerConfigurationClass()
    )

    override fun <T : Any> updateConfiguration(field: Field<T>, newValue: T): Configuration {
        println("Pretended to update field ${field.name.substringAfter("${Configuration::class.qualifiedName!!}\$")} to $newValue")
        return getConfiguration()
    }
}
