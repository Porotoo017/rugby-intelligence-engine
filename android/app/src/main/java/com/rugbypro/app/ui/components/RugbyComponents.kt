package com.rugbypro.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.SportsRugby
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Thunderstorm
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
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
import com.rugbypro.app.domain.model.MatchDayPart
import com.rugbypro.app.domain.model.MatchOfficial
import com.rugbypro.app.domain.model.PlayerProfile
import androidx.compose.ui.platform.LocalUriHandler
import com.rugbypro.app.domain.model.MatchWeather
import com.rugbypro.app.domain.model.NewsArticle
import com.rugbypro.app.domain.model.RugbyMatch
import com.rugbypro.app.domain.model.TeamKit
import com.rugbypro.app.domain.model.ThermalZone
import com.rugbypro.app.domain.usecase.RugbyDateUtils
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


/**
 * Retrato 4x4 estilo transmisión televisiva (óvalo o círculo) con el rostro del jugador
 * y los hombros/cuello de la camiseta oficial puesta (Titular por defecto o Suplente).
 */
@Composable
fun BroadcastPlayerMedallion(
    photoUrl: String?,
    playerName: String,
    kit: TeamKit?,
    jerseyNumber: String = "",
    isCaptain: Boolean = false,
    size: Dp = 74.dp,
    isOval: Boolean = true,
    modifier: Modifier = Modifier
) {
    val shape = if (isOval) RoundedCornerShape(percent = 48) else CircleShape
    val width = if (isOval) size * 0.86f else size
    val primary = parseColorOrNull(kit?.primaryHex)
    val secondary = parseColorOrNull(kit?.secondaryHex) ?: Color.White
    val pattern = kit?.pattern ?: "solid"
    val borderCol = when {
        isCaptain -> MetallicGold
        primary != null -> primary.copy(alpha = 0.9f)
        else -> MaterialTheme.colorScheme.outline
    }

    Box(
        modifier = modifier
            .width(width)
            .height(size)
            .semantics {
                contentDescription = "Retrato 4x4 de $playerName" +
                    if (kit != null && kit.verified) " con camiseta ${kit.label}" else ""
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .border(if (isCaptain) 2.dp else 1.5.dp, borderCol, shape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            (primary ?: Color(0xFF1A2636)).copy(alpha = 0.36f),
                            PitchDarkBg.copy(alpha = 0.94f)
                        )
                    )
                )
        ) {
            val cleanPhoto = photoUrl?.trim().orEmpty()
            if (cleanPhoto.isNotBlank()) {
                SubcomposeAsyncImage(
                    model = cleanPhoto,
                    contentDescription = playerName,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(size * 0.78f)
                        .align(Alignment.TopCenter),
                    loading = {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Person,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.4f),
                                modifier = Modifier.size(size * 0.34f)
                            )
                        }
                    },
                    error = {
                        SilhouetteHead(size = size)
                    }
                )
            } else {
                SilhouetteHead(size = size, modifier = Modifier.align(Alignment.TopCenter))
            }

            // Hombros y cuello de camiseta oficial (Titular o Suplente)
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(size * 0.38f)
                    .align(Alignment.BottomCenter)
            ) {
                val w = this.size.width
                val h = this.size.height
                val jerseyFill = primary ?: Color(0xFF1E293B)
                val collarAccent = if (primary != null) secondary else Color(0xFF94A3B8)

                val shoulders = Path().apply {
                    moveTo(0f, h)
                    lineTo(0f, h * 0.54f)
                    quadraticTo(w * 0.14f, h * 0.10f, w * 0.34f, h * 0.08f)
                    lineTo(w * 0.50f, h * 0.34f)
                    lineTo(w * 0.66f, h * 0.08f)
                    quadraticTo(w * 0.86f, h * 0.10f, w, h * 0.54f)
                    lineTo(w, h)
                    close()
                }
                drawPath(path = shoulders, color = jerseyFill)

                when (pattern) {
                    "hoops" -> {
                        drawRect(
                            color = collarAccent.copy(alpha = 0.92f),
                            topLeft = Offset(0f, h * 0.55f),
                            size = androidx.compose.ui.geometry.Size(w, h * 0.22f)
                        )
                    }
                    "stripes" -> {
                        drawRect(color = collarAccent, topLeft = Offset(w * 0.22f, h * 0.18f), size = androidx.compose.ui.geometry.Size(w * 0.11f, h * 0.82f))
                        drawRect(color = collarAccent, topLeft = Offset(w * 0.67f, h * 0.18f), size = androidx.compose.ui.geometry.Size(w * 0.11f, h * 0.82f))
                    }
                    "sash" -> {
                        drawLine(color = collarAccent, start = Offset(w * 0.16f, h * 0.20f), end = Offset(w * 0.84f, h), strokeWidth = w * 0.14f)
                    }
                    "split" -> {
                        drawRect(color = collarAccent, topLeft = Offset(w * 0.50f, h * 0.10f), size = androidx.compose.ui.geometry.Size(w * 0.50f, h * 0.90f))
                    }
                }

                drawLine(color = collarAccent, start = Offset(w * 0.33f, h * 0.09f), end = Offset(w * 0.50f, h * 0.36f), strokeWidth = 3.2f)
                drawLine(color = collarAccent, start = Offset(w * 0.67f, h * 0.09f), end = Offset(w * 0.50f, h * 0.36f), strokeWidth = 3.2f)
            }
        }

        if (jerseyNumber.isNotBlank() && jerseyNumber != "-") {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = PitchDarkBg.copy(alpha = 0.92f),
                border = BorderStroke(1.dp, if (isCaptain) MetallicGold else PitchGreen.copy(alpha = 0.85f)),
                modifier = Modifier.align(Alignment.BottomEnd)
            ) {
                Text(
                    text = if (isCaptain) "#$jerseyNumber C" else "#$jerseyNumber",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isCaptain) MetallicGold else Color(0xFFF5F5F5),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }
    }
}

