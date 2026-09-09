package com.example.screentranslator

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Rect
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
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
 * glass-like panel whose background is a real blurred copy of the selected area.
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
        val radius = 26f * density
        panel.background = GradientDrawable().apply {
            cornerRadius = radius
            setColor(Color.argb(190, 20, 24, 30))
            setStroke((1.2f * density).toInt().coerceAtLeast(1), Color.argb(90, 255, 255, 255))
        }
        panel.clipToOutline = true
        panel.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, radius)
            }
        }

        val blurImage = ImageView(context).apply {
            setImageBitmap(selectedBitmap)
            scaleType = ImageView.ScaleType.CENTER_CROP
            alpha = 0.78f
            contentDescription = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRenderEffect(RenderEffect.createBlurEffect(22f * density, 22f * density, Shader.TileMode.CLAMP))
            }
        }
        panel.addView(blurImage, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        panel.addView(View(context).apply {
            setBackgroundColor(Color.argb(105, 15, 18, 24))
        }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((22 * density).toInt(), (18 * density).toInt(), (18 * density).toInt(), (18 * density).toInt())
        }
        val header = FrameLayout(context)
        content.addView(header, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (42 * density).toInt()))

        val title = TextView(context).apply {
            text = "Terjemahan"
            setTextColor(Color.WHITE)
            textSize = 20f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(title, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.MATCH_PARENT))

        val close = ImageButton(context).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setColorFilter(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(90, 0, 0, 0))
            }
            contentDescription = "Tutup terjemahan Klip"
            setOnClickListener { closeOnce() }
            isFocusable = true
            isClickable = true
        }
        header.addView(close, FrameLayout.LayoutParams((44 * density).toInt(), (44 * density).toInt()).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        })

        content.addView(View(context).apply {
            setBackgroundColor(Color.argb(70, 255, 255, 255))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * density).toInt().coerceAtLeast(1)).apply {
            bottomMargin = (10 * density).toInt()
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
            setPadding(0, 0, 0, (4 * density).toInt())
        }
        scroll.addView(text, ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT))
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
