package com.aira.assistant

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

/**
 * Siri jaisa assistant panel: power button long-press / home gesture se khulta hai,
 * neeche chhota panel aata hai, turant sunna shuru, command ke baad khud band.
 */
class AssistActivity : Activity() {

    private var recognizer: SpeechRecognizer? = null
    private lateinit var statusView: TextView
    private lateinit var heardView: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var done = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.setGravity(Gravity.BOTTOM)
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)

        setContentView(buildPanel())
        startListening()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun buildPanel(): LinearLayout {
        val orb = TextView(this).apply {
            text = "AIRA"
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#1E88E5"))
                setStroke(dp(2), Color.WHITE)
            }
            layoutParams = LinearLayout.LayoutParams(dp(76), dp(76))
        }

        statusView = TextView(this).apply {
            text = "AIRA sun rahi hai..."
            setTextColor(Color.WHITE)
            textSize = 17f
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, 0)
        }

        heardView = TextView(this).apply {
            setTextColor(Color.parseColor("#B0B0B8"))
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, 0)
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(24), dp(24), dp(32))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F01B1B1F"))
                cornerRadii = floatArrayOf(
                    dp(28).toFloat(), dp(28).toFloat(), dp(28).toFloat(), dp(28).toFloat(),
                    0f, 0f, 0f, 0f
                )
            }
            addView(orb)
            addView(statusView)
            addView(heardView)
            setOnClickListener { finishPanel() }
        }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {
            statusView.text = "Sun rahi hoon..."
        }
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onResults(results: Bundle?) {
            val spoken = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            if (spoken.isBlank()) {
                fail("Kuch sunai nahi diya.")
                return
            }
            heardView.text = "\"$spoken\""
            statusView.text = "Ho gaya..."
            // Panel hatne ke baad command chalao, taaki accessibility actions neeche wali app par lagein
            AiraEngine.run(this@AssistActivity, spoken, 450L)
            handler.postDelayed({ finishPanel() }, 250)
        }

        override fun onError(error: Int) {
            if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                startActivity(Intent(this@AssistActivity, MainActivity::class.java))
                finishPanel()
            } else {
                fail("Sunai nahi diya, phir bolein.")
            }
        }
    }

    private fun startListening() {
        try {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(listener)
            }
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            }
            recognizer?.startListening(intent)
        } catch (_: Exception) {
            fail("Speech service available nahi hai.")
        }
    }

    private fun fail(message: String) {
        statusView.text = message
        handler.postDelayed({ finishPanel() }, 1200)
    }

    private fun finishPanel() {
        if (done) return
        done = true
        finish()
        overridePendingTransition(0, 0)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
        super.onDestroy()
    }
}
