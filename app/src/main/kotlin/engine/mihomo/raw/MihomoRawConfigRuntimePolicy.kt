// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo.raw

internal fun MihomoRawConfigSnapshot?.runtimeIpv6Enabled(applicationValue: Boolean): Boolean =
    if (this == null) applicationValue else ipv6.value == true
