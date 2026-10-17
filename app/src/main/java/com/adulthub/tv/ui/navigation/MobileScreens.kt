package com.adulthub.tv.ui.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.adulthub.tv.data.model.*
import com.adulthub.tv.data.local.BufferProfile
import com.adulthub.tv.ui.auth.AuthViewModel
import com.adulthub.tv.ui.home.HomeViewModel
import com.adulthub.tv.ui.detail.ItemDetailViewModel
import com.adulthub.tv.ui.library.*
import com.adulthub.tv.ui.search.SearchViewModel
import com.adulthub.tv.ui.settings.SettingsViewModel
import com.adulthub.tv.ui.person.PersonDetailViewModel
import com.adulthub.tv.ui.theme.NeonPink
import com.adulthub.tv.ui.theme.OledBlack
import org.koin.compose.koinInject

@Composable
fun MobilePage(
    title: String,
    subtitle: String? = null,
    horizontalPadding: androidx.compose.ui.unit.Dp = 18.dp,
    verticalPadding: androidx.compose.ui.unit.Dp = 12.dp,
    contentGap: androidx.compose.ui.unit.Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Column(Modifier.fillMaxSize().padding(horizontal = horizontalPadding, vertical = verticalPadding)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, color = NeonPink)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(contentGap))
        content()
    }
}

@Composable
private fun MobileError(text: String?) { if (text != null) Text(text, color = MaterialTheme.colorScheme.error) }

@Composable
fun MobileServerScreen(vm: AuthViewModel) {
    val state by vm.serverState.collectAsStateWithLifecycle()
    var url by remember(state.serverUrl) { mutableStateOf(state.serverUrl) }
    MobilePage("Connect to Jellyfin", "Enter your server address") {
        OutlinedTextField(url, { url = it; vm.onServerUrlChanged(it) }, Modifier.fillMaxWidth(), label = { Text("Server URL") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false, imeAction = androidx.compose.ui.text.input.ImeAction.Go), singleLine = true)
        Spacer(Modifier.height(12.dp))
        Button(onClick = { vm.connectServer(url) }, enabled = !state.isConnecting, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(if (state.isConnecting) "Connecting…" else "Connect") }
        if (state.isDiscovering) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.discoveredServers.forEach { server -> TextButton(onClick = { vm.connectServer(server.url) }) { Text(server.name) } }
        MobileError(state.errorMessage)
        state.pendingHttpConsentUrl?.let { host ->
            AlertDialog(onDismissRequest = vm::dismissHttpConsent, title = { Text("Allow unencrypted connection?") }, text = { Text("Allow HTTP for $host on this device?") }, confirmButton = { TextButton(onClick = vm::confirmHttpConsent) { Text("Allow") } }, dismissButton = { TextButton(onClick = vm::dismissHttpConsent) { Text("Cancel") } })
        }
    }
}

