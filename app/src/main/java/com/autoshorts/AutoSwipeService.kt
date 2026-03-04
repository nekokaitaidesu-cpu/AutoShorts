package com.autoshorts

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import java.util.concurrent.Executors

class AutoSwipeService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val screenshotExecutor = Executors.newSingleThreadExecutor()
    private lateinit var settingsManager: SettingsManager

    // VIDEO_COMPLETE モード用の状態管理
    private var isWatching = false
    private var videoBarState = VideoBarState.UNKNOWN
    private var noBarCheckCount = 0
    private var noBarElapsedSeconds = 0

    // ループ検知用：直前のバー進捗を記憶
    private var lastCompletionRatio = 0f
    private var reachedNearEnd = false  // 80%以上に達したことがあるか

    // スクリーンショット取得ループ
    private val screenshotRunnable = Runnable {
        takeScreenshotIfWatching()
    }

    // バーなし動画のフォールバックタイマー
    private val noBarFallbackRunnable = object : Runnable {
        override fun run() {
            if (!isWatching || videoBarState != VideoBarState.NO_BAR) return
            noBarElapsedSeconds++
            val fallback = settingsManager.noBarFallbackSeconds
            if (noBarElapsedSeconds >= fallback) {
                Log.d(TAG, "No-bar fallback fired, swiping")
                performSwipe()
                resetWatchState()
                scheduleNextScreenshot(POST_SWIPE_DELAY_MS)
            } else {
                val remaining = fallback - noBarElapsedSeconds
                sendBroadcastToOverlay(ACTION_NO_BAR_TICK, "remaining", remaining)
                handler.postDelayed(this, 1000L)
            }
        }
    }

    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_PERFORM_SWIPE -> performSwipe()
                ACTION_AUTO_START    -> startWatching()
                ACTION_AUTO_STOP     -> stopWatching()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        settingsManager = SettingsManager(this)

        val filter = IntentFilter().apply {
            addAction(ACTION_PERFORM_SWIPE)
            addAction(ACTION_AUTO_START)
            addAction(ACTION_AUTO_STOP)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(commandReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(commandReceiver, filter)
        }
        Log.d(TAG, "AutoSwipeService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() { Log.d(TAG, "AutoSwipeService interrupted") }

    override fun onDestroy() {
        super.onDestroy()
        stopWatching()
        unregisterReceiver(commandReceiver)
        screenshotExecutor.shutdown()
    }

    // ───────────── 視聴完了検知 ─────────────

    private fun startWatching() {
        isWatching = true
        resetWatchState()
        scheduleNextScreenshot(0)
        Log.d(TAG, "Started watching")
    }

    private fun stopWatching() {
        isWatching = false
        handler.removeCallbacks(screenshotRunnable)
        handler.removeCallbacks(noBarFallbackRunnable)
        Log.d(TAG, "Stopped watching")
    }

    private fun resetWatchState() {
        videoBarState = VideoBarState.UNKNOWN
        noBarCheckCount = 0
        noBarElapsedSeconds = 0
        lastCompletionRatio = 0f
        reachedNearEnd = false
        handler.removeCallbacks(noBarFallbackRunnable)
    }

    private fun scheduleNextScreenshot(delayMs: Long = SCREENSHOT_INTERVAL_MS) {
        handler.removeCallbacks(screenshotRunnable)
        if (isWatching) handler.postDelayed(screenshotRunnable, delayMs)
    }

    private fun takeScreenshotIfWatching() {
        if (!isWatching) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            // API 30未満はスクリーンショット不可 → スケジュールのみ継続
            scheduleNextScreenshot()
            return
        }
        takeScreenshot(
            android.view.Display.DEFAULT_DISPLAY,
            screenshotExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    // HardwareBuffer → ARGB_8888 Bitmap に変換
                    val hwBitmap = Bitmap.wrapHardwareBuffer(
                        screenshot.hardwareBuffer, screenshot.colorSpace
                    )
                    screenshot.hardwareBuffer.close()
                    val bitmap = hwBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                    hwBitmap?.recycle()
                    if (bitmap != null) {
                        val result = analyzeProgressBar(bitmap)
                        bitmap.recycle()
                        handler.post { handleBarResult(result) }
                    } else {
                        handler.post { scheduleNextScreenshot() }
                    }
                }
                override fun onFailure(errorCode: Int) {
                    Log.w(TAG, "Screenshot failed: $errorCode")
                    handler.post { scheduleNextScreenshot() }
                }
            }
        )
    }

    private fun handleBarResult(result: BarAnalysisResult) {
        if (!isWatching) return
        val method = settingsManager.detectMethod
        when {
            result.barFound -> {
                // バーあり → 再生中
                if (videoBarState == VideoBarState.NO_BAR) {
                    handler.removeCallbacks(noBarFallbackRunnable)
                }
                if (videoBarState != VideoBarState.HAS_BAR) {
                    videoBarState = VideoBarState.HAS_BAR
                    noBarCheckCount = 0
                }

                val ratio = result.completionRatio

                // ── 検知方式: 90%閾値 ──
                if (method == SettingsManager.DETECT_THRESHOLD && ratio >= BAR_COMPLETE_THRESHOLD) {
                    Log.d(TAG, "Threshold reached (${ratio}), swiping")
                    performSwipe()
                    resetWatchState()
                    scheduleNextScreenshot(POST_SWIPE_DELAY_MS)
                    return
                }

                // ── 検知方式: ループ検知 ──
                if (method == SettingsManager.DETECT_LOOP) {
                    // 80%以上に到達したことを記憶
                    if (ratio >= LOOP_DETECT_NEAR_END) {
                        reachedNearEnd = true
                    }
                    // 「近端に達した後」かつ「バーが先頭（10%以下）に戻った」→ ループ検知
                    if (reachedNearEnd && ratio <= LOOP_DETECT_RESET && lastCompletionRatio > LOOP_DETECT_NEAR_END) {
                        Log.d(TAG, "Loop detected (${lastCompletionRatio} → ${ratio}), swiping")
                        performSwipe()
                        resetWatchState()
                        scheduleNextScreenshot(POST_SWIPE_DELAY_MS)
                        return
                    }
                }

                lastCompletionRatio = ratio
                val progress = (ratio * 100).toInt()
                sendBroadcastToOverlay(ACTION_BAR_PROGRESS, "progress", progress)
                scheduleNextScreenshot()
            }
            else -> {
                // バーが見つからない
                noBarCheckCount++
                if (videoBarState == VideoBarState.UNKNOWN && noBarCheckCount >= NO_BAR_CONFIRM_COUNT) {
                    videoBarState = VideoBarState.NO_BAR
                    noBarElapsedSeconds = 0
                    Log.d(TAG, "No bar confirmed, fallback timer: ${settingsManager.noBarFallbackSeconds}s")
                    handler.post(noBarFallbackRunnable)
                }
                // フォールバックタイマー中もスクリーンショット継続（バー出現を監視）
                scheduleNextScreenshot()
            }
        }
    }

    // ───────────── 赤いバー解析 ─────────────

    data class BarAnalysisResult(val barFound: Boolean, val completionRatio: Float)

    private fun analyzeProgressBar(bitmap: Bitmap): BarAnalysisResult {
        val width = bitmap.width
        val height = bitmap.height

        // 画面下部 75〜98% の範囲でスキャン（縦横両対応）
        val scanStartY = (height * 0.75).toInt()
        val scanEndY   = (height * 0.98).toInt()

        var bestRowY = -1
        var maxRedCount = 0

        // 最も赤いピクセルが多い行を探す（3ピクセルおきにサンプリング）
        for (y in scanStartY..scanEndY) {
            var redCount = 0
            for (x in 0 until width step 3) {
                if (isYoutubeRed(bitmap.getPixel(x, y))) redCount++
            }
            if (redCount > maxRedCount) {
                maxRedCount = redCount
                bestRowY = y
            }
        }

        // サンプリング幅の10%未満しか赤くなければバーなし
        val minThreshold = (width / 3) / 10
        if (maxRedCount < minThreshold || bestRowY < 0) {
            return BarAnalysisResult(false, 0f)
        }

        // bestRowY ±3行の範囲で左端・右端の赤ピクセルを探す
        var rightmostRed = 0
        var leftmostRed = width
        val rowRange = (bestRowY - 3..bestRowY + 3).filter { it in 0 until height }
        for (y in rowRange) {
            for (x in width - 1 downTo 0) {
                if (isYoutubeRed(bitmap.getPixel(x, y))) {
                    if (x > rightmostRed) rightmostRed = x
                    break
                }
            }
            for (x in 0 until width) {
                if (isYoutubeRed(bitmap.getPixel(x, y))) {
                    if (x < leftmostRed) leftmostRed = x
                    break
                }
            }
        }

        // 進捗バーは必ず左端（0付近）から始まる
        // 左端が10%以上離れていればアイコン等の誤検知とみなす
        if (leftmostRed > width * 0.10f) {
            return BarAnalysisResult(false, 0f)
        }

        // バーは最低でも幅の15%以上の広がりが必要（小さいアイコン等を除外）
        if (rightmostRed - leftmostRed < width * 0.15f) {
            return BarAnalysisResult(false, 0f)
        }

        val completionRatio = rightmostRed.toFloat() / width
        return BarAnalysisResult(true, completionRatio)
    }

    /** YouTube Shortsの進捗バーの赤色を判定 */
    private fun isYoutubeRed(pixel: Int): Boolean {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        return r > 180 && g < 70 && b < 70
    }

    // ───────────── スワイプ実行 ─────────────

    fun performSwipe() {
        val (screenWidth, screenHeight) = getScreenSize()
        val direction        = settingsManager.swipeDirection
        val distanceFraction = settingsManager.swipeDistanceFraction
        val durationMs       = settingsManager.swipeDurationMs.toLong()

        val centerX   = screenWidth  / 2f
        val centerY   = screenHeight / 2f
        val halfDistH = screenWidth  * distanceFraction / 2f
        val halfDistV = screenHeight * distanceFraction / 2f

        val (startX, startY, endX, endY) = when (direction) {
            SettingsManager.DIRECTION_UP    -> listOf(centerX, centerY + halfDistV, centerX, centerY - halfDistV)
            SettingsManager.DIRECTION_DOWN  -> listOf(centerX, centerY - halfDistV, centerX, centerY + halfDistV)
            SettingsManager.DIRECTION_LEFT  -> listOf(centerX + halfDistH, centerY, centerX - halfDistH, centerY)
            SettingsManager.DIRECTION_RIGHT -> listOf(centerX - halfDistH, centerY, centerX + halfDistH, centerY)
            else                            -> listOf(centerX, centerY + halfDistV, centerX, centerY - halfDistV)
        }

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "Swipe completed [$direction]")
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "Swipe cancelled")
            }
        }, null)
    }

    private fun getScreenSize(): Pair<Int, Int> {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            Pair(bounds.width(), bounds.height())
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)
            Pair(metrics.widthPixels, metrics.heightPixels)
        }
    }

    private fun sendBroadcastToOverlay(action: String, key: String, value: Int) {
        val intent = Intent(action).apply {
            setPackage(packageName)
            putExtra(key, value)
        }
        sendBroadcast(intent)
    }

    // ───────────── 定数・列挙 ─────────────

    enum class VideoBarState { UNKNOWN, HAS_BAR, NO_BAR }

    companion object {
        private const val TAG = "AutoSwipeService"

        const val ACTION_PERFORM_SWIPE = "com.autoshorts.ACTION_PERFORM_SWIPE"
        const val ACTION_AUTO_START    = "com.autoshorts.ACTION_AUTO_START"
        const val ACTION_AUTO_STOP     = "com.autoshorts.ACTION_AUTO_STOP"
        const val ACTION_BAR_PROGRESS  = "com.autoshorts.ACTION_BAR_PROGRESS"
        const val ACTION_NO_BAR_TICK   = "com.autoshorts.ACTION_NO_BAR_TICK"

        private const val SCREENSHOT_INTERVAL_MS  = 400L
        private const val POST_SWIPE_DELAY_MS     = 1500L
        private const val BAR_COMPLETE_THRESHOLD  = 0.95f  // 95%閾値モード用
        private const val LOOP_DETECT_NEAR_END    = 0.80f  // ループ検知: 80%以上で「終盤」とみなす
        private const val LOOP_DETECT_RESET       = 0.10f  // ループ検知: 10%以下で「リセット」とみなす
        private const val NO_BAR_CONFIRM_COUNT    = 4      // 約1.6秒でバーなし確定
    }
}
