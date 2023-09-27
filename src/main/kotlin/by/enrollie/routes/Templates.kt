/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 8/13/22, 11:55 PM
 */

package by.enrollie.routes

import by.enrollie.data_classes.UserID
import by.enrollie.impl.ProvidersCatalog
import by.enrollie.plugins.UserPrincipal
import by.enrollie.privateProviders.TemplatingEngineInterface
import by.enrollie.util.RoleUtil
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.LocalDate

@Serializable
private data class TemplateFieldSuggestion(
    val value: String, val displayName: String
)

@Serializable
private data class TemplateFieldResponse(
    val id: String,
    val displayName: String,
    val suggestedValues: List<TemplateFieldSuggestion>,
    val type: TemplatingEngineInterface.TemplateField.FieldType
)

@Serializable
private data class TemplateMetadataResponse(
    @SerialName("templateId")
    val templateID: String,
    val displayName: String, val fields: List<TemplateFieldResponse>
)

internal fun Route.templates() {
    val templateCache: Cache<UserID, List<TemplateMetadataResponse>> = Caffeine.newBuilder().expireAfterWrite(
        Duration.ofMinutes(15)
    ).initialCapacity(40).maximumSize(80).build()

    @OptIn(DelicateCoroutinesApi::class)
    val context = newSingleThreadContext("TemplateChangeListener")
    CoroutineScope(context).launch {
        ProvidersCatalog.databaseProvider.rolesProvider.eventsFlow.collect {
            templateCache.invalidate(it.eventSubject.userID)
        }
    }
    authenticate("jwt") {
        get("/templates") {
            val user = call.principal<UserPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
            val roles = ProvidersCatalog.databaseProvider.rolesProvider.getRolesForUser(user.userID)
            val availableTemplates = RoleUtil.determineAvailableTemplates(roles)
            val date = LocalDate.now()
            val templates = templateCache.get(user.userID) {
                availableTemplates.map { template ->
                    TemplateMetadataResponse(template.templateID, template.displayName, template.fields.map { field ->
                        TemplateFieldResponse(
                            field.id, field.displayName, RoleUtil.suggestValues(roles, field, date).map {
                                TemplateFieldSuggestion(it.second, it.first)
                            }, field.type
                        )
                    })
                }
            }
            call.respond(templates)
        }
    }
}
