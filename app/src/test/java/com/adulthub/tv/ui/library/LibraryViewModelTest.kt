package com.adulthub.tv.ui.library

import com.adulthub.tv.data.jellyfin.JellyfinClientManager
import com.adulthub.tv.data.model.ChannelSortMode
import com.adulthub.tv.data.model.DurationFilter
import com.adulthub.tv.data.model.TvChannel
import com.adulthub.tv.data.model.TvCategory
import com.adulthub.tv.data.model.TvItem
import com.adulthub.tv.data.model.TvPerson
import com.adulthub.tv.data.model.TvStudio
import com.adulthub.tv.data.repository.ChannelHistoryPage
import com.adulthub.tv.data.repository.ChannelRepository
import com.adulthub.tv.data.repository.LibraryRepository
import com.adulthub.tv.data.repository.PersonRepository
import com.adulthub.tv.data.repository.StudioRepository
import com.adulthub.tv.data.repository.TvItemsPage
import com.adulthub.tv.data.repository.TvPersonPage
import com.adulthub.tv.data.resolver.VideoDto
import io.mockk.mockk
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.SortOrder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val fakeLibraryRepository = FakeLibraryRepository()
    private val fakePersonRepository = FakePersonRepository()
    private val fakeStudioRepository = FakeStudioRepository()
    private val fakeChannelRepository = FakeChannelRepository()
    private val clientManager: JellyfinClientManager = mockk(relaxed = true)

    private val sampleMovie1 = TvItem(id = "movie-1", title = "Alpha Movie", isFavorite = true)
    private val sampleMovie2 = TvItem(id = "movie-2", title = "Beta Movie", isFavorite = false)

    private val personA = TvPerson(id = "p-1", name = "Angela White", primaryImageTag = "tag-1")
    private val personB = TvPerson(id = "p-2", name = "Blake Blossom", primaryImageTag = null)

    private val sampleStudio = TvStudio(id = "s-1", name = "Brazzers", movieCount = 50)
    private val sampleChannel = TvChannel(id = "c-1", title = "Hot Telegram Channel", slug = "hot_tg", url = "https://t.me/hot")

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeLibraryRepository.itemsPageResult = Result.success(
            TvItemsPage(items = listOf(sampleMovie1, sampleMovie2), totalRecordCount = 10, startIndex = 0)
        )
        fakePersonRepository.personsResult = Result.success(listOf(personA, personB))
        fakeStudioRepository.studiosResult = Result.success(listOf(sampleStudio))
        fakeStudioRepository.studioMoviesResult = Result.success(listOf(sampleMovie1))
        fakeChannelRepository.categoriesResult = Result.success(listOf(sampleChannel))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init loads All Movies tab items and total count`() = runTest(testDispatcher) {
        val viewModel = LibraryViewModel(
            libraryRepository = fakeLibraryRepository,
            personRepository = fakePersonRepository,
            studioRepository = fakeStudioRepository,
            channelRepository = fakeChannelRepository,
            clientManager = clientManager,
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(LibraryTab.ALL, state.selectedTab)
        assertFalse(state.allState.isLoading)
        assertEquals(2, state.allState.items.size)
        assertEquals(10, state.allState.totalCount)
        assertTrue(state.allState.hasMore)
    }

    @Test
    fun `selectTab switches tab and lazily loads tab data`() = runTest(testDispatcher) {
        val viewModel = LibraryViewModel(
            libraryRepository = fakeLibraryRepository,
            personRepository = fakePersonRepository,
            studioRepository = fakeStudioRepository,
            channelRepository = fakeChannelRepository,
            clientManager = clientManager,
        )

        advanceUntilIdle()

        // Switch to ACTORS
        viewModel.selectTab(LibraryTab.ACTORS)
        advanceUntilIdle()

        val actorsState = viewModel.uiState.value.actorsState
        assertFalse(actorsState.isLoading)
        assertEquals(2, actorsState.allPersons.size)
        assertEquals(2, actorsState.filteredPersons.size)

        // Switch to STUDIOS
        viewModel.selectTab(LibraryTab.STUDIOS)
        advanceUntilIdle()

        val studiosState = viewModel.uiState.value.studiosState
        assertFalse(studiosState.isLoading)
        assertEquals(1, studiosState.studios.size)
        assertEquals("Brazzers", studiosState.studios.first().name)

        // Switch to CHANNELS
        viewModel.selectTab(LibraryTab.CHANNELS)
        advanceUntilIdle()

        val channelsState = viewModel.uiState.value.channelsState
        assertFalse(channelsState.isLoading)
        assertEquals(1, channelsState.channels.size)
        assertEquals("Hot Telegram Channel", channelsState.channels.first().title)
    }

    @Test
    fun `selecting channel loads category videos`() = runTest(testDispatcher) {
        val expectedVideo = VideoDto(id = "video-1", title = "Video One")
        fakeChannelRepository.categoryVideosResult = Result.success(listOf(expectedVideo))
        val viewModel = LibraryViewModel(
            libraryRepository = fakeLibraryRepository,
            personRepository = fakePersonRepository,
            studioRepository = fakeStudioRepository,
            channelRepository = fakeChannelRepository,
            clientManager = clientManager,
        )

        advanceUntilIdle()
        viewModel.selectChannel(sampleChannel)
        advanceUntilIdle()

        val channelsState = viewModel.uiState.value.channelsState
        assertEquals(listOf(expectedVideo), channelsState.channelVideos)
        assertEquals(1, fakeChannelRepository.categoryVideoRequests)
        assertEquals(null, channelsState.channelVideosError)
        assertFalse(channelsState.isLoadingVideos)
    }

    @Test
    fun `channel video request failure is shown as error instead of empty results`() = runTest(testDispatcher) {
        fakeChannelRepository.categoryVideosResult = Result.failure(IllegalStateException("HTTP 502"))
        val viewModel = LibraryViewModel(
            libraryRepository = fakeLibraryRepository,
            personRepository = fakePersonRepository,
            studioRepository = fakeStudioRepository,
            channelRepository = fakeChannelRepository,
            clientManager = clientManager,
        )

        advanceUntilIdle()
        viewModel.selectChannel(sampleChannel)
        advanceUntilIdle()

        val channelsState = viewModel.uiState.value.channelsState
        assertTrue(channelsState.channelVideos.isEmpty())
        assertEquals("HTTP 502", channelsState.channelVideosError)
        assertFalse(channelsState.isLoadingVideos)
    }

    @Test
    fun `actor filtering by alphabet letter and photo works correctly`() = runTest(testDispatcher) {
        val viewModel = LibraryViewModel(
            libraryRepository = fakeLibraryRepository,
            personRepository = fakePersonRepository,
            studioRepository = fakeStudioRepository,
            channelRepository = fakeChannelRepository,
            clientManager = clientManager,
        )

        viewModel.selectTab(LibraryTab.ACTORS)
        advanceUntilIdle()

        // Filter by 'A'
        viewModel.selectAlphabetLetter('A')
        assertEquals(1, viewModel.uiState.value.actorsState.filteredPersons.size)
        assertEquals("Angela White", viewModel.uiState.value.actorsState.filteredPersons.first().name)

        // Toggling 'A' again resets to all
        viewModel.selectAlphabetLetter('A')
        assertEquals(2, viewModel.uiState.value.actorsState.filteredPersons.size)

        // Filter only with photo
        viewModel.toggleOnlyWithPhoto()
        assertEquals(1, viewModel.uiState.value.actorsState.filteredPersons.size)
        assertEquals("Angela White", viewModel.uiState.value.actorsState.filteredPersons.first().name)
    }

    @Test
    fun `all items tab pagination appends items`() = runTest(testDispatcher) {
        val viewModel = LibraryViewModel(
            libraryRepository = fakeLibraryRepository,
            personRepository = fakePersonRepository,
            studioRepository = fakeStudioRepository,
            channelRepository = fakeChannelRepository,
            clientManager = clientManager,
        )

        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.allState.items.size)

        // Setup page 2
        val movie3 = TvItem(id = "movie-3", title = "Gamma Movie")
        fakeLibraryRepository.itemsPageResult = Result.success(
            TvItemsPage(items = listOf(movie3), totalRecordCount = 10, startIndex = 2)
        )

        viewModel.loadAllItems(reset = false)
        advanceUntilIdle()

        assertEquals(3, viewModel.uiState.value.allState.items.size)
        assertEquals("movie-3", viewModel.uiState.value.allState.items.last().id)
    }

    @Test
    fun `setSort resets pagination and updates sort parameters`() = runTest(testDispatcher) {
        val viewModel = LibraryViewModel(
            libraryRepository = fakeLibraryRepository,
            personRepository = fakePersonRepository,
            studioRepository = fakeStudioRepository,
            channelRepository = fakeChannelRepository,
            clientManager = clientManager,
        )

        advanceUntilIdle()

        viewModel.setSort(sortBy = "SortName", sortOrder = SortOrder.ASCENDING)
        advanceUntilIdle()

        assertEquals("SortName", viewModel.uiState.value.allState.sortBy)
        assertEquals(SortOrder.ASCENDING, viewModel.uiState.value.allState.sortOrder)
    }

    @Test
    fun `catalog filters are loaded on demand and passed to the item query`() = runTest(testDispatcher) {
        val viewModel = LibraryViewModel(
            libraryRepository = fakeLibraryRepository,
            personRepository = fakePersonRepository,
            studioRepository = fakeStudioRepository,
            channelRepository = fakeChannelRepository,
            clientManager = clientManager,
        )
        advanceUntilIdle()

        assertEquals(0, fakeLibraryRepository.genreRequestCount)
        viewModel.loadGenresForFilter()
        advanceUntilIdle()
        assertEquals(1, fakeLibraryRepository.genreRequestCount)

        val genreId = "00000000-0000-0000-0000-000000000001"
        viewModel.selectGenre(TvCategory(id = genreId, name = "Action"))
        viewModel.toggleFavoritesOnly()
        viewModel.toggleUnwatchedOnly()
        advanceUntilIdle()

        assertEquals(listOf(UUID.fromString(genreId)), fakeLibraryRepository.lastGenreIds)
        assertTrue(ItemFilter.IS_FAVORITE in fakeLibraryRepository.lastFilters)
        assertTrue(ItemFilter.IS_UNPLAYED in fakeLibraryRepository.lastFilters)

        fakeLibraryRepository.randomResult = Result.success(sampleMovie1)
        viewModel.onSurpriseMeClicked()
        advanceUntilIdle()
        assertEquals(listOf(UUID.fromString(genreId)), fakeLibraryRepository.randomGenreIds)
        assertTrue(ItemFilter.IS_FAVORITE in fakeLibraryRepository.randomFilters)
        assertTrue(ItemFilter.IS_UNPLAYED in fakeLibraryRepository.randomFilters)
    }

    @Test
    fun `selectStudio loads studio movies`() = runTest(testDispatcher) {
        val viewModel = LibraryViewModel(
            libraryRepository = fakeLibraryRepository,
            personRepository = fakePersonRepository,
            studioRepository = fakeStudioRepository,
            channelRepository = fakeChannelRepository,
            clientManager = clientManager,
        )

        viewModel.selectTab(LibraryTab.STUDIOS)
        advanceUntilIdle()

        viewModel.selectStudio(sampleStudio)
        advanceUntilIdle()

        val studioState = viewModel.uiState.value.studiosState
        assertEquals(sampleStudio, studioState.selectedStudio)
        assertEquals(1, studioState.studioMovies.size)
        assertEquals("movie-1", studioState.studioMovies.first().id)
    }

    @Test
    fun `selectCategory loads first page and supports pagination`() = runTest(testDispatcher) {
        val category = TvCategory(id = "cat-1", name = "POV", movieCount = 100)
        val catMovie1 = TvItem(id = "cm-1", title = "Category Movie 1")
        val catMovie2 = TvItem(id = "cm-2", title = "Category Movie 2")
        val catMovie3 = TvItem(id = "cm-3", title = "Category Movie 3")

        fakeLibraryRepository.genreMoviesPageResult = Result.success(
            TvItemsPage(items = listOf(catMovie1, catMovie2), totalRecordCount = 3, startIndex = 0)
        )

        val viewModel = LibraryViewModel(
            libraryRepository = fakeLibraryRepository,
            personRepository = fakePersonRepository,
            studioRepository = fakeStudioRepository,
            channelRepository = fakeChannelRepository,
            clientManager = clientManager,
        )

        viewModel.selectCategory(category)
        advanceUntilIdle()

        val stateFirstPage = viewModel.uiState.value.categoriesState
        assertEquals(category, stateFirstPage.selectedCategory)
        assertEquals(2, stateFirstPage.categoryMovies.size)
        assertEquals(3, stateFirstPage.totalCount)
        assertTrue(stateFirstPage.hasMoreCategoryMovies)
        assertFalse(stateFirstPage.isLoadingCategoryMovies)
        assertFalse(stateFirstPage.isLoadingMoreCategoryMovies)

        fakeLibraryRepository.genreMoviesPageResult = Result.success(
            TvItemsPage(items = listOf(catMovie3), totalRecordCount = 3, startIndex = 2)
        )

        viewModel.loadMoreCategoryMovies()
        advanceUntilIdle()

        val stateSecondPage = viewModel.uiState.value.categoriesState
        assertEquals(3, stateSecondPage.categoryMovies.size)
        assertEquals("cm-3", stateSecondPage.categoryMovies.last().id)
        assertFalse(stateSecondPage.hasMoreCategoryMovies)
        assertFalse(stateSecondPage.isLoadingMoreCategoryMovies)
    }

    @Test
    fun `category pagination advances by server records after removing duplicates`() = runTest(testDispatcher) {
        val category = TvCategory(id = "cat-duplicates", name = "Drama")
        val firstPage = listOf(
            TvItem(id = "same-a", title = "Same A", providerVideoId = "provider-1"),
            TvItem(id = "same-b", title = "Same B", providerVideoId = "provider-1"),
        )
        fakeLibraryRepository.genreMoviesPageResult = Result.success(
            TvItemsPage(items = firstPage, totalRecordCount = 4, startIndex = 0)
        )
        val viewModel = LibraryViewModel(
            libraryRepository = fakeLibraryRepository,
            personRepository = fakePersonRepository,
            studioRepository = fakeStudioRepository,
            channelRepository = fakeChannelRepository,
            clientManager = clientManager,
        )

        viewModel.selectCategory(category)
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.categoriesState.categoryMovies.size)

        fakeLibraryRepository.genreMoviesPageResult = Result.success(
            TvItemsPage(
                items = listOf(TvItem(id = "page-two", title = "Page Two"), TvItem(id = "page-three", title = "Page Three")),
                totalRecordCount = 4,
                startIndex = 2,
            )
        )
        viewModel.loadMoreCategoryMovies()
        advanceUntilIdle()

        assertEquals(listOf(0, 2), fakeLibraryRepository.genreMoviesStartIndexes)
        assertEquals(3, viewModel.uiState.value.categoriesState.categoryMovies.size)
    }

    @Test
    fun `selectStudio supports pagination and load more`() = runTest(testDispatcher) {
        val stdMovie1 = TvItem(id = "sm-1", title = "Studio Movie 1")
        val stdMovie2 = TvItem(id = "sm-2", title = "Studio Movie 2")
        val stdMovie3 = TvItem(id = "sm-3", title = "Studio Movie 3")

        fakeStudioRepository.studioMoviesPageResult = Result.success(
            TvItemsPage(items = listOf(stdMovie1, stdMovie2), totalRecordCount = 3, startIndex = 0)
        )

        val viewModel = LibraryViewModel(
            libraryRepository = fakeLibraryRepository,
            personRepository = fakePersonRepository,
            studioRepository = fakeStudioRepository,
            channelRepository = fakeChannelRepository,
            clientManager = clientManager,
        )

        viewModel.selectStudio(sampleStudio)
        advanceUntilIdle()

        val stateFirstPage = viewModel.uiState.value.studiosState
        assertEquals(sampleStudio, stateFirstPage.selectedStudio)
        assertEquals(2, stateFirstPage.studioMovies.size)
        assertEquals(3, stateFirstPage.totalCount)
        assertTrue(stateFirstPage.hasMoreStudioMovies)
        assertFalse(stateFirstPage.isLoadingStudioMovies)
        assertFalse(stateFirstPage.isLoadingMoreStudioMovies)

        fakeStudioRepository.studioMoviesPageResult = Result.success(
            TvItemsPage(items = listOf(stdMovie3), totalRecordCount = 3, startIndex = 2)
        )

        viewModel.loadMoreStudioMovies()
        advanceUntilIdle()

        val stateSecondPage = viewModel.uiState.value.studiosState
        assertEquals(3, stateSecondPage.studioMovies.size)
        assertEquals("sm-3", stateSecondPage.studioMovies.last().id)
        assertFalse(stateSecondPage.hasMoreStudioMovies)
        assertFalse(stateSecondPage.isLoadingMoreStudioMovies)
    }

    @Test
    fun `channel history pagination loads first page and appends subsequent pages`() = runTest(testDispatcher) {
        val video1 = VideoDto(id = "tg-1", title = "Telegram Video 1")
        val video2 = VideoDto(id = "tg-2", title = "Telegram Video 2")
        val video3 = VideoDto(id = "tg-3", title = "Telegram Video 3")

        fakeChannelRepository.channelHistoryResult = Result.success(
            ChannelHistoryPage(
                videos = listOf(video1, video2),
                nextOffsetId = 500L,
                hasMore = true,
                messageCount = 2,
            )
        )

        val viewModel = LibraryViewModel(
            libraryRepository = fakeLibraryRepository,
            personRepository = fakePersonRepository,
            studioRepository = fakeStudioRepository,
            channelRepository = fakeChannelRepository,
            clientManager = clientManager,
        )

        advanceUntilIdle()
        viewModel.selectChannel(sampleChannel)
        advanceUntilIdle()

        val stateFirstPage = viewModel.uiState.value.channelsState
        assertEquals(listOf(video1, video2), stateFirstPage.channelVideos)
        assertTrue(stateFirstPage.hasMoreVideos)
        assertEquals(500L, stateFirstPage.nextOffsetId)
        assertFalse(stateFirstPage.isLoadingVideos)
        assertFalse(stateFirstPage.isLoadingMoreVideos)
        assertEquals(null, fakeChannelRepository.lastRequestedOffsetId)

        // Load next page
        fakeChannelRepository.channelHistoryResult = Result.success(
            ChannelHistoryPage(
                videos = listOf(video3),
                nextOffsetId = 200L,
                hasMore = false,
                messageCount = 1,
            )
        )

        viewModel.loadMoreChannelVideos()
        advanceUntilIdle()

        val stateSecondPage = viewModel.uiState.value.channelsState
        assertEquals(listOf(video1, video2, video3), stateSecondPage.channelVideos)
        assertFalse(stateSecondPage.hasMoreVideos)
        assertEquals(200L, stateSecondPage.nextOffsetId)
        assertFalse(stateSecondPage.isLoadingMoreVideos)
        assertEquals(500L, fakeChannelRepository.lastRequestedOffsetId)
    }

    @Test
    fun `channel filtering automatically advances when a page has no visible videos`() = runTest(testDispatcher) {
        val shortVideo = VideoDto(id = "short", title = "Short video", duration = 30.0)
        val longVideo = VideoDto(id = "long", title = "Long video", duration = 3_600.0)
        fakeChannelRepository.channelHistoryResults[null] = Result.success(
            ChannelHistoryPage(listOf(shortVideo), 500L, true, 2)
        )
        fakeChannelRepository.channelHistoryResults[500L] = Result.success(
            ChannelHistoryPage(listOf(longVideo), null, false, 1)
        )
        val viewModel = LibraryViewModel(
            libraryRepository = fakeLibraryRepository,
            personRepository = fakePersonRepository,
            studioRepository = fakeStudioRepository,
            channelRepository = fakeChannelRepository,
            clientManager = clientManager,
        )
        viewModel.setChannelDurationFilter(com.adulthub.tv.data.model.DurationFilter.LONG)

        viewModel.selectChannel(sampleChannel)
        advanceUntilIdle()

        assertEquals(2, fakeChannelRepository.channelHistoryRequests)
        assertEquals(listOf(longVideo), viewModel.uiState.value.channelsState.channelVideos)
    }

    @Test
    fun `loadMoreChannelVideos failure preserves existing videos and sets error`() = runTest(testDispatcher) {
        val video1 = VideoDto(id = "tg-1", title = "Telegram Video 1")

        fakeChannelRepository.channelHistoryResult = Result.success(
            ChannelHistoryPage(
                videos = listOf(video1),
                nextOffsetId = 500L,
                hasMore = true,
                messageCount = 1,
            )
        )

        val viewModel = LibraryViewModel(
            libraryRepository = fakeLibraryRepository,
            personRepository = fakePersonRepository,
            studioRepository = fakeStudioRepository,
            channelRepository = fakeChannelRepository,
            clientManager = clientManager,
        )

        advanceUntilIdle()
        viewModel.selectChannel(sampleChannel)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.channelsState.channelVideos.size)

        // Simulate failure on loading more
        fakeChannelRepository.channelHistoryResult = Result.failure(IllegalStateException("Network timeout"))

        viewModel.loadMoreChannelVideos()
        advanceUntilIdle()

        val stateAfterFailure = viewModel.uiState.value.channelsState
        assertEquals(listOf(video1), stateAfterFailure.channelVideos)
        assertEquals("Network timeout", stateAfterFailure.channelVideosError)
        assertFalse(stateAfterFailure.isLoadingMoreVideos)
    }

    @Test
    fun `setChannelSortMode sorts rawChannelVideos properly`() = runTest(testDispatcher) {
        val shortVideo = VideoDto(id = "tg-1", title = "Short", duration = 60.0)
        val longVideo = VideoDto(id = "tg-2", title = "Long", duration = 3600.0)
        val midVideo = VideoDto(id = "tg-3", title = "Mid", duration = 600.0)

        fakeChannelRepository.channelHistoryResult = Result.success(
            ChannelHistoryPage(
                videos = listOf(shortVideo, longVideo, midVideo),
                nextOffsetId = null,
                hasMore = false,
                messageCount = 3,
            )
        )

        val viewModel = LibraryViewModel(
            libraryRepository = fakeLibraryRepository,
            personRepository = fakePersonRepository,
            studioRepository = fakeStudioRepository,
            channelRepository = fakeChannelRepository,
            clientManager = clientManager,
        )
        advanceUntilIdle()
        viewModel.selectChannel(sampleChannel)
        advanceUntilIdle()

        // Default LATEST keeps insertion order
        assertEquals(listOf(shortVideo, longVideo, midVideo), viewModel.uiState.value.channelsState.channelVideos)

        // LONGEST
        viewModel.setChannelSortMode(ChannelSortMode.LONGEST)
        assertEquals(listOf(longVideo, midVideo, shortVideo), viewModel.uiState.value.channelsState.channelVideos)

        // SHORTEST
        viewModel.setChannelSortMode(ChannelSortMode.SHORTEST)
        assertEquals(listOf(shortVideo, midVideo, longVideo), viewModel.uiState.value.channelsState.channelVideos)
    }

    @Test
    fun `channel history deduplicates videos by providerVideoId`() = runTest(testDispatcher) {
        val video1 = VideoDto(id = "tg-1", title = "Video 1", providerVideoId = "pv-100")
        val video2Dup = VideoDto(id = "tg-2", title = "Video 1 Dup", providerVideoId = "pv-100")
        val video3 = VideoDto(id = "tg-3", title = "Video 3", providerVideoId = "pv-200")

        fakeChannelRepository.channelHistoryResult = Result.success(
            ChannelHistoryPage(
                videos = listOf(video1, video2Dup, video3),
                nextOffsetId = null,
                hasMore = false,
                messageCount = 3,
            )
        )

        val viewModel = LibraryViewModel(
            libraryRepository = fakeLibraryRepository,
            personRepository = fakePersonRepository,
            studioRepository = fakeStudioRepository,
            channelRepository = fakeChannelRepository,
            clientManager = clientManager,
        )
        advanceUntilIdle()
        viewModel.selectChannel(sampleChannel)
        advanceUntilIdle()

        val videos = viewModel.uiState.value.channelsState.channelVideos
        assertEquals(2, videos.size)
        assertEquals("tg-1", videos[0].id)
        assertEquals("tg-3", videos[1].id)
    }

    @Test
    fun `focus tracking stores clicked IDs across tabs`() = runTest(testDispatcher) {
        val viewModel = LibraryViewModel(
            libraryRepository = fakeLibraryRepository,
            personRepository = fakePersonRepository,
            studioRepository = fakeStudioRepository,
            channelRepository = fakeChannelRepository,
            clientManager = clientManager,
        )
        advanceUntilIdle()

        // All tab movie click
        viewModel.onMovieClicked(sampleMovie1)
        assertEquals("movie-1", viewModel.uiState.value.allState.lastSelectedItemId)

        // Channel select and video click
        viewModel.selectChannel(sampleChannel)
        assertEquals("c-1", viewModel.uiState.value.channelsState.lastSelectedChannelId)

        val video = VideoDto(id = "tg-10", title = "Video 10")
        viewModel.onChannelVideoClicked(video)
        assertEquals("tg-10", viewModel.uiState.value.channelsState.lastSelectedVideoId)
    }

    @Test
    fun `duration and hd filtering filters allState items`() = runTest(testDispatcher) {
        val shortMovie = TvItem(id = "m-short", title = "Short", runtimeTicks = 10L * 60L * 10_000_000L, width = 1920, height = 1080)
        val longMovie = TvItem(id = "m-long", title = "Long", runtimeTicks = 50L * 60L * 10_000_000L, width = 720, height = 480)
        fakeLibraryRepository.itemsPageResult = Result.success(
            TvItemsPage(items = listOf(shortMovie, longMovie), totalRecordCount = 2, startIndex = 0)
        )
        val viewModel = LibraryViewModel(
            libraryRepository = fakeLibraryRepository,
            personRepository = fakePersonRepository,
            studioRepository = fakeStudioRepository,
            channelRepository = fakeChannelRepository,
            clientManager = clientManager,
        )
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.allState.items.size)

        // Filter short (<15m)
        viewModel.setDurationFilter(com.adulthub.tv.data.model.DurationFilter.SHORT)
        assertEquals(1, viewModel.uiState.value.allState.items.size)
        assertEquals("m-short", viewModel.uiState.value.allState.items.first().id)

        // Filter HD only
        viewModel.setDurationFilter(com.adulthub.tv.data.model.DurationFilter.ALL)
        viewModel.toggleHdOnly()
        assertEquals(1, viewModel.uiState.value.allState.items.size)
        assertEquals("m-short", viewModel.uiState.value.allState.items.first().id)
    }

    @Test
    fun `channel duration filtering filters channelVideos`() = runTest(testDispatcher) {
        val shortVideo = VideoDto(id = "v-1", title = "Short Clip", duration = 300.0)
        val longVideo = VideoDto(id = "v-2", title = "Long Scene", duration = 3000.0)
        fakeChannelRepository.channelHistoryResult = Result.success(
            ChannelHistoryPage(videos = listOf(shortVideo, longVideo), nextOffsetId = null, hasMore = false, messageCount = 2)
        )
        val viewModel = LibraryViewModel(
            libraryRepository = fakeLibraryRepository,
            personRepository = fakePersonRepository,
            studioRepository = fakeStudioRepository,
            channelRepository = fakeChannelRepository,
            clientManager = clientManager,
        )
        advanceUntilIdle()

        viewModel.selectChannel(sampleChannel)
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.channelsState.channelVideos.size)

        viewModel.setChannelDurationFilter(com.adulthub.tv.data.model.DurationFilter.SHORT)
        assertEquals(1, viewModel.uiState.value.channelsState.channelVideos.size)
        assertEquals("v-1", viewModel.uiState.value.channelsState.channelVideos.first().id)

        viewModel.setChannelDurationFilter(com.adulthub.tv.data.model.DurationFilter.LONG)
        assertEquals(1, viewModel.uiState.value.channelsState.channelVideos.size)
        assertEquals("v-2", viewModel.uiState.value.channelsState.channelVideos.first().id)
    }

    // --- Fakes ---

    private class FakeLibraryRepository : LibraryRepository(mockk(relaxed = true), mockk(relaxed = true)) {
        var itemsPageResult: Result<TvItemsPage> = Result.success(TvItemsPage(emptyList(), 0, 0))
        var genreRequestCount = 0
        var lastGenreIds: List<UUID> = emptyList()
        var lastFilters: Set<ItemFilter> = emptySet()
        var randomGenreIds: List<UUID> = emptyList()
        var randomFilters: Set<ItemFilter> = emptySet()
        var randomResult: Result<TvItem?> = Result.success(null)

        var genreMoviesPageResult: Result<TvItemsPage>? = null
        var genreMoviesResult: Result<List<TvItem>> = Result.success(emptyList())
        val genreMoviesStartIndexes = mutableListOf<Int>()

        override suspend fun getGenres(parentId: String?, limit: Int): Result<List<TvCategory>> {
            genreRequestCount += 1
            return Result.success(emptyList())
        }

        override suspend fun getGenreMovies(
            genreName: String,
            genreId: String?,
            parentId: String?,
            startIndex: Int,
            limit: Int,
        ): Result<List<TvItem>> = genreMoviesResult

        override suspend fun getGenreMoviesPage(
            genreName: String,
            genreId: String?,
            parentId: String?,
            startIndex: Int,
            limit: Int,
        ): Result<TvItemsPage> {
            genreMoviesStartIndexes += startIndex
            val pageRes = genreMoviesPageResult
            if (pageRes != null) return pageRes
            return genreMoviesResult.map { items ->
                val slice = items.drop(startIndex).take(limit)
                TvItemsPage(items = slice, totalRecordCount = items.size, startIndex = startIndex)
            }
        }

        override suspend fun getItems(
            parentId: String?,
            startIndex: Int,
            limit: Int,
            sortBy: String,
            sortOrder: SortOrder,
            filters: Set<ItemFilter>,
            searchTerm: String?,
            genreIds: List<UUID>,
            studioIds: List<UUID>,
            personIds: List<UUID>,
        ): Result<TvItemsPage> {
            lastGenreIds = genreIds
            lastFilters = filters
            return itemsPageResult
        }

        override suspend fun getRandomItemFiltered(
            parentId: String?,
            excludeItemId: String?,
            filters: Set<ItemFilter>,
            genreIds: List<UUID>,
            studioIds: List<UUID>,
            personIds: List<UUID>,
            genres: List<String>,
            studios: List<String>,
        ): Result<TvItem?> {
            randomGenreIds = genreIds
            randomFilters = filters
            return randomResult
        }
    }

    private class FakePersonRepository : PersonRepository(mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true)) {
        var personsResult: Result<List<TvPerson>> = Result.success(emptyList())

        override suspend fun getPersons(
            parentId: String?,
            startIndex: Int,
            limit: Int,
            searchTerm: String?,
            nameStartsWith: String?,
        ): Result<List<TvPerson>> = personsResult

        override suspend fun getPersonsPage(
            parentId: String?,
            startIndex: Int,
            limit: Int,
            searchTerm: String?,
            nameStartsWith: String?,
        ): Result<TvPersonPage> = personsResult.map { persons ->
            val page = persons.drop(startIndex).take(limit)
            TvPersonPage(persons = page, totalRecordCount = persons.size)
        }
    }

    private class FakeStudioRepository : StudioRepository(mockk(relaxed = true), mockk(relaxed = true)) {
        var studiosResult: Result<List<TvStudio>> = Result.success(emptyList())
        var studioMoviesResult: Result<List<TvItem>> = Result.success(emptyList())
        var studioMoviesPageResult: Result<TvItemsPage>? = null

        override suspend fun getStudios(parentId: String?, startIndex: Int, limit: Int): Result<List<TvStudio>> = studiosResult

        override suspend fun getStudioMovies(studioName: String, parentId: String?, startIndex: Int, limit: Int): Result<List<TvItem>> = studioMoviesResult

        override suspend fun getStudioMoviesPage(
            studioName: String,
            parentId: String?,
            startIndex: Int,
            limit: Int,
        ): Result<TvItemsPage> {
            val pageResult = studioMoviesPageResult
            if (pageResult != null) return pageResult
            return studioMoviesResult.map { items ->
                val slice = items.drop(startIndex).take(limit)
                TvItemsPage(items = slice, totalRecordCount = items.size, startIndex = startIndex)
            }
        }
    }

    @Test
    fun `library filters survive a process death through the saved state handle`() = runTest(testDispatcher) {
        val handle = SavedStateHandle()
        val viewModel = LibraryViewModel(
            libraryRepository = fakeLibraryRepository,
            personRepository = fakePersonRepository,
            studioRepository = fakeStudioRepository,
            channelRepository = fakeChannelRepository,
            clientManager = clientManager,
            savedStateHandle = handle,
        )
        advanceUntilIdle()

        viewModel.setDurationFilter(DurationFilter.SHORT)
        viewModel.toggleHdOnly()
        viewModel.toggleFavoritesOnly()
        viewModel.selectAlphabetLetter('A')
        viewModel.selectTab(LibraryTab.ACTORS)
        advanceUntilIdle()

        val restored = LibraryViewModel(
            libraryRepository = fakeLibraryRepository,
            personRepository = fakePersonRepository,
            studioRepository = fakeStudioRepository,
            channelRepository = fakeChannelRepository,
            clientManager = clientManager,
            savedStateHandle = handle,
        )
        advanceUntilIdle()

        val state = restored.uiState.value
        assertEquals(DurationFilter.SHORT, state.allState.durationFilter)
        assertTrue(state.allState.hdOnly)
        assertTrue(state.allState.favoritesOnly)
        assertEquals(LibraryTab.ACTORS, state.selectedTab)
        assertEquals('A', state.actorsState.selectedLetter)
    }

    private class FakeChannelRepository : ChannelRepository(mockk(relaxed = true)) {
        var categoriesResult: Result<List<TvChannel>> = Result.success(emptyList())
        var categoryVideosResult: Result<List<VideoDto>> = Result.success(emptyList())
        var categoryVideoRequests = 0
        var channelHistoryResult: Result<ChannelHistoryPage>? = null
        val channelHistoryResults = mutableMapOf<Long?, Result<ChannelHistoryPage>>()
        var channelHistoryRequests = 0
        var lastRequestedOffsetId: Long? = null

        override suspend fun getCategories(): Result<List<TvChannel>> = categoriesResult

        override suspend fun getChannelHistory(channelId: String, offsetId: Long?, limit: Int): Result<ChannelHistoryPage> {
            channelHistoryRequests++
            lastRequestedOffsetId = offsetId
            channelHistoryResults[offsetId]?.let { return it }
            val res = channelHistoryResult
            if (res != null) return res
            return Result.failure(UnsupportedOperationException("Fallback to categoryVideos"))
        }

        override suspend fun getCategoryVideos(channelId: String, page: Int, sort: String, minDuration: Int?): Result<List<VideoDto>> {
            categoryVideoRequests++
            return categoryVideosResult
        }
    }
}
