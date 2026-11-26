package com.rugbypro.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.rugbypro.app.domain.model.AppSection
import com.rugbypro.app.domain.model.PlayerProfile
import com.rugbypro.app.domain.model.TeamProfile
import com.rugbypro.app.domain.model.ThemeMode
import com.rugbypro.app.live.RugbyNotificationManager
import com.rugbypro.app.ui.RugbyUiState
import com.rugbypro.app.ui.RugbyViewModel
import com.rugbypro.app.ui.components.GlassCard
import com.rugbypro.app.ui.components.TeamCrestWithFlag
import com.rugbypro.app.ui.theme.LiveCrimson
import com.rugbypro.app.ui.theme.MetallicGold
import com.rugbypro.app.ui.theme.PendingAmber
import com.rugbypro.app.ui.theme.PitchGreen
import com.rugbypro.app.ui.theme.Spacing

@Composable
fun TeamsSquadsScreen(
    state: RugbyUiState,
    viewModel: RugbyViewModel
) {
    val allTeams = state.dashboard?.teams.orEmpty()
    val filtered = remember(allTeams, state.teamGroupFilter, state.teamQuery) {
        val q = state.teamQuery.trim().lowercase()
        allTeams.filter { t ->
            val matchGrp = state.teamGroupFilter == "todos" ||
                t.groups.any { it.equals(state.teamGroupFilter, true) }
            val matchQ = q.isEmpty() ||
                t.name.lowercase().contains(q) ||
                t.coach.lowercase().contains(q) ||
                t.division.lowercase().contains(q)
            matchGrp && matchQ
        }
    }
    var selectedPlayer by remember { mutableStateOf<Pair<String, PlayerProfile>?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        item {
            OutlinedTextField(
                value = state.teamQuery,
                onValueChange = { viewModel.setTeamFilter(query = it) },
                label = { Text("Buscar club, selección, división o DT (${allTeams.size})") },
                singleLine = true,
                colors = rugbyFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(Spacing.sm))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                listOf(
                    "todos" to "Todos",
                    "nations" to "Selecciones",
                    "urba" to "URBA",
                    "top14" to "Top 14",
                    "clubs" to "Clubes"
                ).forEach { (k, lbl) ->
                    FilterChip(
                        selected = state.teamGroupFilter == k,
                        onClick = { viewModel.setTeamFilter(group = k) },
                        label = { Text(lbl) },
                        colors = rugbyChipColors(),
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                }
            }
        }

        items(filtered.take(120), key = { "${it.key}_${it.name}" }) { team ->
            GlassCard(
                highlightAccent = team.name.contains("Argentina", true),
                onClick = { viewModel.openTeamDetail(team) }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TeamCrestWithFlag(team.crestUrl, team.flagUrl, team.name, size = 48.dp)
                    Spacer(Modifier.width(Spacing.md))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(team.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        val meta = listOfNotNull(
                            team.division.takeIf { it.isNotBlank() }?.let { "URBA $it" },
                            team.coach.takeIf { it.isNotBlank() }?.let { "DT: $it" },
                            team.worldRank?.let { "Ranking #$it" }
                        ).joinToString(" · ").ifBlank { team.leagues.firstOrNull() ?: "Sin datos disponibles" }
                        Text(meta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (team.roster.isNotEmpty()) {
                            Text(
                                text = "Plantel verificado: ${team.roster.size} jugadores",
                                style = MaterialTheme.typography.labelMedium,
                                color = PitchGreen
                            )
                        }
                    }
                }
            }
        }
    }

    state.selectedTeam?.let { team ->
        AlertDialog(
            onDismissRequest = { viewModel.openTeamDetail(null) },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurface,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TeamCrestWithFlag(team.crestUrl, team.flagUrl, team.name, size = 42.dp)
                    Spacer(Modifier.width(Spacing.sm))
                    Text(team.name, style = MaterialTheme.typography.titleLarge)
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Text("Entrenador: ${team.coach.ifBlank { "Sin datos disponibles" }}")
                    Text("Estadio / Cancha: ${team.stadium.ifBlank { "Sin datos disponibles" }}")
                    Text("Torneos: ${team.leagues.joinToString(", ").ifBlank { "Sin datos disponibles" }}")
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    Text("Plantel oficial", style = MaterialTheme.typography.titleMedium, color = PitchGreen)
                    if (team.roster.isEmpty()) {
                        Text("Plantel: Sin datos disponibles", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        team.roster.forEach { pl ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                    .padding(Spacing.sm),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "#${pl.jersey.ifBlank { "-" }} ${pl.name}${if (pl.captain) " (C)" else ""}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${pl.position.ifBlank { "Posición s/d" }} · ${pl.age?.let { "$it años" } ?: "Edad s/d"}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(onClick = { selectedPlayer = team.name to pl }) {
                                    Text("Ficha")
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.openTeamDetail(null) }) { Text("Cerrar") }
            }
        )
    }

    selectedPlayer?.let { (teamName, pl) ->
        AlertDialog(
            onDismissRequest = { selectedPlayer = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(pl.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text("Equipo: $teamName")
                    Text("Camiseta: ${pl.jersey.ifBlank { "Sin datos disponibles" }}")
                    Text("Posición: ${pl.position.ifBlank { "Sin datos disponibles" }}")
                    Text("Edad: ${pl.age?.toString() ?: "Sin datos disponibles"}")
                    Text("Caps: ${pl.caps?.toString() ?: "Sin datos disponibles"}")
                    Text("Altura: ${pl.height ?: "Sin datos disponibles"} · Peso: ${pl.weight ?: "Sin datos disponibles"}")
                }
            },
            confirmButton = { TextButton(onClick = { selectedPlayer = null }) { Text("Cerrar") } }
        )
    }
}

