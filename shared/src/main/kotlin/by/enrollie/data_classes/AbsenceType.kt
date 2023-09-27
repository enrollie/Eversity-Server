/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 8/1/22, 9:24 PM
 */
@file:Suppress("unused")

package by.enrollie.data_classes

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = AbsenceTypeSerializer::class)
enum class AbsenceType {
    ILLNESS {
        override val russianName: String = "Болезнь"
    },
    HEALING {
        override val russianName: String = "Лечение"
    },
    REQUEST {
        override val russianName: String = "Заявление от родителей"
    },

    DECREE {
        override val russianName: String = "Приказ директора"
    },

    COMPETITION {
        override val russianName: String = "Соревнования"
    },

    @Deprecated(
        "It is not descriptive enough for social teachers",
        ReplaceWith("COMPETITION", "by.enrollie.data_classes.AbsenceType"),
        DeprecationLevel.ERROR // You can still use its string representation if you need to i.e. migrate database from old version
    )
    OTHER_RESPECTFUL {
        override val russianName: String = "Другое (уважительная)"
    },
    OTHER_DISRESPECTFUL {
        override val russianName: String = "Другое (неуважительная)"
    };

    abstract val russianName: String
}

class AbsenceTypeSerializer : KSerializer<AbsenceType> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("AbsenceType", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): AbsenceType {
        val value = decoder.decodeString()
        if (value == "OTHER_RESPECTFUL") throw SerializationException("OTHER_RESPECTFUL is deprecated")
        return AbsenceType.values().firstOrNull { it.name == value }
            ?: throw SerializationException("Unknown absence type $value")
    }

    override fun serialize(encoder: Encoder, value: AbsenceType) {
        encoder.encodeString(value.name)
    }
}
