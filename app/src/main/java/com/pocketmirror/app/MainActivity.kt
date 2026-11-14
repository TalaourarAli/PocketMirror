package com.pocketmirror.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.switchmaterial.SwitchMaterial

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var btnCameraPerm: Button
    private lateinit var btnAccessibility: Button
    private lateinit var rgInteractionMode: RadioGroup
    private lateinit var rgMirrorShape: RadioGroup
    private lateinit var tvPopupOffsetY: TextView
    private lateinit var sbPopupOffsetY: SeekBar
    private lateinit var tvPopupOffsetX: TextView
    private lateinit var sbPopupOffsetX: SeekBar
    private lateinit var tvPopupSize: TextView
    private lateinit var sbPopupSize: SeekBar
    private lateinit var btnResetPopupPos: Button
    private lateinit var btnConfigurePosition: Button
    private lateinit var tvButtonSize: TextView
    private lateinit var sbButtonSize: SeekBar
    private lateinit var tvButtonAlpha: TextView
    private lateinit var sbButtonAlpha: SeekBar
    private lateinit var switchTestAlways: SwitchMaterial

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        updateUi()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        btnCameraPerm = findViewById(R.id.btnCameraPerm)
        btnAccessibility = findViewById(R.id.btnAccessibility)
        rgInteractionMode = findViewById(R.id.rgInteractionMode)
        rgMirrorShape = findViewById(R.id.rgMirrorShape)
        tvPopupOffsetY = findViewById(R.id.tvPopupOffsetY)
        sbPopupOffsetY = findViewById(R.id.sbPopupOffsetY)
        tvPopupOffsetX = findViewById(R.id.tvPopupOffsetX)
        sbPopupOffsetX = findViewById(R.id.sbPopupOffsetX)
        tvPopupSize = findViewById(R.id.tvPopupSize)
        sbPopupSize = findViewById(R.id.sbPopupSize)
        btnResetPopupPos = findViewById(R.id.btnResetPopupPos)
        btnConfigurePosition = findViewById(R.id.btnConfigurePosition)
        tvButtonSize = findViewById(R.id.tvButtonSize)
        sbButtonSize = findViewById(R.id.sbButtonSize)
        tvButtonAlpha = findViewById(R.id.tvButtonAlpha)
        sbButtonAlpha = findViewById(R.id.sbButtonAlpha)
        switchTestAlways = findViewById(R.id.switchTestAlways)

        btnCameraPerm.setOnClickListener {
            cameraLauncher.launch(Manifest.permission.CAMERA)
        }

        btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        when (MirrorPrefs.getInteractionMode(this)) {
            MirrorPrefs.MODE_TOGGLE -> rgInteractionMode.check(R.id.rbModeToggle)
            else -> rgInteractionMode.check(R.id.rbModeHold)
        }
        rgInteractionMode.setOnCheckedChangeListener { _, checkedId ->
            val mode = if (checkedId == R.id.rbModeToggle) {
                MirrorPrefs.MODE_TOGGLE
            } else {
                MirrorPrefs.MODE_HOLD
            }
            MirrorPrefs.setInteractionMode(this, mode)
            notifyServicePrefsChanged()
        }

        when (MirrorPrefs.getMirrorShape(this)) {
            MirrorPrefs.SHAPE_ROUNDED_SQ -> rgMirrorShape.check(R.id.rbShapeRoundedSq)
            MirrorPrefs.SHAPE_PILL -> rgMirrorShape.check(R.id.rbShapePill)
            else -> rgMirrorShape.check(R.id.rbShapeCircle)
        }
        rgMirrorShape.setOnCheckedChangeListener { _, checkedId ->
            val shape = when (checkedId) {
                R.id.rbShapeRoundedSq -> MirrorPrefs.SHAPE_ROUNDED_SQ
                R.id.rbShapePill -> MirrorPrefs.SHAPE_PILL
                else -> MirrorPrefs.SHAPE_CIRCLE
            }
            MirrorPrefs.setMirrorShape(this, shape)
            notifyServicePrefsChanged()
        }

        setupPopupSliders()

        btnConfigurePosition.setOnClickListener {
            startActivity(Intent(this, ButtonPositionActivity::class.java))
        }

        setupButtonAppearanceSliders()

        switchTestAlways.isChecked = MirrorPrefs.getTestShowAlways(this)
        switchTestAlways.setOnCheckedChangeListener { _, isChecked ->
            MirrorPrefs.setTestShowAlways(this, isChecked)
            notifyServicePrefsChanged()
        }
    }

    private fun setupButtonAppearanceSliders() {
        // Taille : 36..84 dp (progress 0..48)
        val initSize = MirrorPrefs.getButtonSizeDp(this)
        sbButtonSize.progress = (initSize - 36).coerceIn(0, 48)
        updateButtonSizeLabel(initSize)

        sbButtonSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val dp = progress + 36
                updateButtonSizeLabel(dp)
                if (fromUser) {
                    MirrorPrefs.setButtonSizeDp(this@MainActivity, dp)
                    notifyServicePrefsChanged()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Transparence : 20..100 % (progress 0..80 => alpha 0.2..1.0)
        val initAlpha = MirrorPrefs.getButtonAlpha(this)
        sbButtonAlpha.progress = ((initAlpha * 100f).toInt() - 20).coerceIn(0, 80)
        updateButtonAlphaLabel(initAlpha)

        sbButtonAlpha.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val alpha = (progress + 20) / 100f
                updateButtonAlphaLabel(alpha)
                if (fromUser) {
                    MirrorPrefs.setButtonAlpha(this@MainActivity, alpha)
                    notifyServicePrefsChanged()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun updateButtonSizeLabel(dp: Int) {
        tvButtonSize.text = "Taille du bouton : $dp dp"
    }

    private fun updateButtonAlphaLabel(alpha: Float) {
        val percent = (alpha * 100).toInt()
        val hint = when {
            percent >= 95 -> "opaque"
            percent >= 60 -> "léger"
            percent >= 35 -> "discret"
            else -> "très discret"
        }
        tvButtonAlpha.text = "Transparence du bouton : $percent % ($hint)"
    }

    private fun setupPopupSliders() {
        // Offset Y : plage -40..+200 dp (progress 0..240)
        val initY = MirrorPrefs.getPopupOffsetYDp(this)
        sbPopupOffsetY.progress = (initY + 40).coerceIn(0, 240)
        updateOffsetYLabel(initY)

        sbPopupOffsetY.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val dp = progress - 40
                updateOffsetYLabel(dp)
                if (fromUser) {
                    MirrorPrefs.setPopupOffsetYDp(this@MainActivity, dp)
                    notifyServicePrefsChanged()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Offset X : plage -120..+120 dp (progress 0..240)
        val initX = MirrorPrefs.getPopupOffsetXDp(this)
        sbPopupOffsetX.progress = (initX + 120).coerceIn(0, 240)
        updateOffsetXLabel(initX)

        sbPopupOffsetX.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val dp = progress - 120
                updateOffsetXLabel(dp)
                if (fromUser) {
                    MirrorPrefs.setPopupOffsetXDp(this@MainActivity, dp)
                    notifyServicePrefsChanged()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Taille : plage 180..380 dp (progress 0..200)
        val initSize = MirrorPrefs.getPopupSizeDp(this)
        sbPopupSize.progress = (initSize - 180).coerceIn(0, 200)
        updateSizeLabel(initSize)

        sbPopupSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val dp = progress + 180
                updateSizeLabel(dp)
                if (fromUser) {
                    MirrorPrefs.setPopupSizeDp(this@MainActivity, dp)
                    notifyServicePrefsChanged()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnResetPopupPos.setOnClickListener {
            MirrorPrefs.setPopupOffsetXDp(this, 0)
            MirrorPrefs.setPopupOffsetYDp(this, 0)
            MirrorPrefs.setPopupSizeDp(this, 300)
            sbPopupOffsetY.progress = 40
            sbPopupOffsetX.progress = 120
            sbPopupSize.progress = 120
            updateOffsetYLabel(0)
            updateOffsetXLabel(0)
            updateSizeLabel(300)
            notifyServicePrefsChanged()
        }
    }

    private fun updateOffsetYLabel(dp: Int) {
        val sign = if (dp > 0) "+$dp" else "$dp"
        tvPopupOffsetY.text = "Hauteur (0 = collé à la caméra) : $sign dp"
    }

    private fun updateOffsetXLabel(dp: Int) {
        val sign = if (dp > 0) "+$dp" else "$dp"
        tvPopupOffsetX.text = "Décalage Gauche / Droite : $sign dp"
    }

    private fun updateSizeLabel(dp: Int) {
        tvPopupSize.text = "Taille de la popup : $dp dp"
    }

    override fun onResume() {
        super.onResume()
        updateUi()
    }

    private fun notifyServicePrefsChanged() {
        sendBroadcast(Intent(MirrorPrefs.ACTION_PREFS_CHANGED).setPackage(packageName))
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun isAccessibilityEnabled(): Boolean {
        if (LockScreenMirrorAccessibilityService.isConnectedService) return true
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.contains("$packageName/${LockScreenMirrorAccessibilityService::class.java.name}") ||
            enabled.contains("$packageName/.${LockScreenMirrorAccessibilityService::class.java.simpleName}")
    }

    private fun updateUi() {
        val camOk = hasCameraPermission()
        val accOk = isAccessibilityEnabled()

        tvStatus.text = buildString {
            append("Caméra frontale : ")
            appendLine(if (camOk) "✅ Autorisée" else "❌ À autoriser")
            append("Service écran verrouillé : ")
            append(if (accOk) "✅ Actif" else "❌ Inactif")
        }

        btnCameraPerm.isEnabled = !camOk
    }
}
