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
    const val API_KEY = ""
    const val API_URL = "https://api.groq.com/openai/v1/chat/completions"
    const val MODEL_NAME = "llama-3.1-8b-instant"

    private const val SYSTEM_PROMPT =
        "Tum AIRA ho, ek smart, confident aur fast personal voice assistant. " +
            "User ke sawaalon ka jawab hamesha 1 se 2 short, crisp, natural Hinglish sentences mein do."

    suspend fun askAi(userPrompt: String): String = withContext(Dispatchers.IO) {
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

            val messages = JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
                put(JSONObject().put("role", "user").put("content", userPrompt))
            }

            val body = JSONObject().apply {
                put("model", MODEL_NAME)
                put("messages", messages)
                put("max_tokens", 120)
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
                return@withContext content.trim()
            } else {
                return@withContext "AI server se connect nahi ho paya (${conn.responseCode})."
            }
        } catch (_: Exception) {
            return@withContext "Internet connection check karein, abhi online brain reachable nahi hai."
        }
    }
}
