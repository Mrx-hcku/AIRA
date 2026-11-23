package com.aira.assistant

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/** Jawab ke waqt neeche dikhne wala animated (pulsing) AIRA orb. Touch ko block nahi karta. */
object AiraOverlay {

    private val handler = Handler(Looper.getMainLooper())
    private var container: LinearLayout? = null
    private var label: TextView? = null
    private var animator: ValueAnimator? = null
    private var windowManager: WindowManager? = null
    private val hideRunnable = Runnable { removeNow() }

    fun show(context: Context, message: String, durationMs: Long) {
        val app = context.applicationContext
        handler.post {
            try {
                if (!Settings.canDrawOverlays(app)) return@post
                handler.removeCallbacks(hideRunnable)
                if (container == null) create(app)
                label?.text = message
                handler.postDelayed(hideRunnable, durationMs)
            } catch (_: Exception) {
            }
        }
    }

    fun hide(delayMs: Long = 0L) {
        handler.post {
            handler.removeCallbacks(hideRunnable)
            handler.postDelayed(hideRunnable, delayMs)
        }
    }

    private fun create(app: Context) {
        val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = app.resources.displayMetrics
        val density = metrics.density
        fun dp(v: Int) = (v * density).toInt()

        val orb = TextView(app).apply {
            text = "AIRA"
            setTextColor(Color.WHITE)
            textSize = 11f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#1E88E5"))
                setStroke(dp(2), Color.WHITE)
            }
            layoutParams = LinearLayout.LayoutParams(dp(56), dp(56))
        }

        val text = TextView(app).apply {
            setTextColor(Color.WHITE)
            textSize = 15f
            maxLines = 4
            maxWidth = (metrics.widthPixels * 0.62f).toInt()
            setPadding(dp(14), 0, dp(6), 0)
        }

        val box = LinearLayout(app).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(18), dp(12))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F01B1B1F"))
                cornerRadius = dp(40).toFloat()
            }
            addView(orb)
            addView(text)
        }

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(90)
        }

        wm.addView(box, lp)
        windowManager = wm
        container = box
        label = text

        animator = ValueAnimator.ofFloat(1f, 1.2f).apply {
            duration = 600
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                val v = it.animatedValue as Float
                orb.scaleX = v
                orb.scaleY = v
            }
            start()
        }
    }

    private fun removeNow() {
        animator?.cancel()
        animator = null
        container?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) {
            }
        }
        container = null
        label = null
    }
}
