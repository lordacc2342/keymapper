package com.example.keymapper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.GestureResultCallback
import android.accessibilityservice.GestureDescription
import android.accessibilityservice.GestureDescription.StrokeDescription
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams as LP
import android.view.accessibility.AccessibilityEvent
import android.widget.TextView
import android.widget.Toast

class KeyService : AccessibilityService() {

    companion object {
        var instance: KeyService? = null
        @Volatile var capturing = false // true saat app sedang menangkap tombol (editor)
    }

    private val h = Handler(Looper.getMainLooper())
    private val wm get() = getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var map: Map<Int, Aksi> = emptyMap()
    private var toggleKey = KeyEvent.KEYCODE_F12
    private var on = true

    private var badge: TextView? = null
    private var calBtn: View? = null
    private var capView: View? = null
    private var calIndex = -1
    private var calStep = 0

    // Status tombol "tahan"
    private val SEG = 250L
    private var holdKey = -1
    private var hx = 0f
    private var hy = 0f
    private var stroke: StrokeDescription? = null

    override fun onServiceConnected() {
        instance = this
        reload()
        showBadge()
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        holdKey = -1
        remove(badge); remove(calBtn); remove(capView)
        return super.onUnbind(intent)
    }

    fun reload() {
        map = Store.load(this).associateBy { it.key }
        toggleKey = Store.toggleKey(this)
    }

    // ---------- Penanganan tombol ----------
    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (capturing) return false // editor sedang menangkap tombol: teruskan
        val code = event.keyCode
        val down = event.action == KeyEvent.ACTION_DOWN
        val first = down && event.repeatCount == 0

        // Tombol toggle: selalu aktif, ON <-> OFF
        if (code == toggleKey) {
            if (first) {
                on = !on
                if (!on) holdKey = -1
                updateBadge()
            }
            return true
        }

        if (!on) return false // OFF: semua tombol diteruskan ke game (bisa mengetik)

        val a = map[code] ?: return false // tidak diatur: teruskan
        if (a.x == 0f && a.y == 0f) return false // belum dikalibrasi: teruskan

