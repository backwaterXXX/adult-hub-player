package com.adulthub.tv.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adulthub.tv.data.local.SettingsDataStore
import com.adulthub.tv.data.model.AuthState
import com.adulthub.tv.data.model.ServerInfo
import com.adulthub.tv.data.network.ServiceUrlPolicy
import com.adulthub.tv.data.network.ServiceUrlValidation
import com.adulthub.tv.data.repository.AuthRepository
import com.adulthub.tv.data.repository.LibraryRepository
import com.adulthub.tv.data.repository.TvLibrary
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class ServerUiState(
    val serverUrl: String = "",
    val isConnecting: Boolean = false,
    val discoveredServers: List<ServerInfo> = emptyList(),
    val isDiscovering: Boolean = false,
    val errorMessage: String? = null,
    val pendingHttpConsentUrl: String? = null,
    val pendingHttpConsentRestoresSession: Boolean = false,
)

data class LoginUiState(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val isSigningIn: Boolean = false,
    val errorMessage: String? = null,
)

data class QuickConnectUiState(
    val serverUrl: String = "",
    val code: String = "",
    val secret: String = "",
    val isPolling: Boolean = false,
    val isAuthorized: Boolean = false,
    val isExpired: Boolean = false,
    val errorMessage: String? = null,
)

