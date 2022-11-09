/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/10/22, 11:16 PM
 */

package by.enrollie.data_classes

import kotlin.reflect.KProperty
import kotlin.reflect.jvm.javaField

class Field<T : Any>(property: KProperty<T>) {
    val name: String
    private val property: KProperty<T>

    override fun toString(): String {
        return "Entry(name='$name')"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Field<*>) return false

        if (name != other.name) return false
        if (property != other.property) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + property.hashCode()
        return result
    }

    init {
        name = "${property.javaField!!.declaringClass.name}.${property.name}"
        this.property = property
    }
}
