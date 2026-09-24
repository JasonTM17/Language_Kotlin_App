package com.linguaai.app.ui.screens.vocabulary.saved

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linguaai.app.R
import com.linguaai.app.domain.model.VocabularyCard
import com.linguaai.app.ui.components.EmptyState
import com.linguaai.app.ui.components.LinguaCard
import com.linguaai.app.ui.components.LoadingIndicator
import com.linguaai.app.ui.theme.Spacing

/** Saved words: the learner's starred catalogue entries, offline from Room. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SavedWordsScreen(
    onBack: () -> Unit,
    viewModel: SavedWordsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val tts =
        com.linguaai.app.ui.util
            .rememberLinguaTts(state.languageCode)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.saved_words_title)) },
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
            state.words.isEmpty() ->
                EmptyState(
                    title = stringResource(R.string.vocab_empty_title),
                    message = stringResource(R.string.saved_words_empty_hint),
                    modifier = Modifier.padding(padding),
                )
            else ->
                LazyColumn(
                    modifier = Modifier.padding(padding),
                    contentPadding = PaddingValues(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    items(state.words, key = { it.id }) { word ->
                        SavedWordRow(
                            word = word,
                            onSpeak = {
                                val reading = word.reading?.takeIf { state.languageCode.equals("ja", ignoreCase = true) && it.isNotBlank() }
                                tts.speak(word.word, state.languageCode, reading ?: word.word)
                            },
                        )
                    }
                }
        }
    }
}

@Composable
private fun SavedWordRow(
    word: VocabularyCard,
    onSpeak: () -> Unit,
) {
    LinguaCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(word.word, style = MaterialTheme.typography.titleMedium)
                word.reading?.let { reading ->
                    Text(
                        text = reading,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = word.meaning,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
                Text(
                    text = word.level,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
            IconButton(onClick = onSpeak) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = stringResource(R.string.tts_pronounce),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}
