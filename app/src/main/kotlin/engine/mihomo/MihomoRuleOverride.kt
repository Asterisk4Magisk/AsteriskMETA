// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo

import app.MihomoRuleOverrideState
import app.MihomoSourceRuleEditAction
import app.runtimeProjection

internal const val MihomoMatchRuleType = "MATCH"

/** What applying a rule override did, for the editor's preview and hints. */
internal data class MihomoRuleOverrideDiagnostics(
    val sourceRuleCount: Int = 0,
    val outputRuleCount: Int = 0,
    /** Enabled source edit ID to the number of source rules it changed. */
    val sourceEditMatches: Map<Int, Int> = emptyMap(),
    val matchRuleCount: Int = 0,
    /** Rules that follow the first `MATCH` and can therefore never be reached. */
    val unreachableRuleCount: Int = 0,
) {
    val unmatchedSourceEditIds: List<Int>
        get() = sourceEditMatches.filterValues { count -> count == 0 }.keys.toList()
}

internal class MihomoRuleOverrideResult(
    val config: Map<String, Any?>,
    val diagnostics: MihomoRuleOverrideDiagnostics,
)

/** The source configuration cannot take a rule override; [path] names the offending field. */
internal class MihomoRuleOverrideException(
    val path: String,
    message: String,
) : IllegalArgumentException(message)

/**
 * Applies [override] to the `rules` of a parsed source configuration and returns a new map;
 * neither the receiver nor its lists are modified. Always start from the source configuration:
 * applying an override to its own output would add its rules a second time.
 *
 * Source edits match the trimmed text of a rule and are resolved against the original list,
 * so one edit never feeds another. The fallback target and the rules before the fallback then
 * address the first `MATCH` that is left, and the prepended rules go in front of everything.
 */
internal fun Map<String, Any?>.applyMihomoRuleOverride(override: MihomoRuleOverrideState): MihomoRuleOverrideResult {
    val projection = override.runtimeProjection()
    if (projection.isEmpty) {
        // A profile without `rules` must not gain the key from an override that does nothing.
        val rules = (this["rules"] as? List<*>).orEmpty()
        return MihomoRuleOverrideResult(this, rules.diagnostics(sourceRuleCount = rules.size))
    }

    val source = when (val value = this["rules"]) {
        null -> emptyList()
        is List<*> -> value.mapIndexed { index, rule ->
            rule as? String ?: throw MihomoRuleOverrideException(
                path = "rules[$index]",
                message = "Rule override requires rules[$index] to be a string",
            )
        }
        else -> throw MihomoRuleOverrideException("rules", "Rule override requires rules to be a list")
    }

    val edits = override.sourceEdits
        .filter { edit ->
            edit.enabled && edit.sourceRule.isNotBlank() &&
                (edit.action == MihomoSourceRuleEditAction.Remove || edit.replacementRule.isNotBlank())
        }
        .associateBy { edit -> edit.sourceRule.trim() }
    val matches = edits.values.associateTo(linkedMapOf()) { edit -> edit.id to 0 }
    val rules = ArrayList<String>(source.size + projection.prependRules.size + projection.beforeMatchRules.size + 1)
    source.forEach { rule ->
        val edit = edits[rule.trim()]
        if (edit == null) {
            rules += rule
            return@forEach
        }
        matches[edit.id] = matches.getValue(edit.id) + 1
        if (edit.action == MihomoSourceRuleEditAction.Replace) rules += edit.replacementRule.trim()
    }

    projection.matchTarget?.let { target ->
        val matchRule = "$MihomoMatchRuleType,$target"
        val matchIndex = rules.indexOfFirst(String::isMihomoMatchRule)
        if (matchIndex >= 0) rules[matchIndex] = matchRule else rules += matchRule
    }
    val matchIndex = rules.indexOfFirst(String::isMihomoMatchRule)
    rules.addAll(if (matchIndex >= 0) matchIndex else rules.size, projection.beforeMatchRules)
    rules.addAll(0, projection.prependRules)

    val config = LinkedHashMap(this)
    config["rules"] = rules
    return MihomoRuleOverrideResult(
        config = config,
        diagnostics = rules.diagnostics(sourceRuleCount = source.size).copy(sourceEditMatches = matches),
    )
}

private fun List<*>.diagnostics(sourceRuleCount: Int): MihomoRuleOverrideDiagnostics {
    val firstMatch = indexOfFirst { rule -> (rule as? String)?.isMihomoMatchRule() == true }
    return MihomoRuleOverrideDiagnostics(
        sourceRuleCount = sourceRuleCount,
        outputRuleCount = size,
        matchRuleCount = count { rule -> (rule as? String)?.isMihomoMatchRule() == true },
        unreachableRuleCount = if (firstMatch < 0) 0 else lastIndex - firstMatch,
    )
}

/** The rule type: the first token, which Mihomo reads without regard to case or padding. */
internal fun String.mihomoRuleType(): String = substringBefore(',').trim().uppercase()

internal fun String.isMihomoMatchRule(): Boolean = mihomoRuleType() == MihomoMatchRuleType

/** The source rules a rule editor can list; entries that are not text are left out. */
internal fun Map<String, Any?>.mihomoSourceRules(): List<String> =
    (this["rules"] as? List<*>).orEmpty().filterIsInstance<String>()
