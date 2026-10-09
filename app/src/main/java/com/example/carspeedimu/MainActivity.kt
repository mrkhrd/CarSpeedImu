package com.example.carspeedimu

import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {

    private val ui = Handler(Looper.getMainLooper())
    private val isCar by lazy { packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE) }

    private var car: CarSpeedSource? = null
    private lateinit var imu: ImuSource
    private val rows = mutableListOf<Pair<Channel, Row>>()
    private lateinit var statusView: TextView

    private class Row(val value: TextView, val rate: TextView, val info: TextView)

    private val refresh = object : Runnable {
        override fun run() {
            for ((ch, row) in rows) {
                row.value.text = ch.value
                row.rate.text = ch.rate.summary()
                row.info.text = ch.info
            }
            ui.postDelayed(this, UI_REFRESH_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        imu = ImuSource(this)
        if (isCar) car = CarSpeedSource(this)
        setContentView(buildUi())

        if (car != null) {
            if (checkSelfPermission(PERMISSION_SPEED) == PackageManager.PERMISSION_GRANTED) {
                car?.start()
            } else {
                requestPermissions(arrayOf(PERMISSION_SPEED), REQ_SPEED)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode != REQ_SPEED) return
        if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            car?.start()
        } else {
            statusView.text = "Speed permission denied — vehicle speed unavailable."
        }
    }

    override fun onResume() {
        super.onResume()
        imu.start()
        ui.post(refresh)
    }

    override fun onPause() {
        ui.removeCallbacks(refresh)
        imu.stop()
        super.onPause()
    }

    override fun onDestroy() {
        car?.stop()
        super.onDestroy()
    }

    // ---- UI (built in code: a vertical list of channel cards) ----

    private fun buildUi(): ScrollView {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(32), dp(24), dp(32), dp(24))
            setBackgroundColor(Color.BLACK)
        }

        statusView = text(16f, Color.LTGRAY).apply {
            text = if (isCar) "Android Automotive detected" else "Not a car — vehicle speed disabled, IMU only"
        }
        root.addView(statusView)

        val carChannels = car?.channels.orEmpty()
        if (carChannels.isNotEmpty()) root.addView(header("Vehicle (CarPropertyManager)"))
        carChannels.forEach { root.addView(card(it, valueSize = 56f)) }

        root.addView(header("IMU (head unit SensorManager)"))
        imu.channels.forEach { root.addView(card(it, valueSize = 30f)) }

        root.addView(header("All sensors on this device"))
        root.addView(text(14f, Color.GRAY).apply { text = imu.allSensors() })

        return ScrollView(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(root)
        }
    }

    private fun card(ch: Channel, valueSize: Float): LinearLayout {
        val title = text(18f, Color.rgb(0x9E, 0xC9, 0xFF)).apply { text = ch.title }
        val value = text(valueSize, Color.WHITE).apply { typeface = Typeface.MONOSPACE }
        val rate = text(20f, Color.rgb(0x7C, 0xE3, 0x8B)).apply { typeface = Typeface.MONOSPACE }
        val info = text(14f, Color.GRAY)
        rows += ch to Row(value, rate, info)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(14), dp(20), dp(14))
            setBackgroundColor(Color.rgb(0x1C, 0x1C, 0x1E))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
            addView(title); addView(value); addView(rate); addView(info)
        }
    }

    private fun header(s: String) = text(22f, Color.WHITE).apply {
        text = s
        typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(24), 0, 0)
    }

    private fun text(sizeSp: Float, color: Int) = TextView(this).apply {
        textSize = sizeSp
        setTextColor(color)
        gravity = Gravity.START
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val PERMISSION_SPEED = "android.car.permission.CAR_SPEED"
        private const val REQ_SPEED = 1
        private const val UI_REFRESH_MS = 100L
    }
}
