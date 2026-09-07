package com.example.screentranslator

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.Button

/** Manual TL remains a normal tap; holding it for exactly ~2s enters Klip. */
class KlipManualButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.buttonStyle
) : Button(context, attrs, defStyleAttr) {
    companion object { private const val KLIP_LONG_PRESS_MS = 2_000L }

    private var longTriggered = false
    private var downX = 0f
    private var downY = 0f
    private val startKlip = Runnable {
        longTriggered = true
        performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        KlipSelectionController.start(context, this)
    }

    init { isLongClickable = false }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                longTriggered = false
                downX = event.x
                downY = event.y
                removeCallbacks(startKlip)
                postDelayed(startKlip, KLIP_LONG_PRESS_MS)
                return super.onTouchEvent(event)
            }
            MotionEvent.ACTION_MOVE -> {
                val slop = 12f * resources.displayMetrics.density
                if (kotlin.math.abs(event.x - downX) > slop || kotlin.math.abs(event.y - downY) > slop) {
                    removeCallbacks(startKlip)
                }
                return super.onTouchEvent(event)
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(startKlip)
                if (longTriggered || KlipSelectionController.isActive) {
                    longTriggered = false
                    return true
                }
                return super.onTouchEvent(event)
            }
            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(startKlip)
                longTriggered = false
                return super.onTouchEvent(event)
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(startKlip)
        super.onDetachedFromWindow()
    }
}

/** Existing Cancel button, with an additional Klip-cancel interception. */
class KlipCancelButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.buttonStyle
) : Button(context, attrs, defStyleAttr) {
    override fun performClick(): Boolean {
        if (KlipSelectionController.isActive) {
            KlipSelectionController.cancel()
            return true
        }
        return super.performClick()
    }
}
