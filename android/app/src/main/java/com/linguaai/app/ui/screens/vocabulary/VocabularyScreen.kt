package com.linguaai.app.ui.screens.vocabulary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linguaai.app.domain.model.VocabularyCard
import com.linguaai.app.ui.components.EmptyState
import com.linguaai.app.ui.components.LinguaCard
import com.linguaai.app.ui.components.LoadingIndicator
import com.linguaai.app.ui.components.OfflineBanner
import com.linguaai.app.ui.theme.Spacing

@Composable
fun VocabularyScreen(
    onOpenFlashcards: () -> Unit,
    viewModel: VocabularyViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Vocabulary",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.md),
        )
        OfflineBanner(visible = state.isOffline, modifier = Modifier.padding(horizontal = Spacing.md))

        OutlinedTextField(
            value = state.query,
            onValueChange = { viewModel.onEvent(VocabularyEvent.SearchChanged(it)) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            placeholder = { Text("Search word, reading or meaning") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = Spacing.md),
        ) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.weight(1f),
            ) {
                items(listOf("N5", "N4", "N3", "A1", "A2", "B1")) { level ->
                    FilterChip(
                        selected = state.selectedLevel == level,
                        onClick = {
                            viewModel.onEvent(
                                VocabularyEvent.LevelSelected(if (state.selectedLevel == level) null else level),
                            )
                        },
                        label = { Text(level) },
                    )
                }
            }
        }

        when {
            state.isLoading && state.vocabulary.isEmpty() -> LoadingIndicator()
            state.vocabulary.isEmpty() -> EmptyState(
                title = if (state.query.isBlank()) "No vocabulary yet" else "No matches",
                message = state.error ?: "Try a different search or level filter.",
                actionLabel = "Retry",
                onAction = viewModel::refresh,
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(state.vocabulary, key = { it.id }) { card ->
                    VocabularyRow(card = card, onToggleFavorite = {
                        viewModel.onEvent(VocabularyEvent.ToggleFavorite(card.id))
                    })
                }
            }
        }
    }
}

@Composable
private fun VocabularyRow(card: VocabularyCard, onToggleFavorite: () -> Unit) {
    LinguaCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(Spacing.md),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = card.word, style = MaterialTheme.typography.titleMedium)
                if (card.reading != null) {
                    Text(
                        text = card.reading,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = card.meaning,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (card.favorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    contentDescription = if (card.favorite) "Remove favorite" else "Add favorite",
                    tint = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}
