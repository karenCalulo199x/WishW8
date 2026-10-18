package com.karencalulo.wishwait.presentation.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.karencalulo.wishwait.R

/** One section of the terms: a heading and plain-language text, both from strings.xml. */
private data class TermsSection(@StringRes val title: Int, @StringRes val body: Int)

private val sections = listOf(
    TermsSection(R.string.terms_about_title, R.string.terms_about_body),
    TermsSection(R.string.terms_advice_title, R.string.terms_advice_body),
    TermsSection(R.string.terms_data_title, R.string.terms_data_body),
    TermsSection(R.string.terms_internet_title, R.string.terms_internet_body),
    TermsSection(R.string.terms_notifications_title, R.string.terms_notifications_body),
    TermsSection(R.string.terms_accuracy_title, R.string.terms_accuracy_body),
    TermsSection(R.string.terms_tips_title, R.string.terms_tips_body),
    TermsSection(R.string.terms_use_title, R.string.terms_use_body),
    TermsSection(R.string.terms_liability_title, R.string.terms_liability_body),
    TermsSection(R.string.terms_changes_title, R.string.terms_changes_body),
    TermsSection(R.string.terms_law_title, R.string.terms_law_body),
    TermsSection(R.string.terms_contact_title, R.string.terms_contact_body),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermsScreen(onBack: () -> Unit) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.terms_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.terms_effective),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(stringResource(R.string.terms_intro), style = MaterialTheme.typography.bodyLarge)
                }
            }
            items(sections, key = { it.title }) { section ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(section.title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        stringResource(section.body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
