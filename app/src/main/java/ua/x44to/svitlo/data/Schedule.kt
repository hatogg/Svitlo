package ua.x44to.svitlo.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Усі графіки — за київським часом, як їх публікують обленерго. */
val KYIV: ZoneId = ZoneId.of("Europe/Kyiv")

/** Стан на пів години. */
enum class Slot { ON, OFF, MAYBE, UNKNOWN }

const val SLOTS = 48

object Codes {
    /** Код години з графіка ДТЕК → дві половини години. */
    fun halves(v: String?): Pair<Slot, Slot> = when (v) {
        "yes" -> Slot.ON to Slot.ON
        "no" -> Slot.OFF to Slot.OFF
        "first" -> Slot.OFF to Slot.ON
        "second" -> Slot.ON to Slot.OFF
        "maybe" -> Slot.MAYBE to Slot.MAYBE
        "mfirst" -> Slot.MAYBE to Slot.ON
        "msecond" -> Slot.ON to Slot.MAYBE
        else -> Slot.UNKNOWN to Slot.UNKNOWN
    }

    /** {"1": "yes", …, "24": "no"} → 48 половин. */
    fun day(hours: JsonObject): List<Slot> {
        val out = ArrayList<Slot>(SLOTS)
        for (h in 1..24) {
            val (a, b) = halves((hours[h.toString()] as? JsonPrimitive)?.contentOrNull)
            out += a
            out += b
        }
        return out
    }
}

data class Group(val id: String, val name: String) {
    /** «Черга 1.1» → «1.1», «ЦЕК 1.1» лишається як є — для компактних кнопок. */
    val short: String get() = name.removePrefix("Черга").trim().ifBlank { id.removePrefix("GPV") }
}

/** Графік регіону: фактичний на сьогодні/завтра і тижневий графік можливих відключень. */
class RegionData(
    val id: String,
    val title: String?,
    val groups: List<Group>,
    val fact: Map<LocalDate, Map<String, List<Slot>>>,
    val factUpdate: String?,
    val preset: Map<String, Map<Int, List<Slot>>>,
    val presetUpdate: String?,
    val checkedAt: Instant?,
) {
    fun groupName(g: String?) = groups.firstOrNull { it.id == g }?.name ?: g?.removePrefix("GPV")?.let { "Черга $it" } ?: ""

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String, zone: ZoneId = KYIV): RegionData {
            val root = json.parseToJsonElement(text).jsonObject
            val id = root["regionId"]?.jsonPrimitive?.contentOrNull ?: ""
            val title = root["regionAffiliation"]?.jsonPrimitive?.contentOrNull

            val fact = HashMap<LocalDate, Map<String, List<Slot>>>()
            var factUpdate: String? = null
            (root["fact"] as? JsonObject)?.let { f ->
                factUpdate = (f["update"] as? JsonPrimitive)?.contentOrNull
                (f["data"] as? JsonObject)?.forEach { (ts, groups) ->
                    val date = ts.toLongOrNull()?.let { Instant.ofEpochSecond(it).atZone(zone).toLocalDate() } ?: return@forEach
                    val day = HashMap<String, List<Slot>>()
                    (groups as? JsonObject)?.forEach { (g, hours) -> (hours as? JsonObject)?.let { day[g] = Codes.day(it) } }
                    fact[date] = day
                }
            }

            val preset = HashMap<String, Map<Int, List<Slot>>>()
            var presetUpdate: String? = null
            val names = LinkedHashMap<String, String>()
            (root["preset"] as? JsonObject)?.let { p ->
                presetUpdate = (p["updateFact"] as? JsonPrimitive)?.contentOrNull ?: (p["update"] as? JsonPrimitive)?.contentOrNull
                (p["sch_names"] as? JsonObject)?.forEach { (g, n) -> (n as? JsonPrimitive)?.contentOrNull?.let { names[g] = it } }
                (p["data"] as? JsonObject)?.forEach { (g, days) ->
                    val week = HashMap<Int, List<Slot>>()
                    (days as? JsonObject)?.forEach { (d, hours) ->
                        val dow = d.toIntOrNull() ?: return@forEach
                        (hours as? JsonObject)?.let { week[dow] = Codes.day(it) }
                    }
                    preset[g] = week
                }
            }
            val ids = (names.keys + preset.keys + fact.values.flatMap { it.keys }).toSortedSet(GROUP_ORDER)
            val groups = ids.map { Group(it, names[it] ?: "Черга " + it.removePrefix("GPV")) }

            val checked = ((root["lastUpdateStatus"] as? JsonObject)?.get("at") as? JsonPrimitive)?.contentOrNull
                ?: (root["lastUpdated"] as? JsonPrimitive)?.contentOrNull
            return RegionData(
                id, title, groups, fact, factUpdate, preset, presetUpdate,
                checked?.let { runCatching { Instant.parse(it) }.getOrNull() },
            )
        }

        /** GPV2.1 < GPV10.1 < GPV1001.1 — за числами, а не як текст. */
        val GROUP_ORDER: Comparator<String> = Comparator { a, b ->
            val x = nums(a)
            val y = nums(b)
            for (i in 0 until minOf(x.size, y.size)) if (x[i] != y[i]) return@Comparator x[i].compareTo(y[i])
            if (x.size != y.size) x.size.compareTo(y.size) else a.compareTo(b)
        }

        private fun nums(s: String) = Regex("\\d+").findAll(s).map { it.value.toLongOrNull() ?: 0L }.toList()
    }
}

