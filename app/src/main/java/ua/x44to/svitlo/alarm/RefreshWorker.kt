package ua.x44to.svitlo.alarm

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ua.x44to.svitlo.data.KYIV
import ua.x44to.svitlo.data.Place
import ua.x44to.svitlo.data.Repo
import ua.x44to.svitlo.data.Store
import ua.x44to.svitlo.data.Timeline
import ua.x44to.svitlo.data.t
import ua.x44to.svitlo.widget.LightWidget
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** Щогодини: завантажити свіжі графіки, сповістити, якщо графік адреси змінився, переставити нагадування. */
class RefreshWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        Store.init(ctx)
        val ids = Store.places.mapNotNull { it.region }.distinct()
        Repo.refresh(ctx, ids)
        Sync.afterRefresh(ctx)
        return Result.success()
    }

    companion object {
        private const val WORK = "refresh"

        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<RefreshWorker>(1, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}

/** Що робити після оновлення графіків — і з фону, і з програми. */
object Sync {
    /** Підпис точного графіка адреси на сьогодні й завтра: якщо змінився — графік змінили. */
    fun signature(place: Place, today: LocalDate): String {
        val days = Timeline.days(place, place.region?.let { Repo.regions[it] }, today)
        return days.filter { it.sure }.joinToString("|") { d -> d.date.toString() + ":" + d.slots.joinToString("") { it.ordinal.toString() } }
    }

    suspend fun afterRefresh(ctx: Context) {
        val today = LocalDate.now(KYIV)
        val old = Store.signatures
        val now = HashMap<String, String>()
        for (pl in Store.places) {
            if (pl.isManual) continue
            val sig = signature(pl, today)
            now[pl.id] = sig
            val was = old[pl.id] ?: continue
            if (sig.isEmpty() || sig == was || !Store.notifyChanges) continue
            // що саме змінилося: день, якого раніше не було або в якого інші половини годин
            val wasDays = was.split('|').associate { it.substringBefore(':') to it.substringAfter(':') }
            val days = Timeline.days(pl, Repo.regions[pl.region], today).filter { it.sure }
            val changed = days.filter { d -> wasDays[d.date.toString()] != d.slots.joinToString("") { it.ordinal.toString() } }
            if (changed.isEmpty()) continue
            val text = changed.joinToString("\n") { d -> Notify.describe(d, if (d.date == today) t("Сьогодні", "Today") else t("Завтра", "Tomorrow")) }
            Notify.changed(ctx, pl, text)
        }
        Store.signatures = now
        Alarms.reschedule(ctx)
        LightWidget.update(ctx)
    }
}
