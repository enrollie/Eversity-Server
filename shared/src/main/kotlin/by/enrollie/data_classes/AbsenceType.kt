/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/18/22, 2:52 AM
 */

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
    OTHER {
        override val russianName: String = "Другое"
    };

    abstract val russianName: String
}
