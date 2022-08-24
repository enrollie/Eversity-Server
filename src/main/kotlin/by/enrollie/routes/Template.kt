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
import by.enrollie.util.FILENAME_DATE_TIME_FORMATTER
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

@kotlinx.serialization.Serializable
private data class TemplateRequest(
    val id: String, val fields: Map<String, String>
)

internal fun Route.template() {
    authenticate("jwt") {
        get("/template") {
            val user = call.principal<UserPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
            val templateRequest = call.receive<TemplateRequest>()
            val template =
                ProvidersCatalog.templatingEngine.availableTemplates.firstOrNull { it.templateID == templateRequest.id }
                    ?: return@get call.respond(HttpStatusCode.NotFound)
            template.fields.forEach { // Validate fields
                when (it.type) {
                    TemplatingEngineInterface.TemplateField.FieldType.DATE -> {}
                    TemplatingEngineInterface.TemplateField.FieldType.STRING -> {}
                    TemplatingEngineInterface.TemplateField.FieldType.CLASSID -> ProvidersCatalog.authorization.authorize(
                        user.getUserFromDB(),
                        it.requiredPermission,
                        ProvidersCatalog.databaseProvider.classesProvider.getClass(templateRequest.fields[it.id]?.let {
                            it.toIntOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest)
                        } ?: return@get call.respond(HttpStatusCode.BadRequest)) ?: return@get call.respond(
                            HttpStatusCode.NotFound
                        ))

                    TemplatingEngineInterface.TemplateField.FieldType.USERID -> ProvidersCatalog.authorization.authorize(
                        user.getUserFromDB(),
                        it.requiredPermission,
                        ProvidersCatalog.databaseProvider.usersProvider.getUser(templateRequest.fields[it.id]?.let {
                            it.toIntOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest)
                        } ?: return@get call.respond(HttpStatusCode.BadRequest)) ?: return@get call.respond(
                            HttpStatusCode.NotFound
                        ))
                }
            }
            val file = try {
                ProvidersCatalog.templatingEngine.renderTemplate(templateRequest.id, templateRequest.fields)
            } catch (e: IllegalArgumentException) {
                LoggerFactory.getLogger("TemplateRouter").error("Template input error", e)
                return@get call.respond(HttpStatusCode.BadRequest)
            }
            call.response.headers.append(
                HttpHeaders.ContentDisposition, ContentDisposition.Attachment.withParameter(
                    "filename", "${template.templateID}-${
                        LocalDateTime.now().format(
                            FILENAME_DATE_TIME_FORMATTER
                        )
                    }.${file.extension}"
                ).toString()
            )
            call.respondFile(file)
            file.delete()
        }
    }
}
