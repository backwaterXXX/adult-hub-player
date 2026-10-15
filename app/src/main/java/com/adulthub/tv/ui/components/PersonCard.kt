package com.adulthub.tv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.adulthub.tv.data.local.SettingsDataStore
import com.adulthub.tv.data.model.TvPerson
import com.adulthub.tv.ui.theme.CardSurface
import com.adulthub.tv.ui.theme.CardSurfaceFocused
import com.adulthub.tv.ui.theme.NeonPink
import com.adulthub.tv.ui.theme.SurfaceDark
import com.adulthub.tv.ui.theme.TextPrimary
import com.adulthub.tv.ui.theme.TextSecondary
import org.koin.compose.koinInject

@Composable
fun PersonCard(
    person: TvPerson,
    imageUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cardWidth: Dp = 140.dp,
) {
    val context = LocalContext.current
    val settingsDataStore: SettingsDataStore = koinInject()
    val lowRamMode by settingsDataStore.lowRamMode.collectAsStateWithLifecycle(initialValue = false)

    val targetWidth = ThumbnailQualityConfig.personTargetWidth(lowRamMode)
    val targetHeight = ThumbnailQualityConfig.personTargetHeight(lowRamMode)

    val imageModel = remember(imageUrl, lowRamMode) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            .size(targetWidth, targetHeight)
            .build()
    }

    Card(
        onClick = onClick,
        modifier = modifier
            .width(cardWidth)
            .graphicsLayer { clip = false },
        shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
        scale = CardDefaults.scale(scale = 1f, focusedScale = if (lowRamMode) 1f else 1.08f),
        border = CardDefaults.border(
            border = Border(BorderStroke(1.dp, androidx.compose.ui.graphics.Color.White.copy(alpha = 0.12f))),
            focusedBorder = Border(BorderStroke(3.5.dp, NeonPink)),
        ),
        colors = CardDefaults.colors(
            containerColor = CardSurface,
            focusedContainerColor = CardSurfaceFocused,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                    .background(SurfaceDark),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = imageModel,
                    contentDescription = person.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = person.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.SemiBold,
                )

                if (person.movieCount > 0) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${person.movieCount} movies",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
