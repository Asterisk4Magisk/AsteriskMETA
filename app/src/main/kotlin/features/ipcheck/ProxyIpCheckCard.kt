// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.ipcheck

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.LocalAppServices
import app.R
import ui.icons.AsteriskIcons
import kotlinx.coroutines.launch
import ui.components.AsteriskDropdownAnchor
import ui.components.AsteriskDropdownMenuItem
import ui.components.AsteriskPageCard

/**
 * The card that shows the addresses of a proxy group.
 *
 * The card offers the groups as a dropdown, so a configuration with many groups can be checked
 * group by group, and it shows the two addresses of the checked group: the address the tunnel
 * starts at and the address it leaves with.
 */
@Composable
internal fun ProxyIpCheckCard(
    state: ProxyIpCheckState,
    onSelectGroup: (String) -> Unit,
    onStartCheck: () -> Unit,
    localNetworkOnly: Boolean = false,
    masked: Boolean = true,
    onToggleMask: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    AsteriskPageCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ProxyIpCheckHeader(
                checking = state.checking,
                hasResult = state.hasResult,
                masked = masked,
                onToggleMask = onToggleMask,
            )
            // Without the tunnel of this app the groups describe nothing the check could measure,
            // so the selector would only offer a choice that changes no answer.
            if (!localNetworkOnly) {
                ProxyIpCheckGroupSelector(
                    groups = state.groups.map { group -> group.name },
                    selectedGroup = state.effectiveGroup(),
                    enabled = state.groups.isNotEmpty() && !state.checking,
                    onSelectGroup = onSelectGroup,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (state.result.isDirect) {
                // The group hands the traffic to the network, so one address answers the whole
                // question and a second column would only repeat it.
                ProxyIpCheckEndpointColumn(
                    title = stringResource(R.string.home_proxy_ip_check_direct),
                    endpoint = state.result.entry,
                    masked = masked,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                ProxyIpCheckEndpointRow(
                    entry = state.result.entry,
                    exit = state.result.exit,
                    masked = masked,
                )
            }
            ProxyIpCheckAction(
                checking = state.checking,
                enabled = localNetworkOnly || state.groups.isNotEmpty(),
                onStartCheck = onStartCheck,
            )
        }
    }
}

/**
 * The title row with the state of the last check.
 */
@Composable
private fun ProxyIpCheckHeader(
    checking: Boolean,
    hasResult: Boolean,
    masked: Boolean,
    onToggleMask: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.home_proxy_ip_check_title),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(4.dp))
        ProxyIpCheckVisibilityToggle(masked = masked, onToggleMask = onToggleMask)
        Spacer(Modifier.width(4.dp))
        ProxyIpCheckStatusBadge(checking = checking, hasResult = hasResult)
    }
}

/**
 * The eye that decides how much of the answer is on screen.
 *
 * The choice is a preference of the app rather than a property of a check, so it outlives the
 * result: a user who hides the addresses keeps them hidden after the next check.
 */
