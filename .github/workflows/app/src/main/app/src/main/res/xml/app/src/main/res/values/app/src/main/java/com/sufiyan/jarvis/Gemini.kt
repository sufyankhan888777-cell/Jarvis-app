package com.sufiyan.jarvis

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Gemini {
    private val models = listOf("gemini-3.5-flash-lite", "gemini-3.8-flash")

    private const val SYS = "Tum ek Android phone control agent ho. User ka task screen dekh kar ek ek step karke poora karo. " +
        "Har baar sirf ek JSON do: {\"action\":\"click|type|scroll|back|home|open_app|ask|wait|done\",\"index\":N,\"text\":\"...\",\"app\":\"...\",\"dir\":\"down|up\",\"say\":\"...\"}. " +
        "index screen list ke [number] se lo. type ke liye edit field ka index aur text do. open_app mein app ka naam do. " +
        "Message, email bhejne, delete karne ya koi bhi na-wapas-hone-wala kaam karne se PEHLE action ask se user se pakka confirm lo (say mein chhota sawal). " +
        "Paise, payment, banking, password ya OTP ka kaam kabhi mat karo, done bolke mana karo. " +
        "Task poora ho jaye to done aur say mein 1 chhota Hinglish vaakya. Atak jao to done bolke batao."

    fun step(key: String, task: String, hist: List<String>, screen: String): JSONObject? {
        val user = "Task: $task\nPichle steps:\n${hist.takeLast(8).joinToString("\n")}\n\nScreen:\n$screen"
        for (m in models) for (think in listOf(true, false)) {
            val cfg = JSONObject().put("responseMimeType", "application/json").put("maxOutputTokens", 250)
            if (think) cfg.put("thinkingConfig", JSONObject().put("thinkingLevel", "low"))
            val body = JSONObject()
                .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", SYS))))
                .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", user)))))
                .put("generationConfig", cfg)
            val out = post("https://generativelanguage.googleapis.com/v1beta/models/$m:generateContent?key=$key", body.toString()) ?: continue
            try {
                val t = JSONObject(out).getJSONArray("candidates").getJSONObject(0)
                    .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
                return JSONObject(t)
            } catch (e: Exception) { }
        }
        return null
    }

    private fun post(u: String, b: String): String? = try {
        val c = URL(u).openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.connectTimeout = 15000; c.readTimeout = 25000; c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        c.outputStream.use { it.write(b.toByteArray()) }
        if (c.responseCode == 200) c.inputStream.bufferedReader().readText() else null
    } catch (e: Exception) { null }
}
