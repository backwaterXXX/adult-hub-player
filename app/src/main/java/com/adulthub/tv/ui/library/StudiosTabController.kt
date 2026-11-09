package com.adulthub.tv.ui.library

import android.app.Application
import com.adulthub.tv.R
import com.adulthub.tv.data.model.TvStudio
import com.adulthub.tv.data.repository.StudioRepository
import com.adulthub.tv.data.repository.TvItemsPage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Owns the Studios tab: the studio list and the paged movies of the selected studio.
 */
internal class StudiosTabController(
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<LibraryUiState>,
    private val studioRepository: StudioRepository,
    private val context: Application
) {

    private var studioMoviesJob: Job? = null

    fun loadStudios() {
        scope.launch {
            uiState.update { state ->
                state.copy(studiosState = state.studiosState.copy(isLoading = true, errorMessage = null))
            }
            val result = studioRepository.getStudios(limit = 100, includeMovieCounts = true)
            uiState.update { state ->
                result.fold(
                    onSuccess = { studios ->
                        state.copy(
                            studiosState = state.studiosState.copy(
                                studios = studios.sortedBy { it.name.lowercase() },
                                isLoading = false
                            )
                        )
                    },
                    onFailure = { error ->
                        state.copy(
                            studiosState = state.studiosState.copy(
                                isLoading = false,
                                errorMessage = error.localizedMessage ?: context.getString(R.string.library_error_studios)
                            )
                        )
                    }
                )
            }
        }
    }

    fun selectStudio(studio: TvStudio?) {
        studioMoviesJob?.cancel()
        uiState.update { state ->
            state.copy(
                studiosState = state.studiosState.copy(
                    selectedStudio = studio,
                    lastSelectedStudioId = studio?.id ?: state.studiosState.lastSelectedStudioId,
                    studioMovies = emptyList(),
                    studioMoviesError = null,
                    isLoadingStudioMovies = false,
                    isLoadingMoreStudioMovies = false,
                    hasMoreStudioMovies = false,
                    totalCount = 0,
                    studioMoviesOffset = 0
                )
            )
        }
        studio?.let { loadStudioMovies(it, reset = true) }
    }

    fun loadMoreStudioMovies() {
        val stdState = uiState.value.studiosState
        val studio = stdState.selectedStudio ?: return
        if (!stdState.hasMoreStudioMovies || stdState.isLoadingMoreStudioMovies || stdState.isLoadingStudioMovies || studioMoviesJob?.isActive == true) return
        loadStudioMovies(studio, reset = false)
    }

    fun retrySelectedStudioMovies() {
        val stdState = uiState.value.studiosState
        val studio = stdState.selectedStudio ?: return
        val isFirstPage = stdState.studioMovies.isEmpty()
        loadStudioMovies(studio, reset = isFirstPage)
    }

    private fun loadStudioMovies(studio: TvStudio, reset: Boolean = true) {
        val currentState = uiState.value.studiosState
        if (!reset && (!currentState.hasMoreStudioMovies || currentState.isLoadingMoreStudioMovies || studioMoviesJob?.isActive == true)) return
        if (reset) studioMoviesJob?.cancel()

        studioMoviesJob = scope.launch {
            uiState.update { state -> state.withStudioMoviesLoading(studio, reset) }

            val startIndex = if (reset) 0 else uiState.value.studiosState.studioMoviesOffset
            val pageSize = 50
            val result = studioRepository.getStudioMoviesPage(
                studioName = studio.name,
                startIndex = startIndex,
                limit = pageSize
            )

            uiState.update { state -> state.withStudioMoviesResult(studio, result, reset, startIndex) }
        }
    }

    private fun LibraryUiState.withStudioMoviesLoading(studio: TvStudio, reset: Boolean): LibraryUiState {
        if (studiosState.selectedStudio?.id != studio.id) return this
        return copy(
            studiosState = studiosState.copy(
                isLoadingStudioMovies = reset,
                isLoadingMoreStudioMovies = !reset,
                studioMoviesError = if (reset) null else studiosState.studioMoviesError
            )
        )
    }

    private fun LibraryUiState.withStudioMoviesResult(
        studio: TvStudio,
        result: Result<TvItemsPage>,
        reset: Boolean,
        startIndex: Int
    ): LibraryUiState {
        if (studiosState.selectedStudio?.id != studio.id) return this
        return result.fold(
            onSuccess = { page ->
                val combined = if (reset) {
                    page.items
                } else {
                    studiosState.studioMovies + page.items
                }.distinctBy { it.providerVideoId?.ifBlank { null } ?: it.id }

                val hasMore = page.items.isNotEmpty() && (startIndex + page.items.size < page.totalRecordCount)
                copy(
                    studiosState = studiosState.copy(
                        studioMovies = combined,
                        totalCount = page.totalRecordCount,
                        studioMoviesOffset = page.startIndex + page.items.size,
                        isLoadingStudioMovies = false,
                        isLoadingMoreStudioMovies = false,
                        studioMoviesError = null,
                        hasMoreStudioMovies = hasMore
                    )
                )
            },
            onFailure = { error ->
                copy(
                    studiosState = studiosState.copy(
                        isLoadingStudioMovies = false,
                        isLoadingMoreStudioMovies = false,
                        studioMoviesError = error.localizedMessage ?: context.getString(R.string.library_error_studio_movies)
                    )
                )
            }
        )
    }
}
