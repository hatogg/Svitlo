package ua.x44to.svitlo.data

import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Однакові слова для головного екрана, віджета й сповіщень. */
object Words {
    private val HM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun hm(z: ZonedDateTime): String = z.withZoneSameInstant(KYIV).format(HM)

    fun title(s: Status): String = when (s.now) {
        Slot.OFF -> t("Світла немає", "Power is off")
        Slot.MAYBE -> t("Можливе відключення", "Possible outage")
        Slot.ON -> t("Світло є", "Power is on")
        Slot.UNKNOWN -> t("Графіка немає", "No schedule")
    }

    /** «вимкнуть о 13:00», «буде о 18:00», «можливе відключення о 20:00». */
    fun line(s: Status, now: ZonedDateTime): String {
        val u = s.until
        val tomorrow = u != null && u.withZoneSameInstant(KYIV).toLocalDate() != now.withZoneSameInstant(KYIV).toLocalDate()
        val at = u?.let { (if (tomorrow) t("завтра о ", "tomorrow at ") else t("о ", "at ")) + hm(it) } ?: ""
        return when {
            s.now == Slot.UNKNOWN -> t("Оберіть чергу або перевірте інтернет", "Pick your group or check the internet")
            u == null && s.now == Slot.ON -> t("До кінця завтра відключень немає", "No outages through tomorrow")
            u == null && s.now == Slot.OFF -> t("Коли повернеться — ще невідомо", "Return time is not known yet")
            u == null -> ""
            s.now == Slot.OFF -> t("Буде ", "Back ") + at
            s.now == Slot.MAYBE -> t("До ", "Until ") + hm(u)
            s.next == Slot.OFF -> t("Вимкнуть ", "Goes off ") + at
            s.next == Slot.MAYBE -> t("Можливе відключення ", "Possible outage ") + at
            else -> t("До ", "Until ") + hm(u)
        }
    }

    /** «через 2 год 15 хв» */
    fun countdown(s: Status, now: ZonedDateTime): String? {
        val u = s.until ?: return null
        val m = Duration.between(now, u).toMinutes()
        if (m < 0) return null
        return t("через ", "in ") + duration(m)
    }

    fun span(sp: Span) = Plans.time(sp.from) + "–" + Plans.time(sp.to)

    fun hours(slots: Int): String {
        val h = slots / 2
        val half = slots % 2 == 1
        return when {
            h == 0 -> t("30 хв", "30 min")
            half -> t("$h,5 год", "$h.5 h")
            else -> t("$h год", "$h h")
        }
    }
}
