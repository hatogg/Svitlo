package ua.x44to.svitlo

import android.app.NotificationManager
import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ua.x44to.svitlo.alarm.Notify
import ua.x44to.svitlo.data.Event
import ua.x44to.svitlo.data.EventType
import ua.x44to.svitlo.data.KYIV
import ua.x44to.svitlo.data.Place
import ua.x44to.svitlo.data.RegionData
import ua.x44to.svitlo.data.Repo
import ua.x44to.svitlo.data.Store
import ua.x44to.svitlo.data.t
import java.io.File
import java.io.FileOutputStream
import java.time.ZonedDateTime

/**
 * Перевірка на справжньому Android і фотосесія для README. Графік — тестовий, складений навколо поточної години,
 * щоб на екрані було «Світла немає · буде о …» і список відключень; інтернет не використовується.
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx get() = inst.targetContext

    private fun fixture(): String {
        val now = ZonedDateTime.now(KYIV)
        val today = now.toLocalDate()
        val h = now.hour + 1 // години в графіку — 1..24
        fun day(off: (Int) -> Boolean, first: Set<Int> = emptySet()) =
            (1..24).joinToString(",", "{", "}") { x -> "\"$x\":\"" + (if (x in first) "first" else if (off(x)) "no" else "yes") + "\"" }
        // зараз світла немає: від години тому до +2 год (з половиною), і ще ввечері
        val todayJson = day({ x -> x in (h - 1)..(h + 2) || x in (h + 6)..(h + 7) }, first = setOf(h + 3))
        val tomorrowJson = day({ x -> x in 9..11 || x in 19..21 })
        val week = (1..24).joinToString(",", "{", "}") { x -> "\"$x\":\"" + (if (x in 9..11 || x in 19..21) "maybe" else "yes") + "\"" }
        val ts = today.atStartOfDay(KYIV).toEpochSecond()
        val ts2 = today.plusDays(1).atStartOfDay(KYIV).toEpochSecond()
        val groups = (1..6).flatMap { listOf("GPV$it.1", "GPV$it.2") }
        val names = groups.joinToString(",", "{", "}") { "\"$it\":\"Черга ${it.removePrefix("GPV")}\"" }
        val presets = groups.joinToString(",", "{", "}") { g -> "\"$g\":" + (1..7).joinToString(",", "{", "}") { "\"$it\":$week" } }
        return """{"regionId":"kyiv","regionAffiliation":"м. Київ",
            "fact":{"data":{"$ts":{"GPV3.1":$todayJson},"$ts2":{"GPV3.1":$tomorrowJson}},"update":"${today.dayOfMonth}.${today.monthValue}.${today.year} 07:10","today":$ts},
            "preset":{"sch_names":$names,"data":$presets,"updateFact":"20.10.2026 18:00"},
            "lastUpdateStatus":{"at":"${now.toInstant()}"}}"""
    }

    @Test
    fun checkAndShoot() {
        Repo.offline = true
        Store.init(ctx)
        Repo.file(ctx, "kyiv").writeText(fixture())
        Repo.regions["kyiv"] = RegionData.parse(Repo.file(ctx, "kyiv").readText())
        for (lang in listOf("uk", "en")) {
            Store.chooseLang(lang)
            Store.chooseTheme(1)
            places()
            ActivityScenario.launch(MainActivity::class.java).use {
                settle()
                assertTrue("немає стану «світла немає»", has(t("Світла немає", "Power is off")))
                shot("$lang-light-home")

                click(t("Робота", "Work"))
                settle()
                shot("$lang-light-home-on")
                click(t("Дім", "Home"))

                click(t("Додати", "Add"))
                settle()
                click(t("Київ", "Kyiv"))
                settle()
                click("3.1")
                settle()
                shot("$lang-light-editor")
                click(t("Інша область — свій графік", "Another region — my own schedule"))
                settle()
                for (hh in listOf("18", "19", "20")) click(hh)
                settle()
                shot("$lang-light-manual")
                back()

                if (lang == "uk") {
                    Store.chooseTheme(2)
                    settle()
                    shot("uk-dark-home")
                    Store.chooseTheme(1)
                    settle()
                }
            }
        }

        // нагадування: як воно виглядає на телефоні
        Store.chooseLang("uk")
        places()
        val now = ZonedDateTime.now(KYIV).withSecond(0).withNano(0)
        val home = Store.places.first { it.id == "home" }
        Notify.event(ctx, home, Event("home", EventType.OFF, now.plusMinutes(15), now.plusMinutes(195)), 15)
        Thread.sleep(1500)
        val active = ctx.getSystemService(NotificationManager::class.java).activeNotifications
        assertTrue("нагадування не з'явилося", active.isNotEmpty())
        shell("cmd statusbar expand-notifications")
        Thread.sleep(2500)
        // лише верх шторки з нашим сповіщенням, без чужих системних
        shot("uk-notification", cropTop = 0.47f)
        shell("cmd statusbar collapse")
        Store.chooseLang(null)
        Store.chooseTheme(0)
    }

    /** Дві адреси з назвами мовою знімка: дім (графік Києва) і робота (свій графік, без світла 18:00–21:00). */
    private fun places() {
        for (p in Store.places.toList()) Store.delete(p.id)
        val evening = "0".repeat(36) + "1".repeat(6) + "0".repeat(6)
        Store.save(Place("work", t("Робота", "Work"), manual = List(7) { evening }))
        Store.save(Place("home", t("Дім", "Home"), "kyiv", "GPV3.1"))
    }

    private fun has(text: String) = compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun click(text: String) {
        val node = compose.onAllNodesWithText(text, useUnmergedTree = true).onFirst()
        try {
            node.performScrollTo()
        } catch (e: AssertionError) {
            // не в прокручуваному списку — і так на екрані
        }
        node.performClick()
        compose.waitForIdle()
    }

    private fun back() {
        shell("input keyevent 4")
        settle()
    }

    private fun settle() {
        compose.waitForIdle()
        Thread.sleep(1500)
        compose.waitForIdle()
    }

    private fun shot(name: String, cropTop: Float = 1f) {
        val full: Bitmap = inst.uiAutomation.takeScreenshot() ?: error("no screenshot for $name")
        val bmp = if (cropTop < 1f) Bitmap.createBitmap(full, 0, 0, full.width, (full.height * cropTop).toInt()) else full
        val dir = File(ctx.filesDir, "screens").apply { mkdirs() }
        FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun shell(cmd: String) {
        inst.uiAutomation.executeShellCommand(cmd).use { pfd ->
            java.io.FileInputStream(pfd.fileDescriptor).use { it.readBytes() }
        }
    }
}
