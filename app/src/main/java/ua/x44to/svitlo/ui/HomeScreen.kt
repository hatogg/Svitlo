package ua.x44to.svitlo.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.PowerOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import ua.x44to.svitlo.data.DayPlan
import ua.x44to.svitlo.data.KYIV
import ua.x44to.svitlo.data.Kind
import ua.x44to.svitlo.data.L
import ua.x44to.svitlo.data.Place
import ua.x44to.svitlo.data.Regions
import ua.x44to.svitlo.data.Repo
import ua.x44to.svitlo.data.Slot
import ua.x44to.svitlo.data.Store
import ua.x44to.svitlo.data.Timeline
import ua.x44to.svitlo.data.Words
import ua.x44to.svitlo.data.t
import java.time.ZonedDateTime

/** Поточний час, що оновлюється щопівхвилини. */
@Composable
fun rememberNow(): ZonedDateTime {
    var now by remember { mutableStateOf(ZonedDateTime.now(KYIV)) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = ZonedDateTime.now(KYIV)
        }
    }
    return now
}

@Composable
fun HomeScreen(pad: PaddingValues, onAdd: () -> Unit, onEdit: (Place) -> Unit) {
    val place = Store.place
    val now = rememberNow()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 24.dp),
    ) {
        item { PlacesRow(onAdd) }
        if (place == null) {
            item { Welcome(onAdd) }
            return@LazyColumn
        }
        val region = place.region?.let { Repo.regions[it] }
        val days = Timeline.days(place, region, now.toLocalDate())
        val st = Timeline.status(days, now)
        val idx = Timeline.index(now, days[0].date)
        item { StatusCard(place, st, now, onEdit = { onEdit(place) }) }
        item { DayCard(t("Сьогодні", "Today"), days[0], idx, today = true) }
        item { DayCard(t("Завтра", "Tomorrow"), days[1], null, today = false) }
        val week = region?.preset?.get(place.group)
        if (week != null) item {
            Card(t("Можливі відключення на тиждень", "Possible outages this week")) {
                WeekGrid(week, now.dayOfWeek.value, if (L.ukr) listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Нд") else listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su"))
                Spacer(Modifier.height(8.dp))
                Text(
                    t("Помаранчеве — коли можуть вимкнути, якщо оголосять графіки. Точний графік на день з'являється вище.",
                        "Orange is when power may go off if schedules are announced. The exact daily schedule shows above."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { Footer(place) }
    }
}

@Composable
private fun PlacesRow(onAdd: () -> Unit) {
    val places = Store.places
    if (places.isEmpty()) return
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (p in places) FilterChip(selected = p.id == Store.place?.id, onClick = { Store.select(p.id) }, label = { Text(p.name) })
        AssistChip(onClick = onAdd, label = { Text(t("Додати", "Add")) }, leadingIcon = { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)) })
    }
}

@Composable
private fun Welcome(onAdd: () -> Unit) {
    Surface(color = YELLOW, shape = RoundedCornerShape(32.dp), modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(28.dp)) {
            Icon(Icons.Rounded.Lightbulb, null, tint = INK, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(16.dp))
            Text(t("Коли буде світло", "When the power is on"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, color = INK)
            Spacer(Modifier.height(8.dp))
            Text(
                t("Додайте свою адресу й чергу, і «Світло» нагадає за 15 хвилин до відключення та покаже, коли світло повернеться.",
                    "Add your address and group, and Svitlo will remind you 15 minutes before an outage and show when the power is back."),
                style = MaterialTheme.typography.bodyLarge, color = INK.copy(alpha = 0.8f),
            )
            Spacer(Modifier.height(20.dp))
            Button(onClick = onAdd) { Text(t("Додати адресу", "Add an address")) }
        }
    }
}

