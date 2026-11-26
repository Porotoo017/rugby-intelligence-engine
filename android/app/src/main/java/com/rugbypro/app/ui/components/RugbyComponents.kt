package com.rugbypro.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.rugbypro.app.domain.model.ActiveKitRole
import com.rugbypro.app.domain.model.LiveMatchStats
import com.rugbypro.app.domain.model.MatchEvent
import com.rugbypro.app.domain.model.MatchEventType
import com.rugbypro.app.domain.model.MatchStatus
import com.rugbypro.app.domain.model.RugbyMatch
import com.rugbypro.app.domain.model.TeamKit
import com.rugbypro.app.ui.theme.ElectricCyan
import com.rugbypro.app.ui.theme.LiveCrimson
import com.rugbypro.app.ui.theme.MetallicGold
import com.rugbypro.app.ui.theme.PendingAmber
import com.rugbypro.app.ui.theme.PitchDarkBg
import com.rugbypro.app.ui.theme.PitchGreen
import com.rugbypro.app.ui.theme.Spacing

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    highlightAccent: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    val borderColor = if (highlightAccent) {
        MetallicGold.copy(alpha = 0.65f)
    } else {
        MaterialTheme.colorScheme.outline
    }
    val clickMod = if (onClick != null) {
        Modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .clickable(onClick = onClick)
    } else {
        Modifier
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(clickMod),
        shape = shape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.14f)
                        )
                    )
                )
                .padding(Spacing.md),
            content = content
        )
    }
}

@Composable
fun SectionBackdropBanner(
    @DrawableRes backgroundRes: Int,
    eyebrow: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    val shape = RoundedCornerShape(20.dp)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = PitchDarkBg,
        border = BorderStroke(1.dp, MetallicGold.copy(alpha = 0.45f)),
        shadowElevation = 10.dp
    ) {
        Box(modifier = Modifier.fillMaxWidth().heightIn(min = 136.dp)) {
            Image(
                painter = painterResource(id = backgroundRes),
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().alpha(0.55f)
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                PitchDarkBg.copy(alpha = 0.94f),
                                PitchDarkBg.copy(alpha = 0.78f),
                                PitchDarkBg.copy(alpha = 0.55f)
                            )
                        )
                    )
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.md)
                    .align(Alignment.CenterStart),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = eyebrow.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MetallicGold,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color(0xFFF5F5F5)
                    )
                    if (subtitle.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFD1D9E6),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (trailing != null) {
                    Spacer(Modifier.width(Spacing.sm))
                    trailing()
                }
            }
        }
    }
}

@Composable
fun PulsingLiveIndicator(label: String = "EN VIVO") {
    val inf = rememberInfiniteTransition(label = "livePulse")
    val alpha by inf.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(780), RepeatMode.Reverse),
        label = "pulseAlpha"
    )
    Surface(
        color = LiveCrimson.copy(alpha = 0.16f),
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, LiveCrimson.copy(alpha = 0.65f)),
        modifier = Modifier.semantics { contentDescription = "Partido en vivo ahora" }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .alpha(alpha)
                    .clip(CircleShape)
                    .background(LiveCrimson)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = LiveCrimson,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun TeamCrestWithFlag(
    crestUrl: String,
    flagUrl: String,
    teamName: String,
    size: Dp = 44.dp
) {
    val initials = teamName.trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercaseChar().toString() }
        .ifBlank { "RB" }

    val primaryUrl = crestUrl.ifBlank { flagUrl }
    val showFallbackNote = crestUrl.isBlank() && flagUrl.isBlank()
    Box(
        modifier = Modifier
            .size(size)
            .semantics {
                contentDescription = if (showFallbackNote) {
                    "Escudo de $teamName: información no disponible"
                } else {
                    "Escudo y bandera oficial de $teamName"
                }
            }
    ) {
        Surface(
            modifier = Modifier.size(size),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
        ) {
            if (primaryUrl.isNotBlank()) {
                SubcomposeAsyncImage(
                    model = primaryUrl,
                    contentDescription = teamName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    error = {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(initials, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                )
            } else {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text(initials, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (flagUrl.isNotBlank() && crestUrl.isNotBlank()) {
            SubcomposeAsyncImage(
                model = flagUrl,
                contentDescription = "Bandera de $teamName",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(size * 0.42f)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .border(1.5.dp, MaterialTheme.colorScheme.background, CircleShape)
            )
        }
    }
}

@Composable
fun PlayerAvatar(
    photoUrl: String?,
    playerName: String,
    size: Dp = 52.dp
) {
    val initials = playerName.trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercaseChar().toString() }
        .ifBlank { "JG" }

    Surface(
        modifier = Modifier
            .size(size)
            .semantics { contentDescription = "Foto de $playerName" },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.5.dp, MetallicGold.copy(alpha = 0.65f))
    ) {
        if (!photoUrl.isNullOrBlank()) {
            SubcomposeAsyncImage(
                model = photoUrl,
                contentDescription = playerName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
                error = {
                    PlayerAvatarPlaceholder(initials)
                }
            )
        } else {
            PlayerAvatarPlaceholder(initials)
        }
    }
}

@Composable
private fun PlayerAvatarPlaceholder(initials: String) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(
                        PitchGreen.copy(alpha = 0.25f),
                        ElectricCyan.copy(alpha = 0.14f)
                    )
                )
            )
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Outlined.Person,
                contentDescription = null,
                tint = MetallicGold.copy(alpha = 0.85f),
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = initials,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun parseColorOrNull(hex: String?): Color? {
    if (hex.isNullOrBlank()) return null
    val c = hex.trim().removePrefix("#")
    return runCatching {
        Color(android.graphics.Color.parseColor("#$c"))
    }.getOrNull()
}

