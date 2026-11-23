package com.aira.assistant

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Locale

class FloatingBubbleService : Service(), TextToSpeech.OnInitListener {

    private lateinit var windowManager: WindowManager
    private lateinit var bubbleView: TextView
    private lateinit var bubbleBg: GradientDrawable
    private lateinit var router: LocalCommandRouter

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var listening = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var params: WindowManager.LayoutParams
    private val handler = Handler(Looper.getMainLooper())
    private val bubbleSize = 140
    private val screenWidth get() = resources.displayMetrics.widthPixels

    private val dimRunnable = Runnable { if (!listening) hideToEdge() }
    private val longPressRunnable = Runnable {
        toast("AIRA bubble band kar diya.")
        stopSelf()
    }

    private fun showBubble() {
        handler.removeCallbacks(dimRunnable)
        bubbleView.alpha = 1f
    }

    private fun scheduleDim() {
        handler.removeCallbacks(dimRunnable)
        handler.postDelayed(dimRunnable, 3000)
    }

    private fun snapToEdge() {
        val left = params.x + bubbleSize / 2 < screenWidth / 2
        params.x = if (left) 0 else screenWidth - bubbleSize
        updateBubble()
    }

    private fun hideToEdge() {
        val left = params.x + bubbleSize / 2 < screenWidth / 2
        params.x = if (left) -bubbleSize / 2 else screenWidth - bubbleSize / 2
        bubbleView.alpha = 0.4f
        updateBubble()
    }

    private fun updateBubble() {
        try {
            windowManager.updateViewLayout(bubbleView, params)
        } catch (_: Exception) {
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()

        router = LocalCommandRouter(this)
        tts = TextToSpeech(this, this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        bubbleBg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#1E88E5"))
            setStroke(4, Color.WHITE)
        }

        bubbleView = TextView(this).apply {
            text = "AIRA"
            setTextColor(Color.WHITE)
            textSize = 13f
            gravity = Gravity.CENTER
            background = bubbleBg
            setPadding(24, 24, 24, 24)
        }

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            bubbleSize,
            bubbleSize,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 300
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        var moved = false

        bubbleView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    showBubble()
                    moved = false
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    handler.postDelayed(longPressRunnable, 1500)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = kotlin.math.abs(event.rawX - initialTouchX)
                    val dy = kotlin.math.abs(event.rawY - initialTouchY)
                    if (dx > 12 || dy > 12) {
                        moved = true
                        handler.removeCallbacks(longPressRunnable)
                    }
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    updateBubble()
                    true
                }
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(longPressRunnable)
                    if (moved) {
                        snapToEdge()
                    } else {
                        // Tap: bubble ko poora bahar laao aur sunna shuru karo
                        snapToEdge()
                        startListening()
                    }
                    if (!listening) scheduleDim()
                    true
                }
                else -> false
            }
        }

        windowManager.addView(bubbleView, params)
        scheduleDim()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("hi", "IN")
            tts?.setSpeechRate(1.05f)
            ttsReady = true
        }
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onResults(results: Bundle?) {
            setListeningState(false)
            val spoken = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            if (spoken.isBlank()) {
                toast("Kuch sunai nahi diya, phir try karein.")
            } else {
                handleCommand(spoken)
            }
        }

        override fun onError(error: Int) {
            setListeningState(false)
            if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                openApp()
            } else {
                toast("Sunai nahi diya, phir try karein.")
            }
        }
    }

    private fun startListening() {
        if (listening) return

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            toast("Mic permission allow karein.")
            openApp()
            return
        }

        try {
            if (recognizer == null) {
                recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                    setRecognitionListener(recognitionListener)
                }
            }
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            }
            setListeningState(true)
            recognizer?.startListening(intent)
        } catch (_: Exception) {
            setListeningState(false)
            toast("Speech service available nahi hai.")
        }
    }

    private fun handleCommand(spoken: String) {
        AiraEngine.run(applicationContext, spoken, 150L)
    }

    private fun setListeningState(on: Boolean) {
        listening = on
        if (on) showBubble() else scheduleDim()
        bubbleBg.setColor(Color.parseColor(if (on) "#E53935" else "#1E88E5"))
        bubbleView.text = if (on) "Sun..." else "AIRA"
    }

    private fun speak(message: String) {
        if (ttsReady) {
            tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "AIRA_UTTERANCE")
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun openApp() {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(launchIntent)
    }

    private fun startForegroundNotification() {
        val channelId = "aira_bubble_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "AIRA Assistant Bubble",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("AIRA Active")
            .setContentText("Floating trigger bubble chal raha hai")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()

        startForeground(1001, notification)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
        tts?.stop()
        tts?.shutdown()
        scope.cancel()
        if (::bubbleView.isInitialized) {
            windowManager.removeView(bubbleView)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
