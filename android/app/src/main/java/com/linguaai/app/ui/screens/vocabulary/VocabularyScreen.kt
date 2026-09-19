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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarOutline
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linguaai.app.R
import com.linguaai.app.domain.model.VocabularyCard
import com.linguaai.app.ui.components.EmptyState
import com.linguaai.app.ui.components.IconTile
import com.linguaai.app.ui.components.LinguaCard
import com.linguaai.app.ui.components.LinguaMascot
import com.linguaai.app.ui.components.LinguaMascotPose
import com.linguaai.app.ui.components.LoadingIndicator
import com.linguaai.app.ui.components.OfflineBanner
import com.linguaai.app.ui.theme.Spacing

@Composable
fun VocabularyScreen(
    onBack: () -> Unit = {},
    viewModel: VocabularyViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = Spacing.sm, end = Spacing.md, top = Spacing.md),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.common_back),
                )
            }
            Column(modifier = Modifier.padding(start = Spacing.xs)) {
                Text(text = stringResource(R.string.vocab_title), style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = stringResource(R.string.vocab_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }
        OfflineBanner(visible = state.isOffline, modifier = Modifier.padding(start = Spacing.md, end = Spacing.md, top = Spacing.sm))

        OutlinedTextField(
            value = state.query,
            onValueChange = { viewModel.onEvent(VocabularyEvent.SearchChanged(it)) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.vocab_search_label)) },
            label = { Text(stringResource(R.string.vocab_search_label)) },
            placeholder = { Text(stringResource(R.string.vocab_search_placeholder)) },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.md),
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            contentPadding = PaddingValues(horizontal = Spacing.md),
        ) {
            items(state.availableLevels) { level ->
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

        when {
            state.isLoading && state.vocabulary.isEmpty() -> LoadingIndicator(modifier = Modifier.weight(1f))
            state.vocabulary.isEmpty() ->
                EmptyState(
                    title =
                        if (state.query.isBlank()) {
                            stringResource(R.string.vocab_empty_title)
                        } else {
                            stringResource(R.string.vocab_empty_no_matches)
                        },
                    message = state.error ?: stringResource(R.string.vocab_empty_hint),
                    actionLabel = stringResource(R.string.common_retry),
                    onAction = viewModel::refresh,
                    modifier = Modifier.weight(1f),
                    art = {
                        LinguaMascot(
                            pose = LinguaMascotPose.Book,
                            contentDescription = stringResource(R.string.mascot_content_description),
                            mascotSize = 96.dp,
                            animated = false,
                        )
                    },
                )
            else ->
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.md),
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
private fun VocabularyRow(
    card: VocabularyCard,
    onToggleFavorite: () -> Unit,
) {
    LinguaCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            modifier = Modifier.padding(Spacing.md),
        ) {
            IconTile(
                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = null,
                modifier = Modifier.padding(end = Spacing.xs),
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = card.word, style = MaterialTheme.typography.titleMedium)
                card.reading?.let {
                    Text(
                        text = it,
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
