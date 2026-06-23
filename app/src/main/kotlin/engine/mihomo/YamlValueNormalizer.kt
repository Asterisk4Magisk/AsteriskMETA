// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo

import java.util.IdentityHashMap

internal const val MihomoRecursiveYamlAnchorErrorMessage =
    "YAML anchors that reference themselves are not supported"

internal fun normalizeYamlValue(value: Any?): Any? {
    return normalizeYamlValue(value, IdentityHashMap())
}

private fun normalizeYamlValue(
    value: Any?,
    ancestors: IdentityHashMap<Any, Boolean>,
): Any? {
    return when (value) {
        is Map<*, *> -> {
            if (ancestors.containsKey(value)) {
                error(MihomoRecursiveYamlAnchorErrorMessage)
            }
            ancestors[value] = true
            try {
                linkedMapOf<String, Any?>().apply {
                    value.forEach { (key, childValue) ->
                        val name = key as? String ?: return@forEach
                        put(name, normalizeYamlValue(childValue, ancestors))
                    }
                }
            } finally {
                ancestors.remove(value)
            }
        }
        is List<*> -> {
            if (ancestors.containsKey(value)) {
                error(MihomoRecursiveYamlAnchorErrorMessage)
            }
            ancestors[value] = true
            try {
                ArrayList<Any?>(value.size).apply {
                    value.forEach { childValue ->
                        add(normalizeYamlValue(childValue, ancestors))
                    }
                }
            } finally {
                ancestors.remove(value)
            }
        }
        else -> value
    }
}
