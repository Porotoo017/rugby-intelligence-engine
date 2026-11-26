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
import com.rugbypro.app.R
import com.rugbypro.app.domain.model.ActiveKitRole
import com.rugbypro.app.domain.model.AppSection
import com.rugbypro.app.domain.model.MatchStatus
import com.rugbypro.app.domain.model.RugbyMatch
import com.rugbypro.app.ui.RugbyUiState
import com.rugbypro.app.ui.RugbyViewModel
import com.rugbypro.app.ui.components.GlassCard
import com.rugbypro.app.ui.components.LiveStatsSection
import com.rugbypro.app.ui.components.MatchScoreCard
import com.rugbypro.app.ui.components.MatchTimelineSection
import com.rugbypro.app.ui.components.PulsingLiveIndicator
import com.rugbypro.app.ui.components.RugbyJerseyBadge
import com.rugbypro.app.ui.components.SectionBackdropBanner
import com.rugbypro.app.ui.components.TeamCrestWithFlag
import com.rugbypro.app.ui.theme.ElectricCyan
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

/**
 * Pantalla estricta "Partidos en vivo":
 * Muestra ÚNICAMENTE partidos en juego ahora (LIVE) y próximos partidos del día (SCHEDULED hoy).
 * Prohibido mostrar partidos finalizados en esta sección.
 */
