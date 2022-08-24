/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 8/13/22, 6:10 PM
 */

package by.enrollie.util

import by.enrollie.annotations.UnsafeAPI
import by.enrollie.data_classes.ClassID
import by.enrollie.data_classes.RoleData
import by.enrollie.data_classes.Roles
import by.enrollie.impl.ProvidersCatalog
import by.enrollie.privateProviders.TemplatingEngineInterface
import java.time.LocalDate
import java.time.LocalDateTime

object RoleUtil {
    fun findRoleToWriteAbsence(rolesList: List<RoleData>, targetClass: ClassID): RoleData? {
        return rolesList.filter {
            (it.role == Roles.CLASS.ABSENCE_PROVIDER || it.role == Roles.CLASS.CLASS_TEACHER || it.role == Roles.CLASS.TEACHER) && (it.roleRevokedDateTime?.isAfter(
                LocalDateTime.now()
            ) ?: true)
        }.firstOrNull {
            when (it.role) {
                Roles.CLASS.CLASS_TEACHER -> it.getField(Roles.CLASS.CLASS_TEACHER.classID) == targetClass
                Roles.CLASS.TEACHER -> it.getField(Roles.CLASS.TEACHER.classID) == targetClass
                Roles.CLASS.ABSENCE_PROVIDER -> it.getField(Roles.CLASS.ABSENCE_PROVIDER.classID) == targetClass
                else -> false
            }
        } ?: rolesList.filter {
            it.role == Roles.SCHOOL.ADMINISTRATION || it.role == Roles.SCHOOL.SOCIAL_TEACHER && (it.roleRevokedDateTime?.isAfter(
                LocalDateTime.now()
            ) ?: true)
        }.firstOrNull()
    }

    fun determineAvailableTemplates(rolesList: List<RoleData>): List<TemplatingEngineInterface.Template> {
        val roles = rolesList.map { it.role }.distinct()
        return ProvidersCatalog.templatingEngine.availableTemplates.filter {
            it.allowedRoles.any { it in roles }
        }
    }

    @OptIn(UnsafeAPI::class)
            /**
             *
             */
    fun suggestValues( // TODO: Review it...
        roles: List<RoleData>, field: TemplatingEngineInterface.TemplateField, date: LocalDate
    ): List<Pair<String, String>> {
        return when (field.type) {
            TemplatingEngineInterface.TemplateField.FieldType.DATE -> emptyList()
            TemplatingEngineInterface.TemplateField.FieldType.STRING -> emptyList()
            TemplatingEngineInterface.TemplateField.FieldType.CLASSID -> ProvidersCatalog.authorization.filterAllowed(
                ProvidersCatalog.databaseProvider.usersProvider.getUser(roles.first().userID)!!,
                field.requiredPermission,
                ProvidersCatalog.databaseProvider.classesProvider.getClasses()
            ).map { it.title to it.id.toString() }

            TemplatingEngineInterface.TemplateField.FieldType.USERID -> when (field.scope) {
                TemplatingEngineInterface.TemplateField.FieldScope.CLASS -> roles.mapNotNull {
                    it.getRoleInformationHolder()
                        .getAsMap().entries.firstOrNull { it.key.id.endsWith("classID") }?.value as? ClassID
                }.distinct().flatMap { classID ->
                    ProvidersCatalog.databaseProvider.rolesProvider.getAllRolesByMatch {
                        (it.role == Roles.CLASS.STUDENT) && (it.getField(Roles.CLASS.STUDENT.classID) == classID) && (it.roleRevokedDateTime?.isAfter(
                            date.atStartOfDay()
                        ) ?: true) && (it.roleGrantedDateTime.isBefore(date.atStartOfDay()))
                    }.map {
                        ProvidersCatalog.databaseProvider.usersProvider.getUser(it.userID)!!
                            .let { it.name.toString() to it.id.toString() }
                    }
                }.distinct()

                TemplatingEngineInterface.TemplateField.FieldScope.USER -> roles.map {
                    ProvidersCatalog.databaseProvider.usersProvider.getUser(
                        it.userID
                    )!!.name.toString() to it.userID.toString()
                }.distinct()

                TemplatingEngineInterface.TemplateField.FieldScope.SCHOOL -> ProvidersCatalog.databaseProvider.usersProvider.getUsers()
                    .let {
                        ProvidersCatalog.authorization.filterAllowed(
                            ProvidersCatalog.databaseProvider.usersProvider.getUser(
                                roles.first().userID
                            )!!, field.requiredPermission, it
                        ).map { it.name.toString() to it.id.toString() }
                    }
            }
        }
    }
}
