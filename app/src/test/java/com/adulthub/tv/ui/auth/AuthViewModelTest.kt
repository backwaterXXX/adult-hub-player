package com.adulthub.tv.ui.auth

import android.content.Context
import com.adulthub.tv.R
import com.adulthub.tv.data.local.SettingsDataStore
import com.adulthub.tv.data.model.AuthState
import com.adulthub.tv.data.repository.AuthRepository
import com.adulthub.tv.data.repository.LibraryRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val authRepository: AuthRepository = mockk(relaxed = true)
    private val libraryRepository: LibraryRepository = mockk(relaxed = true)
    private val settingsDataStore: SettingsDataStore = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { authRepository.discoverServers() } returns emptyFlow()
        every { authRepository.sessionRejections } returns emptyFlow()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `restore navigation event remains available to a late collector`() = runTest(dispatcher) {
        coEvery { authRepository.restoreSession() } returns AuthState.NotAuthenticated

        val viewModel = AuthViewModel(authRepository, libraryRepository, settingsDataStore, context = context)
        advanceUntilIdle()

        assertEquals(AuthNavEvent.NavigateToServer, viewModel.navEvents.first())
    }

    @Test
    fun `a rejected Jellyfin session returns to the server screen with an explanation`() = runTest(dispatcher) {
        val rejections = MutableSharedFlow<String>(extraBufferCapacity = 1)
        every { authRepository.sessionRejections } returns rejections
        coEvery { authRepository.restoreSession() } returns AuthState.NotAuthenticated
        coEvery { authRepository.handleRejectedSession("rejected-token") } returns AuthState.Authenticated(
            serverUrl = "https://jf.example.com",
            userId = "user-123",
            accessToken = "rejected-token",
            username = "admin",
            serverName = "Main Jellyfin",
        )
        every { context.getString(R.string.auth_session_rejected) } returns "Session rejected"

        val viewModel = AuthViewModel(authRepository, libraryRepository, settingsDataStore, context = context)
        advanceUntilIdle()

        rejections.emit("rejected-token")
        advanceUntilIdle()

        coVerify { authRepository.handleRejectedSession("rejected-token") }
        assertEquals("Session rejected", viewModel.serverState.value.errorMessage)
        assertEquals("https://jf.example.com", viewModel.serverState.value.serverUrl)
    }
}
