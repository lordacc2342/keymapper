package com.example.keymapper

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var root: LinearLayout

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = dp(16)
            setPadding(p, p, p, p)
        }
        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun name(k: Int) = KeyEvent.keyCodeToString(k).removePrefix("KEYCODE_")

    private fun tv(s: String, size: Float = 15f) = TextView(this).apply {
        text = s; textSize = size; setPadding(0, dp(8), 0, dp(4))
    }

    private fun btn(s: String, f: () -> Unit) = Button(this).apply {
        text = s; isAllCaps = false; setOnClickListener { f() }
    }

    private fun render() {
        root.removeAllViews()
        val aktif = KeyService.instance != null
        root.addView(tv(if (aktif) "Status service: AKTIF" else "Status service: MATI (aktifkan di Accessibility)", 18f))
        root.addView(btn("Buka pengaturan Accessibility") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        root.addView(tv("Izin: Accessibility > Aplikasi terinstal > Key Mapper > aktifkan. " +
                "Android 13: jika tombol abu-abu, buka Info Aplikasi > titik tiga > Izinkan pengaturan terbatas."))
        root.addView(tv("Tombol toggle ON/OFF: ${name(Store.toggleKey(this))}", 16f))
        root.addView(btn("Ganti tombol toggle") {
            captureKey { Store.setToggleKey(this, it); render() }
        })
        root.addView(tv("Mapping profil \"Growtopia\"", 18f))

        val list = Store.load(this)
        list.forEachIndexed { i, a ->
            val pos = when {
                a.x == 0f && a.y == 0f -> "(posisi belum diatur)"
                a.type == "swipe" -> "(${a.x.toInt()},${a.y.toInt()}) -> (${a.x2.toInt()},${a.y2.toInt()})"
                else -> "(${a.x.toInt()},${a.y.toInt()})"
            }
            val jenis = when (a.type) { "tap" -> "TAP"; "hold" -> "TAHAN"; else -> "SWIPE" }
            root.addView(tv("${name(a.key)} -> $jenis $pos"))
            root.addView(LinearLayout(this).apply {
                addView(btn("Atur posisi") { calibrate(i) },
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(btn("Hapus") {
                    list.removeAt(i)
                    Store.save(this@MainActivity, list)
                    render()
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            })
        }
        root.addView(btn("Tambah mapping") { addMapping() })
    }

    private fun calibrate(i: Int) {
        val s = KeyService.instance
        if (s == null) {
            Toast.makeText(this, "Aktifkan service dulu", Toast.LENGTH_LONG).show()
            return
        }
        s.calibrate(i)
        Toast.makeText(this, "Buka Growtopia, lalu sentuh tombol biru PILIH TITIK di atas layar", Toast.LENGTH_LONG).show()
        moveTaskToBack(true)
    }

    private fun addMapping() = captureKey { code ->
        AlertDialog.Builder(this).setTitle("Jenis aksi untuk ${name(code)}")
            .setItems(arrayOf("Tap sekali", "Tahan (jalan terus)", "Swipe")) { _, w ->
                val list = Store.load(this)
                list.removeAll { it.key == code }
                list.add(Aksi(code, arrayOf("tap", "hold", "swipe")[w]))
                Store.save(this, list)
                render()
            }.show()
    }

    // Dialog yang menangkap satu tombol keyboard fisik
    private fun captureKey(cb: (Int) -> Unit) {
        KeyService.capturing = true
        val d = AlertDialog.Builder(this)
            .setMessage("Tekan tombol keyboard yang diinginkan...")
            .setNegativeButton("Batal", null)
            .setOnDismissListener { KeyService.capturing = false }
            .create()
        d.setOnKeyListener { _, code, e ->
            if (e.action == KeyEvent.ACTION_DOWN && code != KeyEvent.KEYCODE_BACK) {
                d.dismiss()
                cb(code)
                true
            } else false
        }
        d.show()
    }
}
