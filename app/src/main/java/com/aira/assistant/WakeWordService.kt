package com.aira.assistant

import ai.picovoice.porcupine.Porcupine
import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * "Hey AIRA" bolne par jaagne wali hands-free service.
 * Porcupine wake word sunta rehta hai -> jaagte hi mic SpeechRecognizer ko deta hai ->
 * command AiraEngine se chalta hai -> wapas wake word sunne lagta hai.
 */
class WakeWordService : Service() {

    companion object {
        // console.picovoice.ai se free AccessKey bana ke yahan paste karein
        const val ACCESS_KEY = ""

        // Console se train ki hui "Hey AIRA" file: app/src/main/assets/hey_aira.ppn
        const val KEYWORD_ASSET = "hey_aira.ppn"

        @Volatile
        var running = false
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var windowManager: WindowManager
    private var porcupine: PorcupineManager? = null
    private var recognizer: SpeechRecognizer? = null
    private var overlay: TextView? = null
    private var busy = false

    private val timeoutRunnable = Runnable {
        recognizer?.cancel()
        hideOverlay()
        resumeWake(300)
    }

    override fun onCreate() {
        super.onCreate()
        running = true
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val hasMic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        startForegroundSafely(hasMic)

        if (!hasMic) {
            toast("Mic permission allow karein.")
            stopSelf()
            return
        }
        if (ACCESS_KEY.isBlank()) {
            toast("WakeWordService.kt mein Picovoice AccessKey daalein.")
            stopSelf()
            return
        }
        setupPorcupine()
    }

    private fun setupPorcupine() {
        try {
            val builder = PorcupineManager.Builder()
                .setAccessKey(ACCESS_KEY)
                .setSensitivity(0.7f)

            val hasCustom = assets.list("")?.contains(KEYWORD_ASSET) == true
            if (hasCustom) {
                builder.setKeywordPath(KEYWORD_ASSET)
            } else {
                builder.setKeyword(Porcupine.BuiltInKeyword.JARVIS)
                toast("hey_aira.ppn nahi mili, abhi 'Jarvis' bolne se jaagegi.")
            }

            porcupine = builder.build(applicationContext, PorcupineManagerCallback { _ ->
                handler.post { onWake() }
            })
            porcupine?.start()
        } catch (e: Exception) {
            toast("Wake word start nahi hua: ${e.message}")
            stopSelf()
        }
    }

    private fun onWake() {
        if (busy) return
        busy = true
        try {
            porcupine?.stop()
        } catch (_: Exception) {
        }
        beep()
        showOverlay("AIRA sun rahi hai...")
        handler.postDelayed({ startRecognition() }, 250)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onResults(results: Bundle?) {
            handler.removeCallbacks(timeoutRunnable)
            val spoken = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            hideOverlay()
            if (spoken.isBlank()) {
                resumeWake(500)
            } else {
                AiraEngine.run(this@WakeWordService, spoken, 300L)
                resumeWake(2500)
            }
        }

        override fun onError(error: Int) {
            handler.removeCallbacks(timeoutRunnable)
            showOverlay("Sunai nahi diya.")
            handler.postDelayed({ hideOverlay() }, 900)
            resumeWake(1000)
        }
    }

    private fun startRecognition() {
        try {
            recognizer?.destroy()
            recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(listener)
            }
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            }
            recognizer?.startListening(intent)
            handler.postDelayed(timeoutRunnable, 10000)
        } catch (_: Exception) {
            hideOverlay()
            resumeWake(500)
        }
    }

    private fun resumeWake(delayMs: Long) {
        handler.postDelayed({
            busy = false
            try {
                porcupine?.start()
            } catch (_: Exception) {
            }
        }, delayMs)
    }

    private fun beep() {
        try {
            ToneGenerator(AudioManager.STREAM_MUSIC, 80).startTone(ToneGenerator.TONE_PROP_BEEP, 150)
        } catch (_: Exception) {
        }
    }

    private fun showOverlay(message: String) {
        try {
            if (overlay == null) {
                val view = TextView(this).apply {
                    setTextColor(Color.WHITE)
                    textSize = 16f
                    gravity = Gravity.CENTER
                    setPadding(48, 28, 48, 28)
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#E61B1B1F"))
                        cornerRadius = 80f
                    }
                }
                val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                }
                val lp = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    type,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    y = 160
                }
                windowManager.addView(view, lp)
                overlay = view
            }
            overlay?.text = message
        } catch (_: Exception) {
            // Overlay permission na ho toh bina panel ke chalta rahega
        }
    }

    private fun hideOverlay() {
        overlay?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }
        overlay = null
    }

    private fun startForegroundSafely(hasMic: Boolean) {
        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && hasMic) {
                startForeground(1002, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(1002, notification)
            }
        } catch (_: Exception) {
            try {
                startForeground(1002, notification)
            } catch (_: Exception) {
            }
        }
    }

    private fun buildNotification(): Notification {
        val channelId = "aira_wake_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "AIRA Hey AIRA",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("AIRA sun rahi hai")
            .setContentText("\"Hey AIRA\" bolkar jagayein")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }

    private fun toast(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
        try {
            porcupine?.stop()
            porcupine?.delete()
        } catch (_: Exception) {
        }
        porcupine = null
        recognizer?.destroy()
        recognizer = null
        hideOverlay()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
