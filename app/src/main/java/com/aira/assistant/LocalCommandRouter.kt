package com.aira.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import java.text.Normalizer
import java.util.Calendar

data class CommandResult(
    val handledLocally: Boolean,
    val spokenReply: String,
    val aiPrompt: String? = null
)

class LocalCommandRouter(private val context: Context) {

    private val notHandled = CommandResult(false, "")

    private val appPackages = mapOf(
        "whatsapp" to "com.whatsapp",
        "youtube" to "com.google.android.youtube",
        "chrome" to "com.android.chrome",
        "instagram" to "com.instagram.android",
        "telegram" to "org.telegram.messenger",
        "maps" to "com.google.android.apps.maps",
        "play store" to "com.android.vending",
        "gmail" to "com.google.android.gm",
        "facebook" to "com.facebook.katana",
        "spotify" to "com.spotify.music",
        "phonepe" to "com.phonepe.app",
        "paytm" to "net.one97.paytm",
        "snapchat" to "com.snapchat.android"
    )

    private val hindiPhrases = listOf(
        "फ्लैश लाइट" to "flashlight",
        "टॉर्च लाइट" to "torch",
        "प्ले स्टोर" to "play store",
        "वाई फाई" to "wifi",
        "वाई-फाई" to "wifi",
        "गो बैक" to "go back",
        "होम स्क्रीन" to "home screen",
        "स्क्रॉल डाउन" to "scroll down",
        "स्क्रॉल अप" to "scroll up"
    )

