package com.adulthub.tv.ui.navigation

import androidx.annotation.StringRes
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
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
import com.adulthub.tv.R
import com.adulthub.tv.data.model.*
import com.adulthub.tv.data.jellyfin.JellyfinClientManager
import com.adulthub.tv.data.resolver.resolveResolverUrl
import com.adulthub.tv.ui.auth.AuthViewModel
import com.adulthub.tv.ui.components.LocalNetworkPermission
import com.adulthub.tv.ui.home.HomeViewModel
import com.adulthub.tv.ui.home.SectionState
import com.adulthub.tv.ui.detail.ItemDetailViewModel
import com.adulthub.tv.ui.library.*
import com.adulthub.tv.ui.search.SearchViewModel
import com.adulthub.tv.ui.person.PersonDetailViewModel
import com.adulthub.tv.ui.theme.NeonPink
import com.adulthub.tv.ui.theme.OledBlack
import kotlinx.coroutines.delay
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
    val localNetwork = LocalNetworkPermission.current
    MobilePage(stringResource(R.string.server_title), stringResource(R.string.mobile_server_subtitle)) {
        OutlinedTextField(url, { url = it; vm.onServerUrlChanged(it) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.mobile_server_url_label)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false, imeAction = androidx.compose.ui.text.input.ImeAction.Go), singleLine = true)
        Spacer(Modifier.height(12.dp))
        Button(onClick = { localNetwork.ensureGranted(url) { vm.connectServer(url) } }, enabled = !state.isConnecting, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(if (state.isConnecting) stringResource(R.string.mobile_connecting) else stringResource(R.string.action_connect)) }
        if (state.isDiscovering) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.discoveredServers.forEach { server -> TextButton(onClick = { localNetwork.ensureGranted(server.url) { vm.connectServer(server.url) } }) { Text(server.name) } }
        MobileError(state.errorMessage)
        if (state.errorMessage != null) {
            TextButton(
                onClick = {
                    if (state.sessionRestoreFailed) {
                        vm.retrySavedSession()
                    } else {
                        localNetwork.ensureGranted(url) { vm.connectServer(url) }
                    }
                },
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(if (state.sessionRestoreFailed) stringResource(R.string.mobile_retry_saved_session) else stringResource(R.string.action_retry)) }
        }
        state.pendingHttpConsentUrl?.let { host ->
            AlertDialog(onDismissRequest = vm::dismissHttpConsent, title = { Text(stringResource(R.string.mobile_http_consent_title)) }, text = { Text(stringResource(R.string.mobile_http_consent_message, host)) }, confirmButton = { TextButton(onClick = vm::confirmHttpConsent) { Text(stringResource(R.string.mobile_allow)) } }, dismissButton = { TextButton(onClick = vm::dismissHttpConsent) { Text(stringResource(R.string.action_cancel)) } })
        }
    }
}

@Composable
fun MobileLoginScreen(vm: AuthViewModel, onBack: () -> Unit) {
    val state by vm.loginState.collectAsStateWithLifecycle()
    val localNetwork = LocalNetworkPermission.current
    MobilePage(stringResource(R.string.mobile_login_title), state.serverUrl) {
        OutlinedTextField(state.username, vm::onUsernameChanged, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.login_username_label)) }, singleLine = true)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(state.password, vm::onPasswordChanged, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.login_password_label)) }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true)
        Spacer(Modifier.height(14.dp))
        Button(onClick = { localNetwork.ensureGranted(state.serverUrl) { vm.signIn() } }, enabled = !state.isSigningIn, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(if (state.isSigningIn) stringResource(R.string.login_signing_in) else stringResource(R.string.mobile_login_title)) }
        OutlinedButton(onClick = { localNetwork.ensureGranted(state.serverUrl) { vm.startQuickConnect() } }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.quick_connect_title)) }
        TextButton(onClick = onBack) { Text(stringResource(R.string.mobile_change_server)) }
        MobileError(state.errorMessage)
    }
}