@Composable
fun RugbyJerseyBadge(
    kit: TeamKit?,
    size: Dp = 36.dp,
    modifier: Modifier = Modifier
) {
    val outlineColor = MaterialTheme.colorScheme.outline
    val primary = parseColorOrNull(kit?.primaryHex)
    val secondary = parseColorOrNull(kit?.secondaryHex) ?: Color.White
    val pattern = kit?.pattern ?: "solid"

    Canvas(
        modifier = modifier
            .size(size)
            .semantics {
                contentDescription = if (kit != null && kit.verified) {
                    "Camiseta ${kit.label}"
                } else {
                    "Camiseta: información no disponible"
                }
            }
    ) {
        val w = this.size.width
        val h = this.size.height
        val jerseyPath = Path().apply {
            moveTo(w * 0.28f, h * 0.12f)
            lineTo(w * 0.40f, h * 0.12f)
            lineTo(w * 0.50f, h * 0.22f)
            lineTo(w * 0.60f, h * 0.12f)
            lineTo(w * 0.72f, h * 0.12f)
            lineTo(w * 0.94f, h * 0.26f)
            lineTo(w * 0.84f, h * 0.45f)
            lineTo(w * 0.73f, h * 0.38f)
            lineTo(w * 0.73f, h * 0.90f)
            lineTo(w * 0.27f, h * 0.90f)
            lineTo(w * 0.27f, h * 0.38f)
            lineTo(w * 0.16f, h * 0.45f)
            lineTo(w * 0.06f, h * 0.26f)
            close()
        }

        if (primary == null || kit?.verified != true) {
            drawPath(
                path = jerseyPath,
                color = outlineColor.copy(alpha = 0.6f),
                style = Stroke(
                    width = 2.2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
                )
            )
            return@Canvas
        }

        drawPath(path = jerseyPath, color = primary)

        when (pattern) {
            "hoops" -> {
                val bandH = h * 0.11f
                drawRect(color = secondary, topLeft = Offset(w * 0.27f, h * 0.38f), size = androidx.compose.ui.geometry.Size(w * 0.46f, bandH))
                drawRect(color = secondary, topLeft = Offset(w * 0.27f, h * 0.62f), size = androidx.compose.ui.geometry.Size(w * 0.46f, bandH))
            }
            "stripes" -> {
                drawRect(color = secondary, topLeft = Offset(w * 0.37f, h * 0.20f), size = androidx.compose.ui.geometry.Size(w * 0.09f, h * 0.70f))
                drawRect(color = secondary, topLeft = Offset(w * 0.54f, h * 0.20f), size = androidx.compose.ui.geometry.Size(w * 0.09f, h * 0.70f))
            }
            "sash" -> {
                drawLine(color = secondary, start = Offset(w * 0.28f, h * 0.24f), end = Offset(w * 0.72f, h * 0.85f), strokeWidth = w * 0.12f)
            }
            "split" -> {
                drawRect(color = secondary, topLeft = Offset(w * 0.50f, h * 0.18f), size = androidx.compose.ui.geometry.Size(w * 0.23f, h * 0.72f))
            }
        }

        // Collar trim
        drawLine(color = secondary, start = Offset(w * 0.38f, h * 0.13f), end = Offset(w * 0.50f, h * 0.23f), strokeWidth = 2.6f)
        drawLine(color = secondary, start = Offset(w * 0.62f, h * 0.13f), end = Offset(w * 0.50f, h * 0.23f), strokeWidth = 2.6f)
        drawPath(path = jerseyPath, color = Color.White.copy(alpha = 0.28f), style = Stroke(width = 1.6f))
    }
}

