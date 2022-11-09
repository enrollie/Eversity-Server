/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 8/15/22, 11:33 PM
 */

package by.enrollie.serializers

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter


class LocalDateTimeSerializer : KSerializer<LocalDateTime> {
    companion object {
        val defaultFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd\'T\'HH:mm:ssXXX")

        /**
         * @throws SerializationException if the string is not in the expected format.
         */
        fun parse(string: String): LocalDateTime {
            val defParseResult = kotlin.runCatching {
                LocalDateTime.parse(string, defaultFormatter)
            }
            if (defParseResult.isSuccess) return defParseResult.getOrThrow()
            val jsParseResult = kotlin.runCatching {
                val temporalAccessor = DateTimeFormatter.ISO_INSTANT.parse(string.replaceAfterLast(".", "").replace('.', 'Z'))
                val instant = Instant.from(temporalAccessor)
                LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
            }
            if (jsParseResult.isSuccess) return jsParseResult.getOrThrow()
            throw SerializationException("Can't parse date")
        }
    }

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LocalDateTime", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): LocalDateTime {
        val string = decoder.decodeString()
        return parse(string)
    }

    override fun serialize(encoder: Encoder, value: LocalDateTime) {
        encoder.encodeString(value.atZone(ZoneId.systemDefault()).format(defaultFormatter))
    }
}
