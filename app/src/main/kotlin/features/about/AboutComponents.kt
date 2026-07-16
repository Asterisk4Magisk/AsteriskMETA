// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import ui.icons.AsteriskIcons as Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.ProjectInfo
import app.R
import ui.components.AsteriskListRow
import ui.components.AsteriskSection
import ui.theme.AsteriskShapeTokens

private const val ProjectSourceUri = "https://github.com/Asterisk4Magisk/AsteriskMETA"
private const val TelegramChannelUri = "https://t.me/Asterisk4Magisk"
private const val AboutIconForegroundScale = 1.25f

@Composable
internal fun AboutIdentityHeader(
    modifier: Modifier = Modifier,
) {
    val identity = buildAboutIdentityState(
        projectName = ProjectInfo.PROJECT_NAME,
        versionName = ProjectInfo.VERSION_NAME,
        versionCode = ProjectInfo.VERSION_CODE,
        mihomoVersion = ProjectInfo.MIHOMO_CORE_VERSION,
        wrapperVersion = ProjectInfo.CMFA_WRAPPER_VERSION,
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(top = 20.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AboutAppIcon(Modifier.size(88.dp))
        Spacer(Modifier.height(12.dp))
        Text(
            text = identity.projectName,
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = identity.versionLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AboutAppIcon(
    modifier: Modifier = Modifier,
) {
    val iconStyle = aboutIconStyle()
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Surface(
            modifier = Modifier.fillMaxSize(),
            shape = AsteriskShapeTokens.PageCard,
            color = iconStyle.containerColor,
        ) {
            Image(
                painter = painterResource(iconStyle.foregroundResId),
                contentDescription = ProjectInfo.PROJECT_NAME,
                contentScale = ContentScale.Fit,
                colorFilter = iconStyle.foregroundTint?.let(ColorFilter::tint),
                modifier = Modifier.fillMaxSize().scale(AboutIconForegroundScale),
            )
        }
    }
}

@Composable
private fun aboutIconStyle(): AboutIconStyle {
    return AboutIconStyle(
        foregroundResId = R.drawable.ic_launcher_monochrome,
        foregroundTint = MaterialTheme.colorScheme.primary,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    )
}

private data class AboutIconStyle(
    val foregroundResId: Int,
    val foregroundTint: Color?,
    val containerColor: Color,
)

@Composable
internal fun AboutRuntimeSection(
    modifier: Modifier = Modifier,
) {
    AsteriskSection(
        modifier = modifier.fillMaxWidth(),
        title = stringResource(R.string.about_runtime),
    ) {
        AboutRuntimeRow("CMFA Mihomo wrapper", ProjectInfo.CMFA_WRAPPER_VERSION, Icons.Rounded.Extension)
        AboutRuntimeRow("Mihomo", ProjectInfo.MIHOMO_CORE_VERSION, Icons.Rounded.Router)
        AboutRuntimeRow("hev-socks5-tunnel", ProjectInfo.HEV_SOCKS5_TUNNEL_VERSION, Icons.Rounded.VpnLock)
    }
}

@Composable
private fun AboutRuntimeRow(name: String, version: String, icon: ImageVector) {
    AsteriskListRow(
        title = name,
        summary = version,
        leadingIcon = icon,
    )
}

@Composable
internal fun AboutLinksSection(
    title: String,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    AsteriskSection(
        modifier = modifier.fillMaxWidth(),
        title = title,
    ) {
        AboutLinkRow(
            title = stringResource(R.string.about_view_source),
            icon = Icons.Rounded.Code,
            onClick = { uriHandler.openUri(ProjectSourceUri) },
        )
        AboutLinkRow(
            title = stringResource(R.string.about_join_telegram),
            icon = Icons.AutoMirrored.Rounded.Send,
            onClick = { uriHandler.openUri(TelegramChannelUri) },
        )
    }
}

@Composable
private fun AboutLinkRow(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    AsteriskListRow(
        title = title,
        leadingIcon = icon,
        onClick = onClick,
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}
