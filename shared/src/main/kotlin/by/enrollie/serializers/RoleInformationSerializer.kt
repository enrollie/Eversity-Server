/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/28/22, 12:00 AM
 */

package by.enrollie.serializers

import by.enrollie.annotations.UnsafeAPI
import by.enrollie.data_classes.RoleInformationHolder
import by.enrollie.data_classes.Roles
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
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
        return RoleInformationHolder(*map.map {
            val field = Roles.getRoleByID(it.key)!!.fieldByID(it.key)!!
            field to Json.decodeFromJsonElement(serializer(field.type), it.value)
        }.toTypedArray())
    }
}
