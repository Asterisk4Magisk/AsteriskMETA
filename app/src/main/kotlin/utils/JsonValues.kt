// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package utils

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

internal fun JsonElement?.jsonObjectOrNull(): JsonObject? = this as? JsonObject

internal fun JsonElement?.jsonArrayOrNull(): JsonArray? = this as? JsonArray

internal fun JsonElement?.jsonPrimitiveOrNull(): JsonPrimitive? = this as? JsonPrimitive

internal fun JsonObject.stringValue(name: String): String? =
    this[name].jsonPrimitiveOrNull()?.contentOrNull?.takeIf(String::isNotBlank)

internal fun JsonObject.objectValue(name: String): JsonObject? = this[name].jsonObjectOrNull()

internal fun JsonObject.arrayValue(name: String): JsonArray? = this[name].jsonArrayOrNull()

internal fun JsonElement?.longValue(): Long? {
    val primitive = jsonPrimitiveOrNull() ?: return null
    return primitive.longOrNull ?: primitive.contentOrNull?.toLongOrNull()
}

internal fun JsonObject.longValue(name: String): Long? = this[name].longValue()

internal fun JsonElement?.intValue(): Int? {
    val primitive = jsonPrimitiveOrNull() ?: return null
    return primitive.intOrNull ?: primitive.contentOrNull?.toIntOrNull()
}

internal fun JsonObject.intValue(name: String): Int? = this[name].intValue()
