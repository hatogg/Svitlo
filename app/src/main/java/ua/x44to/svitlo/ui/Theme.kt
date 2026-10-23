package ua.x44to.svitlo.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import ua.x44to.svitlo.data.Slot

/** Теплий жовтий — як світло в квартирі ввечері; темно-синій — як без нього. */
private val Light = lightColorScheme(
    primary = Color(0xFF6F5400), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE08A), onPrimaryContainer = Color(0xFF231A00),
    secondary = Color(0xFF4B4F63), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E3F3), onSecondaryContainer = Color(0xFF181B2B),
    tertiary = Color(0xFF9A4A00), onTertiary = Color.White,
    error = Color(0xFFBA1A1A), onError = Color.White,
    background = Color(0xFFFFFBF2), onBackground = Color(0xFF1E1B13),
    surface = Color(0xFFFFFBF2), onSurface = Color(0xFF1E1B13),
    surfaceVariant = Color(0xFFEDE1CB), onSurfaceVariant = Color(0xFF4D4639),
    outline = Color(0xFF7F7667), outlineVariant = Color(0xFFD0C5B0),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFBF3E3), surfaceContainer = Color(0xFFF5EDDD),
    surfaceContainerHigh = Color(0xFFEFE7D7), surfaceContainerHighest = Color(0xFFE9E2D2),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFFC94A), onPrimary = Color(0xFF3B2F00),
    primaryContainer = Color(0xFF554000), onPrimaryContainer = Color(0xFFFFE08A),
    secondary = Color(0xFFC4C6DA), onSecondary = Color(0xFF2D3042),
    secondaryContainer = Color(0xFF3F4357), onSecondaryContainer = Color(0xFFE0E1F6),
    tertiary = Color(0xFFFFB77A), onTertiary = Color(0xFF4F2500),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    background = Color(0xFF121318), onBackground = Color(0xFFE5E2E9),
    surface = Color(0xFF121318), onSurface = Color(0xFFE5E2E9),
    surfaceVariant = Color(0xFF46464F), onSurfaceVariant = Color(0xFFC7C5D0),
    outline = Color(0xFF91909A), outlineVariant = Color(0xFF46464F),
    surfaceContainerLowest = Color(0xFF0D0E12), surfaceContainerLow = Color(0xFF1A1B21), surfaceContainer = Color(0xFF1E1F25),
    surfaceContainerHigh = Color(0xFF292A30), surfaceContainerHighest = Color(0xFF34343B),
)

@Composable
fun isDark(theme: Int): Boolean = when (theme) {
    1 -> false
    2 -> true
    else -> isSystemInDarkTheme()
}

@Composable
fun SvitloTheme(theme: Int, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isDark(theme)) Dark else Light, content = content)
}

private val dark: Boolean
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.surface.luminance() < 0.5f

val YELLOW = Color(0xFFFFC94A)
val INK = Color(0xFF1B1C22)

/** Кольори великої картки стану: тло і текст. */
object StateColors {
    @Composable @ReadOnlyComposable
    fun bg(s: Slot): Color = when (s) {
        Slot.ON -> YELLOW
        Slot.OFF -> if (dark) Color(0xFF2E3142) else Color(0xFF24262F)
        Slot.MAYBE -> if (dark) Color(0xFF4A3417) else Color(0xFFFFDDB3)
        Slot.UNKNOWN -> MaterialTheme.colorScheme.surfaceContainerHigh
    }

    @Composable @ReadOnlyComposable
    fun fg(s: Slot): Color = when (s) {
        Slot.ON -> INK
        Slot.OFF -> Color.White
        Slot.MAYBE -> if (dark) Color(0xFFFFD7A8) else Color(0xFF3A2300)
        Slot.UNKNOWN -> MaterialTheme.colorScheme.onSurface
    }

    /** Колір половини години на шкалі. */
    @Composable @ReadOnlyComposable
    fun bar(s: Slot): Color = when (s) {
        Slot.ON -> YELLOW
        Slot.OFF -> if (dark) Color(0xFF4A4D5E) else Color(0xFF2B2D38)
        Slot.MAYBE -> Color(0xFFE58A3A)
        Slot.UNKNOWN -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
}