@Composable
fun StatsComparisonScreen(
    state: RugbyUiState,
    viewModel: RugbyViewModel
) {
    val teamComp = state.teamComparison
    val playerComp = state.playerComparison
    val comps = state.dashboard?.competitions.orEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        item {
            GlassCard(highlightAccent = true) {
                Text("Comparador de Equipos", style = MaterialTheme.typography.titleLarge, color = PitchGreen)
                Spacer(Modifier.height(Spacing.sm))
                if (teamComp == null) {
                    Text("Sin datos disponibles para comparar equipos.")
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        OutlinedButton(
                            onClick = { viewModel.cycleCompareTeams(isLeft = true) },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        ) {
                            Text(teamComp.left.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        OutlinedButton(
                            onClick = { viewModel.cycleCompareTeams(isLeft = false) },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        ) {
                            Text(teamComp.right.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Spacer(Modifier.height(Spacing.md))
                    ComparisonStatRow("Ranking Mundial", teamComp.left.worldRank?.let { "#$it" } ?: "S/D", teamComp.right.worldRank?.let { "#$it" } ?: "S/D")
                    ComparisonStatRow("Partidos Hist.", "${teamComp.leftPlayed}", "${teamComp.rightPlayed}")
                    ComparisonStatRow("Victorias", "${teamComp.leftWins}", "${teamComp.rightWins}")
                    ComparisonStatRow("Pts a Favor", "${teamComp.leftPointsFor}", "${teamComp.rightPointsFor}")
                    ComparisonStatRow("Pts en Contra", "${teamComp.leftPointsAgainst}", "${teamComp.rightPointsAgainst}")
                }
            }
        }

        item {
            GlassCard {
                Text("Comparador de Jugadores", style = MaterialTheme.typography.titleLarge, color = MetallicGold)
                Spacer(Modifier.height(Spacing.sm))
                if (playerComp == null) {
                    Text("Sin datos disponibles de planteles para comparar.")
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        OutlinedButton(
                            onClick = { viewModel.cycleComparePlayers(isLeft = true) },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        ) {
                            Text(playerComp.left.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        OutlinedButton(
                            onClick = { viewModel.cycleComparePlayers(isLeft = false) },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        ) {
                            Text(playerComp.right.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Spacer(Modifier.height(Spacing.md))
                    ComparisonStatRow("Equipo", playerComp.leftTeam, playerComp.rightTeam)
                    ComparisonStatRow("Posición", playerComp.left.position.ifBlank { "S/D" }, playerComp.right.position.ifBlank { "S/D" })
                    ComparisonStatRow("Edad", playerComp.left.age?.toString() ?: "S/D", playerComp.right.age?.toString() ?: "S/D")
                    ComparisonStatRow("Caps", playerComp.left.caps?.toString() ?: "S/D", playerComp.right.caps?.toString() ?: "S/D")
                }
            }
        }

        if (comps.isNotEmpty()) {
            item {
                GlassCard {
                    Text("Competencias Cubiertas (${comps.size} ligas)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(Spacing.sm))
                    comps.forEach { c ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(c.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text("${c.region} · ${c.category} · ${c.season}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("${c.teamsCount} eq.", style = MaterialTheme.typography.labelMedium, color = PitchGreen)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ComparisonStatRow(label: String, leftVal: String, rightVal: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(leftVal, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = PitchGreen, modifier = Modifier.weight(1f))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = Spacing.sm))
        Text(rightVal, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MetallicGold, modifier = Modifier.weight(1f))
    }
}

@Composable
fun NewsScreen(
    state: RugbyUiState,
    viewModel: RugbyViewModel
) {
    val context = LocalContext.current
    val allNews = state.dashboard?.news.orEmpty()
    val categories = remember(allNews) {
        listOf("Todas") + allNews.map { it.category }.filter { it.isNotBlank() }.distinct()
    }
    val visible = remember(allNews, state.newsCategory, state.showSavedNewsOnly) {
        allNews.filter { n ->
            val matchSaved = !state.showSavedNewsOnly || n.savedOffline
            val matchCat = state.newsCategory == "Todas" || n.category == state.newsCategory
            matchSaved && matchCat
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        item {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                FilterChip(
                    selected = !state.showSavedNewsOnly,
                    onClick = { viewModel.setNewsFilter(savedOnly = false) },
                    label = { Text("Todas (${allNews.size})") },
                    colors = rugbyChipColors(),
                    modifier = Modifier.heightIn(min = 48.dp)
                )
                FilterChip(
                    selected = state.showSavedNewsOnly,
                    onClick = { viewModel.setNewsFilter(savedOnly = true) },
                    label = { Text("Guardadas (${allNews.count { it.savedOffline }})") },
                    colors = rugbyChipColors(),
                    modifier = Modifier.heightIn(min = 48.dp)
                )
                categories.forEach { cat ->
                    if (cat != "Todas") {
                        FilterChip(
                            selected = state.newsCategory == cat,
                            onClick = { viewModel.setNewsFilter(category = if (state.newsCategory == cat) "Todas" else cat) },
                            label = { Text(cat) },
                            colors = rugbyChipColors(),
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }
            }
        }

        if (visible.isEmpty()) {
            item {
                GlassCard {
                    Text(
                        text = if (state.showSavedNewsOnly) "No tenés noticias guardadas todavía." else "Noticias: Sin datos disponibles",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            items(visible, key = { it.id }) { art ->
                GlassCard {
                    if (art.imageUrl.isNotBlank()) {
                        SubcomposeAsyncImage(
                            model = art.imageUrl,
                            contentDescription = art.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(14.dp))
                        )
                        Spacer(Modifier.height(Spacing.sm))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${art.source} · ${art.publishedLabel}",
                            style = MaterialTheme.typography.labelMedium,
                            color = PitchGreen,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.toggleSavedNews(art) }) {
                            Icon(
                                imageVector = if (art.savedOffline) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = "Guardar noticia para leer offline",
                                tint = MetallicGold
                            )
                        }
                    }
                    Text(art.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    if (art.summary.isNotBlank()) {
                        Spacer(Modifier.height(Spacing.xs))
                        Text(art.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                    if (art.url.isNotBlank()) {
                        Spacer(Modifier.height(Spacing.sm))
                        OutlinedButton(
                            onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(art.url))) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Outlined.OpenInNew, contentDescription = null)
                            Spacer(Modifier.width(Spacing.xs))
                            Text("Leer nota completa")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    state: RugbyUiState,
    viewModel: RugbyViewModel,
    onRequestNotificationPermission: () -> Unit
) {
    val context = LocalContext.current
    val hasPerm = RugbyNotificationManager.hasNotificationPermission(context)
    var favInput by remember(state.config.favoriteTeam) { mutableStateOf(state.config.favoriteTeam) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        item {
            GlassCard {
                Text("Apariencia", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(Spacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    ThemeMode.entries.forEach { tm ->
                        FilterChip(
                            selected = state.config.themeMode == tm,
                            onClick = { viewModel.updateConfig { it.copy(themeMode = tm) } },
                            label = { Text(tm.label) },
                            colors = rugbyChipColors(),
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.md))
                OutlinedTextField(
                    value = favInput,
                    onValueChange = {
                        favInput = it
                        viewModel.updateConfig { c -> c.copy(favoriteTeam = it) }
                    },
                    label = { Text("Equipo favorito (resaltado en tablas)") },
                    singleLine = true,
                    colors = rugbyFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        item {
            GlassCard {
                Text("Frecuencia en vivo y red", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.height(Spacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    listOf(30, 60).forEach { sec ->
                        FilterChip(
                            selected = state.config.liveRefreshSec == sec,
                            onClick = { viewModel.updateConfig { it.copy(liveRefreshSec = sec) } },
                            label = { Text("Cada ${sec}s") },
                            colors = rugbyChipColors(),
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    listOf(6000L to "Timeout 6s", 10000L to "Timeout 10s", 15000L to "Timeout 15s").forEach { (ms, lbl) ->
                        FilterChip(
                            selected = state.config.apiTimeoutMs == ms,
                            onClick = { viewModel.updateConfig { it.copy(apiTimeoutMs = ms) } },
                            label = { Text(lbl) },
                            colors = rugbyChipColors(),
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }
            }
        }

        item {
            GlassCard {
                Text("Notificaciones Push en Vivo", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(Spacing.sm))
                if (!hasPerm) {
                    Button(
                        onClick = onRequestNotificationPermission,
                        colors = ButtonDefaults.buttonColors(containerColor = PitchGreen),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Text("Permitir notificaciones Android 13+")
                    }
                    Spacer(Modifier.height(Spacing.sm))
                }
                SettingToggleRow("Alertas de Try", state.config.notifyTries) { v ->
                    viewModel.updateConfig { it.copy(notifyTries = v) }
                }
                SettingToggleRow("Tarjetas rojas", state.config.notifyRedCards) { v ->
                    viewModel.updateConfig { it.copy(notifyRedCards = v) }
                }
                SettingToggleRow("Final del partido", state.config.notifyFullTime) { v ->
                    viewModel.updateConfig { it.copy(notifyFullTime = v) }
                }
            }
        }

        item {
            GlassCard {
                Text("Transparencia y Privacidad", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = "Rugby Pro no recolecta datos personales, ubicación ni analíticas externas. Toda preferencia y noticia guardada permanece localmente en tu dispositivo (cumple Google Play Data Safety y App Store Privacy Nutrition Labels).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Spacing.sm))
                OutlinedButton(
                    onClick = { viewModel.selectSection(AppSection.FUENTES) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Text("Abrir Estado de fuentes")
                }
            }
        }
    }
}

@Composable
private fun SettingToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun SourcesStatusScreen(
    state: RugbyUiState,
    viewModel: RugbyViewModel
) {
    val sources = state.dashboard?.sourcesHealth.orEmpty()
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
                        Text("Estado de Fuentes", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "Cascada: Oficiales -> ESPN/APIs -> Room Offline",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(onClick = { viewModel.refreshNow(true) }) {
                        Text("Probar ahora")
                    }
                }
            }
        }

        if (sources.isEmpty()) {
            item {
                GlassCard {
                    Text("Sin chequeos registrados todavía. Tocá 'Probar ahora' para diagnosticar la cascada.")
                }
            }
        } else {
            items(sources, key = { it.id }) { s ->
                GlassCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Nivel ${s.tier} · ${s.name}", style = MaterialTheme.typography.titleMedium)
                            Text("${s.detail} · ${s.latencyMs}ms (${s.checkedAtArg})", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            text = s.statusLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                s.isHealthy -> PitchGreen
                                s.statusLabel.contains("CACH") || s.statusLabel.contains("ESPERA") -> PendingAmber
                                else -> LiveCrimson
                            }
                        )
                    }
                }
            }
        }
    }
}
