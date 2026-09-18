// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.root.publication

import android.content.Context
import engine.root.daemon.AsteriskdClient
import engine.root.daemon.config.AsteriskdOwner
import engine.root.daemon.control.AsteriskdPhase
import engine.root.runtime.boundSnapshot
import features.subscription.runtime.mihomoCoreFetchLock
import kotlinx.coroutines.sync.withLock
import system.AndroidRootShellGateway
import system.ShellExecOptions

/** Only called for a running ROOT API backend, never by bridge/VPN preparation. */
internal suspend fun Context.publishRootProviderUpdates() = mihomoCoreFetchLock.withLock {
    val layout = rootRuntimeLayout()
    val shell = AndroidRootShellGateway()
    val snapshot = AsteriskdClient(shell).status(layout.asteriskdPath).boundSnapshot()
    check(snapshot?.owner == AsteriskdOwner.AsteriskMeta && snapshot.phase == AsteriskdPhase.Running) {
        "ROOT provider publication requires a running AsteriskMETA core"
    }
    val result = shell.exec(RootProviderPublicationCommand.buildRefresh(layout), ShellExecOptions(logFailure = false))
    check(result.errno == 0) { "ROOT provider publication failed (${result.errno}): ${result.stderr}" }
}
