package com.rugbypro.app.ui.screens

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SportsRugby
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
import com.rugbypro.app.ui.RugbyUiState
import com.rugbypro.app.ui.RugbyViewModel
import com.rugbypro.app.ui.components.GlassCard
import com.rugbypro.app.ui.components.MatchScoreCard
import com.rugbypro.app.ui.components.PulsingLiveIndicator
import com.rugbypro.app.ui.theme.ElectricCyan
import com.rugbypro.app.ui.theme.MetallicGold
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
    AppSection.ESTADISTICAS -> Icons.Outlined.BarChart
    AppSection.NOTICIAS -> Icons.Outlined.Article
    AppSection.CONFIGURACION -> Icons.Outlined.Settings
    AppSection.FUENTES -> Icons.Outlined.CloudSync
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
        delay(1300)
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
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(Modifier.width(Spacing.sm))
                                    Column {
                                        Text(
                                            text = state.currentSection.label,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = if (state.liveMatches.isNotEmpty()) "${state.liveMatches.size} en vivo" else "Rugby Pro",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (state.liveMatches.isNotEmpty()) PitchGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            actions = {
                                IconButton(
                                    onClick = { viewModel.selectSection(AppSection.FUENTES) },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.CloudSync,
                                        contentDescription = "Estado de fuentes",
                                        tint = if (state.dashboard?.isOfflineFallback == true) MetallicGold else PitchGreen
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.refreshNow(true) },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Outlined.Refresh, contentDescription = "Actualizar datos")
                                }
                            }
                        )
                    }
                ) { padding ->
                    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                        if (state.isSyncing) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        AnimatedContent(
                            targetState = state.currentSection,
                            label = "sectionTransition",
                            modifier = Modifier.weight(1f)
                        ) { section ->
                            when (section) {
                                AppSection.INICIO -> InicioScreen(state, viewModel)
                                AppSection.VIVO -> LiveMatchesScreen(state, viewModel)
                                AppSection.FIXTURE -> FixtureResultsScreen(state, viewModel)
                                AppSection.TABLAS -> StandingsScreen(state, viewModel)
                                AppSection.EQUIPOS -> TeamsSquadsScreen(state, viewModel)
                                AppSection.ESTADISTICAS -> StatsComparisonScreen(state, viewModel)
                                AppSection.NOTICIAS -> NewsScreen(state, viewModel)
                                AppSection.CONFIGURACION -> SettingsScreen(state, viewModel, onRequestNotificationPermission)
                                AppSection.FUENTES -> SourcesStatusScreen(state, viewModel)
                            }
                        }
                    }
                }
            }

            if (isTablet) {
                PermanentNavigationDrawer(
                    drawerContent = {
                        PermanentDrawerSheet(
                            modifier = Modifier.width(268.dp),
                            drawerContainerColor = MaterialTheme.colorScheme.surface,
                            drawerContentColor = MaterialTheme.colorScheme.onSurface
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
                            modifier = Modifier.width(292.dp),
                            drawerContainerColor = MaterialTheme.colorScheme.surface,
                            drawerContentColor = MaterialTheme.colorScheme.onSurface
                        ) {
                            DrawerMenuContent(
                                current = state.currentSection,
                                liveCount = state.liveMatches.size,
                                onSelect = { sec ->
                                    viewModel.selectSection(sec)
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
                onDismiss = { viewModel.openMatchDetail(null) }
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
                    text = "Centro en vivo & Datos",
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
            AppSection.ESTADISTICAS,
            AppSection.NOTICIAS
        )
        val systemSections = listOf(
            AppSection.CONFIGURACION,
            AppSection.FUENTES
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
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val scale by animateFloatAsState(
        targetValue = if (started) 1f else 0.94f,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "splashScale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (started) 1f else 0.35f,
        animationSpec = tween(750),
        label = "splashAlpha"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PitchDarkBg)
            .semantics { contentDescription = "Pantalla de bienvenida Rugby Pro" }
    ) {
        Image(
            painter = painterResource(
                id = if (isLandscape) R.drawable.splash_horizontal else R.drawable.splash_vertical
            ),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().scale(scale).alpha(alpha)
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Transparent, PitchDarkBg.copy(alpha = 0.86f))
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "RUGBY PRO",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "PASIÓN EN VIVO · DATOS VERIFICADOS",
                style = MaterialTheme.typography.labelMedium,
                color = ElectricCyan
            )
        }
    }
}

@Composable
fun InicioScreen(
    state: RugbyUiState,
    viewModel: RugbyViewModel
) {
    val dash = state.dashboard
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        item {
            GlassCard(highlightAccent = true) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Centro Internacional & URBA",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Actualizado: ${dash?.generatedAtArg ?: "Cargando..."}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (state.liveMatches.isNotEmpty()) {
                        PulsingLiveIndicator("${state.liveMatches.size} EN VIVO")
                    }
                }
                Spacer(Modifier.height(Spacing.sm))
                if (dash != null) {
                    Text(
                        text = "Head Coaches · Los Pumas XV: ${dash.coachXV} · Seven 7s: ${dash.coach7s}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        OutlinedButton(
                            onClick = { viewModel.selectSection(AppSection.VIVO) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Ver En Vivo")
                        }
                        OutlinedButton(
                            onClick = { viewModel.selectSection(AppSection.FIXTURE) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Fixture (${dash.matches.size})")
                        }
                    }
                }
            }
        }

        if (state.liveMatches.isNotEmpty()) {
            item {
                Text(
                    text = "Partidos en vivo ahora",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            items(state.liveMatches.take(4), key = { it.id }) { m ->
                MatchScoreCard(match = m, onClick = { viewModel.openMatchDetail(m) })
            }
        }

        dash?.nextArgentinaXV?.let { pumasXV ->
            item {
                Text(
                    text = "Próximo partido · Los Pumas XV",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            item {
                MatchScoreCard(match = pumasXV, onClick = { viewModel.openMatchDetail(pumasXV) })
            }
        }

        dash?.nextArgentina7s?.let { pumas7s ->
            item {
                Text(
                    text = "Próximo partido · Los Pumas 7s",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            item {
                MatchScoreCard(match = pumas7s, onClick = { viewModel.openMatchDetail(pumas7s) })
            }
        }

        if (dash != null && dash.news.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Últimas noticias",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    OutlinedButton(onClick = { viewModel.selectSection(AppSection.NOTICIAS) }) {
                        Text("Ver todas")
                    }
                }
            }
            items(dash.news.take(3), key = { it.id }) { art ->
                GlassCard(onClick = { viewModel.selectSection(AppSection.NOTICIAS) }) {
                    Text(
                        text = "${art.source} · ${art.category}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        text = art.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (art.publishedLabel.isNotBlank()) {
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            text = art.publishedLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
