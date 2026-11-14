package com.pocketmirror.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var btnPermissions: Button
    private lateinit var btnToggleService: Button

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        updateUi()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        btnPermissions = findViewById(R.id.btnPermissions)
        btnToggleService = findViewById(R.id.btnToggleService)

        btnPermissions.setOnClickListener {
            requestMissingPermissions()
        }

        btnToggleService.setOnClickListener {
            if (!hasAllPermissions()) {
                requestMissingPermissions()
                return@setOnClickListener
            }
            val intent = Intent(this, MirrorOverlayService::class.java)
            if (MirrorOverlayService.isRunning) {
                stopService(intent)
                btnToggleService.postDelayed({ updateUi() }, 150)
            } else {
                ContextCompat.startForegroundService(this, intent)
                btnToggleService.postDelayed({ updateUi() }, 150)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateUi()
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun hasOverlayPermission(): Boolean = Settings.canDrawOverlays(this)

    private fun hasNotificationPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    private fun hasAllPermissions(): Boolean =
        hasCameraPermission() && hasOverlayPermission() && hasNotificationPermission()

    private fun requestMissingPermissions() {
        val perms = mutableListOf<String>()
        if (!hasCameraPermission()) perms.add(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission()) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (perms.isNotEmpty()) {
            permissionLauncher.launch(perms.toTypedArray())
            return
        }
        if (!hasOverlayPermission()) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    private fun updateUi() {
        val cam = if (hasCameraPermission()) "OK" else "Manquante"
        val overlay = if (hasOverlayPermission()) "OK" else "Manquante"
        val service = if (MirrorOverlayService.isRunning) "Actif" else "Inactif"

        tvStatus.text = "Caméra : $cam\nSuperposition (Overlay) : $overlay\nBouton miroir : $service"
        btnPermissions.isEnabled = !hasAllPermissions()
        btnToggleService.text = if (MirrorOverlayService.isRunning) {
            "Désactiver le bouton miroir"
        } else {
            "Activer le bouton miroir"
        }
    }
}