    private val hindiWords = mapOf(
        // flashlight / on-off
        "फ्लैशलाइट" to "flashlight", "टॉर्च" to "torch", "टार्च" to "torch", "लाइट" to "light",
        "ऑन" to "on", "ओन" to "on", "चालू" to "chalu", "ऑफ" to "off", "बंद" to "band", "बन्द" to "band",
        "जलाओ" to "jalao", "बुझाओ" to "bujhao",
        // volume / brightness
        "वॉल्यूम" to "volume", "वॉल्युम" to "volume", "वोल्यूम" to "volume", "आवाज" to "awaz", "साउंड" to "sound",
        "फुल" to "full", "मैक्स" to "max", "अप" to "up", "डाउन" to "down", "लो" to "low",
        "बढ़ा" to "badha", "बढ़ाओ" to "badha", "बढ़ाइए" to "badha", "ज्यादा" to "zyada",
        "कम" to "kam", "घटाओ" to "kam",
        "साइलेंट" to "silent", "म्यूट" to "mute", "नॉर्मल" to "normal", "रिंग" to "ring",
        "ब्राइटनेस" to "brightness",
        // battery / settings / camera
        "बैटरी" to "battery", "चार्ज" to "charge",
        "वाईफाई" to "wifi", "वायफाय" to "wifi", "ब्लूटूथ" to "bluetooth",
        "सेटिंग्स" to "settings", "सेटिंग" to "setting",
        "कैमरा" to "camera", "फोटो" to "photo", "सेल्फी" to "selfie",
        // alarm / timer / reminder / time
        "अलार्म" to "alarm", "टाइमर" to "timer", "रिमाइंडर" to "reminder",
        "याद" to "yaad", "दिलाना" to "dilana", "दिलाओ" to "dilana", "दिला" to "dilana",
        "बजे" to "baje", "सुबह" to "subah", "शाम" to "shaam", "रात" to "raat", "दोपहर" to "dopahar",
        "साढे" to "saade", "सवा" to "sawa", "पौने" to "paune", "आधा" to "aadha",
        "मिनट" to "minute", "सेकंड" to "second", "सेकेंड" to "second",
        "घंटे" to "ghanta", "घंटा" to "ghanta", "घण्टे" to "ghanta",
        "कल" to "kal", "आज" to "aaj",
        "समय" to "samay", "टाइम" to "time", "तारीख" to "tarikh", "डेट" to "date", "दिन" to "din",
        "कितने" to "kitne",
        // screen control
        "स्क्रॉल" to "scroll", "नीचे" to "niche", "ऊपर" to "upar", "बैक" to "back", "पीछे" to "piche",
        "जाओ" to "jao", "करो" to "karo", "होम" to "home", "स्क्रीन" to "screen",
        "क्लिक" to "click", "टैप" to "tap", "दबाओ" to "dabao",
        "स्क्रीनशॉट" to "screenshot", "लॉक" to "lock", "रीसेंट" to "recent",
        "नोटिफिकेशन" to "notifications", "नोटिफिकेशंस" to "notifications", "क्विक" to "quick",
        "पढो" to "padho", "पढ" to "padh", "लिखा" to "likha",
        // apps
        "खोलो" to "kholo", "ओपन" to "open", "यूट्यूब" to "youtube", "व्हाट्सएप" to "whatsapp",
        "व्हाट्सऐप" to "whatsapp", "वॉट्सऐप" to "whatsapp", "वॉट्सएप" to "whatsapp", "व्हाट्स" to "whatsapp",
        "क्रोम" to "chrome", "इंस्टाग्राम" to "instagram", "टेलीग्राम" to "telegram",
        "मैप्स" to "maps", "मैप" to "maps", "प्ले" to "play", "पर" to "par",
        "जीमेल" to "gmail", "फेसबुक" to "facebook", "स्पॉटिफाई" to "spotify",
        "फोनपे" to "phonepe", "पेटीएम" to "paytm", "स्नैपचैट" to "snapchat",
        // call / message
        "कॉल" to "call", "काल" to "call", "कोल" to "call", "फोन" to "phone", "को" to "ko",
        "लगाओ" to "lagao", "लगा" to "lagao", "भेजो" to "bhejo", "भेज" to "bhejo", "भेजना" to "bhejo",
        "मैसेज" to "message", "मेसेज" to "message", "एसएमएस" to "sms", "की" to "ki",
        // navigation
        "रास्ता" to "rasta", "बताओ" to "batao", "नेविगेट" to "navigate", "नेविगेशन" to "navigation",
        "पास" to "paas", "में" to "mein", "नजदीक" to "nazdeek", "है" to "hai", "क्या" to "kya", "कहाँ" to "kahan",
        // music
        "म्यूजिक" to "music", "सॉन्ग" to "song", "गाना" to "gaana", "गाने" to "gaana",
        "पॉज" to "pause", "नेक्स्ट" to "next", "प्रीवियस" to "previous",
        "अगला" to "agla", "अगले" to "agla", "पिछला" to "pichla", "पिछले" to "pichla",
        "रोको" to "roko", "रुको" to "roko", "रिज्यूम" to "resume", "स्टॉप" to "stop"
    )

    private val wordNums = mapOf(
        "ek" to 1, "do" to 2, "teen" to 3, "char" to 4, "panch" to 5, "paanch" to 5, "chhe" to 6,
        "che" to 6, "chah" to 6, "saat" to 7, "aath" to 8, "nau" to 9, "das" to 10, "gyarah" to 11, "barah" to 12,
        "एक" to 1, "दो" to 2, "तीन" to 3, "चार" to 4, "पांच" to 5, "पाँच" to 5, "छह" to 6, "छः" to 6,
        "छे" to 6, "सात" to 7, "आठ" to 8, "नौ" to 9, "दस" to 10, "ग्यारह" to 11, "बारह" to 12
    )

    private val contactStop = setOf(
        "call", "karo", "kar", "do", "lagao", "dial", "phone", "ko", "message", "msg", "sms",
        "bhejo", "bhej", "send", "to", "whatsapp", "par", "pe", "mein", "ki", "ke", "liye",
        "please", "mujhe", "ek", "na", "a", "kaise", "hai"
    )

