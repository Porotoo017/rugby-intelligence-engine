package com.rugbypro.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.rugbypro.app.domain.model.LiveMatchStats
import com.rugbypro.app.domain.model.MatchEvent
import com.rugbypro.app.domain.model.MatchEventType
import com.rugbypro.app.domain.model.MatchStatus
import com.rugbypro.app.domain.model.RugbyMatch
import com.rugbypro.app.domain.model.VerificationStatus
import com.rugbypro.app.ui.theme.ElectricCyan
import com.rugbypro.app.ui.theme.LiveCrimson
import com.rugbypro.app.ui.theme.MetallicGold
import com.rugbypro.app.ui.theme.PendingAmber
import com.rugbypro.app.ui.theme.PitchEmerald
import com.rugbypro.app.ui.theme.PitchGreen
import com.rugbypro.app.ui.theme.ScoreDisplayStyle
import com.rugbypro.app.ui.theme.Spacing

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    highlightAccent: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val borderBrush = if (highlightAccent) {
        Brush.linearGradient(
            listOf(PitchGreen.copy(alpha = 0.85f), MetallicGold.copy(alpha = 0.65f), ElectricCyan.copy(alpha = 0.45f))
        )
    } else {
        Brush.linearGradient(
            listOf(
                MaterialTheme.colorScheme.outline.copy(alpha = 0.8f),
                MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
            )
        )
    }
    val onSurf = MaterialTheme.colorScheme.onSurface
    val cardMod = if (onClick != null) {
        modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick)
    } else {
        modifier.fillMaxWidth()
    }
    Surface(
        modifier = cardMod,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        contentColor = onSurf,
        border = BorderStroke(1.dp, borderBrush),
        tonalElevation = 4.dp,
        shadowElevation = 6.dp
    ) {
        CompositionLocalProvider(LocalContentColor provides onSurf) {
            Column(
                modifier = Modifier.padding(Spacing.md),
                content = content
            )
        }
    }
}

