package com.adulthub.tv.data.model

sealed interface AuthState {
    data object NotAuthenticated : AuthState

    data class HttpConsentRequired(val serverUrl: String) : AuthState

    data class HttpBlocked(val serverUrl: String, val reason: String) : AuthState

    data class Authenticated(
        val serverUrl: String,
        val userId: String,
        val accessToken: String,
        val username: String,
        val serverName: String = "",
    ) : AuthState
}
