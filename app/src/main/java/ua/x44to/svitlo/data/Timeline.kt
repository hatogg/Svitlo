package ua.x44to.svitlo.data

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/** Адреса: регіон і черга, або свій графік (manual: 7 рядків по 48 символів, '1' — світла немає). */
@Serializable
data class Place(
    val id: String,
    val name: String,
    val region: String? = null,
    val group: String? = null,
    val manual: List<String>? = null,
) {
    val isManual: Boolean get() = region == null
}

/** Що зараз і коли зміниться. */
data class Status(
    val now: Slot,
    val sure: Boolean,
    /** коли закінчиться поточний стан; null — не в межах відомих двох днів */
    val until: ZonedDateTime?,
    val next: Slot?,
)

enum class EventType { OFF, ON, MAYBE }

/** Зміна стану, про яку варто попередити. */
data class Event(val placeId: String, val type: EventType, val at: ZonedDateTime, val till: ZonedDateTime? = null) {
    val key get() = "$placeId@${at.toEpochSecond()}@$type"
}

object Timeline {
    /** Сьогодні й завтра. */
    fun days(place: Place, region: RegionData?, today: LocalDate): List<DayPlan> =
        listOf(Plans.day(place, region, today), Plans.day(place, region, today.plusDays(1)))

    private fun slotAt(days: List<DayPlan>, i: Int): Slot = days[i / SLOTS].slots[i % SLOTS]

    private fun sureAt(days: List<DayPlan>, i: Int) = days[i / SLOTS].sure

    /** Час початку половини години [i] (рахуючи від півночі першого дня). Через LocalTime — щоб не збиватися в дні переведення годинника. */
    fun timeOf(days: List<DayPlan>, i: Int): ZonedDateTime {
        val d = days[0].date.plusDays((i / SLOTS).toLong())
        val s = i % SLOTS
        return ZonedDateTime.of(d, LocalTime.of(s / 2, (s % 2) * 30), KYIV)
    }

    fun index(now: ZonedDateTime, today: LocalDate): Int {
        val k = now.withZoneSameInstant(KYIV)
        val dayShift = (k.toLocalDate().toEpochDay() - today.toEpochDay()).toInt()
        return dayShift * SLOTS + (k.hour * 60 + k.minute) / 30
    }

    fun status(days: List<DayPlan>, now: ZonedDateTime): Status {
        val total = days.size * SLOTS
        val i = index(now, days[0].date).coerceIn(0, total - 1)
        val cur = slotAt(days, i)
        for (j in i + 1 until total) {
            val s = slotAt(days, j)
            if (s != cur) return Status(cur, sureAt(days, i), timeOf(days, j), s)
        }
        return Status(cur, sureAt(days, i), null, null)
    }

    /** Майбутні зміни, про які треба попередити (нагадування ставимо за [lead] хвилин до них). */
    fun events(place: Place, days: List<DayPlan>, now: ZonedDateTime, lead: Long, withOn: Boolean, withMaybe: Boolean): List<Event> {
        val out = ArrayList<Event>()
        val total = days.size * SLOTS
        for (j in 1 until total) {
            val a = slotAt(days, j - 1)
            val b = slotAt(days, j)
            if (a == b) continue
            val at = timeOf(days, j)
            if (!at.minusMinutes(lead).isAfter(now)) continue
            val sure = sureAt(days, j)
            when {
                b == Slot.OFF && sure -> out += Event(place.id, EventType.OFF, at, endOf(days, j))
                a == Slot.OFF && b != Slot.UNKNOWN && sureAt(days, j - 1) && withOn -> out += Event(place.id, EventType.ON, at)
                b == Slot.MAYBE && a == Slot.ON && withMaybe -> out += Event(place.id, EventType.MAYBE, at, endOf(days, j))
            }
        }
        return out
    }

    /** Коли закінчиться стан, що почався в половині [j]; null — за межами двох днів. */
    private fun endOf(days: List<DayPlan>, j: Int): ZonedDateTime? {
        val total = days.size * SLOTS
        val s = slotAt(days, j)
        for (k in j + 1 until total) if (slotAt(days, k) != s) return timeOf(days, k)
        return null
    }

    /** Відрізок дня без світла / з можливим відключенням: «13:00–16:30». */
    fun outages(day: DayPlan): List<Span> = Plans.spans(day.slots).filter { it.slot == Slot.OFF || it.slot == Slot.MAYBE }

    /** Скільки годин без світла за день (за точним графіком). */
    fun offHours(day: DayPlan): Double = day.slots.count { it == Slot.OFF } / 2.0
}
