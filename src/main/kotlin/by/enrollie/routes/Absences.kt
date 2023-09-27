/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 8/24/22, 7:50 PM
 */

package by.enrollie.routes

import by.enrollie.data_classes.*
import by.enrollie.extensions.isBetweenOrEqual
import by.enrollie.impl.AuthorizationProviderImpl
import by.enrollie.impl.ProvidersCatalog
import by.enrollie.plugins.UserPrincipal
import by.enrollie.serializers.LocalDateSerializer
import by.enrollie.util.parseDate
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.mapSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

@kotlinx.serialization.Serializable
internal data class AbsencesGetResponse(
    val absences: List<AbsenceRecord>, val noData: List<SchoolClass>
)

private fun Route.absencesGet() {
    get {
        val user = call.principal<UserPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
        ProvidersCatalog.authorization.authorize(
            user.getUserFromDB(), "read_all_absences", AuthorizationProviderImpl.school
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
internal data class AbsenceTotalStudentsByShift(
    val firstShift: Int, val secondShift: Int,
)

@kotlinx.serialization.Serializable
internal data class AbsencesSummaryElement(
    val firstShift: Map<AbsenceType, Int>,
    val secondShift: Map<AbsenceType, Int>,
    val noDataClasses: List<SchoolClass>,
    val totalStudents: AbsenceTotalStudentsByShift
)

@kotlinx.serialization.Serializable(with = AbsencesSummaryResponseSerializer::class)
internal data class AbsencesSummaryResponse(
    val map: Map<@kotlinx.serialization.Serializable(with = LocalDateSerializer::class) LocalDate, AbsencesSummaryElement>
)

private class AbsencesSummaryResponseSerializer : KSerializer<AbsencesSummaryResponse> {
    @OptIn(ExperimentalSerializationApi::class)
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

private fun Route.absencesSummaryGet() {
    val absenceSummariesCache: Cache<ClosedRange<LocalDateTime>, Map<LocalDate, AbsencesSummaryElement>> =
        Caffeine.newBuilder().maximumSize(5).initialCapacity(5).expireAfterWrite(Duration.ofMinutes(10)).build()

    @OptIn(DelicateCoroutinesApi::class)
    val context = newSingleThreadContext("AbsencesCacheListener")
    CoroutineScope(context).launch {
        ProvidersCatalog.databaseProvider.absenceProvider.eventsFlow.collect { event ->
            absenceSummariesCache.asMap().keys.forEach {
                if (it.contains(event.eventSubject.absenceDate.atStartOfDay())) {
                    absenceSummariesCache.invalidate(it)
                }
            }
        }
    }

    get("/summary") {
        val user = call.principal<UserPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
        ProvidersCatalog.authorization.authorize(
            user.getUserFromDB(), "read_statistics", AuthorizationProviderImpl.school
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
        if (startDate != null && endDate != null) {
            absenceSummariesCache.getIfPresent(startDate.atStartOfDay()..endDate.atStartOfDay())?.also {
                return@get call.respond(AbsencesSummaryResponse(it))
            }
        } else {
            absenceSummariesCache.getIfPresent(LocalDate.now().atStartOfDay()..LocalDate.now().atStartOfDay())?.also {
                return@get call.respond(AbsencesSummaryResponse(it))
            }
        }
        val (absences, dates) = if (startDate != null && endDate != null) {
            ProvidersCatalog.databaseProvider.absenceProvider.getAbsences(startDate to endDate)
                .filterNot { it.lessonsList.isEmpty() }
                .fold(mutableMapOf<LocalDate, List<AbsenceRecord>>()) { acc, absenceRecord ->
                    acc[absenceRecord.absenceDate] = (acc[absenceRecord.absenceDate] ?: listOf()).plus(absenceRecord)
                    acc
                } to startDate.datesUntil(
                endDate.plusDays(1)
            ).toList()
        } else {
            ProvidersCatalog.databaseProvider.absenceProvider.getAbsences(LocalDate.now())
                .filterNot { it.lessonsList.isEmpty() }
                .fold(mutableMapOf<LocalDate, List<AbsenceRecord>>()) { acc, absenceRecord ->
                    acc[absenceRecord.absenceDate] = (acc[absenceRecord.absenceDate] ?: listOf()).plus(absenceRecord)
                    acc
                } to listOf(LocalDate.now())
        }
        val summary = ProvidersCatalog.databaseProvider.runInSingleTransaction { database ->
            val classes = database.classesProvider.getClasses()
            val noData = if (classes.size < dates.size) {
                classes.map { schoolClass ->
                    database.absenceProvider.getDatesWithoutAbsenceInfo(
                        schoolClass.id, (startDate ?: LocalDate.now()) to (endDate ?: LocalDate.now())
                    ).associateWith {
                        schoolClass
                    }
                }.let { mapList ->
                    mapList.fold<Map<LocalDate, SchoolClass>, MutableMap<LocalDate, List<SchoolClass>>>(mutableMapOf()) { map, pair ->
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
                dates.associateWith { date ->
                    val ids = database.absenceProvider.getClassesWithoutAbsenceInfo(date)
                    classes.filter { it.id in ids }
                }
            }
            val (firstShiftClasses, secondShiftClasses) = classes.partition {
                it.shift == TeachingShift.FIRST
            }.let {
                it.first.map(SchoolClass::id) to it.second.map(SchoolClass::id)
            }
            val (firstShiftStudents, secondShiftStudents) = database.rolesProvider.getAllRolesByMatch {
                it.role == Roles.CLASS.STUDENT && !(it.roleRevokedDateTime?.isBetweenOrEqual(
                    startDate?.atStartOfDay() ?: LocalDateTime.now(),
                    endDate?.atStartOfDay() ?: LocalDateTime.now().plusDays(1)
                ) ?: false)
            }.partition {
                it.getField(Roles.CLASS.STUDENT.classID) in firstShiftClasses
            }
            val summary = dates.associateWith { date ->
                val firstShift =
                    (absences[date] ?: listOf()).filter { it.classID in firstShiftClasses }.groupBy { it.absenceType }
                        .mapValues {
                            it.value.size
                        }.let { map ->
                            AbsenceType.values().associateWith {
                                map[it] ?: 0
                            }
                        }
                val secondShift =
                    (absences[date] ?: listOf()).filter { it.classID in secondShiftClasses && it.absenceDate == date }
                        .groupBy { it.absenceType }.mapValues {
                            it.value.size
                        }.let { map ->
                            AbsenceType.values().associateWith {
                                map[it] ?: 0
                            }
                        }
                AbsencesSummaryElement(
                    firstShift, secondShift, noData[date] ?: listOf(), AbsenceTotalStudentsByShift(
                        firstShiftStudents.filter {
                            it.roleGrantedDateTime.toLocalDate()
                                .isBefore(date) && !(it.roleRevokedDateTime?.toLocalDate()?.isAfter(date) ?: false)
                        }.size,
                        secondShiftStudents.filter {
                            it.roleGrantedDateTime.toLocalDate()
                                .isBefore(date) && !(it.roleRevokedDateTime?.toLocalDate()?.isAfter(date) ?: false)
                        }.size
                    )
                )
            }
            summary
        }
        if (startDate != null && endDate != null) {
            absenceSummariesCache.put(startDate.atStartOfDay()..endDate.atStartOfDay(), summary)
        } else {
            absenceSummariesCache.put(LocalDate.now().atStartOfDay()..LocalDate.now().atStartOfDay(), summary)
        }
        call.respond(AbsencesSummaryResponse(summary))
    }
}

internal fun Route.absences() {
    authenticate("jwt") {
        route("/absences") {
            absencesGet()
            absencesSummaryGet()
        }
    }
}
