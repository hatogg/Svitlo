@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package ua.x44to.svitlo.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ua.x44to.svitlo.BuildConfig
import ua.x44to.svitlo.alarm.Alarms
import ua.x44to.svitlo.alarm.Notify
import ua.x44to.svitlo.data.Store
import ua.x44to.svitlo.data.t

@Composable
fun SettingsScreen(pad: PaddingValues, tick: Int, onAskNotifications: () -> Unit) {
    val ctx = LocalContext.current
    val notifOk = remember(tick) { Notify.allowed(ctx) }
    val exactOk = remember(tick) { Alarms.exactAllowed(ctx) }
    fun changed() = Alarms.reschedule(ctx)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 24.dp),
    ) {
        Title(t("Нагадувати за", "Remind me"))
        val leads = listOf(5, 15, 30, 60)
        Choice(leads.map { if (it == 60) t("1 год", "1 h") else t("$it хв", "$it min") }, leads.indexOf(Store.lead).coerceAtLeast(0)) {
            Store.chooseLead(leads[it]); changed()
        }

        Title(t("Сповіщення", "Notifications"))
        Card {
            if (!notifOk) {
                Text(t("Сповіщення вимкнені — нагадувань не буде.", "Notifications are off — no reminders."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onAskNotifications) { Text(t("Увімкнути сповіщення", "Turn on notifications")) }
            }
            Toggle(t("Перед відключенням", "Before an outage"), t("Завжди, якщо є точний графік", "Always, when there's an exact schedule"), true, enabled = false) {}
            Toggle(t("Коли світло повернеться", "When the power comes back"), null, Store.notifyOn) { Store.chooseNotifyOn(it); changed() }
            Toggle(
                t("Про можливі відключення", "About possible outages"),
                t("З тижневого графіка, коли точного немає", "From the weekly plan when there's no exact one"), Store.notifyMaybe,
            ) { Store.chooseNotifyMaybe(it); changed() }
            Toggle(t("Коли графік змінився", "When the schedule changes"), t("Перевіряю щогодини", "Checked every hour"), Store.notifyChanges) { Store.chooseNotifyChanges(it) }
            if (Build.VERSION.SDK_INT >= 31 && !exactOk) {
                Spacer(Modifier.height(8.dp))
                Text(
                    t("Android може пересунути нагадування на кілька хвилин. Щоб приходили хвилина в хвилину, дозвольте точні нагадування.",
                        "Android may shift reminders by a few minutes. Allow exact alarms to get them on the minute."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = {
                    Links.start(ctx, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + BuildConfig.APPLICATION_ID)))
                }, modifier = Modifier.padding(top = 6.dp)) { Text(t("Дозволити точні нагадування", "Allow exact alarms")) }
            }
        }

        Title(t("Тема", "Theme"))
        Choice(listOf(t("Як у системі", "System"), t("Світла", "Light"), t("Темна", "Dark")), Store.theme) { Store.chooseTheme(it) }

        Title(t("Мова", "Language"))
        val langIdx = when (Store.lang) { "uk" -> 1; "en" -> 2; else -> 0 }
        Choice(listOf(t("Як у системі", "System"), "Українська", "English"), langIdx) { Store.chooseLang(when (it) { 1 -> "uk"; 2 -> "en"; else -> null }) }

        Title(t("Про «Світло»", "About Svitlo"))
        Card {
            Text(t("Версія ${BuildConfig.VERSION_NAME}", "Version ${BuildConfig.VERSION_NAME}"), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                t("Графіки беруться з відкритого проєкту outage-data-ua, який збирає їх зі сторінок ДТЕК. Графік може змінитися будь-коли — орієнтуйтеся й на повідомлення свого обленерго.",
                    "Schedules come from the open outage-data-ua project, which collects them from DTEK pages. A schedule can change at any time — also follow your utility's announcements."),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                t("Без реклами й стеження. Інтернет потрібен лише, щоб завантажити графік.", "No ads or tracking. The internet is only used to download the schedule."),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row {
                TextButton(onClick = { Links.open(ctx, "https://github.com/Baskerville42/outage-data-ua") }) { Text("outage-data-ua") }
                TextButton(onClick = { Links.open(ctx, "https://github.com/hatogg/Svitlo") }) { Text(t("Код", "Source")) }
            }
        }
    }
}

@Composable
private fun Title(text: String) {
    Text(
        text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun Toggle(title: String, sub: String?, on: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = on, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun Choice(options: List<String>, selected: Int, onPick: (Int) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        options.forEachIndexed { i, o ->
            SegmentedButton(selected = i == selected, onClick = { onPick(i) }, shape = SegmentedButtonDefaults.itemShape(i, options.size)) { Text(o, maxLines = 1) }
        }
    }
}
