// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.mihomo.provider

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.LocalAppServices
import app.LocalIsWideScreen
import app.R
import engine.mihomo.MihomoProviderRawContent
import engine.mihomo.MihomoRuleSetMrs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ui.clipboard.setPlainText
import ui.components.AsteriskScaffold
import ui.components.AsteriskStatusCard
import ui.components.AsteriskTopAppBar
import ui.components.AsteriskTopBarControls
import ui.layout.pageContentPaddingWithCutout
import ui.layout.pageListPadding
import ui.theme.AsteriskShapeTokens
import utils.toReadableBytes
import ui.icons.AsteriskIcons as Icons

/**
 * Full-screen reader for one provider.
 *
 * Text providers — `text`, `yaml` and `mrs` rule sets alike — are read line by line with a gutter of
 * line numbers, and the search bar matches literals or regular expressions with match navigation.
 * A rule set that cannot be decoded keeps its binary summary card instead of an empty reader.
 */
@Composable
internal fun MihomoProviderPreviewDialog(
    providerName: String,
    loading: Boolean,
    rawContent: MihomoProviderRawContent?,
    onDismissRequest: () -> Unit,
) {
    val isWideScreen = LocalIsWideScreen.current
    val services = LocalAppServices.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val textContent = rawContent as? MihomoProviderRawContent.Text
    val content = textContent?.content.orEmpty()
    val lines = remember(content) {
        if (content.isEmpty()) emptyList() else content.lines()
    }
    val listState = rememberLazyListState()
    val searchFocusRequester = remember { FocusRequester() }
    var searchActive by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var matchCase by rememberSaveable { mutableStateOf(false) }
    var useRegex by rememberSaveable { mutableStateOf(false) }
    var currentMatchIndex by remember { mutableIntStateOf(0) }
    var matches by remember { mutableStateOf(MihomoRuleTextMatches()) }
    var searching by remember { mutableStateOf(false) }
    // A large rule set takes long enough to scan that the query must not be matched while typing.
    LaunchedEffect(lines, query, matchCase, useRegex) {
        if (query.isEmpty() || lines.isEmpty()) {
            searching = false
            matches = MihomoRuleTextMatches()
            return@LaunchedEffect
        }
        searching = true
        delay(SearchDebounceMillis)
        matches = withContext(Dispatchers.Default) {
            searchMihomoRuleText(
                lines = lines,
                query = query,
                matchCase = matchCase,
                useRegex = useRegex,
            )
        }
        searching = false
    }
    val currentMatch = matches.matches.getOrNull(currentMatchIndex)
    val metadata = providerPreviewMetadata(rawContent = rawContent, lineCount = lines.size)
    val copiedMessage = stringResource(R.string.logs_copied_to_clipboard)

    fun closeSearch() {
        searchActive = false
        query = ""
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    fun stepMatch(step: Int) {
        val count = matches.matches.size
        if (count == 0) return
        currentMatchIndex = ((currentMatchIndex + step) % count + count) % count
    }

    LaunchedEffect(matches) {
        currentMatchIndex = 0
    }
    LaunchedEffect(currentMatchIndex, matches) {
        val match = matches.matches.getOrNull(currentMatchIndex) ?: return@LaunchedEffect
        listState.animateScrollToItem((match.lineIndex - LinesAboveMatch).coerceAtLeast(0))
    }
    LaunchedEffect(searchActive) {
        if (searchActive) searchFocusRequester.requestFocus()
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = false,
        ),
    ) {
        AsteriskScaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                Column {
                    if (searchActive) {
                        AsteriskTopAppBar(
                            title = {
                                ProviderRuleSearchField(
                                    query = query,
                                    onQueryChange = { query = it },
                                    matchCase = matchCase,
                                    onToggleMatchCase = { matchCase = !matchCase },
                                    useRegex = useRegex,
                                    onToggleRegex = { useRegex = !useRegex },
                                    focusRequester = searchFocusRequester,
                                    onSearch = { keyboardController?.hide() },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            },
                            navigationIcon = {
                                IconButton(onClick = ::closeSearch) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                        contentDescription = stringResource(
                                            R.string.mihomo_provider_preview_search_close,
                                        ),
                                    )
                                }
                            },
                        )
                        AsteriskTopBarControls(alignEndToAppBarActions = true) {
                            ProviderRuleSearchControls(
                                matches = matches,
                                currentIndex = currentMatchIndex,
                                hasQuery = query.isNotEmpty(),
                                searching = searching,
                                onPrevious = { stepMatch(-1) },
                                onNext = { stepMatch(1) },
                                onClose = ::closeSearch,
                            )
                        }
                    } else {
                        AsteriskTopAppBar(
                            title = {
                                Text(
                                    text = providerName.ifBlank { ProviderPreviewFallbackName },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            navigationIcon = {
                                IconButton(onClick = onDismissRequest) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                        contentDescription = stringResource(R.string.common_back),
                                    )
                                }
                            },
                            actions = {
                                if (lines.isNotEmpty()) {
                                    IconButton(onClick = { searchActive = true }) {
                                        Icon(
                                            imageVector = Icons.Rounded.Search,
                                            contentDescription = stringResource(R.string.common_search),
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                clipboard.setPlainText(content)
                                                services.tipNotifier.show(copiedMessage)
                                            }
                                        },
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.ContentCopy,
                                            contentDescription = stringResource(R.string.common_copy),
                                        )
                                    }
                                }
                            },
                        )
                        if (metadata.isNotBlank()) {
                            AsteriskTopBarControls {
                                Text(
                                    text = metadata,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            },
        ) { innerPadding ->
            val contentPadding = pageListPadding(
                pageContentPaddingWithCutout(
                    innerPadding = innerPadding,
                    outerPadding = PaddingValues(),
                    isWideScreen = isWideScreen,
                ),
            )
            when {
                loading -> ProviderPreviewLoading()
                textContent != null && lines.isNotEmpty() -> ProviderRuleTextList(
                    lines = lines,
                    matches = matches,
                    currentMatch = currentMatch,
                    contentPadding = contentPadding,
                    listState = listState,
                )

                textContent != null -> ProviderPreviewMessage(
                    contentPadding = contentPadding,
                    text = stringResource(R.string.common_empty),
                )

                else -> ProviderPreviewBinaryContent(
                    contentPadding = contentPadding,
                    content = rawContent as? MihomoProviderRawContent.Binary,
                )
            }
        }
    }
}

