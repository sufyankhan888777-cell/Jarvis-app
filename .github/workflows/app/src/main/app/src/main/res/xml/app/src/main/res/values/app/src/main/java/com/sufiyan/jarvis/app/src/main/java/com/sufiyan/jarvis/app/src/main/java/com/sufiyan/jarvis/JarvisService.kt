package com.sufiyan.jarvis

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class JarvisService : AccessibilityService() {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var wm: WindowManager
    private var btn: TextView? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var speakLatch: CountDownLatch? = null
    private var recognizer: SpeechRecognizer? = null
    private val heard = LinkedBlockingQueue<String>()
    private var nodes: List<AccessibilityNodeInfo> = emptyList()
    @Volatile private var running = false
    @Volatile private var stopFlag = false
    private val blocked = listOf("paisa", "phonepe", "paytm", "bank", "upi", "wallet", "bhim")

    override fun onServiceConnected() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        tts = TextToSpeech(this) { st ->
            if (st == TextToSpeech.SUCCESS) {
                ttsReady = true
                tts?.language = Locale("en", "IN")
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {}
                    override fun onDone(id: String?) { speakLatch?.countDown() }
                    override fun onError(id: String?) { speakLatch?.countDown() }
                })
                say("Sufiyan Khan, welcome to Jarvis")
            }
        }
        addButton()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        try { btn?.let { wm.removeView(it) } } catch (e: Exception) { }
        recognizer?.destroy(); tts?.shutdown(); worker.shutdownNow()
        super.onDestroy()
    }

    // ---------- floating button ----------
    private fun addButton() {
        val d = resources.displayMetrics.density
        val b = TextView(this)
        b.text = "J"; b.textSize = 22f; b.setTextColor(Color.BLACK); b.gravity = Gravity.CENTER
        val bg = GradientDrawable(); bg.shape = GradientDrawable.OVAL; bg.setColor(0xFF2FD8FF.toInt())
        b.background = bg
        val size = (56 * d).toInt()
        val lp = WindowManager.LayoutParams(
            size, size, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.END
        lp.x = (8 * d).toInt(); lp.y = (300 * d).toInt()
        var sx = 0; var sy = 0; var tx = 0f; var ty = 0f; var moved = false
        b.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = lp.x; sy = lp.y; tx = e.rawX; ty = e.rawY; moved = false }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - tx; val dy = e.rawY - ty
                    if (Math.abs(dx) > 12 || Math.abs(dy) > 12) moved = true
                    if (moved) { lp.x = (sx - dx).toInt(); lp.y = (sy + dy).toInt(); wm.updateViewLayout(b, lp) }
                }
                MotionEvent.ACTION_UP -> if (!moved) onTap()
            }
            true
        }
        btn = b
        wm.addView(b, lp)
    }

    private fun setBtn(active: Boolean) = main.post {
        (btn?.background as? GradientDrawable)?.setColor(if (active) 0xFFFF5252.toInt() else 0xFF2FD8FF.toInt())
    }

    private fun onTap() {
        if (running) { stopFlag = true; heard.offer(""); say("Ruk gaya"); return }
        val key = getSharedPreferences("jarvis", MODE_PRIVATE).getString("gkey", "") ?: ""
        if (key.isBlank()) { say("Pehle Jarvis app kholke API key daalo"); return }
        running = true; stopFlag = false; setBtn(true)
        worker.execute {
            try {
                say("Boliye"); Thread.sleep(800)
                val cmd = listenBlocking()
                if (cmd.isBlank()) speakWait("Samajh nahi aaya") else runTask(key, cmd)
            } finally { running = false; setBtn(false) }
        }
    }

    // ---------- voice ----------
    private fun say(t: String) { main.post { if (ttsReady) tts?.speak(t, TextToSpeech.QUEUE_FLUSH, null, "u") } }

    private fun speakWait(t: String) {
        val l = CountDownLatch(1); speakLatch = l; say(t); l.await(6, TimeUnit.SECONDS)
    }

    private fun listenBlocking(): String {
        heard.clear(); main.post { startListen() }
        val r = heard.poll(12, TimeUnit.SECONDS) ?: ""
        main.post { recognizer?.stopListening() }
        return r
    }

    private fun startListen() {
        recognizer?.destroy()
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { heard.offer(""); return }
        val r = SpeechRecognizer.createSpeechRecognizer(this); recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onResults(b: Bundle?) {
                heard.offer(b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: "")
            }
            override fun onError(e: Int) { heard.offer("") }
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(b: Bundle?) {}
            override fun onEvent(t: Int, b: Bundle?) {}
        })
        r.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
        )
    }

    // ---------- agent loop ----------
    private fun quickOpen(cmd: String): Boolean {
        val c = cmd.trim().trimEnd('.', '!', '?')
        val name = Regex("^open\\s+(.+)$", RegexOption.IGNORE_CASE).find(c)?.groupValues?.get(1)
            ?: Regex("^(.+?)\\s+(?:kholo|khol do|kholdo|open karo|open|खोलो|खोल दो)$", RegexOption.IGNORE_CASE).find(c)?.groupValues?.get(1)
            ?: return false
        return if (openApp(name.trim())) { say("Khol diya"); true } else false
    }

    private fun runTask(key: String, cmd: String) {
        if (quickOpen(cmd)) return
        val hist = ArrayList<String>()
        for (step in 1..15) {
            if (stopFlag) return
            val screen = snapshot()
            if (blocked.any { screen.substringBefore('\n').contains(it, true) }) {
                speakWait("Ye payment ya bank app hai, main isse nahi chalaunga"); return
            }
            val o = Gemini.step(key, cmd, hist, screen)
            if (o == null) { speakWait("Gemini se jawab nahi aaya"); return }
            val act = o.optString("action"); val idx = o.optInt("index", -1)
            val text = o.optString("text"); val msg = o.optString("say")
            hist.add("step $step: $act index=$idx text=$text app=${o.optString("app")}")
            when (act) {
                "click" -> clickNode(idx)
                "type" -> typeText(idx, text)
                "scroll" -> scroll(idx, o.optString("dir"))
                "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
                "home" -> performGlobalAction(GLOBAL_ACTION_HOME)
                "open_app" -> if (!openApp(o.optString("app"))) hist.add("app mila nahi")
                "ask" -> { speakWait(msg); val r = listenBlocking(); hist.add("user ne kaha: $r") }
                "done" -> { speakWait(msg.ifBlank { "Ho gaya" }); return }
                else -> {}
            }
            Thread.sleep(600)
        }
        speakWait("Kaafi steps ho gaye, ruk raha hoon")
    }

    private fun snapshot(): String {
        val root = rootInActiveWindow ?: return "package=unknown\n(screen unavailable)"
        val list = ArrayList<AccessibilityNodeInfo>()
        val sb = StringBuilder("package=${root.packageName}\n")
        fun walk(n: AccessibilityNodeInfo?) {
            if (n == null || list.size >= 50) return
            if (n.isVisibleToUser) {
                val t = n.text?.toString()?.take(40)
                val d = n.contentDescription?.toString()?.take(40)
                val fl = ArrayList<String>()
                if (n.isClickable) fl.add("click"); if (n.isEditable) fl.add("edit"); if (n.isScrollable) fl.add("scroll")
                if (!t.isNullOrBlank() || !d.isNullOrBlank() || fl.isNotEmpty()) {
                    sb.append("[${list.size}] ").append(n.className?.toString()?.substringAfterLast('.')).append(' ')
                    if (!t.isNullOrBlank()) sb.append("\"$t\" ")
                    if (!d.isNullOrBlank()) sb.append("desc=\"$d\" ")
                    sb.append(fl.joinToString("/")).append('\n')
                    list.add(n)
                }
            }
            for (i in 0 until n.childCount) walk(n.getChild(i))
        }
        walk(root)
        nodes = list
        return sb.toString()
    }

    private fun clickNode(i: Int) {
        val n0 = nodes.getOrNull(i) ?: return
        var n: AccessibilityNodeInfo? = n0
        while (n != null && !n.isClickable) n = n.parent
        if (n != null && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
        val r = Rect(); n0.getBoundsInScreen(r); tapAt(r.exactCenterX(), r.exactCenterY())
    }

    private fun tapAt(x: Float, y: Float) {
        val p = Path().apply { moveTo(x, y) }
        val g = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p, 0, 60)).build()
        dispatchGesture(g, null, null)
    }

    private fun typeText(i: Int, t: String) {
        val n = nodes.getOrNull(i) ?: return
        n.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val a = Bundle(); a.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, t)
        n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, a)
    }

    private fun scroll(i: Int, dir: String) {
        val n = nodes.getOrNull(i)?.takeIf { it.isScrollable } ?: nodes.firstOrNull { it.isScrollable } ?: return
        n.performAction(if (dir == "up") AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD else AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
    }

    private fun openApp(name: String): Boolean {
        if (name.isBlank()) return false
        val pm = packageManager
        val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val hit = pm.queryIntentActivities(q, 0).firstOrNull { it.loadLabel(pm).toString().contains(name, true) } ?: return false
        val li = pm.getLaunchIntentForPackage(hit.activityInfo.packageName) ?: return false
        li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(li)
        return true
    }
}
