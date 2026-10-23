package ua.x44to.svitlo.data

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Мова: українська для uk/ru/be, інакше англійська. Тексти — парами прямо в коді: t("Захист", "Protection"). */
object L {
    /** для тестів зі скриншотами і вибору мови в налаштуваннях: "uk", "en" або null — як у системі */
    @Volatile var override: String? = null

    val ukr: Boolean get() = (override ?: Locale.getDefault().language) in setOf("uk", "ru", "be")

    /** 1 програма, 2 програми, 5 програм / 1 app, 2 apps */
    fun plural(n: Int, one: String, few: String, many: String, en1: String, enN: String): String {
        if (!ukr) return "$n ${if (n == 1) en1 else enN}"
        val m10 = n % 10
        val m100 = n % 100
        val w = when {
            m10 == 1 && m100 != 11 -> one
            m10 in 2..4 && m100 !in 12..14 -> few
            else -> many
        }
        return "$n $w"
    }

    fun apps(n: Int) = plural(n, "програма", "програми", "програм", "app", "apps")
    fun files(n: Int) = plural(n, "файл", "файли", "файлів", "file", "files")
    fun folders(n: Int) = plural(n, "папка", "папки", "папок", "folder", "folders")
    fun days(n: Int) = plural(n, "день", "дні", "днів", "day", "days")

    private val monthsUkGen = listOf("січня", "лютого", "березня", "квітня", "травня", "червня", "липня", "серпня", "вересня", "жовтня", "листопада", "грудня")
    private val monthsUk = listOf("січень", "лютий", "березень", "квітень", "травень", "червень", "липень", "серпень", "вересень", "жовтень", "листопад", "грудень")
    private val monthsEn = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")

    /** «20 жовтня 2026» / «October 20, 2026» */
    fun date(y: Int, m: Int, d: Int) = if (ukr) "$d ${monthsUkGen[m - 1]} $y" else "${monthsEn[m - 1]} $d, $y"

    /** «травень 2024» / «May 2024» */
    fun monthYear(y: Int, m: Int) = if (ukr) "${monthsUk[m - 1]} $y" else "${monthsEn[m - 1]} $y"

    /** «щойно», «5 хв тому», «3 год тому», «вчора», «4 дні тому» */
    fun ago(ms: Long, now: Long = System.currentTimeMillis()): String {
        val s = ((now - ms) / 1000).coerceAtLeast(0)
        return when {
            s < 60 -> t("щойно", "just now")
            s < 3600 -> t("${s / 60} хв тому", "${s / 60} min ago")
            s < 86400 -> t("${s / 3600} год тому", "${s / 3600} h ago")
            s < 2 * 86400 -> t("вчора", "yesterday")
            else -> t("${days((s / 86400).toInt())} тому", "${days((s / 86400).toInt())} ago")
        }
    }
}

fun t(uk: String, en: String) = if (L.ukr) uk else en

/** 1,2 ГБ · 340 МБ · 12 КБ */
fun bytes(b: Long): String {
    val sym = DecimalFormatSymbols(if (L.ukr) Locale.forLanguageTag("uk") else Locale.ENGLISH)
    val f1 = DecimalFormat("0.0", sym)
    val f0 = DecimalFormat("0", sym)
    val kb = 1024.0
    return when {
        b >= kb * kb * kb -> f1.format(b / (kb * kb * kb)) + t(" ГБ", " GB")
        b >= kb * kb * 100 -> f0.format(b / (kb * kb)) + t(" МБ", " MB")
        b >= kb * kb -> f1.format(b / (kb * kb)) + t(" МБ", " MB")
        b >= kb -> f0.format(b / kb) + t(" КБ", " KB")
        else -> "$b" + t(" Б", " B")
    }
}

/** «2 год 15 хв» / «2 h 15 min»; менше хвилини — «менше хвилини». */
fun duration(minutes: Long): String {
    val m = minutes.coerceAtLeast(0)
    val h = m / 60
    val r = m % 60
    return when {
        m < 1 -> t("менше хвилини", "under a minute")
        h == 0L -> t("$r хв", "$r min")
        r == 0L -> t("$h год", "$h h")
        else -> t("$h год $r хв", "$h h $r min")
    }
}
