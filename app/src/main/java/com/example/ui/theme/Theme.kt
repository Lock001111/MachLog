package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class MachinaSemanticColors(
    val operational: Color,
    val operationalContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val critical: Color,
    val criticalContainer: Color,
    val info: Color,
    val infoContainer: Color
)

private val LightSemanticColors = MachinaSemanticColors(
    operational = LightTertiary,
    operationalContainer = LightTertiaryContainer,
    warning = LightSecondary,
    warningContainer = LightSecondaryContainer,
    critical = LightError,
    criticalContainer = LightErrorContainer,
    info = LightPrimary,
    infoContainer = LightPrimaryContainer
)

private val DarkSemanticColors = MachinaSemanticColors(
    operational = DarkTertiary,
    operationalContainer = DarkTertiaryContainer,
    warning = DarkSecondary,
    warningContainer = DarkSecondaryContainer,
    critical = DarkError,
    criticalContainer = DarkErrorContainer,
    info = DarkPrimary,
    infoContainer = DarkPrimaryContainer
)

val LocalMachinaColors = staticCompositionLocalOf { LightSemanticColors }

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer
)

@Composable
fun MachinaLogTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val semanticColors = if (darkTheme) DarkSemanticColors else LightSemanticColors

    CompositionLocalProvider(LocalMachinaColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MachinaTypography,
            content = content
        )
    }
}

fun groupColorForKey(key: String): Color = when (key.uppercase()) {
    "AMBER" -> GroupAccentAmber
    "EMERALD" -> GroupAccentEmerald
    "CRIMSON" -> GroupAccentCrimson
    "VIOLET" -> GroupAccentViolet
    "TEAL" -> GroupAccentTeal
    else -> GroupAccentCobalt
}
