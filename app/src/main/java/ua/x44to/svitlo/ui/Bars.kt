package ua.x44to.svitlo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ua.x44to.svitlo.data.SLOTS
import ua.x44to.svitlo.data.Slot

/** Шкала дня: 48 половин години, позначка «зараз», підписи годин. */
@Composable
fun DayBar(slots: List<Slot>, nowIndex: Int?, modifier: Modifier = Modifier, height: Int = 28) {
    val colors = Slot.entries.associateWith { StateColors.bar(it) }
    val marker = MaterialTheme.colorScheme.onSurface
    val passed = MaterialTheme.colorScheme.surface
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(height.dp)) {
            val gap = 2.dp.toPx()
            val cell = (size.width - gap * (SLOTS - 1)) / SLOTS
            val r = CornerRadius(3.dp.toPx())
            for (i in 0 until SLOTS) {
                val x = i * (cell + gap)
                drawRoundRect(colors.getValue(slots[i]), Offset(x, 3.dp.toPx()), Size(cell, size.height - 6.dp.toPx()), r)
                // що вже минуло — притушити
                if (nowIndex != null && i < nowIndex) drawRoundRect(passed.copy(alpha = 0.55f), Offset(x, 3.dp.toPx()), Size(cell, size.height - 6.dp.toPx()), r)
            }
            if (nowIndex != null && nowIndex in 0 until SLOTS) {
                val x = nowIndex * (cell + gap) + cell / 2
                drawRoundRect(marker, Offset(x - 1.5.dp.toPx(), 0f), Size(3.dp.toPx(), size.height), CornerRadius(1.5.dp.toPx()))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
            for (h in listOf(0, 6, 12, 18, 24)) {
                Box(Modifier.weight(if (h == 0 || h == 24) 0.5f else 1f)) {
                    Text(
                        "%02d".format(h), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = when (h) { 0 -> TextAlign.Start; 24 -> TextAlign.End; else -> TextAlign.Center },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** Тиждень можливих відключень: 7 рядків по 48 половин. */
@Composable
fun WeekGrid(week: Map<Int, List<Slot>>, today: Int, dayNames: List<String>) {
    val colors = Slot.entries.associateWith { StateColors.bar(it) }
    val strong = MaterialTheme.colorScheme.primary
    Column {
        for (d in 1..7) {
            val slots = week[d] ?: List(SLOTS) { Slot.UNKNOWN }
            Row(Modifier.padding(vertical = 2.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(
                    dayNames[d - 1], style = MaterialTheme.typography.labelMedium,
                    color = if (d == today) strong else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(28.dp),
                )
                Canvas(Modifier.weight(1f).height(14.dp)) {
                    val gap = 1.dp.toPx()
                    val cell = (size.width - gap * (SLOTS - 1)) / SLOTS
                    for (i in 0 until SLOTS) {
                        drawRoundRect(colors.getValue(slots[i]), Offset(i * (cell + gap), 0f), Size(cell, size.height), CornerRadius(2.dp.toPx()))
                    }
                }
            }
        }
    }
}
