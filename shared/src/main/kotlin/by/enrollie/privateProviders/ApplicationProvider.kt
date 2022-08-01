/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 8/1/22, 9:24 PM
 */

package by.enrollie.privateProviders

import by.enrollie.providers.ConfigurationInterface
import by.enrollie.providers.DatabaseProviderInterface

interface ApplicationProvider {
    val metadata: ApplicationMetadata
    val configuration: ConfigurationInterface
    val database: DatabaseProviderInterface
    val tokenSigner: TokenSignerProvider
}
