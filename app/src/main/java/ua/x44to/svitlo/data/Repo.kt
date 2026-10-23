package ua.x44to.svitlo.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Регіон, для якого графік завантажується сам. */
data class Region(val id: String, val uk: String, val en: String, val site: String) {
    val title get() = t(uk, en)
}

object Regions {
    val ALL = listOf(
        Region("kyiv", "Київ", "Kyiv", "https://www.dtek-kem.com.ua/ua/shutdowns"),
        Region("kyiv-region", "Київська область", "Kyiv region", "https://www.dtek-krem.com.ua/ua/shutdowns"),
        Region("dnipro", "Дніпро й область", "Dnipro and region", "https://www.dtek-dnem.com.ua/ua/shutdowns"),
        Region("odesa", "Одеса й область", "Odesa and region", "https://www.dtek-oem.com.ua/ua/shutdowns"),
    )

    fun byId(id: String?) = ALL.firstOrNull { it.id == id }
}

/**
 * Графіки з відкритого проєкту outage-data-ua (github.com/Baskerville42/outage-data-ua, MIT): він кожні кілька
 * хвилин збирає їх зі сторінок ДТЕК. Останній завантажений графік зберігається, тож без інтернету програма
 * показує його.
 */
object Repo {
    private const val BASE = "https://raw.githubusercontent.com/Baskerville42/outage-data-ua/main/data/"

    /** Для перевірки в емуляторі: не ходити в інтернет, брати лише збережене. */
    @Volatile var offline = false

    val regions = mutableStateMapOf<String, RegionData>()
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private fun dir(ctx: Context) = File(ctx.filesDir, "regions").apply { mkdirs() }

    fun file(ctx: Context, id: String) = File(dir(ctx), "$id.json")

    /** Коли востаннє вдалося завантажити (мс). */
    fun fetchedAt(ctx: Context, id: String): Long = file(ctx, id).takeIf { it.exists() }?.lastModified() ?: 0L

    /** Прочитати збережене (без інтернету). */
    fun loadCached(ctx: Context, ids: Collection<String>) {
        for (id in ids) {
            if (id in regions) continue
            val f = file(ctx, id)
            if (f.exists()) runCatching { RegionData.parse(f.readText()) }.getOrNull()?.let { regions[id] = it }
        }
    }

    /** Завантажити свіжі графіки. @return які регіони змінилися (порівняно з попереднім збереженим). */
    suspend fun refresh(ctx: Context, ids: Collection<String>): Set<String> = withContext(Dispatchers.IO) {
        loadCached(ctx, ids)
        if (offline || ids.isEmpty()) return@withContext emptySet()
        loading = true
        val changed = HashSet<String>()
        var fail: String? = null
        for (id in ids.distinct()) {
            try {
                val text = download(BASE + id + ".json")
                val data = RegionData.parse(text)
                val old = regions[id]
                file(ctx, id).writeText(text)
                regions[id] = data
                if (old == null || old.fact != data.fact || old.preset != data.preset) changed += id
            } catch (e: Exception) {
                fail = e.message ?: e.javaClass.simpleName
            }
        }
        error = fail
        loading = false
        changed
    }

    private fun download(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        c.setRequestProperty("User-Agent", "Svitlo-Android")
        try {
            if (c.responseCode != 200) throw IllegalStateException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }
}
