package com.folio.wealth.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val SavingsGreen = Color(0xFF67B899)
val StockBlue = Color(0xFF86A9DF)
val FundAmber = Color(0xFFDAB77A)
val NegativeRed = Color(0xFFF29B9B)

private val dark = darkColorScheme(
    primary = Color(0xFF98DDBB),
    onPrimary = Color(0xFF103828),
    primaryContainer = Color(0xFF1E4033),
    onPrimaryContainer = Color(0xFFEDF8F0),
    background = Color(0xFF101C19),
    onBackground = Color(0xFFEDF3EE),
    surface = Color(0xFF192923),
    onSurface = Color(0xFFEDF3EE),
    surfaceVariant = Color(0xFF253A31),
    onSurfaceVariant = Color(0xFFB2C4B9),
    outline = Color(0xFF52695B),
    outlineVariant = Color(0xFF30473A),
    surfaceContainerLowest = Color(0xFF0B1511),
    surfaceContainerLow = Color(0xFF14211B),
    surfaceContainer = Color(0xFF192923),
    surfaceContainerHigh = Color(0xFF22352B),
    surfaceContainerHighest = Color(0xFF2A4033),
    secondary = Color(0xFFB4CDBC),
    onSecondary = Color(0xFF213B2C),
    error = NegativeRed,
)

private val light = lightColorScheme(
    primary = Color(0xFF226B4D),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE1EFE4),
    onPrimaryContainer = Color(0xFF173E2A),
    background = Color(0xFFF5F6F0),
    onBackground = Color(0xFF1C3024),
    surface = Color(0xFFFEFFFA),
    onSurface = Color(0xFF1C3024),
    surfaceVariant = Color(0xFFEBF0E8),
    onSurfaceVariant = Color(0xFF526558),
    outline = Color(0xFF7B8E7F),
    outlineVariant = Color(0xFFDCE5DA),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F5ED),
    surfaceContainer = Color(0xFFECF1E7),
    surfaceContainerHigh = Color(0xFFE6EDDF),
    surfaceContainerHighest = Color(0xFFDFE8D8),
    secondary = Color(0xFF506E59),
    onSecondary = Color.White,
    error = Color(0xFFB94350),
)

private fun type(size: Int, height: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = height.sp,
    fontFeatureSettings = "tnum",
)

private val folioTypography = Typography(
    displaySmall = type(34, 42, FontWeight.Bold),
    headlineLarge = type(30, 38, FontWeight.Bold),
    headlineMedium = type(28, 36, FontWeight.Bold),
    headlineSmall = type(24, 32, FontWeight.SemiBold),
    titleLarge = type(24, 32, FontWeight.Bold),
    titleMedium = type(17, 24, FontWeight.SemiBold),
    titleSmall = type(15, 22, FontWeight.SemiBold),
    bodyLarge = type(16, 24),
    bodyMedium = type(14, 22),
    bodySmall = type(13, 20),
    labelLarge = type(14, 20, FontWeight.Medium),
    labelMedium = type(13, 18, FontWeight.Medium),
    labelSmall = type(12, 18),
)

@Composable
fun FolioTheme(isDark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isDark) dark else light,
        typography = folioTypography,
        content = content,
    )
}