@Composable
private fun ProxyIpCheckVisibilityToggle(
    masked: Boolean,
    onToggleMask: () -> Unit,
) {
    IconButton(
        onClick = onToggleMask,
        modifier = Modifier.size(32.dp),
    ) {
        Icon(
            imageVector = if (masked) AsteriskIcons.Rounded.VisibilityOff else AsteriskIcons.Rounded.Visibility,
            contentDescription = stringResource(
                if (masked) {
                    R.string.home_proxy_ip_check_show_details
                } else {
                    R.string.home_proxy_ip_check_hide_details
                },
            ),
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The badge that states what the card knows.
 */
@Composable
private fun ProxyIpCheckStatusBadge(
    checking: Boolean,
    hasResult: Boolean,
) {
    val text = when {
        checking -> stringResource(R.string.home_proxy_ip_check_checking)
        hasResult -> stringResource(R.string.home_proxy_ip_check_done)
        else -> stringResource(R.string.home_proxy_ip_check_pending)
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
    }
}

/**
 * The dropdown that names the group the check runs against.
 */
@Composable
private fun ProxyIpCheckGroupSelector(
    groups: List<String>,
    selectedGroup: String,
    enabled: Boolean,
    onSelectGroup: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.home_proxy_ip_check_strategy),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable(enabled = enabled && groups.isNotEmpty(), role = Role.DropdownList) {
                    expanded = !expanded
                },
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = selectedGroup.ifBlank { stringResource(R.string.home_proxy_ip_check_no_group) },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                AsteriskDropdownAnchor(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    menuModifier = Modifier.widthIn(min = 180.dp, max = 280.dp),
                ) {
                    groups.forEach { group ->
                        AsteriskDropdownMenuItem(
                            text = group,
                            selected = group == selectedGroup,
                            onClick = {
                                expanded = false
                                onSelectGroup(group)
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * The two addresses of the checked group, side by side.
 *
 * The two sides are laid out as one table rather than as two columns: every field is a row of its
 * own, so the value that runs onto a second line makes the row taller for both sides and the next
 * field starts at the same height on either side. The side with the shorter answer keeps the room
 * it does not need, and the rule between the families is one line across the table instead of one
 * line per side at a different height.
 */
@Composable
private fun ProxyIpCheckEndpointRow(
    entry: IpCheckEndpoint,
    exit: IpCheckEndpoint,
    masked: Boolean,
) {
    val showV6 = entry.rendersV6() || exit.rendersV6()
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        ProxyIpCheckCells(
            left = { ProxyIpCheckColumnTitle(stringResource(R.string.home_proxy_ip_check_entry)) },
            right = { ProxyIpCheckColumnTitle(stringResource(R.string.home_proxy_ip_check_exit)) },
        )
        ProxyIpCheckFields(
            masked = masked,
            left = { slot -> ProxyIpCheckAddressSlot(entry, slot, masked) },
            right = { slot -> ProxyIpCheckAddressSlot(exit, slot, masked) },
        )
        if (showV6) {
            ProxyIpCheckSectionDivider()
            ProxyIpCheckFields(
                masked = masked,
                left = { slot -> ProxyIpCheckV6Slot(entry, slot, masked) },
                right = { slot -> ProxyIpCheckV6Slot(exit, slot, masked) },
            )
        }
    }
}

/**
 * One address and the network it belongs to, for the case where there is only one side.
 *
 * Every value is on its own line and copies itself when it is tapped, so a user can take the
 * address, the province, the autonomous system and the organization one at a time.
 */
@Composable
private fun ProxyIpCheckEndpointColumn(
    title: String,
    endpoint: IpCheckEndpoint,
    masked: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        ProxyIpCheckColumnTitle(title)
        ProxyIpCheckSlots(masked = masked) { slot -> ProxyIpCheckAddressSlot(endpoint, slot, masked) }
        if (endpoint.rendersV6()) {
            ProxyIpCheckSectionDivider()
            ProxyIpCheckSlots(masked = masked) { slot -> ProxyIpCheckV6Slot(endpoint, slot, masked) }
        }
    }
}

/** The label above an address. */
@Composable
private fun ProxyIpCheckColumnTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
    )
}

/**
 * One row of the table: the same field of both sides, so the two cells share a height.
 */
@Composable
private fun ProxyIpCheckCells(
    left: @Composable () -> Unit,
    right: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) { left() }
        Column(modifier = Modifier.weight(1f)) { right() }
    }
}

/** The four fields of one side, one row of the table each. */
@Composable
private fun ProxyIpCheckFields(
    masked: Boolean,
    left: @Composable (Int) -> Unit,
    right: @Composable (Int) -> Unit,
) {
    (if (masked) ProxyIpCheckPublicSlots else ProxyIpCheckFullSlots).forEach { slot ->
        ProxyIpCheckCells(
            left = { left(slot) },
            right = { right(slot) },
        )
    }
}

/** The slots of one side, stacked, for the case where there is only one side. */
@Composable
private fun ProxyIpCheckSlots(masked: Boolean, content: @Composable (Int) -> Unit) {
    (if (masked) ProxyIpCheckPublicSlots else ProxyIpCheckFullSlots).forEach { slot -> content(slot) }
}

/** The rule between the families. It crosses the whole table, so the two sides share one line. */
@Composable
private fun ProxyIpCheckSectionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(top = 5.dp, bottom = 3.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/**
 * One field of the IPv4 side. A side without an address shows the placeholder in the first two
 * slots and leaves the rest empty, which keeps the table rows where they are.
 */
@Composable
private fun ProxyIpCheckAddressSlot(endpoint: IpCheckEndpoint, slot: Int, masked: Boolean) {
    if (!endpoint.hasAddress) {
        when (slot) {
            0 -> Text(
                text = stringResource(R.string.home_proxy_ip_check_pending_value),
                style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            1 -> Text(
                text = stringResource(R.string.home_proxy_ip_check_unknown_network),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        return
    }
    when (slot) {
        0 -> ProxyIpCheckAddressValue(endpoint.displayAddress(masked))
        1 -> ProxyIpCheckPlaceValue(endpoint)
        2 -> ProxyIpCheckDetailValue(endpoint.asn)
        3 -> ProxyIpCheckDetailValue(endpoint.organization)
    }
}

/**
 * One field of the IPv6 side, told apart from the IPv4 side by the rule above it.
 *
 * An address of this family is long enough that one line rarely holds it, so the parts are broken
 * after the colons and the tail continues on a second line. The breaks are only for the eye: the
 * value that is copied is the address itself.
 */
@Composable
private fun ProxyIpCheckV6Slot(endpoint: IpCheckEndpoint, slot: Int, masked: Boolean) {
    if (endpoint.v6Unsupported) {
        if (slot == 0) {
            Text(
                text = stringResource(R.string.home_proxy_ip_check_ipv6_unsupported),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        return
    }
    val v6 = endpoint.v6 ?: return
    if (!v6.hasAddress) return
    when (slot) {
        0 -> ProxyIpCheckCopyableValue(
            text = v6.displayAddress(masked).withIpv6Breaks(),
            copyText = v6.displayAddress(masked),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurface,
        )
        // The IPv6 side is described the same way as the IPv4 side: the two can belong to
        // different networks even when they reach the same machine.
        1 -> ProxyIpCheckPlaceValue(v6)
        2 -> ProxyIpCheckDetailValue(v6.asn)
        3 -> ProxyIpCheckDetailValue(v6.organization)
    }
}

/** Whether the IPv6 part of a side has anything to say. */
private fun IpCheckEndpoint.rendersV6(): Boolean {
    return hasAddress && (v6Unsupported || v6?.hasAddress == true)
}

/**
 * The address as it is shown: whole, or only the part that tells one network from another.
 *
 * The two leading segments are enough to recognise an address and not enough to reach it, which
 * is what a reader who hides the card wants. The autonomous system and the operator are not part
 * of the answer in that state, so their fields are not laid out at all.
 */
private fun IpCheckEndpoint.displayAddress(masked: Boolean): String {
    return if (masked) address.maskedAddress() else address
}

/** The first two segments of an address, the rest replaced by a marker. */
private fun String.maskedAddress(): String {
    val separator = if (contains(':')) ':' else '.'
    val segments = split(separator)
    if (segments.size <= 2) return this
    return segments.take(2).joinToString(separator.toString()) + "$separator***"
}

/** The fields a side is described by when everything is shown. */
private val ProxyIpCheckFullSlots = 0..3

/** The fields a side is described by when the identifying parts are hidden. */
private val ProxyIpCheckPublicSlots = 0..1

/**
 * The address of the endpoint, which copies itself when it is tapped.
 */
@Composable
private fun ProxyIpCheckAddressValue(address: String) {
    ProxyIpCheckCopyableValue(
        text = address,
        style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
        color = MaterialTheme.colorScheme.onSurface,
    )
}

/**
 * One line of the network an address belongs to, which copies itself when it is tapped. An empty
 * part of the answer takes no room, so a network without an autonomous system does not leave a
 * blank line behind.
 */
@Composable
private fun ProxyIpCheckDetailValue(value: String) {
    if (value.isBlank()) return
    ProxyIpCheckCopyableValue(
        text = value,
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * The country and the region the address belongs to, on one line behind the flag of the country.
 *
 * The flag and the two names read as one statement, so the whole line copies itself at once. The
 * flag is a bundled vector, so the line looks the same with no network and no image loader.
 */
@Composable
private fun ProxyIpCheckPlaceValue(endpoint: IpCheckEndpoint) {
    val region = endpoint.province.ifBlank { endpoint.city }
    val label = listOfNotNull(
        endpoint.country.takeIf { value -> value.isNotBlank() },
        // A city state reports the same name for the country and the region; saying it twice
        // reads as a mistake rather than as an answer.
        region.takeIf { value -> value.isNotBlank() && !value.equals(endpoint.country, ignoreCase = true) },
    ).joinToString(" · ")
    if (label.isBlank()) return
    val flag = rememberFlagResource(endpoint.countryCode)
    val copy = rememberCopyAction(label)
    val style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp)
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable(role = Role.Button, onClick = copy)
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (flag != 0) {
            val side = with(LocalDensity.current) { style.fontSize.toDp() }
            Image(
                painter = painterResource(flag),
                contentDescription = null,
                modifier = Modifier.size(side),
            )
        }
        Text(
            text = label,
            style = style,
            color = color,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The drawable of the flag of [countryCode], or zero when the code has no bundled flag. The
 * lookup is by resource name, so the flags stay plain resources with no generated table to keep
 * in step with them.
 */
@SuppressLint("DiscouragedApi")
@Composable
private fun rememberFlagResource(countryCode: String): Int {
    val context = LocalContext.current
    return remember(countryCode, context) {
        val code = countryCode.trim().lowercase()
        if (code.length == 2 && code.all { character -> character in 'a'..'z' }) {
            context.resources.getIdentifier("flag_$code", "drawable", context.packageName)
        } else {
            0
        }
    }
}

/**
 * The action that copies [text] to the clipboard, paired with the toast that names it.
 *
 * The clipboard is the platform one and the toast is the platform toast, so the value lands
 * where every other app expects it and the confirmation looks like the rest of the system.
 */
@Composable
private fun rememberCopyAction(text: String): () -> Unit {
    val context = LocalContext.current
    val services = LocalAppServices.current
    val scope = rememberCoroutineScope()
    val copiedMessage = stringResource(R.string.home_proxy_ip_check_copied, text)
    return {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText(IpCheckClipLabel, text))
        scope.launch { services.tipNotifier.show(copiedMessage) }
    }
}

/**
 * The address with a soft break after every colon, so a long address wraps between its parts
 * instead of running off the column. A zero-width space leaves the rendering unchanged, and the
 * layout decides where the line actually ends.
 */
private fun String.withIpv6Breaks(): String {
    if (!contains(':')) return this
    return replace(":", ":\u200B")
}

/**
 * A value that copies itself to the clipboard when it is tapped, and says so with a toast.
 *
 * [copyText] is what lands in the clipboard. It defaults to the text on screen, and differs only
 * where the text carries breaks that are not part of the value.
 */
@Composable
private fun ProxyIpCheckCopyableValue(
    text: String,
    style: TextStyle,
    color: Color,
    copyText: String = text,
) {
    val copy = rememberCopyAction(copyText)
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable(role = Role.Button, onClick = copy)
            .padding(vertical = 3.dp),
        style = style,
        color = color,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/** The label the copied value carries, which is what a clipboard history reads. */
private const val IpCheckClipLabel = "AsteriskMETA proxy IP check"

/**
 * The button that starts the check.
 */
@Composable
private fun ProxyIpCheckAction(
    checking: Boolean,
    enabled: Boolean,
    onStartCheck: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(enabled = enabled && !checking, onClick = onStartCheck),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            if (checking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Text(
                    text = stringResource(R.string.home_proxy_ip_check_start),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f),
                    maxLines = 1,
                )
            }
        }
    }
}
