package com.rugbypro.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.SubcomposeAsyncImage
import com.rugbypro.app.R
import com.rugbypro.app.domain.model.PlayerProfile
import com.rugbypro.app.domain.model.PlayerSearchEntry
import com.rugbypro.app.domain.model.TeamKit
import com.rugbypro.app.domain.model.TeamProfile
import com.rugbypro.app.domain.model.ThemeMode
import com.rugbypro.app.domain.usecase.RugbyDateUtils
import com.rugbypro.app.live.RugbyNotificationManager
import com.rugbypro.app.ui.RugbyUiState
import com.rugbypro.app.ui.RugbyViewModel
import com.rugbypro.app.ui.components.GlassCard
import com.rugbypro.app.ui.components.BroadcastPlayerMedallion
import com.rugbypro.app.ui.components.PlayerAvatar
import com.rugbypro.app.ui.components.RugbyJerseyBadge
import com.rugbypro.app.ui.components.SectionBackdropBanner
import com.rugbypro.app.ui.components.TeamCrestWithFlag
import com.rugbypro.app.ui.theme.ElectricCyan
import com.rugbypro.app.ui.theme.MetallicGold
import com.rugbypro.app.ui.theme.PitchDarkBg
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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        item {
            SectionBackdropBanner(
                backgroundRes = R.drawable.bg_rugby_stadium,
                eyebrow = "Clubes y Selecciones",
                title = "Equipos y planteles",
                subtitle = "Escudos, banderas oficiales, camisetas titular/suplente y fichas de jugadores"
            )
        }

        item {
            OutlinedTextField(
                value = state.teamQuery,
                onValueChange = { viewModel.setTeamFilter(query = it) },
                label = { Text("Buscar club, selección, división o DT (${allTeams.size})") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
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
                    "sra" to "SRA",
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

        items(filtered.take(140), key = { "${it.key}_${it.name}" }) { team ->
            GlassCard(
                highlightAccent = team.name.contains("Argentina", true),
                onClick = { viewModel.openTeamDetail(team) }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TeamCrestWithFlag(team.crestUrl, team.flagUrl, team.name, size = 50.dp)
                    Spacer(Modifier.width(Spacing.md))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(team.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        val meta = listOfNotNull(
                            team.division.takeIf { it.isNotBlank() }?.let { "URBA $it" },
                            team.coach.takeIf { it.isNotBlank() }?.let { "DT: $it" },
                            team.worldRank?.let { "Rank #$it" },
                            team.roster.takeIf { it.isNotEmpty() }?.let { "${it.size} jugadores" }
                        ).joinToString(" · ")
                        Text(
                            text = meta.ifBlank { team.leagues.joinToString(", ").ifBlank { "Información no disponible" } },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(Spacing.sm))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RugbyJerseyBadge(kit = team.homeKit, size = 28.dp)
                        if (team.awayKit != null) {
                            Spacer(Modifier.width(4.dp))
                            RugbyJerseyBadge(kit = team.awayKit, size = 24.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TeamDetailDialog(
    team: TeamProfile,
    onDismiss: () -> Unit,
    onSelectPlayer: (PlayerProfile) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TeamCrestWithFlag(team.crestUrl, team.flagUrl, team.name, size = 48.dp)
                Spacer(Modifier.width(Spacing.sm))
                Column {
                    Text(team.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = team.leagues.joinToString(" · ").ifBlank { team.division.ifBlank { "Equipo oficial" } },
                        style = MaterialTheme.typography.labelMedium,
                        color = MetallicGold
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Text("Entrenador: ${team.coach.ifBlank { "No disponible" }}")
                Text("Estadio / Sede: ${team.stadium.ifBlank { "No disponible" }}")
                team.worldRank?.let { Text("Ranking Mundial: #$it", color = PitchGreen, fontWeight = FontWeight.Bold) }

                if (team.historicalPlayed != null && team.historicalPlayed > 0) {
                    Text(
                        text = "Historial reciente: ${team.historicalPlayed} PJ · ${team.historicalWins ?: 0}G · ${team.historicalDraws ?: 0}E · ${team.historicalLosses ?: 0}P",
                        style = MaterialTheme.typography.labelMedium,
                        color = ElectricCyan
                    )
                } else {
                    Text("Estadísticas históricas: Sin datos disponibles", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                // Camisetas oficiales Titular y Suplente
                GlassCard {
                    Text("Camisetas Oficiales", style = MaterialTheme.typography.titleMedium, color = MetallicGold)
                    Spacer(Modifier.height(Spacing.sm))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        KitCardSlot(title = "Titular", kit = team.homeKit, modifier = Modifier.weight(1f))
                        KitCardSlot(title = "Suplente", kit = team.awayKit, modifier = Modifier.weight(1f))
                    }
                }

                HorizontalDivider()
                Text("Plantel (${team.roster.size})", style = MaterialTheme.typography.titleMedium, color = PitchGreen)
                if (team.roster.isEmpty()) {
                    Text("Sin datos disponibles de plantel.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(
                        text = "Tocá cualquier jugador para abrir su ficha completa",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    team.roster.forEach { p ->
                        GlassCard(onClick = { onSelectPlayer(p) }) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "#${p.jersey}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MetallicGold,
                                    modifier = Modifier.width(34.dp)
                                )
                                BroadcastPlayerMedallion(p.photoUrl, p.name, kit = team.homeKit ?: team.awayKit, jerseyNumber = p.jersey, isCaptain = p.captain, size = 48.dp, isOval = true)
                                Spacer(Modifier.width(Spacing.sm))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${p.name}${if (p.captain) " (C)" else ""}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "${p.position.ifBlank { p.posCode.ifBlank { "No disponible" } }} · ${if (p.starter) "Titular" else "Suplente"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text("Ficha ›", style = MaterialTheme.typography.labelMedium, color = PitchGreen)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}

@Composable
private fun KitCardSlot(
    title: String,
    kit: TeamKit?,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(Spacing.sm)
        ) {
            Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(6.dp))
            RugbyJerseyBadge(kit = kit, size = 40.dp)
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (kit != null && kit.verified) kit.pattern.replaceFirstChar { it.uppercaseChar() } else "Información no disponible",
                style = MaterialTheme.typography.labelSmall,
                color = if (kit != null && kit.verified) PitchGreen else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun PlayerDetailDialog(
    entry: PlayerSearchEntry,
    onDismiss: () -> Unit
) {
    val p = entry.player
    val heightText = p.height?.let { if (it.contains("cm") || it.contains("m")) it else "$it cm" } ?: "No disponible"
    val weightText = p.weight?.let { if (it.contains("kg")) it else "$it kg" } ?: "No disponible"
    val ageText = p.age?.let { "$it años" } ?: "No disponible"
    val capsText = p.caps?.toString() ?: "No disponible"
    val natText = p.nationality?.takeIf { it.isNotBlank() } ?: "No disponible"
    val flagToShow = p.nationalityFlagUrl ?: entry.teamFlagUrl

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(Spacing.md),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MetallicGold.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.bg_rugby_stadium),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().alpha(0.55f)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        PitchDarkBg.copy(alpha = 0.55f),
                                        PitchDarkBg.copy(alpha = 0.96f)
                                    )
                                )
                            )
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.sm).size(48.dp)
                    ) {
                        Icon(Icons.Outlined.Close, contentDescription = "Cerrar ficha", tint = Color.White)
                    }

                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(Spacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BroadcastPlayerMedallion(p.photoUrl, p.name, kit = entry.teamKit, jerseyNumber = p.jersey, isCaptain = p.captain, size = 94.dp, isOval = true)
                        Spacer(Modifier.width(Spacing.md))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = PitchGreen.copy(alpha = 0.2f),
                                    border = BorderStroke(1.dp, PitchGreen)
                                ) {
                                    Text(
                                        text = "#${p.jersey}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = PitchGreen,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                                if (p.captain) {
                                    Spacer(Modifier.width(6.dp))
                                    Icon(Icons.Outlined.Star, contentDescription = "Capitán", tint = MetallicGold, modifier = Modifier.size(18.dp))
                                    Text("Capitán", style = MaterialTheme.typography.labelSmall, color = MetallicGold)
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = p.name,
                                style = MaterialTheme.typography.headlineSmall,
                                color = Color(0xFFF5F5F5)
                            )
                            Text(
                                text = "${p.position.ifBlank { "No disponible" }} · ${entry.teamName}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFD1D9E6)
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.padding(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    GlassCard {
                        Text("Datos Personales y Biometría", style = MaterialTheme.typography.titleMedium, color = MetallicGold)
                        Spacer(Modifier.height(Spacing.sm))
                        PlayerDataRow("Nombre completo", p.name)
                        PlayerDataRow("Posición en cancha", "${p.position.ifBlank { "No disponible" }} ${p.posCode.takeIf { it.isNotBlank() }?.let { "($it)" } ?: ""}".trim())
                        PlayerDataRow("Número de camiseta", "#${p.jersey}")
                        PlayerDataRow("Altura", heightText)
                        PlayerDataRow("Peso", weightText)
                        PlayerDataRow("Edad", ageText)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Nacionalidad", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (flagToShow.isNotBlank() && natText != "No disponible") {
                                    SubcomposeAsyncImage(
                                        model = flagToShow,
                                        contentDescription = "Bandera de $natText",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(20.dp).clip(CircleShape)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text(natText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    GlassCard {
                        Text("Estadísticas y Trayectoria", style = MaterialTheme.typography.titleMedium, color = PitchGreen)
                        Spacer(Modifier.height(Spacing.sm))
                        PlayerDataRow("Rol habitual", if (p.starter) "Titular" else "Suplente")
                        PlayerDataRow("Caps internacionales", capsText)
                        PlayerDataRow("Tries registrados", p.tries?.toString() ?: "No disponible")
                        PlayerDataRow("Puntos totales", p.points?.toString() ?: "No disponible")
                        PlayerDataRow("Tackles", p.tackles?.toString() ?: "No disponible")
                        PlayerDataRow("Información adicional", p.extraInfo ?: "No disponible")
                    }

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = PitchGreen),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Text("Volver", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerDataRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (value == "No disponible") MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun ComparisonScreen(
    state: RugbyUiState,
    viewModel: RugbyViewModel
) {
    val teams = state.dashboard?.teams.orEmpty()
    val countries = remember(teams) {
        listOf("Todos") + teams
            .filter { "nations" in it.groups || it.worldRank != null }
            .map { it.name.removeSuffix(" 7s").trim() }
            .distinct()
            .sorted()
            .take(18)
    }
    val teamsWithRoster = remember(teams) {
        listOf("Todos") + teams.filter { it.roster.isNotEmpty() }.map { it.name }.distinct().sorted().take(24)
    }
    val positions = listOf("Todas", "Pilar", "Hooker", "Segunda Línea", "Ala", "Octavo", "Medio Scrum", "Apertura", "Centro", "Wing", "Fullback")

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        item {
            SectionBackdropBanner(
                backgroundRes = R.drawable.bg_rugby_stadium,
                eyebrow = "Comparación Lado a Lado",
                title = "Comparación",
                subtitle = "Buscador integrado con filtros por nombre, país, equipo y posición"
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                FilterChip(
                    selected = state.compareMode == "teams",
                    onClick = { viewModel.setCompareMode("teams") },
                    label = { Text("Comparar Equipos") },
                    colors = rugbyChipColors(),
                    modifier = Modifier.heightIn(min = 48.dp)
                )
                FilterChip(
                    selected = state.compareMode == "players",
                    onClick = { viewModel.setCompareMode("players") },
                    label = { Text("Comparar Jugadores") },
                    colors = rugbyChipColors(),
                    modifier = Modifier.heightIn(min = 48.dp)
                )
            }
        }

        // Comparador lado a lado activo arriba para feedback inmediato
        item {
            if (state.compareMode == "teams") {
                val comp = state.teamComparison
                if (comp == null) {
                    GlassCard { Text("Seleccioná dos equipos desde el buscador para comparar.") }
                } else {
                    GlassCard(highlightAccent = true) {
                        Text("Cara a Cara · Equipos", style = MaterialTheme.typography.titleLarge, color = MetallicGold)
                        Spacer(Modifier.height(Spacing.md))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                TeamCrestWithFlag(comp.left.crestUrl, comp.left.flagUrl, comp.left.name, size = 48.dp)
                                Spacer(Modifier.height(4.dp))
                                Text(comp.left.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                RugbyJerseyBadge(comp.left.homeKit, size = 26.dp)
                            }
                            Text("VS", style = MaterialTheme.typography.titleLarge, color = ElectricCyan, modifier = Modifier.padding(horizontal = 8.dp))
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                TeamCrestWithFlag(comp.right.crestUrl, comp.right.flagUrl, comp.right.name, size = 48.dp)
                                Spacer(Modifier.height(4.dp))
                                Text(comp.right.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                RugbyJerseyBadge(comp.right.homeKit, size = 26.dp)
                            }
                        }
                        Spacer(Modifier.height(Spacing.md))
                        CompareRow("Ranking Mundial", comp.left.worldRank?.let { "#$it" } ?: "N/D", comp.right.worldRank?.let { "#$it" } ?: "N/D")
                        val leftPct = if (comp.leftPlayed > 0) RugbyDateUtils.formatPercent(comp.leftWins * 100.0 / comp.leftPlayed) else "N/D"
                        val rightPct = if (comp.rightPlayed > 0) RugbyDateUtils.formatPercent(comp.rightWins * 100.0 / comp.rightPlayed) else "N/D"
                        CompareRow("Partidos cerrados", "${comp.leftPlayed}", "${comp.rightPlayed}")
                        CompareRow("Victorias", "${comp.leftWins}", "${comp.rightWins}")
                        CompareRow("Efectividad", leftPct, rightPct)
                        CompareRow("Puntos a favor", "${comp.leftPointsFor}", "${comp.rightPointsFor}")
                        CompareRow("Puntos en contra", "${comp.leftPointsAgainst}", "${comp.rightPointsAgainst}")
                        CompareRow("Entrenador", comp.left.coach.ifBlank { "No disponible" }, comp.right.coach.ifBlank { "No disponible" })
                    }
                }
            } else {
                val pComp = state.playerComparison
                if (pComp == null) {
                    GlassCard { Text("Seleccioná dos jugadores desde el buscador para comparar.") }
                } else {
                    GlassCard(highlightAccent = true) {
                        Text("Cara a Cara · Jugadores", style = MaterialTheme.typography.titleLarge, color = MetallicGold)
                        Spacer(Modifier.height(Spacing.md))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                BroadcastPlayerMedallion(pComp.left.photoUrl, pComp.left.name, kit = state.comparePlayerA?.teamKit, jerseyNumber = pComp.left.jersey, isCaptain = pComp.left.captain, size = 62.dp, isOval = true)
                                Spacer(Modifier.height(4.dp))
                                Text(pComp.left.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(pComp.leftTeam, style = MaterialTheme.typography.labelSmall, color = PitchGreen)
                            }
                            Text("VS", style = MaterialTheme.typography.titleLarge, color = ElectricCyan, modifier = Modifier.padding(horizontal = 8.dp))
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                BroadcastPlayerMedallion(pComp.right.photoUrl, pComp.right.name, kit = state.comparePlayerB?.teamKit, jerseyNumber = pComp.right.jersey, isCaptain = pComp.right.captain, size = 62.dp, isOval = true)
                                Spacer(Modifier.height(4.dp))
                                Text(pComp.right.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(pComp.rightTeam, style = MaterialTheme.typography.labelSmall, color = MetallicGold)
                            }
                        }
                        Spacer(Modifier.height(Spacing.md))
                        CompareRow("Número", "#${pComp.left.jersey}", "#${pComp.right.jersey}")
                        CompareRow("Posición", pComp.left.position.ifBlank { "N/D" }, pComp.right.position.ifBlank { "N/D" })
                        CompareRow("Altura", pComp.left.height?.let { "$it cm" } ?: "No disponible", pComp.right.height?.let { "$it cm" } ?: "No disponible")
                        CompareRow("Peso", pComp.left.weight?.let { "$it kg" } ?: "No disponible", pComp.right.weight?.let { "$it kg" } ?: "No disponible")
                        CompareRow("Edad", pComp.left.age?.let { "$it años" } ?: "No disponible", pComp.right.age?.let { "$it años" } ?: "No disponible")
                        CompareRow("Caps", pComp.left.caps?.toString() ?: "No disponible", pComp.right.caps?.toString() ?: "No disponible")
                    }
                }
            }
        }

        // Buscador integrado con filtros por nombre, país, equipo y posición
        item {
            OutlinedTextField(
                value = state.compareQuery,
                onValueChange = { viewModel.setCompareFilters(query = it) },
                label = {
                    Text(
                        if (state.compareMode == "teams") "Buscar equipo por nombre o país"
                        else "Buscar jugador por nombre, equipo o dorsal"
                    )
                },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                colors = rugbyFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(Spacing.sm))
            Text("Filtrar por país / selección", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                countries.forEach { c ->
                    FilterChip(
                        selected = state.compareCountryFilter == c,
                        onClick = { viewModel.setCompareFilters(country = c) },
                        label = { Text(c) },
                        colors = rugbyChipColors()
                    )
                }
            }
            if (state.compareMode == "players") {
                Spacer(Modifier.height(Spacing.xs))
                Text("Filtrar por equipo", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    teamsWithRoster.forEach { tn ->
                        FilterChip(
                            selected = state.compareTeamFilter == tn,
                            onClick = { viewModel.setCompareFilters(team = tn) },
                            label = { Text(tn) },
                            colors = rugbyChipColors()
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.xs))
                Text("Filtrar por posición", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    positions.forEach { pos ->
                        FilterChip(
                            selected = state.comparePositionFilter == pos,
                            onClick = { viewModel.setCompareFilters(position = pos) },
                            label = { Text(pos) },
                            colors = rugbyChipColors()
                        )
                    }
                }
            }
        }

        if (state.compareMode == "teams") {
            val res = state.teamSearchResults
            items(res.take(50), key = { "cmp_t_${it.key}_${it.name}" }) { team ->
                GlassCard {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        TeamCrestWithFlag(team.crestUrl, team.flagUrl, team.name, size = 44.dp)
                        Spacer(Modifier.width(Spacing.sm))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(team.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = listOfNotNull(
                                    team.worldRank?.let { "Rank #$it" },
                                    team.coach.takeIf { it.isNotBlank() }?.let { "DT: $it" }
                                ).joinToString(" · ").ifBlank { team.division.ifBlank { "Club oficial" } },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { viewModel.assignCompareTeam(true, team) }) { Text("Lado A") }
                        TextButton(onClick = { viewModel.assignCompareTeam(false, team) }) { Text("Lado B") }
                    }
                }
            }
        } else {
            val res = state.playerSearchResults
            if (res.isEmpty()) {
                item {
                    GlassCard { Text("No se encontraron jugadores con esos filtros.") }
                }
            } else {
                items(res.take(70), key = { "cmp_p_${it.teamKey}_${it.player.jersey}_${it.player.name}" }) { entry ->
                    GlassCard(onClick = { viewModel.openPlayerDetail(entry) }) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            BroadcastPlayerMedallion(entry.player.photoUrl, entry.player.name, kit = entry.teamKit, jerseyNumber = entry.player.jersey, isCaptain = entry.player.captain, size = 54.dp, isOval = true)
                            Spacer(Modifier.width(Spacing.sm))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "#${entry.player.jersey} ${entry.player.name}",
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${entry.player.position.ifBlank { "Jugador" }} · ${entry.teamName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                val bio = listOfNotNull(
                                    entry.player.age?.let { "${it}a" },
                                    entry.player.height?.let { "${it}cm" },
                                    entry.player.weight?.let { "${it}kg" },
                                    entry.player.caps?.let { "${it} caps" }
                                ).joinToString(" · ")
                                if (bio.isNotBlank()) {
                                    Text(bio, style = MaterialTheme.typography.labelSmall, color = ElectricCyan)
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Row {
                                    TextButton(onClick = { viewModel.assignComparePlayer(true, entry) }) { Text("A") }
                                    TextButton(onClick = { viewModel.assignComparePlayer(false, entry) }) { Text("B") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompareRow(label: String, left: String, right: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(left, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = PitchGreen, modifier = Modifier.weight(1f))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(right, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MetallicGold, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

@Composable
fun NewsScreen(
    state: RugbyUiState,
    viewModel: RugbyViewModel
) {
    val context = LocalContext.current
    val allNews = state.dashboard?.news.orEmpty()
    val cats = remember(allNews) {
        listOf("Todas") + allNews.map { it.category }.filter { it.isNotBlank() }.distinct().take(6)
    }
    val list = remember(allNews, state.newsCategory, state.showSavedNewsOnly) {
        allNews.filter { n ->
            val catOk = state.newsCategory == "Todas" || n.category.equals(state.newsCategory, true)
            val savedOk = !state.showSavedNewsOnly || n.savedOffline
            catOk && savedOk
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    cats.forEach { c ->
                        FilterChip(
                            selected = state.newsCategory == c,
                            onClick = { viewModel.setNewsFilter(category = c) },
                            label = { Text(c) },
                            colors = rugbyChipColors(),
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }
                Spacer(Modifier.width(Spacing.sm))
                FilterChip(
                    selected = state.showSavedNewsOnly,
                    onClick = { viewModel.setNewsFilter(savedOnly = !state.showSavedNewsOnly) },
                    label = { Text("Guardadas") },
                    colors = rugbyChipColors(),
                    modifier = Modifier.heightIn(min = 48.dp)
                )
            }
        }

        if (list.isEmpty()) {
            item { GlassCard { Text("Sin datos disponibles de noticias para este filtro.") } }
        } else {
            items(list, key = { it.id }) { art ->
                GlassCard {
                    if (art.imageUrl.isNotBlank()) {
                        SubcomposeAsyncImage(
                            model = art.imageUrl,
                            contentDescription = art.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(168.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                        Spacer(Modifier.height(Spacing.sm))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = listOf(art.category, RugbyDateUtils.normalizeDisplayDate(art.publishedLabel)).filter { it.isNotBlank() }.joinToString(" · "),
                            style = MaterialTheme.typography.labelMedium,
                            color = MetallicGold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.toggleSavedNews(art) }) {
                            Icon(
                                imageVector = if (art.savedOffline) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = "Guardar noticia para leer sin conexión",
                                tint = MetallicGold
                            )
                        }
                    }
                    Text(art.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    if (art.summary.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = art.summary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (art.url.isNotBlank()) {
                        Spacer(Modifier.height(Spacing.sm))
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(art.url)))
                                }
                            },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Outlined.OpenInNew, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
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
                Text("Apariencia y Equipo Favorito", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
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
                Text("Frecuencia en Vivo", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
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
                    listOf(6000L to "Espera 6s", 10000L to "Espera 10s", 15000L to "Espera 15s").forEach { (ms, lbl) ->
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
                Text("Notificaciones en Vivo", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(Spacing.sm))
                if (!hasPerm) {
                    Button(
                        onClick = onRequestNotificationPermission,
                        colors = ButtonDefaults.buttonColors(containerColor = PitchGreen),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Text("Permitir notificaciones")
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
                Text("Experiencia Inicial", style = MaterialTheme.typography.titleMedium, color = MetallicGold)
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = "Podés volver a ver las diapositivas de bienvenida y elegir tu equipo favorito.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Spacing.sm))
                OutlinedButton(
                    onClick = { viewModel.replayOnboarding() },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Text("Ver guía de bienvenida")
                }
            }
        }
        item {
            GlassCard {
                Text("Privacidad y Seguridad", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = "Rugby Pro no recolecta datos personales, ubicación ni analíticas externas. Toda preferencia y noticia guardada permanece exclusivamente en tu dispositivo, cumpliendo las políticas de Google Play Data Safety y App Store Privacy.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
