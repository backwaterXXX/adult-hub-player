package com.adulthub.tv.ui.library

import android.app.Application
import com.adulthub.tv.R
import com.adulthub.tv.data.model.TvCategory
import com.adulthub.tv.data.repository.LibraryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Owns the Categories tab: the genre list and the paged movies of the selected genre.
 */
internal class CategoriesTabController(
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<LibraryUiState>,
    private val libraryRepository: LibraryRepository,
    private val context: Application
) {

    private var categoryMoviesJob: Job? = null

    fun openCategory(categoryId: String) {
        uiState.update { it.copy(selectedTab = LibraryTab.CATEGORIES) }
        scope.launch {
            val categories = libraryRepository.getGenres().getOrDefault(emptyList())
            uiState.update { state ->
                state.copy(
                    categoriesState = state.categoriesState.copy(
                        categories = categories.sortedBy { it.name.lowercase() },
                        isLoading = false,
                        errorMessage = null
                    )
                )
            }
            categories.firstOrNull { it.id == categoryId }?.let(::selectCategory)
        }
    }

    fun loadCategories() {
        scope.launch {
            uiState.update { state ->
                state.copy(categoriesState = state.categoriesState.copy(isLoading = true, errorMessage = null))
            }
            val result = libraryRepository.getGenres()
            uiState.update { state ->
                result.fold(
                    onSuccess = { categories ->
                        state.copy(
                            categoriesState = state.categoriesState.copy(
                                categories = categories.sortedBy { it.name.lowercase() },
                                isLoading = false
                            )
                        )
                    },
                    onFailure = { error ->
                        state.copy(
                            categoriesState = state.categoriesState.copy(
                                isLoading = false,
                                errorMessage = error.localizedMessage ?: context.getString(R.string.library_error_categories)
                            )
                        )
                    }
                )
            }
        }
    }

    fun selectCategory(category: TvCategory?) {
        categoryMoviesJob?.cancel()
        uiState.update { state ->
            state.copy(
                categoriesState = state.categoriesState.copy(
                    selectedCategory = category,
                    lastSelectedCategoryId = category?.id ?: state.categoriesState.lastSelectedCategoryId,
                    categoryMovies = emptyList(),
                    categoryMoviesError = null,
                    isLoadingCategoryMovies = false,
                    isLoadingMoreCategoryMovies = false,
                    hasMoreCategoryMovies = false,
                    totalCount = 0,
                    categoryMoviesOffset = 0
                )
            )
        }
        category?.let { loadCategoryMovies(it, reset = true) }
    }

    fun loadMoreCategoryMovies() {
        val catState = uiState.value.categoriesState
        val category = catState.selectedCategory ?: return
        if (!catState.hasMoreCategoryMovies || catState.isLoadingMoreCategoryMovies || catState.isLoadingCategoryMovies || categoryMoviesJob?.isActive == true) return
        loadCategoryMovies(category, reset = false)
    }

    fun retrySelectedCategoryMovies() {
        val catState = uiState.value.categoriesState
        val category = catState.selectedCategory ?: return
        val isFirstPage = catState.categoryMovies.isEmpty()
        loadCategoryMovies(category, reset = isFirstPage)
    }

    private fun loadCategoryMovies(category: TvCategory, reset: Boolean = true) {
        val currentState = uiState.value.categoriesState
        if (!reset && (!currentState.hasMoreCategoryMovies || currentState.isLoadingMoreCategoryMovies || categoryMoviesJob?.isActive == true)) return
        if (reset) categoryMoviesJob?.cancel()

        categoryMoviesJob = scope.launch {
            uiState.update { state ->
                if (state.categoriesState.selectedCategory?.id == category.id) {
                    state.copy(
                        categoriesState = state.categoriesState.copy(
                            isLoadingCategoryMovies = reset,
                            isLoadingMoreCategoryMovies = !reset,
                            categoryMoviesError = if (reset) null else state.categoriesState.categoryMoviesError
                        )
                    )
                } else {
                    state
                }
            }

            val startIndex = if (reset) 0 else uiState.value.categoriesState.categoryMoviesOffset
            val pageSize = 60
            val result = libraryRepository.getGenreMoviesPage(
                genreName = category.name,
                genreId = category.id,
                startIndex = startIndex,
                limit = pageSize
            )

            uiState.update { state ->
                if (state.categoriesState.selectedCategory?.id != category.id) return@update state
                result.fold(
                    onSuccess = { page ->
                        val combined = if (reset) {
                            page.items
                        } else {
                            state.categoriesState.categoryMovies + page.items
                        }.distinctBy { it.providerVideoId?.ifBlank { null } ?: it.id }

                        val hasMore = page.items.isNotEmpty() && (startIndex + page.items.size < page.totalRecordCount)
                        state.copy(
                            categoriesState = state.categoriesState.copy(
                                categoryMovies = combined,
                                totalCount = page.totalRecordCount,
                                categoryMoviesOffset = page.startIndex + page.items.size,
                                isLoadingCategoryMovies = false,
                                isLoadingMoreCategoryMovies = false,
                                categoryMoviesError = null,
                                hasMoreCategoryMovies = hasMore
                            )
                        )
                    },
                    onFailure = { error ->
                        state.copy(
                            categoriesState = state.categoriesState.copy(
                                isLoadingCategoryMovies = false,
                                isLoadingMoreCategoryMovies = false,
                                categoryMoviesError = error.localizedMessage ?: context.getString(R.string.library_error_category_movies)
                            )
                        )
                    }
                )
            }
        }
    }
}