@Composable
fun MobileLoginScreen(vm: AuthViewModel, onBack: () -> Unit) {
    val state by vm.loginState.collectAsStateWithLifecycle()
    MobilePage("Sign in", state.serverUrl) {
        OutlinedTextField(state.username, vm::onUsernameChanged, Modifier.fillMaxWidth(), label = { Text("Username") }, singleLine = true)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(state.password, vm::onPasswordChanged, Modifier.fillMaxWidth(), label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true)
        Spacer(Modifier.height(14.dp))
        Button(onClick = vm::signIn, enabled = !state.isSigningIn, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(if (state.isSigningIn) "Signing in…" else "Sign in") }
        OutlinedButton(onClick = vm::startQuickConnect, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Quick Connect") }
        TextButton(onClick = onBack) { Text("Change server") }
        MobileError(state.errorMessage)
    }
}

@Composable
fun MobileQuickConnectScreen(vm: AuthViewModel, onBack: () -> Unit) {
    val state by vm.quickConnectState.collectAsStateWithLifecycle()
    MobilePage("Quick Connect", "Enter this code in Jellyfin account settings") {
        Text(state.code.ifBlank { "Preparing code…" }, style = MaterialTheme.typography.displayMedium, color = NeonPink)
        Text(state.serverUrl)
        if (state.isPolling) LinearProgressIndicator(Modifier.fillMaxWidth())
        MobileError(state.errorMessage)
        if (state.isExpired) Text("The code expired. Start Quick Connect again.")
        OutlinedButton(onClick = { vm.stopQuickConnect(); onBack() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Cancel") }
    }
}

@Composable
fun MobileLibrarySelectScreen(vm: AuthViewModel) {
    val state by vm.libraryState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.loadLibraries() }
    MobilePage("Choose a library", "Select the collection to browse") {
        if (state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.libraries.forEach { library ->
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { vm.selectLibrary(library) }) { Text(library.name, Modifier.padding(18.dp)) }
        }
        MobileError(state.errorMessage)
    }
}

@Composable
fun MobileHomeScreen(vm: HomeViewModel, onItem: (String) -> Unit, onPlay: (String) -> Unit, onPerson: (String, String) -> Unit, onSearch: () -> Unit, onLibrary: () -> Unit, onCategory: (String) -> Unit) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(vm) {
        vm.navEvents.collect { event ->
            when (event) {
                is com.adulthub.tv.ui.home.HomeNavEvent.NavigateToItemDetail -> onItem(event.itemId)
                is com.adulthub.tv.ui.home.HomeNavEvent.NavigateToPlayer -> onPlay(event.itemId)
                is com.adulthub.tv.ui.home.HomeNavEvent.NavigateToPersonDetail -> onPerson(event.personId, event.personName)
                is com.adulthub.tv.ui.home.HomeNavEvent.NavigateToLibraryCategory -> onCategory(event.categoryId)
                com.adulthub.tv.ui.home.HomeNavEvent.NavigateToLibrary -> onLibrary()
                com.adulthub.tv.ui.home.HomeNavEvent.NavigateToSearch -> onSearch()
                com.adulthub.tv.ui.home.HomeNavEvent.NavigateToSettings -> Unit
            }
        }
    }
    MobilePage("${state.libraryName.ifBlank { "Home" }}") {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 6.dp)) {
            item { Button(onClick = onLibrary) { Text("Browse library") } }
            item { OutlinedButton(onClick = onSearch) { Text("Search") } }
            item { OutlinedButton(onClick = vm::onSurpriseMeClicked) { Text("Surprise me") } }
            item { FilterChip(selected = state.randomUnwatchedOnly, onClick = vm::toggleRandomUnwatchedOnly, label = { Text("Unwatched") }) }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(top = 10.dp, bottom = 16.dp)) {
            fun section(title: String, items: List<TvItem>) {
                if (items.isNotEmpty()) {
                    item { Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.SemiBold) }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(end = 4.dp)) {
                            items(items.take(12), key = { title + it.id }) { media ->
                                MobileMediaPosterCard(media.title, vm.getItemImageUrl(media)) {
                                    if (media.providerVideoId != null || media.id.startsWith("telegram:")) onPlay(media.id) else onItem(media.id)
                                }
                            }
                        }
                    }
                }
            }
            section("Continue watching", state.resumeSection.items)
            section("Recently added", state.latestSection.items)
            section("Watch history", state.watchHistorySection.items)
            section("Random picks", state.randomSection.items)
            if (state.performersSection.items.isNotEmpty()) {
                item { Text("Performers", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.SemiBold) }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(state.performersSection.items.take(12), key = { it.id }) { person ->
                            MobileMediaPosterCard(person.name, vm.getPersonImageUrl(person)) { onPerson(person.id, person.name) }
                        }
                    }
                }
            }
            state.categoryRows.forEach { row ->
                item { Text(row.categoryName, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.SemiBold) }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(row.items.take(12), key = { row.categoryId + it.id }) { media ->
                            MobileMediaPosterCard(media.title, vm.getItemImageUrl(media)) { onItem(media.id) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MobileMediaPosterCard(title: String, imageUrl: String?, onClick: () -> Unit) {
    Card(Modifier.width(142.dp).height(196.dp).clickable(onClick = onClick), shape = RoundedCornerShape(14.dp)) {
        Box(Modifier.fillMaxSize().background(Color(0xFF202027))) {
            if (imageUrl != null) AsyncImage(
                model = imageUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .92f))))
                    .padding(horizontal = 10.dp, vertical = 10.dp),
            ) {
                Text(title, color = Color.White, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun MobileCatalogTile(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().height(112.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF24242D)),
    ) {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.Center) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(5.dp))
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = NeonPink)
        }
    }
}

