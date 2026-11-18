package com.example.keymapper

import android.content.Context
import android.view.KeyEvent
import org.json.JSONArray
import org.json.JSONObject

// type: "tap" | "hold" | "swipe". (x,y) = titik utama, (x2,y2) = titik akhir swipe
data class Aksi(
    var key: Int, var type: String,
    var x: Float = 0f, var y: Float = 0f,
    var x2: Float = 0f, var y2: Float = 0f
)

object Store {
    private fun sp(c: Context) = c.getSharedPreferences("km", Context.MODE_PRIVATE)

    // Profil "Growtopia" disimpan sebagai JSON di SharedPreferences
    fun load(c: Context): MutableList<Aksi> {
        val out = mutableListOf<Aksi>()
        val a = JSONArray(sp(c).getString("Growtopia", "[]"))
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            out.add(
                Aksi(
                    o.getInt("k"), o.getString("t"),
                    o.getDouble("x").toFloat(), o.getDouble("y").toFloat(),
                    o.getDouble("x2").toFloat(), o.getDouble("y2").toFloat()
                )
            )
        }
        return out
    }

    fun save(c: Context, list: List<Aksi>) {
        val a = JSONArray()
        for (m in list) {
            a.put(
                JSONObject().put("k", m.key).put("t", m.type)
                    .put("x", m.x.toDouble()).put("y", m.y.toDouble())
                    .put("x2", m.x2.toDouble()).put("y2", m.y2.toDouble())
            )
        }
        sp(c).edit().putString("Growtopia", a.toString()).apply()
        KeyService.instance?.reload() // beri tahu service
    }

    fun toggleKey(c: Context) = sp(c).getInt("toggle", KeyEvent.KEYCODE_F12)

    fun setToggleKey(c: Context, k: Int) {
        sp(c).edit().putInt("toggle", k).apply()
        KeyService.instance?.reload()
    }
}
