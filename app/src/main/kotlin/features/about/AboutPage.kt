// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package features.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import ui.icons.AsteriskIcons as Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import app.LocalIsWideScreen
import app.LocalNavigator
import app.R
import androidx.compose.ui.res.stringResource
import ui.layout.pageContentPaddingWithCutout
import ui.layout.pageListPadding

@Composable
fun AboutPage(
    padding: PaddingValues,
) {
    val isWideScreen = LocalIsWideScreen.current
    val navigator = LocalNavigator.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
                navigationIcon = {
                    IconButton(onClick = { navigator.pop() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
            )
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "about_identity") { AboutIdentityHeader() }
            item(key = "about_runtime") { AboutRuntimeSection() }
            item(key = "about_other") {
                AboutLinksSection(title = stringResource(R.string.about_other))
            }
        }
    }
}
