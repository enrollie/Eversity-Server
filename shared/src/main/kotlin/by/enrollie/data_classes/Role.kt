/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/27/22, 11:40 PM
 */
@file:Suppress("UNUSED")

package by.enrollie.data_classes

import by.enrollie.annotations.UnsafeAPI
import by.enrollie.serializers.LocalDateTimeSerializer
import by.enrollie.serializers.RoleInformationSerializer
import by.enrollie.serializers.RoleSerializer
import java.time.LocalDateTime
import kotlin.reflect.KType
import kotlin.reflect.KTypeProjection
import kotlin.reflect.full.createType
import kotlin.reflect.full.starProjectedType

/**
 * Members of scopes define possible role IDs in the scope.
 */
sealed class Roles private constructor() {

    sealed interface RoleCategory {
        fun roleByID(id: String): Role?
    }

    @kotlinx.serialization.Serializable(with = RoleSerializer::class)
    sealed interface Role {
        class Field<T> private constructor(val id: String, val isRequired: Boolean, val type: KType) {
            override fun toString(): String = id

            companion object {
                internal operator fun <T> invoke(role: Role, id: String, isRequired: Boolean, type: KType): Field<T> =
                    Field("$role.$id", isRequired, type)
            }
        }

        fun fieldByID(id: String): Field<*>? = properties.firstOrNull { it.id == id }

        val properties: List<Field<*>>

        fun getID(): String = this.toString()
        override fun toString(): String
    }

    object CLASS : RoleCategory {
        class AbsenceProvider internal constructor() : Role {
            val classID = Role.Field<ClassID>(this, "classID", true, ClassID::class.starProjectedType)
            val delegatedBy = Role.Field<UserID>(this, "delegatedBy", true, UserID::class.starProjectedType)

            override val properties: List<Role.Field<*>> = listOf(classID, delegatedBy)

            override fun toString(): String = "CLASS.AbsenceProvider"
        }

        val ABSENCE_PROVIDER = AbsenceProvider()

        class Student internal constructor() : Role {
            val classID: Role.Field<ClassID> = Role.Field(this, "classID", true, ClassID::class.starProjectedType)
            val subgroups: Role.Field<List<SubgroupID>> = Role.Field(
                this, "subgroups", false, List::class.createType(
                    listOf(KTypeProjection.invariant(SubgroupID::class.starProjectedType))
                )
            )

            override fun toString(): String = "CLASS.Student"

            // List of all properties of the class.
            override val properties: List<Role.Field<*>> = listOf(classID, subgroups)
        }

        val STUDENT = Student()

        class ClassTeacher internal constructor() : Role {
            val classID: Role.Field<ClassID> = Role.Field(this, "classID", true, ClassID::class.starProjectedType)
            override fun toString(): String = "CLASS.ClassTeacher"
            override val properties: List<Role.Field<*>> = listOf(classID)
        }

        val CLASS_TEACHER = ClassTeacher()
        override fun roleByID(id: String): Role? = when (id.split(".")[1]) {
            "AbsenceProvider" -> ABSENCE_PROVIDER
            "Student" -> STUDENT
            "ClassTeacher" -> CLASS_TEACHER
            else -> null
        }
    }

    object SCHOOL : RoleCategory {
        class Administration internal constructor() : Role {
            override fun toString(): String = "SCHOOL.Administration"
            override fun equals(other: Any?): Boolean {
                return this === other
            }

            override fun hashCode(): Int {
                return System.identityHashCode(this)
            }

            override val properties: List<Role.Field<*>> = emptyList()
        }

        val ADMINISTRATION = Administration()

        class SocialTeacher internal constructor() : Role {
            override val properties: List<Role.Field<*>> = emptyList()

            override fun toString(): String = "SCHOOL.SocialTeacher"
            override fun equals(other: Any?): Boolean {
                return this === other
            }

            override fun hashCode(): Int {
                return System.identityHashCode(this)
            }
        }

