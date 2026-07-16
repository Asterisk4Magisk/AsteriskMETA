// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import ui.icons.AsteriskIcons as Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.LocalIsWideScreen
import app.LocalNavigator
import app.R
import features.about.license.Library
import features.about.license.decodeAboutLibraries
import ui.layout.pageContentPaddingWithCutout
import ui.layout.pageListPadding
import ui.components.AsteriskContentHeader
import ui.components.AsteriskPinnedSearchArea

private sealed interface LicenseLoadState {
    data object Loading : LicenseLoadState
    data class Ready(val libraries: List<Library>) : LicenseLoadState
    data class Failed(val message: String) : LicenseLoadState
}

@Composable
fun LicensePage(
    padding: PaddingValues,
) {
    val isWideScreen = LocalIsWideScreen.current
    val navigator = LocalNavigator.current
    val appContext = LocalContext.current.applicationContext
    var query by remember { mutableStateOf("") }
    var reloadToken by remember { mutableIntStateOf(0) }
    val loadState by produceState<LicenseLoadState>(
        initialValue = LicenseLoadState.Loading,
        appContext,
        reloadToken,
    ) {
        value = runCatching {
            appContext.assets.open("aboutlibraries.json").bufferedReader().use { reader ->
                LicenseLoadState.Ready(decodeAboutLibraries(reader.readText()).libraries)
            }
        }.getOrElse { error ->
            LicenseLoadState.Failed(error.message.orEmpty())
        }
    }
    val libraries = (loadState as? LicenseLoadState.Ready)?.libraries.orEmpty()
    val visibleLibraries = remember(libraries, query) {
        reduceLibraries(libraries, query)
    }
    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                TopAppBar(
                    title = { Text(stringResource(R.string.license_title)) },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.common_back),
                            )
                        }
                    },
                )
                AsteriskPinnedSearchArea(
                    query = query,
                    onQueryChange = { query = it },
                    placeholder = stringResource(R.string.common_search),
                    clearContentDescription = stringResource(R.string.common_clear),
                )
            }
        },
    ) { innerPadding ->
        val contentPadding = pageListPadding(
            pageContentPaddingWithCutout(
                innerPadding = innerPadding,
                outerPadding = padding,
                isWideScreen = isWideScreen,
            ),
        )
        LazyColumn(
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (loadState !is LicenseLoadState.Ready) {
                item(key = "license_load_status") {
                    LicenseLoadStatus(
                        loadState = loadState,
                        onRetry = { reloadToken += 1 },
                    )
                }
            }
            when (val state = loadState) {
                LicenseLoadState.Loading -> Unit

                is LicenseLoadState.Failed -> Unit

                is LicenseLoadState.Ready -> {
                    if (visibleLibraries.isEmpty()) {
                        item(key = "license_empty") {
                            Text(
                                text = stringResource(R.string.common_empty),
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                            )
                        }
                    } else {
                        items(visibleLibraries, key = Library::uniqueId) { library ->
                            LibraryLicenseCard(library)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LicenseLoadStatus(
    loadState: LicenseLoadState,
    onRetry: () -> Unit,
) {
    val summary = when (loadState) {
        LicenseLoadState.Loading -> stringResource(R.string.mihomo_dashboard_network_detection_checking)
        is LicenseLoadState.Failed -> loadState.message.ifBlank { stringResource(R.string.license_load_failed) }
        is LicenseLoadState.Ready -> return
    }
    AsteriskContentHeader(
        status = summary,
        controls = {
            when (loadState) {
                LicenseLoadState.Loading -> CircularProgressIndicator(
                    modifier = Modifier.padding(10.dp),
                    strokeWidth = 2.5.dp,
                )
                is LicenseLoadState.Failed -> TextButton(onClick = onRetry) {
                    Text(stringResource(R.string.common_retry))
                }
                is LicenseLoadState.Ready -> Unit
            }
        },
    ) {}
}