@Composable
fun PulsingLiveIndicator(label: String = "EN VIVO") {
    val inf = rememberInfiniteTransition(label = "livePulse")
    val alpha by inf.animateFloat(
        initialValue = 1f,
        targetValue = 0.32f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = LiveCrimson.copy(alpha = 0.16f),
        contentColor = LiveCrimson,
        border = BorderStroke(1.dp, LiveCrimson.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically
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
fun VerificationBadge(status: VerificationStatus) {
    val (color, label) = when (status) {
        VerificationStatus.VERIFIED -> PitchGreen to "VERIFICADO"
        VerificationStatus.PENDING_VERIFICATION -> PendingAmber to "Pendiente de verificación"
        VerificationStatus.LOCAL_CACHE -> MaterialTheme.colorScheme.onSurfaceVariant to "Caché local"
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.14f),
        contentColor = color,
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 3.dp)
        )
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

    Box(
        modifier = Modifier
            .size(size)
            .semantics { contentDescription = "Escudo de $teamName" }
    ) {
        Surface(
            modifier = Modifier.size(size),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
        ) {
            if (crestUrl.isNotBlank() && !crestUrl.contains("25912.png")) {
                SubcomposeAsyncImage(
                    model = crestUrl,
                    contentDescription = teamName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.padding(6.dp),
                    error = {
                        InitialsFallback(initials)
                    },
                    loading = {
                        InitialsFallback(initials)
                    }
                )
            } else {
                InitialsFallback(initials)
            }
        }
        if (flagUrl.isNotBlank() && flagUrl != crestUrl) {
            Surface(
                modifier = Modifier
                    .size((size.value * 0.42f).dp)
                    .align(Alignment.BottomEnd),
                shape = CircleShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surface),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                SubcomposeAsyncImage(
                    model = flagUrl,
                    contentDescription = "Bandera de $teamName",
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

@Composable
private fun InitialsFallback(initials: String) {
    Box(
        modifier = Modifier
            .background(Brush.linearGradient(listOf(PitchEmerald, Color(0xFF10202C)))),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.labelMedium,
            color = MetallicGold,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun MatchScoreCard(
    match: RugbyMatch,
    onClick: () -> Unit
) {
    val isLive = match.status == MatchStatus.LIVE
    val hasScores = match.status != MatchStatus.SCHEDULED && match.homeScore != null && match.awayScore != null

    GlassCard(highlightAccent = isLive || match.isArgentina, onClick = onClick) {
        // Row 1: League + status/time cleanly separated with zero overlap
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (match.phase.isNotBlank()) "${match.leagueShort} · ${match.phase}" else match.leagueShort,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = Spacing.sm)
            )
            if (isLive) {
                val clockTxt = listOf(match.minuteDisplay, match.periodDisplay)
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                    .ifBlank { "EN VIVO" }
                PulsingLiveIndicator(label = clockTxt)
            } else {
                Text(
                    text = match.dateArg.ifBlank { match.status.label },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }

        Spacer(Modifier.height(Spacing.md))

        // Row 2: Clean two-sided scoreboard (no overlapping team names & time)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Home
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TeamCrestWithFlag(
                    crestUrl = match.homeCrestUrl,
                    flagUrl = match.homeFlagUrl,
                    teamName = match.homeName,
                    size = 46.dp
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = match.homeName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val hMeta = when {
                    match.homeCoach.isNotBlank() -> "DT: ${match.homeCoach}"
                    match.homeRank != null -> "Ranking #${match.homeRank}"
                    else -> ""
                }
                if (hMeta.isNotBlank()) {
                    Text(
                        text = hMeta,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Center Score / VS badge
            Column(
                modifier = Modifier.padding(horizontal = Spacing.sm),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (hasScores) {
                    Text(
                        text = "${match.homeScore} - ${match.awayScore}",
                        style = ScoreDisplayStyle,
                        color = if (isLive) LiveCrimson else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Text(
                            text = match.timeArg.ifBlank { "VS" },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Away
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TeamCrestWithFlag(
                    crestUrl = match.awayCrestUrl,
                    flagUrl = match.awayFlagUrl,
                    teamName = match.awayName,
                    size = 46.dp
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = match.awayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val aMeta = when {
                    match.awayCoach.isNotBlank() -> "DT: ${match.awayCoach}"
                    match.awayRank != null -> "Ranking #${match.awayRank}"
                    else -> ""
                }
                if (aMeta.isNotBlank()) {
                    Text(
                        text = aMeta,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(Modifier.height(Spacing.sm))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
        Spacer(Modifier.height(Spacing.sm))

        // Row 3: Venue + Verification pill
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val venueText = listOf(match.venueName, match.venueCity)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
                .ifBlank { "Sede por confirmar" }
            Text(
                text = venueText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = Spacing.sm)
            )
            VerificationBadge(match.verificationStatus)
        }
    }
}

@Composable
fun MatchTimelineSection(events: List<MatchEvent>) {
    if (events.isEmpty()) {
        Text(
            text = "Cronología de eventos: Sin datos disponibles",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        events.forEach { ev ->
            val badgeColor = when (ev.type) {
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
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = badgeColor.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, badgeColor)
                ) {
                    Text(
                        text = "${ev.minute}'",
                        style = MaterialTheme.typography.labelMedium,
                        color = badgeColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                Spacer(Modifier.width(Spacing.sm))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${ev.type.label} · ${ev.player.ifBlank { ev.teamSide.uppercase() }}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (ev.detail.isNotBlank()) {
                        Text(
                            text = ev.detail,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LiveStatsSection(stats: LiveMatchStats?) {
    if (stats == null || !stats.hasAnyMetric()) {
        Text(
            text = "Estadísticas en vivo: Sin datos disponibles",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        LiveStatComparisonRow("Posesión (%)", stats.possessionHomePct, stats.possessionAwayPct, suffix = "%")
        LiveStatComparisonRow("Territorio (%)", stats.territoryHomePct, stats.territoryAwayPct, suffix = "%")
        LiveStatComparisonRow("Tackles", stats.tacklesHome, stats.tacklesAway)
        LiveStatComparisonRow("Lineouts ganados", stats.lineoutsWonHome, stats.lineoutsWonAway)
        LiveStatComparisonRow("Scrums ganados", stats.scrumsWonHome, stats.scrumsWonAway)
        LiveStatComparisonRow("Penales concedidos", stats.penaltiesHome, stats.penaltiesAway)
    }
}

@Composable
private fun LiveStatComparisonRow(
    label: String,
    homeVal: Int?,
    awayVal: Int?,
    suffix: String = ""
) {
    if (homeVal == null && awayVal == null) return
    val h = homeVal ?: 0
    val a = awayVal ?: 0
    val ratio = if (h + a > 0) h.toFloat() / (h + a).toFloat() else 0.5f
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("$h$suffix", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = PitchGreen)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("$a$suffix", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MetallicGold)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { ratio },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp)),
            color = PitchGreen,
            trackColor = MetallicGold.copy(alpha = 0.45f)
        )
    }
}
