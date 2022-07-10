/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/10/22, 11:19 PM
 */

package by.enrollie.data_classes

/**
 * Stores russian declension name / titles.
 * All comments are in Russian.
 */
data class Declensions(
    /**
     * Именительный падеж.
     */
    val nominative: String,
    /**
     * Родительный падеж.
     */
    val genitive: String,
    /**
     * Дательный падеж.
     */
    val dative: String,
    /**
     * Винительный падеж.
     */
    val accusative: String,
    /**
     * Творительный падеж.
     */
    val instrumental: String,
    /**
     * Предложный падеж.
     */
    val prepositional: String
)

