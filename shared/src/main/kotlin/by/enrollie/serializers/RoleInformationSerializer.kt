/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 8/24/22, 7:50 PM
 */

package by.enrollie.serializers

import by.enrollie.annotations.UnsafeAPI
import by.enrollie.data_classes.RoleInformationHolder
import by.enrollie.data_classes.Roles
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.mapSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.serializer

class RoleInformationSerializer : KSerializer<RoleInformationHolder> {
    @OptIn(ExperimentalSerializationApi::class)
    override val descriptor: SerialDescriptor = mapSerialDescriptor<String, JsonElement>()

    @OptIn(UnsafeAPI::class)
    override fun serialize(encoder: Encoder, value: RoleInformationHolder) {
        encoder.encodeSerializableValue(
            MapSerializer(String.serializer(), JsonElement.serializer()),
            value.getAsMap().map { it.key.id to Json.encodeToJsonElement(serializer(it.key.type), it.value) }.toMap()
        )
    }

    override fun deserialize(decoder: Decoder): RoleInformationHolder {
        val map = decoder.decodeSerializableValue(
            MapSerializer(String.serializer(), JsonElement.serializer())
        )
        return try {
            RoleInformationHolder(*map.mapNotNull {
                val field = Roles.getRoleByID(it.key)?.fieldByID(it.key)
                if (field == null) {
                    val yellowText = "\u001B[33m"
                    val normalize = "\u001B[0m"
                    println("${yellowText}RoleInformationSerializer: Field with id ${it.key} not found${normalize}")
                    return@mapNotNull null
                }
                field to Json.decodeFromJsonElement(serializer(field.type), it.value)
            }.toTypedArray())
        } catch (e: Exception) {
            throw SerializationException(e.message)
        }
    }
}
