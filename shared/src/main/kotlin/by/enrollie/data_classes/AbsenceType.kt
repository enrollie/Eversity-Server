/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 8/1/22, 9:24 PM
 */
@file:Suppress("unused")

package by.enrollie.data_classes

@kotlinx.serialization.Serializable
enum class AbsenceType {
    ILLNESS {
        override val russianName: String = "Болезнь"
    },
    HEALING {
        override val russianName: String = "Лечение"
    },
    REQUEST {
        override val russianName: String = "Запрос от родителей"
    },
    DECREE {
        override val russianName: String = "Приказ"
    },
    OTHER_RESPECTFUL {
        override val russianName: String = "Другое (уважительная)"
    },
    OTHER_DISRESPECTFUL {
        override val russianName: String = "Другое (неуважительная)"
    };

    abstract val russianName: String
}