    private val navStop = setOf(
        "ka", "ke", "ki", "liye", "rasta", "batao", "navigate", "to", "navigation", "directions",
        "direction", "kaise", "jao", "jana", "hai", "mujhe", "mere", "paas", "nearby", "near",
        "nazdeek", "dhundo", "dhoondo", "dikhao", "khojo", "search", "find", "mein", "me", "se",
        "kahan", "sabse", "closest", "nearest", "please", "kar", "karo", "do", "ko", "tak", "chalo", "le"
    )

    private val reminderStop = setOf(
        "yaad", "dilana", "dilao", "dilaye", "dila", "remind", "reminder", "me", "mujhe", "kal", "aaj",
        "baje", "subah", "shaam", "raat", "dopahar", "ko", "ka", "ki", "ke", "at", "to", "set", "karo",
        "kar", "do", "minute", "minutes", "second", "seconds", "ghanta", "ghante", "hour", "hours",
        "baad", "mein", "saade", "sawa", "paune", "am", "pm", "morning", "evening", "night",
        "afternoon", "in", "after", "please", "aadha", "half", "min", "sec", "hr"
    )

    private val clockRe = Regex("""^(\d{1,2})[:.](\d{2})(am|pm)?$""")
    private val ampmRe = Regex("""^(\d{1,2})(am|pm)$""")

    private fun stripNukta(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD).replace("\u093C", "")

    private fun normalizeHindi(raw: String): String {
        var t = stripNukta(raw.lowercase().trim())
        t = buildString {
            for (c in t) append(if (c in '०'..'९') ('0' + (c - '०')) else c)
        }
        for ((hindi, english) in hindiPhrases) t = t.replace(stripNukta(hindi), english)
        val words = hindiWords.mapKeys { stripNukta(it.key) }
        return t.split(Regex("\\s+")).joinToString(" ") { w ->
            val clean = w.trim('.', ',', '?', '!', '।', '"', '\'')
            words[clean] ?: clean
        }.trim()
    }

    private fun numOf(token: String): Int? = token.toIntOrNull() ?: wordNums[token]

