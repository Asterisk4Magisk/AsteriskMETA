// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.proxy

import app.AutoSwitchSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.Serializable

internal data class AutoSwitchRegion(val code: String, val aliases: List<String>)

internal val AutoSwitchRegions = listOf(
    AutoSwitchRegion("HK", listOf("🇭🇰", "香港", "Hong Kong", "HongKong")),
    AutoSwitchRegion("TW", listOf("🇹🇼", "台湾", "台灣", "Taiwan")),
    AutoSwitchRegion("JP", listOf("🇯🇵", "日本", "东京", "大阪", "Japan", "Tokyo", "Osaka")),
    AutoSwitchRegion("SG", listOf("🇸🇬", "新加坡", "狮城", "Singapore")),
    AutoSwitchRegion("US", listOf("🇺🇸", "美国", "美國", "United States", "USA", "Los Angeles")),
    AutoSwitchRegion("KR", listOf("🇰🇷", "韩国", "韓國", "Korea", "Seoul")),
    AutoSwitchRegion(
        "GB",
        listOf("🇬🇧", "英国", "英國", "United Kingdom", "UK", "Britain", "London")
    ),
    AutoSwitchRegion("DE", listOf("🇩🇪", "德国", "德國", "Germany", "Frankfurt")),
    AutoSwitchRegion("FR", listOf("🇫🇷", "法国", "法國", "France", "Paris")),
    AutoSwitchRegion("CA", listOf("🇨🇦", "加拿大", "Canada")),
    AutoSwitchRegion("AU", listOf("🇦🇺", "澳大利亚", "澳洲", "Australia", "Sydney")),
    AutoSwitchRegion("NL", listOf("🇳🇱", "荷兰", "荷蘭", "Netherlands", "Amsterdam")),
)

internal fun AutoSwitchSettings.prefersNode(name: String): Boolean =
    preferredKeywords.any { name.contains(it, ignoreCase = true) } ||
            AutoSwitchRegions.any { region ->
                region.code in preferredRegions && (
                        region.aliases.any { name.contains(it, ignoreCase = true) } ||
                                Regex(
                                    "(?<![A-Za-z])${region.code}(?![A-Za-z])",
                                    RegexOption.IGNORE_CASE
                                ).containsMatchIn(name)
                        )
            }

internal data class AutoSwitchGroup(
    val name: String,
    val selected: String,
    val candidates: List<String>,
)

internal interface AutoSwitchGateway {
    fun networkAvailable(): Boolean
    suspend fun groups(): List<AutoSwitchGroup>
    suspend fun measure(group: AutoSwitchGroup, node: String): Int?
    suspend fun selectIfUnchanged(group: AutoSwitchGroup, node: String): Boolean
}

@Serializable
internal enum class AutoSwitchCheckKind {
    Healthy, Failed, Offline, SkippedSelection, NoGroups, NoReplacement, SelectionChanged, ControlError,
    CandidateSummary, Recovered, ReplacementUnavailable,
    InteractiveOperation,
}

@Serializable
internal enum class AutoSwitchProbePurpose { Current, Candidate, Verification }

@Serializable
internal data class AutoSwitchCheckResult(
    val kind: AutoSwitchCheckKind,
    val groupName: String = "",
    val nodeName: String = "",
    val purpose: AutoSwitchProbePurpose = AutoSwitchProbePurpose.Current,
    val delayMillis: Int? = null,
    val failureCount: Int? = null,
    val failureThreshold: Int? = null,
    val errorCategory: String = "",
    val totalNodeCount: Int? = null,
    val availableNodeCount: Int? = null,
    val preferredOnly: Boolean = false,
)

internal enum class AutoSwitchCheckStage {
    Preparing, CurrentNode, PreferredNodes, AllNodes, RecheckingReplacement, Switching,
}

internal data class AutoSwitchCheckProgress(
    val stage: AutoSwitchCheckStage = AutoSwitchCheckStage.Preparing,
    val groupName: String = "",
    val nodeName: String = "",
    val completedNodes: Int = 0,
    val totalNodes: Int = 0,
    val preferredRegions: List<String> = emptyList(),
    val usesCustomKeywords: Boolean = false,
)

