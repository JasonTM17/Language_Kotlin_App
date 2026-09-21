package com.linguaai.app.ui.screens.grammar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linguaai.app.R
import com.linguaai.app.ui.components.EmptyState
import com.linguaai.app.ui.components.IconTile
import com.linguaai.app.ui.components.LinguaButton
import com.linguaai.app.ui.components.LinguaCard
import com.linguaai.app.ui.components.LoadingIndicator
import com.linguaai.app.ui.components.OfflineBanner
import com.linguaai.app.ui.theme.Spacing
import com.linguaai.app.ui.util.render

@Composable
fun GrammarScreen(
    onOpenGrammar: (Long) -> Unit,
    viewModel: GrammarViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(start = Spacing.md, end = Spacing.md, top = Spacing.lg)) {
            Text(text = stringResource(R.string.grammar_title), style = MaterialTheme.typography.headlineSmall)
            Text(
                text = stringResource(R.string.grammar_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
        OfflineBanner(visible = state.isOffline, modifier = Modifier.padding(start = Spacing.md, end = Spacing.md, top = Spacing.sm))
        when {
            state.isLoading && state.items.isEmpty() -> LoadingIndicator(modifier = Modifier.weight(1f))
            state.items.isEmpty() ->
                EmptyState(
                    title = stringResource(R.string.grammar_empty),
                    message = state.error?.render() ?: stringResource(R.string.grammar_list_empty_hint),
                    actionLabel = stringResource(R.string.common_retry),
                    onAction = viewModel::refresh,
                    modifier = Modifier.weight(1f),
                )
            else ->
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    items(state.items, key = { it.id }) { item ->
                        LinguaCard(onClick = { onOpenGrammar(item.id) }) {
                            Row(
                                modifier = Modifier.padding(Spacing.md),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                            ) {
                                IconTile(
                                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                    contentDescription = null,
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.primary,
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                                    Text(
                                        text = item.meaning.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        modifier = Modifier.padding(top = Spacing.xs),
                                    )
                                }
                            }
                        }
                    }
                }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrammarDetailScreen(
    onBack: () -> Unit,
    onAskAi: (Long) -> Unit,
    viewModel: GrammarDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.grammar?.title ?: stringResource(R.string.grammar_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> LoadingIndicator(modifier = Modifier.padding(padding))
            state.grammar == null ->
                EmptyState(
                    title = stringResource(R.string.grammar_unavailable),
                    message = state.error?.render() ?: stringResource(R.string.grammar_detail_unloaded),
                    modifier = Modifier.padding(padding),
                )
            else ->
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = Spacing.md, vertical = Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    val grammar = state.grammar!!
                    grammar.structure?.let { GrammarSection("Structure", it) }
                    grammar.meaning?.let { GrammarSection("Meaning", it) }
                    grammar.usage?.let { GrammarSection("Usage", it) }
                    if (grammar.examples.isNotEmpty()) {
                        Text(
                            stringResource(R.string.grammar_examples),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = Spacing.md),
                        )
                        grammar.examples.forEach { example ->
                            LinguaCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                                Column(modifier = Modifier.padding(Spacing.md)) {
                                    Text(example.sentence, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        text = example.translation,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = Spacing.xs),
                                    )
                                }
                            }
                        }
                    }
                    grammar.notes?.let { GrammarSection("Notes", it) }
                    LinguaButton(
                        text = stringResource(R.string.grammar_ask_tutor),
                        onClick = { onAskAi(grammar.id) },
                        modifier = Modifier.padding(top = Spacing.md),
                    )
                }
        }
    }
}

@Composable
private fun GrammarSection(
    title: String,
    body: String,
) {
    LinguaCard {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }
    }
}
