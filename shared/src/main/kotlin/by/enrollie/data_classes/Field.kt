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

    override fun toString(): String {
        return "Entry(name='$name')"
    }

    init {
        name = "${property.javaField!!.declaringClass.name}.${property.name}"
    }
}
