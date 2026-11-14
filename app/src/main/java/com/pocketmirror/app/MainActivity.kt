package com.pocketmirror.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.RadioGroup
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
    private lateinit var btnConfigurePosition: Button
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
        btnConfigurePosition = findViewById(R.id.btnConfigurePosition)
        switchTestAlways = findViewById(R.id.switchTestAlways)

        btnCameraPerm.setOnClickListener {
            cameraLauncher.launch(Manifest.permission.CAMERA)
        }

        btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        // Initialiser le mode d'ouverture
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

        // Initialiser la forme
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

        // Page dédiée à l'emplacement du bouton
        btnConfigurePosition.setOnClickListener {
            startActivity(Intent(this, ButtonPositionActivity::class.java))
        }

        switchTestAlways.isChecked = MirrorPrefs.getTestShowAlways(this)
        switchTestAlways.setOnCheckedChangeListener { _, isChecked ->
            MirrorPrefs.setTestShowAlways(this, isChecked)
            notifyServicePrefsChanged()
        }
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
