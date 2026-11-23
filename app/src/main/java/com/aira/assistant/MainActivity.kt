package com.aira.assistant

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var statusText: TextView
    private lateinit var commandText: TextView
    private lateinit var speakButton: Button
    private lateinit var startBubbleButton: Button
    private lateinit var wakeButton: Button
    private lateinit var openAccessibilityButton: Button

    private lateinit var localRouter: LocalCommandRouter
    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    private val speechLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                val spokenText = matches?.firstOrNull().orEmpty()
                if (spokenText.isNotBlank()) {
                    commandText.text = "Aapne kaha: \"$spokenText\""
                    handleCommand(spokenText)
                } else {
                    statusText.text = "AIRA: Kuch sunai nahi diya, phir try karein."
                }
            } else {
                statusText.text = "AIRA ready hai."
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        commandText = findViewById(R.id.commandText)
        speakButton = findViewById(R.id.speakButton)
        startBubbleButton = findViewById(R.id.startBubbleButton)
        wakeButton = findViewById(R.id.wakeButton)
        openAccessibilityButton = findViewById(R.id.openAccessibilityButton)

        localRouter = LocalCommandRouter(this)
        tts = TextToSpeech(this, this)

        requestNeededPermissions()

        speakButton.setOnClickListener {
            startVoiceListening()
        }

        startBubbleButton.setOnClickListener {
            enableFloatingBubble()
        }

        wakeButton.setOnClickListener {
            toggleWakeWord()
        }

        openAccessibilityButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        if (intent?.getBooleanExtra("AUTO_LISTEN", false) == true) {
            startVoiceListening()
        }
    }

    override fun onResume() {
        super.onResume()
        updateWakeButton()
    }

    private fun toggleWakeWord() {
        val serviceIntent = Intent(this, WakeWordService::class.java)
        if (WakeWordService.running) {
            stopService(serviceIntent)
            wakeButton.text = "Hey AIRA (Hands-free) On Karein"
            statusText.text = "Hey AIRA band ho gaya."
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            statusText.text = "Pehle mic permission allow karein."
            requestNeededPermissions()
            return
        }
        ContextCompat.startForegroundService(this, serviceIntent)
        wakeButton.text = "Hey AIRA Band Karein"
        statusText.text = "\"Hey AIRA\" bolkar jagayein..."
    }

    private fun updateWakeButton() {
        wakeButton.text = if (WakeWordService.running) "Hey AIRA Band Karein" else "Hey AIRA (Hands-free) On Karein"
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra("AUTO_LISTEN", false)) {
            startVoiceListening()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("hi", "IN")
            tts?.setSpeechRate(1.05f)
            isTtsReady = true
            statusText.text = "AIRA ready hai."
        }
    }

    private fun startVoiceListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "AIRA sun rahi hai...")
        }
        statusText.text = "AIRA sun rahi hai..."
        try {
            speechLauncher.launch(intent)
        } catch (_: Exception) {
            statusText.text = "Speech service available nahi hai."
        }
    }

    private fun handleCommand(spokenText: String) {
        // 1. Pehle offline local command
        val localResult = localRouter.tryExecuteLocal(spokenText)
        if (localResult.handledLocally && localResult.aiPrompt == null) {
            statusText.text = "AIRA: ${localResult.spokenReply}"
            speakOut(localResult.spokenReply)
            return
        }

        // 2. Online AI (nahi mili, ya screen padhne jaisa AI wala kaam)
        statusText.text = "AIRA soch rahi hai..."
        val prompt = localResult.aiPrompt ?: spokenText
        val remember = localResult.aiPrompt == null
        lifecycleScope.launch {
            val reply = AiBrainClient.askAi(prompt, remember)
            statusText.text = "AIRA: $reply"
            speakOut(reply)
        }
    }

    private fun enableFloatingBubble() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            statusText.text = "Pehle 'Display over other apps' permission allow karein."
            return
        }

        val serviceIntent = Intent(this, FloatingBubbleService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        statusText.text = "AIRA Floating Bubble active ho gaya hai!"
    }

    private fun speakOut(message: String) {
        if (isTtsReady) {
            tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "AIRA_UTTERANCE")
        }
    }

    private fun requestNeededPermissions() {
        val wanted = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE
        )
        if (Build.VERSION.SDK_INT >= 33) wanted.add(Manifest.permission.POST_NOTIFICATIONS)
        val needed = wanted.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), 101)
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
