package com.zak.stardust

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The whole app: a particle playground drawn on a Canvas.
 * Touch to paint stars, drop black holes, repel with your finger, hold for a supernova,
 * tilt the phone to pour the stardust around.
 */
class StardustView(context: Context, private val synth: Synth) : View(context) {

    enum class Mode(val label: String) {
        EMIT("✨ Emit"),
        WELL("🕳 Well"),
        REPEL("💨 Repel")
    }

    class Well(var x: Float, var y: Float, var age: Float = 0f)

    // ---- public state (controlled from the button bar) ----
    var mode = Mode.EMIT
    var tiltEnabled = true
    var paletteIndex = 0

    val paletteNames = arrayOf("Aurora", "Sunset", "Ocean", "Neon", "Ember")

    private fun hex(s: String): Int = Color.parseColor(s)

    private val palettes: Array<IntArray> = arrayOf(
        intArrayOf(hex("#7CF7FF"), hex("#5B8CFF"), hex("#B36BFF"), hex("#FF6BD6")),
        intArrayOf(hex("#FFE66B"), hex("#FF9A3C"), hex("#FF4D6D"), hex("#8E2DE2")),
        intArrayOf(hex("#B8FFF1"), hex("#3EE0C6"), hex("#2A8CFF"), hex("#1B3BFF")),
        intArrayOf(hex("#39FF14"), hex("#F6FF00"), hex("#FF00E5"), hex("#00E5FF")),
        intArrayOf(hex("#FFFFFF"), hex("#FFD27A"), hex("#FF6A00"), hex("#B00020"))
    )

    // ---- scaling for different screen densities ----
    private val dens = resources.displayMetrics.density
    private val sc = dens / 2.75f

    // ---- particles (struct-of-arrays for speed) ----
    private val maxP = 1400
    private val px = FloatArray(maxP)
    private val py = FloatArray(maxP)
    private val pvx = FloatArray(maxP)
    private val pvy = FloatArray(maxP)
    private val plife = FloatArray(maxP)
    private val pmax = FloatArray(maxP)
    private val pcol = FloatArray(maxP)
    private val psize = FloatArray(maxP)
    private var count = 0

    // ---- wells (black holes) ----
    private val wells = ArrayList<Well>()

    // ---- background stars ----
    private val starCount = 90
    private val stars = FloatArray(starCount * 3)

    init {
        for (i in 0 until starCount) {
            stars[i * 3] = Random.nextFloat()
            stars[i * 3 + 1] = Random.nextFloat()
            stars[i * 3 + 2] = Random.nextFloat() * 6.28f
        }
    }

    // ---- input / timing state ----
    private var tiltX = 0f
    private var tiltY = 0f
    private var touching = false
    private var tx = 0f
    private var ty = 0f
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var burstDone = true
    private var fvx = 0f
    private var fvy = 0f
    private var lastNote = 0L
    private var lastNanos = 0L
    private var time = 0f
    private val startTime = SystemClock.uptimeMillis()

    private val notes = intArrayOf(0, 2, 4, 7, 9, 12, 14, 16)

