// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.mihomo

internal enum class MihomoProxyLoadingPresentation {
    None,
    Initial,
    ContentRefresh,
}

internal fun resolveMihomoProxyLoadingPresentation(
    pageRefreshInProgress: Boolean,
    hasProxySnapshot: Boolean,
): MihomoProxyLoadingPresentation = when {
    !pageRefreshInProgress -> MihomoProxyLoadingPresentation.None
    hasProxySnapshot -> MihomoProxyLoadingPresentation.ContentRefresh
    else -> MihomoProxyLoadingPresentation.Initial
}
