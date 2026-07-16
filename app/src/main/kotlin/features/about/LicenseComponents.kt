// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import ui.icons.AsteriskIcons as Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import features.about.license.Library
import ui.components.AsteriskChipTone
import ui.components.AsteriskInfoChip
import ui.components.AsteriskPageCard
import ui.theme.AsteriskShapeTokens

@Composable
internal fun LibraryLicenseCard(
    library: Library,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val hasWebsite = hasLibraryWebsite(library)
    val artifactVersion = library.artifactVersion?.takeIf(String::isNotBlank)
    AsteriskPageCard(
        modifier = modifier.fillMaxWidth(),
        onClick = if (hasWebsite) {
            {
                library.website?.takeIf(String::isNotBlank)?.let(uriHandler::openUri)
            }
        } else {
            null
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = AsteriskShapeTokens.SmallContainer,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Widgets,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(10.dp).size(22.dp),
                )
            }
            androidx.compose.foundation.layout.Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = library.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = library.uniqueId,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (library.licenses.isNotEmpty() || artifactVersion != null) {
                    Row(
                        modifier = Modifier.padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (library.licenses.isNotEmpty()) {
                            AsteriskInfoChip(
                                text = library.licenses.joinToString(),
                                tone = AsteriskChipTone.Secondary,
                            )
                        }
                        artifactVersion?.let { version ->
                            AsteriskInfoChip(text = version)
                        }
                    }
                }
            }
            if (hasWebsite) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    }
}
