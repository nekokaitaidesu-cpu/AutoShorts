package com.autoshorts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.core.app.NotificationCompat

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var settingsManager: SettingsManager
    private lateinit var btnToggle: Button
    private lateinit var tvCountdown: TextView

    private var isAutoEnabled = false
    private var countDownTimer: CountDownTimer? = null

    private val layoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = 16
        y = 200
    }

    // AutoSwipeService からのバー進捗・バーなしタイマーを受信
    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                AutoSwipeService.ACTION_BAR_PROGRESS -> {
                    val progress = intent.getIntExtra("progress", 0)
                    tvCountdown.text = "${progress}%"
                }
                AutoSwipeService.ACTION_NO_BAR_TICK -> {
                    val remaining = intent.getIntExtra("remaining", 0)
                    tvCountdown.text = "${remaining}s"
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        settingsManager = SettingsManager(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        setupOverlay()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopAll()
        unregisterStatusReceiver()
        if (::overlayView.isInitialized) {
            windowManager.removeView(overlayView)
        }
    }

    private fun setupOverlay() {
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_button, null)
        btnToggle  = overlayView.findViewById(R.id.btnAutoToggle)
        tvCountdown = overlayView.findViewById(R.id.tvCountdown)

        // シングルタップ：AUTO ON/OFF 切り替え
        btnToggle.setOnClickListener {
            isAutoEnabled = !isAutoEnabled
            if (isAutoEnabled) {
                setButtonOn()
                startAutoMode()
            } else {
                setButtonOff()
                stopAll()
            }
        }

        // ①長押し：アプリ（MainActivity）を開く
        btnToggle.setOnLongClickListener {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
            true
        }

        setupDrag()
        windowManager.addView(overlayView, layoutParams)
    }

    private fun startAutoMode() {
        when (settingsManager.autoMode) {
            SettingsManager.MODE_TIMER -> {
                tvCountdown.text = "--s"
                startTimerMode()
            }
            SettingsManager.MODE_VIDEO_COMPLETE -> {
                tvCountdown.text = "..."
                registerStatusReceiver()
                sendCommandToSwipeService(AutoSwipeService.ACTION_AUTO_START)
            }
        }
    }

    private fun stopAll() {
        // タイマーモード停止
        countDownTimer?.cancel()
        countDownTimer = null

        // VIDEO_COMPLETE モード停止
        sendCommandToSwipeService(AutoSwipeService.ACTION_AUTO_STOP)
        unregisterStatusReceiver()

        tvCountdown.text = "--s"
    }

    private fun startTimerMode() {
        val totalMs = settingsManager.timerSeconds * 1000L

        countDownTimer = object : CountDownTimer(totalMs, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val remaining = (millisUntilFinished / 1000).toInt() + 1
                tvCountdown.text = "${remaining}s"
            }
            override fun onFinish() {
                triggerSwipe()
                if (isAutoEnabled) startTimerMode()
            }
        }.start()
    }

    private fun triggerSwipe() {
        val intent = Intent(AutoSwipeService.ACTION_PERFORM_SWIPE).apply {
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    private fun sendCommandToSwipeService(action: String) {
        val intent = Intent(action).apply { setPackage(packageName) }
        sendBroadcast(intent)
    }

    // ───────────── ステータスレシーバー管理 ─────────────

    private var statusReceiverRegistered = false

    private fun registerStatusReceiver() {
        if (statusReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(AutoSwipeService.ACTION_BAR_PROGRESS)
            addAction(AutoSwipeService.ACTION_NO_BAR_TICK)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(statusReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(statusReceiver, filter)
        }
        statusReceiverRegistered = true
    }

    private fun unregisterStatusReceiver() {
        if (!statusReceiverRegistered) return
        try { unregisterReceiver(statusReceiver) } catch (_: Exception) {}
        statusReceiverRegistered = false
    }

    // ───────────── ボタン状態 ─────────────

    private fun setButtonOn() {
        btnToggle.text = "AUTO ON"
        btnToggle.backgroundTintList =
            android.content.res.ColorStateList.valueOf(0xFF00AA00.toInt())
    }

    private fun setButtonOff() {
        isAutoEnabled = false
        btnToggle.text = "AUTO"
        btnToggle.backgroundTintList =
            android.content.res.ColorStateList.valueOf(0xFF444444.toInt())
    }

    // ───────────── ドラッグ ─────────────

    private fun setupDrag() {
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f

        overlayView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    touchX = event.rawX
                    touchY = event.rawY
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    layoutParams.x = initialX + (event.rawX - touchX).toInt()
                    layoutParams.y = initialY + (event.rawY - touchY).toInt()
                    windowManager.updateViewLayout(overlayView, layoutParams)
                    true
                }
                else -> false
            }
        }
    }

    // ───────────── 通知 ─────────────

    private fun buildNotification(): Notification {
        val channelId = "autoshorts_overlay"
        val channel = NotificationChannel(
            channelId,
            "AutoShorts オーバーレイ",
            NotificationManager.IMPORTANCE_LOW
        )
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)

        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("AutoShorts 稼働中")
            .setContentText("YouTubeをAutoShortsが監視しています")
            .setSmallIcon(android.R.drawable.ic_media_ff)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
    }
}
