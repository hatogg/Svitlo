package ua.x44to.svitlo.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import ua.x44to.svitlo.MainActivity
import ua.x44to.svitlo.R
import ua.x44to.svitlo.data.DayPlan
import ua.x44to.svitlo.data.KYIV
import ua.x44to.svitlo.data.Repo
import ua.x44to.svitlo.data.SLOTS
import ua.x44to.svitlo.data.Slot
import ua.x44to.svitlo.data.Store
import ua.x44to.svitlo.data.Timeline
import ua.x44to.svitlo.data.Words
import ua.x44to.svitlo.data.t
import java.time.ZonedDateTime

/** Віджет: «Світло є · до 13:00» або «Світла немає · буде о 18:00» і шкала дня. */
class LightWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Store.init(context.applicationContext)
        Repo.loadCached(context, Store.places.mapNotNull { it.region })
        provideContent { Body(context) }
    }

    companion object {
        suspend fun update(ctx: Context) {
            runCatching { LightWidget().updateAll(ctx) }
        }
    }
}

class LightWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LightWidget()
}

private val YELLOW = Color(0xFFFFC94A)
private val INK = Color(0xFF1B1C22)
private val NIGHT = Color(0xFF24262F)
private val MUTED_ON_NIGHT = Color(0xFFB9BCCB)

/** Шкала дня картинкою: так вона однакова на всіх лаунчерах. */
fun dayBar(day: DayPlan, nowIndex: Int?, w: Int = 480, h: Int = 36, dark: Boolean): Bitmap {
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val gap = 2f
    val cell = (w - gap * (SLOTS - 1)) / SLOTS
    for (i in 0 until SLOTS) {
        p.color = when (day.slots[i]) {
            Slot.ON -> if (dark) 0xFFFFC94A.toInt() else 0xFF2B2C33.toInt()
            Slot.OFF -> if (dark) 0xFF4A4D5C.toInt() else 0x33000000
            Slot.MAYBE -> 0xFFE58A3A.toInt()
            Slot.UNKNOWN -> 0x22888888
        }
        if (nowIndex != null && i < nowIndex) p.alpha = (p.alpha * 0.45f).toInt()
        val x = i * (cell + gap)
        c.drawRoundRect(RectF(x, 4f, x + cell, h - 4f), 3f, 3f, p)
    }
    if (nowIndex != null && nowIndex in 0 until SLOTS) {
        p.color = if (dark) 0xFFFFFFFF.toInt() else 0xFF1B1C22.toInt()
        p.alpha = 255
        val x = nowIndex * (cell + gap) + cell / 2
        c.drawRoundRect(RectF(x - 2f, 0f, x + 2f, h.toFloat()), 2f, 2f, p)
    }
    return bmp
}

@Composable
private fun Body(context: Context) {
    val place = Store.place
    val open = actionStartActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    if (place == null) {
        Column(
            GlanceModifier.fillMaxSize().background(YELLOW).cornerRadius(24.dp).padding(16.dp).clickable(open),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(t("Світло", "Svitlo"), style = TextStyle(color = ColorProvider(INK), fontSize = 18.sp, fontWeight = FontWeight.Bold))
            Text(t("Додайте адресу в програмі", "Add your address in the app"), style = TextStyle(color = ColorProvider(INK), fontSize = 13.sp))
        }
        return
    }
    val now = ZonedDateTime.now(KYIV)
    val days = Timeline.days(place, place.region?.let { Repo.regions[it] }, now.toLocalDate())
    val st = Timeline.status(days, now)
    val off = st.now == Slot.OFF
    val bg = if (off) NIGHT else YELLOW
    val fg = if (off) Color.White else INK
    val sub = if (off) MUTED_ON_NIGHT else INK.copy(alpha = 0.7f)
    val title = Words.title(st)
    val line = Words.line(st, now)
    Column(GlanceModifier.fillMaxSize().background(bg).cornerRadius(24.dp).padding(horizontal = 16.dp, vertical = 12.dp).clickable(open)) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Image(
                ImageProvider(if (off) R.drawable.ic_stat_off else R.drawable.ic_stat_on), null,
                modifier = GlanceModifier.size(20.dp), colorFilter = ColorFilter.tint(ColorProvider(fg)),
            )
            Spacer(GlanceModifier.width(8.dp))
            Text(place.name, style = TextStyle(color = ColorProvider(sub), fontSize = 13.sp), maxLines = 1)
        }
        Spacer(GlanceModifier.height(4.dp))
        Text(title, style = TextStyle(color = ColorProvider(fg), fontSize = 22.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        if (line.isNotEmpty()) Text(line, style = TextStyle(color = ColorProvider(sub), fontSize = 14.sp, fontWeight = FontWeight.Medium), maxLines = 1)
        Spacer(GlanceModifier.defaultWeight())
        val idx = Timeline.index(now, days[0].date)
        Image(ImageProvider(dayBar(days[0], idx, dark = off)), t("Шкала дня", "Day bar"), modifier = GlanceModifier.fillMaxWidth().height(14.dp))
    }
}
