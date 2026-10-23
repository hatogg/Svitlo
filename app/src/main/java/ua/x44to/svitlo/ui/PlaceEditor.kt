@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package ua.x44to.svitlo.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ua.x44to.svitlo.data.L
import ua.x44to.svitlo.data.Place
import ua.x44to.svitlo.data.Regions
import ua.x44to.svitlo.data.Repo
import ua.x44to.svitlo.data.SLOTS
import ua.x44to.svitlo.data.Store
import ua.x44to.svitlo.data.t
import ua.x44to.svitlo.ui.Links.open

private const val MANUAL = "manual"

/** Додати чи змінити адресу: регіон і черга, або свій графік. */
@Composable
fun PlaceEditor(pad: PaddingValues, initial: Place?, onDone: (Place?) -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: if (Store.places.isEmpty()) t("Дім", "Home") else "") }
    var region by remember { mutableStateOf(initial?.region ?: if (initial?.manual != null) MANUAL else null) }
    var group by remember { mutableStateOf(initial?.group) }
    val manual = remember { mutableStateListOf<String>().apply { addAll(initial?.manual ?: List(7) { "0".repeat(SLOTS) }) } }
    var askDelete by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 24.dp)
            .padding(horizontal = 16.dp),
    ) {
        OutlinedTextField(
            value = name, onValueChange = { name = it.take(30) },
            label = { Text(t("Назва", "Name")) }, placeholder = { Text(t("Дім, робота, батьки", "Home, work, parents")) },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Section(t("Де", "Where"))
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(vertical = 6.dp)) {
                for (r in Regions.ALL) Choice(r.title, region == r.id) {
                    if (region != r.id) group = null
                    region = r.id
                }
                Choice(t("Інша область — свій графік", "Another region — my own schedule"), region == MANUAL) { region = MANUAL; group = null }
            }
        }

        val reg = region
        if (reg != null && reg != MANUAL) {
            Section(t("Черга", "Group"))
            GroupPicker(reg, group) { group = it }
        } else if (reg == MANUAL) {
            Section(t("Коли немає світла", "When the power is off"))
            ManualWeek(manual)
        }

        Spacer(Modifier.height(24.dp))
        val ok = name.isNotBlank() && ((reg == MANUAL) || (reg != null && group != null))
        Button(
            onClick = {
                val p = Place(
                    id = initial?.id ?: Store.newId(),
                    name = name.trim(),
                    region = reg.takeIf { it != MANUAL },
                    group = group.takeIf { reg != MANUAL },
                    manual = if (reg == MANUAL) manual.toList() else null,
                )
                Store.save(p)
                onDone(p)
            },
            enabled = ok, modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text(t("Зберегти", "Save"), style = MaterialTheme.typography.titleMedium) }
        if (initial != null) TextButton(onClick = { askDelete = true }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(t("Видалити адресу", "Delete the address"), color = MaterialTheme.colorScheme.error)
        }
    }
    if (askDelete && initial != null) AlertDialog(
        onDismissRequest = { askDelete = false },
        title = { Text(t("Видалити «${initial.name}»?", "Delete “${initial.name}”?")) },
        confirmButton = { TextButton(onClick = { Store.delete(initial.id); askDelete = false; onDone(null) }) { Text(t("Видалити", "Delete")) } },
        dismissButton = { TextButton(onClick = { askDelete = false }) { Text(t("Скасувати", "Cancel")) } },
    )
}

@Composable
private fun Section(text: String) {
    Text(
        text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun Choice(text: String, on: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = on, onClick = onClick)
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun GroupPicker(regionId: String, selected: String?, onPick: (String) -> Unit) {
    val ctx = LocalContext.current
    val data = Repo.regions[regionId]
    LaunchedEffect(regionId) { if (Repo.regions[regionId] == null) Repo.refresh(ctx, listOf(regionId)) }
    var query by remember(regionId) { mutableStateOf("") }
    if (data == null) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
            if (Repo.loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 3.dp)
            Spacer(Modifier.width(12.dp))
            Text(
                if (Repo.loading) t("Завантажую черги…", "Loading groups…") else t("Не вдалося завантажити. Перевірте інтернет.", "Couldn't load. Check the internet."),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }
    val many = data.groups.size > 24
    if (many) OutlinedTextField(
        value = query, onValueChange = { query = it.trim() },
        leadingIcon = { Icon(Icons.Rounded.Search, null) }, placeholder = { Text(t("Номер черги, напр. 3.1", "Group number, e.g. 3.1")) },
        singleLine = true, modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
    )
    val shown = data.groups.filter { query.isEmpty() || it.short.contains(query) || it.name.contains(query, ignoreCase = true) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        for (g in shown) FilterChip(selected = g.id == selected, onClick = { onPick(g.id) }, label = { Text(g.short) })
    }
    Spacer(Modifier.height(8.dp))
    val site = Regions.byId(regionId)?.site
    Text(
        t("Свою чергу можна дізнатися за адресою на сайті обленерго або в рахунку за світло.",
            "You can find your group by address on the utility's website or on your power bill."),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (site != null) TextButton(onClick = { open(ctx, site) }) { Text(t("Дізнатися свою чергу", "Find my group")) }
}

/** Свій графік: день тижня, а в ньому 24 години; натискання перемикає годину. */
@Composable
private fun ManualWeek(rows: MutableList<String>) {
    var day by remember { mutableStateOf(0) }
    val names = if (L.ukr) listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Нд") else listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        names.forEachIndexed { i, n ->
            val on = i == day
            Surface(
                onClick = { day = i },
                shape = RoundedCornerShape(12.dp),
                color = if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.weight(1f).height(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        n, style = MaterialTheme.typography.labelLarge, maxLines = 1,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (on) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    Text(
        t("Натисніть години, коли світла немає.", "Tap the hours when the power is off."),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    val row = rows[day]
    for (r in 0 until 4) {
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (c in 0 until 6) {
                val h = r * 6 + c
                val off = row.getOrNull(h * 2) == '1'
                Surface(
                    onClick = {
                        val ch = if (off) '0' else '1'
                        val sb = StringBuilder(rows[day])
                        sb.setCharAt(h * 2, ch)
                        sb.setCharAt(h * 2 + 1, ch)
                        rows[day] = sb.toString()
                    },
                    color = if (off) StateColors.bar(ua.x44to.svitlo.data.Slot.OFF) else YELLOW,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.weight(1f).aspectRatio(1.25f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("%02d".format(h), fontWeight = FontWeight.SemiBold, color = if (off) androidx.compose.ui.graphics.Color.White else INK)
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = { val v = rows[day]; for (i in 0 until 7) rows[i] = v }) {
        Text(t("Так само на всі дні", "Same for every day"))
    }
}