@Composable
fun MobileQuickConnectScreen(vm: AuthViewModel, onBack: () -> Unit) {
    val state by vm.quickConnectState.collectAsStateWithLifecycle()
    MobilePage(stringResource(R.string.quick_connect_title), stringResource(R.string.mobile_quick_connect_subtitle)) {
        Text(state.code.ifBlank { stringResource(R.string.mobile_preparing_code) }, style = MaterialTheme.typography.displayMedium, color = NeonPink)
        Text(state.serverUrl)
        if (state.isPolling) LinearProgressIndicator(Modifier.fillMaxWidth())
        MobileError(state.errorMessage)
        if (state.isExpired) Text(stringResource(R.string.mobile_code_expired))
        OutlinedButton(onClick = { vm.stopQuickConnect(); onBack() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.action_cancel)) }
    }
}

@Composable
fun MobileLibrarySelectScreen(vm: AuthViewModel) {
    val state by vm.libraryState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.loadLibraries() }
    MobilePage(stringResource(R.string.mobile_library_select_title), stringResource(R.string.mobile_library_select_subtitle)) {
        if (state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.libraries.forEach { library ->
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { vm.selectLibrary(library) }) { Text(library.name, Modifier.padding(18.dp)) }
        }
        MobileError(state.errorMessage)
    }
}

