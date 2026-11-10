// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package app

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * The GUI rule override of one profile. It is applied to the parsed `rules` of the source
 * configuration before the override script runs, so a script sees and may rewrite the result.
 * The lists are ordered: their order is the order of the generated rules.
 */
@Immutable
@Serializable
data class MihomoRuleOverrideState(
    val enabled: Boolean = false,
    val prependRules: List<MihomoOverrideRuleState> = emptyList(),
    val beforeMatchRules: List<MihomoOverrideRuleState> = emptyList(),
    val sourceEdits: List<MihomoSourceRuleEditState> = emptyList(),
    /** The policy of the first source `MATCH`; `null` keeps the source configuration's. */
    val matchTarget: String? = null,
)

/** One user rule; [rawRule] is the whole scalar of a `rules` entry, without the list dash. */
@Immutable
@Serializable
data class MihomoOverrideRuleState(
    val id: Int,
    val enabled: Boolean = true,
    val rawRule: String,
    val remark: String = "",
)

@Serializable
enum class MihomoSourceRuleEditAction { Remove, Replace }

/** An edit of every source rule whose trimmed text equals [sourceRule]. */
@Immutable
@Serializable
data class MihomoSourceRuleEditState(
    val id: Int,
    val enabled: Boolean = true,
    val sourceRule: String,
    val action: MihomoSourceRuleEditAction,
    val replacementRule: String = "",
    val remark: String = "",
)

/**
 * What a rule override contributes to the compiled configuration. IDs, remarks and disabled
 * entries are left out, so two overrides with equal projections compile to the same rules.
 */
data class MihomoRuleOverrideProjection(
    val prependRules: List<String> = emptyList(),
    val beforeMatchRules: List<String> = emptyList(),
    val removedSourceRules: List<String> = emptyList(),
    val replacedSourceRules: List<Pair<String, String>> = emptyList(),
    val matchTarget: String? = null,
) {
    val isEmpty: Boolean
        get() = prependRules.isEmpty() && beforeMatchRules.isEmpty() && removedSourceRules.isEmpty() &&
            replacedSourceRules.isEmpty() && matchTarget == null

    companion object {
        val Empty = MihomoRuleOverrideProjection()
    }
}

fun MihomoRuleOverrideState.runtimeProjection(): MihomoRuleOverrideProjection {
    if (!enabled) return MihomoRuleOverrideProjection.Empty
    val edits = sourceEdits.filter { edit -> edit.enabled && edit.sourceRule.isNotBlank() }
    return MihomoRuleOverrideProjection(
        prependRules = prependRules.effectiveRules(),
        beforeMatchRules = beforeMatchRules.effectiveRules(),
        removedSourceRules = edits
            .filter { edit -> edit.action == MihomoSourceRuleEditAction.Remove }
            .map { edit -> edit.sourceRule.trim() },
        replacedSourceRules = edits
            .filter { edit -> edit.action == MihomoSourceRuleEditAction.Replace && edit.replacementRule.isNotBlank() }
            .map { edit -> edit.sourceRule.trim() to edit.replacementRule.trim() },
        matchTarget = matchTarget?.trim()?.takeIf(String::isNotEmpty),
    )
}

/** The projection the core would see for this profile; empty in raw configuration mode. */
fun MihomoProfileState.ruleOverrideRuntimeProjection(): MihomoRuleOverrideProjection =
    if (disableOverrides) MihomoRuleOverrideProjection.Empty else ruleOverride.runtimeProjection()

/** The number of entries that currently change the compiled rules. */
val MihomoRuleOverrideProjection.effectiveEntryCount: Int
    get() = prependRules.size + beforeMatchRules.size + removedSourceRules.size + replacedSourceRules.size +
        if (matchTarget == null) 0 else 1

private fun List<MihomoOverrideRuleState>.effectiveRules(): List<String> =
    filter { rule -> rule.enabled && rule.rawRule.isNotBlank() }.map { rule -> rule.rawRule.trim() }

/** Empty strings and surrounding blanks are not stored. */
fun MihomoRuleOverrideState.normalized(): MihomoRuleOverrideState = copy(
    prependRules = prependRules.map(MihomoOverrideRuleState::normalized),
    beforeMatchRules = beforeMatchRules.map(MihomoOverrideRuleState::normalized),
    sourceEdits = sourceEdits.map { edit ->
        edit.copy(
            sourceRule = edit.sourceRule.trim(),
            replacementRule = edit.replacementRule.trim(),
            remark = edit.remark.trim(),
        )
    },
    matchTarget = matchTarget?.trim()?.takeIf(String::isNotEmpty),
)

private fun MihomoOverrideRuleState.normalized(): MihomoOverrideRuleState =
    copy(rawRule = rawRule.trim(), remark = remark.trim())

/** The next entry ID of this profile's override: IDs are unique across all three lists. */
fun MihomoRuleOverrideState.nextEntryId(): Int {
    val used = entryIds()
    val next = (used.maxOrNull() ?: 0).toLong() + 1L
    if (next < Int.MAX_VALUE) return next.toInt()
    // The highest ID is exhausted; reuse the first gap instead of overflowing.
    val taken = used.toHashSet()
    return generateSequence(1) { candidate -> candidate + 1 }.first { candidate -> candidate !in taken }
}

fun MihomoRuleOverrideState.entryIds(): List<Int> =
    prependRules.map(MihomoOverrideRuleState::id) +
        beforeMatchRules.map(MihomoOverrideRuleState::id) +
        sourceEdits.map(MihomoSourceRuleEditState::id)

/**
 * The structural problem that makes this override unusable, or `null`. Disabled entries may
 * hold unfinished text, so only identity and the shape of enabled entries are checked.
 */
fun MihomoRuleOverrideState.structuralError(): String? {
    val ids = entryIds()
    if (ids.any { id -> id <= 0 || id == Int.MAX_VALUE }) return "Rule override entry ID is out of range"
    if (ids.toHashSet().size != ids.size) return "Duplicate rule override entry ID"
    val sources = sourceEdits.map { edit -> edit.sourceRule.trim() }
    if (sources.any(String::isEmpty)) return "Source rule edit has no source rule"
    if (sources.toHashSet().size != sources.size) return "Duplicate source rule edit"
    if ((prependRules + beforeMatchRules).any { rule -> rule.enabled && rule.rawRule.isBlank() }) {
        return "Enabled override rule is blank"
    }
    val blankReplacement = sourceEdits.any { edit ->
        edit.enabled && edit.action == MihomoSourceRuleEditAction.Replace && edit.replacementRule.isBlank()
    }
    return if (blankReplacement) "Enabled source rule replacement is blank" else null
}
