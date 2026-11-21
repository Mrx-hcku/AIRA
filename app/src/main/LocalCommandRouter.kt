package com.aira.assistant

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings

data class CommandResult(
    val handledLocally: Boolean,
    val spokenReply: String
)

class LocalCommandRouter(private val context: Context) {

    private val appPackages = mapOf(
        "whatsapp" to "com.whatsapp",
        "youtube" to "com.google.android.youtube",
        "chrome" to "com.android.chrome",
        "instagram" to "com.instagram.android",
        "telegram" to "org.telegram.messenger",
        "maps" to "com.google.android.apps.maps",
        "play store" to "com.android.vending"
    )

    fun tryExecuteLocal(rawText: String): CommandResult {
        val text = rawText.lowercase().trim()

        // 1. Flashlight / Torch
        if (text.contains("flashlight") || text.contains("torch") || text.contains("light")) {
            return if (text.contains("off") || text.contains("band")) {
                setFlashlight(false)
                CommandResult(true, "Flashlight band kar di hai.")
            } else {
                setFlashlight(true)
                CommandResult(true, "Flashlight on kar di hai.")
            }
        }

        // 2. Volume & Sound Controls
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (text.contains("volume") || text.contains("awaz") || text.contains("sound")) {
            return when {
                text.contains("full") || text.contains("max") -> {
                    val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVol, AudioManager.FLAG_SHOW_UI)
                    CommandResult(true, "Volume full kar diya hai.")
                }
                text.contains("up") || text.contains("badha") || text.contains("zyada") -> {
                    audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                    CommandResult(true, "Volume badha diya hai.")
                }
                text.contains("down") || text.contains("kam") -> {
                    audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                    CommandResult(true, "Volume kam kar diya hai.")
                }
                text.contains("silent") || text.contains("mute") -> {
                    try {
                        audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
                        CommandResult(true, "Phone silent mode par kar diya hai.")
                    } catch (_: Exception) {
                        CommandResult(true, "Silent mode ke liye Do Not Disturb permission chahiye.")
                    }
                }
                text.contains("normal") || text.contains("ring") -> {
                    audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                    CommandResult(true, "Phone normal ring mode par kar diya hai.")
                }
                else -> CommandResult(false, "")
            }
        }

        // 3. Battery Status
        if (text.contains("battery") || text.contains("charge")) {
            val pct = getBatteryPercentage()
            return CommandResult(true, "Abhi battery $pct percent hai.")
        }

        // 4. Quick Settings Shortcuts (Wi-Fi, Bluetooth, Hotspot, Settings)
        if (text.contains("wifi") || text.contains("wi-fi")) {
            openIntent(Intent(Settings.ACTION_WIFI_SETTINGS))
            return CommandResult(true, "Wi-Fi settings khol rahi hoon.")
        }
        if (text.contains("bluetooth")) {
            openIntent(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            return CommandResult(true, "Bluetooth settings khol rahi hoon.")
        }
        if (text.contains("settings") || text.contains("setting")) {
            openIntent(Intent(Settings.ACTION_SETTINGS))
            return CommandResult(true, "Phone settings khol rahi hoon.")
        }

        // 5. Camera
        if (text.contains("camera") || text.contains("photo") || text.contains("selfie")) {
            openIntent(Intent(MediaStore.ACTION_IMAGE_CAPTURE))
            return CommandResult(true, "Camera khol rahi hoon.")
        }

        // 6. Alarm / Timer
        if (text.contains("alarm")) {
            openIntent(Intent(AlarmClock.ACTION_SHOW_ALARMS))
            return CommandResult(true, "Alarms khol rahi hoon.")
        }

        // 7. Screen Scroll & Back via Accessibility Service
        val acc = AiraAccessibilityService.instance
        if (text.contains("scroll down") || text.contains("niche scroll")) {
            return if (acc != null) {
                acc.scrollDown()
                CommandResult(true, "Niche scroll kar diya hai.")
            } else {
                CommandResult(true, "Scroll ke liye AIRA Accessibility on karein.")
            }
        }
        if (text.contains("scroll up") || text.contains("upar scroll")) {
            return if (acc != null) {
                acc.scrollUp()
                CommandResult(true, "Upar scroll kar diya hai.")
            } else {
                CommandResult(true, "Scroll ke liye AIRA Accessibility on karein.")
            }
        }
        if (text.contains("go back") || text.contains("back karo") || text.contains("piche jao")) {
            return if (acc != null) {
                acc.pressBack()
                CommandResult(true, "Back kar diya hai.")
            } else {
                CommandResult(true, "Back action ke liye AIRA Accessibility on karein.")
            }
        }
        if (text.contains("home screen") || text.contains("home jao")) {
            return if (acc != null) {
                acc.pressHome()
                CommandResult(true, "Home screen par aa gaye hain.")
            } else {
                CommandResult(true, "Home action ke liye AIRA Accessibility on karein.")
            }
        }
        if (text.startsWith("click ") || text.startsWith("tap ") || text.startsWith("dabao ")) {
            val target = text.removePrefix("click ").removePrefix("tap ").removePrefix("dabao ").trim()
            return if (acc != null && target.isNotBlank()) {
                val clicked = acc.clickNodeByText(target)
                if (clicked) CommandResult(true, "$target par click kar diya hai.")
                else CommandResult(true, "Screen par $target nahi mila.")
            } else {
                CommandResult(true, "Screen click ke liye AIRA Accessibility on karein.")
            }
        }

        // 8. YouTube / Google Search
        if (text.startsWith("youtube par ") || text.startsWith("play ")) {
            val query = text.removePrefix("youtube par ").removePrefix("play ").trim()
            if (query.isNotBlank()) {
                val searchIntent = Intent(Intent.ACTION_SEARCH).apply {
                    setPackage("com.google.android.youtube")
                    putExtra("query", query)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                return try {
                    context.startActivity(searchIntent)
                    CommandResult(true, "YouTube par $query search kar rahi hoon.")
                } catch (_: Exception) {
                    val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}"))
                    openIntent(webIntent)
                    CommandResult(true, "Browser mein YouTube par $query khol rahi hoon.")
                }
            }
        }

        // 9. Open Installed Apps
        if (text.contains("kholo") || text.contains("open") || text.contains("chalu")) {
            for ((keyword, pkg) in appPackages) {
                if (text.contains(keyword)) {
                    val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
                    return if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(launchIntent)
                        CommandResult(true, "$keyword khol rahi hoon.")
                    } else {
                        CommandResult(true, "$keyword is phone mein install nahi mila.")
                    }
                }
            }
        }

        return CommandResult(false, "")
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
