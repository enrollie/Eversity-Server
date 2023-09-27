/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 8/12/22, 3:26 AM
 */

package by.enrollie.privateProviders

import by.enrollie.providers.ConfigurationInterface
import by.enrollie.providers.DatabaseProviderInterface

interface ApplicationProvider {
    @Deprecated("Use DatabaseProviderInterface instead. Will be removed in API v1.0", ReplaceWith("environment"), DeprecationLevel.WARNING)
    val metadata: @Suppress("DEPRECATION") ApplicationMetadata
    val configuration: ConfigurationInterface
    val environment: EnvironmentInterface
    val database: DatabaseProviderInterface
    val tokenSigner: TokenSignerProvider
    val eventScheduler: EventSchedulerInterface
    val templatingEngine: TemplatingEngineInterface
    val commandLine: CommandLineInterface
    val routing: RoutingInterface
}
