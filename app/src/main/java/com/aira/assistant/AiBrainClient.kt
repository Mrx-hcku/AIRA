package com.aira.assistant

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object AiBrainClient {

    // Apni Groq ya OpenAI-compatible API key yahan daal sakte hain (optional for offline mode)
    const val API_KEY = "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"
    const val API_URL = "https://api.groq.com/openai/v1/chat/completions"
    const val MODEL_NAME = "openai/gpt-oss-20b"

    private const val SYSTEM_PROMPT =
        "Tum AIRA ho, ek smart, confident aur fast personal voice assistant. " +
            "User ke sawaalon ka jawab hamesha 1 se 2 short, crisp, natural Hinglish sentences mein do."

    private const val MAX_HISTORY = 8
    private const val RESET_AFTER_MS = 10 * 60 * 1000L
    private val history = ArrayList<Pair<String, String>>()
    private var lastUsed = 0L

    fun clearHistory() {
        synchronized(history) { history.clear() }
    }

    suspend fun askAi(userPrompt: String, remember: Boolean = true): String = withContext(Dispatchers.IO) {
        if (API_KEY.isBlank()) {
            return@withContext "Yeh offline local command nahi thi. Online AI jawab ke liye API key set karein."
        }

        try {
            val url = URL(API_URL)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Authorization", "Bearer $API_KEY")
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = 10000
                readTimeout = 15000
                doOutput = true
            }

            val past = synchronized(history) {
                if (System.currentTimeMillis() - lastUsed > RESET_AFTER_MS) history.clear()
                history.toList()
            }
            val messages = JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
                for ((role, content) in past) {
                    put(JSONObject().put("role", role).put("content", content))
                }
                put(JSONObject().put("role", "user").put("content", userPrompt))
            }

            val body = JSONObject().apply {
                put("model", MODEL_NAME)
                put("messages", messages)
                put("max_tokens", 500)
                put("reasoning_effort", "low")
                put("temperature", 0.6)
            }

            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }

            if (conn.responseCode in 200..299) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseText)
                val content = json.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                val reply = content.trim().ifBlank { "Jawab nahi mila, phir try karein." }
                if (remember) {
                    synchronized(history) {
                        history.add("user" to userPrompt)
                        history.add("assistant" to reply)
                        while (history.size > MAX_HISTORY) history.removeAt(0)
                        lastUsed = System.currentTimeMillis()
                    }
                }
                return@withContext reply
            } else {
                return@withContext "AI server se connect nahi ho paya (${conn.responseCode})."
            }
        } catch (_: Exception) {
            return@withContext "Internet connection check karein, abhi online brain reachable nahi hai."
        }
    }
}
