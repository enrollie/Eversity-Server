/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/10/22, 11:16 PM
 */

package by.enrollie.data_classes

import by.enrollie.annotations.UnsafeAPI
import org.joda.time.DateTime

/**
 * Members of scopes define possible role IDs in the scope.
 */
sealed class Roles private constructor() {
    sealed interface Role {
        class Field<T> private constructor(val id: String, val isRequired: Boolean) {
            override fun toString(): String = id

            companion object {
                internal operator fun <T> invoke(role: Role, id: String, isRequired: Boolean): Field<T> =
                    Field("$role.$id", isRequired)
            }
        }

        val properties: List<Field<*>>

        fun getID(): String = this.toString()
        override fun toString(): String
    }

    object CLASS {
        class AbsenceProvider internal constructor() : Role {
            val classID = Role.Field<ClassID>(this, "classID", true)
            val delegatedBy = Role.Field<UserID>(this, "delegatedBy", true)
            override val properties: List<Role.Field<*>> = listOf(classID, delegatedBy)

            override fun toString(): String = "CLASS.AbsenceProvider"
        }

        val ABSENCE_PROVIDER = AbsenceProvider()

        class Student internal constructor() : Role {
            val classID: Role.Field<ClassID> = Role.Field(this, "classID", true)
            val subgroups: Role.Field<List<SubgroupID>> = Role.Field(this, "subgroups", false)
            override fun toString(): String = "CLASS.Student"

            // List of all properties of the class.
            override val properties: List<Role.Field<*>> = listOf(classID, subgroups)
        }

        val STUDENT = Student()

        class ClassTeacher internal constructor() : Role {
            val classID: Role.Field<ClassID> = Role.Field(this, "classID", true)
            override fun toString(): String = "CLASS.ClassTeacher"
            override val properties: List<Role.Field<*>> = listOf(classID)
        }

        val CLASS_TEACHER = ClassTeacher()
    }

    object LESSON {
        class Teacher internal constructor() : Role {
            val subjectID: Role.Field<LessonID> = Role.Field(this, "subjectID", true)
            val classID: Role.Field<ClassID> = Role.Field(this, "classID", true)
            val subgroupID: Role.Field<SubgroupID?> = Role.Field(this, "subgroupID", false)
            override fun toString(): String = "LESSON.Teacher"
            override val properties: List<Role.Field<*>> = listOf(subjectID, classID, subgroupID)
        }

        val TEACHER = Teacher()
    }

    object SCHOOL {
        class Principal internal constructor() : Role {
            override fun toString(): String = "SCHOOL.Principal"
            override fun equals(other: Any?): Boolean {
                return this === other
            }

            override fun hashCode(): Int {
                return System.identityHashCode(this)
            }

            override val properties: List<Role.Field<*>> = emptyList()
        }

        val PRINCIPAL = Principal()

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

        class VicePrincipal internal constructor() : Role {
            override val properties: List<Role.Field<*>> = emptyList()

            override fun toString(): String = "SCHOOL.VicePrincipal"
            override fun equals(other: Any?): Boolean {
                return this === other
            }

            override fun hashCode(): Int {
                return System.identityHashCode(this)
            }
        }

        val VICE_PRINCIPAL = VicePrincipal()
    }

    object SERVICE {
        class Administrator internal constructor() : Role {
            override val properties: List<Role.Field<*>> = emptyList()

            override fun toString(): String = "SERVICE.Administrator"
            override fun equals(other: Any?): Boolean {
                return this === other
            }

            override fun hashCode(): Int {
                return System.identityHashCode(this)
            }
        }

        val ADMINISTRATOR = Administrator()
    }
}

class RoleData<T : Roles.Role>(
    val userID: UserID,
    val role: T,
    private val additionalInformation: Map<Roles.Role.Field<*>, Any?>,
    val roleGrantedDateTime: DateTime,
    val roleRevokedDateTime: DateTime?
) {
    val roleID: String = role.toString()

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

    val uniqueID: String = "$userID.$roleID.${hashCode()}"

    override fun toString(): String {
        return "RoleData(userID=$userID, scope=$role, additionalInformation=$additionalInformation, roleGrantedDateTime=$roleGrantedDateTime, roleRevokedDateTime=$roleRevokedDateTime)"
    }

    override fun hashCode(): Int =
        31 * userID.hashCode() + role.hashCode() + additionalInformation.hashCode() + roleGrantedDateTime.hashCode() + (roleRevokedDateTime?.hashCode()
            ?: 0)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RoleData<*>

        if (userID != other.userID) return false
        if (role != other.role) return false
        if (additionalInformation != other.additionalInformation) return false
        if (roleGrantedDateTime != other.roleGrantedDateTime) return false
        if (roleRevokedDateTime != other.roleRevokedDateTime) return false
        if (roleID != other.roleID) return false

        return true
    }

}
