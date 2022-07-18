/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/18/22, 2:56 AM
 */

package by.enrollie.data_classes

/**
 * Stores russian declension name / titles.
 * All comments are in Russian.
 */
@kotlinx.serialization.Serializable
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

