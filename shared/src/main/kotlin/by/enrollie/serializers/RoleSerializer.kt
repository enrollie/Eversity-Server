/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 8/15/22, 11:36 PM
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
        return try {
            Roles.getRoleByID(decoder.decodeString()) ?: throw SerializationException("Invalid role ID")
        } catch (e: Exception) {
            throw SerializationException(e.message ?: "Invalid role ID")
        }
    }

    override fun serialize(encoder: Encoder, value: Roles.Role) {
        encoder.encodeString(value.getID())
    }
}
