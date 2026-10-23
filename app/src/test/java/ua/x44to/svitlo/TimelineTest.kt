package ua.x44to.svitlo

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.x44to.svitlo.data.EventType
import ua.x44to.svitlo.data.KYIV
import ua.x44to.svitlo.data.Kind
import ua.x44to.svitlo.data.L
import ua.x44to.svitlo.data.Place
import ua.x44to.svitlo.data.RegionData
import ua.x44to.svitlo.data.Slot
import ua.x44to.svitlo.data.Timeline
import ua.x44to.svitlo.data.Words
import java.time.LocalDate
import java.time.ZonedDateTime

class TimelineTest {
    @After fun reset() { L.override = null }

    private val today: LocalDate = LocalDate.of(2026, 10, 23)

    /** Години 1..24 → код. Сьогодні: 00–03 немає, 03:00–03:30 немає (first), 09–12 немає, 12:00–12:30 немає, решта є. */
    private val todayHours = (1..24).associateWith { h ->
        when (h) {
            in 1..3 -> "no"; 4 -> "first"; in 10..12 -> "no"; 13 -> "first"; else -> "yes"
        }
    }

    private fun json(withTomorrow: Boolean): String {
        val ts = today.atStartOfDay(KYIV).toEpochSecond()
        val ts2 = today.plusDays(1).atStartOfDay(KYIV).toEpochSecond()
        fun hours(m: Map<Int, String>) = m.entries.joinToString(",", "{", "}") { "\"${it.key}\":\"${it.value}\"" }
        val tomorrow = (1..24).associateWith { h -> if (h in 19..21) "no" else "yes" }
        val week = (1..24).associateWith { h -> when (h) { in 17..19 -> "maybe"; 20 -> "mfirst"; else -> "yes" } }
        val fact = buildString {
            append("\"$ts\":{\"GPV1.1\":${hours(todayHours)}}")
            if (withTomorrow) append(",\"$ts2\":{\"GPV1.1\":${hours(tomorrow)}}")
        }
        val preset = (1..7).joinToString(",", "{", "}") { "\"$it\":${hours(week)}" }
        return """{"regionId":"kyiv","regionAffiliation":"м. Київ",
            "fact":{"data":{$fact},"update":"22.10.2026 22:13","today":$ts},
            "preset":{"sch_names":{"GPV1.1":"Черга 1.1"},"data":{"GPV1.1":$preset},"updateFact":"22.10.2026 22:13"},
            "lastUpdateStatus":{"at":"2026-10-23T07:36:05.608Z"}}"""
    }

    private val place = Place("home", "Дім", "kyiv", "GPV1.1")

    private fun at(h: Int, m: Int, day: LocalDate = today) = ZonedDateTime.of(day.year, day.monthValue, day.dayOfMonth, h, m, 0, 0, KYIV)

    @Test fun statusNowAndNext() {
        val r = RegionData.parse(json(withTomorrow = true))
        val days = Timeline.days(place, r, today)
        assertEquals(Kind.FACT, days[0].kind)
        assertEquals(Kind.FACT, days[1].kind)

        val off = Timeline.status(days, at(10, 15))
        assertEquals(Slot.OFF, off.now)
        assertTrue(off.sure)
        assertEquals(at(12, 30), off.until)

        val on = Timeline.status(days, at(13, 0))
        assertEquals(Slot.ON, on.now)
        assertEquals(Slot.OFF, on.next)
        // наступне відключення — завтра о 18:00
        assertEquals(at(18, 0, today.plusDays(1)), on.until)
    }

    @Test fun tomorrowNotPublishedFallsBackToPossible() {
        val r = RegionData.parse(json(withTomorrow = false))
        val days = Timeline.days(place, r, today)
        assertEquals(Kind.POSSIBLE, days[1].kind)
        assertTrue(Slot.MAYBE in days[1].slots)
        assertTrue(Slot.OFF !in days[1].slots)
    }

    @Test fun reminders() {
        val r = RegionData.parse(json(withTomorrow = true))
        val days = Timeline.days(place, r, today)
        val ev = Timeline.events(place, days, at(8, 0), lead = 15, withOn = true, withMaybe = false)
        // 09:00 вимкнуть (до 12:30), 12:30 повернеться, завтра 18:00 вимкнуть (до 21:00), 21:00 повернеться
        assertEquals(listOf(EventType.OFF, EventType.ON, EventType.OFF, EventType.ON), ev.map { it.type })
        assertEquals(at(9, 0), ev[0].at)
        assertEquals(at(12, 30), ev[0].till)
        assertEquals(at(12, 30), ev[1].at)
        assertEquals(at(21, 0, today.plusDays(1)), ev[2].till)
        // за 15 хвилин до 09:00 — уже 08:50: нагадування про 09:00 не повторюємо
        val late = Timeline.events(place, days, at(8, 50), lead = 15, withOn = false, withMaybe = false)
        assertEquals(at(18, 0, today.plusDays(1)), late.first().at)
    }

    @Test fun possibleOutagesOnlyWhenAsked() {
        val r = RegionData.parse(json(withTomorrow = false))
        val days = Timeline.days(place, r, today)
        val none = Timeline.events(place, days, at(13, 0), 15, withOn = false, withMaybe = false)
        assertTrue(none.isEmpty())
        val maybe = Timeline.events(place, days, at(13, 0), 15, withOn = false, withMaybe = true)
        assertEquals(EventType.MAYBE, maybe.single().type)
        assertEquals(at(16, 0, today.plusDays(1)), maybe.single().at)
    }

    @Test fun clockChangeDay() {
        // 25 жовтня 2026 у Києві переводять годинник: 04:00 літнього часу стає 03:00 зимового
        val d = LocalDate.of(2026, 10, 25)
        val days = Timeline.days(Place("m", "m", manual = List(7) { "0".repeat(48) }), null, d)
        assertEquals("03:00", Words.hm(Timeline.timeOf(days, 6)))
        assertEquals("05:00", Words.hm(Timeline.timeOf(days, 10)))
    }

    @Test fun words() {
        L.override = "uk"
        val r = RegionData.parse(json(withTomorrow = true))
        val days = Timeline.days(place, r, today)
        assertEquals("Світла немає", Words.title(Timeline.status(days, at(10, 15))))
        assertEquals("Буде о 12:30", Words.line(Timeline.status(days, at(10, 15)), at(10, 15)))
        assertEquals("Вимкнуть о 09:00", Words.line(Timeline.status(days, at(8, 0)), at(8, 0)))
        assertEquals("через 1 год", Words.countdown(Timeline.status(days, at(8, 0)), at(8, 0)))
        assertEquals("Вимкнуть завтра о 18:00", Words.line(Timeline.status(days, at(13, 0)), at(13, 0)))
        assertEquals("3,5 год", Words.hours(7))
        L.override = "en"
        assertEquals("Back at 12:30", Words.line(Timeline.status(days, at(10, 15)), at(10, 15)))
    }

    @Test fun nothingKnown() {
        val days = Timeline.days(place, null, today)
        val s = Timeline.status(days, at(10, 0))
        assertEquals(Slot.UNKNOWN, s.now)
        assertNull(s.until)
    }
}
