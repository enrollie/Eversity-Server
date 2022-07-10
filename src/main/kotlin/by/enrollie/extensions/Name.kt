/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/10/22, 11:47 PM
 */

package by.enrollie.extensions

import by.enrollie.data_classes.Name

fun Name.Companion.fromParserName(name: com.neitex.Name): Name {
    return Name(name.firstName, name.middleName, name.lastName)
}
