/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/17/22, 10:44 PM
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

class RoleInformationSerializer : KSerializer<RoleInformationHolder> {
    @OptIn(ExperimentalSerializationApi::class)
    override val descriptor: SerialDescriptor = mapSerialDescriptor<String, String>()

    @OptIn(UnsafeAPI::class)
    override fun serialize(encoder: Encoder, value: RoleInformationHolder) {
        encoder.encodeSerializableValue(
            MapSerializer(String.serializer(), AnySerializer),
            value.getAsMap().map { it.key.id to it.value }.toMap()
        )
    }

    override fun deserialize(decoder: Decoder): RoleInformationHolder {
        val map = decoder.decodeSerializableValue(
            MapSerializer(String.serializer(), AnySerializer)
        )
        return RoleInformationHolder(*map.map {
            val field = Roles.getRoleByID(it.key)!!.fieldByID(it.key)!!
            field to it.value
        }.toTypedArray())
    }

}
