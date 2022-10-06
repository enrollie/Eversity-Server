/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 8/24/22, 7:50 PM
 */

package by.enrollie.routes

import by.enrollie.impl.ProvidersCatalog
import by.enrollie.plugins.UserPrincipal
import by.enrollie.privateProviders.TemplatingEngineInterface
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.time.Duration

@kotlinx.serialization.Serializable
private data class TemplateRequest(
    val id: String, val fields: Map<String, String>
)

@kotlinx.serialization.Serializable
private data class FilledTemplateResponse(
    val filename: String, val mime: String, val url: String, val expiresIn: Long
)

internal fun Route.template() {
    authenticate("jwt") {
        post("/template") {
            val user = call.principal<UserPrincipal>() ?: return@post call.respond(HttpStatusCode.Unauthorized)
            val templateRequest = call.receive<TemplateRequest>()
            val template =
                ProvidersCatalog.templatingEngine.availableTemplates.firstOrNull { it.templateID == templateRequest.id }
                    ?: return@post call.respond(HttpStatusCode.NotFound)
            template.fields.forEach { field -> // Validate fields
                when (field.type) {
                    TemplatingEngineInterface.TemplateField.FieldType.DATE, TemplatingEngineInterface.TemplateField.FieldType.STRING -> {
                    }

                    TemplatingEngineInterface.TemplateField.FieldType.CLASSID -> ProvidersCatalog.authorization.authorize(
                        user.getUserFromDB(),
                        field.requiredPermission,
                        ProvidersCatalog.databaseProvider.classesProvider.getClass(templateRequest.fields[field.id]?.let {
                            it.toIntOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest)
                        } ?: return@post call.respond(HttpStatusCode.BadRequest)) ?: return@post call.respond(
                            HttpStatusCode.NotFound
                        ))

                    TemplatingEngineInterface.TemplateField.FieldType.USERID -> ProvidersCatalog.authorization.authorize(
                        user.getUserFromDB(),
                        field.requiredPermission,
                        ProvidersCatalog.databaseProvider.usersProvider.getUser(templateRequest.fields[field.id]?.let {
                            it.toIntOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest)
                        } ?: return@post call.respond(HttpStatusCode.BadRequest)) ?: return@post call.respond(
                            HttpStatusCode.NotFound
                        ))
                }
            }
            val file = try {
                ProvidersCatalog.templatingEngine.renderTemplate(templateRequest.id, templateRequest.fields)
            } catch (e: IllegalArgumentException) {
                LoggerFactory.getLogger("TemplateRouter").error("Template input error", e)
                return@post call.respond(HttpStatusCode.BadRequest)
            }
            val mime = withContext(Dispatchers.IO) {
                Files.probeContentType(file.toPath())
            }
            val id = ProvidersCatalog.expiringFilesServer.registerNewFile(
                file, ProvidersCatalog.configuration.serverConfiguration.tempFileTTL
            )
            call.respond(
                FilledTemplateResponse(
                    file.name,
                    mime,
                    "${ProvidersCatalog.configuration.serverConfiguration.baseHttpUrl}/temp/file/${id}",
                    Duration.ofMillis(ProvidersCatalog.configuration.serverConfiguration.tempFileTTL).toSeconds()
                )
            )
        }
    }
}
