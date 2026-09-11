package com.linguaai.app.ui.screens.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linguaai.app.data.remote.dto.ProgressSummaryDto
import com.linguaai.app.domain.model.AppResult
import com.linguaai.app.domain.repository.ProgressRepository
import com.linguaai.app.ui.util.toUserMessage
import com.linguaai.app.util.NetworkMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProgressUiState(
    val isLoading: Boolean = true,
    val summary: ProgressSummaryDto? = null,
    val error: String? = null,
    /** True when the shown data came from the cache after a failed refresh. */
    val isStale: Boolean = false,
) {
    /** Nothing recorded yet — distinct from "still loading" and from "failed". */
    val isEmpty: Boolean
        get() = summary != null &&
            summary.totals.activeDays == 0 &&
            summary.totals.quizAttempts == 0 &&
            summary.vocabulary.tracked == 0
}

@HiltViewModel
class ProgressViewModel @Inject constructor(
    private val progressRepository: ProgressRepository,
    networkMonitor: NetworkMonitor,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProgressUiState())
    val uiState: StateFlow<ProgressUiState> = _uiState.asStateFlow()

    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline

    init {
        observeCache()
        refresh()
    }

    /**
     * Renders whatever was cached immediately, so the screen is never blank while
     * the network call is in flight or when the device is offline.
     */
    private fun observeCache() {
        viewModelScope.launch {
            progressRepository.observeCached().collect { cached ->
                if (cached != null) {
                    _uiState.update { it.copy(summary = cached, isLoading = false) }
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = it.summary == null, error = null) }
            when (val result = progressRepository.refresh()) {
                is AppResult.Success -> _uiState.update {
                    it.copy(isLoading = false, summary = result.data, error = null, isStale = false)
                }
                is AppResult.Failure -> _uiState.update {
                    // Keep cached data; only surface an error when there is nothing to show.
                    it.copy(
                        isLoading = false,
                        error = if (it.summary == null) result.error.toUserMessage() else null,
                        isStale = it.summary != null,
                    )
                }
            }
        }
    }
}
