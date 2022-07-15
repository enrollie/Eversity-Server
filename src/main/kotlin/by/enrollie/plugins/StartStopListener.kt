/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/15/22, 3:38 AM
 */

package by.enrollie.plugins

import by.enrollie.APPLICATION_METADATA
import by.enrollie.data_classes.User
import by.enrollie.impl.ProvidersCatalog
import by.enrollie.privateProviders.AbsenceManagerInterface
import by.enrollie.privateProviders.ApplicationMetadata
import by.enrollie.privateProviders.ApplicationProvider
import by.enrollie.privateProviders.TokenSignerProvider
import by.enrollie.providers.ConfigurationInterface
import by.enrollie.providers.DatabaseProviderInterface
import by.enrollie.providers.PluginMetadataInterface
import io.ktor.server.application.*

internal fun Application.configureStartStopListener(plugins: List<PluginMetadataInterface>) {
    environment.monitor.subscribe(ApplicationStarted) {
        val applicationProvider = object : ApplicationProvider {
            override val metadata: ApplicationMetadata = APPLICATION_METADATA
            override val configuration: ConfigurationInterface = ProvidersCatalog.configuration
            override val database: DatabaseProviderInterface = ProvidersCatalog.databaseProvider
            override val absenceManager: AbsenceManagerInterface = ProvidersCatalog.absenceManager
            override val tokenSigner: TokenSignerProvider = object : TokenSignerProvider {
                override fun signToken(user: User, token: String): String = jwtProvider.signToken(user, token)
            }
        }
        plugins.forEach {
            it.onStart(applicationProvider)
        }
    }
    environment.monitor.subscribe(ApplicationStopping) {
        plugins.forEach {
            it.onStop()
        }
    }
}
