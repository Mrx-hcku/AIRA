package com.aira.assistant

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Activity ke bahar command chalane wala common engine.
 * Local command pehle, nahi mili toh online AI. Jawab animated orb + awaaz mein.
 */
object AiraEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private fun ensureTts(app: Context) {
        if (tts != null) return
        tts = TextToSpeech(app) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("hi", "IN")
                tts?.setSpeechRate(1.05f)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        AiraOverlay.hide(500)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        AiraOverlay.hide(500)
                    }
                })
                ttsReady = true
            }
        }
    }

    fun run(context: Context, spoken: String, delayMs: Long = 0L) {
        val app = context.applicationContext
        ensureTts(app)
        Handler(Looper.getMainLooper()).postDelayed({
            val local = LocalCommandRouter(app).tryExecuteLocal(spoken)
            val prompt = local.aiPrompt
            if (prompt != null) {
                askAndSay(app, prompt, false)
            } else if (local.handledLocally) {
                say(app, local.spokenReply)
            } else {
                askAndSay(app, spoken, true)
            }
        }, delayMs)
    }

    private fun askAndSay(app: Context, prompt: String, remember: Boolean) {
        AiraOverlay.show(app, "AIRA soch rahi hai...", 20000L)
        scope.launch {
            val reply = AiBrainClient.askAi(prompt, remember)
            say(app, reply)
        }
    }

    private fun say(app: Context, message: String) {
        if (message.isBlank()) {
            AiraOverlay.hide(0)
            return
        }
        if (Settings.canDrawOverlays(app)) {
            val duration = if (ttsReady) 4000L + message.length * 110L else 2500L + message.length * 80L
            AiraOverlay.show(app, message, duration)
        } else {
            Toast.makeText(app, message, Toast.LENGTH_LONG).show()
        }
        if (ttsReady) {
            tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "AIRA_UTTERANCE")
        }
    }
}