/** Як отримано графік дня. */
enum class Kind {
    /** Графік відключень на цей день від обленерго — точно. */
    FACT,
    /** Свій графік, введений вручну. */
    MANUAL,
    /** Графіка на день немає: показуємо можливі відключення з тижневого графіка. */
    POSSIBLE,
    /** Даних немає (ще не завантажено). */
    NONE,
}

data class DayPlan(val date: LocalDate, val slots: List<Slot>, val kind: Kind) {
    val sure get() = kind == Kind.FACT || kind == Kind.MANUAL
}

/** Проміжок однакового стану: з якої половини години і до якої (не включно). */
data class Span(val from: Int, val to: Int, val slot: Slot)

object Plans {
    fun day(place: Place, region: RegionData?, date: LocalDate): DayPlan {
        if (place.isManual) {
            val dow = date.dayOfWeek.value
            val row = place.manual?.getOrNull(dow - 1) ?: ""
            return DayPlan(date, List(SLOTS) { i -> if (row.getOrNull(i) == '1') Slot.OFF else Slot.ON }, Kind.MANUAL)
        }
        if (region == null) return DayPlan(date, List(SLOTS) { Slot.UNKNOWN }, Kind.NONE)
        val g = place.group ?: return DayPlan(date, List(SLOTS) { Slot.UNKNOWN }, Kind.NONE)
        region.fact[date]?.get(g)?.let { return DayPlan(date, it, Kind.FACT) }
        val week = region.preset[g]?.get(date.dayOfWeek.value)
        return if (week != null) DayPlan(date, week, Kind.POSSIBLE) else DayPlan(date, List(SLOTS) { Slot.UNKNOWN }, Kind.NONE)
    }

    /** Злиті проміжки однакового стану. */
    fun spans(slots: List<Slot>): List<Span> {
        val out = ArrayList<Span>()
        var start = 0
        for (i in 1..slots.size) {
            if (i == slots.size || slots[i] != slots[start]) {
                out += Span(start, i, slots[start])
                start = i
            }
        }
        return out
    }

    /** «13:00», «24:00» для кінця дня. */
    fun time(slot: Int): String = String.format(java.util.Locale.ROOT, "%02d:%02d", slot / 2, (slot % 2) * 30)
}
