package ua.x44to.svitlo

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import ua.x44to.svitlo.data.Place
import ua.x44to.svitlo.data.Regions
import ua.x44to.svitlo.data.RegionData
import ua.x44to.svitlo.data.Timeline
import ua.x44to.svitlo.data.KYIV
import java.net.URL
import java.time.LocalDate

/** Справжні графіки з інтернету: чи розбираються всі регіони. Лише в CI (SVITLO_LIVE=1). */
class LiveDataTest {
    @Test fun allRegionsParse() {
        assumeTrue(System.getenv("SVITLO_LIVE") == "1")
        for (r in Regions.ALL) {
            val text = URL("https://raw.githubusercontent.com/Baskerville42/outage-data-ua/main/data/${r.id}.json").readText()
            val d = RegionData.parse(text)
            assertTrue("${r.id}: немає черг", d.groups.size >= 6)
            assertTrue("${r.id}: немає тижневого графіка", d.preset.isNotEmpty())
            val g = d.groups.first().id
            val days = Timeline.days(Place("t", "t", r.id, g), d, LocalDate.now(KYIV))
            println("${r.id}: ${d.groups.size} черг, сьогодні ${days[0].kind}, завтра ${days[1].kind}, графік від ${d.factUpdate}")
        }
    }
}