@Composable
private fun MobileMediaCard(title: String, imageUrl: String?, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().heightIn(min = 96.dp).clickable(onClick = onClick)) {
        Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AsyncImage(model = imageUrl, contentDescription = title, modifier = Modifier.size(width = 68.dp, height = 88.dp))
            Text(title, Modifier.weight(1f).padding(top = 8.dp), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun MobileLibraryScreen(vm: LibraryViewModel, initialCategoryId: String?, onItem: (String) -> Unit, onPlay: (String) -> Unit, onPerson: (String, String) -> Unit) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    var tab by remember(initialCategoryId) { mutableStateOf(if (initialCategoryId.isNullOrBlank()) LibraryTab.ALL else LibraryTab.CATEGORIES) }
    var sortMenu by remember { mutableStateOf(false) }
    var genreMenu by remember { mutableStateOf(false) }
    var studioMenu by remember { mutableStateOf(false) }
    var performerMenu by remember { mutableStateOf(false) }
    LaunchedEffect(initialCategoryId) { if (!initialCategoryId.isNullOrBlank()) vm.openCategory(initialCategoryId) }
    MobilePage("Library", horizontalPadding = 12.dp, verticalPadding = 6.dp, contentGap = 4.dp) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.widthIn(min = maxWidth).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LibraryTab.entries.forEach { option ->
                    TextButton(onClick = { tab = option; vm.selectTab(option) }) {
                        Text(option.title, color = if (option == tab) NeonPink else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
        when (tab) {
            LibraryTab.ALL -> {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(selected = state.allState.favoritesOnly, onClick = vm::toggleFavoritesOnly, label = { Text("Favorites") })
                    FilterChip(selected = state.allState.unwatchedOnly, onClick = vm::toggleUnwatchedOnly, label = { Text("Unwatched") })
                    FilterChip(selected = state.allState.hdOnly, onClick = vm::toggleHdOnly, label = { Text("HD") })
                    TextButton(onClick = vm::onSurpriseMeClicked) { Text("Shuffle") }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DurationFilter.entries.forEach { filter -> FilterChip(selected = state.allState.durationFilter == filter, onClick = { vm.setDurationFilter(filter) }, label = { Text(filter.label) }) }
                    Box {
                        TextButton(onClick = { sortMenu = true }) { Text("Sort") }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            listOf("DateCreated" to "Newest", "SortName" to "Title", "CommunityRating" to "Rating").forEach { (key, label) ->
                                DropdownMenuItem(text = { Text(label) }, onClick = { vm.setSort(key, org.jellyfin.sdk.model.api.SortOrder.DESCENDING); sortMenu = false })
                            }
                        }
                    }
                    Box {
                        TextButton(onClick = { vm.loadGenresForFilter(); genreMenu = true }) { Text(state.allState.selectedGenre?.name ?: "Genre") }
                        DropdownMenu(expanded = genreMenu, onDismissRequest = { genreMenu = false }) {
                            DropdownMenuItem(text = { Text("All genres") }, onClick = { vm.selectGenre(null); genreMenu = false })
                            state.allState.genres.forEach { genre -> DropdownMenuItem(text = { Text(genre.name) }, onClick = { vm.selectGenre(genre); genreMenu = false }) }
                        }
                    }
                    Box {
                        TextButton(onClick = { vm.loadStudiosForFilter(); studioMenu = true }) { Text(state.allState.selectedStudio?.name ?: "Studio") }
                        DropdownMenu(expanded = studioMenu, onDismissRequest = { studioMenu = false }) {
                            DropdownMenuItem(text = { Text("All studios") }, onClick = { vm.selectCatalogStudio(null); studioMenu = false })
                            state.allState.studios.forEach { studio -> DropdownMenuItem(text = { Text(studio.name) }, onClick = { vm.selectCatalogStudio(studio); studioMenu = false }) }
                        }
                    }
                    Box {
                        TextButton(onClick = { vm.loadPerformersForFilter(); performerMenu = true }) { Text(state.allState.selectedPerformer?.name ?: "Performer") }
                        DropdownMenu(expanded = performerMenu, onDismissRequest = { performerMenu = false }) {
                            DropdownMenuItem(text = { Text("All performers") }, onClick = { vm.selectPerformer(null); performerMenu = false })
                            state.allState.performers.forEach { performer -> DropdownMenuItem(text = { Text(performer.name) }, onClick = { vm.selectPerformer(performer); performerMenu = false }) }
                        }
                    }
                }
                LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 142.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(top = 8.dp, bottom = 18.dp)) {
                    items(state.allState.items, key = { it.id }) { item ->
                        MobileMediaPosterCard(item.title, vm.getItemImageUrl(item)) {
                            if (item.providerVideoId != null) onPlay(item.id) else onItem(item.id)
                        }
                    }
                    item { if (state.allState.hasMore) TextButton(onClick = { vm.loadAllItems() }) { Text("Load more") } }
                }
            }
            LibraryTab.CATEGORIES -> {
                val selected = state.categoriesState.selectedCategory
                if (selected != null) {
                    Column(Modifier.fillMaxSize()) {
                        TextButton(onClick = { vm.selectCategory(null) }) { Text("‹  All categories") }
                        Text(selected.name, style = MaterialTheme.typography.titleMedium)
                        if (state.categoriesState.isLoadingCategoryMovies) LinearProgressIndicator(Modifier.fillMaxWidth())
                        MobileError(state.categoriesState.categoryMoviesError ?: state.categoriesState.errorMessage)
                        LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 142.dp), modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(state.categoriesState.categoryMovies, key = { it.id }) { item -> MobileMediaPosterCard(item.title, vm.getItemImageUrl(item)) { onItem(item.id) } }
                            item { if (state.categoriesState.hasMoreCategoryMovies) TextButton(onClick = vm::loadMoreCategoryMovies) { Text("Load more") } }
                        }
                    }
                } else {
                    LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 142.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(state.categoriesState.categories, key = { it.id }) { category ->
                            MobileCatalogTile(category.name, "${category.movieCount} titles") { vm.selectCategory(category) }
                        }
                    }
                }
            }
            LibraryTab.ACTORS -> Column {
                OutlinedTextField(state.actorsState.searchQuery, vm::setActorSearchQuery, Modifier.fillMaxWidth(), label = { Text("Find a performer") }, singleLine = true)
                LazyRow {
                    item {
                    FilterChip(selected = state.actorsState.onlyWithPhoto, onClick = vm::toggleOnlyWithPhoto, label = { Text("Photo only") })
                    FilterChip(selected = state.actorsState.selectedLetter == null, onClick = { vm.selectAlphabetLetter(null) }, label = { Text("All") })
                    }
                    items(('A'..'Z').toList()) { letter -> FilterChip(selected = state.actorsState.selectedLetter == letter, onClick = { vm.selectAlphabetLetter(letter) }, label = { Text(letter.toString()) }) }
                }
                MobileError(state.actorsState.errorMessage)
                LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 142.dp), modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(state.actorsState.filteredPersons, key = { it.id }) { person ->
                        MobileMediaPosterCard(person.name, vm.getPersonImageUrl(person)) { onPerson(person.id, person.name) }
                    }
                    item { if (state.actorsState.hasMore) TextButton(onClick = vm::loadMoreActors) { Text("Load more") } }
                }
            }
            LibraryTab.STUDIOS -> {
                val selected = state.studiosState.selectedStudio
                if (selected != null) {
                    Column(Modifier.fillMaxSize()) {
                        TextButton(onClick = { vm.selectStudio(null) }) { Text("‹  All studios") }
                        Text(selected.name, style = MaterialTheme.typography.titleMedium)
                        if (state.studiosState.isLoadingStudioMovies) LinearProgressIndicator(Modifier.fillMaxWidth())
                        MobileError(state.studiosState.studioMoviesError ?: state.studiosState.errorMessage)
                        LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 142.dp), modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(state.studiosState.studioMovies, key = { it.id }) { item -> MobileMediaPosterCard(item.title, vm.getItemImageUrl(item)) { onItem(item.id) } }
                            item { if (state.studiosState.hasMoreStudioMovies) TextButton(onClick = vm::loadMoreStudioMovies) { Text("Load more") } }
                        }
                    }
                } else {
                    LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 142.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(state.studiosState.studios, key = { it.id }) { studio ->
                            MobileCatalogTile(studio.name, "${studio.movieCount} titles") { vm.selectStudio(studio) }
                        }
                    }
                }
            }
            LibraryTab.CHANNELS -> {
                val selected = state.channelsState.selectedChannel
                if (selected == null) {
                    Column(Modifier.fillMaxSize()) {
                        if (state.channelsState.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                        MobileError(state.channelsState.errorMessage)
                        if (!state.channelsState.isLoading && state.channelsState.errorMessage != null) TextButton(onClick = vm::loadChannels) { Text("Retry") }
                        if (!state.channelsState.isLoading && state.channelsState.errorMessage == null && state.channelsState.channels.isEmpty()) {
                            Text("No channels available", color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(vertical = 12.dp))
                        }
                        LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 142.dp), modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(state.channelsState.channels, key = { it.id }) { channel ->
                                MobileCatalogTile(channel.title, "Open channel") { vm.selectChannel(channel) }
                            }
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { vm.selectChannel(null) }) { Text("‹  Channels") }
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = vm::hideCurrentChannel) { Text("Hide") }
                        }
                        Text(selected.title, style = MaterialTheme.typography.titleMedium)
                        LazyRow {
                            items(ChannelSortMode.entries) { mode -> FilterChip(selected = state.channelsState.sortMode == mode, onClick = { vm.setChannelSortMode(mode) }, label = { Text(mode.label) }) }
                            items(DurationFilter.entries) { filter -> FilterChip(selected = state.channelsState.durationFilter == filter, onClick = { vm.setChannelDurationFilter(filter) }, label = { Text(filter.label) }) }
                        }
                        if (state.channelsState.isLoadingVideos) LinearProgressIndicator(Modifier.fillMaxWidth())
                        MobileError(state.channelsState.channelVideosError)
                        if (!state.channelsState.isLoadingVideos && state.channelsState.channelVideosError == null && state.channelsState.channelVideos.isEmpty()) {
                            Text("No videos in this channel yet", color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(vertical = 12.dp))
                        }
                        LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 142.dp), modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(state.channelsState.channelVideos, key = { it.id }) { video -> MobileMediaPosterCard(video.title, vm.getChannelVideoThumbnailUrl(video.thumbnail)) { onPlay(video.providerVideoId.ifBlank { video.id }) } }
                            item { if (state.channelsState.hasMoreVideos) TextButton(onClick = vm::loadMoreChannelVideos) { Text("Load more") } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MobileSearchScreen(vm: SearchViewModel, initialQuery: String?, onItem: (String) -> Unit, onPlay: (String) -> Unit, onPerson: (String, String) -> Unit) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(initialQuery) { if (!initialQuery.isNullOrBlank()) vm.onQueryChanged(initialQuery) }
    MobilePage("Search") {
        OutlinedTextField(state.query, vm::onQueryChanged, Modifier.fillMaxWidth(), label = { Text("Movies, performers, channels") }, singleLine = true)
        if (state.query.isBlank() && state.recentQueries.isNotEmpty()) {
            Text("Recent searches", style = MaterialTheme.typography.labelLarge, color = NeonPink, modifier = Modifier.padding(top = 8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                items(state.recentQueries.take(8)) { query ->
                    AssistChip(onClick = { vm.onQueryChanged(query) }, label = { Text(query, maxLines = 1, overflow = TextOverflow.Ellipsis) })
                }
            }
        }
        MobileError(state.errorMessage)
        if (state.isSearching) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn {
            items(state.movies, key = { "m${it.id}" }) { item -> MobileMediaCard(item.title, null) { if (item.providerVideoId != null) onPlay(item.id) else onItem(item.id) } }
            items(state.actors, key = { "p${it.id}" }) { person -> MobileMediaCard(person.name, null) { onPerson(person.id, person.name) } }
            items(state.channelVideos, key = { "c${it.id}" }) { video -> MobileMediaCard(video.title, null) { onPlay(video.id) } }
        }
    }
}

@Composable
fun MobileSettingsScreen(vm: SettingsViewModel, auth: AuthViewModel, onSignOut: () -> Unit) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    MobilePage("Settings", "Adult Hub v${com.adulthub.tv.BuildConfig.VERSION_NAME}") {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text("Account: ${state.username}"); Text("Server: ${state.serverUrl}"); Button(onClick = { vm.logout(context); auth.signOut(); onSignOut() }) { Text("Sign out") } }
            item { Text("Active library", style = MaterialTheme.typography.titleLarge); state.availableLibraries.forEach { lib -> FilterChip(selected = lib.id == state.selectedLibraryId, onClick = { vm.selectLibrary(lib) }, label = { Text(lib.name) }) } }
            item { Text("Home categories", style = MaterialTheme.typography.titleLarge); state.availableHomeCategories.forEach { category -> FilterChip(selected = category.id in state.selectedHomeCategoryIds, onClick = { vm.toggleHomeCategory(category.id) }, label = { Text(category.name) }) } }
            item { Text("Resolver URL", style = MaterialTheme.typography.titleLarge); OutlinedTextField(state.resolverUrlDraft, vm::onResolverUrlDraftChanged, Modifier.fillMaxWidth(), singleLine = true); Button(onClick = { vm.setResolverUrl(state.resolverUrlDraft) }) { Text("Save Resolver URL") }; Button(onClick = vm::checkConnections) { Text(if (state.isCheckingConnections) "Checking…" else "Check connections") }; Text("Jellyfin: ${state.jellyfinConnectionStatus}"); Text("Resolver: ${state.resolverConnectionStatus}") }
            item { Text("Playback", style = MaterialTheme.typography.titleLarge); listOf(10, 15).forEach { step -> FilterChip(selected = state.seekStepSec == step, onClick = { vm.setSeekStepSec(step) }, label = { Text("Seek ${step}s") }) }; Row { Text("Auto-play next"); Switch(state.autoplayNext, vm::setAutoplayNext) }; Row { Text("Low memory mode"); Switch(state.lowRamMode, vm::setLowRamMode) }; Row { Text("Video matching"); Switch(state.phashOnPlayback, vm::setPhashOnPlayback) } }
            item { Text("Buffer profile", style = MaterialTheme.typography.titleLarge); BufferProfile.entries.forEach { profile -> FilterChip(selected = state.bufferProfile == profile, onClick = { vm.setBufferProfile(profile) }, label = { Text(profile.label) }) } }
            item { Text("Clear local data", style = MaterialTheme.typography.titleLarge); OutlinedButton(onClick = vm::clearWatchHistory) { Text("Clear watch history") }; OutlinedButton(onClick = vm::clearSearchHistory) { Text("Clear search history") }; OutlinedButton(onClick = vm::clearResumeProgress) { Text("Clear channel progress") }; OutlinedButton(onClick = { vm.clearCache(context) }) { Text("Clear image cache") } }
            item { state.statusMessage?.let { Text(it, color = NeonPink) } }
        }
    }
}

