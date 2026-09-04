package com.example.screentranslator

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton

class FloatingService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var fabMain: FloatingActionButton
    private lateinit var layoutSubMenu: LinearLayout
    private lateinit var btnRealtime: Button
    private lateinit var btnManual: Button
    private lateinit var btnExit: Button

    private var params: WindowManager.LayoutParams? = null
    private var isRealtimeActive = false
    private var isSubMenuVisible = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundServiceNotification()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_widget, null)

        fabMain = floatingView.findViewById(R.id.fabMain)
        layoutSubMenu = floatingView.findViewById(R.id.layoutSubMenu)
        btnRealtime = floatingView.findViewById(R.id.btnRealtime)
        btnManual = floatingView.findViewById(R.id.btnManual)
        btnExit = floatingView.findViewById(R.id.btnExit)

        setupWindowManagerParams()
        setupTouchAndDragListener()
        setupClickListeners()

        windowManager.addView(floatingView, params)
    }

    private fun setupWindowManagerParams() {
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 300
        }
    }

    private fun setupTouchAndDragListener() {
        fabMain.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isClick = true

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params!!.x
                        initialY = params!!.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isClick = true
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                            isClick = false
                        }
                        params!!.x = initialX + dx
                        params!!.y = initialY + dy
                        windowManager.updateViewLayout(floatingView, params)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (isClick) {
                            onFloatingButtonClicked()
                        }
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun onFloatingButtonClicked() {
        if (isRealtimeActive) {
            // Jika Real-Time sedang berjalan, klik tombol akan langsung menghentikan Real-Time
            stopRealtimeTranslation()
        } else {
            // Toggle ekspansi Sub-Menu
            isSubMenuVisible = !isSubMenuVisible
            layoutSubMenu.visibility = if (isSubMenuVisible) View.VISIBLE else View.GONE
        }
    }

    private fun setupClickListeners() {
        btnRealtime.setOnClickListener {
            startRealtimeTranslation()
        }

        btnManual.setOnClickListener {
            layoutSubMenu.visibility = View.GONE
            isSubMenuVisible = false
            triggerManualTranslation()
        }

        btnExit.setOnClickListener {
            stopSelf()
        }
    }

    private fun startRealtimeTranslation() {
        isRealtimeActive = true
        layoutSubMenu.visibility = View.GONE
        isSubMenuVisible = false
        fabMain.setImageResource(android.R.drawable.ic_media_pause)
        Toast.makeText(this, "Real-Time Translator Aktif", Toast.LENGTH_SHORT).show()
    }

    private fun stopRealtimeTranslation() {
        isRealtimeActive = false
        fabMain.setImageResource(android.R.drawable.ic_menu_compass)
        Toast.makeText(this, "Real-Time Translator Diberhentikan", Toast.LENGTH_SHORT).show()
    }

    private fun triggerManualTranslation() {
        Toast.makeText(this, "Memproses Terjemahan Layar...", Toast.LENGTH_SHORT).show()
        // Logika OCR & Overlay Teks Terjemahan akan dihubungkan di sini
    }

    private fun startForegroundServiceNotification() {
        val channelId = "screen_translator_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Screen Translator Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Screen Translator Running")
            .setContentText("Tombol melayang siap digunakan.")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()

        startForeground(1, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::floatingView.isInitialized) {
            windowManager.removeView(floatingView)
        }
    }
}
