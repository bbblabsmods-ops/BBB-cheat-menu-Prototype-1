package com.example.wdoverlay

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.*

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var output: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)

        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }

        fun label(t: String) = TextView(this).apply { text = t; textSize = 16f; setPadding(0, pad / 2, 0, 0) }
        fun field(hint: String, number: Boolean = true) = EditText(this).apply {
            this.hint = hint
            if (number) inputType = InputType.TYPE_CLASS_NUMBER
        }
        fun button(t: String, onClick: () -> Unit) = Button(this).apply { text = t; setOnClickListener { onClick() } }

        status = TextView(this).apply { textSize = 18f }
        output = TextView(this).apply { setTextIsSelectable(true); setPadding(0, pad, 0, 0) }

        val pairPort = field("Pairing port")
        val pairCode = field("Pairing code")
        val debugPort = field("Wireless debugging port")
        val cmd = field("Shell command (e.g. getprop ro.product.model)", number = false)

        root.addView(status)
        root.addView(label("1. Turn on Wireless Debugging, then tap \"Pair device with pairing code\"."))
        root.addView(button("Open Developer Options") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
        })
        root.addView(pairPort); root.addView(pairCode)
        root.addView(button("Pair") {
            val port = pairPort.text.toString().toIntOrNull()
            if (port == null) show("Enter the pairing port.") else
                Adb.run({ Adb.pair(this, port, pairCode.text.toString().trim()) }) { show(it) }
        })
        root.addView(label("2. Enter the port shown on the main Wireless Debugging screen."))
        root.addView(debugPort)
        root.addView(button("Connect") {
            val port = debugPort.text.toString().toIntOrNull()
            if (port == null) show("Enter the debug port.") else
                Adb.run({ Adb.connect(this, port) }) { show(it) }
        })
        root.addView(label("3. Run a command"))
        root.addView(cmd)
        root.addView(button("Run") {
            val c = cmd.text.toString().trim()
            if (c.isNotEmpty()) Adb.run({ Adb.shell(this, c) }) { show(it) }
        })
        root.addView(label("4. Overlay"))
        root.addView(button("Grant overlay permission") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })
        root.addView(button("Start overlay") {
            if (!Settings.canDrawOverlays(this)) show("Grant overlay permission first.")
            else startForegroundService(Intent(this, OverlayService::class.java))
        })
        root.addView(button("Stop overlay") { stopService(Intent(this, OverlayService::class.java)) })
        root.addView(output)

        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onResume() { super.onResume(); refreshStatus() }

    private fun show(text: String) { output.text = text; refreshStatus() }

    private fun refreshStatus() {
        Adb.run({ if (Adb.isConnected(this)) "ADB: connected" else "ADB: not connected" }) { status.text = it }
    }
}