@Composable
fun MatchScoreCard(
    match: RugbyMatch,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier,
        highlightAccent = match.status == MatchStatus.LIVE || match.isArgentina,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = match.leagueShort.ifBlank { match.leagueName },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(Spacing.sm))
            when (match.status) {
                MatchStatus.LIVE -> PulsingLiveIndicator(
                    label = if (match.minuteDisplay.isNotBlank()) "EN VIVO · ${match.minuteDisplay}" else "EN VIVO"
                )
                MatchStatus.FINISHED -> Text(
                    text = "FINALIZADO",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
                MatchStatus.SCHEDULED -> Text(
                    text = "${match.dayArg} ${match.timeArg}".trim().ifBlank { match.dateArg },
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricCyan,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(Modifier.height(Spacing.md))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Local
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                TeamCrestWithFlag(match.homeCrestUrl, match.homeFlagUrl, match.homeName, size = 48.dp)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = match.homeName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    RugbyJerseyBadge(kit = match.homeKit, size = 22.dp)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (match.homeActiveKitRole != ActiveKitRole.UNKNOWN) match.homeActiveKitRole.label else "Camiseta N/D",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (match.homeActiveKitRole != ActiveKitRole.UNKNOWN) PitchGreen else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Marcador central
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = Spacing.sm)
            ) {
                if (match.status == MatchStatus.SCHEDULED && match.homeScore == null) {
                    Text(
                        text = "VS",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = match.timeArg.ifBlank { "Hora N/D" },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "${match.homeScore ?: 0} - ${match.awayScore ?: 0}",
                        style = MaterialTheme.typography.displaySmall,
                        color = if (match.status == MatchStatus.LIVE) PitchGreen else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = match.periodDisplay.ifBlank { match.status.label },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Visitante
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                TeamCrestWithFlag(match.awayCrestUrl, match.awayFlagUrl, match.awayName, size = 48.dp)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = match.awayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    RugbyJerseyBadge(kit = match.awayKit, size = 22.dp)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (match.awayActiveKitRole != ActiveKitRole.UNKNOWN) match.awayActiveKitRole.label else "Camiseta N/D",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (match.awayActiveKitRole == ActiveKitRole.AWAY) MetallicGold else if (match.awayActiveKitRole == ActiveKitRole.HOME) PitchGreen else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        val venueText = listOf(match.venueName, match.venueCity).filter { it.isNotBlank() }.joinToString(", ")
        if (venueText.isNotBlank() || match.homeKit == null || match.awayKit == null) {
            Spacer(Modifier.height(Spacing.sm))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            Spacer(Modifier.height(Spacing.sm))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = venueText.ifBlank { "Sede: Sin datos disponibles" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (match.homeKit == null || match.awayKit == null) {
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        text = "Indumentaria: Información no disponible",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun MatchTimelineSection(events: List<MatchEvent>) {
    if (events.isEmpty()) {
        Text(
            text = "Sin datos disponibles",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        events.forEach { ev ->
            val accent = when (ev.type) {
                MatchEventType.TRY -> PitchGreen
                MatchEventType.CONVERSION, MatchEventType.PENALTY, MatchEventType.DROP_GOAL -> MetallicGold
                MatchEventType.YELLOW_CARD -> PendingAmber
                MatchEventType.RED_CARD -> LiveCrimson
                MatchEventType.SUBSTITUTION -> ElectricCyan
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                    .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${ev.minute}'",
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(36.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${ev.type.label} · ${ev.player.ifBlank { "Jugador" }}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (ev.detail.isNotBlank()) {
                        Text(
                            text = ev.detail,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = if (ev.teamSide == "home") "LOCAL" else "VISITA",
                    style = MaterialTheme.typography.labelSmall,
                    color = accent
                )
            }
        }
    }
}

@Composable
fun LiveStatsSection(stats: LiveMatchStats?) {
    if (stats == null || !stats.hasAnyMetric()) {
        Text(
            text = "Sin datos disponibles",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        StatCompareRow("Posesión (%)", stats.possessionHomePct, stats.possessionAwayPct, suffix = "%")
        StatCompareRow("Territorio (%)", stats.territoryHomePct, stats.territoryAwayPct, suffix = "%")
        StatCompareRow("Tackles", stats.tacklesHome, stats.tacklesAway)
        StatCompareRow("Lineouts ganados", stats.lineoutsWonHome, stats.lineoutsWonAway)
        StatCompareRow("Scrums ganados", stats.scrumsWonHome, stats.scrumsWonAway)
        StatCompareRow("Penalidades", stats.penaltiesHome, stats.penaltiesAway)
    }
}

@Composable
private fun StatCompareRow(label: String, homeVal: Int?, awayVal: Int?, suffix: String = "") {
    if (homeVal == null || awayVal == null) return
    val total = (homeVal + awayVal).coerceAtLeast(1)
    val ratio = homeVal.toFloat() / total.toFloat()
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("$homeVal$suffix", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = PitchGreen)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("$awayVal$suffix", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MetallicGold)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { ratio.coerceIn(0f, 1f) },
            color = PitchGreen,
            trackColor = MetallicGold.copy(alpha = 0.45f),
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(4.dp))
        )
    }
}
