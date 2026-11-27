package com.rugbypro.app.ui.screens

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.SportsRugby
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rugbypro.app.R
import com.rugbypro.app.domain.model.AppSection
import com.rugbypro.app.domain.usecase.RugbyDateUtils
import com.rugbypro.app.ui.RugbyUiState
import com.rugbypro.app.ui.RugbyViewModel
import com.rugbypro.app.ui.components.DashboardSkeletonList
import com.rugbypro.app.ui.components.GlassCard
import com.rugbypro.app.ui.components.MatchScoreCard
import com.rugbypro.app.ui.components.PulsingLiveIndicator
import com.rugbypro.app.ui.components.SectionBackdropBanner
import com.rugbypro.app.ui.theme.ElectricCyan
import com.rugbypro.app.ui.theme.MetallicGold
import com.rugbypro.app.ui.theme.LiveCrimson
import com.rugbypro.app.ui.theme.PendingAmber
import com.rugbypro.app.ui.theme.PitchDarkBg
import com.rugbypro.app.ui.theme.PitchGreen
import com.rugbypro.app.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun AppSection.icon(): ImageVector = when (this) {
    AppSection.INICIO -> Icons.Outlined.Home
    AppSection.VIVO -> Icons.Outlined.SportsRugby
    AppSection.FIXTURE -> Icons.Outlined.CalendarMonth
    AppSection.TABLAS -> Icons.Outlined.EmojiEvents
    AppSection.EQUIPOS -> Icons.Outlined.Groups
    AppSection.COMPARACION -> Icons.Outlined.CompareArrows
    AppSection.NOTICIAS -> Icons.Outlined.Article
    AppSection.CONFIGURACION -> Icons.Outlined.Settings
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RugbyProRoot(
    state: RugbyUiState,
    viewModel: RugbyViewModel,
    onRequestNotificationPermission: () -> Unit
) {
    var showSplash by remember { mutableStateOf(true) }
    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        delay(1350)
        showSplash = false
    }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeSnackbar()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isTablet = maxWidth >= 600.dp

            val scaffoldContent: @Composable () -> Unit = {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    topBar = {
                        TopAppBar(
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                                titleContentColor = MaterialTheme.colorScheme.onSurface,
                                navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                                actionIconContentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            navigationIcon = {
                                if (!isTablet) {
                                    IconButton(
                                        onClick = { scope.launch { drawerState.open() } },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(Icons.Outlined.Menu, contentDescription = "Abrir menú de navegación")
                                    }
                                }
                            },
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Image(
                                        painter = painterResource(id = R.drawable.logo_rugby_master),
                                        contentDescription = "Logo Rugby Pro",
                                        modifier = Modifier.size(34.dp)
                                    )
                                    Spacer(Modifier.width(Spacing.sm))
                                    Column {
                                        Text(
                                            text = state.currentSection.label,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = if (state.liveMatches.isNotEmpty()) "${state.liveMatches.size} partidos en vivo" else "Rugby Pro",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (state.liveMatches.isNotEmpty()) PitchGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            actions = {
                                IconButton(
                                    onClick = { viewModel.refreshNow(forceSnapshot = true) },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Outlined.Refresh, contentDescription = "Actualizar resultados")
                                }
                            }
                        )
                    }
                ) { padding ->
                    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                        if (state.isSyncing) {
                            LinearProgressIndicator(
                                color = PitchGreen,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth().height(2.dp)
                            )
                        }
                        if (state.dashboard?.isOfflineFallback == true && !state.isLoading) {
                            Surface(
                                color = PendingAmber.copy(alpha = 0.14f),
                                border = BorderStroke(1.dp, PendingAmber.copy(alpha = 0.45f)),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = 4.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Icon(Icons.Outlined.CloudOff, contentDescription = "Sin conexión", tint = PendingAmber, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "Sin conexión. Mostrando últimos datos guardados.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = PendingAmber
                                        )
                                    }
                                    TextButton(onClick = { viewModel.refreshNow(true) }) {
                                        Text("Reintentar", style = MaterialTheme.typography.labelSmall, color = MetallicGold)
                                    }
                                }
                            }
                        } else if (state.syncError != null) {
                            Surface(
                                color = LiveCrimson.copy(alpha = 0.14f),
                                border = BorderStroke(1.dp, LiveCrimson.copy(alpha = 0.45f)),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = 4.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = state.syncError,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = { viewModel.refreshNow(true) }) {
                                        Text("Reintentar", style = MaterialTheme.typography.labelSmall, color = PitchGreen)
                                    }
                                }
                            }
                        }

                        PullToRefreshBox(
                            isRefreshing = state.isSyncing,
                            onRefresh = { viewModel.refreshNow(forceSnapshot = true) },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            if (state.isLoading && state.dashboard == null) {
                                DashboardSkeletonList()
                            } else {
                                AnimatedContent(
                                    targetState = state.currentSection,
                                    label = "sectionTransition"
                                ) { section ->
                                    when (section) {
                                        AppSection.INICIO -> HomeScreen(state, viewModel)
                                        AppSection.VIVO -> LiveMatchesScreen(state, viewModel)
                                        AppSection.FIXTURE -> FixtureResultsScreen(state, viewModel)
                                        AppSection.TABLAS -> StandingsScreen(state, viewModel)
                                        AppSection.EQUIPOS -> TeamsSquadsScreen(state, viewModel)
                                        AppSection.COMPARACION -> ComparisonScreen(state, viewModel)
                                        AppSection.NOTICIAS -> NewsScreen(state, viewModel)
                                        AppSection.CONFIGURACION -> SettingsScreen(state, viewModel, onRequestNotificationPermission)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (isTablet) {
                PermanentNavigationDrawer(
                    drawerContent = {
                        PermanentDrawerSheet(
                            drawerContainerColor = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.width(276.dp)
                        ) {
                            DrawerMenuContent(
                                current = state.currentSection,
                                liveCount = state.liveMatches.size,
                                onSelect = { viewModel.selectSection(it) }
                            )
                        }
                    },
                    content = scaffoldContent
                )
            } else {
                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet(
                            drawerContainerColor = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.width(292.dp)
                        ) {
                            DrawerMenuContent(
                                current = state.currentSection,
                                liveCount = state.liveMatches.size,
                                onSelect = {
                                    viewModel.selectSection(it)
                                    scope.launch { drawerState.close() }
                                }
                            )
                        }
                    },
                    content = scaffoldContent
                )
            }
        }

        state.selectedMatchDetail?.let { match ->
            MatchDetailDialog(
                match = match,
                onDismiss = { viewModel.openMatchDetail(null) },
                onSelectPlayer = { isHome, player -> viewModel.openPlayerFromMatch(match, isHome, player) }
            )
        }

        state.selectedTeam?.let { team ->
            TeamDetailDialog(
                team = team,
                onDismiss = { viewModel.openTeamDetail(null) },
                onSelectPlayer = { p -> viewModel.openPlayerFromTeam(team, p) }
            )
        }

        state.selectedPlayer?.let { entry ->
            PlayerDetailDialog(
                entry = entry,
                onDismiss = { viewModel.openPlayerDetail(null) }
            )
        }

        AnimatedVisibility(
            visible = !showSplash && !state.config.onboardingCompleted,
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(260))
        ) {
            OnboardingOverlay(
                initialFavorite = state.config.favoriteTeam,
                onComplete = { fav -> viewModel.completeOnboarding(fav) }
            )
        }

        AnimatedVisibility(
            visible = showSplash,
            enter = fadeIn(),
            exit = fadeOut(animationSpec = tween(420))
        ) {
            CinematicSplashOverlay()
        }
    }
}

