/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 8/7/22, 3:49 AM
 */

package by.enrollie.routes

import by.enrollie.data_classes.AbsenceRecord
import by.enrollie.data_classes.SchoolClass
import by.enrollie.impl.AuthorizationProviderImpl
import by.enrollie.impl.ProvidersCatalog
import by.enrollie.plugins.UserPrincipal
import by.enrollie.util.parseDate
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

@kotlinx.serialization.Serializable
internal data class AbsencesGetResponse(
    val absences: List<AbsenceRecord>, val noData: List<SchoolClass>
)

private fun Route.AbsencesGet() {
    get {
        val user = call.principal<UserPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
        ProvidersCatalog.authorization.authorize(
            user.getUserFromDB(),
            "read_all_absences",
            AuthorizationProviderImpl.School()
        )
        val date =
            call.request.queryParameters["date"]?.parseDate() ?: return@get call.respond(HttpStatusCode.BadRequest)
        val absences = ProvidersCatalog.databaseProvider.absenceProvider.getAbsences(date)
        val noData = kotlin.run {
            val classes = ProvidersCatalog.databaseProvider.classesProvider.getClasses()
            val noDataIDs = ProvidersCatalog.databaseProvider.absenceProvider.getClassesWithoutAbsenceInfo(date)
            classes.filter { it.id in noDataIDs }
        }
        call.respond(AbsencesGetResponse(absences, noData))
    }
}

internal fun Route.absences() {
    authenticate("jwt") { route("/absences") { AbsencesGet() } }
}
