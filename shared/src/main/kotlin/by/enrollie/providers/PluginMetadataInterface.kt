/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/25/22, 2:19 PM
 */

package by.enrollie.providers

import by.enrollie.privateProviders.ApplicationProvider

/**
 * Interface that every plugin must implement.
 */
interface PluginMetadataInterface {
    /**
     * @return name of the plugin
     */
    val title: String

    /**
     * @return version of the plugin
     */
    val version: String

    /**
     * @return author of the plugin
     */
    val author: String

    /**
     * Version of plugin API that this plugin was built for.
     */
    val pluginApiVersion: String

    /**
     * Called when the plugin is loaded.
     */
    fun onLoad()

    /**
     * Called when application is ready.
     */
    fun onStart(application: ApplicationProvider)

    /**
     * Called when application is stopped.
     */
    fun onStop()

    /**
     * Called when plugin is unloaded.
     */
    fun onUnload()
}
