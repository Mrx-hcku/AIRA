package com.aira.assistant

import android.content.Context
import android.provider.ContactsContract
import java.text.Normalizer

/**
 * Contact ka naam dhoondhta hai. Hindi (Devanagari) naam ko roman mein badalke
 * English naam wale contacts se approximate match karta hai ("राहुल" -> "Rahul").
 */
object ContactFinder {

    data class Match(val name: String, val number: String)

    private const val VIRAMA = '\u094D'

    private val vowelsIndep = mapOf(
        'अ' to "a", 'आ' to "a", 'इ' to "i", 'ई' to "i", 'उ' to "u", 'ऊ' to "u",
        'ए' to "e", 'ऐ' to "ai", 'ओ' to "o", 'औ' to "au", 'ऑ' to "o"
    )
    private val matras = mapOf(
        'ा' to "a", 'ि' to "i", 'ी' to "i", 'ु' to "u", 'ू' to "u", 'े' to "e",
        'ै' to "ai", 'ो' to "o", 'ौ' to "au", 'ॉ' to "o", 'ं' to "n", 'ँ' to "n", 'ः' to "h"
    )
    private val consonants = mapOf(
        'क' to "k", 'ख' to "kh", 'ग' to "g", 'घ' to "gh", 'च' to "ch", 'छ' to "chh",
        'ज' to "j", 'झ' to "jh", 'ट' to "t", 'ठ' to "th", 'ड' to "d", 'ढ' to "dh",
        'ण' to "n", 'त' to "t", 'थ' to "th", 'द' to "d", 'ध' to "dh", 'न' to "n",
        'प' to "p", 'फ' to "f", 'ब' to "b", 'भ' to "bh", 'म' to "m", 'य' to "y",
        'र' to "r", 'ल' to "l", 'व' to "v", 'श' to "sh", 'ष' to "sh", 'स' to "s", 'ह' to "h"
    )

    private fun transliterate(input: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < input.length) {
            val c = input[i]
            val cons = consonants[c]
            if (cons != null) {
                sb.append(cons)
                val next = input.getOrNull(i + 1)
                if (next == VIRAMA) {
                    i++
                } else if (next != null && (consonants.containsKey(next) || vowelsIndep.containsKey(next))) {
                    sb.append("a")
                }
            } else if (matras.containsKey(c)) {
                sb.append(matras[c])
            } else if (vowelsIndep.containsKey(c)) {
                sb.append(vowelsIndep[c])
            } else if (c != VIRAMA) {
                sb.append(c)
            }
            i++
        }
        return sb.toString()
    }

    private fun canon(s: String): String {
        val d = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace("\u093C", "")
        return transliterate(d).filter { it.isLetterOrDigit() || it == ' ' }.trim()
    }

    private fun skeleton(s: String): String = s.filter { it !in "aeiouy " }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            prev = cur
        }
        return prev[b.length]
    }

    /** Kam score = behtar match. null = match nahi. */
    private fun score(queryNo: String, contactName: String): Int? {
        val full = canon(contactName)
        val no = full.replace(" ", "")
        if (no.isEmpty()) return null
        if (no == queryNo) return 0
        if (no.contains(queryNo) || (no.length >= 3 && queryNo.contains(no))) return 1
        val tokens = full.split(" ").filter { it.isNotBlank() }
        var d = levenshtein(queryNo, no)
        for (t in tokens) d = minOf(d, levenshtein(queryNo, t))
        val limit = maxOf(1, queryNo.length / 3)
        if (d <= limit) return 2 + d
        val qs = skeleton(queryNo)
        if (qs.length >= 2 && (qs == skeleton(no) || tokens.any { skeleton(it) == qs })) return 6
        return null
    }

    fun find(context: Context, query: String): Match? {
        val queryNo = canon(query).replace(" ", "")
        if (queryNo.isEmpty()) return null
        var best: Match? = null
        var bestScore = Int.MAX_VALUE
        try {
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null, null, null
            )
            cursor?.use {
                while (it.moveToNext()) {
                    val name = it.getString(0) ?: continue
                    val number = it.getString(1) ?: continue
                    val sc = score(queryNo, name) ?: continue
                    if (sc < bestScore) {
                        bestScore = sc
                        best = Match(name, number)
                    }
                }
            }
        } catch (_: Exception) {
            return null
        }
        return best
    }
}