/** A fresh instance belongs to one running profile/settings session. */
internal class AutoSwitchController(
    private val onCheckResult: (AutoSwitchCheckResult) -> Unit = {},
    private val onProgress: (AutoSwitchCheckProgress) -> Unit = {},
) {
    private data class Failures(val selected: String, val count: Int)

    private val failures = mutableMapOf<String, Failures>()

    fun reset() {
        failures.clear()
    }

    suspend fun check(settings: AutoSwitchSettings, gateway: AutoSwitchGateway) {
        if (!settings.enabled) {
            failures.clear()
            return
        }
        if (!gateway.networkAvailable()) {
            failures.clear()
            onCheckResult(AutoSwitchCheckResult(AutoSwitchCheckKind.Offline))
            return
        }
        onProgress(AutoSwitchCheckProgress())
        val groups = gateway.groups().filter { it.name in settings.groups }
        if (groups.isEmpty()) onCheckResult(AutoSwitchCheckResult(AutoSwitchCheckKind.NoGroups))
        failures.keys.retainAll(groups.map { it.name }.toSet())
        for (group in groups) {
            // Never replace DIRECT/REJECT or a nested automatic/manual group with a leaf node.
            if (group.selected !in group.candidates) {
                failures.remove(group.name)
                onCheckResult(
                    AutoSwitchCheckResult(
                        AutoSwitchCheckKind.SkippedSelection,
                        group.name,
                        group.selected
                    )
                )
                continue
            }
            onProgress(
                AutoSwitchCheckProgress(
                    AutoSwitchCheckStage.CurrentNode,
                    group.name,
                    group.selected
                )
            )
            val currentDelay = gateway.measure(group, group.selected)
            if (currentDelay != null) {
                failures.remove(group.name)
                onCheckResult(
                    AutoSwitchCheckResult(
                        AutoSwitchCheckKind.Healthy,
                        group.name,
                        group.selected,
                        delayMillis = currentDelay
                    )
                )
                continue
            }
            if (!gateway.networkAvailable()) {
                failures.clear()
                onCheckResult(
                    AutoSwitchCheckResult(
                        AutoSwitchCheckKind.Offline,
                        group.name,
                        group.selected
                    )
                )
                return
            }
            val previous = failures[group.name]?.takeIf { it.selected == group.selected }
            val count = ((previous?.count ?: 0) + 1).coerceAtMost(settings.failureThreshold)
            failures[group.name] = Failures(group.selected, count)
            onCheckResult(
                AutoSwitchCheckResult(
                    AutoSwitchCheckKind.Failed, group.name, group.selected,
                    failureCount = count, failureThreshold = settings.failureThreshold,
                ),
            )
            if (count < settings.failureThreshold) continue
            val candidates = group.candidates.distinct().filter { it != group.selected }
            val preferred = candidates.filter(settings::prefersNode)
            val other = candidates.filterNot(settings::prefersNode)
            val tested = linkedMapOf<String, Int?>()
            var replacement: String? = null
            var preferredOnly = false
            // Test the preferred tier first; only probe other regions when it has no healthy node.
            for ((tierIndex, tier) in listOf(preferred, other).withIndex()) {
                if (tier.isEmpty()) continue
                val progress = AutoSwitchCheckProgress(
                    stage = if (tierIndex == 0) AutoSwitchCheckStage.PreferredNodes else AutoSwitchCheckStage.AllNodes,
                    groupName = group.name,
                    completedNodes = tested.size,
                    totalNodes = if (tierIndex == 0) preferred.size else candidates.size,
                    preferredRegions = settings.preferredRegions,
                    usesCustomKeywords = settings.preferredKeywords.isNotEmpty(),
                )
                val progressLock = Any()
                var completed = tested.size
                onProgress(progress)
                val measurements = tier.chunked(4).flatMap { batch ->
                    coroutineScope {
                        batch.map { node ->
                            async(Dispatchers.IO) {
                                val delay = gateway.measure(group, node)
                                currentCoroutineContext().ensureActive()
                                synchronized(progressLock) {
                                    completed++
                                    onProgress(progress.copy(completedNodes = completed))
                                }
                                node to delay
                            }
                        }.awaitAll()
                    }
                }
                if (!gateway.networkAvailable()) {
                    failures.clear()
                    onCheckResult(AutoSwitchCheckResult(AutoSwitchCheckKind.Offline, group.name))
                    return
                }
                tested.putAll(measurements)
                preferredOnly = tierIndex == 0
                val healthy = measurements.mapNotNull { (node, delay) -> delay?.let { node to it } }
                replacement = healthy.minByOrNull { it.second }?.first
                if (replacement != null) break
            }
            if (!gateway.networkAvailable()) {
                failures.clear()
                onCheckResult(AutoSwitchCheckResult(AutoSwitchCheckKind.Offline, group.name))
                return
            }
            val fastest = tested.mapNotNull { (node, delay) -> delay?.let { node to it } }
                .minByOrNull { it.second }
            onCheckResult(
                AutoSwitchCheckResult(
                    kind = AutoSwitchCheckKind.CandidateSummary,
                    groupName = group.name,
                    nodeName = fastest?.first.orEmpty(),
                    delayMillis = fastest?.second,
                    totalNodeCount = tested.size,
                    availableNodeCount = tested.values.count { it != null },
                    preferredOnly = preferredOnly,
                ),
            )
            if (replacement != null) {
                onProgress(
                    AutoSwitchCheckProgress(
                        AutoSwitchCheckStage.RecheckingReplacement,
                        group.name,
                        replacement
                    )
                )
                if (gateway.measure(group, replacement) != null && gateway.networkAvailable()) {
                    onProgress(
                        AutoSwitchCheckProgress(
                            AutoSwitchCheckStage.Switching,
                            group.name,
                            replacement
                        )
                    )
                    if (gateway.selectIfUnchanged(group, replacement)) {
                        failures.remove(group.name)
                    } else {
                        onCheckResult(
                            AutoSwitchCheckResult(
                                AutoSwitchCheckKind.SelectionChanged,
                                group.name,
                                group.selected
                            )
                        )
                    }
                } else {
                    onCheckResult(
                        AutoSwitchCheckResult(
                            if (gateway.networkAvailable()) AutoSwitchCheckKind.ReplacementUnavailable else AutoSwitchCheckKind.Offline,
                            group.name,
                        )
                    )
                }
            } else {
                onCheckResult(
                    AutoSwitchCheckResult(
                        AutoSwitchCheckKind.NoReplacement,
                        group.name,
                        group.selected
                    )
                )
            }
        }
    }
}
