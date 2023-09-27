/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 11/5/22, 2:23 AM
 */

package by.enrollie

import by.enrollie.annotations.UnsafeAPI
import by.enrollie.data_classes.*
import by.enrollie.impl.ProvidersCatalog
import by.enrollie.impl.setProvidersCatalog
import by.enrollie.plugins.configureKtorPlugins
import by.enrollie.plugins.jwtProvider
import by.enrollie.providers.DatabaseRolesProviderInterface
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.BeforeTest
import kotlin.test.assertEquals

@OptIn(UnsafeAPI::class)
class UserDataTest {
    private val token: String by lazy {
        JWT.create().withAudience(Defaults.configuration.jwtConfiguration.audience)
            .withClaim("user", 1).withClaim("token", _token)
            .sign(Algorithm.HMAC256(Defaults.configuration.jwtConfiguration.secret))
    }
    private val _token by lazy {
        Defaults.providersCatalog.databaseProvider.authenticationDataProvider.generateNewToken(
            1
        ).token
    }

    companion object {
        @JvmStatic
        @BeforeClass
        fun setup(): Unit {
            println("Setting up test environment")
            (Defaults.providersCatalog.databaseProvider as TemporaryDatabaseImplementation).clear()
            Defaults.providersCatalog.databaseProvider.runInSingleTransaction {
                it.usersProvider.createUser(User(1, Name("Pavel", "Andreevich", "Matusevich")))
                it.usersProvider.createUser(User(2, Name("Pavel", "Andreevich", "Matusevich")))
                it.classesProvider.createClass(SchoolClass(1, "1A", TeachingShift.FIRST))
            }
        }

        @BeforeTest
        fun beforeTest(): Unit {
            println("Hi! I'm before test!")
        }
    }

    @Test
    fun testUserDataGetting() = testApplication {
        application {
            install(providersCatalogPlugin)
            configureKtorPlugins()
        }
        val client = createClient {
            this.install(ContentNegotiation) {
                json()
            }
        }
        val result = client.get {
            url("/user/1")
            header("Authorization", "Bearer $token")
        }.body<User>()
        assertEquals(User(1, Name("Pavel", "Andreevich", "Matusevich")), result)
    }

    @Test
    fun testUserTokenRemoving() = testApplication {
        application {
            install(providersCatalogPlugin)
            configureKtorPlugins()
        }
        val client = createClient {
            this.install(ContentNegotiation) {
                json()
            }
        }
        val result = client.delete {
            url("user/token")
            println("Token: $token")
            header("Authorization", "Bearer $token")
        }
        assert(result.status.isSuccess()) { result }
        val secondResult = client.get {
            url("/user/1")
            header("Authorization", "Bearer $token")
        }
        assert(secondResult.status == HttpStatusCode.Unauthorized)
    }

    @Test
    fun getUserByToken() = testApplication {
        application {
            install(providersCatalogPlugin)
            configureKtorPlugins()
        }
        val client = createClient {
            this.install(ContentNegotiation) {
                json()
            }
        }
        val result = client.get {
            url("/user")
            header("Authorization", "Bearer $token")
        }.body<User>()
        assertEquals(User(1, Name("Pavel", "Andreevich", "Matusevich")), result)
    }

    @Test
    fun createRole() = testApplication {
        application {
            install(providersCatalogPlugin)
            configureKtorPlugins()
        }
        ProvidersCatalog.databaseProvider.rolesProvider.appendRoleToUser(
            1,
            DatabaseRolesProviderInterface.RoleCreationData(
                1,
                Roles.SERVICE.SYSTEM_ADMINISTRATOR,
                RoleInformationHolder()
            )
        )
        val client = createClient {
            this.install(ContentNegotiation) {
                json()
            }
        }
        val result = client.put {
            url("/user/2/roles")
            header("Authorization", "Bearer $token")
            setBody(
                mapOf(
                    "role" to JsonPrimitive(Roles.CLASS.ABSENCE_PROVIDER.getID()),
                    "additionalInfo" to JsonObject(mapOf("classID" to JsonPrimitive(1)))
                )
            )
        }
        assert(result.status.isSuccess())
        val secondResult = client.get {
            url("/user/2/roles")
            header("Authorization", "Bearer $token")
        }.body<List<RoleData>>()
        assert(secondResult.any { it.role == Roles.CLASS.ABSENCE_PROVIDER && it.getField(Roles.CLASS.ABSENCE_PROVIDER.classID) == 1 && it.userID == 2 })
    }
}
