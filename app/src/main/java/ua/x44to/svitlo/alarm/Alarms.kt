package ua.x44to.svitlo.alarm

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ua.x44to.svitlo.MainActivity
import ua.x44to.svitlo.R
import ua.x44to.svitlo.data.DayPlan
import ua.x44to.svitlo.data.Event
import ua.x44to.svitlo.data.EventType
import ua.x44to.svitlo.data.KYIV
import ua.x44to.svitlo.data.Place
import ua.x44to.svitlo.data.Plans
import ua.x44to.svitlo.data.Repo
import ua.x44to.svitlo.data.Store
import ua.x44to.svitlo.data.Timeline
import ua.x44to.svitlo.data.t
import ua.x44to.svitlo.widget.LightWidget
import java.time.ZonedDateTime

private fun hm(z: ZonedDateTime): String = ua.x44to.svitlo.data.Words.hm(z)

/** Сповіщення: нагадування перед зміною і «графік змінився». */
object Notify {
    const val SOON = "soon"
    const val CHANGES = "changes"

    fun channels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(SOON, t("Нагадування про світло", "Power reminders"), NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(CHANGES, t("Зміни графіка", "Schedule changes"), NotificationManager.IMPORTANCE_DEFAULT))
    }

    fun allowed(ctx: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            ctx.getSystemService(NotificationManager::class.java).areNotificationsEnabled()

    /** Текст нагадування: заголовок і рядок. */
    fun texts(place: Place, e: Event, lead: Int): Pair<String, String> {
        val inMin = t("за $lead хв", "in $lead min")
        return when (e.type) {
            EventType.OFF -> t("Світло вимкнуть о ${hm(e.at)}", "Power goes off at ${hm(e.at)}") to
                (place.name + ": " + inMin + (e.till?.let { t(". Без світла до ${hm(it)}", ". Off until ${hm(it)}") } ?: ""))
            EventType.ON -> t("Світло повернеться о ${hm(e.at)}", "Power is back at ${hm(e.at)}") to
                (place.name + ": " + inMin + t(" за графіком", " by the schedule"))
            EventType.MAYBE -> t("Можливе відключення о ${hm(e.at)}", "Possible outage at ${hm(e.at)}") to
                (place.name + ": " + inMin + (e.till?.let { t(", до ${hm(it)}", ", until ${hm(it)}") } ?: ""))
        }
    }

    fun event(ctx: Context, place: Place, e: Event, lead: Int) {
        if (!allowed(ctx)) return
        channels(ctx)
        val (title, text) = texts(place, e, lead)
        val n = NotificationCompat.Builder(ctx, SOON)
            .setSmallIcon(if (e.type == EventType.ON) R.drawable.ic_stat_on else R.drawable.ic_stat_off)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open(ctx, place.id))
            .setAutoCancel(true)
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(e.key.hashCode(), n)
    }

    fun changed(ctx: Context, place: Place, text: String) {
        if (!allowed(ctx)) return
        channels(ctx)
        val n = NotificationCompat.Builder(ctx, CHANGES)
            .setSmallIcon(R.drawable.ic_stat_off)
            .setContentTitle(t("Графік змінився: ${place.name}", "Schedule changed: ${place.name}"))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open(ctx, place.id))
            .setAutoCancel(true)
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(("ch" + place.id).hashCode(), n)
    }

    private fun open(ctx: Context, placeId: String): PendingIntent = PendingIntent.getActivity(
        ctx, placeId.hashCode(),
        Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_PLACE, placeId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** «Сьогодні без світла: 13:00–16:30, 20:00–22:00» */
    fun describe(day: DayPlan, label: String): String {
        val off = Timeline.outages(day).filter { it.slot == ua.x44to.svitlo.data.Slot.OFF }
        return if (off.isEmpty()) t("$label відключень немає", "$label: no outages")
        else t("$label без світла: ", "$label off: ") + off.joinToString(", ") { Plans.time(it.from) + "–" + Plans.time(it.to) }
    }
}

/** Будильник на найближчу подію: нагадування або зміну стану (щоб віджет показував правду). */
object Alarms {
    private fun pending(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx, 1, Intent(ctx, AlarmReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun exactAllowed(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    private fun events(now: ZonedDateTime, from: ZonedDateTime): List<Pair<Place, Event>> {
        val today = now.withZoneSameInstant(KYIV).toLocalDate()
        return Store.places.flatMap { pl ->
            val days = Timeline.days(pl, pl.region?.let { Repo.regions[it] }, today)
            Timeline.events(pl, days, from, Store.lead.toLong(), Store.notifyOn, Store.notifyMaybe).map { pl to it }
        }
    }

    /** Найближчий момент, коли щось зміниться в будь-якій адресі. */
    private fun nextChange(now: ZonedDateTime): ZonedDateTime? {
        val today = now.withZoneSameInstant(KYIV).toLocalDate()
        return Store.places.mapNotNull { pl ->
            Timeline.status(Timeline.days(pl, pl.region?.let { Repo.regions[it] }, today), now).until
        }.minOrNull()
    }

    fun reschedule(ctx: Context, now: ZonedDateTime = ZonedDateTime.now(KYIV)) {
        val fired = Store.fired
        val lead = Store.lead.toLong()
        val nextEvent = events(now, now).filter { it.second.key !in fired }.minOfOrNull { it.second.at.minusMinutes(lead) }
        val next = listOfNotNull(nextEvent, nextChange(now)?.plusSeconds(5)).minOrNull()
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = pending(ctx)
        if (next == null) {
            am.cancel(pi)
            return
        }
        val at = next.toInstant().toEpochMilli()
        if (exactAllowed(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    /** Будильник спрацював: показати нагадування, яким настав час, і поставити наступний. */
    fun fire(ctx: Context, now: ZonedDateTime = ZonedDateTime.now(KYIV)) {
        val fired = Store.fired.toMutableSet()
        val lead = Store.lead.toLong()
        for ((pl, e) in events(now, now.minusMinutes(10))) {
            val trigger = e.at.minusMinutes(lead)
            if (trigger.isAfter(now.plusSeconds(30)) || e.key in fired) continue
            Notify.event(ctx, pl, e, Store.lead)
            fired += e.key
        }
        // старі позначки (понад два дні) більше не потрібні
        val cutoff = now.minusDays(2).toEpochSecond()
        Store.fired = fired.filter { k -> k.split('@').getOrNull(1)?.toLongOrNull()?.let { it > cutoff } ?: false }.toSet()
        reschedule(ctx, now)
    }
}

private val bg = CoroutineScope(SupervisorJob() + Dispatchers.Default)

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ctx = context.applicationContext
        val done = goAsync()
        bg.launch {
            try {
                Store.init(ctx)
                Repo.loadCached(ctx, Store.places.mapNotNull { it.region })
                Alarms.fire(ctx)
                LightWidget.update(ctx)
            } finally {
                done.finish()
            }
        }
    }
}

/** Після перезавантаження, оновлення програми чи зміни часу — поставити все заново. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ctx = context.applicationContext
        val done = goAsync()
        bg.launch {
            try {
                Store.init(ctx)
                Repo.loadCached(ctx, Store.places.mapNotNull { it.region })
                Alarms.reschedule(ctx)
                RefreshWorker.schedule(ctx)
                LightWidget.update(ctx)
            } finally {
                done.finish()
            }
        }
    }
}