@Composable
fun MobilePersonScreen(vm: PersonDetailViewModel, personId: String, personName: String, onItem: (String) -> Unit) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(personId, personName) { vm.loadPerson(personId, personName) }
    MobilePage(state.person?.name ?: personName, state.errorMessage) {
        state.person?.bio?.let { Text(it) }
        LazyColumn { items(state.movies, key = { it.id }) { item -> MobileMediaCard(item.title, vm.getItemImageUrl(item)) { onItem(item.id) } } }
    }
}

@Composable
fun MobileItemDetailScreen(
    vm: ItemDetailViewModel,
    itemId: String,
    onBack: () -> Unit,
    onPlay: (String, Boolean?) -> Unit,
    onItem: (String) -> Unit,
    onPerson: (String, String) -> Unit,
    onSearch: (String) -> Unit,
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(itemId) { vm.loadDetails(itemId) }
    LaunchedEffect(vm) {
        vm.navEvents.collect { event ->
            when (event) {
                is com.adulthub.tv.ui.detail.ItemDetailNavEvent.NavigateToPlayer -> onPlay(event.itemId, event.resume)
                is com.adulthub.tv.ui.detail.ItemDetailNavEvent.NavigateToItemDetail -> onItem(event.itemId)
                is com.adulthub.tv.ui.detail.ItemDetailNavEvent.NavigateToPersonDetail -> onPerson(event.personId, event.personName)
                com.adulthub.tv.ui.detail.ItemDetailNavEvent.NavigateBack -> onBack()
            }
        }
    }
    MobilePage(state.item?.title ?: "Details", state.errorMessage) {
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.item?.let { media ->
                AsyncImage(vm.getItemImageUrl(media), contentDescription = media.title, modifier = Modifier.fillMaxWidth().height(280.dp))
                Text(listOfNotNull(media.year?.toString(), media.formattedDuration.takeIf(String::isNotBlank)).joinToString(" • "))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onPlay(media.id, false) }) { Text("Play") }
                    OutlinedButton(onClick = { onPlay(media.id, true) }) { Text("Resume") }
                    FilterChip(selected = state.isFavorite, onClick = vm::toggleFavorite, label = { Text(if (state.isFavorite) "♥ Favorite" else "♡ Favorite") })
                    FilterChip(selected = media.isPlayed, onClick = vm::toggleWatched, label = { Text(if (media.isPlayed) "Watched" else "Mark watched") })
                }
                Text(media.overview.orEmpty())
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    com.adulthub.tv.data.model.ShuffleContextMode.entries.forEach { mode ->
                        OutlinedButton(onClick = { vm.playRandomContextual(mode) }) { Text(mode.label) }
                    }
                }
                Text("Genres", style = MaterialTheme.typography.titleMedium)
                Row {
                    media.genres.forEach { tag -> TextButton(onClick = { onSearch(tag) }) { Text(tag) } }
                }
                Text("Cast", style = MaterialTheme.typography.titleMedium)
                media.people.forEach { person -> TextButton(onClick = { onPerson(person.id, person.name) }) { Text(listOfNotNull(person.name, person.role).joinToString(" • ")) } }
            }
            if (state.similarItems.isNotEmpty()) {
                Text("Similar", style = MaterialTheme.typography.titleLarge, color = NeonPink)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.similarItems, key = { it.id }) { item ->
                        MobileMediaPosterCard(item.title, vm.getItemImageUrl(item)) { onItem(item.id) }
                    }
                }
            }
        }
    }
}
