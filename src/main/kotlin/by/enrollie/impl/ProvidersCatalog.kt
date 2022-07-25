/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/25/22, 2:58 PM
 */

package by.enrollie.impl

import by.enrollie.annotations.UnsafeAPI
import by.enrollie.privateProviders.AbsenceManagerInterface
import by.enrollie.providers.*
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.instance

interface ProvidersCatalogInterface {
    val databaseProvider: DatabaseProviderInterface
    val registrarProvider: DataRegistrarProviderInterface
    val configuration: ConfigurationInterface
    val commandLine: CommandLineInterface
    val absenceManager: AbsenceManagerInterface
    val authorization: AuthorizationInterface
}

class ProvidersCatalogImpl(override val di: DI) : ProvidersCatalogInterface, DIAware {
    override val databaseProvider: DatabaseProviderInterface by instance()
    override val registrarProvider: DataRegistrarProviderInterface by instance()
    override val configuration: ConfigurationInterface by instance()
    override val commandLine: CommandLineInterface by instance()
    override val absenceManager: AbsenceManagerInterface by instance()
    override val authorization: AuthorizationInterface by instance()
}

private var providersCatalogField: ProvidersCatalogInterface? = null

@UnsafeAPI
fun setProvidersCatalog(providersCatalog: ProvidersCatalogImpl) {
    require(providersCatalogField == null) { "Providers catalog is already set" }
    providersCatalogField = providersCatalog
}

val ProvidersCatalog: ProvidersCatalogInterface
    get() = providersCatalogField ?: throw IllegalStateException("Providers catalog is not set")
