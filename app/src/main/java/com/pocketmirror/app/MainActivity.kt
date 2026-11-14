package com.pocketmirror.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.switchmaterial.SwitchMaterial

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var btnCameraPerm: Button
    private lateinit var btnAccessibility: Button
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
        switchTestAlways = findViewById(R.id.switchTestAlways)

        btnCameraPerm.setOnClickListener {
            cameraLauncher.launch(Manifest.permission.CAMERA)
        }

        btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        switchTestAlways.isChecked = LockScreenMirrorAccessibilityService.testShowAlways
        switchTestAlways.setOnCheckedChangeListener { _, isChecked ->
            LockScreenMirrorAccessibilityService.testShowAlways = isChecked
            sendBroadcast(Intent(Intent.ACTION_USER_PRESENT))
        }
    }

    override fun onResume() {
        super.onResume()
        updateUi()
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
            append("Bouton écran verrouillé : ")
            append(if (accOk) "✅ Actif (Press & hold en bas)" else "❌ Inactif")
        }

        btnCameraPerm.isEnabled = !camOk
        btnAccessibility.text = if (accOk) {
            "Paramètres écran verrouillé (Actif)"
        } else {
            "2. Activer sur l'écran de verrouillage"
        }
    }
}
