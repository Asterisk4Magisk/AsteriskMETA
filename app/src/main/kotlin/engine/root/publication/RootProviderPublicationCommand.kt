// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.root.publication

import engine.mihomo.CmfaProvidersDirectory
import engine.mihomo.MihomoRootProvidersDirectory
import utils.shellQuote

/** ROOT-only publication commands. Rebuild requires a stopped core; refresh preserves live files. */
internal object RootProviderPublicationCommand {
    fun buildStart(layout: RootRuntimeLayout, command: String, manageProviders: Boolean = true): String = buildString {
        require(command == "start" || command == "monitor")
        appendLine("(")
        appendLine("set -eu")
        with(RootPublicationCommand) { appendStatusMustBePublishable(layout) }
        if (manageProviders) appendRebuild(this, layout)
        appendLine("nohup ${layout.asteriskdPath.shellQuote()} $command --config ${layout.asteriskdConfigPath.shellQuote()} </dev/null >/dev/null 2>>${layout.asteriskdLogPath.shellQuote()} &")
        appendLine(")")
    }

    fun appendRebuild(builder: StringBuilder, layout: RootRuntimeLayout) = with(builder) {
        appendLine("(")
        appendLine("set -eu")
        appendLine("[ -d ${layout.dataDir.shellQuote()} ] && [ ! -L ${layout.dataDir.shellQuote()} ] || exit 70")
        appendLine("cd ${layout.dataDir.shellQuote()}")
        appendLine("[ ! -L '$CmfaProvidersDirectory' ] && [ ! -L '$MihomoRootProvidersDirectory' ] || { printf '%s\\n' 'root_providers unsafe_directory' >&2; exit 70; }")
        appendLine("if [ -e '$CmfaProvidersDirectory' ]; then")
        appendLine("  [ -d '$CmfaProvidersDirectory' ] || exit 70")
        appendLine("  provider_links=\"\$(find '$CmfaProvidersDirectory' -type l -print)\"")
        appendLine("  [ -z \"\$provider_links\" ] || { printf '%s\\n' 'root_providers source_symlink' >&2; exit 70; }")
        appendLine("fi")
        // Only this fixed child of the verified working directory is disposable.
        appendLine("rm -rf -- './$MihomoRootProvidersDirectory' || { printf '%s\\n' 'root_providers delete_failed' >&2; exit 70; }")
        appendLine("mkdir './$MihomoRootProvidersDirectory'")
        appendLine("if [ -d '$CmfaProvidersDirectory' ]; then")
        appendLine("  cp -R -- './$CmfaProvidersDirectory/.' './$MihomoRootProvidersDirectory/' || { printf '%s\\n' 'root_providers copy_failed' >&2; exit 70; }")
        appendLine("fi")
        // Ancestor app-private permissions still protect this cache. Allow ordinary UI previews.
        appendLine("chmod -R u+rwX,go+rX './$MihomoRootProvidersDirectory'")
        appendLine(")")
    }

    /** Publish before an API reload without deleting a directory used by the running core. */
    fun buildRefresh(layout: RootRuntimeLayout): String = buildString {
        appendLine("(")
        appendLine("set -eu")
        appendLine("[ -d ${layout.dataDir.shellQuote()} ] && [ ! -L ${layout.dataDir.shellQuote()} ] || exit 70")
        appendLine("cd ${layout.dataDir.shellQuote()}")
        appendLine("[ ! -L '$CmfaProvidersDirectory' ] && [ ! -L '$MihomoRootProvidersDirectory' ] || exit 70")
        appendLine("[ -e '$CmfaProvidersDirectory' ] || exit 0")
        appendLine("[ -d '$CmfaProvidersDirectory' ] || exit 70")
        appendLine("mkdir -p '$MihomoRootProvidersDirectory'")
        appendLine("provider_pending=''")
        appendLine("trap '[ -z \"\$provider_pending\" ] || rm -f -- \"\$provider_pending\"' EXIT")
        appendLine("publish_provider_directory() {")
        appendLine("  for provider_source in \"\$1\"/* \"\$1\"/.[!.]* \"\$1\"/..?*; do")
        appendLine("    [ ! -L \"\$provider_source\" ] || exit 70")
        appendLine("    [ -e \"\$provider_source\" ] || continue")
        appendLine("    provider_target=\"$MihomoRootProvidersDirectory/\${provider_source#$CmfaProvidersDirectory/}\"")
        appendLine("    [ ! -L \"\$provider_target\" ] || exit 70")
        appendLine("    if [ -d \"\$provider_source\" ]; then")
        appendLine("      mkdir -p \"\$provider_target\"")
        appendLine("      chmod 755 \"\$provider_target\"")
        appendLine("      publish_provider_directory \"\$provider_source\"")
        appendLine("    else")
        appendLine("      [ -f \"\$provider_source\" ] || exit 70")
        appendLine("      [ ! -e \"\$provider_target\" ] || [ -f \"\$provider_target\" ] || exit 70")
        appendLine("      provider_candidate=\"\$provider_target.asterisk-publish-\$\$\"")
        appendLine("      [ ! -e \"\$provider_candidate\" ] && [ ! -L \"\$provider_candidate\" ] || exit 70")
        appendLine("      provider_pending=\"\$provider_candidate\"")
        appendLine("      cp -- \"\$provider_source\" \"\$provider_pending\"")
        appendLine("      chmod 644 \"\$provider_pending\"")
        appendLine("      mv -f -- \"\$provider_pending\" \"\$provider_target\"")
        appendLine("      provider_pending=''")
        appendLine("    fi")
        appendLine("  done")
        appendLine("}")
        appendLine("publish_provider_directory '$CmfaProvidersDirectory'")
        appendLine(")")
    }
}