@Composable
private fun ProviderPreviewLoading() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
            Text(
                text = stringResource(R.string.mihomo_provider_preview_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProviderPreviewMessage(
    contentPadding: PaddingValues,
    text: String,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
    ) {
        ProviderMessageCard(text = text)
    }
}

@Composable
private fun ProviderPreviewBinaryContent(
    contentPadding: PaddingValues,
    content: MihomoProviderRawContent.Binary?,
) {
    val metadata = providerPreviewMetadata(rawContent = content, lineCount = 0)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
    ) {
        AsteriskStatusCard(status = metadata.takeIf { it.isNotBlank() }) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Description,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.mihomo_rule_provider_binary_unavailable),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                val lastError = content?.lastError.orEmpty()
                if (lastError.isNotBlank()) {
                    Text(
                        text = lastError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderRuleTextList(
    lines: List<String>,
    matches: MihomoRuleTextMatches,
    currentMatch: MihomoRuleTextMatch?,
    contentPadding: PaddingValues,
    listState: LazyListState,
) {
    val colorScheme = MaterialTheme.colorScheme
    val matchColor = colorScheme.primary.copy(alpha = MatchHighlightAlpha)
    val currentMatchColor = colorScheme.primary.copy(alpha = CurrentMatchHighlightAlpha)
    val currentMatchTextColor = colorScheme.onPrimary
    val currentLineColor = colorScheme.primary.copy(alpha = CurrentLineAlpha)
    val gutterWidth = remember(lines.size) { ruleTextGutterWidth(lines.size) }
    LazyColumn(
        state = listState,
        contentPadding = contentPadding,
        modifier = Modifier.fillMaxSize(),
    ) {
        items(count = lines.size) { index ->
            ProviderRuleTextLine(
                lineNumber = index + 1,
                text = lines[index],
                ranges = matches.byLine[index].orEmpty(),
                currentRange = currentMatch?.takeIf { match -> match.lineIndex == index }?.range,
                gutterWidth = gutterWidth,
                matchColor = matchColor,
                currentMatchColor = currentMatchColor,
                currentMatchTextColor = currentMatchTextColor,
                currentLineColor = currentLineColor,
            )
        }
    }
}

@Composable
private fun ProviderRuleTextLine(
    lineNumber: Int,
    text: String,
    ranges: List<IntRange>,
    currentRange: IntRange?,
    gutterWidth: Dp,
    matchColor: Color,
    currentMatchColor: Color,
    currentMatchTextColor: Color,
    currentLineColor: Color,
    modifier: Modifier = Modifier,
) {
    val annotated = remember(text, ranges, currentRange, matchColor, currentMatchColor, currentMatchTextColor) {
        buildAnnotatedString {
            append(text)
            ranges.forEach { range ->
                addHighlight(range = range, textLength = text.length, background = matchColor)
            }
            currentRange?.let { range ->
                addHighlight(
                    range = range,
                    textLength = text.length,
                    background = currentMatchColor,
                    textColor = currentMatchTextColor,
                )
            }
        }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (currentRange != null) currentLineColor else Color.Transparent)
            .padding(vertical = RuleTextVerticalPadding),
    ) {
        Text(
            text = lineNumber.toString(),
            style = RuleTextStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = GutterAlpha),
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.width(gutterWidth),
        )
        Spacer(modifier = Modifier.width(RuleTextGutterGap))
        Text(
            text = annotated,
            style = RuleTextStyle,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun AnnotatedString.Builder.addHighlight(
    range: IntRange,
    textLength: Int,
    background: Color,
    textColor: Color? = null,
) {
    val start = range.first.coerceIn(0, textLength)
    val end = (range.last + 1).coerceIn(start, textLength)
    if (end <= start) return
    addStyle(
        style = SpanStyle(
            background = background,
            color = textColor ?: Color.Unspecified,
        ),
        start = start,
        end = end,
    )
}

@Composable
private fun ProviderRuleSearchControls(
    matches: MihomoRuleTextMatches,
    currentIndex: Int,
    hasQuery: Boolean,
    searching: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val counter = when {
        !hasQuery || searching -> ""
        matches.patternInvalid -> stringResource(R.string.mihomo_provider_preview_search_invalid)
        !matches.hasMatches -> stringResource(R.string.mihomo_provider_preview_search_empty)
        matches.truncated -> stringResource(
            R.string.mihomo_provider_preview_search_count_truncated,
            currentIndex + 1,
            matches.matches.size,
        )

        else -> stringResource(
            R.string.mihomo_provider_preview_search_count,
            currentIndex + 1,
            matches.matches.size,
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = counter,
            style = MaterialTheme.typography.labelLarge,
            color = if (matches.patternInvalid) colorScheme.error else colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onPrevious, enabled = matches.hasMatches) {
            Icon(
                imageVector = Icons.Rounded.ArrowUpward,
                contentDescription = stringResource(R.string.mihomo_provider_preview_search_previous),
            )
        }
        IconButton(onClick = onNext, enabled = matches.hasMatches) {
            Icon(
                imageVector = Icons.Rounded.ArrowDownward,
                contentDescription = stringResource(R.string.mihomo_provider_preview_search_next),
            )
        }
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.mihomo_provider_preview_search_close),
            )
        }
    }
}

@Composable
private fun ProviderRuleSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    matchCase: Boolean,
    onToggleMatchCase: () -> Unit,
    useRegex: Boolean,
    onToggleRegex: () -> Unit,
    focusRequester: FocusRequester,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.focusRequester(focusRequester),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge,
        shape = AsteriskShapeTokens.Pill,
        placeholder = {
            Text(
                text = stringResource(R.string.mihomo_provider_preview_search_hint),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProviderRuleSearchToggle(
                    text = SearchMatchCaseLabel,
                    active = matchCase,
                    description = stringResource(R.string.mihomo_provider_preview_search_case),
                    onClick = onToggleMatchCase,
                )
                ProviderRuleSearchToggle(
                    text = SearchRegexLabel,
                    active = useRegex,
                    description = stringResource(R.string.mihomo_provider_preview_search_regex),
                    onClick = onToggleRegex,
                )
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(SearchToggleSize)) {
                        Icon(
                            imageVector = Icons.Rounded.Clear,
                            contentDescription = stringResource(R.string.common_clear),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
private fun ProviderRuleSearchToggle(
    text: String,
    active: Boolean,
    description: String,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .padding(start = 2.dp)
            .size(SearchToggleSize)
            .clip(AsteriskShapeTokens.Pill)
            .background(if (active) colorScheme.primary.copy(alpha = ToggleActiveAlpha) else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            color = if (active) colorScheme.primary else colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun providerPreviewMetadata(
    rawContent: MihomoProviderRawContent?,
    lineCount: Int,
): String {
    return when (rawContent) {
        is MihomoProviderRawContent.Text -> {
            val source = stringResource(
                when {
                    // A decoded rule set is no longer the file the provider declares.
                    rawContent.sourceFormat.equals(MihomoRuleSetMrs.Format, ignoreCase = true) ->
                        R.string.mihomo_provider_preview_decoded_mrs

                    rawContent.declarationOnly -> R.string.mihomo_provider_raw_declaration
                    else -> R.string.mihomo_provider_raw_read_only
                },
            )
            var metadata = source
            if (rawContent.byteSize > 0L) {
                metadata = stringResource(
                    R.string.mihomo_provider_raw_metadata,
                    metadata,
                    rawContent.byteSize.toReadableBytes(),
                )
            }
            if (lineCount > 0) {
                metadata = stringResource(
                    R.string.mihomo_provider_raw_metadata,
                    metadata,
                    pluralStringResource(R.plurals.mihomo_provider_preview_lines, lineCount, lineCount),
                )
            }
            metadata
        }

        is MihomoProviderRawContent.Binary -> {
            var metadata = stringResource(
                R.string.mihomo_provider_raw_metadata,
                stringResource(R.string.mihomo_rule_provider_binary_preview),
                rawContent.format.ifBlank { MihomoRuleSetMrs.Format }.uppercase(),
            )
            metadata = stringResource(
                R.string.mihomo_provider_raw_metadata,
                metadata,
                rawContent.byteSize.toReadableBytes(),
            )
            rawContent.ruleCount?.let { count ->
                metadata = stringResource(
                    R.string.mihomo_provider_raw_metadata,
                    metadata,
                    pluralStringResource(R.plurals.mihomo_rule_provider_rules_count, count, count),
                )
            }
            metadata
        }

        null -> ""
    }
}

private fun ruleTextGutterWidth(lineCount: Int): Dp {
    val digits = lineCount.toString().length.coerceAtLeast(MinimumGutterDigits)
    return (digits * GutterDigitWidth + GutterTrailingSpace).dp
}

private const val ProviderPreviewFallbackName = "-"
private const val MinimumGutterDigits = 2
private const val GutterDigitWidth = 7.5f
private const val GutterTrailingSpace = 6f
private const val GutterAlpha = 0.6f
private const val MatchHighlightAlpha = 0.22f
private const val CurrentMatchHighlightAlpha = 0.55f
private const val CurrentLineAlpha = 0.08f
private const val ToggleActiveAlpha = 0.16f
private const val LinesAboveMatch = 2
private const val SearchDebounceMillis = 120L
private const val SearchMatchCaseLabel = "Aa"
private const val SearchRegexLabel = ".*"

private val SearchToggleSize = 32.dp
private val RuleTextGutterGap = 10.dp
private val RuleTextVerticalPadding = 2.dp
private val RuleTextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 13.sp,
    lineHeight = 18.sp,
)
