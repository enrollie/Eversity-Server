/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 8/12/22, 8:45 PM
 */

package by.enrollie.privateProviders

import by.enrollie.data_classes.Roles
import by.enrollie.exceptions.BadArgumentException
import java.io.File

interface TemplatingEngineInterface {
    /**
     * Data class representing a template field.
     * @param id ID of the field.
     * @param displayName Human-readable name of the field on Russian language.
     * @param type Type of the field. (Note: rendering engine will receive every field as a string, so it's up to the rendering engine to convert it to the desired type.)
     * @param scope Scope of the field to help server to suggest possible values.
     * @param requiredPermission Required permission on [scope] to access the field.
     */
    @kotlinx.serialization.Serializable
    data class TemplateField(
        val id: String,
        val displayName: String,
        val type: FieldType,
        @kotlinx.serialization.Transient
        val scope: FieldScope = FieldScope.USER,
        @kotlinx.serialization.Transient
        val requiredPermission: String = "read"
    ) {
        enum class FieldType {
            DATE, STRING, CLASSID, USERID
        }

        enum class FieldScope {
            CLASS, USER, SCHOOL
        }
    }

    /**
     * Data class representing a template.
     * @param templateID ID of the template. May be not human-readable, but must only contain letters, numbers, underscores and dashes.
     * @param displayName Human-readable name of the template on Russian language.
     * @param fields List of template fields
     * @param allowedRoles List of roles who have sufficient permissions to use this template.
     */
    @kotlinx.serialization.Serializable
    data class Template(
        val templateID: String,
        val displayName: String,
        val fields: List<TemplateField>,
        @kotlinx.serialization.Transient
        val allowedRoles: List<Roles.Role> = listOf()
    )

    /**
     * Returns a list of templates that are available for render.
     */
    val availableTemplates: List<Template>

    /**
     * Adds a new template to the list of available templates.
     * @throws IllegalArgumentException If same [template] is already registered
     */
    fun registerTemplate(template: Template, render: (Map<String, String>) -> File)

    /**
     * Renders a template with the given fields.
     * @throws IllegalArgumentException if model does not contain all required fields.
     * @throws NoSuchElementException if no template with the given ID is found.
     * @throws BadArgumentException if some given fields are syntactically correct, but they make no logical sense.
     */
    fun renderTemplate(template: String, model: Map<String, String>): File
}
