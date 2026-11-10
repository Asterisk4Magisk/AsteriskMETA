// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import ui.theme.AsteriskShapeTokens

/*
 * A titled card that holds a list of entries, built from segments: a [SectionCardHeader], any
 * number of [SectionCardItem]s and a [SectionCardFooter] read as one card. Keep the segments
 * adjacent; the header carries the gap to whatever is above it.
 *
 * In a Column every segment paints its own part of the card. In a lazy list each segment is its
 * own item and the list paints the cards behind its items with [lazySectionCards]: an item moved
 * by reordering or by an item animation then never takes a piece of the card with it.
 */

internal enum class SectionCardPart { Header, Item, Footer }

/** The top of a section card: its title, entry count, description and header actions. */
@Composable
internal fun SectionCardHeader(
    title: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
    description: String? = null,
    descriptionIsWarning: Boolean = false,
    paintBackground: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = SectionCardSpacing)
            .sectionCardBackground(SectionCardColor.takeIf { paintBackground }, SectionCardTopShape)
            .padding(start = 16.dp, end = SectionCardHeaderEndPadding, top = 4.dp),
    ) {
        Row(
            modifier = Modifier.heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            count?.let { AsteriskInfoChip(text = it.toString()) }
            // Header buttons carry 9dp of inner padding; this keeps every header gap at 18dp,
            // as in the StringListEditor headers of AsteriskBOX and AsteriskNG.
            Spacer(Modifier.width(9.dp))
            actions()
        }
        if (!description.isNullOrBlank()) {
            Text(
                text = description,
                modifier = Modifier.padding(end = 12.dp, bottom = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = if (descriptionIsWarning) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

/** An icon button of a [SectionCardHeader]. */
@Composable
internal fun SectionCardHeaderAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(SectionCardHeaderActionSize)) {
        Icon(icon, contentDescription)
    }
}

/** One entry of a section card; [content] is usually a [SectionCardEntry]. */
@Composable
internal fun SectionCardItem(
    modifier: Modifier = Modifier,
    paintBackground: Boolean = true,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .sectionCardBackground(SectionCardColor.takeIf { paintBackground }, null)
            .padding(start = 12.dp, end = 12.dp, top = 6.dp),
    ) {
        content()
    }
}

/** The bottom of a section card; [text] is shown when the card has no entries. */
@Composable
internal fun SectionCardFooter(
    modifier: Modifier = Modifier,
    text: String? = null,
    paintBackground: Boolean = true,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .sectionCardBackground(SectionCardColor.takeIf { paintBackground }, SectionCardBottomShape)
            .padding(start = 16.dp, end = 16.dp, top = if (text == null) 6.dp else 4.dp, bottom = 12.dp),
    ) {
        if (text != null) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The inner card of one entry, styled like the values of a [StringListEditor]. */
@Composable
internal fun SectionCardEntry(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = AsteriskShapeTokens.InnerContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/**
 * Paints the section cards of a lazy list behind its items. [partOf] tells, for an item key,
 * which card the item belongs to and which part of it the item is, or `null` for an item outside
 * every card; those items are composed with `paintBackground = false`. A card runs from its
 * header to its footer, or to the viewport edge while either is scrolled out of view.
 */
internal fun Modifier.lazySectionCards(
    state: LazyListState,
    contentPadding: PaddingValues,
    color: Color,
    partOf: (key: Any) -> Pair<Any, SectionCardPart>?,
): Modifier = drawBehind { drawLazySectionCards(state, contentPadding, color, partOf) }

/** The color of a section card, for [lazySectionCards]. */
internal val SectionCardColor: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh

private fun DrawScope.drawLazySectionCards(
    state: LazyListState,
    contentPadding: PaddingValues,
    color: Color,
    partOf: (key: Any) -> Pair<Any, SectionCardPart>?,
) {
    val layoutInfo = state.layoutInfo
    val radius = AsteriskShapeTokens.ListCardRadius.toPx()
    val spacing = SectionCardSpacing.toPx()
    // Item offsets start after the leading content padding.
    val origin = -layoutInfo.viewportStartOffset.toFloat()
    val left = contentPadding.calculateStartPadding(layoutDirection).toPx()
    val right = size.width - contentPadding.calculateEndPadding(layoutDirection).toPx()
    // An open edge is pushed past the viewport so its rounded corners are never seen.
    val openTop = -radius
    val openBottom = size.height + radius
    val bounds = LinkedHashMap<Any, FloatArray>()
    layoutInfo.visibleItemsInfo.forEach { item ->
        val (card, part) = partOf(item.key) ?: return@forEach
        val edges = bounds.getOrPut(card) { floatArrayOf(openTop, openBottom) }
        val top = origin + item.offset
        when (part) {
            SectionCardPart.Header -> edges[0] = top + spacing
            SectionCardPart.Footer -> edges[1] = top + item.size
            SectionCardPart.Item -> Unit
        }
    }
    bounds.values.forEach { (top, bottom) ->
        if (bottom > top) {
            drawRoundRect(
                color = color,
                topLeft = Offset(left, top),
                size = Size(right - left, bottom - top),
                cornerRadius = CornerRadius(radius),
            )
        }
    }
}

private fun Modifier.sectionCardBackground(color: Color?, shape: RoundedCornerShape?): Modifier = when {
    color == null -> this
    shape == null -> background(color)
    else -> background(color, shape)
}

private val SectionCardSpacing = 12.dp

private val SectionCardHeaderActionSize = 42.dp

// Centres the last header button over the trailing 48dp button of the entries below it.
private val SectionCardHeaderEndPadding = 19.dp

private val SectionCardTopShape = RoundedCornerShape(
    topStart = AsteriskShapeTokens.ListCardRadius,
    topEnd = AsteriskShapeTokens.ListCardRadius,
)

private val SectionCardBottomShape = RoundedCornerShape(
    bottomStart = AsteriskShapeTokens.ListCardRadius,
    bottomEnd = AsteriskShapeTokens.ListCardRadius,
)