        val SOCIAL_TEACHER = SocialTeacher()

        override fun roleByID(id: String): Role? = when (id.split(".")[1]) {
            "Administration" -> ADMINISTRATION
            "SocialTeacher" -> SOCIAL_TEACHER
            else -> null
        }
    }

    object SERVICE : RoleCategory {
        class SystemAdministrator internal constructor() : Role {
            override val properties: List<Role.Field<*>> = emptyList()

            override fun toString(): String = "SERVICE.SystemAdministrator"
            override fun equals(other: Any?): Boolean {
                return this === other
            }

            override fun hashCode(): Int {
                return System.identityHashCode(this)
            }
        }

        val SYSTEM_ADMINISTRATOR = SystemAdministrator()

        override fun roleByID(id: String): Role? = when (id.split(".")[1]) {
            "SystemAdministrator" -> SYSTEM_ADMINISTRATOR
            else -> null
        }
    }

    companion object {
        fun getRoleByID(id: String): Role? = when (id.split(".")[0]) {
            "CLASS" -> CLASS.roleByID(id)
            "SCHOOL" -> SCHOOL.roleByID(id)
            "SERVICE" -> SERVICE.roleByID(id)
            else -> null
        }
    }
}

@kotlinx.serialization.Serializable(with = RoleInformationSerializer::class)
class RoleInformationHolder(vararg information: Pair<Roles.Role.Field<*>, Any?>) {
    private val information: Map<String, Any?> = information.associate { it.first.id to it.second }

    @UnsafeAPI
    fun getAsMap(): Map<Roles.Role.Field<*>, Any?> =
        information.map { Roles.getRoleByID(it.key)!!.fieldByID(it.key)!! to it.value }
            .toMap() // NPE isn't possible as it was initialized with real Fields.

    @UnsafeAPI
    fun getAsStringMap(): Map<String, Any?> = information

    operator fun get(field: Roles.Role.Field<*>): Any? = information[field.id]
}

@kotlinx.serialization.Serializable
class RoleData(
    val userID: UserID,
    val role: Roles.Role,
    @kotlinx.serialization.Serializable(with = RoleInformationSerializer::class) private val additionalInformation: RoleInformationHolder,
    @kotlinx.serialization.Serializable(with = LocalDateTimeSerializer::class) val roleGrantedDateTime: LocalDateTime,
    @kotlinx.serialization.Serializable(with = LocalDateTimeSerializer::class) val roleRevokedDateTime: LocalDateTime?
) {

    @UnsafeAPI
    fun getRoleInformationHolder() = additionalInformation

    init {
        role.properties.forEach {
            if (it.isRequired && additionalInformation[it] == null) throw IllegalArgumentException("RoleData: missing required property $it")
        }
    }

    @UnsafeAPI
    fun unsafeGetField(field: Roles.Role.Field<*>): Any? = additionalInformation[field]

    @OptIn(UnsafeAPI::class)
    inline fun <reified A : Any> getField(field: Roles.Role.Field<A>): A? {
        return unsafeGetField(field) as A?
    }

    val uniqueID: String
        get() = "$userID.${role.getID()}.${hashCode()}"

    override fun toString(): String {
        return "RoleData(userID=$userID, scope=$role, additionalInformation=$additionalInformation, roleGrantedDateTime=$roleGrantedDateTime, roleRevokedDateTime=$roleRevokedDateTime)"
    }

    override fun hashCode(): Int =
        31 * userID.hashCode() + role.hashCode() + additionalInformation.hashCode() + roleGrantedDateTime.hashCode() + (roleRevokedDateTime?.hashCode()
            ?: 0)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RoleData

        if (userID != other.userID) return false
        if (role != other.role) return false
        if (additionalInformation != other.additionalInformation) return false
        if (roleGrantedDateTime != other.roleGrantedDateTime) return false
        if (roleRevokedDateTime != other.roleRevokedDateTime) return false

        return true
    }

}
