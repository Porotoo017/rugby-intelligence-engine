package com.rugbypro.app.ui.screens

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rugbypro.app.domain.model.MatchStatus
import com.rugbypro.app.domain.model.RugbyMatch
import com.rugbypro.app.ui.RugbyUiState
import com.rugbypro.app.ui.RugbyViewModel
import com.rugbypro.app.ui.components.GlassCard
import com.rugbypro.app.ui.components.LiveStatsSection
import com.rugbypro.app.ui.components.MatchScoreCard
import com.rugbypro.app.ui.components.MatchTimelineSection
import com.rugbypro.app.ui.components.PulsingLiveIndicator
import com.rugbypro.app.ui.components.TeamCrestWithFlag
import com.rugbypro.app.ui.components.VerificationBadge
import com.rugbypro.app.ui.theme.MetallicGold
import com.rugbypro.app.ui.theme.PitchGreen
import com.rugbypro.app.ui.theme.Spacing
import java.util.Locale

@Composable
internal fun rugbyChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
    labelColor = MaterialTheme.colorScheme.onSurface,
    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
    selectedLabelColor = MaterialTheme.colorScheme.primary
)

@Composable
internal fun rugbyFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline
)

@Composable
fun LiveMatchesScreen(
    state: RugbyUiState,
    viewModel: RugbyViewModel
) {
    val live = state.liveMatches
    val upcoming = remember(state.dashboard) {
        state.dashboard?.matches?.filter { it.status == MatchStatus.SCHEDULED }?.take(8).orEmpty()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        item {
            GlassCard(highlightAccent = live.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Centro de Partidos en Vivo",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Actualización automática cada ${state.config.liveRefreshSec} s · Cascada oficial + ESPN",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (live.isNotEmpty()) PulsingLiveIndicator()
                }
            }
        }

        if (live.isEmpty()) {
            item {
                GlassCard {
                    Text(
                        text = "Sin partidos en vivo en este momento",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        text = "Cuando comience un encuentro oficial verás aquí el reloj en tiempo real, tries, tarjetas, estadísticas y alertas.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (upcoming.isNotEmpty()) {
                item {
                    Text(
                        text = "Próximos partidos en agenda",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                items(upcoming, key = { it.id }) { m ->
                    MatchScoreCard(match = m, onClick = { viewModel.openMatchDetail(m) })
                }
            }
        } else {
            items(live, key = { it.id }) { m ->
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    MatchScoreCard(match = m, onClick = { viewModel.openMatchDetail(m) })
                    GlassCard {
                        Text("Cronología de eventos", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(Spacing.sm))
                        MatchTimelineSection(m.events)
                        Spacer(Modifier.height(Spacing.md))
                        Text("Estadísticas en vivo", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.height(Spacing.sm))
                        LiveStatsSection(m.liveStats)
                    }
                }
            }
        }
    }
}

@Composable
fun FixtureResultsScreen(
    state: RugbyUiState,
    viewModel: RugbyViewModel
) {
    val groups = listOf(
        "todos" to "Todos",
        "argentina" to "Los Pumas",
        "urba" to "URBA",
        "sra" to "SRA",
        "top14" to "Top 14",
        "clubs" to "Clubes",
        "copas" to "Copas",
        "nations" to "Naciones",
        "sevens" to "Seven 7s",
        "worldcup" to "Mundial"
    )
    val matches = state.filteredMatches

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        item {
            OutlinedTextField(
                value = state.fixtureQuery,
                onValueChange = { viewModel.setFixtureFilter(query = it) },
                label = { Text("Buscar equipo, torneo o ciudad") },
                singleLine = true,
                colors = rugbyFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(Spacing.sm))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                groups.forEach { (key, label) ->
                    FilterChip(
                        selected = state.fixtureGroup == key,
                        onClick = { viewModel.setFixtureFilter(group = key) },
                        label = { Text(label) },
                        colors = rugbyChipColors(),
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                }
            }
            Spacer(Modifier.height(Spacing.xs))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                FilterChip(
                    selected = state.fixtureStatus == null,
                    onClick = { viewModel.setFixtureFilter(status = null) },
                    label = { Text("Todos (${state.dashboard?.matches?.size ?: 0})") },
                    colors = rugbyChipColors(),
                    modifier = Modifier.heightIn(min = 48.dp)
                )
                FilterChip(
                    selected = state.fixtureStatus == MatchStatus.SCHEDULED,
                    onClick = { viewModel.setFixtureFilter(status = MatchStatus.SCHEDULED) },
                    label = { Text("Fixture") },
                    colors = rugbyChipColors(),
                    modifier = Modifier.heightIn(min = 48.dp)
                )
                FilterChip(
                    selected = state.fixtureStatus == MatchStatus.FINISHED,
                    onClick = { viewModel.setFixtureFilter(status = MatchStatus.FINISHED) },
                    label = { Text("Resultados") },
                    colors = rugbyChipColors(),
                    modifier = Modifier.heightIn(min = 48.dp)
                )
            }
        }

        if (matches.isEmpty()) {
            item {
                GlassCard {
                    Text(
                        text = "Sin datos disponibles para este filtro",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(matches.take(120), key = { it.id }) { m ->
                MatchScoreCard(match = m, onClick = { viewModel.openMatchDetail(m) })
            }
        }
    }
}

@Composable
fun StandingsScreen(
    state: RugbyUiState,
    viewModel: RugbyViewModel
) {
    val dash = state.dashboard
    var mode by remember { mutableStateOf("TABLAS") } // TABLAS | RANK_M | RANK_W
    val tables = dash?.standings.orEmpty()
    val activeTable = tables.firstOrNull { it.id == state.selectedStandingId } ?: tables.firstOrNull()
    val fav = state.config.favoriteTeam.trim().lowercase()

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
                    selected = mode == "TABLAS",
                    onClick = { mode = "TABLAS" },
                    label = { Text("Tablas de Torneos") },
                    colors = rugbyChipColors(),
                    modifier = Modifier.heightIn(min = 48.dp)
                )
                FilterChip(
                    selected = mode == "RANK_M",
                    onClick = { mode = "RANK_M" },
                    label = { Text("World Ranking Masc.") },
                    colors = rugbyChipColors(),
                    modifier = Modifier.heightIn(min = 48.dp)
                )
                FilterChip(
                    selected = mode == "RANK_W",
                    onClick = { mode = "RANK_W" },
                    label = { Text("World Ranking Fem.") },
                    colors = rugbyChipColors(),
                    modifier = Modifier.heightIn(min = 48.dp)
                )
            }
        }

        if (mode == "TABLAS") {
            if (tables.isEmpty()) {
                item {
                    GlassCard {
                        Text("Tablas de posiciones: Sin datos disponibles", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                item {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        tables.forEach { tbl ->
                            FilterChip(
                                selected = activeTable?.id == tbl.id,
                                onClick = { viewModel.selectStanding(tbl.id) },
                                label = { Text(tbl.shortName) },
                                colors = rugbyChipColors(),
                                modifier = Modifier.heightIn(min = 48.dp)
                            )
                        }
                    }
                }
                activeTable?.let { tbl ->
                    item {
                        GlassCard(highlightAccent = true) {
                            Text(tbl.name, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(Spacing.sm))
                            Column(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                // Table header
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("#", Modifier.width(28.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    Text("Equipo", Modifier.width(150.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    listOf("PJ", "PG", "PE", "PP", "PF", "PC", "Dif", "Bon", "Pts").forEach { col ->
                                        Text(col, Modifier.width(40.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Spacer(Modifier.height(Spacing.xs))
                                tbl.rows.forEachIndexed { idx, r ->
                                    val isFav = fav.isNotBlank() && r.teamName.lowercase().contains(fav)
                                    val rowBg = when {
                                        isFav -> PitchGreen.copy(alpha = 0.22f)
                                        idx % 2 == 1 -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                        else -> Color.Transparent
                                    }
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(rowBg)
                                            .padding(horizontal = Spacing.sm, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("${r.pos}", Modifier.width(28.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MetallicGold)
                                        Row(Modifier.width(150.dp), verticalAlignment = Alignment.CenterVertically) {
                                            TeamCrestWithFlag(r.crestUrl, "", r.teamName, size = 24.dp)
                                            Spacer(Modifier.width(6.dp))
                                            Text(r.teamName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        listOf("${r.played}", "${r.won}", "${r.drawn}", "${r.lost}", "${r.pf}", "${r.pc}", "${r.diff}", "${r.bonus}").forEach { v ->
                                            Text(v, Modifier.width(40.dp), style = MaterialTheme.typography.labelMedium)
                                        }
                                        Text("${r.pts}", Modifier.width(40.dp), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = PitchGreen)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            val rows = if (mode == "RANK_M") dash?.rankingsMen.orEmpty() else dash?.rankingsWomen.orEmpty()
            if (rows.isEmpty()) {
                item {
                    GlassCard {
                        Text("Rankings World Rugby: Sin datos disponibles", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                items(rows, key = { "${mode}_${it.pos}_${it.teamName}" }) { rk ->
                    val isFav = fav.isNotBlank() && rk.teamName.lowercase().contains(fav)
                    GlassCard(highlightAccent = isFav || rk.teamName.contains("Argentina", true)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "#${rk.pos}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MetallicGold,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(44.dp)
                            )
                            TeamCrestWithFlag(rk.crestUrl, rk.flagUrl, rk.teamName, size = 40.dp)
                            Spacer(Modifier.width(Spacing.sm))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(rk.teamName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                                Text(rk.abbr, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                text = String.format(Locale.US, "%.2f pts", rk.points),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = PitchGreen
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MatchDetailDialog(
    match: RugbyMatch,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        title = {
            Column {
                Text("${match.homeName} vs ${match.awayName}", style = MaterialTheme.typography.titleLarge)
                Text("${match.leagueName} · ${match.dateArg}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    VerificationBadge(match.verificationStatus)
                    if (match.homeScore != null && match.awayScore != null) {
                        Text(
                            text = "${match.homeScore} - ${match.awayScore}",
                            style = MaterialTheme.typography.headlineMedium,
                            color = PitchGreen
                        )
                    }
                }
                Text(
                    text = "Fuentes consultadas: ${match.verifiedSources.joinToString(", ").ifBlank { "Caché local" }}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val venue = listOf(match.venueName, match.venueCity).filter { it.isNotBlank() }.joinToString(" · ")
                Text(
                    text = "Sede: ${venue.ifBlank { "Sin datos disponibles" }}",
                    style = MaterialTheme.typography.bodyMedium
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                Text("Eventos del partido", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                MatchTimelineSection(match.events)
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                Text("Estadísticas en vivo", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
                LiveStatsSection(match.liveStats)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}
