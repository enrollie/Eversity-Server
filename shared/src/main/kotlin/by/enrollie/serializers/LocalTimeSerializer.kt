/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 8/15/22, 11:36 PM
 */

package by.enrollie.serializers

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.LocalTime

class LocalTimeSerializer : KSerializer<LocalTime> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LocalTime", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): LocalTime {
        decoder.decodeString().split(":").let {
            return try {
                LocalTime.of(it[0].toInt(), it[1].toInt())
            } catch (e: Exception) {
                throw SerializationException(e.message)
            }
        }
    }

    override fun serialize(encoder: Encoder, value: LocalTime) {
        encoder.encodeString("${value.hour.toString().padStart(2, '0')}:${value.minute.toString().padStart(2, '0')}")
    }
}
