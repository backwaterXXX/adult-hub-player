package com.adulthub.tv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.adulthub.tv.data.jellyfin.JellyfinClientManager
import com.adulthub.tv.data.local.SettingsDataStore
import com.adulthub.tv.data.model.TvItem
import com.adulthub.tv.ui.theme.CardSurface
import com.adulthub.tv.ui.theme.CardSurfaceFocused
import com.adulthub.tv.ui.theme.NeonPink
import com.adulthub.tv.ui.theme.SurfaceDark
import com.adulthub.tv.ui.theme.TextPrimary
import com.adulthub.tv.ui.theme.TextSecondary
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import android.net.Uri

@Composable
fun MovieCard(
    item: TvItem,
    imageUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cardWidth: Dp? = 260.dp,
) {
    val context = LocalContext.current
    val settingsDataStore: SettingsDataStore = koinInject()
    val jellyfinClientManager: JellyfinClientManager = koinInject()
    val lowRamMode by settingsDataStore.lowRamMode.collectAsStateWithLifecycle(initialValue = false)
    val telegramVideoId = item.providerVideoId?.takeIf { it.isNotBlank() }
        ?: item.id.takeIf { it.startsWith("telegram:") }?.removePrefix("telegram:")
    var phashThumbnailUrl by remember(item.id) { mutableStateOf<String?>(null) }
    var phashThumbnailFailed by remember(item.id) { mutableStateOf(false) }
    var usePhashThumbnail by remember(item.id, imageUrl, item.progressFraction) {
        mutableStateOf(
            item.progressFraction > 0f ||
                (item.primaryImageTag.isNullOrBlank() && item.thumbImageTag.isNullOrBlank() && item.backdropImageTag.isNullOrBlank()),
        )
    }

    LaunchedEffect(item.id, telegramVideoId, usePhashThumbnail) {
        val rawId = telegramVideoId ?: return@LaunchedEffect
        if (!usePhashThumbnail || phashThumbnailUrl != null) return@LaunchedEffect
        runCatching {
            val configured = settingsDataStore.getResolverBaseUrl().trimEnd('/')
            val base = settingsDataStore.requireAllowedServiceUrl(configured).normalized.trimEnd('/')
            val videoId = if (rawId.startsWith("telegram:")) rawId else "telegram:$rawId"
            phashThumbnailUrl = "$base/admin/api/v2/video-phash/thumbnail?video_id=${Uri.encode(videoId)}"
        }
    }

    val targetWidth = ThumbnailQualityConfig.movieTargetWidth(lowRamMode)
    val targetHeight = ThumbnailQualityConfig.movieTargetHeight(lowRamMode)

    val imageModel = remember(imageUrl, phashThumbnailUrl, usePhashThumbnail, item.id, lowRamMode) {
        val selectedUrl = if (usePhashThumbnail) phashThumbnailUrl else imageUrl
        if (selectedUrl.isNullOrBlank()) imageUrl else {
            val request = ImageRequest.Builder(context)
                .data(selectedUrl)
                .size(targetWidth, targetHeight)
            jellyfinClientManager.getApiOrNull()?.accessToken?.takeIf { it.isNotBlank() }?.let {
                request.addHeader("X-Emby-Token", it)
            }
            request.build()
        }
    }

    Card(
        onClick = onClick,
        modifier = modifier
            .then(if (cardWidth == null) Modifier.fillMaxWidth() else Modifier.width(cardWidth))
            .graphicsLayer { clip = false },
        shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
        scale = CardDefaults.scale(scale = 1f, focusedScale = if (lowRamMode) 1f else 1.06f),
        border = CardDefaults.border(
            border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))),
            focusedBorder = Border(BorderStroke(3.5.dp, NeonPink)),
        ),
        colors = CardDefaults.colors(
            containerColor = CardSurface,
            focusedContainerColor = CardSurfaceFocused,
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                    .background(SurfaceDark),
            ) {
                AsyncImage(
                    model = imageModel,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    onSuccess = { state ->
                        if (!usePhashThumbnail && !phashThumbnailFailed && telegramVideoId != null) {
                            usePhashThumbnail = isPoorThumbnail(state.result.drawable)
                        }
                    },
                    onError = {
                        if (usePhashThumbnail) {
                            phashThumbnailFailed = true
                            usePhashThumbnail = false
                        } else if (!phashThumbnailFailed && telegramVideoId != null) {
                            usePhashThumbnail = true
                        }
                    },
                )

                // Genre badge top-left
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val firstGenre = item.genres.firstOrNull().orEmpty()
                    if (firstGenre.isNotBlank()) {
                        GenreBadge(genre = firstGenre)
                    }
                }

                // Watched badge top-right
                if (item.isPlayed) {
                    WatchedBadge(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                    )
                }

                // Duration badge bottom-right
                if (item.formattedDuration.isNotBlank()) {
                    DurationBadge(
                        durationText = item.formattedDuration,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(horizontal = 8.dp, vertical = if (!item.isPlayed && item.progressFraction > 0f) 12.dp else 8.dp),
                    )
                }

                // Resume progress bar if partially played and not completed
                if (!item.isPlayed && item.progressFraction > 0f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(Color(0x80000000)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(item.progressFraction)
                                .background(NeonPink),
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                )

                val meta = buildString {
                    item.year?.let { append(it) }
                    val studio = item.studios.firstOrNull()
                    if (!studio.isNullOrBlank()) {
                        if (isNotEmpty()) append(" • ")
                        append(studio)
                    }
                }

                if (meta.isNotBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun WatchedBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.78f))
            .border(1.dp, NeonPink.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = "WATCHED",
            color = NeonPink,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