@Composable
private fun DrawerMenuContent(
    current: AppSection,
    liveCount: Int,
    onSelect: (AppSection) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.md)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = Spacing.sm)
        ) {
            Image(
                painter = painterResource(id = R.drawable.logo_rugby_master),
                contentDescription = "Rugby Pro Logo",
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.width(Spacing.sm))
            Column {
                Text(
                    text = "RUGBY PRO",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Edición Internacional",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        Spacer(Modifier.height(Spacing.sm))

        val primarySections = listOf(
            AppSection.INICIO,
            AppSection.VIVO,
            AppSection.FIXTURE,
            AppSection.TABLAS
        )
        val exploreSections = listOf(
            AppSection.EQUIPOS,
            AppSection.COMPARACION,
            AppSection.NOTICIAS
        )
        val systemSections = listOf(
            AppSection.CONFIGURACION
        )

        val itemColors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurface
        )

        primarySections.forEach { sec ->
            NavigationDrawerItem(
                selected = current == sec,
                onClick = { onSelect(sec) },
                icon = { Icon(sec.icon(), contentDescription = sec.label) },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(sec.label, modifier = Modifier.weight(1f))
                        if (sec == AppSection.VIVO && liveCount > 0) {
                            Text(
                                text = "$liveCount",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                colors = itemColors,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }

        Spacer(Modifier.height(Spacing.sm))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
        Spacer(Modifier.height(Spacing.sm))

        exploreSections.forEach { sec ->
            NavigationDrawerItem(
                selected = current == sec,
                onClick = { onSelect(sec) },
                icon = { Icon(sec.icon(), contentDescription = sec.label) },
                label = { Text(sec.label) },
                colors = itemColors,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }

        Spacer(Modifier.height(Spacing.sm))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
        Spacer(Modifier.height(Spacing.sm))

        systemSections.forEach { sec ->
            NavigationDrawerItem(
                selected = current == sec,
                onClick = { onSelect(sec) },
                icon = { Icon(sec.icon(), contentDescription = sec.label) },
                label = { Text(sec.label) },
                colors = itemColors,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun CinematicSplashOverlay() {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val scale = remember { Animatable(0.84f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch { alpha.animateTo(1f, animationSpec = tween(550)) }
        launch { scale.animateTo(1f, animationSpec = tween(720, easing = FastOutSlowInEasing)) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PitchDarkBg)
            .semantics { contentDescription = "Pantalla de bienvenida Rugby Pro" },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(
                id = if (isLandscape) R.drawable.splash_horizontal else R.drawable.splash_vertical
            ),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().alpha(0.88f)
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            PitchDarkBg.copy(alpha = 0.58f),
                            PitchDarkBg.copy(alpha = 0.72f),
                            PitchDarkBg.copy(alpha = 0.96f)
                        )
                    )
                )
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.scale(scale.value).alpha(alpha.value).padding(Spacing.xl)
        ) {
            Image(
                painter = painterResource(id = R.drawable.logo_rugby_master),
                contentDescription = "Rugby Pro Logo",
                modifier = Modifier.size(164.dp)
            )
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = "RUGBY PRO",
                style = MaterialTheme.typography.displaySmall,
                color = Color(0xFFF5F5F5)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "RUGBY DE ÉLITE · EN VIVO",
                style = MaterialTheme.typography.labelMedium,
                color = MetallicGold
            )
        }
    }
}

@Composable
fun HomeScreen(
    state: RugbyUiState,
    viewModel: RugbyViewModel
) {
    val dash = state.dashboard
    val live = state.liveMatches
    val todayUpcoming = state.upcomingTodayMatches
    val pumasXV = dash?.nextArgentinaXV
    val pumas7s = dash?.nextArgentina7s
    val topNews = dash?.news?.take(4).orEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        item {
            SectionBackdropBanner(
                backgroundRes = R.drawable.bg_rugby_action,
                eyebrow = "Centro Internacional",
                title = "Rugby Pro",
                subtitle = if (dash != null) {
                    "Los Pumas XV (DT ${dash.coachXV}) · Seven (DT ${dash.coach7s})"
                } else {
                    "Cargando temporada..."
                },
                trailing = {
                    if (live.isNotEmpty()) PulsingLiveIndicator("${live.size} EN VIVO")
                }
            )
        }

        item {
            GlassCard(highlightAccent = live.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (live.isNotEmpty()) "${live.size} partidos jugándose ahora" else "Jornada de hoy",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (todayUpcoming.isNotEmpty()) {
                                "${todayUpcoming.size} partidos programados para hoy"
                            } else {
                                "Seguimiento automático cada ${state.config.liveRefreshSec} s"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(onClick = { viewModel.selectSection(AppSection.VIVO) }) {
                        Text("Ir a En Vivo")
                    }
                }
            }
        }

        if (live.isNotEmpty()) {
            item {
                Text("En juego ahora", style = MaterialTheme.typography.titleLarge, color = PitchGreen)
            }
            items(live.take(3), key = { it.id }) { m ->
                MatchScoreCard(match = m, favoriteTeam = state.config.favoriteTeam, onClick = { viewModel.openMatchDetail(m) })
            }
        } else if (todayUpcoming.isNotEmpty()) {
            item {
                Text("Próximos de hoy", style = MaterialTheme.typography.titleLarge, color = ElectricCyan)
            }
            items(todayUpcoming.take(3), key = { it.id }) { m ->
                MatchScoreCard(match = m, favoriteTeam = state.config.favoriteTeam, onClick = { viewModel.openMatchDetail(m) })
            }
        }

        if (pumasXV != null) {
            item {
                Text("Los Pumas XV · Próxima cita", style = MaterialTheme.typography.titleMedium, color = MetallicGold)
                Spacer(Modifier.height(Spacing.xs))
                MatchScoreCard(match = pumasXV, favoriteTeam = state.config.favoriteTeam, onClick = { viewModel.openMatchDetail(pumasXV) })
            }
        }

        if (pumas7s != null) {
            item {
                Text("Los Pumas 7s · Circuito Mundial", style = MaterialTheme.typography.titleMedium, color = ElectricCyan)
                Spacer(Modifier.height(Spacing.xs))
                MatchScoreCard(match = pumas7s, favoriteTeam = state.config.favoriteTeam, onClick = { viewModel.openMatchDetail(pumas7s) })
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                QuickNavCard("Fixture y\nresultados", Icons.Outlined.CalendarMonth, Modifier.weight(1f)) {
                    viewModel.selectSection(AppSection.FIXTURE)
                }
                QuickNavCard("Tabla de\nposiciones", Icons.Outlined.EmojiEvents, Modifier.weight(1f)) {
                    viewModel.selectSection(AppSection.TABLAS)
                }
                QuickNavCard("Comparar\nequipos", Icons.Outlined.CompareArrows, Modifier.weight(1f)) {
                    viewModel.selectSection(AppSection.COMPARACION)
                }
            }
        }

        if (topNews.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Actualidad del rugby", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
                    OutlinedButton(onClick = { viewModel.selectSection(AppSection.NOTICIAS) }) {
                        Text("Ver todas")
                    }
                }
            }
            items(topNews, key = { it.id }) { art ->
                GlassCard(onClick = { viewModel.selectSection(AppSection.NOTICIAS) }) {
                    Text(
                        text = art.category.ifBlank { "Rugby" },
                        style = MaterialTheme.typography.labelMedium,
                        color = MetallicGold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(art.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    if (art.publishedLabel.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(RugbyDateUtils.normalizeDisplayDate(art.publishedLabel), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickNavCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.height(92.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(Spacing.sm),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = title, tint = PitchGreen)
            Spacer(Modifier.height(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OnboardingOverlay(
    initialFavorite: String,
    onComplete: (String) -> Unit
) {
    var step by remember { mutableStateOf(0) }
    var favorite by remember { mutableStateOf(initialFavorite.ifBlank { "Argentina" }) }
    val bgRes = if (step == 1) R.drawable.bg_rugby_stadium else R.drawable.bg_rugby_action

    val slides = listOf(
        Triple(
            "RUGBY PRO · EN VIVO",
            "Marcadores, eventos y estadísticas al segundo",
            "Seguí cada try, conversión, tarjeta y estadística en tiempo real, con alertas inteligentes y cero distracciones."
        ),
        Triple(
            "PRECISIÓN PREVIA AL KICKOFF",
            "Todo listo 30 minutos antes",
            "Planteles, entrenadores, camisetas titular y suplente, fotos oficiales y clima del estadio precargados automáticamente incluso sin conexión."
        ),
        Triple(
            "TU EQUIPO FAVORITO",
            "Personalizá tu experiencia",
            "Elegí tu selección o club favorito para destacarlo en tablas, rankings mundiales, fixture y partidos en vivo."
        )
    )
    val (_, title, desc) = slides[step]
    val eyebrow = slides[step].first

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PitchDarkBg)
    ) {
        Image(
            painter = painterResource(id = bgRes),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().alpha(0.42f)
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            PitchDarkBg.copy(alpha = 0.65f),
                            PitchDarkBg.copy(alpha = 0.88f),
                            PitchDarkBg.copy(alpha = 0.98f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.logo_rugby_master),
                        contentDescription = "Rugby Pro Logo",
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Text("RUGBY PRO", style = MaterialTheme.typography.titleLarge, color = Color(0xFFF5F5F5))
                }
                TextButton(
                    onClick = { onComplete(favorite) },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Omitir", color = MetallicGold)
                }
            }

            Column(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.lg)) {
                Text(eyebrow, style = MaterialTheme.typography.labelMedium, color = PitchGreen, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(Spacing.xs))
                Text(title, style = MaterialTheme.typography.headlineMedium, color = Color(0xFFF5F5F5))
                Spacer(Modifier.height(Spacing.sm))
                Text(desc, style = MaterialTheme.typography.bodyLarge, color = Color(0xFFD0D7E2))

                if (step == 2) {
                    Spacer(Modifier.height(Spacing.md))
                    val quickTeams = listOf(
                        "Argentina", "Los Pumas 7s", "Newman", "SIC", "Hindú",
                        "Pampas", "Dogos XV", "New Zealand", "South Africa", "France", "Ireland", "England"
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        quickTeams.forEach { t ->
                            FilterChip(
                                selected = favorite.equals(t, ignoreCase = true),
                                onClick = { favorite = t },
                                leadingIcon = if (favorite.equals(t, ignoreCase = true)) {
                                    { Icon(Icons.Outlined.Star, contentDescription = null, tint = MetallicGold, modifier = Modifier.size(16.dp)) }
                                } else null,
                                label = { Text(t) },
                                colors = rugbyChipColors(),
                                modifier = Modifier.heightIn(min = 48.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(Spacing.md))
                    OutlinedTextField(
                        value = favorite,
                        onValueChange = { favorite = it },
                        label = { Text("O escribí otro equipo / club") },
                        singleLine = true,
                        colors = rugbyFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(3) { idx ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (idx == step) PitchGreen else Color.White.copy(alpha = 0.28f),
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .width(if (idx == step) 28.dp else 10.dp)
                                .height(6.dp)
                        ) {}
                    }
                }
                Spacer(Modifier.height(Spacing.md))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    if (step > 0) {
                        OutlinedButton(
                            onClick = { step-- },
                            modifier = Modifier.weight(1f).heightIn(min = 50.dp)
                        ) {
                            Text("Anterior", color = Color(0xFFF5F5F5))
                        }
                    }
                    Button(
                        onClick = {
                            if (step < 2) step++ else onComplete(favorite)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PitchGreen),
                        modifier = Modifier.weight(1.4f).heightIn(min = 50.dp)
                    ) {
                        Text(if (step < 2) "Siguiente" else "Comenzar")
                    }
                }
            }
        }
    }
}