@Composable
private fun StatusCard(place: Place, st: ua.x44to.svitlo.data.Status, now: ZonedDateTime, onEdit: () -> Unit) {
    val bg by animateColorAsState(StateColors.bg(st.now), label = "bg")
    val fg = StateColors.fg(st.now)
    val icon: ImageVector = when (st.now) {
        Slot.ON -> Icons.Rounded.Lightbulb
        Slot.OFF -> Icons.Rounded.PowerOff
        Slot.MAYBE -> Icons.Rounded.WarningAmber
        Slot.UNKNOWN -> Icons.Rounded.HelpOutline
    }
    val region = Regions.byId(place.region)
    val group = place.region?.let { Repo.regions[it] }?.groupName(place.group)
    Surface(color = bg, shape = RoundedCornerShape(32.dp), modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(start = 24.dp, end = 12.dp, top = 20.dp, bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    listOfNotNull(place.name, region?.title ?: t("свій графік", "own schedule"), group?.takeIf { it.isNotBlank() }).joinToString(" · "),
                    style = MaterialTheme.typography.labelLarge, color = fg.copy(alpha = 0.75f), modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, t("Змінити адресу", "Edit address"), tint = fg.copy(alpha = 0.75f)) }
            }
            Surface(color = fg.copy(alpha = 0.10f), shape = CircleShape, modifier = Modifier.size(72.dp)) {
                Icon(icon, null, tint = fg, modifier = Modifier.padding(16.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(Words.title(st), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold, color = fg)
            Spacer(Modifier.height(6.dp))
            Text(Words.line(st, now), style = MaterialTheme.typography.titleLarge, color = fg)
            Words.countdown(st, now)?.let {
                Text(it, style = MaterialTheme.typography.bodyLarge, color = fg.copy(alpha = 0.75f), modifier = Modifier.padding(top = 2.dp))
            }
            if (!st.sure && st.now != Slot.UNKNOWN) Text(
                t("Графіка на сьогодні немає — це можливі відключення з тижневого графіка.", "No schedule for today — these are possible outages from the weekly plan."),
                style = MaterialTheme.typography.bodySmall, color = fg.copy(alpha = 0.75f), modifier = Modifier.padding(top = 10.dp, end = 12.dp),
            )
        }
    }
}

@Composable
private fun Card(title: String, note: String? = null, content: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column(Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (note != null) Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun DayCard(title: String, day: DayPlan, nowIndex: Int?, today: Boolean) {
    val note = when (day.kind) {
        Kind.FACT -> t("Графік обленерго", "Utility schedule")
        Kind.MANUAL -> t("Ваш графік", "Your schedule")
        Kind.POSSIBLE -> if (today) t("Графіка на сьогодні немає — показано можливі відключення", "No schedule today — showing possible outages")
        else t("Графік на завтра ще не опублікували — показано можливі відключення", "Tomorrow's schedule isn't out yet — showing possible outages")
        Kind.NONE -> t("Графіка немає", "No schedule")
    }
    Card(title, note) {
        DayBar(day.slots, nowIndex)
        val spans = Timeline.outages(day)
        Spacer(Modifier.height(12.dp))
        if (day.kind == Kind.NONE) return@Card
        if (spans.isEmpty()) {
            Text(t("Відключень немає", "No outages"), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            return@Card
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (sp in spans) Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = StateColors.bar(sp.slot), shape = CircleShape, modifier = Modifier.size(10.dp)) {}
                Spacer(Modifier.width(10.dp))
                Text(Words.span(sp), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.width(118.dp))
                Text(
                    (if (sp.slot == Slot.OFF) t("без світла · ", "off · ") else t("можливо · ", "possible · ")) + Words.hours(sp.to - sp.from),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val off = day.slots.count { it == Slot.OFF }
        if (off > 0) Text(
            t("Разом без світла: ", "Off in total: ") + Words.hours(off),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp),
        )
    }
}

@Composable
private fun Footer(place: Place) {
    val ctx = LocalContext.current
    val region = place.region?.let { Repo.regions[it] }
    val text = when {
        place.isManual -> t("Графік ви вводили самі. Змінити його можна кнопкою ✎ угорі.", "You entered this schedule yourself. Edit it with ✎ above.")
        region == null -> t("Графік ще не завантажено. Перевірте інтернет.", "The schedule hasn't loaded yet. Check the internet.")
        else -> buildString {
            region.factUpdate?.let { append(t("Графік обленерго від $it. ", "Utility schedule from $it. ")) }
            val got = Repo.fetchedAt(ctx, region.id)
            if (got > 0) append(t("Завантажено ", "Downloaded ") + L.ago(got) + ". ")
            Repo.error?.let { append(t("Не вдалося оновити: показано збережений графік.", "Couldn't update: showing the saved schedule.")) }
        }
    }
    Text(
        text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
    )
}
