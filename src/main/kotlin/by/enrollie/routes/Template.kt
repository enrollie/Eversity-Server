/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 8/24/22, 7:50 PM
 */

package by.enrollie.routes

import by.enrollie.data_classes.Roles
import by.enrollie.exceptions.BadArgumentException
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
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.time.Duration

@Serializable
private data class TemplateRequest(
    val id: String, val fields: Map<String, String>
)

@Serializable
private data class FilledTemplateResponse(
    val filename: String, val mime: String, val url: String, val expiresIn: Long
)

@Serializable
private data class TemplateGenerationError(
    val error: String
)

internal fun Route.template() {
    authenticate("jwt") {
        post("/template") {
            val user = call.principal<UserPrincipal>() ?: return@post call.respond(HttpStatusCode.Unauthorized)
            val templateRequest = call.receive<TemplateRequest>()
            val template =
                ProvidersCatalog.templatingEngine.availableTemplates.firstOrNull { it.templateID == templateRequest.id }
                    ?: return@post call.respond(HttpStatusCode.NotFound)
            template.fields.forEach { field -> // Validate fields access
                when (field.type) {
                    TemplatingEngineInterface.TemplateField.FieldType.DATE, TemplatingEngineInterface.TemplateField.FieldType.STRING -> {
                        // There is no way to validate those
                    }

                    TemplatingEngineInterface.TemplateField.FieldType.CLASSID -> ProvidersCatalog.authorization.authorize(
                        user.getUserFromDB(),
                        field.requiredPermission,
                        ProvidersCatalog.databaseProvider.classesProvider.getClass(
                            templateRequest.fields[field.id]?.toIntOrNull() ?: return@post call.respond(
                                HttpStatusCode.BadRequest
                            )
                        ) ?: return@post call.respond(
                            HttpStatusCode.NotFound
                        )
                    )

                    TemplatingEngineInterface.TemplateField.FieldType.USERID -> {
                        when (field.scope) {
                            TemplatingEngineInterface.TemplateField.FieldScope.CLASS -> {
                                val target = ProvidersCatalog.databaseProvider.usersProvider.getUser(
                                    templateRequest.fields[field.id]?.toIntOrNull() ?: return@post call.respond(
                                        HttpStatusCode.BadRequest
                                    )
                                ) ?: return@post call.respond(HttpStatusCode.NotFound)
                                ProvidersCatalog.databaseProvider.rolesProvider.getAllRolesByMatch {
                                    it.userID == target.id && Roles.CLASS.roleByID(it.role.getID()) != null
                                }.any {
                                    val classID = when (it.role) {
                                        is Roles.CLASS.AbsenceProvider ->
                                            it.getField(Roles.CLASS.ABSENCE_PROVIDER.classID)

                                        is Roles.CLASS.ClassTeacher -> it.getField(Roles.CLASS.CLASS_TEACHER.classID)
                                        is Roles.CLASS.Student -> it.getField(Roles.CLASS.STUDENT.classID)
                                        is Roles.CLASS.Teacher -> it.getField(Roles.CLASS.TEACHER.classID)
                                        else -> null
                                    }
                                        ?: throw IllegalStateException("Role ${it.role.getID()} doesn't have a `classID` property")
                                    return@any runCatching {
                                        ProvidersCatalog.authorization.authorize(
                                            user.getUserFromDB(),
                                            field.requiredPermission,
                                            ProvidersCatalog.databaseProvider.classesProvider.getClass(classID)
                                                ?: throw IllegalStateException("Class with ID $classID was not found in DB, although listed in role")
                                        )
                                    }.fold({ true }, { false })
                                }
                            }

                            TemplatingEngineInterface.TemplateField.FieldScope.USER,
                            TemplatingEngineInterface.TemplateField.FieldScope.SCHOOL -> {
                                val target = ProvidersCatalog.databaseProvider.usersProvider.getUser(
                                    templateRequest.fields[field.id]?.toIntOrNull() ?: return@post call.respond(
                                        HttpStatusCode.BadRequest
                                    )
                                ) ?: return@post call.respond(HttpStatusCode.NotFound)
                                ProvidersCatalog.authorization.authorize(
                                    user.getUserFromDB(),
                                    field.requiredPermission,
                                    target
                                )
                            }
                        }
                    }
                }
            }
            val file = try {
                ProvidersCatalog.templatingEngine.renderTemplate(templateRequest.id, templateRequest.fields)
            } catch (e: IllegalArgumentException) {
                LoggerFactory.getLogger("TemplateRouter").error("Template input error", e)
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    TemplateGenerationError("Какое-то поле было заполнено неверно или не было заполнено вовсе")
                )
            } catch (e: BadArgumentException) {
                LoggerFactory.getLogger("TemplateRouter").error("Template input error", e)
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    TemplateGenerationError("Генератор шаблона вернул ошибку: ${e.message}")
                )
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
