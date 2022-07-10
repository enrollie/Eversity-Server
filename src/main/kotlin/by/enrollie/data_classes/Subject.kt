/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/10/22, 11:16 PM
 */

package by.enrollie.data_classes

typealias SubjectID = Int

data class Subject(
    val id: SubjectID, val fullTitle: String, val shortTitle: String? = null,
) {
    override fun toString(): String = "Subject(id=$id, fullTitle='$fullTitle', shortTitle='$shortTitle')"
}
