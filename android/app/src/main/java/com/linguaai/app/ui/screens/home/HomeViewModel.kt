package com.linguaai.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linguaai.app.data.datastore.SettingsDataStore
import com.linguaai.app.data.remote.dto.LessonSummaryDto
import com.linguaai.app.data.remote.dto.ProgressSummaryDto
import com.linguaai.app.data.repository.ProfileData
import com.linguaai.app.data.repository.RemoteAuthRepository
import com.linguaai.app.domain.model.AppResult
import com.linguaai.app.domain.model.VocabularyCard
import com.linguaai.app.domain.repository.LearningContentRepository
import com.linguaai.app.ui.util.UiMessage
import com.linguaai.app.ui.util.toUiMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = true,
    val profile: ProfileData? = null,
    val languageName: String? = null,
    val dailyGoalMinutes: Int = 20,
    val todayMinutes: Int = 0,
    val streakDays: Int = 0,
    val dueVocabularyCount: Int = 0,
    val continueLesson: LessonSummaryDto? = null,
    val wordOfDay: VocabularyCard? = null,
    val userXp: Int = 120,
    val dailyQuests: List<com.linguaai.app.domain.model.DailyQuest> = emptyList(),
    val error: UiMessage? = null,
    val isOffline: Boolean = false,
)

@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val remoteAuthRepository: RemoteAuthRepository,
        private val learningContentRepository: LearningContentRepository,
        private val progressRepository: com.linguaai.app.domain.repository.ProgressRepository,
        private val settingsDataStore: SettingsDataStore,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(HomeUiState())
        val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                settingsDataStore.userXp.collect { xp ->
                    _uiState.update { it.copy(userXp = xp) }
                }
            }
            viewModelScope.launch {
                settingsDataStore.dailyQuests.collect { quests ->
                    _uiState.update { it.copy(dailyQuests = quests) }
                }
            }
            refresh()
        }

        fun claimQuest(questType: com.linguaai.app.domain.model.DailyQuestType) {
            viewModelScope.launch {
                settingsDataStore.claimQuest(questType)
            }
        }

        fun practiceWordOfDay() {
            viewModelScope.launch {
                settingsDataStore.recordQuestProgress(com.linguaai.app.domain.model.DailyQuestType.WORD_OF_DAY)
            }
        }

        fun toggleFavoriteWordOfDay() {
            val card = _uiState.value.wordOfDay ?: return
            viewModelScope.launch {
                learningContentRepository.toggleFavorite(card.id)
                _uiState.update { state ->
                    state.copy(wordOfDay = state.wordOfDay?.copy(favorite = !card.favorite))
                }
            }
        }

        fun refresh() {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, error = null) }

                var profileFromServer = false
                when (val profileResult = remoteAuthRepository.fetchProfile()) {
                    is AppResult.Success -> {
                        profileFromServer = true
                        _uiState.update { state ->
                            state.copy(
                                profile = profileResult.data,
                                dailyGoalMinutes = profileResult.data.dailyGoalMinutes,
                                isOffline = false,
                            )
                        }
                    }
                    is AppResult.Failure ->
                        _uiState.update { state ->
                            state.copy(
                                error = profileResult.error.toUiMessage(),
                                isOffline = profileResult.error == com.linguaai.app.domain.model.AppError.NetworkUnavailable,
                            )
                        }
                }

                _uiState.value.profile?.languageId?.let { languageId ->
                    when (val languages = learningContentRepository.languages()) {
                        is AppResult.Success ->
                            _uiState.update { state ->
                                state.copy(
                                    languageName = languages.data.firstOrNull { it.id == languageId }?.name,
                                )
                            }
                        is AppResult.Failure -> Unit
                    }
                }

                if (!profileFromServer) {
                    val goal = settingsDataStore.dailyGoalMinutes.first()
                    _uiState.update { it.copy(dailyGoalMinutes = goal) }
                }

                // Streak and today's minutes come from the progress endpoint, the same
                // source the Progress screen renders, so the two views cannot disagree.
                refreshProgress()?.let { progress ->
                    _uiState.update { state ->
                        state.copy(
                            streakDays = progress.streak.current,
                            dueVocabularyCount = progress.vocabulary.dueForReview,
                            todayMinutes = progress.recentActivity.lastOrNull()?.minutes ?: state.todayMinutes,
                        )
                    }
                }

                // Continue learning: the first lesson for the learner's language.
                val languageId = _uiState.value.profile?.languageId
                if (languageId != null) {
                    when (val refresh = learningContentRepository.refreshLessons(languageId, null)) {
                        is AppResult.Failure ->
                            if (_uiState.value.error == null) {
                                _uiState.update { it.copy(error = refresh.error.toUiMessage()) }
                            }
                        else -> Unit
                    }
                    val lessons = learningContentRepository.observeLessons(languageId, null).first()
                    _uiState.update { it.copy(continueLesson = lessons.firstOrNull()) }

                    // Word of the day: deterministic per-date pick from the words the
                    // learner already tracks, so it is stable offline all day long.
                    val tracked = learningContentRepository.observeVocabulary(languageId, null, null, null).first()
                    _uiState.update { it.copy(wordOfDay = wordOfTheDay(tracked, today())) }
                }

                _uiState.update { it.copy(isLoading = false) }
            }
        }

        private suspend fun refreshProgress(): ProgressSummaryDto? =
            when (val progress = progressRepository.refresh()) {
                is AppResult.Success -> progress.data
                is AppResult.Failure -> progressRepository.observeCached().first()
            }
    }

/** Stable per-date pick so the word does not change on every refresh. */
internal fun wordOfTheDay(
    words: List<VocabularyCard>,
    date: java.time.LocalDate,
): VocabularyCard? {
    if (words.isEmpty()) return null
    return words[(date.dayOfYear - 1).mod(words.size)]
}

private fun today(): java.time.LocalDate = java.time.LocalDate.now()
