/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 8/7/22, 3:49 AM
 */

package by.enrollie.plugins

import by.enrollie.data_classes.User
import by.enrollie.data_classes.UserID
import by.enrollie.impl.ProvidersCatalog
import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.util.*

interface JwtProvider {
    fun getJwtVerifier(): JWTVerifier
    fun signToken(user: User, token: String): String
}

lateinit var jwtProvider: JwtProvider

data class UserPrincipal(
    val userID: UserID,
    val token: String?
) : Principal {
    fun getUserFromDB() =
        ProvidersCatalog.databaseProvider.usersProvider.getUser(userID)!! // We assume that user exists in DB
}

internal fun Application.configureSecurity() {
    authentication {
        jwt("jwt") {
            val jwtAudience = ProvidersCatalog.configuration.jwtConfiguration.audience
            val secret = ProvidersCatalog.configuration.jwtConfiguration.secret
            jwtProvider = object : JwtProvider {
                override fun getJwtVerifier(): JWTVerifier {
                    return JWT.require(Algorithm.HMAC256(secret)).withAudience(jwtAudience).build()
                }

                override fun signToken(user: User, token: String): String {
                    return JWT.create()
                        .withAudience(jwtAudience)
                        .withClaim("user", user.id)
                        .withClaim("token", token)
                        .sign(Algorithm.HMAC256(secret))
                }
            }
            realm = "Eversity"
            verifier(jwtProvider.getJwtVerifier())
            validate { credential ->
                val token = credential.payload.getClaim("token").asString()
                val userId = credential.payload.getClaim("user").asInt()
                if (ProvidersCatalog.databaseProvider.authenticationDataProvider.checkToken(token, userId)) {
                    attributes.put(AttributeKey("userID"), userId)
                    attributes.put(AttributeKey("token"), token)
                    UserPrincipal(userId, token)
                } else {
                    null
                }
            }
        }
    }
}