@Composable
fun LiveMatchesScreen(
    state: RugbyUiState,
    viewModel: RugbyViewModel
) {
    val live = state.liveMatches
    val upcomingToday = state.upcomingTodayMatches

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        item {
            SectionBackdropBanner(
                backgroundRes = R.drawable.bg_rugby_action,
                eyebrow = "Tiempo Real",
                title = "Partidos en vivo",
                subtitle = "Actualización automática cada ${state.config.liveRefreshSec} s · Partidos activos y agenda de hoy",
                trailing = {
                    if (live.isNotEmpty()) PulsingLiveIndicator()
                }
            )
        }

        if (live.isEmpty() && upcomingToday.isEmpty()) {
            item {
                GlassCard {
                    Text(
                        text = "Sin partidos en vivo ni programados para hoy",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        text = "Cuando comience un encuentro o haya cruces programados en la fecha de hoy, verás aquí el marcador, eventos cronológicos y estadísticas en vivo.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(Spacing.md))
                    OutlinedButton(
                        onClick = { viewModel.selectSection(AppSection.FIXTURE) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Text("Ver agenda completa y resultados en Fixture")
                    }
                }
            }
        } else {
            if (live.isEmpty()) {
                item {
                    GlassCard {
                        Text(
                            text = "No hay encuentros jugándose en este instante",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Debajo figuran exclusivamente los próximos partidos programados para el día de hoy.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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

            if (upcomingToday.isNotEmpty()) {
                item {
                    Text(
                        text = "Próximos partidos de hoy (${upcomingToday.size})",
                        style = MaterialTheme.typography.titleLarge,
                        color = ElectricCyan
                    )
                }
                items(upcomingToday, key = { it.id }) { m ->
                    MatchScoreCard(match = m, onClick = { viewModel.openMatchDetail(m) })
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
    val list = state.filteredMatches

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                OutlinedTextField(
                    value = state.fixtureQuery,
                    onValueChange = { viewModel.setFixtureFilter(query = it) },
                    label = { Text("Equipo, torneo o sede") },
                    singleLine = true,
                    colors = rugbyFieldColors(),
                    modifier = Modifier.weight(1.4f)
                )
                OutlinedTextField(
                    value = state.fixtureDateQuery,
                    onValueChange = { viewModel.setFixtureFilter(dateQuery = it) },
                    label = { Text("Fecha (ej. 26/11)") },
                    singleLine = true,
                    colors = rugbyFieldColors(),
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(Spacing.sm))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                listOf(
                    null to "Todos",
                    MatchStatus.LIVE to "En vivo",
                    MatchStatus.SCHEDULED to "Próximos",
                    MatchStatus.FINISHED to "Finalizados"
                ).forEach { (st, lbl) ->
                    FilterChip(
                        selected = state.fixtureStatus == st,
                        onClick = { viewModel.setFixtureFilter(status = st) },
                        label = { Text(lbl) },
                        colors = rugbyChipColors(),
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                }
            }
            Spacer(Modifier.height(Spacing.sm))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                groups.forEach { (k, lbl) ->
                    FilterChip(
                        selected = state.fixtureGroup == k,
                        onClick = { viewModel.setFixtureFilter(group = k) },
                        label = { Text(lbl) },
                        colors = rugbyChipColors(),
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                }
            }
        }

        if (list.isEmpty()) {
            item {
                GlassCard {
                    Text(
                        text = "Sin datos disponibles para los filtros actuales.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(list.take(150), key = { it.id }) { m ->
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
    val standings = dash?.standings.orEmpty()
    val selected = standings.firstOrNull { it.id == state.selectedStandingId } ?: standings.firstOrNull()
    var rankMode by remember { mutableStateOf("torneos") }
    val fav = state.config.favoriteTeam.trim()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                listOf(
                    "torneos" to "Tablas de Torneos",
                    "wr_men" to "Ranking WR Masc.",
                    "wr_women" to "Ranking WR Fem."
                ).forEach { (k, lbl) ->
                    FilterChip(
                        selected = rankMode == k,
                        onClick = { rankMode = k },
                        label = { Text(lbl) },
                        colors = rugbyChipColors(),
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                }
            }
        }

        if (rankMode == "torneos") {
            if (standings.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        standings.forEach { st ->
                            FilterChip(
                                selected = st.id == selected?.id,
                                onClick = { viewModel.selectStanding(st.id) },
                                label = { Text(st.shortName) },
                                colors = rugbyChipColors(),
                                modifier = Modifier.heightIn(min = 48.dp)
                            )
                        }
                    }
                }
            }

            if (selected == null || selected.rows.isEmpty()) {
                item {
                    GlassCard { Text("Sin datos disponibles para tablas.") }
                }
            } else {
                item {
                    GlassCard {
                        Text(selected.name, style = MaterialTheme.typography.titleLarge, color = MetallicGold)
                        Spacer(Modifier.height(Spacing.sm))
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("#", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(24.dp))
                            Text("Equipo", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                            listOf("PJ", "PG", "PE", "PP", "PF", "PC", "DIF", "B", "PTS").forEach { col ->
                                Text(col, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(28.dp))
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                        selected.rows.forEachIndexed { idx, row ->
                            val isFav = fav.isNotBlank() && row.teamName.contains(fav, ignoreCase = true)
                            val rowBg = when {
                                isFav -> PitchGreen.copy(alpha = 0.18f)
                                idx % 2 == 1 -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                else -> Color.Transparent
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(rowBg)
                                    .padding(vertical = 8.dp, horizontal = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${row.pos}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (row.pos <= 4) PitchGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(24.dp)
                                )
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TeamCrestWithFlag(row.crestUrl, row.flagUrl, row.teamName, size = 24.dp)
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = row.teamName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isFav) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                listOf(row.played, row.won, row.drawn, row.lost, row.pf, row.pc, row.diff, row.bonus, row.pts).forEachIndexed { cIdx, v ->
                                    Text(
                                        text = "$v",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (cIdx == 8) FontWeight.Bold else FontWeight.Normal,
                                        color = if (cIdx == 8) MetallicGold else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.width(28.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            val rows = if (rankMode == "wr_men") dash?.rankingsMen.orEmpty() else dash?.rankingsWomen.orEmpty()
            items(rows, key = { "${it.pos}_${it.teamName}" }) { r ->
                val isFav = fav.isNotBlank() && r.teamName.contains(fav, ignoreCase = true)
                GlassCard(highlightAccent = isFav) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "#${r.pos}",
                            style = MaterialTheme.typography.titleLarge,
                            color = MetallicGold,
                            modifier = Modifier.width(42.dp)
                        )
                        TeamCrestWithFlag(r.crestUrl, r.flagUrl, r.teamName, size = 40.dp)
                        Spacer(Modifier.width(Spacing.md))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(r.teamName, style = MaterialTheme.typography.titleMedium)
                            val delta = r.prevPos - r.pos
                            Text(
                                text = when {
                                    delta > 0 -> "Subió $delta"
                                    delta < 0 -> "Bajó ${-delta}"
                                    else -> "Sin cambios"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = String.format(Locale.US, "%.2f", r.points),
                            style = MaterialTheme.typography.titleLarge,
                            color = PitchGreen
                        )
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
        title = {
            Column {
                Text(match.leagueName, style = MaterialTheme.typography.titleMedium, color = MetallicGold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${match.homeName} ${match.homeScore ?: "-"} : ${match.awayScore ?: "-"} ${match.awayName}",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${match.dateArg} · ${match.periodDisplay}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Escudos, banderas y camiseta oficial Titular / Suplente en el cruce
                GlassCard {
                    Text("Indumentaria y Equipos", style = MaterialTheme.typography.titleMedium, color = MetallicGold)
                    Spacer(Modifier.height(Spacing.sm))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            TeamCrestWithFlag(match.homeCrestUrl, match.homeFlagUrl, match.homeName, size = 44.dp)
                            Spacer(Modifier.height(6.dp))
                            Text(match.homeName, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            RugbyJerseyBadge(kit = match.homeKit, size = 34.dp)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = if (match.homeActiveKitRole != ActiveKitRole.UNKNOWN) {
                                    "Juega con ${match.homeActiveKitRole.label}"
                                } else {
                                    "Información no disponible"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (match.homeActiveKitRole != ActiveKitRole.UNKNOWN) PitchGreen else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(Spacing.sm))
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            TeamCrestWithFlag(match.awayCrestUrl, match.awayFlagUrl, match.awayName, size = 44.dp)
                            Spacer(Modifier.height(6.dp))
                            Text(match.awayName, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            RugbyJerseyBadge(kit = match.awayKit, size = 34.dp)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = if (match.awayActiveKitRole != ActiveKitRole.UNKNOWN) {
                                    "Juega con ${match.awayActiveKitRole.label}"
                                } else {
                                    "Información no disponible"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (match.awayActiveKitRole == ActiveKitRole.AWAY) MetallicGold else if (match.awayActiveKitRole == ActiveKitRole.HOME) PitchGreen else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (match.venueName.isNotBlank() || match.venueCity.isNotBlank()) {
                    Text(
                        text = "Estadio: ${listOf(match.venueName, match.venueCity).filter { it.isNotBlank() }.joinToString(", ")}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider()
                Text("Cronología del partido", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                MatchTimelineSection(match.events)

                HorizontalDivider()
                Text("Estadísticas en vivo", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
                LiveStatsSection(match.liveStats)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}
