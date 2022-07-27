/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/28/22, 12:01 AM
 */

package by.enrollie.serializers

import by.enrollie.data_classes.Roles
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

class RoleSerializer : KSerializer<Roles.Role> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Role", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Roles.Role {
        return Roles.getRoleByID(decoder.decodeString()) ?: throw SerializationException("Invalid role ID")
    }

    override fun serialize(encoder: Encoder, value: Roles.Role) {
        encoder.encodeString(value.getID())
    }
}
