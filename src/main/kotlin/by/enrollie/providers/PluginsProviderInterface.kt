/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 8/7/22, 3:49 AM
 */

package by.enrollie.providers

interface PluginsProviderInterface {
    val list: List<PluginMetadataInterface>
    fun getPlugin(name: String): PluginMetadataInterface?
}
