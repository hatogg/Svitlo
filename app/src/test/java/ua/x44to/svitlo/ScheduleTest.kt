package ua.x44to.svitlo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.x44to.svitlo.data.Codes
import ua.x44to.svitlo.data.Kind
import ua.x44to.svitlo.data.KYIV
import ua.x44to.svitlo.data.Place
import ua.x44to.svitlo.data.Plans
import ua.x44to.svitlo.data.RegionData
import ua.x44to.svitlo.data.SLOTS
import ua.x44to.svitlo.data.Slot
import java.time.LocalDate

class ScheduleTest {
    private fun res(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()

    @Test fun halves() {
        assertEquals(Slot.ON to Slot.ON, Codes.halves("yes"))
        assertEquals(Slot.OFF to Slot.OFF, Codes.halves("no"))
        assertEquals(Slot.OFF to Slot.ON, Codes.halves("first"))
        assertEquals(Slot.ON to Slot.OFF, Codes.halves("second"))
        assertEquals(Slot.MAYBE to Slot.MAYBE, Codes.halves("maybe"))
        assertEquals(Slot.MAYBE to Slot.ON, Codes.halves("mfirst"))
        assertEquals(Slot.ON to Slot.MAYBE, Codes.halves("msecond"))
        assertEquals(Slot.UNKNOWN to Slot.UNKNOWN, Codes.halves(null))
    }

    @Test fun realKyivRegion() {
        val r = RegionData.parse(res("kyiv-region.json"))
        assertEquals("kyiv-region", r.id)
        assertEquals(12, r.groups.size)
        assertEquals("GPV1.1", r.groups.first().id)
        assertEquals("1.1", r.groups.first().short)
        assertEquals("Черга 6.2", r.groupName("GPV6.2"))
        val day = r.fact.keys.single()
        assertEquals(LocalDate.of(2026, 10, 23), day)
        assertEquals(SLOTS, r.fact.getValue(day).getValue("GPV1.1").size)
        assertEquals(7, r.preset.getValue("GPV1.1").size)
        assertTrue(r.checkedAt != null)
    }

    @Test fun regionWithoutTodaysScheduleShowsPossible() {
        // у Дніпра сьогоднішнього графіка немає (fact.data — порожній список)
        val r = RegionData.parse(res("dnipro.json"))
        assertTrue(r.fact.isEmpty())
        assertTrue(r.groups.any { it.short == "ЦЕК 1.1" })
        val p = Place("p", "Дім", "dnipro", "GPV1.1")
        val d = Plans.day(p, r, LocalDate.of(2026, 10, 23))
        assertEquals(Kind.POSSIBLE, d.kind)
        assertTrue(d.slots.none { it == Slot.OFF })
    }

    @Test fun groupsSortByNumbers() {
        val s = listOf("GPV10.1", "GPV1001.1", "GPV2.2", "GPV2.1", "GPV1.1").sortedWith(RegionData.GROUP_ORDER)
        assertEquals(listOf("GPV1.1", "GPV2.1", "GPV2.2", "GPV10.1", "GPV1001.1"), s)
    }

    @Test fun manualSchedule() {
        val off = "0".repeat(26) + "1".repeat(8) + "0".repeat(14) // 13:00–17:00
        val p = Place("m", "Дача", manual = List(7) { off })
        val d = Plans.day(p, null, LocalDate.of(2026, 10, 23))
        assertEquals(Kind.MANUAL, d.kind)
        val spans = Plans.spans(d.slots).filter { it.slot == Slot.OFF }
        assertEquals(1, spans.size)
        assertEquals("13:00", Plans.time(spans[0].from))
        assertEquals("17:00", Plans.time(spans[0].to))
    }

    @Test fun midnightIsTheEndOfDay() {
        assertEquals("24:00", Plans.time(SLOTS))
        assertNull(Plans.day(Place("x", "x", "kyiv", null), null, LocalDate.now(KYIV)).slots.firstOrNull { it != Slot.UNKNOWN })
    }
}
