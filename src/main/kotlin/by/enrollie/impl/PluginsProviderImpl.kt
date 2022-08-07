/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 8/7/22, 3:49 AM
 */

package by.enrollie.impl

import by.enrollie.providers.PluginMetadataInterface
import by.enrollie.providers.PluginsProviderInterface

data class PluginsProviderImpl(override val list: List<PluginMetadataInterface>) : PluginsProviderInterface {
    override fun getPlugin(name: String): PluginMetadataInterface? = list.firstOrNull { it.name == name }
}
