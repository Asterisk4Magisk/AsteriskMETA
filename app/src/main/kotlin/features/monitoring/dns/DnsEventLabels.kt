// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.monitoring.dns

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.R

@Composable
internal fun dnsStatusLabel(record: DnsRecord): String = when (record.event.status) {
    "answered" -> stringResource(R.string.monitor_dns_answered)
    "timeout" -> stringResource(R.string.monitor_dns_timeout)
    "failed" -> stringResource(R.string.monitor_dns_failed)
    else -> "—"
}
