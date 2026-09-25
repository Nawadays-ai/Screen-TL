package com.example.screentranslator

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.max
import kotlin.math.min

/**
 * Result UI for Klip.
 *
 * The selected screen area stays transparent/untouched while the rest of the
 * screen is dimmed. The translation is presented in a compact, scrollable
 * panel with one solid near-opaque fill: the earlier version stacked a blurred
 * copy of the selection under dark washes, which read as a glass slab and
 * pulled unrelated colours off the game screen.
 */
class KlipResultOverlayView(
    context: Context,
    private val selection: Rect,
    private val selectedBitmap: Bitmap,
    translatedText: String,
    private val onClose: () -> Unit
) : FrameLayout(context) {

    private val density = resources.displayMetrics.density
    private val dimView = OutsideSelectionView(context, selection)
    private val panel = FrameLayout(context)
    private var closed = false

    init {
        setWillNotDraw(false)
        addView(dimView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        buildPanel(translatedText)
        addView(panel)
        post { positionPanel() }
    }

    private fun buildPanel(translatedText: String) {
        // One solid, near-opaque charcoal panel. The previous version stacked a blurred copy of the
        // crop under two dark washes, which read as a glass slab; over a game screen the blur also
        // pulled unrelated colours into the panel. A single fill keeps it plain and predictable.
        val radius = 6f * density
        panel.background = GradientDrawable().apply {
            cornerRadius = radius
            setColor(Color.argb(232, 28, 28, 30))
            setStroke(1, Color.argb(46, 255, 255, 255))
        }
        panel.clipToOutline = true
        panel.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, radius)
            }
        }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * density).toInt(), (12 * density).toInt(), (12 * density).toInt(), (16 * density).toInt())
        }

        // The close button stays; the "Terjemahan" heading and its divider go, so the translation
        // is the first thing the panel shows.
        val close = ImageButton(context).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setColorFilter(Color.argb(220, 255, 255, 255))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(38, 0, 0, 0))
            }
            contentDescription = "Tutup terjemahan Klip"
            setOnClickListener { closeOnce() }
            isFocusable = true
            isClickable = true
        }
        content.addView(close, LinearLayout.LayoutParams((36 * density).toInt(), (36 * density).toInt()).apply {
            gravity = Gravity.END
        })

        val scroll = ScrollView(context).apply {
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        val text = TextView(context).apply {
            this.text = translatedText.trim().ifBlank { "Tidak ada hasil terjemahan." }
            setTextColor(Color.WHITE)
            textSize = 18f
            setLineSpacing(0f, 1.12f)
            includeFontPadding = true
        }
        scroll.addView(text, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        content.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        panel.addView(content, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }

    private fun positionPanel() {
        val screenW = width
        val screenH = height
        if (screenW <= 0 || screenH <= 0) return

        val horizontalMargin = (20 * density).toInt()
        val gap = (16 * density).toInt()
        val minPanelWidth = (280 * density).toInt()
        val desiredWidth = max(minPanelWidth, selection.width())
        val panelWidth = min(desiredWidth, screenW - horizontalMargin * 2).coerceAtLeast(1)

        val maxPanelHeight = (screenH * 0.48f).toInt()
        val minPanelHeight = (170 * density).toInt()
        val desiredHeight = (selection.height() * 0.55f).toInt().coerceAtLeast(minPanelHeight)
        val panelHeight = min(desiredHeight, maxPanelHeight).coerceAtLeast(1)

        val x = ((screenW - panelWidth) / 2).coerceAtLeast(horizontalMargin)
        val belowY = selection.bottom + gap
        val aboveY = selection.top - gap - panelHeight
        val y = when {
            belowY + panelHeight <= screenH - horizontalMargin -> belowY
            aboveY >= horizontalMargin -> aboveY
            else -> ((screenH - panelHeight) / 2).coerceAtLeast(horizontalMargin)
        }

        panel.layoutParams = FrameLayout.LayoutParams(panelWidth, panelHeight).apply {
            leftMargin = x.coerceIn(0, max(0, screenW - panelWidth))
            topMargin = y.coerceIn(0, max(0, screenH - panelHeight))
        }
        panel.requestLayout()
    }

    private fun closeOnce() {
        if (closed) return
        closed = true
        onClose()
    }

    override fun onDetachedFromWindow() {
        if (!selectedBitmap.isRecycled) selectedBitmap.recycle()
        super.onDetachedFromWindow()
    }

    private class OutsideSelectionView(context: Context, private val selection: Rect) : View(context) {
        private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(150, 0, 0, 0)
            style = android.graphics.Paint.Style.FILL
        }

        override fun onDraw(canvas: android.graphics.Canvas) {
            val left = selection.left.toFloat().coerceIn(0f, width.toFloat())
            val top = selection.top.toFloat().coerceIn(0f, height.toFloat())
            val right = selection.right.toFloat().coerceIn(left, width.toFloat())
            val bottom = selection.bottom.toFloat().coerceIn(top, height.toFloat())
            canvas.drawRect(0f, 0f, width.toFloat(), top, paint)
            canvas.drawRect(0f, bottom, width.toFloat(), height.toFloat(), paint)
            canvas.drawRect(0f, top, left, bottom, paint)
            canvas.drawRect(right, top, width.toFloat(), bottom, paint)
        }
    }
}
