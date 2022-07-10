/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/10/22, 11:16 PM
 */

package by.enrollie

import by.enrollie.data_classes.Field
import by.enrollie.data_classes.RoleData
import by.enrollie.data_classes.Roles
import org.joda.time.DateTime
import org.junit.Test
import kotlin.test.assertFailsWith

class DataClassesTest {
    @Test
    fun testField() {
        class TestClass {
            val testField: String = "test"
        }

        val field = Field(TestClass::testField)
        assert(field.name == "by.enrollie.DataClassesTest\$testField\$TestClass.testField\n")
    }

    @Test
    fun testRoleData() {
        val validRoleData = RoleData(
            -1,
            Roles.CLASS.STUDENT,
            mapOf(Roles.CLASS.STUDENT.classID to -1, Roles.CLASS.STUDENT.subgroups to listOf(-1)),
            DateTime.now(),
            null
        )
        assert(validRoleData.role == Roles.CLASS.STUDENT)
        assert(validRoleData.roleID == Roles.CLASS.STUDENT.toString())
        assert(validRoleData.userID == -1)
        assert(validRoleData.getField(Roles.CLASS.STUDENT.classID) == -1)
        assert(validRoleData.getField(Roles.CLASS.STUDENT.subgroups) == listOf(-1))
        assert(validRoleData.getField(Roles.CLASS.CLASS_TEACHER.classID) == null)

        assertFailsWith(exceptionClass = IllegalArgumentException::class) {
            RoleData(
                -1,
                Roles.CLASS.STUDENT,
                mapOf(Roles.CLASS.STUDENT.subgroups to ""), // Not setting classID should throw an exception
                DateTime.now(),
                null
            )
        }
    }
}
