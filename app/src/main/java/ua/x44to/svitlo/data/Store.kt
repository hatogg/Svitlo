package ua.x44to.svitlo.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Адреси й налаштування. Усе лише на телефоні. */
object Store {
    private lateinit var p: SharedPreferences
    private val json = Json { ignoreUnknownKeys = true }
    private val placesSer = ListSerializer(Place.serializer())
    private val setSer = SetSerializer(String.serializer())
    private val mapSer = MapSerializer(String.serializer(), String.serializer())

    var places by mutableStateOf<List<Place>>(emptyList())
        private set
    var current by mutableStateOf<String?>(null)
        private set

    /** За скільки хвилин попереджати. */
    var lead by mutableIntStateOf(15)
        private set
    var notifyOn by mutableStateOf(true)
        private set
    var notifyMaybe by mutableStateOf(false)
        private set
    var notifyChanges by mutableStateOf(true)
        private set
    var theme by mutableIntStateOf(0)
        private set
    var lang by mutableStateOf<String?>(null)
        private set

    val ready get() = ::p.isInitialized

    fun init(ctx: Context) {
        if (ready) return
        p = ctx.applicationContext.getSharedPreferences("svitlo", Context.MODE_PRIVATE)
        places = p.getString("places", null)?.let { runCatching { json.decodeFromString(placesSer, it) }.getOrNull() } ?: emptyList()
        current = p.getString("current", null)?.takeIf { id -> places.any { it.id == id } } ?: places.firstOrNull()?.id
        lead = p.getInt("lead", 15)
        notifyOn = p.getBoolean("notifyOn", true)
        notifyMaybe = p.getBoolean("notifyMaybe", false)
        notifyChanges = p.getBoolean("notifyChanges", true)
        theme = p.getInt("theme", 0)
        lang = p.getString("lang", null)
        L.override = lang
    }

    val place: Place? get() = places.firstOrNull { it.id == current } ?: places.firstOrNull()

    fun select(id: String) {
        current = id
        p.edit().putString("current", id).apply()
    }

    fun save(pl: Place) {
        places = if (places.any { it.id == pl.id }) places.map { if (it.id == pl.id) pl else it } else places + pl
        writePlaces()
        select(pl.id)
    }

    fun delete(id: String) {
        places = places.filter { it.id != id }
        writePlaces()
        if (current == id) places.firstOrNull()?.let { select(it.id) } ?: run { current = null }
    }

    private fun writePlaces() = p.edit().putString("places", json.encodeToString(placesSer, places)).apply()

    fun chooseLead(v: Int) { lead = v; p.edit().putInt("lead", v).apply() }
    fun chooseNotifyOn(v: Boolean) { notifyOn = v; p.edit().putBoolean("notifyOn", v).apply() }
    fun chooseNotifyMaybe(v: Boolean) { notifyMaybe = v; p.edit().putBoolean("notifyMaybe", v).apply() }
    fun chooseNotifyChanges(v: Boolean) { notifyChanges = v; p.edit().putBoolean("notifyChanges", v).apply() }
    fun chooseTheme(v: Int) { theme = v; p.edit().putInt("theme", v).apply() }
    fun chooseLang(v: String?) { lang = v; L.override = v; p.edit().putString("lang", v).apply() }

    /** Нагадування, які вже показали (щоб не повторювати після перепланування). */
    var fired: Set<String>
        get() = p.getString("fired", null)?.let { runCatching { json.decodeFromString(setSer, it) }.getOrNull() } ?: emptySet()
        set(v) = p.edit().putString("fired", json.encodeToString(setSer, v)).apply()

    /** Останній відомий точний графік кожної адреси — щоб помітити, що його змінили. */
    var signatures: Map<String, String>
        get() = p.getString("sigs", null)?.let { runCatching { json.decodeFromString(mapSer, it) }.getOrNull() } ?: emptyMap()
        set(v) = p.edit().putString("sigs", json.encodeToString(mapSer, v)).apply()

    fun newId() = "p" + System.currentTimeMillis().toString(36)
}
