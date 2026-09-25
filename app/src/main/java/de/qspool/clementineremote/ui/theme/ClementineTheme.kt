package de.qspool.clementineremote.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/*
 * Clementine's colours as Material 3 schemes, generated with Google's material-color-utilities
 * (the Fidelity scheme, 2021 spec, via the materialyoucolor Python port): Clementine's orange
 * (#db6835) as the source colour, keeping its intensity, and the purple of its gradient
 * (#af597d) as the tertiary colour. The connect screen's gradient uses the same two colours.
 */

val ClementineLightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF9F3C09),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC05422),
    onPrimaryContainer = Color(0xFFFFFBFF),
    inversePrimary = Color(0xFFFFB598),
    secondary = Color(0xFF87503A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFDB69A),
    onSecondaryContainer = Color(0xFF79452F),
    tertiary = Color(0xFF914164),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFAE597D),
    onTertiaryContainer = Color(0xFFFFFBFF),
    background = Color(0xFFFFF8F6),
    onBackground = Color(0xFF241915),
    surface = Color(0xFFFFF8F6),
    onSurface = Color(0xFF241915),
    surfaceVariant = Color(0xFFFBDCD1),
    onSurfaceVariant = Color(0xFF57423A),
    surfaceTint = Color(0xFFA23F0C),
    inverseSurface = Color(0xFF3A2E29),
    inverseOnSurface = Color(0xFFFFEDE7),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    outline = Color(0xFF8A7269),
    outlineVariant = Color(0xFFDEC0B6),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFF8F6),
    surfaceContainer = Color(0xFFFFE9E2),
    surfaceContainerHigh = Color(0xFFF9E4DC),
    surfaceContainerHighest = Color(0xFFF3DED7),
    surfaceContainerLow = Color(0xFFFFF1EC),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFEAD6CF),
)

val ClementineDarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFFFB598),
    onPrimary = Color(0xFF591C00),
    primaryContainer = Color(0xFFE46F3B),
    onPrimaryContainer = Color(0xFF431300),
    inversePrimary = Color(0xFFA23F0C),
    secondary = Color(0xFFFDB69A),
    onSecondary = Color(0xFF502411),
    secondaryContainer = Color(0xFF6E3C27),
    onSecondaryContainer = Color(0xFFEEA88D),
    tertiary = Color(0xFFFFB0CD),
    onTertiary = Color(0xFF5B1437),
    tertiaryContainer = Color(0xFFD07499),
    onTertiaryContainer = Color(0xFF480429),
    background = Color(0xFF1B110D),
    onBackground = Color(0xFFF3DED7),
    surface = Color(0xFF1B110D),
    onSurface = Color(0xFFF3DED7),
    surfaceVariant = Color(0xFF57423A),
    onSurfaceVariant = Color(0xFFDEC0B6),
    surfaceTint = Color(0xFFFFB598),
    inverseSurface = Color(0xFFF3DED7),
    inverseOnSurface = Color(0xFF3A2E29),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFFA58B81),
    outlineVariant = Color(0xFF57423A),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF433632),
    surfaceContainer = Color(0xFF281D19),
    surfaceContainerHigh = Color(0xFF332723),
    surfaceContainerHighest = Color(0xFF3F322D),
    surfaceContainerLow = Color(0xFF241915),
    surfaceContainerLowest = Color(0xFF150C08),
    surfaceDim = Color(0xFF1B110D),
)

/**
 * The app's Compose theme: Clementine's colours, light or dark as the system is. With
 * [dynamicColor] on Android 12 and later, the colours come from the wallpaper instead.
 */
@Composable
fun ClementineTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> ClementineDarkColors
        else -> ClementineLightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
