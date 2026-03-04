package com.autoshorts

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("autoshorts_prefs", Context.MODE_PRIVATE)

    var timerSeconds: Int
        get() = prefs.getInt("timer_seconds", DEFAULT_TIMER)
        set(value) = prefs.edit().putInt("timer_seconds", value.coerceIn(MIN_TIMER, MAX_TIMER)).apply()

    var swipeDurationMs: Int
        get() = prefs.getInt("swipe_duration_ms", DEFAULT_SWIPE_DURATION)
        set(value) = prefs.edit().putInt("swipe_duration_ms", value.coerceIn(MIN_SWIPE_DURATION, MAX_SWIPE_DURATION)).apply()

    var swipeDistanceFraction: Float
        get() = prefs.getFloat("swipe_distance_fraction", DEFAULT_SWIPE_DISTANCE)
        set(value) = prefs.edit().putFloat("swipe_distance_fraction", value.coerceIn(MIN_SWIPE_DISTANCE, MAX_SWIPE_DISTANCE)).apply()

    var swipeDirection: String
        get() = prefs.getString("swipe_direction", DIRECTION_UP) ?: DIRECTION_UP
        set(value) = prefs.edit().putString("swipe_direction", value).apply()

    var autoMode: String
        get() = prefs.getString("auto_mode", MODE_TIMER) ?: MODE_TIMER
        set(value) = prefs.edit().putString("auto_mode", value).apply()

    var detectMethod: String
        get() = prefs.getString("detect_method", DETECT_THRESHOLD) ?: DETECT_THRESHOLD
        set(value) = prefs.edit().putString("detect_method", value).apply()

    var noBarFallbackSeconds: Int
        get() = prefs.getInt("no_bar_fallback_seconds", DEFAULT_NO_BAR_FALLBACK)
        set(value) = prefs.edit().putInt("no_bar_fallback_seconds", value.coerceIn(MIN_NO_BAR_FALLBACK, MAX_NO_BAR_FALLBACK)).apply()

    companion object {
        const val DEFAULT_TIMER = 60
        const val MIN_TIMER = 3
        const val MAX_TIMER = 300

        const val DEFAULT_SWIPE_DURATION = 300
        const val MIN_SWIPE_DURATION = 100
        const val MAX_SWIPE_DURATION = 800

        const val DEFAULT_SWIPE_DISTANCE = 0.4f
        const val MIN_SWIPE_DISTANCE = 0.2f
        const val MAX_SWIPE_DISTANCE = 0.8f

        const val DIRECTION_UP = "UP"
        const val DIRECTION_DOWN = "DOWN"
        const val DIRECTION_LEFT = "LEFT"
        const val DIRECTION_RIGHT = "RIGHT"

        const val MODE_TIMER = "TIMER"
        const val MODE_VIDEO_COMPLETE = "VIDEO_COMPLETE"

        // VIDEO_COMPLETE モード内の検知方式
        const val DETECT_THRESHOLD = "THRESHOLD"   // 90%到達でスワイプ
        const val DETECT_LOOP     = "LOOP"         // ループ(バーリセット)検知でスワイプ

        const val DEFAULT_NO_BAR_FALLBACK = 10
        const val MIN_NO_BAR_FALLBACK = 3
        const val MAX_NO_BAR_FALLBACK = 60
    }
}
