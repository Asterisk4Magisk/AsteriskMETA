// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import ui.theme.AsteriskShapeTokens

/**
 * A multi-line [androidx.compose.material3.OutlinedTextField] whose lines never wrap: a line wider
 * than the field scrolls horizontally, and the field scrolls to keep the cursor in view. Use it
 * for line-oriented input, such as one entry per line, where a wrapped line would read as two.
 */
@Composable
internal fun AsteriskNoWrapTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    minLines: Int = 1,
    maxLines: Int = Int.MAX_VALUE,
    shape: Shape = AsteriskShapeTokens.InnerContainer,
) {
    val state = rememberTextFieldState(value)
    val currentValue by rememberUpdatedState(value)
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    // [value] stays the source of truth; the state only adds the selection the field follows.
    LaunchedEffect(value) {
        if (state.text.toString() != value) state.setTextAndPlaceCursorAtEnd(value)
    }
    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }.collect { text ->
            if (text != currentValue) currentOnValueChange(text)
        }
    }

    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val colors = OutlinedTextFieldDefaults.colors()
    val lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = minLines, maxHeightInLines = maxLines)
    val horizontalScrollState = rememberScrollState()
    var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var viewportWidth by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    LaunchedEffect(focused, state.selection, textLayout, viewportWidth, horizontalScrollState.maxValue) {
        // An unfocused field keeps its scroll, so a field opened with the cursor at the end still
        // shows the start of its lines.
        if (!focused) return@LaunchedEffect
        val layout = textLayout ?: return@LaunchedEffect
        // The selection can change before the text it belongs to is laid out.
        if (layout.layoutInput.text.text != state.text.toString()) return@LaunchedEffect
        val cursor = layout.getCursorRect(state.selection.end.coerceIn(0, state.text.length))
        val padding = with(density) { NoWrapCursorScrollPadding.toPx() }
        val next = scrollToVisible(
            current = horizontalScrollState.value,
            viewportSize = viewportWidth,
            targetStart = cursor.left - padding,
            targetEnd = cursor.right + padding,
            maxValue = horizontalScrollState.maxValue,
        )
        if (next != horizontalScrollState.value) horizontalScrollState.scrollTo(next)
    }

    val materialDecorator = OutlinedTextFieldDefaults.decorator(
        state = state,
        enabled = true,
        lineLimits = lineLimits,
        outputTransformation = null,
        interactionSource = interactionSource,
        label = label?.let { content -> { content() } },
        placeholder = placeholder,
        supportingText = supportingText,
        isError = isError,
        colors = colors,
        container = {
            OutlinedTextFieldDefaults.Container(
                enabled = true,
                isError = isError,
                interactionSource = interactionSource,
                colors = colors,
                shape = shape,
            )
        },
    )

    BasicTextField(
        state = state,
        // Like OutlinedTextField, reserve room for the label that floats above the outline, so a
        // clipping parent such as a scrolling column does not cut it off.
        modifier = if (label != null) modifier.padding(top = NoWrapLabelTopPadding) else modifier,
        textStyle = textStyle.merge(
            TextStyle(
                color = when {
                    isError -> colors.errorTextColor
                    focused -> colors.focusedTextColor
                    else -> colors.unfocusedTextColor
                },
            ),
        ),
        keyboardOptions = keyboardOptions,
        lineLimits = lineLimits,
        onTextLayout = { getResult -> textLayout = getResult() },
        interactionSource = interactionSource,
        cursorBrush = SolidColor(if (isError) colors.errorCursorColor else colors.cursorColor),
        decorator = TextFieldDecorator { innerTextField ->
            materialDecorator.Decoration {
                // Unbounded width keeps every line on one line; the minimum width keeps the whole
                // field tappable when the text is short.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { size -> viewportWidth = size.width }
                        .horizontalScroll(horizontalScrollState),
                ) {
                    Box(modifier = Modifier.widthIn(min = with(density) { viewportWidth.toDp() })) {
                        innerTextField()
                    }
                }
            }
        },
    )
}

/** The scroll position that brings [targetStart]..[targetEnd] into a viewport of [viewportSize]. */
private fun scrollToVisible(
    current: Int,
    viewportSize: Int,
    targetStart: Float,
    targetEnd: Float,
    maxValue: Int,
): Int {
    if (viewportSize <= 0 || maxValue <= 0) return current
    val next = when {
        targetStart < current -> targetStart.toInt()
        targetEnd > current + viewportSize -> (targetEnd - viewportSize + 1f).toInt()
        else -> current
    }
    return next.coerceIn(0, maxValue)
}

private val NoWrapCursorScrollPadding = 24.dp

/** Half the floating label's height, the space OutlinedTextField keeps above its outline. */
private val NoWrapLabelTopPadding = 8.dp
