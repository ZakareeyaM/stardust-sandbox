package com.zak.stardust

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout

class MainActivity : Activity(), SensorEventListener {

    private lateinit var view: StardustView
    private lateinit var sensors: SensorManager
    private var accel: Sensor? = null
    private val synth = Synth()

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun pill(label: String, onClick: (Button) -> Unit): Button {
        val btn = Button(this)
        btn.text = label
        btn.isAllCaps = false
        btn.textSize = 11f
        btn.isSingleLine = true
        btn.setTextColor(Color.WHITE)
        btn.minWidth = 0
        btn.minHeight = 0
        btn.setPadding(dp(4), 0, dp(4), 0)
        val bg = GradientDrawable()
        bg.cornerRadius = dp(22).toFloat()
        bg.setColor(Color.argb(110, 255, 255, 255))
        bg.setStroke(dp(1), Color.argb(90, 255, 255, 255))
        btn.background = bg
        btn.setOnClickListener {
            btn.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onClick(btn)
        }
        return btn
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        view = StardustView(this, synth)

        val modeBtn = pill(view.mode.label) { b ->
            val all = StardustView.Mode.values()
            view.mode = all[(view.mode.ordinal + 1) % all.size]
            b.text = view.mode.label
        }

        val paletteBtn = pill("🎨 " + view.paletteNames[view.paletteIndex]) { b ->
            view.paletteIndex = (view.paletteIndex + 1) % view.paletteNames.size
            b.text = "🎨 " + view.paletteNames[view.paletteIndex]
        }

        val tiltBtn = pill("📱 Tilt on") { b ->
            view.tiltEnabled = !view.tiltEnabled
            b.text = if (view.tiltEnabled) "📱 Tilt on" else "📱 Tilt off"
        }

        val soundBtn = pill("🔊") { b ->
            synth.enabled = !synth.enabled
            b.text = if (synth.enabled) "🔊" else "🔇"
        }

        val clearBtn = pill("🧹 Clear") { _ ->
            view.clearAll()
        }

        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        for (b in listOf(modeBtn, paletteBtn, tiltBtn, soundBtn, clearBtn)) {
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            lp.setMargins(dp(3), 0, dp(3), 0)
            bar.addView(b, lp)
        }

        val root = FrameLayout(this)
        root.addView(
            view,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        val barParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(48),
            Gravity.BOTTOM
        )
        barParams.setMargins(dp(8), 0, dp(8), dp(20))
        root.addView(bar, barParams)

        setContentView(root)

        sensors = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accel = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    override fun onResume() {
        super.onResume()
        accel?.let { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    override fun onPause() {
        super.onPause()
        sensors.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        // Screen gravity: x is mirrored, y matches the sensor's y axis.
        view.setTilt(-event.values[0], event.values[1])
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
