/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 8/14/22, 1:43 AM
 */

package by.enrollie.impl

import by.enrollie.data_classes.*
import by.enrollie.exceptions.BadArgumentException
import by.enrollie.extensions.isBetweenOrEqual
import by.enrollie.privateProviders.TemplatingEngineInterface
import by.enrollie.util.parseDate
import fr.opensagres.xdocreport.document.registry.XDocReportRegistry
import fr.opensagres.xdocreport.template.TemplateEngineKind
import java.io.File
import java.time.LocalDate
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
        if (template.templateID.matches(Regex("[^a-zA-Z0-9\\-_]"))) throw IllegalArgumentException("Template ID must contain only alphanumeric latin characters, dashes and underscores")
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
        registerTemplate(
            TemplatingEngineInterface.Template(
                "EVERSITY_ClassAbsenceReportDatesRange",
                "Отчёт об истории отсутствия в классе",
                listOf(
                    TemplatingEngineInterface.TemplateField(
                        "classID",
                        "Класс",
                        TemplatingEngineInterface.TemplateField.FieldType.CLASSID,
                        TemplatingEngineInterface.TemplateField.FieldScope.USER,
                        "read_absence"
                    ),
                    TemplatingEngineInterface.TemplateField(
                        "firstDate",
                        "Начало периода (включительно)",
                        TemplatingEngineInterface.TemplateField.FieldType.DATE
                    ),
                    TemplatingEngineInterface.TemplateField(
                        "secondDate",
                        "Конец периода (включительно)",
                        TemplatingEngineInterface.TemplateField.FieldType.DATE
                    )
                ),
                listOf(
                    Roles.SCHOOL.ADMINISTRATION,
                    Roles.SCHOOL.SOCIAL_TEACHER,
                    Roles.SERVICE.SYSTEM_ADMINISTRATOR,
                    Roles.CLASS.CLASS_TEACHER
                )
            ), render = ::renderClassAbsenceReport
        )
    }
}

