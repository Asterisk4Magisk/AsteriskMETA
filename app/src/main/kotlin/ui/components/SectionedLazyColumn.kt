// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Keeps normal row spacing independent of the 8dp spacing around section titles. */
@Composable
internal fun SectionedLazyColumn(
    itemSpacing: Dp,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: SectionedListScope.() -> Unit,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding,
    ) {
        SectionedListScope(this, itemSpacing).content()
    }
}

internal class SectionedListScope(
    private val target: LazyListScope,
    private val itemSpacing: Dp,
) : LazyListScope by target {
    private var firstItem = true
    private var previousWasTitle = false

    /** Title content must not add its own vertical padding. */
    fun sectionTitleItem(
        key: Any,
        content: @Composable LazyItemScope.() -> Unit,
    ) {
        target.item(key = key) {
            val itemScope = this
            Column(Modifier.padding(vertical = 8.dp)) { content(itemScope) }
        }
        firstItem = false
        previousWasTitle = true
    }

    override fun item(
        key: Any?,
        contentType: Any?,
        content: @Composable LazyItemScope.() -> Unit,
    ) {
        val topPadding = nextItemPadding()
        target.item(key, contentType) {
            val itemScope = this
            Column(Modifier.padding(top = topPadding)) { content(itemScope) }
        }
        firstItem = false
        previousWasTitle = false
    }

    override fun items(
        count: Int,
        key: ((Int) -> Any)?,
        contentType: (Int) -> Any?,
        itemContent: @Composable LazyItemScope.(Int) -> Unit,
    ) {
        val firstItemPadding = nextItemPadding()
        val remainingItemPadding = itemSpacing
        target.items(count, key, contentType) { index ->
            val itemScope = this
            Column(Modifier.padding(top = if (index == 0) firstItemPadding else remainingItemPadding)) {
                itemContent(itemScope, index)
            }
        }
        if (count > 0) {
            firstItem = false
            previousWasTitle = false
        }
    }

    private fun nextItemPadding(): Dp = if (firstItem || previousWasTitle) 0.dp else itemSpacing
}
