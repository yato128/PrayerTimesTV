package com.example.prayertimestv

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.util.Calendar
import java.util.Locale
import java.util.zip.ZipInputStream

data class PrayerDay(
    val date: String,
    val fajr: String,
    val sunrise: String,
    val dhuhr: String,
    val asr: String,
    val maghrib: String,
    val isha: String
)

class MainActivity : Activity() {
    private val bg = Color.rgb(7, 31, 27)
    private val panel = Color.rgb(14, 49, 42)
    private val gold = Color.rgb(224, 190, 117)
    private val cream = Color.rgb(246, 241, 225)
    private val muted = Color.rgb(177, 198, 186)
    private lateinit var root: LinearLayout
    private lateinit var cityText: TextView
    private lateinit var dateText: TextView
    private lateinit var clockText: TextView
    private lateinit var nextText: TextView
    private lateinit var countdownText: TextView
    private lateinit var prayerRow: LinearLayout
    private lateinit var statusText: TextView
    private var city = "نام شهر"
    private var days: List<PrayerDay> = emptyList()
    private var timer: android.os.Handler? = null
    private val tick = object : Runnable {
        override fun run() {
            updateClockAndPrayer()
            timer?.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val prefs = getPreferences(MODE_PRIVATE)
        city = prefs.getString("city", "نام شهر") ?: "نام شهر"
        days = decodeDays(prefs.getString("prayer_days", "") ?: "")
        render()
        timer = android.os.Handler(mainLooper)
        timer?.post(tick)
    }

    override fun onDestroy() {
        timer?.removeCallbacks(tick)
        super.onDestroy()
    }

    private fun render() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(30), dp(22), dp(30), dp(20))
            setBackgroundColor(bg)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val brandColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        cityText = text(city, 30, gold, true)
        dateText = text("تاریخ امروز", 16, muted, false)
        brandColumn.addView(cityText)
        brandColumn.addView(dateText)
        header.addView(brandColumn, LinearLayout.LayoutParams(0, -2, 1f))
        val clockColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        clockText = text("--:--:--", 38, cream, true).apply { gravity = Gravity.CENTER }
        clockColumn.addView(clockText)
        clockColumn.addView(text("ساعت رسمی دستگاه", 13, muted, false))
        header.addView(clockColumn)
        root.addView(header)

