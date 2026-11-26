package com.rugbypro.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rugbypro.app.R
import com.rugbypro.app.domain.model.ThemeMode

val PitchDarkBg = Color(0xFF0B0F14)
val PitchDarkSurface = Color(0xFF121922)
val PitchDarkSurfaceAlt = Color(0xFF192431)
val PitchDarkBorder = Color(0xFF28384A)

val PitchGreen = Color(0xFF00C853)
val PitchEmerald = Color(0xFF00695C)
val MetallicGold = Color(0xFFFFD700)
val PearlWhite = Color(0xFFF5F5F5)
val ElectricCyan = Color(0xFF00E5FF)
val LiveCrimson = Color(0xFFF43F5E)
val PendingAmber = Color(0xFFF59E0B)

val LightBg = Color(0xFFF2F6FA)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceAlt = Color(0xFFE6EEF4)
val LightBorder = Color(0xFFCBD8E4)
val LightGreen = Color(0xFF007E33)
val LightGold = Color(0xFF9C6D00)

object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
}

@OptIn(ExperimentalTextApi::class)
val OutfitFontFamily = FontFamily(
    Font(
        resId = R.font.outfit_variable,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400))
    ),
    Font(
        resId = R.font.outfit_variable,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(600))
    ),
    Font(
        resId = R.font.outfit_variable,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(800))
    )
)

@OptIn(ExperimentalTextApi::class)
val InterFontFamily = FontFamily(
    Font(
        resId = R.font.inter_variable,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400))
    ),
    Font(
        resId = R.font.inter_variable,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500))
    ),
    Font(
        resId = R.font.inter_variable,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700))
    )
)

private val DarkScheme = darkColorScheme(
    primary = PitchGreen,
    onPrimary = Color(0xFF041A0D),
    secondary = MetallicGold,
    onSecondary = Color(0xFF1B1400),
    tertiary = ElectricCyan,
    onTertiary = Color(0xFF001C22),
    background = PitchDarkBg,
    onBackground = PearlWhite,
    surface = PitchDarkSurface,
    onSurface = PearlWhite,
    surfaceVariant = PitchDarkSurfaceAlt,
    onSurfaceVariant = Color(0xFFB5C4D6),
    outline = PitchDarkBorder
)

private val LightScheme = lightColorScheme(
    primary = LightGreen,
    onPrimary = Color.White,
    secondary = LightGold,
    onSecondary = Color.White,
    tertiary = Color(0xFF007799),
    onTertiary = Color.White,
    background = LightBg,
    onBackground = Color(0xFF0F172A),
    surface = LightSurface,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = LightSurfaceAlt,
    onSurfaceVariant = Color(0xFF475569),
    outline = LightBorder
)

val RugbyTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = OutfitFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp
    ),
    titleLarge = TextStyle(
        fontFamily = OutfitFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = OutfitFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )
)

val ScoreDisplayStyle = TextStyle(
    fontFamily = OutfitFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 26.sp,
    fontFeatureSettings = "tnum"
)

@Composable
fun RugbyProTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.AUTO -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    MaterialTheme(
        colorScheme = if (isDark) DarkScheme else LightScheme,
        typography = RugbyTypography,
        content = content
    )
}