data class LibrarySelectUiState(
    val libraries: List<TvLibrary> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

sealed interface AuthNavEvent {
    data class NavigateToLogin(val serverUrl: String) : AuthNavEvent
    data object NavigateToQuickConnect : AuthNavEvent
    data object NavigateToLibrarySelect : AuthNavEvent
    data object NavigateToHome : AuthNavEvent
    data object NavigateToServer : AuthNavEvent
}

class AuthViewModel(
    private val authRepository: AuthRepository,
    private val libraryRepository: LibraryRepository,
    private val settingsDataStore: SettingsDataStore,
) : ViewModel() {

    private val _serverState = MutableStateFlow(ServerUiState())
    val serverState: StateFlow<ServerUiState> = _serverState.asStateFlow()

    private val _loginState = MutableStateFlow(LoginUiState())
    val loginState: StateFlow<LoginUiState> = _loginState.asStateFlow()

    private val _quickConnectState = MutableStateFlow(QuickConnectUiState())
    val quickConnectState: StateFlow<QuickConnectUiState> = _quickConnectState.asStateFlow()

    private val _libraryState = MutableStateFlow(LibrarySelectUiState())
    val libraryState: StateFlow<LibrarySelectUiState> = _libraryState.asStateFlow()

    // Auth restoration can finish before AppNavigation starts collecting. A buffered
    // channel keeps that one-shot destination event until the collector is ready.
    private val _navEvents = Channel<AuthNavEvent>(Channel.BUFFERED)
    val navEvents: Flow<AuthNavEvent> = _navEvents.receiveAsFlow()

    private var quickConnectJob: Job? = null
    private var discoveryJob: Job? = null

    init {
        checkSession()
        startServerDiscovery()
    }

    fun checkSession() {
        viewModelScope.launch {
            val session = try {
                authRepository.restoreSession()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _serverState.update {
                    it.copy(errorMessage = "Could not restore saved settings: ${error.localizedMessage ?: "storage error"}")
                }
                _navEvents.send(AuthNavEvent.NavigateToServer)
                return@launch
            }
            when (session) {
                is AuthState.Authenticated -> {
                    _serverState.update { it.copy(serverUrl = session.serverUrl) }
                    _loginState.update { it.copy(serverUrl = session.serverUrl, username = session.username) }

                    val discoveryResult = authRepository.discoverServer(session.serverUrl)
                    if (discoveryResult.isSuccess) {
                        val savedLibraryId = settingsDataStore.getSelectedLibraryId()
                        if (!savedLibraryId.isNullOrBlank()) {
                            _navEvents.send(AuthNavEvent.NavigateToHome)
                        } else {
                            loadLibraries()
                        }
                    } else {
                        _serverState.update {
                            it.copy(errorMessage = "Saved server unavailable. Please reconnect.")
                        }
                        _navEvents.send(AuthNavEvent.NavigateToServer)
                    }
                }
                is AuthState.HttpConsentRequired -> {
                    _serverState.update {
                        it.copy(
                            serverUrl = session.serverUrl,
                            pendingHttpConsentUrl = session.serverUrl,
                            pendingHttpConsentRestoresSession = true,
                        )
                    }
                    _navEvents.send(AuthNavEvent.NavigateToServer)
                }
                is AuthState.HttpBlocked -> {
                    _serverState.update {
                        it.copy(serverUrl = session.serverUrl, errorMessage = session.reason)
                    }
                    _navEvents.send(AuthNavEvent.NavigateToServer)
                }
                AuthState.NotAuthenticated -> _navEvents.send(AuthNavEvent.NavigateToServer)
            }
        }
    }

    fun onServerUrlChanged(url: String) {
        _serverState.update { it.copy(serverUrl = url, errorMessage = null) }
    }

    fun startServerDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = viewModelScope.launch {
            _serverState.update { it.copy(isDiscovering = true) }
            authRepository.discoverServers()
                .catch { _serverState.update { state -> state.copy(isDiscovering = false) } }
                .collect { server ->
                    _serverState.update { state ->
                        val current = state.discoveredServers
                        if (current.none { it.id == server.id || it.url == server.url }) {
                            state.copy(
                                discoveredServers = current + server,
                                isDiscovering = false,
                            )
                        } else {
                            state.copy(isDiscovering = false)
                        }
                    }
                }
        }
    }

    fun connectServer(url: String? = null) {
        val targetUrl = (url ?: _serverState.value.serverUrl).trim().trimEnd('/')
        if (targetUrl.isBlank()) {
            _serverState.update { it.copy(errorMessage = "Server address cannot be empty") }
            return
        }

        val validation = ServiceUrlPolicy.inspect(targetUrl)
        if (validation is ServiceUrlValidation.Invalid) {
            _serverState.update { it.copy(errorMessage = validation.reason) }
            return
        }

        viewModelScope.launch {
            val serviceUrl = (validation as ServiceUrlValidation.Valid).value
            if (serviceUrl.usesCleartext && !settingsDataStore.isCleartextOriginApproved(serviceUrl.normalized)) {
                _serverState.update {
                    it.copy(
                        pendingHttpConsentUrl = serviceUrl.normalized,
                        pendingHttpConsentRestoresSession = false,
                        errorMessage = null,
                    )
                }
                return@launch
            }
            performServerConnection(serviceUrl.normalized)
        }
    }

    fun confirmHttpConsent() {
        val pendingUrl = _serverState.value.pendingHttpConsentUrl ?: return
        val restoresSession = _serverState.value.pendingHttpConsentRestoresSession
        viewModelScope.launch {
            try {
                settingsDataStore.approveCleartextOrigin(pendingUrl)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _serverState.update {
                    it.copy(errorMessage = "Could not save HTTP consent: ${error.localizedMessage ?: "storage error"}")
                }
                return@launch
            }
            _serverState.update {
                it.copy(
                    pendingHttpConsentUrl = null,
                    pendingHttpConsentRestoresSession = false,
                )
            }
            if (restoresSession) checkSession() else connectServer(pendingUrl)
        }
    }

    fun dismissHttpConsent() {
        _serverState.update {
            it.copy(
                pendingHttpConsentUrl = null,
                pendingHttpConsentRestoresSession = false,
            )
        }
    }

    private suspend fun performServerConnection(targetUrl: String) {
        _serverState.update { it.copy(isConnecting = true, errorMessage = null) }
        val result = authRepository.discoverServer(targetUrl)
        _serverState.update { it.copy(isConnecting = false) }

        result.onSuccess {
            _loginState.update { it.copy(serverUrl = targetUrl, errorMessage = null) }
            _quickConnectState.update { it.copy(serverUrl = targetUrl, errorMessage = null) }
            _navEvents.send(AuthNavEvent.NavigateToLogin(targetUrl))
        }.onFailure { error ->
            _serverState.update {
                it.copy(errorMessage = "Could not connect to server: ${error.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    fun onUsernameChanged(username: String) {
        _loginState.update { it.copy(username = username, errorMessage = null) }
    }

    fun onPasswordChanged(password: String) {
        _loginState.update { it.copy(password = password, errorMessage = null) }
    }

    fun signIn() {
        val state = _loginState.value
        if (state.username.isBlank()) {
            _loginState.update { it.copy(errorMessage = "Username cannot be empty") }
            return
        }

        viewModelScope.launch {
            _loginState.update { it.copy(isSigningIn = true, errorMessage = null) }
            val result = authRepository.login(
                serverUrl = state.serverUrl,
                username = state.username,
                password = state.password,
            )
            _loginState.update { it.copy(isSigningIn = false) }

            result.onSuccess {
                loadLibraries()
            }.onFailure { error ->
                _loginState.update {
                    it.copy(errorMessage = "Sign in failed: ${error.localizedMessage ?: "Invalid credentials"}")
                }
            }
        }
    }

    fun startQuickConnect() {
        val serverUrl = _loginState.value.serverUrl.ifBlank { _serverState.value.serverUrl }
        quickConnectJob?.cancel()

        viewModelScope.launch {
            _quickConnectState.update {
                it.copy(
                    serverUrl = serverUrl,
                    isPolling = false,
                    isAuthorized = false,
                    isExpired = false,
                    errorMessage = null,
                )
            }
            _navEvents.send(AuthNavEvent.NavigateToQuickConnect)

            val initResult = authRepository.initiateQuickConnect(serverUrl)
            initResult.onSuccess { init ->
                _quickConnectState.update {
                    it.copy(
                        code = init.code,
                        secret = init.secret,
                        isPolling = true,
                    )
                }
                startQuickConnectPolling(serverUrl, init.secret)
            }.onFailure { error ->
                _quickConnectState.update {
                    it.copy(errorMessage = "Quick Connect unavailable: ${error.localizedMessage ?: "Error"}")
                }
            }
        }
    }

    private fun startQuickConnectPolling(serverUrl: String, secret: String) {
        quickConnectJob?.cancel()
        quickConnectJob = viewModelScope.launch {
            var elapsedSeconds = 0
            val maxSeconds = 300 // 5 min timeout

            while (elapsedSeconds < maxSeconds) {
                delay(5000)
                elapsedSeconds += 5

                val checkResult = authRepository.checkQuickConnectState(serverUrl, secret)
                if (checkResult.getOrDefault(false)) {
                    _quickConnectState.update { it.copy(isAuthorized = true, isPolling = false) }
                    // Exchange secret for actual token
                    val authResult = authRepository.authenticateQuickConnect(serverUrl, secret)
                    authResult.onSuccess {
                        loadLibraries()
                    }.onFailure { error ->
                        _quickConnectState.update {
                            it.copy(errorMessage = "Quick Connect exchange failed: ${error.localizedMessage}")
                        }
                    }
                    return@launch
                }
            }

            // Timed out
            _quickConnectState.update {
                it.copy(isPolling = false, isExpired = true, errorMessage = "Code expired. Please request a new code.")
            }
        }
    }

    fun stopQuickConnect() {
        quickConnectJob?.cancel()
        quickConnectJob = null
        _quickConnectState.update { it.copy(secret = "", isPolling = false) }
    }

    fun loadLibraries() {
        viewModelScope.launch {
            _libraryState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = libraryRepository.getLibraries()
            _libraryState.update { it.copy(isLoading = false) }

            result.onSuccess { libraries ->
                if (libraries.size == 1) {
                    val singleLib = libraries.first()
                    settingsDataStore.setSelectedLibrary(singleLib.id, singleLib.name)
                    _navEvents.send(AuthNavEvent.NavigateToHome)
                } else if (libraries.isNotEmpty()) {
                    _libraryState.update { it.copy(libraries = libraries) }
                    _navEvents.send(AuthNavEvent.NavigateToLibrarySelect)
                } else {
                    _libraryState.update {
                        it.copy(errorMessage = "No video libraries found on this server.")
                    }
                }
            }.onFailure { error ->
                _libraryState.update {
                    it.copy(errorMessage = "Failed to load libraries: ${error.localizedMessage}")
                }
            }
        }
    }

    fun selectLibrary(library: TvLibrary) {
        viewModelScope.launch {
            settingsDataStore.setSelectedLibrary(library.id, library.name)
            _navEvents.send(AuthNavEvent.NavigateToHome)
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.logout()
            _navEvents.send(AuthNavEvent.NavigateToServer)
        }
    }

    override fun onCleared() {
        super.onCleared()
        quickConnectJob?.cancel()
        discoveryJob?.cancel()
    }
}
