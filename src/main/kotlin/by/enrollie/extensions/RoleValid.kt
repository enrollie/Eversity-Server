/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 11/20/22, 7:01 PM
 */

package by.enrollie.extensions

import by.enrollie.data_classes.RoleData
import java.time.LocalDateTime

fun RoleData.isValid(): Boolean {
    return LocalDateTime.now() in roleGrantedDateTime..(roleRevokedDateTime ?: LocalDateTime.MAX)
}

fun List<RoleData>.filterValid(): List<RoleData> {
    return this.filter { it.isValid() }
}

fun List<RoleData>.filterInvalid(): List<RoleData> {
    return this.filter { !it.isValid() }
}