    fun tryExecuteLocal(rawText: String): CommandResult {
        val text = normalizeHindi(rawText)
        if (text.isBlank()) return notHandled
        val tokens = text.split(" ").filter { it.isNotBlank() }
        val set = tokens.toSet()

        fun has(vararg words: String): Boolean = words.any { it in set }

        // 1. Screen padhna (AI se samjhayega)
        if (text.contains("screen") && has("padho", "padh", "read", "likha", "summary", "summarize", "samjhao")) {
            val acc = AiraAccessibilityService.instance
                ?: return CommandResult(true, "Screen padhne ke liye AIRA Accessibility on karein.")
            val screenText = acc.readScreenText()
            if (screenText.isBlank()) return CommandResult(true, "Screen par padhne layak text nahi mila.")
            return CommandResult(
                true, "",
                "Neeche kisi phone screen ka text hai. 1 se 2 chhote Hinglish sentences mein batao ki screen par kya hai:\n$screenText"
            )
        }

        // 2. Reminder / Timer / Alarm (time ke saath)
        if (has("yaad", "remind", "reminder")) return setReminder(tokens)
        if (has("timer")) return setTimer(tokens)
        if (has("alarm")) {
            val clock = parseClock(tokens)
            if (clock != null) return setAlarm(clock.first, clock.second, "AIRA Alarm")
        }

        // 3. Date / Time
        if (tokens.size <= 7 && (has("date", "tarikh", "tareekh") || (has("aaj") && has("din")))) {
            return CommandResult(true, dateReply())
        }
        if (tokens.size <= 6 && (has("time", "samay") || (has("kitne") && has("baje")))) {
            return CommandResult(true, timeReply())
        }

        // 4. Flashlight
        if (has("flashlight", "torch", "flash") ||
            (has("light") && has("on", "off", "band", "chalu", "jalao", "bujhao"))
        ) {
            return if (has("off", "band", "bujhao", "bujha")) {
                setFlashlight(false)
                CommandResult(true, "Flashlight band kar di hai.")
            } else {
                setFlashlight(true)
                CommandResult(true, "Flashlight chalu kar di hai.")
            }
        }

        // 5. Volume
        if (has("volume", "awaz", "sound", "silent", "mute")) {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            return when {
                has("full", "max") -> {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVol, AudioManager.FLAG_SHOW_UI)
                    CommandResult(true, "Volume full kar diya hai.")
                }
                has("silent", "mute") -> {
                    try {
                        audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
                        CommandResult(true, "Phone silent kar diya hai.")
                    } catch (_: SecurityException) {
                        openIntent(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                        CommandResult(true, "Silent mode ke liye Do Not Disturb access allow karein.")
                    }
                }
                has("up", "badha", "zyada", "increase", "high") -> {
                    audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                    CommandResult(true, "Volume badha diya hai.")
                }
                has("down", "kam", "decrease", "low") -> {
                    audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                    CommandResult(true, "Volume kam kar diya hai.")
                }
                has("normal", "ring") -> {
                    try {
                        audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                    } catch (_: SecurityException) {
                    }
                    CommandResult(true, "Phone normal mode mein hai.")
                }
                else -> notHandled
            }
        }

        // 6. Brightness
        if (has("brightness")) {
            if (!Settings.System.canWrite(context)) {
                openIntent(
                    Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))
                )
                return CommandResult(true, "Brightness ke liye AIRA ko Modify System Settings ki permission dein.")
            }
            val resolver = context.contentResolver
            val current = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, 128)
            val target = when {
                has("full", "max") -> 255
                has("low", "min") -> 20
                has("up", "badha", "zyada", "increase", "high") -> minOf(255, current + 60)
                has("down", "kam", "decrease") -> maxOf(10, current - 60)
                else -> return notHandled
            }
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, target)
            return CommandResult(true, "Brightness set kar di hai.")
        }

        // 7. Battery
        if (has("battery", "charge", "charging")) {
            return CommandResult(true, "Battery ${getBatteryPercentage()} percent hai.")
        }

        // 8. Music control
        val mediaWords = has("music", "gaana", "gana", "song")
        if (has("pause", "roko") && (mediaWords || tokens.size <= 2)) {
            sendMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
            return CommandResult(true, "Gaana pause kar diya hai.")
        }
        if (has("next", "agla") && (mediaWords || tokens.size <= 2)) {
            sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
            return CommandResult(true, "Agla gaana laga diya hai.")
        }
        if (has("previous", "pichla") && (mediaWords || tokens.size <= 2)) {
            sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            return CommandResult(true, "Pichla gaana laga diya hai.")
        }
        if (has("resume") || text == "play" || (has("play") && mediaWords && tokens.size <= 3)) {
            sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
            return CommandResult(true, "Gaana chalu kar diya hai.")
        }
        if (has("stop") && mediaWords) {
            sendMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
            return CommandResult(true, "Gaana band kar diya hai.")
        }

        // 9. Call / SMS / WhatsApp message
        val isWhatsAppMsg = has("whatsapp") && has("message", "msg", "bhejo", "send")
        val isSms = has("message", "msg", "sms")
        val isCall = has("call", "dial") || (has("lagao") && has("phone"))
        if (isWhatsAppMsg || isSms || isCall) {
            return handleContact(tokens, isWhatsAppMsg, isSms)
        }

        // 10. Navigation / nearby
        val navWords = has("rasta", "navigate", "navigation", "directions", "direction")
        val nearWords = has("paas", "nearby", "nazdeek", "near")
        if (navWords || (nearWords && tokens.size >= 2)) {
            return handleNavigation(tokens, nearWords && !navWords)
        }

        // 11. Screen-wide actions (Accessibility)
        if (has("screenshot")) {
            val acc = AiraAccessibilityService.instance
                ?: return CommandResult(true, "Is kaam ke liye AIRA Accessibility on karein.")
            return if (acc.takeScreenshotAction()) {
                CommandResult(true, "Screenshot le liya hai.")
            } else {
                CommandResult(true, "Screenshot nahi le paayi.")
            }
        }
        if (has("lock") && tokens.size <= 4) {
            val acc = AiraAccessibilityService.instance
                ?: return CommandResult(true, "Is kaam ke liye AIRA Accessibility on karein.")
            return if (acc.lockScreen()) CommandResult(true, "Screen lock kar di hai.")
            else CommandResult(true, "Screen lock nahi ho paayi.")
        }
        if (has("recent", "recents") && tokens.size <= 4) {
            val acc = AiraAccessibilityService.instance
                ?: return CommandResult(true, "Is kaam ke liye AIRA Accessibility on karein.")
            acc.pressRecents()
            return CommandResult(true, "Recent apps khol di hain.")
        }
        if (has("quick") && has("settings", "setting")) {
            val acc = AiraAccessibilityService.instance
                ?: return CommandResult(true, "Is kaam ke liye AIRA Accessibility on karein.")
            acc.openQuickSettings()
            return CommandResult(true, "Quick settings khol di hain.")
        }
        if (has("notifications", "notification") && tokens.size <= 4) {
            val acc = AiraAccessibilityService.instance
                ?: return CommandResult(true, "Is kaam ke liye AIRA Accessibility on karein.")
            acc.openNotifications()
            return CommandResult(true, "Notifications khol di hain.")
        }

        // 12. Scroll / Back / Home / Click
        if (text.contains("scroll down") || text.contains("scroll niche") || text.contains("niche scroll")) {
            val acc = AiraAccessibilityService.instance
                ?: return CommandResult(true, "Screen control ke liye AIRA Accessibility on karein.")
            acc.scrollDown()
            return CommandResult(true, "Neeche scroll kar rahi hoon.")
        }
        if (text.contains("scroll up") || text.contains("scroll upar") || text.contains("upar scroll")) {
            val acc = AiraAccessibilityService.instance
                ?: return CommandResult(true, "Screen control ke liye AIRA Accessibility on karein.")
            acc.scrollUp()
            return CommandResult(true, "Upar scroll kar rahi hoon.")
        }
        if (text == "back" || text.contains("go back") || text.contains("piche jao") || text.contains("wapas jao")) {
            val acc = AiraAccessibilityService.instance
                ?: return CommandResult(true, "Screen control ke liye AIRA Accessibility on karein.")
            acc.pressBack()
            return CommandResult(true, "Peeche ja rahi hoon.")
        }
        if (text == "home" || text.contains("home screen") || text.contains("go home") || text.contains("home jao")) {
            val acc = AiraAccessibilityService.instance
                ?: return CommandResult(true, "Screen control ke liye AIRA Accessibility on karein.")
            acc.pressHome()
            return CommandResult(true, "Home screen par ja rahi hoon.")
        }
        if (text.startsWith("click ") || text.startsWith("tap ")) {
            val target = text.removePrefix("click ").removePrefix("tap ").trim()
            val acc = AiraAccessibilityService.instance
                ?: return CommandResult(true, "Screen control ke liye AIRA Accessibility on karein.")
            val clicked = acc.clickNodeByText(target)
            return if (clicked) CommandResult(true, "$target par click kar diya hai.")
            else CommandResult(true, "Screen par $target nahi mila.")
        }

        // 13. Settings shortcuts
        if (text.contains("wifi")) {
            openIntent(Intent(Settings.ACTION_WIFI_SETTINGS))
            return CommandResult(true, "Wi-Fi settings khol rahi hoon.")
        }
        if (text.contains("bluetooth")) {
            openIntent(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            return CommandResult(true, "Bluetooth settings khol rahi hoon.")
        }
        if (text.contains("settings") || text == "setting") {
            openIntent(Intent(Settings.ACTION_SETTINGS))
            return CommandResult(true, "Phone settings khol rahi hoon.")
        }

        // 14. Camera
        if (has("camera", "selfie", "photo")) {
            openIntent(Intent(MediaStore.ACTION_IMAGE_CAPTURE))
            return CommandResult(true, "Camera khol rahi hoon.")
        }

        // 15. Alarm list (bina time ke)
        if (has("alarm")) {
            openIntent(Intent(AlarmClock.ACTION_SHOW_ALARMS))
            return CommandResult(true, "Alarms khol rahi hoon. Time ke saath bolein, jaise 'subah 6 baje alarm'.")
        }

        // 16. YouTube search / play
        if (text.startsWith("youtube par ") || text.startsWith("play ")) {
            val query = text.removePrefix("youtube par ").removePrefix("play ").trim()
            if (query.isNotEmpty()) {
                val intent = Intent(Intent.ACTION_SEARCH).apply {
                    setPackage("com.google.android.youtube")
                    putExtra("query", query)
                }
                openIntent(intent)
                return CommandResult(true, "YouTube par $query dhoondh rahi hoon.")
            }
        }

        // 17. Apps kholna
        if (has("kholo", "open", "chalu")) {
            for ((name, pkg) in appPackages) {
                if (text.contains(name)) {
                    val launch = context.packageManager.getLaunchIntentForPackage(pkg)
                    if (launch != null) {
                        openIntent(launch)
                        return CommandResult(true, "$name khol rahi hoon.")
                    }
                    return CommandResult(true, "$name is phone mein install nahi hai.")
                }
            }
        }

        return notHandled
    }

    // ---------- Alarm / Timer / Reminder ----------

    private fun parseClock(tokens: List<String>): Pair<Int, Int>? {
        var hour: Int? = null
        var minute = 0
        var ampm: String? = null

        for ((i, tok) in tokens.withIndex()) {
            val m1 = clockRe.find(tok)
            val m2 = ampmRe.find(tok)
            if (m1 != null) {
                hour = m1.groupValues[1].toInt()
                minute = m1.groupValues[2].toInt()
                if (m1.groupValues[3].isNotEmpty()) ampm = m1.groupValues[3]
                break
            }
            if (m2 != null) {
                hour = m2.groupValues[1].toInt()
                ampm = m2.groupValues[2]
                break
            }
            if (tok == "baje" && i > 0) {
                val n = numOf(tokens[i - 1])
                if (n != null) {
                    hour = n
                    when (tokens.getOrNull(i - 2)) {
                        "saade" -> minute = 30
                        "sawa" -> minute = 15
                        "paune" -> {
                            hour = if (n == 1) 12 else n - 1
                            minute = 45
                        }
                    }
                    val afterNum = tokens.getOrNull(i + 1)?.let { numOf(it) }
                    if (afterNum != null && tokens.getOrNull(i + 2) == "minute") minute = afterNum
                    break
                }
            }
        }

        var h = hour ?: return null
        if (h > 23 || minute > 59) return null
        val set = tokens.toSet()
        when {
            ampm == "am" || "am" in set -> if (h == 12) h = 0
            ampm == "pm" || "pm" in set -> if (h < 12) h += 12
            "subah" in set || "morning" in set -> if (h == 12) h = 0
            "dopahar" in set || "afternoon" in set -> if (h in 1..6) h += 12
            "shaam" in set || "evening" in set -> if (h < 12) h += 12
            "raat" in set || "night" in set -> if (h in 6..11) h += 12 else if (h == 12) h = 0
        }
        return Pair(h % 24, minute)
    }

    private fun parseDurationSeconds(tokens: List<String>): Int {
        var total = 0
        for ((i, tok) in tokens.withIndex()) {
            val unit = when (tok) {
                "second", "seconds", "sec" -> 1
                "minute", "minutes", "min" -> 60
                "ghanta", "ghante", "hour", "hours", "hr" -> 3600
                else -> 0
            }
            if (unit == 0) continue
            val prev = tokens.getOrNull(i - 1) ?: continue
            if (prev == "aadha" || prev == "half") {
                total += unit / 2
                continue
            }
            val n = numOf(prev)
            if (n != null) total += n * unit
        }
        return total
    }

    private fun formatClock(hour: Int, minute: Int): String {
        val h12 = if (hour % 12 == 0) 12 else hour % 12
        val suffix = if (hour < 12) "AM" else "PM"
        val mm = if (minute < 10) "0$minute" else "$minute"
        return "$h12:$mm $suffix"
    }

    private fun setAlarm(hour: Int, minute: Int, label: String): CommandResult {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            CommandResult(true, "${formatClock(hour, minute)} ke liye alarm laga diya hai.")
        } catch (_: Exception) {
            CommandResult(true, "Alarm set nahi ho paaya.")
        }
    }

    private fun startTimer(seconds: Int, label: String): CommandResult {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val mins = seconds / 60
        val spoken = when {
            seconds >= 3600 -> "${seconds / 3600} ghante ${(seconds % 3600) / 60} minute"
            mins > 0 -> "$mins minute"
            else -> "$seconds second"
        }
        return try {
            context.startActivity(intent)
            CommandResult(true, "$spoken ka timer laga diya hai.")
        } catch (_: Exception) {
            CommandResult(true, "Timer set nahi ho paaya.")
        }
    }

    private fun setTimer(tokens: List<String>): CommandResult {
        val seconds = parseDurationSeconds(tokens)
        if (seconds <= 0) return CommandResult(true, "Kitne der ka timer lagana hai? Jaise '5 minute ka timer'.")
        return startTimer(seconds, "AIRA Timer")
    }

    private fun setReminder(tokens: List<String>): CommandResult {
        val label = tokens.filter { tok ->
            tok !in reminderStop && numOf(tok) == null && !clockRe.matches(tok) && !ampmRe.matches(tok)
        }.joinToString(" ").ifBlank { "Reminder" }

        val seconds = parseDurationSeconds(tokens)
        if (seconds > 0) {
            val r = startTimer(seconds, label)
            return CommandResult(true, "Theek hai, $label ke liye yaad dilaungi. ${r.spokenReply}")
        }
        val clock = parseClock(tokens)
            ?: return CommandResult(true, "Kitne baje yaad dilana hai? Jaise 'shaam 5 baje dawai yaad dilana'.")
        return setAlarm(clock.first, clock.second, label)
    }

    // ---------- Time / Date ----------

    private fun timeReply(): String {
        val cal = Calendar.getInstance()
        val h24 = cal.get(Calendar.HOUR_OF_DAY)
        val h12 = if (cal.get(Calendar.HOUR) == 0) 12 else cal.get(Calendar.HOUR)
        val minute = cal.get(Calendar.MINUTE)
        val part = when (h24) {
            in 0..3 -> "raat"
            in 4..11 -> "subah"
            in 12..15 -> "dopahar"
            in 16..18 -> "shaam"
            else -> "raat"
        }
        return if (minute == 0) "Abhi $part ke $h12 baje hain."
        else "Abhi $part ke $h12 bajkar $minute minute hue hain."
    }

    private fun dateReply(): String {
        val cal = Calendar.getInstance()
        val days = arrayOf("Ravivar", "Somvar", "Mangalvar", "Budhvar", "Guruvar", "Shukravar", "Shanivar")
        val months = arrayOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        )
        val day = days[cal.get(Calendar.DAY_OF_WEEK) - 1]
        return "Aaj $day, ${cal.get(Calendar.DAY_OF_MONTH)} ${months[cal.get(Calendar.MONTH)]} ${cal.get(Calendar.YEAR)} hai."
    }

    // ---------- Contacts: call / sms / whatsapp ----------

    private fun waNumber(raw: String): String {
        val d = raw.filter { it.isDigit() }
        return when {
            d.length == 10 -> "91$d"
            d.length == 11 && d.startsWith("0") -> "91" + d.substring(1)
            else -> d
        }
    }

    private fun handleContact(tokens: List<String>, whatsapp: Boolean, sms: Boolean): CommandResult {
        val cut = tokens.indexOfFirst { it == "ki" || it == "that" || it == "bolo" || it == "likho" }
        val head = if (cut >= 0) tokens.subList(0, cut) else tokens
        val body = if (cut >= 0) tokens.subList(cut + 1, tokens.size).joinToString(" ") else ""

        val ko = head.indexOf("ko")
        val base = if (ko > 0) head.subList(0, ko) else head
        val name = base.filter { it !in contactStop }.joinToString(" ")
        val digitsToken = head.firstOrNull { t -> t.length >= 8 && t.all { it.isDigit() } }

        val displayName: String
        val number: String
        if (digitsToken != null) {
            displayName = digitsToken
            number = digitsToken
        } else {
            if (name.isBlank()) {
                return CommandResult(
                    true,
                    if (whatsapp || sms) "Kisko message bhejna hai? Naam batayein." else "Kisko call karna hai? Naam batayein."
                )
            }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                return CommandResult(true, "Contacts ke liye AIRA app mein permission allow karein.")
            }
            val match = ContactFinder.find(context, name)
                ?: return CommandResult(true, "$name naam ka contact nahi mila.")
            displayName = match.name
            number = match.number
        }

        return try {
            when {
                whatsapp -> {
                    val url = "https://wa.me/${waNumber(number)}" +
                        if (body.isNotBlank()) "?text=${Uri.encode(body)}" else ""
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        setPackage("com.whatsapp")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    CommandResult(true, "$displayName ko WhatsApp message taiyaar hai, Send dabayein.")
                }
                sms -> {
                    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(number)}")).apply {
                        if (body.isNotBlank()) putExtra("sms_body", body)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    CommandResult(true, "$displayName ko message taiyaar hai, Send dabayein.")
                }
                else -> {
                    val canCall = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
                        PackageManager.PERMISSION_GRANTED
                    val action = if (canCall) Intent.ACTION_CALL else Intent.ACTION_DIAL
                    val intent = Intent(action, Uri.parse("tel:${Uri.encode(number)}")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    CommandResult(true, "$displayName ko call laga rahi hoon.")
                }
            }
        } catch (_: Exception) {
            CommandResult(true, "Ye kaam abhi nahi ho paaya.")
        }
    }

    // ---------- Navigation ----------

    private fun handleNavigation(tokens: List<String>, nearby: Boolean): CommandResult {
        val query = tokens.filter { it !in navStop }.joinToString(" ")
        if (query.isBlank()) return CommandResult(true, "Kahan jana hai? Jagah ka naam batayein.")

        val uri = if (nearby) Uri.parse("geo:0,0?q=${Uri.encode(query)}")
        else Uri.parse("google.navigation:q=${Uri.encode(query)}")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            CommandResult(true, if (nearby) "$query paas mein dhoondh rahi hoon." else "$query ka rasta khol rahi hoon.")
        } catch (_: Exception) {
            try {
                val web = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(query)}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(web)
                CommandResult(true, "Browser mein map khol rahi hoon.")
            } catch (_: Exception) {
                CommandResult(true, "Maps khul nahi paaya.")
            }
        }
    }

    // ---------- Low level helpers ----------

    private fun sendMediaKey(code: Int) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    private fun openIntent(intent: Intent) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    private fun getBatteryPercentage(): Int {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) (level * 100) / scale else 0
    }

    private fun setFlashlight(enabled: Boolean) {
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                hasFlash && facing == CameraCharacteristics.LENS_FACING_BACK
            } ?: return
            cameraManager.setTorchMode(cameraId, enabled)
        } catch (_: Exception) {
        }
    }
}