    // ---- paints ----
    private val bgPaint = Paint()
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val streakPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
    }
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
    }
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val oval = RectF()

    // ---------------------------------------------------------------- public API

    fun setTilt(x: Float, y: Float) {
        tiltX += (x - tiltX) * 0.25f
        tiltY += (y - tiltY) * 0.25f
    }

    fun clearAll() {
        count = 0
        wells.clear()
    }

    // ---------------------------------------------------------------- layout

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        bgPaint.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            hex("#03030F"), hex("#0E0A2E"),
            Shader.TileMode.CLAMP
        )
    }

    // ---------------------------------------------------------------- touch

    private fun noteFor(x: Float): Double {
        val idx = ((x / width) * notes.size).toInt().coerceIn(0, notes.size - 1)
        return 220.0 * 2.0.pow(notes[idx] / 12.0)
    }

    private fun addWell(x: Float, y: Float) {
        if (wells.size >= 5) wells.removeAt(0)
        wells.add(Well(x, y))
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touching = true
                tx = e.x
                ty = e.y
                downX = e.x
                downY = e.y
                downTime = SystemClock.uptimeMillis()
                burstDone = false
                fvx = 0f
                fvy = 0f
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                when (mode) {
                    Mode.EMIT -> synth.play(noteFor(e.x), 220, 0.5)
                    Mode.WELL -> {
                        addWell(e.x, e.y)
                        synth.play(110.0, 450, 0.6)
                    }
                    Mode.REPEL -> synth.play(165.0, 160, 0.3)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                fvx = (e.x - tx) * 60f
                fvy = (e.y - ty) * 60f
                tx = e.x
                ty = e.y
                if (mode == Mode.WELL && wells.isNotEmpty()) {
                    val last = wells[wells.size - 1]
                    last.x = e.x
                    last.y = e.y
                }
                if (mode == Mode.EMIT) {
                    val now = SystemClock.uptimeMillis()
                    if (now - lastNote > 150) {
                        lastNote = now
                        synth.play(noteFor(e.x), 140, 0.25)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                touching = false
            }
        }
        return true
    }

    // ---------------------------------------------------------------- simulation

    private fun spawn(x: Float, y: Float, bvx: Float, bvy: Float, smin: Float, smax: Float) {
        val idx = if (count < maxP) count++ else Random.nextInt(maxP)
        val a = Random.nextFloat() * 6.2831855f
        val s = (smin + Random.nextFloat() * (smax - smin)) * sc
        px[idx] = x
        py[idx] = y
        pvx[idx] = kotlin.math.cos(a) * s + bvx
        pvy[idx] = sin(a) * s + bvy
        val life = 2.5f + Random.nextFloat() * 3.5f
        plife[idx] = life
        pmax[idx] = life
        pcol[idx] = Random.nextFloat()
        psize[idx] = (1.6f + Random.nextFloat() * 3.2f) * sc
    }

    private fun burst(x: Float, y: Float) {
        for (k in 0 until 350) spawn(x, y, 0f, 0f, 150f, 900f)
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        synth.play(98.0, 600, 0.7)
    }

    private fun copyParticle(from: Int, to: Int) {
        px[to] = px[from]
        py[to] = py[from]
        pvx[to] = pvx[from]
        pvy[to] = pvy[from]
        plife[to] = plife[from]
        pmax[to] = pmax[from]
        pcol[to] = pcol[from]
        psize[to] = psize[from]
    }

    private fun update(dt: Float) {
        time += dt
        val gx = if (tiltEnabled) tiltX / 9.8f * 1300f * sc else 0f
        val gy = if (tiltEnabled) tiltY / 9.8f * 1300f * sc else 0f

        for (wl in wells) wl.age += dt
        fvx *= 0.9f
        fvy *= 0.9f

        // ambient drifting sparkles so the screen is never empty
        if (count < 120 && Random.nextFloat() < 0.15f) {
            spawn(Random.nextFloat() * width, Random.nextFloat() * height, 0f, 0f, 10f, 50f)
        }

        if (touching && mode == Mode.EMIT) {
            for (k in 0 until 9) spawn(tx, ty, fvx * 0.25f, fvy * 0.25f, 40f, 260f)
            if (!burstDone &&
                SystemClock.uptimeMillis() - downTime > 650 &&
                hypot(tx - downX, ty - downY) < 28f * dens
            ) {
                burst(tx, ty)
                burstDone = true
            }
        }

        val w = width.toFloat()
        val h = height.toFloat()
        val kWell = 9_000_000f * sc * sc * sc
        val swallow = 16f * dens

        var i = 0
        while (i < count) {
            var ax = gx
            var ay = gy
            var dead = false

            for (wl in wells) {
                val dx = wl.x - px[i]
                val dy = wl.y - py[i]
                val raw = dx * dx + dy * dy
                if (raw < swallow * swallow) dead = true
                val d2 = raw + 900f
                val dist = sqrt(d2)
                val f = kWell / d2
                ax += dx / dist * f
                ay += dy / dist * f
            }

            if (touching && mode == Mode.REPEL) {
                val dx = px[i] - tx
                val dy = py[i] - ty
                val d2 = dx * dx + dy * dy
                val rad = 260f * dens
                if (d2 < rad * rad) {
                    val dist = sqrt(d2) + 1f
                    val f = (1f - dist / rad) * 9000f * sc
                    ax += dx / dist * f
                    ay += dy / dist * f
                }
            }

            pvx[i] += ax * dt
            pvy[i] += ay * dt
            val drag = 1f - 0.3f * dt
            pvx[i] *= drag
            pvy[i] *= drag

            val sp2 = pvx[i] * pvx[i] + pvy[i] * pvy[i]
            val cap = 1500f * sc
            if (sp2 > cap * cap) {
                val s = cap / sqrt(sp2)
                pvx[i] *= s
                pvy[i] *= s
            }

            px[i] += pvx[i] * dt
            py[i] += pvy[i] * dt

            if (px[i] < 0f) {
                px[i] = 0f
                pvx[i] = -pvx[i] * 0.6f
            } else if (px[i] > w) {
                px[i] = w
                pvx[i] = -pvx[i] * 0.6f
            }
            if (py[i] < 0f) {
                py[i] = 0f
                pvy[i] = -pvy[i] * 0.6f
            } else if (py[i] > h) {
                py[i] = h
                pvy[i] = -pvy[i] * 0.6f
            }

            plife[i] -= dt
            if (dead || plife[i] <= 0f) {
                val last = count - 1
                if (i != last) copyParticle(last, i)
                count--
                continue
            }
            i++
        }
    }

    // ---------------------------------------------------------------- drawing

    private fun colorAt(pal: IntArray, t0: Float, alpha: Int): Int {
        val t = t0.coerceIn(0f, 0.999f) * (pal.size - 1)
        val i0 = t.toInt()
        val f = t - i0
        val c0 = pal[i0]
        val c1 = pal[i0 + 1]
        val r = Color.red(c0) + ((Color.red(c1) - Color.red(c0)) * f).toInt()
        val g = Color.green(c0) + ((Color.green(c1) - Color.green(c0)) * f).toInt()
        val b = Color.blue(c0) + ((Color.blue(c1) - Color.blue(c0)) * f).toInt()
        return Color.argb(alpha.coerceIn(0, 255), r, g, b)
    }

    override fun onDraw(canvas: Canvas) {
        val now = System.nanoTime()
        var dt = if (lastNanos == 0L) 0.016f else (now - lastNanos) / 1e9f
        lastNanos = now
        if (dt > 0.033f) dt = 0.033f
        update(dt)

        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        drawStars(canvas)
        drawParticles(canvas)
        drawWells(canvas)
        drawTouchRing(canvas)
        drawHud(canvas)

        postInvalidateOnAnimation()
    }

    private fun drawStars(canvas: Canvas) {
        for (i in 0 until starCount) {
            val x = stars[i * 3] * width
            val y = stars[i * 3 + 1] * height
            val tw = 0.5f + 0.5f * sin(time * 1.5f + stars[i * 3 + 2])
            starPaint.color = Color.argb((40 + 120 * tw).toInt(), 255, 255, 255)
            canvas.drawCircle(x, y, (0.6f + tw) * dens * 0.8f, starPaint)
        }
    }

    private fun drawParticles(canvas: Canvas) {
        val pal = palettes[paletteIndex]
        for (i in 0 until count) {
            val t = plife[i] / pmax[i]
            val a = (min(1f, t * 2.5f) * 255f).toInt()
            val col = colorAt(pal, pcol[i] * 0.85f + (1f - t) * 0.15f, a)
            val sz = psize[i] * (0.4f + 0.6f * t)

            streakPaint.color = col
            streakPaint.strokeWidth = sz
            canvas.drawLine(
                px[i] - pvx[i] * 0.035f, py[i] - pvy[i] * 0.035f,
                px[i], py[i], streakPaint
            )

            haloPaint.color = (col and 0x00FFFFFF) or ((a / 7) shl 24)
            canvas.drawCircle(px[i], py[i], sz * 3.2f, haloPaint)
        }
    }

    private fun drawWells(canvas: Canvas) {
        val accent = colorAt(palettes[paletteIndex], 0.5f, 255)
        for (wl in wells) {
            val pulse = 1f + 0.12f * sin(wl.age * 5f)
            val r = 16f * dens * pulse

            haloPaint.color = (accent and 0x00FFFFFF) or (60 shl 24)
            canvas.drawCircle(wl.x, wl.y, r * 3.4f, haloPaint)

            canvas.drawCircle(wl.x, wl.y, r, corePaint)

            ringPaint.color = accent
            ringPaint.strokeWidth = 2f * dens
            canvas.drawCircle(wl.x, wl.y, r + 2f * dens, ringPaint)

            val big = r * 1.9f
            oval.set(wl.x - big, wl.y - big, wl.x + big, wl.y + big)
            ringPaint.strokeWidth = 3f * dens
            canvas.drawArc(oval, wl.age * 140f, 70f, false, ringPaint)
            canvas.drawArc(oval, wl.age * 140f + 180f, 70f, false, ringPaint)
        }
    }

    private fun drawTouchRing(canvas: Canvas) {
        if (!touching) return
        ringPaint.color = Color.argb(120, 255, 255, 255)
        ringPaint.strokeWidth = 1.5f * dens
        canvas.drawCircle(tx, ty, (22f + 4f * sin(time * 8f)) * dens, ringPaint)
    }

    private fun drawHud(canvas: Canvas) {
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = 12f * dens
        textPaint.color = Color.argb(150, 255, 255, 255)
        canvas.drawText(
            paletteNames[paletteIndex] + "  •  " + count + " stars",
            14f * dens, 44f * dens, textPaint
        )

        val age = (SystemClock.uptimeMillis() - startTime) / 1000f
        if (age < 9f) {
            val alpha = (min(1f, (9f - age) / 3f) * 220f).toInt()
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.color = Color.argb(alpha, 255, 255, 255)
            textPaint.textSize = 18f * dens
            canvas.drawText("Drag to paint stars", width / 2f, height * 0.30f, textPaint)
            textPaint.textSize = 13f * dens
            canvas.drawText(
                "Hold still for a supernova  •  Tilt to pour",
                width / 2f, height * 0.30f + 26f * dens, textPaint
            )
        }
    }
}
