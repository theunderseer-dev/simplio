package com.theunderseer.simplio.shared.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val SilverLight = lightColorScheme(
    primary = Color(0xFF343D49),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE1E6ED),
    onPrimaryContainer = Color(0xFF202832),
    secondary = Color(0xFF505E70),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE5EAF1),
    onSecondaryContainer = Color(0xFF273443),
    tertiary = Color(0xFF355E86),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDCEAF8),
    onTertiaryContainer = Color(0xFF18344F),
    background = Color(0xFFF3F4F6),
    onBackground = Color(0xFF20252C),
    surface = Color(0xFFFAFBFC),
    onSurface = Color(0xFF20252C),
    surfaceVariant = Color(0xFFE8EBEF),
    onSurfaceVariant = Color(0xFF555F6C),
    surfaceDim = Color(0xFFDADDE2),
    surfaceBright = Color(0xFFFAFBFC),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6F7F9),
    surfaceContainer = Color(0xFFEFF1F4),
    surfaceContainerHigh = Color(0xFFE8EBEF),
    surfaceContainerHighest = Color(0xFFE1E5EA),
    outline = Color(0xFF747E8A),
    outlineVariant = Color(0xFFD4D9E0),
    error = Color(0xFFB32636),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFCE8EB),
    onErrorContainer = Color(0xFF761823),
    inverseSurface = Color(0xFF2C323B),
    inverseOnSurface = Color(0xFFF1F3F6),
    inversePrimary = Color(0xFFC5CFDC),
    surfaceTint = Color(0xFF343D49),
)

internal val SilverDark = darkColorScheme(
    primary = Color(0xFFD5DCE5),
    onPrimary = Color(0xFF232B35),
    primaryContainer = Color(0xFF363F4B),
    onPrimaryContainer = Color(0xFFE8EDF4),
    secondary = Color(0xFFBBC8D9),
    onSecondary = Color(0xFF243140),
    secondaryContainer = Color(0xFF344252),
    onSecondaryContainer = Color(0xFFE1EAF5),
    tertiary = Color(0xFFA8CBEF),
    onTertiary = Color(0xFF17334F),
    tertiaryContainer = Color(0xFF294765),
    onTertiaryContainer = Color(0xFFDCEAF8),
    background = Color(0xFF14171C),
    onBackground = Color(0xFFE7EBF0),
    surface = Color(0xFF1C2027),
    onSurface = Color(0xFFE7EBF0),
    surfaceVariant = Color(0xFF333B47),
    onSurfaceVariant = Color(0xFFB3BDCA),
    surfaceDim = Color(0xFF14171C),
    surfaceBright = Color(0xFF353C47),
    surfaceContainerLowest = Color(0xFF101318),
    surfaceContainerLow = Color(0xFF191D24),
    surfaceContainer = Color(0xFF222730),
    surfaceContainerHigh = Color(0xFF2A303A),
    surfaceContainerHighest = Color(0xFF333B47),
    outline = Color(0xFF8C97A6),
    outlineVariant = Color(0xFF414B59),
    error = Color(0xFFFFB0B8),
    onError = Color(0xFF641A26),
    errorContainer = Color(0xFF702331),
    onErrorContainer = Color(0xFFFFDADF),
    inverseSurface = Color(0xFFE7EBF0),
    inverseOnSurface = Color(0xFF29313C),
    inversePrimary = Color(0xFF343D49),
    surfaceTint = Color(0xFFD5DCE5),
)

private val SilverTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 30.sp,
        lineHeight = 38.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 26.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 23.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 21.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Normal,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
    ),
)

@Composable
fun SimplioTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) SilverDark else SilverLight,
        typography = SilverTypography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(6.dp),
            small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(14.dp),
            large = RoundedCornerShape(20.dp),
            extraLarge = RoundedCornerShape(24.dp),
        ),
        content = content,
    )
}

val ColorScheme.positive: Color
    get() = if (surface.luminance() < 0.5f) Color(0xFF8BD8AF) else Color(0xFF246747)

val ColorScheme.warning: Color
    get() = if (surface.luminance() < 0.5f) Color(0xFFFFD07A) else Color(0xFF805400)
