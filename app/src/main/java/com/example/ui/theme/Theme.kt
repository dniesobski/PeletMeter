package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = EmberOrange,
    onPrimary = Color.White,
    primaryContainer = EmberContainerLight,
    onPrimaryContainer = Color(0xFF471802),
    secondary = SpruceGreen,
    onSecondary = Color.White,
    secondaryContainer = SpruceContainerLight,
    onSecondaryContainer = Color(0xFF0A2B18),
    tertiary = WarmAmber,
    onTertiary = CharcoalInk,
    background = WarmCreamBackground,
    onBackground = CharcoalInk,
    surface = WarmCreamSurface,
    onSurface = CharcoalInk,
    surfaceVariant = WarmOatSurfaceVariant,
    onSurfaceVariant = SlateMuted,
    outline = WarmOutline,
    outlineVariant = Color(0xFFE7E0D5),
    error = DangerRed,
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = EmberOrangeDark,
    onPrimary = Color(0xFF3E1501),
    primaryContainer = EmberContainerDark,
    onPrimaryContainer = Color(0xFFFFD8C4),
    secondary = SpruceGreenDark,
    onSecondary = Color(0xFF062916),
    secondaryContainer = SpruceContainerDark,
    onSecondaryContainer = Color(0xFFC5F2D6),
    tertiary = WarmAmberDark,
    onTertiary = Color(0xFF2E1E00),
    background = NightSlateBackground,
    onBackground = IvoryInk,
    surface = NightSlateSurface,
    onSurface = IvoryInk,
    surfaceVariant = NightSlateSurfaceVariant,
    onSurfaceVariant = AshMuted,
    outline = NightOutline,
    outlineVariant = Color(0xFF2D353F),
    error = DangerRedDark,
    onError = Color(0xFF420806)
)

private val PeletMetrShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun PeletMetrTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = PeletMetrTypography,
        shapes = PeletMetrShapes,
        content = content
    )
}
