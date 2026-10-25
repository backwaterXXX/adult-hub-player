package com.adulthub.tv.ui.player

import com.adulthub.tv.data.local.SettingsDataStore
import com.adulthub.tv.data.model.PlaybackInfo
import com.adulthub.tv.data.model.TvItem
import com.adulthub.tv.data.repository.LibraryRepository
import com.adulthub.tv.data.repository.PlaybackRepository
import com.adulthub.tv.data.resolver.VideoVariant
import com.adulthub.tv.data.resolver.VideoVariantsResult
import com.adulthub.tv.player.PlaybackReporter
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.jellyfin.sdk.model.api.PlayMethod
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelQualityTest {

    private val testDispatcher = StandardTestDispatcher()

    private val telegramItem = TvItem(
        id = "telegram:-1001618765010:21406:5393323762257014475",
        title = "Scene",
        providerVideoId = "-1001618765010:21406:5393323762257014475",
        runtimeTicks = 30L * 10_000_000L,
    )

    private val originalVariant = VideoVariant(
        id = "original",
        isOriginal = true,
        width = 720,
        height = 1280,
        codec = "h264",
        mimeType = "video/mp4",
        fileSizeBytes = 5_892_214L,
        durationSec = 30.5,
        bitrate = null,
        estimatedAverageBitrate = 1_542_717L,
    )

    private val lightVariant = VideoVariant(
        id = "alt_5393558434975097219",
        isOriginal = false,
        width = 480,
        height = 852,
        codec = "av01",
        mimeType = "video/mp4",
        fileSizeBytes = 1_511_665L,
        durationSec = 30.4,
        bitrate = null,
        estimatedAverageBitrate = 397_362L,
    )

    private val fakePlaybackRepo = QualityFakePlaybackRepository()
    private val fakeLibraryRepo = QualityFakeLibraryRepository(telegramItem)
    private val settingsDataStore: SettingsDataStore = mockk(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel(): PlayerViewModel = PlayerViewModel(
        libraryRepository = fakeLibraryRepo,
        playbackRepository = fakePlaybackRepo,
        settingsDataStore = settingsDataStore,
        playbackReporter = PlaybackReporter(fakePlaybackRepo, CoroutineScope(testDispatcher)),
    )

    @Test
    fun `quality options appear after the resolver reports alternatives`() = runTest(testDispatcher) {
        fakePlaybackRepo.variantsResult = VideoVariantsResult.Available(listOf(originalVariant, lightVariant))
        val viewModel = buildViewModel()

        viewModel.loadItem(telegramItem.id)
        advanceUntilIdle()

        val options = viewModel.quality.value.options
        assertEquals(2, options.size)
        assertEquals("original", options.first { it.isSelected }.variantId)
        assertTrue(options.map { it.variantId }.contains("alt_5393558434975097219"))
        assertNull(viewModel.quality.value.qualityUnavailableMessage)
    }

    @Test
    fun `legacy resolver keeps the selector hidden without an error`() = runTest(testDispatcher) {
        fakePlaybackRepo.variantsResult = VideoVariantsResult.Unsupported
        val viewModel = buildViewModel()

        viewModel.loadItem(telegramItem.id)
        advanceUntilIdle()

        assertTrue(viewModel.quality.value.options.isEmpty())
        assertNull(viewModel.quality.value.qualityUnavailableMessage)
    }

    @Test
    fun `video with only the original keeps the selector hidden without an error`() = runTest(testDispatcher) {
        fakePlaybackRepo.variantsResult = VideoVariantsResult.OriginalOnly
        val viewModel = buildViewModel()

        viewModel.loadItem(telegramItem.id)
        advanceUntilIdle()

        assertTrue(viewModel.quality.value.options.isEmpty())
        assertNull(viewModel.quality.value.qualityUnavailableMessage)
    }

    @Test
    fun `failed variants fetch is distinguishable from having no alternatives`() = runTest(testDispatcher) {
        fakePlaybackRepo.variantsResult = VideoVariantsResult.Failed(IllegalStateException("gateway down"))
        val viewModel = buildViewModel()

        viewModel.loadItem(telegramItem.id)
        advanceUntilIdle()

        assertNotNull(viewModel.quality.value.qualityUnavailableMessage)
        assertTrue(viewModel.quality.value.options.isEmpty())
    }

    @Test
    fun `switch request is prepared before confirmation and only accepted once`() = runTest(testDispatcher) {
        fakePlaybackRepo.variantsResult = VideoVariantsResult.Available(listOf(originalVariant, lightVariant))
        val viewModel = buildViewModel()
        viewModel.loadItem(telegramItem.id)
        advanceUntilIdle()

        assertTrue(viewModel.beginQualitySwitch("alt_5393558434975097219"))
        // Overlapping switches are ignored while one is running.
        assertFalse(viewModel.beginQualitySwitch("original"))
        assertTrue(viewModel.quality.value.isSwitching)

        val info = viewModel.resolveQualityStreamUrl("alt_5393558434975097219")
        assertNotNull(info)
        assertEquals("alt_5393558434975097219", info!!.variantId)
        assertEquals("alt_5393558434975097219", fakePlaybackRepo.lastVariantId)

        viewModel.confirmQualitySwitch("alt_5393558434975097219")
        assertFalse(viewModel.quality.value.isSwitching)
        assertEquals("alt_5393558434975097219", viewModel.quality.value.options.first { it.isSelected }.variantId)
    }

    @Test
    fun `switching to the already playing variant is rejected`() = runTest(testDispatcher) {
        fakePlaybackRepo.variantsResult = VideoVariantsResult.Available(listOf(originalVariant, lightVariant))
        val viewModel = buildViewModel()
        viewModel.loadItem(telegramItem.id)
        advanceUntilIdle()

        assertFalse(viewModel.beginQualitySwitch("original"))
        assertFalse(viewModel.quality.value.isSwitching)
    }

    @Test
    fun `aborted switch keeps the previous selection and reports a short message`() = runTest(testDispatcher) {
        fakePlaybackRepo.variantsResult = VideoVariantsResult.Available(listOf(originalVariant, lightVariant))
        val viewModel = buildViewModel()
        viewModel.loadItem(telegramItem.id)
        advanceUntilIdle()

        assertTrue(viewModel.beginQualitySwitch("alt_5393558434975097219"))
        viewModel.abortQualitySwitch("Could not change quality. Keeping current stream.")

        assertFalse(viewModel.quality.value.isSwitching)
        assertEquals("original", viewModel.quality.value.options.first { it.isSelected }.variantId)
        assertEquals("Could not change quality. Keeping current stream.", viewModel.qualityMessage.value)
    }

    @Test
    fun `fresh stream address for retries keeps the selected variant`() = runTest(testDispatcher) {
        fakePlaybackRepo.variantsResult = VideoVariantsResult.Available(listOf(originalVariant, lightVariant))
        val viewModel = buildViewModel()
        viewModel.loadItem(telegramItem.id)
        advanceUntilIdle()

        assertTrue(viewModel.beginQualitySwitch("alt_5393558434975097219"))
        viewModel.confirmQualitySwitch("alt_5393558434975097219")
        val freshUrl = viewModel.resolveFreshStreamUrl(telegramItem.id)

        assertNotNull(freshUrl)
        assertEquals("alt_5393558434975097219", fakePlaybackRepo.lastVariantId)
        assertEquals(lightVariant, viewModel.activeVariant())
    }

    @Test
    fun `new movie resets the quality selection state`() = runTest(testDispatcher) {
        fakePlaybackRepo.variantsResult = VideoVariantsResult.Available(listOf(originalVariant, lightVariant))
        val viewModel = buildViewModel()
        viewModel.loadItem(telegramItem.id)
        advanceUntilIdle()
        assertTrue(viewModel.beginQualitySwitch("alt_5393558434975097219"))

        viewModel.loadItem("telegram:-1001618765010:21406:5393323762257019999")
        advanceUntilIdle()

        assertFalse(viewModel.quality.value.isSwitching)
        assertEquals("original", viewModel.quality.value.options.first { it.isSelected }.variantId)
        assertNull(viewModel.activeVariant()?.takeIf { !it.isOriginal })
    }
}

private class QualityFakeLibraryRepository(private val item: TvItem) :
    LibraryRepository(mockk(relaxed = true), mockk(relaxed = true)) {
    override suspend fun getItemDetails(itemId: String): Result<TvItem> = Result.success(item)
}

private class QualityFakePlaybackRepository : PlaybackRepository(mockk(relaxed = true), mockk(relaxed = true)) {
    var variantsResult: VideoVariantsResult = VideoVariantsResult.Unsupported
    var lastVariantId: String? = null

    override suspend fun resolvePlaybackInfo(item: TvItem, variantId: String?): Result<PlaybackInfo> {
        lastVariantId = variantId
        val variantQuery = variantId?.takeIf { it != "original" }?.let { "&variant=$it" }.orEmpty()
        return Result.success(
            PlaybackInfo(
                itemId = item.id,
                streamUrl = "http://resolver:8090/admin/play/${item.id}/stream?mode=proxy$variantQuery",
                playMethod = PlayMethod.DIRECT_PLAY,
                resolverVideoId = "telegram:-1001618765010:21406:5393323762257014475",
                variantId = variantId,
            ),
        )
    }

    override suspend fun fetchVideoVariants(resolverVideoId: String?): VideoVariantsResult = variantsResult
}