private fun renderSchoolAbsenceTemplate(model: Map<String, String>): File {
    val date = model["date"]?.parseDate() ?: throw IllegalArgumentException("No date in model")

    data class AbsenceReportRow(
        val totalPupils: Int,
        val totalIll: Int,
        val totalHealing: Int,
        val totalRequest: Int,
        val totalCompetition: Int,
        val totalUnknown: Int,
    ) {
        val totalAttended: Int
            get() = totalPupils - (totalIll + totalHealing + totalRequest + totalCompetition + totalUnknown)
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
        val totalCompetitionPercent: String
            get() = "${
                String.format("%.1f",
                    ((totalCompetition.toDouble() / totalPupils.toDouble()) * 100).takeIf { !it.isNaN() } ?: 0.0)
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
        absences.filter { it.classID in classList && it.lessonsList.contains(1) }
            .count { it.absenceType == AbsenceType.ILLNESS },
        absences.filter { it.classID in classList && it.lessonsList.contains(1) }
            .count { it.absenceType == AbsenceType.HEALING },
        absences.filter { it.classID in classList && it.lessonsList.contains(1) }
            .count { it.absenceType == AbsenceType.REQUEST || it.absenceType == AbsenceType.DECREE },
        absences.filter { it.classID in classList && it.lessonsList.contains(1) }
            .count { it.absenceType == AbsenceType.COMPETITION },
        absences.filter { it.classID in classList && it.lessonsList.contains(1) }
            .count { it.absenceType == AbsenceType.OTHER_DISRESPECTFUL },
    )

    val template = Unit.javaClass.getResourceAsStream("/templates/SchoolAbsenceTemplate.docx")
    val (firstShiftData, secondShiftData) = ProvidersCatalog.databaseProvider.runInSingleTransaction { database ->
        val absenceData = database.absenceProvider.getAbsences(date).filterNot {
            it.lessonsList.isEmpty()
        }
        val (firstShiftClasses, secondShiftClasses) = database.classesProvider.getClasses()
            .partition {
                it.shift == TeachingShift.FIRST
            }.let { listsPair ->
                listsPair.first.map { it.id } to listsPair.second.map { it.id }
            }
        val pupilsFirstShift = database.rolesProvider.getAllRolesByMatch {
            it.role == Roles.CLASS.STUDENT && date.isBetweenOrEqual(
                it.roleGrantedDateTime.toLocalDate(), it.roleRevokedDateTime?.toLocalDate() ?: date.plusYears(1)
            ) && it.getField(Roles.CLASS.STUDENT.classID) in firstShiftClasses
        }
        val pupilsSecondShift = database.rolesProvider.getAllRolesByMatch {
            it.role == Roles.CLASS.STUDENT && date.isBetweenOrEqual(
                it.roleGrantedDateTime.toLocalDate(), it.roleRevokedDateTime?.toLocalDate() ?: date.plusYears(1)
            ) && it.getField(Roles.CLASS.STUDENT.classID) in secondShiftClasses
        }
        val firstShiftData = generateAbsenceReportRow(absenceData, firstShiftClasses, pupilsFirstShift.size)
        val secondShiftData = generateAbsenceReportRow(absenceData, secondShiftClasses, pupilsSecondShift.size)
        firstShiftData to secondShiftData
    }

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

private fun renderClassAbsenceReport(model: Map<String, String>): File {
    val classID = model["classID"]?.toIntOrNull() ?: throw IllegalArgumentException("classID is not specified")
    val firstDate = model["firstDate"]?.parseDate() ?: throw IllegalArgumentException("firstDate is not specified")
    val secondDate = model["secondDate"]?.parseDate() ?: throw IllegalArgumentException("secondDate is not specified")
    if (firstDate > secondDate) throw BadArgumentException(
        "firstDate is greater than secondDate",
        "Конечная дата наступает раньше начальной"
    )
    val schoolClass = ProvidersCatalog.databaseProvider.classesProvider.getClass(classID)
        ?: throw IllegalArgumentException("classID is not specified")

    data class Absence(
        val pupilPlace: Int,
        val pupilName: String,
        val totalSkippedLessons: Int,
        val illnessLessons: Int,
        val requestLessons: Int,
        val healingLessons: Int,
        val decreeLessons: Int,
        val competitionLessons: Int,
        val unknownDays: Int,
        val unknownLessons: Int
    )

    val absences = ProvidersCatalog.databaseProvider.runInSingleTransaction { database ->
        val pupils = database.rolesProvider.getAllRolesByMatch {
            it.role == Roles.CLASS.STUDENT && it.getField(Roles.CLASS.STUDENT.classID) == classID && it.roleGrantedDateTime.toLocalDate() <= secondDate && (it.roleRevokedDateTime?.toLocalDate()
                ?: secondDate) >= firstDate
        }.map { it.userID }.let { userIDs ->
            database.usersProvider.getUsers().filter { it.id in userIDs }
        }
        val absences = database.absenceProvider.getAbsencesForClass(classID, firstDate to secondDate)
        pupils.mapIndexed { index, pupil ->
            Absence(
                index + 1,
                pupil.name.toString(),
                absences.filter { it.student.id == pupil.id }.sumOf { it.lessonsList.size },
                absences.filter { it.student.id == pupil.id && it.absenceType == AbsenceType.ILLNESS }
                    .sumOf { it.lessonsList.size },
                absences.filter { it.student.id == pupil.id && it.absenceType == AbsenceType.REQUEST }
                    .sumOf { it.lessonsList.size },
                absences.filter { it.student.id == pupil.id && it.absenceType == AbsenceType.HEALING }
                    .sumOf { it.lessonsList.size },
                absences.filter { it.student.id == pupil.id && it.absenceType == AbsenceType.DECREE }
                    .sumOf { it.lessonsList.size },
                absences.filter { it.student.id == pupil.id && it.absenceType == AbsenceType.COMPETITION }
                    .sumOf { it.lessonsList.size },
                absences.filter { it.student.id == pupil.id && it.absenceType == AbsenceType.OTHER_DISRESPECTFUL }
                    .map { it.absenceDate }.distinct().size,
                absences.filter { it.student.id == pupil.id && it.absenceType == AbsenceType.OTHER_DISRESPECTFUL }
                    .sumOf { it.lessonsList.size }
            )
        }
    }

    data class FillInfo(
        val classTitle: String,
        val beginDate: String,
        val endDate: String,
        val fillDate: String
    )

    data class AbsenceSummary(
        val summary: Int,
        val illness: Int,
        val request: Int,
        val healing: Int,
        val decree: Int,
        val competition: Int,
        val unknownDays: Int,
        val unknownLessons: Int
    )

    val fillInfo = FillInfo(
        schoolClass.title,
        firstDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.forLanguageTag("ru"))),
        secondDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.forLanguageTag("ru"))),
        LocalDate.now()
            .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.forLanguageTag("ru")))
    )
    val summaries = AbsenceSummary(
        absences.sumOf { it.totalSkippedLessons },
        absences.sumOf { it.illnessLessons },
        absences.sumOf { it.requestLessons },
        absences.sumOf { it.healingLessons },
        absences.sumOf { it.decreeLessons },
        absences.sumOf { it.competitionLessons },
        absences.sumOf { it.unknownDays },
        absences.sumOf { it.unknownLessons }
    )

    val template = Unit::class.java.getResourceAsStream("/templates/ClassAbsenceReport.docx")
    val report = XDocReportRegistry.getRegistry().loadReport(template, TemplateEngineKind.Velocity)
    val fieldsMetadata = report.createFieldsMetadata().apply {
        load("absence", Absence::class.java)
        load("info", FillInfo::class.java)
        load("absenceSummary", AbsenceSummary::class.java)
        addFieldAsList("absence.pupilPlace")
        addFieldAsList("absence.pupilName")
        addFieldAsList("absence.totalSkippedLessons")
        addFieldAsList("absence.illnessLessons")
        addFieldAsList("absence.requestLessons")
        addFieldAsList("absence.healingLessons")
        addFieldAsList("absence.decreeLessons")
        addFieldAsList("absence.competitionLessons")
        addFieldAsList("absence.unknownDays")
        addFieldAsList("absence.unknownLessons")
    }
    report.fieldsMetadata = fieldsMetadata
    val context = report.createContext()
    context.put("absenceSummary", summaries)
    context.put("absence", absences)
    context.put("info", fillInfo)

    val tempFile = File.createTempFile("EV-Class-$classID-stat", ".docx")
    report.process(context, tempFile.outputStream())
    return tempFile
}
