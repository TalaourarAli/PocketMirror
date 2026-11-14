package com.pocketmirror.app

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewAnimationUtils
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import kotlin.math.hypot

class MirrorLockActivity : AppCompatActivity() {

    private lateinit var circleMirrorCard: View
    private lateinit var previewView: PreviewView
    private var cameraProvider: ProcessCameraProvider? = null
    private var isClosing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        setContentView(R.layout.activity_mirror_lock)

        val root = findViewById<View>(R.id.lockMirrorRoot)
        circleMirrorCard = findViewById(R.id.circleMirrorCard)
        previewView = findViewById(R.id.lockPreviewView)

        // Mode miroir horizontal
        previewView.scaleX = -1f

        root.setOnClickListener {
            closeWithAnimation()
        }

        circleMirrorCard.post {
            val centerX = circleMirrorCard.width / 2
            val finalRadius = hypot(
                circleMirrorCard.width.toDouble(),
                circleMirrorCard.height.toDouble()
            ).toFloat()

            ViewAnimationUtils.createCircularReveal(
                circleMirrorCard,
                centerX,
                0,
                0f,
                finalRadius
            ).apply {
                duration = 300
                interpolator = AccelerateDecelerateInterpolator()
                start()
            }
        }

        startFrontCamera()
    }

    private fun startFrontCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            cameraProvider = provider
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            runCatching {
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun closeWithAnimation() {
        if (isClosing) return
        isClosing = true
        cameraProvider?.unbindAll()

        val centerX = circleMirrorCard.width / 2
        val startRadius = hypot(
            circleMirrorCard.width.toDouble(),
            circleMirrorCard.height.toDouble()
        ).toFloat()

        ViewAnimationUtils.createCircularReveal(
            circleMirrorCard,
            centerX,
            0,
            startRadius,
            0f
        ).apply {
            duration = 220
            interpolator = AccelerateDecelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    finish()
                    overridePendingTransition(0, 0)
                }
            })
            start()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraProvider?.unbindAll()
    }
}
