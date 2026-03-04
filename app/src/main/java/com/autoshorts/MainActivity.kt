package com.autoshorts

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var settingsManager: SettingsManager

    // 既存のビュー
    private lateinit var tvOverlayStatus: TextView
    private lateinit var tvAccessibilityStatus: TextView
    private lateinit var tvTimerValue: TextView
    private lateinit var seekBarTimer: SeekBar

    // 自動モード
    private lateinit var rgAutoMode: RadioGroup
    private lateinit var rbTimerMode: RadioButton
    private lateinit var rbVideoCompleteMode: RadioButton
    private lateinit var layoutTimerSettings: LinearLayout
    private lateinit var layoutVideoCompleteSettings: LinearLayout
    private lateinit var tvApiWarning: TextView

    // 検知方式
    private lateinit var rgDetectMethod: RadioGroup
    private lateinit var rbDetectThreshold: RadioButton
    private lateinit var rbDetectLoop: RadioButton
    private lateinit var layoutThresholdSlider: LinearLayout
    private lateinit var tvBarThresholdValue: TextView
    private lateinit var seekBarBarThreshold: SeekBar

    // バーなしフォールバック
    private lateinit var tvNoBarFallbackValue: TextView
    private lateinit var seekBarNoBarFallback: SeekBar

    // スワイプ設定
    private lateinit var rgSwipeDirection: RadioGroup
    private lateinit var rbSwipeUp: RadioButton
    private lateinit var rbSwipeDown: RadioButton
    private lateinit var rbSwipeLeft: RadioButton
    private lateinit var rbSwipeRight: RadioButton
    private lateinit var tvSwipeSpeedValue: TextView
    private lateinit var seekBarSwipeSpeed: SeekBar
    private lateinit var tvSwipeDistanceValue: TextView
    private lateinit var seekBarSwipeDistance: SeekBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        settingsManager = SettingsManager(this)

        bindViews()
        setupAutoModeSection()
        setupDetectMethodSection()
        setupBarThresholdSeekBar()
        setupTimerSeekBar()
        setupNoBarFallbackSeekBar()
        setupSwipeSettings()
        setupButtons()
    }

    override fun onResume() {
        super.onResume()
        updateStatusViews()
    }

    private fun bindViews() {
        tvOverlayStatus        = findViewById(R.id.tvOverlayStatus)
        tvAccessibilityStatus  = findViewById(R.id.tvAccessibilityStatus)
        tvTimerValue           = findViewById(R.id.tvTimerValue)
        seekBarTimer           = findViewById(R.id.seekBarTimer)

        rgAutoMode                   = findViewById(R.id.rgAutoMode)
        rbTimerMode                  = findViewById(R.id.rbTimerMode)
        rbVideoCompleteMode          = findViewById(R.id.rbVideoCompleteMode)
        layoutTimerSettings          = findViewById(R.id.layoutTimerSettings)
        layoutVideoCompleteSettings  = findViewById(R.id.layoutVideoCompleteSettings)
        tvApiWarning                 = findViewById(R.id.tvApiWarning)

        rgDetectMethod         = findViewById(R.id.rgDetectMethod)
        rbDetectThreshold      = findViewById(R.id.rbDetectThreshold)
        rbDetectLoop           = findViewById(R.id.rbDetectLoop)
        layoutThresholdSlider  = findViewById(R.id.layoutThresholdSlider)
        tvBarThresholdValue    = findViewById(R.id.tvBarThresholdValue)
        seekBarBarThreshold    = findViewById(R.id.seekBarBarThreshold)

        tvNoBarFallbackValue  = findViewById(R.id.tvNoBarFallbackValue)
        seekBarNoBarFallback  = findViewById(R.id.seekBarNoBarFallback)

        rgSwipeDirection    = findViewById(R.id.rgSwipeDirection)
        rbSwipeUp           = findViewById(R.id.rbSwipeUp)
        rbSwipeDown         = findViewById(R.id.rbSwipeDown)
        rbSwipeLeft         = findViewById(R.id.rbSwipeLeft)
        rbSwipeRight        = findViewById(R.id.rbSwipeRight)
        tvSwipeSpeedValue   = findViewById(R.id.tvSwipeSpeedValue)
        seekBarSwipeSpeed   = findViewById(R.id.seekBarSwipeSpeed)
        tvSwipeDistanceValue = findViewById(R.id.tvSwipeDistanceValue)
        seekBarSwipeDistance = findViewById(R.id.seekBarSwipeDistance)
    }

    // ───────────── 自動モード ─────────────

    private fun setupAutoModeSection() {
        // 保存済みの設定を反映
        when (settingsManager.autoMode) {
            SettingsManager.MODE_VIDEO_COMPLETE -> {
                rbVideoCompleteMode.isChecked = true
                showVideoCompleteSettings()
            }
            else -> {
                rbTimerMode.isChecked = true
                showTimerSettings()
            }
        }

        rgAutoMode.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rbTimerMode -> {
                    settingsManager.autoMode = SettingsManager.MODE_TIMER
                    showTimerSettings()
                }
                R.id.rbVideoCompleteMode -> {
                    settingsManager.autoMode = SettingsManager.MODE_VIDEO_COMPLETE
                    showVideoCompleteSettings()
                }
            }
        }
    }

    private fun showTimerSettings() {
        layoutTimerSettings.visibility = View.VISIBLE
        layoutVideoCompleteSettings.visibility = View.GONE
    }

    private fun showVideoCompleteSettings() {
        layoutTimerSettings.visibility = View.GONE
        layoutVideoCompleteSettings.visibility = View.VISIBLE
        tvApiWarning.visibility =
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) View.VISIBLE else View.GONE
    }

    // ───────────── 検知方式 ─────────────

    private fun setupDetectMethodSection() {
        val isLoop = settingsManager.detectMethod == SettingsManager.DETECT_LOOP
        if (isLoop) rbDetectLoop.isChecked = true else rbDetectThreshold.isChecked = true
        layoutThresholdSlider.visibility = if (isLoop) View.GONE else View.VISIBLE

        rgDetectMethod.setOnCheckedChangeListener { _, checkedId ->
            val loop = checkedId == R.id.rbDetectLoop
            settingsManager.detectMethod = if (loop) SettingsManager.DETECT_LOOP else SettingsManager.DETECT_THRESHOLD
            layoutThresholdSlider.visibility = if (loop) View.GONE else View.VISIBLE
        }
    }

    // ───────────── 閾値スライダー ─────────────

    private fun setupBarThresholdSeekBar() {
        val current = settingsManager.barCompleteThresholdPercent
        // seekBar: 0〜50 → 50%〜100%
        seekBarBarThreshold.progress = current - SettingsManager.MIN_BAR_THRESHOLD
        tvBarThresholdValue.text = "${current}% に達したらスワイプ"

        seekBarBarThreshold.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val percent = progress + SettingsManager.MIN_BAR_THRESHOLD
                settingsManager.barCompleteThresholdPercent = percent
                tvBarThresholdValue.text = "${percent}% に達したらスワイプ"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    // ───────────── タイマー設定 ─────────────

    private fun setupTimerSeekBar() {
        val current = settingsManager.timerSeconds
        seekBarTimer.progress = current - SettingsManager.MIN_TIMER
        tvTimerValue.text = "${current} 秒ごとに次へ"

        seekBarTimer.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val seconds = progress + SettingsManager.MIN_TIMER
                settingsManager.timerSeconds = seconds
                tvTimerValue.text = "${seconds} 秒ごとに次へ"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    // ───────────── バーなしフォールバック ─────────────

    private fun setupNoBarFallbackSeekBar() {
        val current = settingsManager.noBarFallbackSeconds
        seekBarNoBarFallback.progress = current - SettingsManager.MIN_NO_BAR_FALLBACK
        tvNoBarFallbackValue.text = "${current} 秒後にスワイプ"

        seekBarNoBarFallback.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val seconds = progress + SettingsManager.MIN_NO_BAR_FALLBACK
                settingsManager.noBarFallbackSeconds = seconds
                tvNoBarFallbackValue.text = "${seconds} 秒後にスワイプ"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    // ───────────── スワイプ設定 ─────────────

    private fun setupSwipeSettings() {
        // 方向
        when (settingsManager.swipeDirection) {
            SettingsManager.DIRECTION_UP    -> rbSwipeUp.isChecked    = true
            SettingsManager.DIRECTION_DOWN  -> rbSwipeDown.isChecked  = true
            SettingsManager.DIRECTION_LEFT  -> rbSwipeLeft.isChecked  = true
            SettingsManager.DIRECTION_RIGHT -> rbSwipeRight.isChecked = true
        }
        rgSwipeDirection.setOnCheckedChangeListener { _, checkedId ->
            settingsManager.swipeDirection = when (checkedId) {
                R.id.rbSwipeUp    -> SettingsManager.DIRECTION_UP
                R.id.rbSwipeDown  -> SettingsManager.DIRECTION_DOWN
                R.id.rbSwipeLeft  -> SettingsManager.DIRECTION_LEFT
                R.id.rbSwipeRight -> SettingsManager.DIRECTION_RIGHT
                else              -> SettingsManager.DIRECTION_UP
            }
        }

        // 速度 (seekBar: 0〜700 → 100〜800ms)
        val currentSpeed = settingsManager.swipeDurationMs
        seekBarSwipeSpeed.progress = currentSpeed - SettingsManager.MIN_SWIPE_DURATION
        tvSwipeSpeedValue.text = "スワイプ速度: ${currentSpeed}ms"

        seekBarSwipeSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val ms = progress + SettingsManager.MIN_SWIPE_DURATION
                settingsManager.swipeDurationMs = ms
                tvSwipeSpeedValue.text = "スワイプ速度: ${ms}ms"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 距離 (seekBar: 0〜12 → 20%〜80%, step 5%)
        val currentDistPercent = (settingsManager.swipeDistanceFraction * 100).toInt()
        seekBarSwipeDistance.progress = (currentDistPercent - 20) / 5
        tvSwipeDistanceValue.text = "スワイプ距離: ${currentDistPercent}%"

        seekBarSwipeDistance.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val percent = 20 + progress * 5
                settingsManager.swipeDistanceFraction = percent / 100f
                tvSwipeDistanceValue.text = "スワイプ距離: ${percent}%"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    // ───────────── ボタン ─────────────

    private fun setupButtons() {
        findViewById<Button>(R.id.btnOverlayPermission).setOnClickListener {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }

        findViewById<Button>(R.id.btnAccessibilitySettings).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.btnStartService).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                tvOverlayStatus.text = "❌ 先にオーバーレイ許可を与えてください"
                return@setOnClickListener
            }
            startForegroundService(Intent(this, OverlayService::class.java))
        }

        findViewById<Button>(R.id.btnStopService).setOnClickListener {
            stopService(Intent(this, OverlayService::class.java))
        }
    }

    // ───────────── ステータス表示 ─────────────

    private fun updateStatusViews() {
        if (Settings.canDrawOverlays(this)) {
            tvOverlayStatus.text = "✅ 許可済み"
            tvOverlayStatus.setTextColor(0xFF00CC00.toInt())
        } else {
            tvOverlayStatus.text = "❌ 未許可"
            tvOverlayStatus.setTextColor(0xFFFF5555.toInt())
        }

        if (isAccessibilityEnabled()) {
            tvAccessibilityStatus.text = "✅ 有効"
            tvAccessibilityStatus.setTextColor(0xFF00CC00.toInt())
        } else {
            tvAccessibilityStatus.text = "❌ 無効 — 設定から手動でONにしてください"
            tvAccessibilityStatus.setTextColor(0xFFFF5555.toInt())
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val am = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        val services = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        return services.any { it.resolveInfo.serviceInfo.packageName == packageName }
    }
}
