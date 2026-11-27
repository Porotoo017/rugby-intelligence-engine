package com.rugbypro.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rugbypro.app.R
import com.rugbypro.app.domain.model.ThemeMode

val PitchDarkBg = Color(0xFF0B0F14)
val PitchDarkSurface = Color(0xFF121821)
val PitchDarkCard = Color(0xFF17202C)
val PitchGreen = Color(0xFF00C853)
val DeepEmerald = Color(0xFF00695C)
val MetallicGold = Color(0xFFFFD700)
val PearlWhite = Color(0xFFF5F5F5)
val ElectricCyan = Color(0xFF00E5FF)
val LiveCrimson = Color(0xFFFF3B5C)
val PendingAmber = Color(0xFFFFB300)
val GlassBorderDark = Color(0x38FFFFFF)
val MutedSlate = Color(0xFF9FB0C3)

val LightBg = Color(0xFFF4F7FA)
val LightSurface = Color(0xFFFFFFFF)
val LightCard = Color(0xFFE9EFF5)
val LightGreen = Color(0xFF008A3C)
val LightGold = Color(0xFF9C7600)
val LightTextPrimary = Color(0xFF0B0F14)
val LightTextMuted = Color(0xFF475569)

object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
}

private fun makeOutfitFamily(): FontFamily = FontFamily(
    Font(R.font.outfit_regular, FontWeight.Normal),
    Font(R.font.outfit_semibold, FontWeight.SemiBold),
    Font(R.font.outfit_bold, FontWeight.Bold),
    Font(R.font.outfit_extrabold, FontWeight.ExtraBold)
)

private fun makeInterFamily(): FontFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold)
)

val OutfitFontFamily = makeOutfitFamily()
val InterFontFamily = makeInterFamily()

private val RugbyDarkColors = darkColorScheme(
    primary = PitchGreen,
    onPrimary = Color(0xFF02160A),
    primaryContainer = DeepEmerald,
    onPrimaryContainer = PearlWhite,
    secondary = MetallicGold,
    onSecondary = Color(0xFF1A1400),
    tertiary = ElectricCyan,
    background = PitchDarkBg,
    onBackground = PearlWhite,
    surface = PitchDarkSurface,
    onSurface = PearlWhite,
    surfaceVariant = PitchDarkCard,
    onSurfaceVariant = MutedSlate,
    outline = GlassBorderDark,
    error = LiveCrimson
)

private val RugbyLightColors = lightColorScheme(
    primary = LightGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5F5E3),
    onPrimaryContainer = Color(0xFF043B1B),
    secondary = LightGold,
    onSecondary = Color.White,
    tertiary = Color(0xFF007A8C),
    background = LightBg,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightCard,
    onSurfaceVariant = LightTextMuted,
    outline = Color(0x280B0F14),
    error = Color(0xFFD32F2F)
)

private val RugbyTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = OutfitFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 32.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = OutfitFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleLarge = TextStyle(
        fontFamily = OutfitFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = OutfitFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelSmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp
    )
)

@Composable
fun RugbyProTheme(
    themeMode: ThemeMode,
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        ThemeMode.AUTO -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    MaterialTheme(
        colorScheme = if (useDark) RugbyDarkColors else RugbyLightColors,
        typography = RugbyTypography,
        content = content
    )
}
