package com.adulthub.tv.ui.auth

import com.adulthub.tv.data.local.SettingsDataStore
import com.adulthub.tv.data.model.AuthState
import com.adulthub.tv.data.repository.AuthRepository
import com.adulthub.tv.data.repository.LibraryRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { authRepository.discoverServers() } returns emptyFlow()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `restore navigation event remains available to a late collector`() = runTest(dispatcher) {
        coEvery { authRepository.restoreSession() } returns AuthState.NotAuthenticated

        val viewModel = AuthViewModel(authRepository, libraryRepository, settingsDataStore)
        advanceUntilIdle()

        assertEquals(AuthNavEvent.NavigateToServer, viewModel.navEvents.first())
    }
}