        val separator = View(this).apply { setBackgroundColor(Color.rgb(57, 91, 76)) }
        root.addView(separator, LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(16); bottomMargin = dp(18) })

        val middle = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val nextCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(20), dp(20), dp(20))
            setBackgroundColor(panel)
        }
        nextCard.background = rounded(panel, dp(18))
        nextCard.addView(text("اذان بعدی", 18, muted, true))
        nextText = text("در انتظار فایل اکسل", 25, gold, true).apply { gravity = Gravity.CENTER; setPadding(0, dp(10), 0, dp(6)) }
        countdownText = text("--:--:--", 32, cream, true).apply { gravity = Gravity.CENTER }
        nextCard.addView(nextText)
        nextCard.addView(countdownText)
        middle.addView(nextCard, LinearLayout.LayoutParams(0, dp(165), 1.1f).apply { marginEnd = dp(18) })

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        info.addView(text("نمایشگر هوشمند اوقات شرعی", 21, cream, true))
        info.addView(text("اطلاعات شهر از فایل Excel خوانده می‌شود", 15, muted, false).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(12), 0, dp(12))
        })
        val importButton = Button(this).apply {
            text = "بارگذاری فایل اکسل"
            textSize = 16f
            setTextColor(bg)
            isAllCaps = false
            backgroundTintList = android.content.res.ColorStateList.valueOf(gold)
            setOnClickListener { chooseExcel() }
        }
        info.addView(importButton, LinearLayout.LayoutParams(-1, dp(54)))
        val cityButton = Button(this).apply {
            text = "تنظیم نام شهر"
            textSize = 15f
            isAllCaps = false
            setOnClickListener { askCity() }
        }
        info.addView(cityButton, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(6) })
        middle.addView(info, LinearLayout.LayoutParams(0, dp(165), 1f))
        root.addView(middle)

        prayerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        root.addView(prayerRow, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(18); bottomMargin = dp(8) })
        statusText = text("برای شروع، فایل اکسل اوقات شرعی را وارد کنید.", 14, muted, false).apply {
            gravity = Gravity.CENTER
        }
        root.addView(statusText)
        setContentView(root)
        drawPrayerCards()
    }

    private fun drawPrayerCards(active: String? = null) {
        prayerRow.removeAllViews()
        val labels = listOf("اذان صبح", "طلوع آفتاب", "اذان ظهر", "اذان عصر", "اذان مغرب", "اذان عشا")
        val values = listOf("--:--", "--:--", "--:--", "--:--", "--:--", "--:--")
        val today = findToday()
        val times = today?.let { listOf(it.fajr, it.sunrise, it.dhuhr, it.asr, it.maghrib, it.isha) } ?: values
        labels.forEachIndexed { i, label ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(6), dp(16), dp(6), dp(16))
                background = rounded(if (label == active) Color.rgb(73, 83, 49) else panel, dp(14))
            }
            card.addView(text(label, 16, if (label == active) gold else muted, true).apply { gravity = Gravity.CENTER })
            card.addView(text(times[i], 24, cream, true).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(12), 0, 0)
            })
            prayerRow.addView(card, LinearLayout.LayoutParams(0, -1, 1f).apply {
                if (i != 5) marginEnd = dp(8)
            })
        }
    }

    private fun updateClockAndPrayer() {
        val now = Calendar.getInstance()
        clockText.text = String.format(Locale.US, "%02d:%02d:%02d",
            now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), now.get(Calendar.SECOND))
        val date = String.format(Locale.US, "%04d/%02d/%02d",
            now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH))
        if (days.isEmpty()) {
            dateText.text = "امروز: $date"
            return
        }
        val day = findToday()
        if (day == null) {
            dateText.text = "امروز: $date"
            nextText.text = "اطلاعات امروز موجود نیست"
            countdownText.text = "--:--:--"
            statusText.text = "فایل اکسل بارگذاری شده؛ اما تاریخ امروز در آن پیدا نشد."
            drawPrayerCards()
            return
        }
        dateText.text = "تاریخ فایل: ${day.date}"
        val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val upcoming = listOf(
            "اذان صبح" to day.fajr, "اذان ظهر" to day.dhuhr, "اذان عصر" to day.asr,
            "اذان مغرب" to day.maghrib, "اذان عشا" to day.isha
        ).mapNotNull { (name, time) ->
            val parts = time.split(":")
            if (parts.size >= 2) {
                val mins = parts[0].toIntOrNull()?.times(60)?.plus(parts[1].toIntOrNull() ?: 0)
                if (mins != null) Pair(name, mins) else null
            } else null
        }.firstOrNull { it.second > nowMinutes }
        if (upcoming != null) {
            nextText.text = "${upcoming.first} • ${String.format(Locale.US, "%02d:%02d", upcoming.second / 60, upcoming.second % 60)}"
            val seconds = upcoming.second * 60 - (now.get(Calendar.HOUR_OF_DAY) * 3600 + now.get(Calendar.MINUTE) * 60 + now.get(Calendar.SECOND))
            countdownText.text = String.format(Locale.US, "%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60)
            drawPrayerCards(upcoming.first)
        } else {
            nextText.text = "اذان صبح فردا"
            countdownText.text = "پس از تکمیل اطلاعات فردا"
            drawPrayerCards()
        }
        statusText.text = "اطلاعات به‌صورت محلی ذخیره می‌شود • بدون نیاز به اینترنت"
    }

    private fun findToday(): PrayerDay? {
        val cal = Calendar.getInstance()
        val gy = cal.get(Calendar.YEAR)
        val gm = cal.get(Calendar.MONTH) + 1
        val gd = cal.get(Calendar.DAY_OF_MONTH)
        val gregorian = String.format(Locale.US, "%04d/%02d/%02d", gy, gm, gd)
        val jalaliParts = gregorianToJalali(gy, gm, gd)
        val jalali = String.format(Locale.US, "%04d/%02d/%02d", jalaliParts[0], jalaliParts[1], jalaliParts[2])
        return days.firstOrNull {
            val d = normalizeDate(it.date)
            d == normalizeDate(gregorian) || d == normalizeDate(jalali)
        }
    }

    private fun normalizeDate(s: String): String =
        s.trim().replace('-', '/').replace('.', '/').replace(" ", "")

    // Gregorian to Jalali conversion, used to match the Persian dates commonly found in local prayer calendars.
    private fun gregorianToJalali(gy0: Int, gm: Int, gd: Int): IntArray {
        val gdm = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        var gy = gy0
        val gy2 = if (gm > 2) gy + 1 else gy
        var days = 355666 + 365 * gy + (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 399) / 400 + gd + gdm[gm - 1]
        var jy = -1595 + 33 * (days / 12053)
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            jy += (days - 1) / 365
            days = (days - 1) % 365
        }
        val jm: Int
        val jd: Int
        if (days < 186) {
            jm = 1 + (days / 31).toInt()
            jd = 1 + (days % 31).toInt()
        } else {
            jm = 7 + ((days - 186) / 30).toInt()
            jd = 1 + ((days - 186) % 30).toInt()
        }
        return intArrayOf(jy, jm, jd)
    }

    private fun encodeDays(items: List<PrayerDay>): String = items.joinToString("\\n") {
        listOf(it.date, it.fajr, it.sunrise, it.dhuhr, it.asr, it.maghrib, it.isha)
            .joinToString("|") { value -> value.replace("|", "") }
    }

    private fun decodeDays(raw: String): List<PrayerDay> {
        if (raw.isBlank()) return emptyList()
        return raw.lineSequence().mapNotNull { line ->
            val c = line.split("|")
            if (c.size == 7) PrayerDay(c[0], c[1], c[2], c[3], c[4], c[5], c[6]) else null
        }.toList()
    }

    private fun chooseExcel() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, 1001)
    }

    @Deprecated("Legacy callback used for broad Android TV compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == RESULT_OK && data?.data != null) {
            try {
                val parsed = parseXlsx(contentResolver.openInputStream(data.data!!)!!)
                if (parsed.isEmpty()) throw IllegalArgumentException("ردیف معتبر پیدا نشد")
                days = parsed
                getPreferences(MODE_PRIVATE).edit()
                    .putString("prayer_days", encodeDays(parsed))
                    .putString("excel_uri", data.data.toString())
                    .apply()
                drawPrayerCards()
                updateClockAndPrayer()
                Toast.makeText(this, "فایل اکسل با ${days.size} ردیف خوانده شد", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                AlertDialog.Builder(this).setTitle("خطا در فایل اکسل")
                    .setMessage("فایل خوانده نشد. قالب پیشنهادی را در README ببینید.\n${e.message ?: ""}")
                    .setPositiveButton("متوجه شدم", null).show()
            }
        }
    }

    private fun parseXlsx(input: InputStream): List<PrayerDay> {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) entries[entry.name] = zip.readBytes()
                zip.closeEntry()
            }
        }
        val shared = mutableListOf<String>()
        entries["xl/sharedStrings.xml"]?.let { bytes ->
            val p = parser(bytes)
            var inSi = false
            val sb = StringBuilder()
            while (p.eventType != XmlPullParser.END_DOCUMENT) {
                if (p.eventType == XmlPullParser.START_TAG && p.name == "si") { inSi = true; sb.setLength(0) }
                else if (inSi && p.eventType == XmlPullParser.START_TAG && p.name == "t") {
                    sb.append(readText(p))
                } else if (p.eventType == XmlPullParser.END_TAG && p.name == "si") {
                    shared.add(sb.toString()); inSi = false
                }
                p.next()
            }
        }
        val sheet = entries["xl/worksheets/sheet1.xml"] ?: throw IllegalArgumentException("برگه اول پیدا نشد")
        val p = parser(sheet)
        val rows = mutableListOf<List<String>>()
        var row = mutableMapOf<Int, String>()
        var currentCol = 0
        var cellType = ""
        var cellValue = ""
        var inValue = false
        while (p.eventType != XmlPullParser.END_DOCUMENT) {
            when {
                p.eventType == XmlPullParser.START_TAG && p.name == "row" -> row = mutableMapOf()
                p.eventType == XmlPullParser.START_TAG && p.name == "c" -> {
                    val ref = p.getAttributeValue(null, "r") ?: "A1"
                    currentCol = columnIndex(ref)
                    cellType = p.getAttributeValue(null, "t") ?: ""
                    cellValue = ""
                }
                p.eventType == XmlPullParser.START_TAG && (p.name == "v" || p.name == "t") -> {
                    inValue = true
                    cellValue += readText(p)
                    inValue = false
                }
                p.eventType == XmlPullParser.END_TAG && p.name == "c" -> {
                    val value = if (cellType == "s") shared.getOrNull(cellValue.toIntOrNull() ?: -1) ?: "" else cellValue
                    row[currentCol] = value.trim()
                }
                p.eventType == XmlPullParser.END_TAG && p.name == "row" -> {
                    if (row.isNotEmpty()) rows.add((0..(row.keys.maxOrNull() ?: 0)).map { row[it] ?: "" })
                }
            }
            p.next()
        }
        if (rows.size < 2) throw IllegalArgumentException("فایل باید سطر عنوان و حداقل یک روز داشته باشد")
        val header = rows.first().map { normalizeHeader(it) }
        fun idx(vararg names: String): Int = header.indexOfFirst { h -> names.any { h.contains(it) } }
        val dateI = idx("تاریخ", "date")
        val fajrI = idx("اذان صبح", "صبح", "fajr")
        val sunriseI = idx("طلوع", "sunrise")
        val dhuhrI = idx("اذان ظهر", "ظهر", "dhuhr", "zuhr")
        val asrI = idx("اذان عصر", "عصر", "asr")
        val maghribI = idx("اذان مغرب", "مغرب", "maghrib")
        val ishaI = idx("اذان عشا", "عشا", "عشاء", "isha")
        if (dateI < 0 || fajrI < 0 || dhuhrI < 0 || maghribI < 0) {
            throw IllegalArgumentException("ستون‌های تاریخ، اذان صبح، اذان ظهر و اذان مغرب الزامی هستند")
        }
        return rows.drop(1).mapNotNull { r ->
            fun get(i: Int) = if (i >= 0) r.getOrElse(i) { "" } else ""
            val date = get(dateI)
            if (date.isBlank()) null else PrayerDay(date, fixTime(get(fajrI)), fixTime(get(sunriseI)),
                fixTime(get(dhuhrI)), fixTime(get(asrI)), fixTime(get(maghribI)), fixTime(get(ishaI)))
        }
    }

    private fun normalizeHeader(s: String): String = s.lowercase(Locale.ROOT)
        .replace("‌", "").replace(" ", "").replace("_", "")

    private fun fixTime(s: String): String {
        val t = s.trim()
        if (t.matches(Regex("\\d{1,2}:\\d{2}(:\\d{2})?"))) {
            val parts = t.split(":")
            return String.format(Locale.US, "%02d:%02d", parts[0].toInt(), parts[1].toInt())
        }
        // Excel numeric time fraction (e.g. 0.5 = noon).
        val n = t.toDoubleOrNull()
        if (n != null && n in 0.0..1.0) {
            val total = (n * 1440).toInt()
            return String.format(Locale.US, "%02d:%02d", total / 60, total % 60)
        }
        return t
    }

    private fun parser(bytes: ByteArray): XmlPullParser {
        val p = XmlPullParserFactory.newInstance().newPullParser()
        p.setInput(bytes.inputStream(), "UTF-8")
        return p
    }

    private fun readText(p: XmlPullParser): String {
        val b = StringBuilder()
        val depth = p.depth
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            if (p.eventType == XmlPullParser.TEXT) b.append(p.text)
            if (p.eventType == XmlPullParser.END_TAG && p.depth == depth) break
        }
        return b.toString()
    }

    private fun columnIndex(ref: String): Int {
        val letters = ref.takeWhile { it.isLetter() }.uppercase(Locale.ROOT)
        var n = 0
        letters.forEach { n = n * 26 + (it - 'A' + 1) }
        return (n - 1).coerceAtLeast(0)
    }

    private fun askCity() {
        val edit = EditText(this).apply { setText(city); textSize = 20f; setSingleLine(true) }
        AlertDialog.Builder(this).setTitle("نام شهر")
            .setMessage("نام شهر را وارد کنید")
            .setView(edit)
            .setPositiveButton("ذخیره") { _, _ ->
                city = edit.text.toString().trim().ifBlank { "نام شهر" }
                getPreferences(MODE_PRIVATE).edit().putString("city", city).apply()
                cityText.text = city
            }
            .setNegativeButton("انصراف", null).show()
    }

    private fun text(value: String, size: Int, color: Int, bold: Boolean): TextView =
        TextView(this).apply {
            text = value
            textSize = size.toFloat()
            setTextColor(color)
            typeface = if (bold) Typeface.create("sans-serif", Typeface.BOLD) else Typeface.create("sans-serif", Typeface.NORMAL)
            gravity = Gravity.CENTER_VERTICAL
            includeFontPadding = true
        }

    private fun rounded(color: Int, radius: Int): android.graphics.drawable.GradientDrawable =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius.toFloat()
            setStroke(dp(1), Color.rgb(49, 82, 69))
        }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
