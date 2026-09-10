package com.linguaai.app.ui.screens.grammar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linguaai.app.ui.components.EmptyState
import com.linguaai.app.ui.components.LinguaCard
import com.linguaai.app.ui.components.LoadingIndicator
import com.linguaai.app.ui.components.OfflineBanner
import com.linguaai.app.ui.theme.Spacing

@Composable
fun GrammarScreen(
    onOpenGrammar: (Long) -> Unit,
    viewModel: GrammarViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Grammar",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.md),
        )
        OfflineBanner(visible = state.isOffline, modifier = Modifier.padding(horizontal = Spacing.md))
        when {
            state.isLoading && state.items.isEmpty() -> LoadingIndicator()
            state.items.isEmpty() -> EmptyState(
                title = "No grammar lessons yet",
                message = state.error ?: "Grammar points for your level will appear here.",
                actionLabel = "Retry",
                onAction = viewModel::refresh,
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(state.items, key = { it.id }) { item ->
                    LinguaCard(onClick = { onOpenGrammar(item.id) }) {
                        Column(modifier = Modifier.padding(Spacing.md)) {
                            Text(item.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = item.meaning.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                            )
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
                title = { Text(state.grammar?.title ?: "Grammar") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> LoadingIndicator()
            state.grammar == null -> EmptyState(
                title = "Unavailable",
                message = state.error ?: "This grammar point could not be loaded.",
            )
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.md),
            ) {
                val grammar = state.grammar!!
                if (grammar.structure != null) {
                    Text("Structure", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = grammar.structure,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.md),
                    )
                }
                if (grammar.meaning != null) {
                    Text("Meaning", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = grammar.meaning,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.md),
                    )
                }
                if (grammar.usage != null) {
                    Text("Usage", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = grammar.usage,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.md),
                    )
                }
                if (grammar.examples.isNotEmpty()) {
                    Text("Examples", style = MaterialTheme.typography.titleSmall)
                    grammar.examples.forEach { example ->
                        Text(
                            text = example.sentence,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(top = Spacing.sm),
                        )
                        Text(
                            text = example.translation,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (grammar.notes != null) {
                    Text("Notes", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = Spacing.md))
                    Text(
                        text = grammar.notes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
                com.linguaai.app.ui.components.LinguaButton(
                    text = "Ask AI to explain",
                    onClick = { onAskAi(grammar.id) },
                    modifier = Modifier.padding(top = Spacing.lg),
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(bottom = Spacing.xl))
            }
        }
    }
}