        when (a.type) {
            "tap" -> if (first) tap(a.x, a.y)
            "swipe" -> if (first) swipe(a)
            "hold" -> {
                if (first && holdKey == -1) {
                    holdKey = code
                    startHold(a.x, a.y)
                } else if (!down && code == holdKey) {
                    holdKey = -1 // lepas: rangkaian stroke akan ditutup
                }
            }
        }
        return true // tombol yang diatur dikonsumsi
    }

    // ---------- Gestur ----------
    private fun pt(x: Float, y: Float) = Path().apply { moveTo(x, y) }

    private fun tap(x: Float, y: Float) {
        dispatchGesture(
            GestureDescription.Builder().addStroke(StrokeDescription(pt(x, y), 0, 40)).build(),
            null, null
        )
    }

    private fun swipe(a: Aksi) {
        val p = Path().apply { moveTo(a.x, a.y); lineTo(a.x2, a.y2) }
        dispatchGesture(
            GestureDescription.Builder().addStroke(StrokeDescription(p, 0, 300)).build(),
            null, null
        )
    }

    // Tahan = stroke bersambung (willContinue) dalam potongan 250 ms
    private fun startHold(x: Float, y: Float) {
        hx = x; hy = y
        send(StrokeDescription(pt(x, y), 0, SEG, true))
    }

    private fun send(s: StrokeDescription) {
        stroke = s
        dispatchGesture(
            GestureDescription.Builder().addStroke(s).build(),
            object : GestureResultCallback() {
                override fun onCompleted(g: GestureDescription?) {
                    if (stroke === s) next()
                }

                override fun onCancelled(g: GestureDescription?) {
                    if (stroke === s) {
                        stroke = null
                        // Terpotong (mis. oleh tap lain): mulai lagi jika tombol masih ditahan
                        if (holdKey != -1) h.postDelayed({
                            if (holdKey != -1 && stroke == null) startHold(hx, hy)
                        }, 120)
                    }
                }
            }, null
        )
    }

    private fun next() {
        val s = stroke ?: return
        try {
            if (holdKey != -1) send(s.continueStroke(pt(hx, hy), 0, SEG, true))
            else if (s.willContinue()) send(s.continueStroke(pt(hx, hy), 0, 10, false)) // tutup
            else stroke = null
        } catch (e: Exception) {
            stroke = null
        }
    }

    // ---------- Overlay ----------
    private fun lp(w: Int, ht: Int, flags: Int, g: Int) =
        LP(w, ht, LP.TYPE_ACCESSIBILITY_OVERLAY, flags, PixelFormat.TRANSLUCENT).apply { gravity = g }

    private fun remove(v: View?) {
        try { if (v != null) wm.removeView(v) } catch (_: Exception) {}
    }

    private fun showBadge() {
        val t = TextView(this).apply {
            textSize = 12f; setTextColor(Color.WHITE); setPadding(16, 6, 16, 6)
        }
        badge = t
        val p = lp(LP.WRAP_CONTENT, LP.WRAP_CONTENT, LP.FLAG_NOT_FOCUSABLE or LP.FLAG_NOT_TOUCHABLE,
            Gravity.TOP or Gravity.START)
        p.x = 16; p.y = 80
        wm.addView(t, p)
        updateBadge()
    }

    private fun updateBadge() {
        badge?.apply {
            text = if (on) "MAP ON" else "MAP OFF"
            setBackgroundColor(if (on) 0xCC2E7D32.toInt() else 0xCCC62828.toInt())
        }
    }

    // ---------- Kalibrasi ----------
    fun calibrate(i: Int) {
        calIndex = i; calStep = 0
        remove(calBtn); remove(capView)
        val b = TextView(this).apply {
            text = "PILIH TITIK"; textSize = 16f; setTextColor(Color.WHITE)
            setBackgroundColor(0xDD1565C0.toInt()); setPadding(48, 24, 48, 24)
            setOnClickListener { v ->
                h.post { remove(v) }
                calBtn = null
                startCapture()
            }
        }
        calBtn = b
        wm.addView(b, lp(LP.WRAP_CONTENT, LP.WRAP_CONTENT, LP.FLAG_NOT_FOCUSABLE,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL))
    }

    private fun startCapture() {
        val a = Store.load(this).getOrNull(calIndex) ?: return
        val k = KeyEvent.keyCodeToString(a.key).removePrefix("KEYCODE_")
        val info = if (a.type != "swipe") "titik aksi"
        else if (calStep == 0) "titik AWAL swipe" else "titik AKHIR swipe"
        val v = TextView(this).apply {
            text = "Sentuh $info untuk tombol $k"
            textSize = 20f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setBackgroundColor(0x66000000)
            setOnTouchListener { _, e ->
                if (e.action == MotionEvent.ACTION_DOWN) {
                    val x = e.rawX
                    val y = e.rawY
                    h.post { record(x, y) }
                }
                true
            }
        }
        capView = v
        wm.addView(v, lp(LP.MATCH_PARENT, LP.MATCH_PARENT,
            LP.FLAG_NOT_FOCUSABLE or LP.FLAG_LAYOUT_IN_SCREEN or LP.FLAG_LAYOUT_NO_LIMITS,
            Gravity.TOP or Gravity.START))
    }

    private fun record(x: Float, y: Float) {
        val list = Store.load(this)
        val a = list.getOrNull(calIndex) ?: return
        remove(capView); capView = null
        if (a.type == "swipe" && calStep == 0) {
            a.x = x; a.y = y; calStep = 1
            Store.save(this, list)
            startCapture() // minta titik akhir
            return
        }
        if (a.type == "swipe") { a.x2 = x; a.y2 = y } else { a.x = x; a.y = y }
        Store.save(this, list)
        Toast.makeText(this, "Tersimpan: ${x.toInt()}, ${y.toInt()}", Toast.LENGTH_SHORT).show()
        calIndex = -1
    }
}
