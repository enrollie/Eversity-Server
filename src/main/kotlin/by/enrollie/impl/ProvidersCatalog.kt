/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 8/7/22, 3:49 AM
 */

package by.enrollie.impl

import by.enrollie.annotations.UnsafeAPI
import by.enrollie.providers.*
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.instance

interface ProvidersCatalogInterface {
    val databaseProvider: DatabaseProviderInterface
    val registrarProvider: DataRegistrarProviderInterface
    val configuration: ConfigurationInterface
    val commandLine: CommandLineInterface
    val authorization: AuthorizationInterface
    val plugins: PluginsProviderInterface
}

class ProvidersCatalogImpl(override val di: DI) : ProvidersCatalogInterface, DIAware {
    override val databaseProvider: DatabaseProviderInterface by instance()
    override val registrarProvider: DataRegistrarProviderInterface by instance()
    override val configuration: ConfigurationInterface by instance()
    override val commandLine: CommandLineInterface by instance()
    override val authorization: AuthorizationInterface by instance()
    override val plugins: PluginsProviderInterface by instance()
}

private var providersCatalogField: ProvidersCatalogInterface? = null

@UnsafeAPI
fun setProvidersCatalog(providersCatalog: ProvidersCatalogImpl) {
    require(providersCatalogField == null) { "Providers catalog is already set" }
    providersCatalogField = providersCatalog
}

val ProvidersCatalog: ProvidersCatalogInterface
    get() = providersCatalogField ?: throw IllegalStateException("Providers catalog is not set")