@Composable
private fun SilhouetteHead(size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(size * 0.72f),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Person,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.65f),
            modifier = Modifier.size(size * 0.42f)
        )
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
    modifier: Modifier = Modifier,
    favoriteTeam: String = ""
) {
    val isFavorite = favoriteTeam.isNotBlank() && (
        match.homeName.contains(favoriteTeam, ignoreCase = true) ||
            match.awayName.contains(favoriteTeam, ignoreCase = true)
        )
    val localDateDisplay = RugbyDateUtils.formatLocalKickoff(match.timestampSec, match.dateArg)
    val isDraw = match.homeScore != null && match.awayScore != null && match.homeScore == match.awayScore &&
        (match.status == MatchStatus.FINISHED || match.status == MatchStatus.LIVE)

    GlassCard(
        modifier = modifier,
        highlightAccent = match.status == MatchStatus.LIVE || match.isArgentina || isFavorite,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                if (isFavorite) {
                    Icon(
                        imageVector = Icons.Outlined.Star,
                        contentDescription = "Equipo favorito",
                        tint = MetallicGold,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = match.leagueShort.ifBlank { match.leagueName },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(Spacing.sm))
            when (match.status) {
                MatchStatus.LIVE -> PulsingLiveIndicator(
                    label = if (match.minuteDisplay.isNotBlank()) "EN VIVO · ${match.minuteDisplay}" else "EN VIVO"
                )
                MatchStatus.FINISHED -> Text(
                    text = if (isDraw) "FINAL · EMPATE" else "FINALIZADO",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isDraw) MetallicGold else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
                MatchStatus.POSTPONED -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Schedule, contentDescription = "Postergado", tint = PendingAmber, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("POSTERGADO", style = MaterialTheme.typography.labelSmall, color = PendingAmber, fontWeight = FontWeight.Bold)
                }
                MatchStatus.CANCELLED -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Cancel, contentDescription = "Cancelado", tint = LiveCrimson, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("CANCELADO", style = MaterialTheme.typography.labelSmall, color = LiveCrimson, fontWeight = FontWeight.Bold)
                }
                MatchStatus.SCHEDULED -> Text(
                    text = localDateDisplay,
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricCyan,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        if (match.isExtraTime || !match.statusNote.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                if (match.isExtraTime) {
                    Text(
                        text = "TIEMPO EXTRA",
                        style = MaterialTheme.typography.labelSmall,
                        color = MetallicGold,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (!match.statusNote.isNullOrBlank()) {
                    Text(
                        text = match.statusNote,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(Modifier.height(Spacing.md))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
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

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = Spacing.sm)
            ) {
                if ((match.status == MatchStatus.SCHEDULED || match.status == MatchStatus.POSTPONED || match.status == MatchStatus.CANCELLED) && match.homeScore == null) {
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
                    AnimatedContent(
                        targetState = "${match.homeScore ?: 0} - ${match.awayScore ?: 0}",
                        transitionSpec = {
                            (fadeIn(tween(240)) + scaleIn(initialScale = 0.92f, animationSpec = tween(240))) togetherWith fadeOut(tween(180))
                        },
                        label = "scoreTransition"
                    ) { scoreText ->
                        Text(
                            text = scoreText,
                            style = MaterialTheme.typography.displaySmall,
                            color = if (match.status == MatchStatus.LIVE) PitchGreen else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = when {
                            isDraw && match.status == MatchStatus.FINISHED -> "Empate"
                            match.isExtraTime -> "Tiempo extra"
                            else -> match.periodDisplay.ifBlank { match.status.label }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isDraw) MetallicGold else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

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
        val weatherSummary = match.weather?.takeIf { it.isAvailable && it.tempC != null }?.let {
            "${String.format(java.util.Locale.US, "%.0f", it.tempC)}°C · ${it.conditionLabel}"
        }
        if (venueText.isNotBlank() || weatherSummary != null || match.homeKit == null || match.awayKit == null) {
            Spacer(Modifier.height(Spacing.sm))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            Spacer(Modifier.height(Spacing.sm))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val refSuffix = if (match.refereeName.isNotBlank()) " · Árb: ${match.refereeName.substringBefore(" (")}" else ""
                Text(
                    text = (venueText.ifBlank { "Sede por confirmar" }) + refSuffix,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (weatherSummary != null) {
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        text = weatherSummary,
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricCyan,
                        fontWeight = FontWeight.SemiBold
                    )
                } else if (match.homeKit == null || match.awayKit == null) {
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
fun MatchWeatherCard(
    weather: MatchWeather?,
    dayPart: MatchDayPart,
    localTimeLabel: String
) {
    GlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Clima y Condiciones",
                style = MaterialTheme.typography.titleMedium,
                color = ElectricCyan
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                val dpIcon = when (dayPart) {
                    MatchDayPart.DIA -> Icons.Outlined.WbSunny
                    MatchDayPart.TARDE -> Icons.Outlined.WbTwilight
                    MatchDayPart.NOCHE -> Icons.Outlined.NightsStay
                    MatchDayPart.DESCONOCIDO -> Icons.Outlined.Schedule
                }
                Icon(dpIcon, contentDescription = "Horario ${dayPart.label}", tint = MetallicGold, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "${dayPart.label} · $localTimeLabel",
                    style = MaterialTheme.typography.labelSmall,
                    color = MetallicGold
                )
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        if (weather == null || !weather.isAvailable || weather.tempC == null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Cloud, contentDescription = "Clima no disponible", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(Spacing.md))
                Column {
                    Text(
                        text = "Clima no disponible",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Se actualizará automáticamente al confirmarse la estación del estadio.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            return@GlassCard
        }

        val wIcon = when (weather.conditionCode) {
            "sun" -> Icons.Outlined.WbSunny
            "rain" -> Icons.Outlined.WaterDrop
            "drizzle" -> Icons.Outlined.WaterDrop
            "storm" -> Icons.Outlined.Thunderstorm
            "snow" -> Icons.Outlined.AcUnit
            "wind" -> Icons.Outlined.Air
            else -> Icons.Outlined.Cloud
        }
        val zoneColor = when (weather.thermalZone) {
            ThermalZone.FRIO -> ElectricCyan
            ThermalZone.TEMPLADO -> PitchGreen
            ThermalZone.CALOR -> PendingAmber
            null -> MaterialTheme.colorScheme.onSurfaceVariant
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(wIcon, contentDescription = weather.conditionLabel, tint = ElectricCyan, modifier = Modifier.size(46.dp))
                Spacer(Modifier.width(Spacing.md))
                Column {
                    Text(
                        text = String.format(java.util.Locale.US, "%.1f °C", weather.tempC),
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = weather.conditionLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                weather.thermalZone?.let { tz ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = zoneColor.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, zoneColor.copy(alpha = 0.55f))
                    ) {
                        Text(
                            text = tz.label.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = zoneColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }
                val feelsText = weather.feelsLikeC?.let {
                    "Sensación ${String.format(java.util.Locale.US, "%.1f °C", it)}"
                } ?: "Sensación N/D"
                Text(feelsText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(Spacing.md))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            WeatherMetricTile(
                label = "Lluvia",
                value = weather.rainProbabilityPct?.let { "$it%" } ?: "N/D",
                modifier = Modifier.weight(1f)
            )
            WeatherMetricTile(
                label = "Viento",
                value = weather.windSpeedKmh?.let {
                    "${String.format(java.util.Locale.US, "%.0f", it)} km/h ${weather.windDirection.orEmpty()}".trim()
                } ?: "N/D",
                modifier = Modifier.weight(1f)
            )
            WeatherMetricTile(
                label = "Humedad",
                value = weather.humidityPct?.let { "$it%" } ?: "N/D",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun WeatherMetricTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(Spacing.sm), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
            Text(value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun ShimmerSkeletonCard(modifier: Modifier = Modifier) {
    val inf = rememberInfiniteTransition(label = "skeletonPulse")
    val alpha by inf.animateFloat(
        initialValue = 0.22f,
        targetValue = 0.52f,
        animationSpec = infiniteRepeatable(tween(780), RepeatMode.Reverse),
        label = "skeletonAlpha"
    )
    GlassCard(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.45f)
                .height(14.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
        )
        Spacer(Modifier.height(Spacing.md))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
            )
            Box(
                modifier = Modifier
                    .width(84.dp)
                    .height(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(PitchGreen.copy(alpha = alpha))
            )
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
            )
        }
        Spacer(Modifier.height(Spacing.md))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = alpha * 0.7f))
        )
    }
}

@Composable
fun DashboardSkeletonList() {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        repeat(4) {
            ShimmerSkeletonCard()
        }
    }
}

@Composable
fun EmptyLiveMatchesCard(
    onOpenFixture: () -> Unit,
    onRetry: () -> Unit
) {
    GlassCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Canvas(
                modifier = Modifier
                    .size(72.dp)
                    .semantics { contentDescription = "Cancha de rugby sin partidos activos" }
            ) {
                val w = size.width
                val h = size.height
                drawRoundRect(
                    color = PitchGreen.copy(alpha = 0.22f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f, 16f),
                    style = Stroke(width = 3f)
                )
                drawLine(color = PitchGreen.copy(alpha = 0.35f), start = Offset(w * 0.5f, 4f), end = Offset(w * 0.5f, h - 4f), strokeWidth = 2f)
                drawOval(
                    color = MetallicGold.copy(alpha = 0.85f),
                    topLeft = Offset(w * 0.31f, h * 0.36f),
                    size = androidx.compose.ui.geometry.Size(w * 0.38f, h * 0.28f),
                    style = Stroke(width = 3f)
                )
            }
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = "No hay partidos en vivo ahora",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.secondary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = "Cuando comience un encuentro o haya partidos programados para hoy, verás el marcador minuto a minuto, estadísticas y eventos aquí.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(Spacing.md))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                OutlinedButton(
                    onClick = onRetry,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) {
                    Text("Reintentar")
                }
                Button(
                    onClick = onOpenFixture,
                    colors = ButtonDefaults.buttonColors(containerColor = PitchGreen),
                    modifier = Modifier.weight(1.4f).heightIn(min = 48.dp)
                ) {
                    Text("Ver Fixture")
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (ev.type == MatchEventType.YELLOW_CARD || ev.type == MatchEventType.RED_CARD) {
                        Icon(
                            imageVector = Icons.Outlined.Warning,
                            contentDescription = ev.type.label,
                            tint = accent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                    } else if (ev.type == MatchEventType.TRY) {
                        Icon(
                            imageVector = Icons.Outlined.SportsRugby,
                            contentDescription = "Try",
                            tint = accent,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        text = if (ev.teamSide == "home") "LOCAL" else "VISITA",
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                        fontWeight = FontWeight.Bold
                    )
                }
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
        StatCompareRow("Tries", stats.triesHome, stats.triesAway)
        StatCompareRow("Conversiones", stats.conversionsHome, stats.conversionsAway)
        StatCompareRow("Posesión (%)", stats.possessionHomePct, stats.possessionAwayPct, suffix = "%")
        StatCompareRow("Territorio (%)", stats.territoryHomePct, stats.territoryAwayPct, suffix = "%")
        StatCompareRow("Metros recorridos", stats.metresHome, stats.metresAway)
        StatCompareRow("Quiebres limpios", stats.cleanBreaksHome, stats.cleanBreaksAway)
        StatCompareRow("Defensores superados", stats.defendersBeatenHome, stats.defendersBeatenAway)
        StatCompareRow("Tackles", stats.tacklesHome, stats.tacklesAway)
        StatCompareRow("Turnovers ganados", stats.turnoversWonHome, stats.turnoversWonAway)
        StatCompareRow("Lineouts ganados", stats.lineoutsWonHome, stats.lineoutsWonAway)
        StatCompareRow("Scrums ganados", stats.scrumsWonHome, stats.scrumsWonAway)
        StatCompareRow("Penalidades concedidas", stats.penaltiesHome, stats.penaltiesAway)
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


@Composable
fun MatchOfficialsCard(
    refereeName: String,
    officials: List<MatchOfficial>
) {
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Gavel,
                contentDescription = "Árbitro y autoridades",
                tint = MetallicGold,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(
                text = "Árbitro y Autoridades",
                style = MaterialTheme.typography.titleMedium,
                color = MetallicGold
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        if (officials.isEmpty() && refereeName.isBlank()) {
            Text(
                text = "Árbitro por confirmar (se actualiza automáticamente 30 min antes del inicio).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else if (officials.isEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Árbitro principal", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(refereeName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = PitchGreen)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                officials.forEach { off ->
                    val isMain = off.role.contains("principal", ignoreCase = true)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = off.role,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (off.country.isNotBlank()) "${off.name} (${off.country})" else off.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isMain) FontWeight.Bold else FontWeight.Medium,
                            color = if (isMain) PitchGreen else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MatchLineupShowcaseCard(
    match: RugbyMatch,
    selectedSide: String,
    onSelectSide: (String) -> Unit,
    onSelectPlayer: (isHome: Boolean, player: PlayerProfile) -> Unit
) {
    val isHome = selectedSide == "home"
    val players = if (isHome) match.homeLineup else match.awayLineup
    val activeKit = if (isHome) match.homeKit else match.awayKit
    val roleLabel = if (isHome) match.homeActiveKitRole.label else match.awayActiveKitRole.label
    val teamName = if (isHome) match.homeName else match.awayName

    GlassCard {
        Text(
            text = "Plantel en Cancha · Retratos 4×4",
            style = MaterialTheme.typography.titleMedium,
            color = PitchGreen
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "$teamName · Camiseta: $roleLabel",
            style = MaterialTheme.typography.labelSmall,
            color = MetallicGold
        )
        Spacer(Modifier.height(Spacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            FilterChip(
                selected = isHome,
                onClick = { onSelectSide("home") },
                label = { Text(match.homeAbbr.ifBlank { match.homeName.take(12) }) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                    labelColor = MaterialTheme.colorScheme.onSurface,
                    selectedContainerColor = PitchGreen.copy(alpha = 0.24f),
                    selectedLabelColor = PitchGreen
                ),
                modifier = Modifier.heightIn(min = 48.dp)
            )
            FilterChip(
                selected = !isHome,
                onClick = { onSelectSide("away") },
                label = { Text(match.awayAbbr.ifBlank { match.awayName.take(12) }) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                    labelColor = MaterialTheme.colorScheme.onSurface,
                    selectedContainerColor = MetallicGold.copy(alpha = 0.24f),
                    selectedLabelColor = MetallicGold
                ),
                modifier = Modifier.heightIn(min = 48.dp)
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        if (players.isEmpty()) {
            Text(
                text = "Plantel oficial por confirmar (se precarga 30 min antes del inicio).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@GlassCard
        }

        val starters = players.filter { it.starter }.ifEmpty { players.take(15) }
        val subs = players.filter { !it.starter }

        Text("Titulares (${starters.size})", style = MaterialTheme.typography.labelMedium, color = ElectricCyan, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(Spacing.sm))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            maxItemsInEachRow = 3
        ) {
            starters.forEach { p ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(90.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelectPlayer(isHome, p) }
                        .padding(4.dp)
                ) {
                    BroadcastPlayerMedallion(
                        photoUrl = p.photoUrl,
                        playerName = p.name,
                        kit = activeKit,
                        jerseyNumber = p.jersey,
                        isCaptain = p.captain,
                        size = 70.dp,
                        isOval = true
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = p.name.substringAfterLast(" ").ifBlank { p.name },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = p.posCode.ifBlank { p.position.take(10) },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }

        if (subs.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.md))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            Spacer(Modifier.height(Spacing.sm))
            Text("Suplentes (${subs.size})", style = MaterialTheme.typography.labelMedium, color = MetallicGold, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(Spacing.sm))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                maxItemsInEachRow = 3
            ) {
                subs.forEach { p ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(90.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelectPlayer(isHome, p) }
                            .padding(4.dp)
                    ) {
                        BroadcastPlayerMedallion(
                            photoUrl = p.photoUrl,
                            playerName = p.name,
                            kit = activeKit,
                            jerseyNumber = p.jersey,
                            isCaptain = p.captain,
                            size = 64.dp,
                            isOval = true
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = p.name.substringAfterLast(" ").ifBlank { p.name },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}


@Composable
fun MatchLiveCoverageSection(
    match: RugbyMatch,
    allNews: List<NewsArticle>
) {
    val uriHandler = LocalUriHandler.current
    val homeToken = match.homeName.lowercase().replace(" 7s", "").trim()
    val awayToken = match.awayName.lowercase().replace(" 7s", "").trim()
    val isSevens = match.group.equals("sevens", true) ||
        match.leagueName.contains("sevens", true) ||
        match.leagueName.contains("svns", true) ||
        match.homeName.contains("7s", true)

    val relatedNews = allNews.filter { art ->
        val hay = "${art.title} ${art.summary} ${art.category}".lowercase()
        (homeToken.length >= 4 && homeToken in hay) ||
            (awayToken.length >= 4 && awayToken in hay) ||
            (isSevens && ("svns" in hay || "sevens" in hay || " 7s" in hay || "dubai" in hay)) ||
            (match.leagueShort.length >= 4 && match.leagueShort.lowercase() in hay)
    }.take(4)

    val statusHeadline = when (match.status) {
        MatchStatus.LIVE -> buildString {
            append("EN VIVO")
            if (match.periodDisplay.isNotBlank()) append(" · ${match.periodDisplay}")
            if (match.minuteDisplay.isNotBlank()) append(" (${match.minuteDisplay})")
            append(" — ${match.homeName} ${match.homeScore ?: 0} : ${match.awayScore ?: 0} ${match.awayName}")
        }
        MatchStatus.FINISHED -> "${match.homeName} ${match.homeScore ?: "-"} : ${match.awayScore ?: "-"} ${match.awayName} · Finalizado"
        MatchStatus.POSTPONED -> "${match.homeName} vs ${match.awayName} · Postergado"
        MatchStatus.CANCELLED -> "${match.homeName} vs ${match.awayName} · Cancelado"
        MatchStatus.SCHEDULED -> "${match.homeName} vs ${match.awayName} · Programado (${RugbyDateUtils.formatLocalKickoff(match.timestampSec, match.dateArg)})"
    }

    GlassCard(highlightAccent = match.status == MatchStatus.LIVE) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Cobertura en vivo y noticias del evento",
                style = MaterialTheme.typography.titleMedium,
                color = if (match.status == MatchStatus.LIVE) PitchGreen else MetallicGold
            )
            if (match.status == MatchStatus.LIVE) {
                PulsingLiveIndicator(label = "MINUTO A MINUTO")
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = statusHeadline,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        if (match.events.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.sm))
            val latestEvents = match.events.sortedByDescending { it.minute }.take(5)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                latestEvents.forEach { ev ->
                    val sideTeam = if (ev.teamSide == "away") match.awayName else match.homeName
                    Text(
                        text = "• ${ev.minute}' · ${ev.type.label} ($sideTeam)${if (ev.player.isNotBlank()) " — ${ev.player}" else ""}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (match.status == MatchStatus.LIVE) {
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = "Esperando incidencias detalladas del encuentro. La app actualiza automáticamente cada 15 segundos.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (relatedNews.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.md))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = "Noticias del torneo y protagonistas",
                style = MaterialTheme.typography.labelLarge,
                color = MetallicGold,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(Spacing.xs))
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                relatedNews.forEach { art ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                            .clickable(enabled = art.url.isNotBlank()) {
                                runCatching { uriHandler.openUri(art.url) }
                            }
                            .padding(Spacing.sm)
                    ) {
                        Text(
                            text = art.title,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (art.publishedLabel.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = art.publishedLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricCyan
                            )
                        }
                    }
                }
            }
        }
    }
}
