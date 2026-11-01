package com.sufiyan.jarvis

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("jarvis", MODE_PRIVATE)
        val pad = (20 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(pad, pad * 2, pad, pad)
        root.setBackgroundColor(0xFF020B12.toInt())

        fun tv(t: String, sz: Float = 15f): TextView {
            val v = TextView(this); v.text = t; v.textSize = sz
            v.setTextColor(0xFFD8F6FF.toInt()); v.setPadding(0, pad / 2, 0, pad / 2); return v
        }
        fun bt(t: String, f: () -> Unit): Button {
            val b = Button(this); b.text = t; b.setOnClickListener { f() }; return b
        }

        root.addView(tv("JARVIS", 30f))
        status = tv("")
        root.addView(status)

        root.addView(bt("1. Mic permission do") {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        })
        root.addView(bt("2. Accessibility ON karo (Jarvis chuno)") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })

        root.addView(tv("3. Gemini API key"))
        val et = EditText(this)
        et.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        et.setTextColor(0xFFD8F6FF.toInt())
        et.setText(prefs.getString("gkey", ""))
        root.addView(et)
        root.addView(bt("Save key") {
            prefs.edit().putString("gkey", et.text.toString().trim()).apply()
            Toast.makeText(this, "Key save ho gayi", Toast.LENGTH_SHORT).show()
        })

        root.addView(tv("Use: kisi bhi app mein neela J button dabao, Boliye sunke apna kaam bolo. Chalte kaam ko rokne ke liye lal button dabao.\n\nBanking aur payment apps par Jarvis kaam nahi karta."))

        val sv = ScrollView(this); sv.addView(root); setContentView(sv)
    }

    override fun onResume() {
        super.onResume()
        val mic = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val acc = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?.contains(packageName) == true
        status.text = "Mic: " + (if (mic) "OK" else "baaki") + "\nAccessibility: " + (if (acc) "ON" else "baaki")
    }
}
