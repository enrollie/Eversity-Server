/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/15/22, 3:38 AM
 */

package by.enrollie.privateProviders

import by.enrollie.providers.ConfigurationInterface
import by.enrollie.providers.DatabaseProviderInterface

interface ApplicationProvider {
    val metadata: ApplicationMetadata
    val configuration: ConfigurationInterface
    val database: DatabaseProviderInterface
    val absenceManager: AbsenceManagerInterface
    val tokenSigner: TokenSignerProvider
}
