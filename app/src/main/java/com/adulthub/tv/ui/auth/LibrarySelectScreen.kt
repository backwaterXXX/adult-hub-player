package com.adulthub.tv.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.adulthub.tv.R
import com.adulthub.tv.ui.components.ErrorMessage
import com.adulthub.tv.ui.components.LoadingIndicator
import com.adulthub.tv.ui.components.TvPrimaryButton
import com.adulthub.tv.ui.theme.NeonPink
import com.adulthub.tv.ui.theme.OledBlack
import com.adulthub.tv.ui.theme.TextSecondary

@Composable
fun LibrarySelectScreen(
    viewModel: AuthViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.libraryState.collectAsStateWithLifecycle()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OledBlack)
            .padding(horizontal = 48.dp, vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (state.isLoading) {
            LoadingIndicator(message = stringResource(R.string.library_loading))
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(R.string.library_select_title),
                    style = MaterialTheme.typography.headlineLarge,
                    color = NeonPink,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.library_select_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
                Spacer(modifier = Modifier.height(32.dp))

                if (state.libraries.isEmpty()) {
                    Text(
                        text = stringResource(R.string.library_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary,
                    )
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        items(state.libraries) { library ->
                            TvPrimaryButton(
                                onClick = { viewModel.selectLibrary(library) },
                                modifier = Modifier
                                    .width(220.dp)
                                    .height(100.dp),
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        text = library.name,
                                        style = MaterialTheme.typography.titleLarge,
                                    )
                                    if (!library.collectionType.isNullOrBlank()) {
                                        Text(
                                            text = library.collectionType.orEmpty(),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = TextSecondary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (state.errorMessage != null) {
                    Spacer(modifier = Modifier.height(24.dp))
                    ErrorMessage(
                        message = state.errorMessage ?: "",
                        onRetry = { viewModel.loadLibraries() },
                    )
                }
            }
        }
    }
}
