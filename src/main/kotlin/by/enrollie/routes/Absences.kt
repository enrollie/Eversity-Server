/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 8/24/22, 7:50 PM
 */

package by.enrollie.routes

import by.enrollie.data_classes.AbsenceRecord
import by.enrollie.data_classes.AbsenceType
import by.enrollie.data_classes.SchoolClass
import by.enrollie.data_classes.TeachingShift
import by.enrollie.impl.AuthorizationProviderImpl
import by.enrollie.impl.ProvidersCatalog
import by.enrollie.plugins.UserPrincipal
import by.enrollie.serializers.LocalDateSerializer
import by.enrollie.util.parseDate
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.mapSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.LocalDate

@kotlinx.serialization.Serializable
internal data class AbsencesGetResponse(
    val absences: List<AbsenceRecord>, val noData: List<SchoolClass>
)

private fun Route.AbsencesGet() {
    get {
        val user = call.principal<UserPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
        ProvidersCatalog.authorization.authorize(
            user.getUserFromDB(), "read_all_absences", AuthorizationProviderImpl.School()
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

@kotlinx.serialization.Serializable
internal data class AbsencesSummaryElement(
    val firstShift: Map<AbsenceType, Int>, val secondShift: Map<AbsenceType, Int>, val noData: List<SchoolClass>
)

@kotlinx.serialization.Serializable(with = AbsencesSummaryResponseSerializer::class)
internal data class AbsencesSummaryResponse(
    val map: Map<@kotlinx.serialization.Serializable(with = LocalDateSerializer::class) LocalDate, AbsencesSummaryElement>
)

private class AbsencesSummaryResponseSerializer : KSerializer<AbsencesSummaryResponse> {
    override val descriptor: SerialDescriptor =
        mapSerialDescriptor(LocalDateSerializer().descriptor, AbsencesSummaryElement.serializer().descriptor)

    override fun deserialize(decoder: Decoder): AbsencesSummaryResponse {
        val deserialized =
            decoder.decodeSerializableValue(MapSerializer(LocalDateSerializer(), AbsencesSummaryElement.serializer()))
        return AbsencesSummaryResponse(deserialized)
    }

    override fun serialize(encoder: Encoder, value: AbsencesSummaryResponse) {
        encoder.encodeSerializableValue(
            MapSerializer(LocalDateSerializer(), AbsencesSummaryElement.serializer()), value.map
        )
    }
}

private fun Route.AbsencesSummaryGet() {
    get("/summary") {
        val user = call.principal<UserPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
        ProvidersCatalog.authorization.authorize(
            user.getUserFromDB(), "read_statistics", AuthorizationProviderImpl.School()
        )
        val startDate = call.request.queryParameters["startDate"]?.let {
            it.parseDate() ?: return@get call.respond(HttpStatusCode.BadRequest)
        }
        val endDate = call.request.queryParameters["endDate"]?.let {
            it.parseDate() ?: return@get call.respond(HttpStatusCode.BadRequest)
        } ?: if (startDate != null) LocalDate.now() else null
        if (endDate != null && startDate == null) {
            return@get call.respond(HttpStatusCode.BadRequest)
        }
        if (endDate != null && endDate < startDate) {
            return@get call.respond(HttpStatusCode.BadRequest)
        }
        val (absences, dates) = if (startDate != null && endDate != null) {
            ProvidersCatalog.databaseProvider.absenceProvider.getAbsences(startDate to endDate) to startDate.datesUntil(
                endDate.plusDays(1)
            ).toList()
        } else {
            ProvidersCatalog.databaseProvider.absenceProvider.getAbsences(LocalDate.now()) to listOf(LocalDate.now())
        }
        val classes = ProvidersCatalog.databaseProvider.classesProvider.getClasses()
        val noData = if (classes.size < dates.size) {
            classes.map { schoolClass ->
                ProvidersCatalog.databaseProvider.absenceProvider.getDatesWithoutAbsenceInfo(
                    schoolClass.id, (startDate ?: LocalDate.now()) to (endDate ?: LocalDate.now())
                ).associateWith {
                    schoolClass
                }
            }.let {
                it.fold<Map<LocalDate, SchoolClass>, MutableMap<LocalDate, List<SchoolClass>>>(mutableMapOf()) { map, pair ->
                    pair.toList().forEach {
                        map[it.first] = (map[it.first] ?: listOf()) + it.second
                    }
                    map
                }.let { map ->
                    dates.associateWith {
                        map[it] ?: listOf()
                    }
                }
            }
        } else {
            dates.associateWith {
                val ids = ProvidersCatalog.databaseProvider.absenceProvider.getClassesWithoutAbsenceInfo(it)
                classes.filter { it.id in ids }
            }
        }
        val (firstShiftClasses, secondShiftClasses) = classes.partition {
            it.shift == TeachingShift.FIRST
        }.let {
            it.first.map(SchoolClass::id) to it.second.map(SchoolClass::id)
        }
        val summary = dates.associateWith {
            val firstShift = absences.filter { it.classID in firstShiftClasses }.groupBy { it.absenceType }.mapValues {
                it.value.size
            }.let { map ->
                AbsenceType.values().associateWith {
                    map[it] ?: 0
                }
            }
            val secondShift =
                absences.filter { it.classID in secondShiftClasses }.groupBy { it.absenceType }.mapValues {
                    it.value.size
                }.let { map ->
                    AbsenceType.values().associateWith {
                        map[it] ?: 0
                    }
                }
            AbsencesSummaryElement(firstShift, secondShift, noData[it] ?: listOf())
        }
        call.respond(AbsencesSummaryResponse(summary))
    }
}

internal fun Route.absences() {
    authenticate("jwt") {
        route("/absences") {
            AbsencesGet()
            AbsencesSummaryGet()
        }
    }
}
