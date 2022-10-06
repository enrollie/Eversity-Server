/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 8/14/22, 1:43 AM
 */

package by.enrollie.impl

import by.enrollie.data_classes.*
import by.enrollie.extensions.isBetweenOrEqual
import by.enrollie.privateProviders.TemplatingEngineInterface
import by.enrollie.util.parseDate
import fr.opensagres.xdocreport.document.registry.XDocReportRegistry
import fr.opensagres.xdocreport.template.TemplateEngineKind
import java.io.File
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.*

class TemplatingEngineImpl : TemplatingEngineInterface {
    private val templatesWithHandlers: HashMap<TemplatingEngineInterface.Template, (Map<String, String>) -> File> =
        HashMap()
    override val availableTemplates: List<TemplatingEngineInterface.Template>
        get() = templatesWithHandlers.keys.toList()

    override fun registerTemplate(template: TemplatingEngineInterface.Template, render: (Map<String, String>) -> File) {
        if (template.hashCode() in templatesWithHandlers.keys.map { it.hashCode() }
                .distinct()) throw IllegalArgumentException("Template with hashcode ${template.hashCode()} is already registered")
        if (template.templateID in templatesWithHandlers.keys.map { it.templateID }
                .distinct()) throw IllegalArgumentException("Template with ID ${template.templateID} is already registered")
        templatesWithHandlers[template] = render
    }

    override fun renderTemplate(template: String, model: Map<String, String>): File =
        (templatesWithHandlers.entries.firstOrNull {
            it.key.templateID == template
        } ?: throw NoSuchElementException("")).value(model)

    init {
        registerTemplate(
            TemplatingEngineInterface.Template(
                "EVERSITY_SchoolWideAbsenceReportSingleDate", "Отчёт об отсутствии в школе за один день", listOf(
                    TemplatingEngineInterface.TemplateField(
                        "date", "Дата", TemplatingEngineInterface.TemplateField.FieldType.DATE
                    )
                ), listOf(Roles.SCHOOL.ADMINISTRATION, Roles.SCHOOL.SOCIAL_TEACHER, Roles.SERVICE.SYSTEM_ADMINISTRATOR)
            ), render = ::renderSchoolAbsenceTemplate
        )
    }
}

private fun renderSchoolAbsenceTemplate(model: Map<String, String>): File {
    val date = model["date"]?.parseDate() ?: throw IllegalArgumentException("No date in model")

    @Suppress("UNUSED") // Used in DocX
    data class AbsenceReportRow(
        val totalPupils: Int,
        val totalIll: Int,
        val totalHealing: Int,
        val totalRequest: Int,
        val totalPrincipalDecision: Int,
        val totalUnknown: Int,
    ) {
        val totalAttended: Int
            get() = totalPupils - (totalIll + totalHealing + totalRequest + totalPrincipalDecision + totalUnknown)
        val totalAttendedPercent: String
            get() = "${
                String.format("%.1f",
                    ((totalAttended.toDouble() / totalPupils.toDouble()) * 100).takeIf { !it.isNaN() } ?: 0.0)
            }%"
        val totalIllPercent: String
            get() = "${
                String.format("%.1f",
                    ((totalIll.toDouble() / totalPupils.toDouble()) * 100).takeIf { !it.isNaN() } ?: 0.0)
            }%"
        val totalHealingPercent: String
            get() = "${
                String.format("%.1f",
                    ((totalHealing.toDouble() / totalPupils.toDouble()) * 100).takeIf { !it.isNaN() } ?: 0.0)
            }%"
        val totalRequestPercent: String
            get() = "${
                String.format("%.1f",
                    ((totalRequest.toDouble() / totalPupils.toDouble()) * 100).takeIf { !it.isNaN() } ?: 0.0)
            }%"
        val totalPrincipalDecisionPercent: String
            get() = "${
                String.format("%.1f",
                    ((totalPrincipalDecision.toDouble() / totalPupils.toDouble()) * 100).takeIf { !it.isNaN() } ?: 0.0)
            }%"
        val totalUnknownPercent: String
            get() = "${
                String.format("%.1f",
                    ((totalUnknown.toDouble() / totalPupils.toDouble()) * 100).takeIf { !it.isNaN() } ?: 0.0)
            }%"
    }

    fun generateAbsenceReportRow(
        absences: List<AbsenceRecord>, classList: List<ClassID>, totalPupils: Int
    ): AbsenceReportRow = AbsenceReportRow(
        totalPupils,
        absences.filter { it.classID in classList }.count { it.absenceType == AbsenceType.ILLNESS },
        absences.filter { it.classID in classList }.count { it.absenceType == AbsenceType.HEALING },
        absences.filter { it.classID in classList }.count { it.absenceType == AbsenceType.REQUEST },
        absences.filter { it.classID in classList }.count { it.absenceType == AbsenceType.DECREE },
        absences.filter { it.classID in classList }
            .count { it.absenceType == AbsenceType.OTHER_RESPECTFUL || it.absenceType == AbsenceType.OTHER_DISRESPECTFUL },
    )

    val template = Unit.javaClass.getResourceAsStream("/templates/SchoolAbsenceTemplate.docx")
    val absenceData = ProvidersCatalog.databaseProvider.absenceProvider.getAbsences(date).filterNot {
        it.lessonsList.isEmpty()
    }
    val (firstShiftClasses, secondShiftClasses) = ProvidersCatalog.databaseProvider.classesProvider.getClasses()
        .partition {
            it.shift == TeachingShift.FIRST
        }.let { listsPair ->
            listsPair.first.map { it.id } to listsPair.second.map { it.id }
        }
    val pupilsFirstShift = ProvidersCatalog.databaseProvider.rolesProvider.getAllRolesByMatch {
        it.role == Roles.CLASS.STUDENT && date.isBetweenOrEqual(
            it.roleGrantedDateTime.toLocalDate(), it.roleRevokedDateTime?.toLocalDate() ?: date.plusYears(1)
        ) && it.getField(Roles.CLASS.STUDENT.classID) in firstShiftClasses
    }
    val pupilsSecondShift = ProvidersCatalog.databaseProvider.rolesProvider.getAllRolesByMatch {
        it.role == Roles.CLASS.STUDENT && date.isBetweenOrEqual(
            it.roleGrantedDateTime.toLocalDate(), it.roleRevokedDateTime?.toLocalDate() ?: date.plusYears(1)
        ) && it.getField(Roles.CLASS.STUDENT.classID) in secondShiftClasses
    }
    val firstShiftData = generateAbsenceReportRow(absenceData, firstShiftClasses, pupilsFirstShift.size)
    val secondShiftData = generateAbsenceReportRow(absenceData, secondShiftClasses, pupilsSecondShift.size)

    val report = XDocReportRegistry.getRegistry().loadReport(template, TemplateEngineKind.Velocity)
    val fieldsMetadata = report.createFieldsMetadata().apply {
        load("firstShift", AbsenceReportRow::class.java)
        load("secondShift", AbsenceReportRow::class.java)
    }
    report.fieldsMetadata = fieldsMetadata
    val context = report.createContext().apply {
        put("firstShift", firstShiftData)
        put("secondShift", secondShiftData)
        put("schoolName", ProvidersCatalog.configuration.schoolConfiguration.title.nominative)
        put(
            "currentDate",
            date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.forLanguageTag("ru-ru")))
        )
    }
    val returnFile = File.createTempFile("schoolAbsence", ".docx")
    report.process(context, returnFile.outputStream())
    return returnFile
}
