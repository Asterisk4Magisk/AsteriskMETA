// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.mihomo.provider

import engine.mihomo.MihomoProviderType

internal enum class MihomoProviderManagementTab(
    val providerType: MihomoProviderType,
) {
    Proxy(MihomoProviderType.Proxy),
    Rule(MihomoProviderType.Rule),
}

internal fun defaultMihomoProviderManagementTab(): MihomoProviderManagementTab {
    return MihomoProviderManagementTab.Proxy
}

internal fun mihomoProviderManagementTabForPage(page: Int): MihomoProviderManagementTab {
    return MihomoProviderManagementTab.entries.getOrNull(page) ?: defaultMihomoProviderManagementTab()
}

internal data class ProviderRefreshSummary(
    val successCount: Int,
    val failureCount: Int,
)

internal data class ProviderDeclarationsState(
    val loading: Boolean = false,
    val providers: List<engine.mihomo.MihomoProviderDeclaration> = emptyList(),
    val error: String = "",
)

internal data class MihomoProviderPreviewState(
    val requestId: Int = 0,
    val providerName: String = "",
    val rawContent: engine.mihomo.MihomoProviderRawContent? = null,
)

internal fun beginMihomoProviderPreview(
    state: MihomoProviderPreviewState,
    providerName: String,
): MihomoProviderPreviewState = state.copy(
    requestId = state.requestId + 1,
    providerName = providerName,
    rawContent = null,
)

internal fun completeMihomoProviderPreview(
    state: MihomoProviderPreviewState,
    requestId: Int,
    rawContent: engine.mihomo.MihomoProviderRawContent,
): MihomoProviderPreviewState = if (state.requestId == requestId) {
    state.copy(rawContent = rawContent)
} else {
    state
}

internal fun failMihomoProviderPreview(
    state: MihomoProviderPreviewState,
    requestId: Int,
): MihomoProviderPreviewState = if (state.requestId == requestId) {
    state.copy(providerName = "", rawContent = null)
} else {
    state
}

internal fun dismissMihomoProviderPreview(
    state: MihomoProviderPreviewState,
): MihomoProviderPreviewState = MihomoProviderPreviewState(requestId = state.requestId + 1)

internal fun reduceProviderRefreshResults(results: List<Result<Unit>>): ProviderRefreshSummary {
    return ProviderRefreshSummary(
        successCount = results.count(Result<Unit>::isSuccess),
        failureCount = results.count(Result<Unit>::isFailure),
    )
}

internal fun isProviderTypeBusy(
    type: MihomoProviderType,
    refreshingNamesByType: Map<MihomoProviderType, Set<String>>,
    refreshingAllTypes: Set<MihomoProviderType>,
    ruleRuntimeLoading: Boolean,
): Boolean {
    return type in refreshingAllTypes ||
        refreshingNamesByType[type].orEmpty().isNotEmpty() ||
        (type == MihomoProviderType.Rule && ruleRuntimeLoading)
}

internal fun currentRuleProviderSummaries(
    declaredNames: Set<String>,
    summaries: Map<String, engine.mihomo.runtime.MihomoRuleProviderRuntimeSummary>,
): Map<String, engine.mihomo.runtime.MihomoRuleProviderRuntimeSummary> {
    return summaries.filterKeys(declaredNames::contains)
}
