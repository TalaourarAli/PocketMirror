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

        bindModeSelector()
        bindShapeSelector()
        setupPopupSliders()
        setupButtonAppearanceSliders()

        btnConfigurePosition.setOnClickListener {
            startActivity(Intent(this, ButtonPositionActivity::class.java))
        }

        switchTestAlways.isChecked = MirrorPrefs.getTestShowAlways(this)
        switchTestAlways.setOnCheckedChangeListener { _, isChecked ->
            MirrorPrefs.setTestShowAlways(this, isChecked)
            notifyServicePrefsChanged()
        }
    }

    private fun bindModeSelector() {
        val mode = MirrorPrefs.getInteractionMode(this)
        rgInteractionMode.check(
            if (mode == InteractionMode.TOGGLE) R.id.rbModeToggle else R.id.rbModeHold
        )
        rgInteractionMode.setOnCheckedChangeListener { _, checkedId ->
            MirrorPrefs.setInteractionMode(this, modeForRadio(checkedId))
            notifyServicePrefsChanged()
        }
    }

    private fun bindShapeSelector() {
        rgMirrorShape.check(radioForShape(MirrorPrefs.getMirrorShape(this)))
        rgMirrorShape.setOnCheckedChangeListener { _, checkedId ->
            MirrorPrefs.setMirrorShape(this, shapeForRadio(checkedId))
            notifyServicePrefsChanged()
        }
    }

    private fun modeForRadio(checkedId: Int): InteractionMode =
        if (checkedId == R.id.rbModeToggle) InteractionMode.TOGGLE else InteractionMode.HOLD

    private fun shapeForRadio(checkedId: Int): MirrorShape = when (checkedId) {
        R.id.rbShapeRoundedSq -> MirrorShape.ROUNDED_SQ
        R.id.rbShapePill -> MirrorShape.PILL
        else -> MirrorShape.CIRCLE
    }

    private fun radioForShape(shape: MirrorShape): Int = when (shape) {
        MirrorShape.ROUNDED_SQ -> R.id.rbShapeRoundedSq
        MirrorShape.PILL -> R.id.rbShapePill
        MirrorShape.CIRCLE -> R.id.rbShapeCircle
    }

    /**
     * Câble un SeekBar à une préférence entière : `render` met à jour le libellé,
     * `store` persiste la valeur (uniquement quand c'est l'utilisateur qui bouge).
     * Les quatre sliders faisaient ce travail à la main avec le même code copié.
     */
    private fun bindSlider(
        seekBar: SeekBar,
        initialValue: Int,
        min: Int,
        max: Int,
        render: (Int) -> Unit,
        store: (Int) -> Unit,
    ) {
        seekBar.max = max - min
        seekBar.progress = (initialValue - min).coerceIn(0, seekBar.max)
        render(initialValue)

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progress + min
                render(value)
                if (fromUser) store(value)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
    }

    private fun setupButtonAppearanceSliders() {
        bindSlider(
            seekBar = sbButtonSize,
            initialValue = MirrorPrefs.getButtonSizeDp(this),
            min = BUTTON_SIZE_MIN_DP,
            max = BUTTON_SIZE_MAX_DP,
            render = ::updateButtonSizeLabel,
            store = {
                MirrorPrefs.setButtonSizeDp(this, it)
                notifyServicePrefsChanged()
            },
        )

        bindSlider(
            seekBar = sbButtonAlpha,
            initialValue = (MirrorPrefs.getButtonAlpha(this) * 100f).toInt(),
            min = BUTTON_ALPHA_MIN_PERCENT,
            max = 100,
            render = { updateButtonAlphaLabel(it / 100f) },
            store = {
                MirrorPrefs.setButtonAlpha(this, it / 100f)
                notifyServicePrefsChanged()
            },
        )
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
        bindSlider(
            seekBar = sbPopupOffsetY,
            initialValue = MirrorPrefs.getPopupOffsetYDp(this),
            min = POPUP_OFFSET_Y_MIN_DP,
            max = POPUP_OFFSET_Y_MAX_DP,
            render = ::updateOffsetYLabel,
            store = {
                MirrorPrefs.setPopupOffsetYDp(this, it)
                notifyServicePrefsChanged()
            },
        )

        bindSlider(
            seekBar = sbPopupOffsetX,
            initialValue = MirrorPrefs.getPopupOffsetXDp(this),
            min = POPUP_OFFSET_X_MIN_DP,
            max = POPUP_OFFSET_X_MAX_DP,
            render = ::updateOffsetXLabel,
            store = {
                MirrorPrefs.setPopupOffsetXDp(this, it)
                notifyServicePrefsChanged()
            },
        )

        bindSlider(
            seekBar = sbPopupSize,
            initialValue = MirrorPrefs.getPopupSizeDp(this),
            min = POPUP_SIZE_MIN_DP,
            max = POPUP_SIZE_MAX_DP,
            render = ::updateSizeLabel,
            store = {
                MirrorPrefs.setPopupSizeDp(this, it)
                notifyServicePrefsChanged()
            },
        )

        btnResetPopupPos.setOnClickListener {
            MirrorPrefs.setPopupOffsetXDp(this, 0)
            MirrorPrefs.setPopupOffsetYDp(this, 0)
            MirrorPrefs.setPopupSizeDp(this, POPUP_SIZE_DEFAULT_DP)
            sbPopupOffsetY.progress = -POPUP_OFFSET_Y_MIN_DP
            sbPopupOffsetX.progress = -POPUP_OFFSET_X_MIN_DP
            sbPopupSize.progress = POPUP_SIZE_DEFAULT_DP - POPUP_SIZE_MIN_DP
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

    private fun notifyServicePrefsChanged() = MirrorPrefs.notifyChanged(this)

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun isAccessibilityEnabled(): Boolean {
        if (LockScreenMirrorAccessibilityService.isRunning) return true
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

    private companion object {
        const val BUTTON_SIZE_MIN_DP = 36
        const val BUTTON_SIZE_MAX_DP = 84
        const val BUTTON_ALPHA_MIN_PERCENT = 20
        const val POPUP_OFFSET_Y_MIN_DP = -40
        const val POPUP_OFFSET_Y_MAX_DP = 200
        const val POPUP_OFFSET_X_MIN_DP = -120
        const val POPUP_OFFSET_X_MAX_DP = 120
        const val POPUP_SIZE_MIN_DP = 180
        const val POPUP_SIZE_MAX_DP = 380
        const val POPUP_SIZE_DEFAULT_DP = 300
    }
}