@Composable
fun MobileHomeScreen(vm: HomeViewModel, isCurrentDestination: Boolean = true, onItem: (String) -> Unit, onPlay: (String) -> Unit, onPerson: (String, String) -> Unit, onSearch: () -> Unit, onLibrary: () -> Unit, onCategory: (String) -> Unit) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    var hasBeenCurrentDestination by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(isCurrentDestination) {
        if (isCurrentDestination) {
            if (hasBeenCurrentDestination) {
                delay(500L)
                vm.loadResume()
                vm.loadWatchHistory()
            }
            hasBeenCurrentDestination = true
        }
    }
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
    val homeTitle = if (state.libraryName.isBlank()) stringResource(R.string.nav_home) else state.libraryName
    MobilePage(homeTitle) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 6.dp)) {
            item { Button(onClick = onLibrary) { Text(stringResource(R.string.mobile_browse_library)) } }
            item { OutlinedButton(onClick = onSearch) { Text(stringResource(R.string.nav_search)) } }
            item { OutlinedButton(onClick = vm::onSurpriseMeClicked) { Text(stringResource(R.string.mobile_surprise_me)) } }
            item { FilterChip(selected = state.randomUnwatchedOnly, onClick = vm::toggleRandomUnwatchedOnly, label = { Text(stringResource(R.string.mobile_unwatched)) }) }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(top = 10.dp, bottom = 16.dp)) {
            fun failure(@StringRes title: Int, message: String, onRetry: () -> Unit) {
                item(key = "${title}_error") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(title), style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text(message, color = Color.White.copy(alpha = 0.75f))
                        OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.mobile_try_again)) }
                    }
                }
            }
            fun section(@StringRes title: Int, section: SectionState<TvItem>, onRetry: () -> Unit) {
                if (section.errorMessage != null) {
                    failure(title, section.errorMessage, onRetry)
                    return
                }
                if (section.isLoading) {
                    item(key = "${title}_loading") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    return
                }
                if (section.items.isEmpty()) return
                item { Text(stringResource(title), style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.SemiBold) }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(end = 4.dp)) {
                        items(section.items.take(12), key = { "$title-${it.id}" }) { media ->
                            MobileMediaPosterCard(media.title, vm.getItemImageUrl(media)) {
                                if (media.providerVideoId != null || media.id.startsWith("telegram:")) onPlay(media.id) else onItem(media.id)
                            }
                        }
                    }
                }
            }
            section(R.string.mobile_continue_watching, state.resumeSection, vm::loadResume)
            section(R.string.mobile_recently_added, state.latestSection, vm::loadLatest)
            section(R.string.mobile_watch_history, state.watchHistorySection, vm::loadWatchHistory)
            section(R.string.mobile_random_picks, state.randomSection, vm::loadRandom)
            state.performersSection.errorMessage?.let { message ->
                failure(R.string.mobile_performers, message, vm::loadPerformers)
            }
            if (state.performersSection.items.isNotEmpty()) {
                item { Text(stringResource(R.string.mobile_performers), style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.SemiBold) }
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
            if (state.categoryRowsLoading) {
                item(key = "category_rows_loading") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            }
            state.categoryRowsErrorMessage?.let { message ->
                item(key = "category_rows_error") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(message, color = Color.White)
                        OutlinedButton(onClick = vm::reloadCategoryRows) { Text(stringResource(R.string.mobile_retry_categories)) }
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
private fun MobileCatalogTile(title: String, subtitle: String?, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().height(112.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF24242D)),
    ) {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.Center) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(5.dp))
                Text(subtitle, style = MaterialTheme.typography.labelMedium, color = NeonPink)
            }
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
    var sortMenu by rememberSaveable { mutableStateOf(false) }
    var genreMenu by rememberSaveable { mutableStateOf(false) }
    var studioMenu by rememberSaveable { mutableStateOf(false) }
    var performerMenu by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(initialCategoryId) { if (!initialCategoryId.isNullOrBlank()) vm.openCategory(initialCategoryId) }
    MobilePage(stringResource(R.string.nav_library), horizontalPadding = 12.dp, verticalPadding = 6.dp, contentGap = 4.dp) {
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
                    FilterChip(selected = state.allState.favoritesOnly, onClick = vm::toggleFavoritesOnly, label = { Text(stringResource(R.string.mobile_favorites)) })
                    FilterChip(selected = state.allState.unwatchedOnly, onClick = vm::toggleUnwatchedOnly, label = { Text(stringResource(R.string.mobile_unwatched)) })
                    FilterChip(selected = state.allState.hdOnly, onClick = vm::toggleHdOnly, label = { Text(stringResource(R.string.mobile_hd)) })
                    TextButton(onClick = vm::onSurpriseMeClicked) { Text(stringResource(R.string.mobile_shuffle)) }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DurationFilter.entries.forEach { filter -> FilterChip(selected = state.allState.durationFilter == filter, onClick = { vm.setDurationFilter(filter) }, label = { Text(filter.label) }) }
                    Box {
                        TextButton(onClick = { sortMenu = true }) { Text(stringResource(R.string.mobile_sort)) }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            listOf("DateCreated" to R.string.mobile_sort_newest, "SortName" to R.string.mobile_sort_title, "CommunityRating" to R.string.mobile_sort_rating).forEach { (key, label) ->
                                DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = { vm.setSort(key, org.jellyfin.sdk.model.api.SortOrder.DESCENDING); sortMenu = false })
                            }
                        }
                    }
                    Box {
                        TextButton(onClick = { vm.loadGenresForFilter(); genreMenu = true }) { Text(state.allState.selectedGenre?.name ?: stringResource(R.string.mobile_genre)) }
                        DropdownMenu(expanded = genreMenu, onDismissRequest = { genreMenu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.mobile_all_genres)) }, onClick = { vm.selectGenre(null); genreMenu = false })
                            state.allState.genres.forEach { genre -> DropdownMenuItem(text = { Text(genre.name) }, onClick = { vm.selectGenre(genre); genreMenu = false }) }
                        }
                    }
                    Box {
                        TextButton(onClick = { vm.loadStudiosForFilter(); studioMenu = true }) { Text(state.allState.selectedStudio?.name ?: stringResource(R.string.mobile_studio)) }
                        DropdownMenu(expanded = studioMenu, onDismissRequest = { studioMenu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.mobile_all_studios)) }, onClick = { vm.selectCatalogStudio(null); studioMenu = false })
                            state.allState.studios.forEach { studio -> DropdownMenuItem(text = { Text(studio.name) }, onClick = { vm.selectCatalogStudio(studio); studioMenu = false }) }
                        }
                    }
                    Box {
                        TextButton(onClick = { vm.loadPerformersForFilter(); performerMenu = true }) { Text(state.allState.selectedPerformer?.name ?: stringResource(R.string.mobile_performer)) }
                        DropdownMenu(expanded = performerMenu, onDismissRequest = { performerMenu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.mobile_all_performers)) }, onClick = { vm.selectPerformer(null); performerMenu = false })
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
                    item { if (state.allState.hasMore) TextButton(onClick = { vm.loadAllItems() }) { Text(stringResource(R.string.mobile_load_more)) } }
                }
            }
            LibraryTab.CATEGORIES -> {
                val selected = state.categoriesState.selectedCategory
                if (selected != null) {
                    Column(Modifier.fillMaxSize()) {
                        TextButton(onClick = { vm.selectCategory(null) }) { Text(stringResource(R.string.mobile_back_all_categories)) }
                        Text(selected.name, style = MaterialTheme.typography.titleMedium)
                        if (state.categoriesState.isLoadingCategoryMovies) LinearProgressIndicator(Modifier.fillMaxWidth())
                        MobileError(state.categoriesState.categoryMoviesError ?: state.categoriesState.errorMessage)
                        LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 142.dp), modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(state.categoriesState.categoryMovies, key = { it.id }) { item -> MobileMediaPosterCard(item.title, vm.getItemImageUrl(item)) { onItem(item.id) } }
                            item { if (state.categoriesState.hasMoreCategoryMovies) TextButton(onClick = vm::loadMoreCategoryMovies) { Text(stringResource(R.string.mobile_load_more)) } }
                        }
                    }
                } else {
                    LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 142.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(state.categoriesState.categories, key = { it.id }) { category ->
                            MobileCatalogTile(category.name, stringResource(R.string.mobile_titles_count, category.movieCount)) { vm.selectCategory(category) }
                        }
                    }
                }
            }
            LibraryTab.ACTORS -> Column {
                OutlinedTextField(state.actorsState.searchQuery, vm::setActorSearchQuery, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.mobile_find_performer)) }, singleLine = true)
                LazyRow {
                    item {
                    FilterChip(selected = state.actorsState.onlyWithPhoto, onClick = vm::toggleOnlyWithPhoto, label = { Text(stringResource(R.string.mobile_photo_only)) })
                    FilterChip(selected = state.actorsState.selectedLetter == null, onClick = { vm.selectAlphabetLetter(null) }, label = { Text(stringResource(R.string.mobile_all)) })
                    }
                    items(('A'..'Z').toList()) { letter -> FilterChip(selected = state.actorsState.selectedLetter == letter, onClick = { vm.selectAlphabetLetter(letter) }, label = { Text(letter.toString()) }) }
                }
                MobileError(state.actorsState.errorMessage)
                LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 142.dp), modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(state.actorsState.filteredPersons, key = { it.id }) { person ->
                        MobileMediaPosterCard(person.name, vm.getPersonImageUrl(person)) { onPerson(person.id, person.name) }
                    }
                    item { if (state.actorsState.hasMore) TextButton(onClick = vm::loadMoreActors) { Text(stringResource(R.string.mobile_load_more)) } }
                }
            }
            LibraryTab.STUDIOS -> {
                val selected = state.studiosState.selectedStudio
                if (selected != null) {
                    Column(Modifier.fillMaxSize()) {
                        TextButton(onClick = { vm.selectStudio(null) }) { Text(stringResource(R.string.mobile_back_all_studios)) }
                        Text(selected.name, style = MaterialTheme.typography.titleMedium)
                        if (state.studiosState.isLoadingStudioMovies) LinearProgressIndicator(Modifier.fillMaxWidth())
                        MobileError(state.studiosState.studioMoviesError ?: state.studiosState.errorMessage)
                        LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 142.dp), modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(state.studiosState.studioMovies, key = { it.id }) { item -> MobileMediaPosterCard(item.title, vm.getItemImageUrl(item)) { onItem(item.id) } }
                            item { if (state.studiosState.hasMoreStudioMovies) TextButton(onClick = vm::loadMoreStudioMovies) { Text(stringResource(R.string.mobile_load_more)) } }
                        }
                    }
                } else {
                    LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 142.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(state.studiosState.studios, key = { it.id }) { studio ->
                            val subtitle = studio.movieCount?.let { stringResource(R.string.mobile_titles_count, it) }
                            MobileCatalogTile(studio.name, subtitle) { vm.selectStudio(studio) }
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
                        if (!state.channelsState.isLoading && state.channelsState.errorMessage != null) TextButton(onClick = vm::loadChannels) { Text(stringResource(R.string.action_retry)) }
                        if (!state.channelsState.isLoading && state.channelsState.errorMessage == null && state.channelsState.channels.isEmpty()) {
                            Text(stringResource(R.string.mobile_no_channels), color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(vertical = 12.dp))
                        }
                        LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 142.dp), modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(state.channelsState.channels, key = { it.id }) { channel ->
                                MobileCatalogTile(channel.title, stringResource(R.string.mobile_open_channel)) { vm.selectChannel(channel) }
                            }
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { vm.selectChannel(null) }) { Text(stringResource(R.string.mobile_back_channels)) }
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = vm::hideCurrentChannel) { Text(stringResource(R.string.mobile_hide)) }
                        }
                        Text(selected.title, style = MaterialTheme.typography.titleMedium)
                        LazyRow {
                            items(ChannelSortMode.entries) { mode -> FilterChip(selected = state.channelsState.sortMode == mode, onClick = { vm.setChannelSortMode(mode) }, label = { Text(mode.label) }) }
                            items(DurationFilter.entries) { filter -> FilterChip(selected = state.channelsState.durationFilter == filter, onClick = { vm.setChannelDurationFilter(filter) }, label = { Text(filter.label) }) }
                        }
                        if (state.channelsState.isLoadingVideos) LinearProgressIndicator(Modifier.fillMaxWidth())
                        MobileError(state.channelsState.channelVideosError)
                        if (!state.channelsState.isLoadingVideos && state.channelsState.channelVideosError == null && state.channelsState.channelVideos.isEmpty()) {
                            Text(stringResource(R.string.mobile_no_channel_videos), color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(vertical = 12.dp))
                        }
                        LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 142.dp), modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(state.channelsState.channelVideos, key = { it.id }) { video -> MobileMediaPosterCard(video.title, vm.getChannelVideoThumbnailUrl(video.thumbnail)) { onPlay(video.providerVideoId.ifBlank { video.id }) } }
                            item { if (state.channelsState.hasMoreVideos) TextButton(onClick = vm::loadMoreChannelVideos) { Text(stringResource(R.string.mobile_load_more)) } }
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
    val clientManager: JellyfinClientManager = koinInject()
    LaunchedEffect(initialQuery) { if (!initialQuery.isNullOrBlank()) vm.onQueryChanged(initialQuery) }
    MobilePage(stringResource(R.string.nav_search)) {
        OutlinedTextField(
            state.query,
            vm::onQueryChanged,
            Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.mobile_search_hint)) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    TextButton(onClick = vm::clearQuery) { Text(stringResource(R.string.action_confirm_clear)) }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
            singleLine = true,
        )
        if (state.query.isBlank() && state.recentQueries.isNotEmpty()) {
            Text(stringResource(R.string.mobile_recent_searches), style = MaterialTheme.typography.labelLarge, color = NeonPink, modifier = Modifier.padding(top = 8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                items(state.recentQueries.take(8)) { query ->
                    AssistChip(onClick = { vm.onQueryChanged(query) }, label = { Text(query, maxLines = 1, overflow = TextOverflow.Ellipsis) })
                }
            }
        }
        MobileError(state.errorMessage)
        if (state.isSearching) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f).imePadding(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            if (state.movies.isNotEmpty()) {
                item(key = "h_movies") { MobileSearchSectionHeader(stringResource(R.string.mobile_movies)) }
                items(state.movies, key = { "m${it.id}" }) { item ->
                    MobileMediaCard(item.title, clientManager.getItemImageUrl(item)) {
                        if (item.providerVideoId != null) onPlay(item.id) else onItem(item.id)
                    }
                }
            }
            if (state.actors.isNotEmpty()) {
                item(key = "h_actors") { MobileSearchSectionHeader(stringResource(R.string.mobile_performers)) }
                items(state.actors, key = { "p${it.id}" }) { person ->
                    MobileMediaCard(person.name, clientManager.getPersonImageUrl(person.id, person.primaryImageTag)) {
                        onPerson(person.id, person.name)
                    }
                }
            }
            if (state.channelVideos.isNotEmpty()) {
                item(key = "h_channels") { MobileSearchSectionHeader(stringResource(R.string.mobile_channel_videos)) }
                items(state.channelVideos, key = { "c${it.id}" }) { video ->
                    MobileMediaCard(
                        video.title,
                        resolveResolverUrl(video.thumbnail, state.resolverBaseUrl),
                    ) { onPlay(video.providerVideoId.ifBlank { video.id }) }
                }
            }
            if (state.hasSearched && !state.isSearching &&
                state.movies.isEmpty() && state.actors.isEmpty() && state.channelVideos.isEmpty()
            ) {
                item(key = "no_results") {
                    Text(
                        stringResource(R.string.mobile_no_results, state.query.trim()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 18.dp).fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun MobileSearchSectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = NeonPink,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
    )
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
    MobilePage(state.item?.title ?: stringResource(R.string.mobile_details), state.errorMessage) {
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.item?.let { media ->
                AsyncImage(vm.getItemImageUrl(media), contentDescription = media.title, modifier = Modifier.fillMaxWidth().height(280.dp))
                Text(listOfNotNull(media.year?.toString(), media.formattedDuration.takeIf(String::isNotBlank)).joinToString(" • "))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onPlay(media.id, false) }) { Text(stringResource(R.string.mobile_play)) }
                    OutlinedButton(onClick = { onPlay(media.id, true) }) { Text(stringResource(R.string.mobile_resume)) }
                    FilterChip(selected = state.isFavorite, onClick = vm::toggleFavorite, label = { Text(if (state.isFavorite) stringResource(R.string.mobile_favorite_on) else stringResource(R.string.mobile_favorite_off)) })
                    FilterChip(selected = media.isPlayed, onClick = vm::toggleWatched, label = { Text(if (media.isPlayed) stringResource(R.string.mobile_watched) else stringResource(R.string.mobile_mark_watched)) })
                }
                Text(media.overview.orEmpty())
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    com.adulthub.tv.data.model.ShuffleContextMode.entries.forEach { mode ->
                        OutlinedButton(onClick = { vm.playRandomContextual(mode) }) { Text(mode.label) }
                    }
                }
                Text(stringResource(R.string.mobile_genres), style = MaterialTheme.typography.titleMedium)
                Row {
                    media.genres.forEach { tag -> TextButton(onClick = { onSearch(tag) }) { Text(tag) } }
                }
                Text(stringResource(R.string.mobile_cast), style = MaterialTheme.typography.titleMedium)
                media.people.forEach { person -> TextButton(onClick = { onPerson(person.id, person.name) }) { Text(listOfNotNull(person.name, person.role).joinToString(" • ")) } }
            }
            if (state.similarItems.isNotEmpty()) {
                Text(stringResource(R.string.mobile_similar), style = MaterialTheme.typography.titleLarge, color = NeonPink)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.similarItems, key = { it.id }) { item ->
                        MobileMediaPosterCard(item.title, vm.getItemImageUrl(item)) { onItem(item.id) }
                    }
                }
            }
        }
    }
}
