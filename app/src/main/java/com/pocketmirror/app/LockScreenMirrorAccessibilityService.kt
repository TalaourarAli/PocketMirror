package com.pocketmirror.app

import android.accessibilityservice.AccessibilityService
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Outline
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewAnimationUtils
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry

class LockScreenMirrorAccessibilityService : AccessibilityService(), LifecycleOwner {

    companion object {
        @Volatile
        var isConnectedService: Boolean = false

        @Volatile
        var testShowAlways: Boolean = false
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private lateinit var windowManager: WindowManager
    private lateinit var keyguardManager: KeyguardManager

    private var buttonView: View? = null
    private var mirrorContainerWindow: View? = null
    private var circleCard: View? = null
    private var previewView: PreviewView? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var isMirrorOpen = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON,
                Intent.ACTION_USER_PRESENT -> updateLockButtonVisibility()
                Intent.ACTION_SCREEN_OFF -> {
                    closeMirrorImmediate()
                    updateLockButtonVisibility()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenReceiver, filter)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isConnectedService = true
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        ensureButtonCreated()
        updateLockButtonVisibility()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        updateLockButtonVisibility()
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        isConnectedService = false
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        runCatching { unregisterReceiver(screenReceiver) }
        closeMirrorImmediate()
        buttonView?.let { runCatching { windowManager.removeView(it) } }
        buttonView = null
        super.onDestroy()
    }

    private fun updateLockButtonVisibility() {
        val btn = buttonView ?: return
        val shouldShow = keyguardManager.isKeyguardLocked || testShowAlways
        btn.visibility = if (shouldShow) View.VISIBLE else View.GONE
        if (!shouldShow && isMirrorOpen) {
            closeMirrorImmediate()
        }
    }

    private fun ensureButtonCreated() {
        if (buttonView != null) return

        val view = LayoutInflater.from(this).inflate(R.layout.overlay_lock_button, null)
        val density = resources.displayMetrics.density

        val params = WindowManager.LayoutParams(
            (54 * density).toInt(),
            (54 * density).toInt(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = (34 * density).toInt()
        }

        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(80).start()
                    openMirrorOverlay()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                    closeMirrorAnimated()
                    true
                }
                else -> false
            }
        }

        buttonView = view
        windowManager.addView(view, params)
    }

    private fun openMirrorOverlay() {
        if (isMirrorOpen) return
        isMirrorOpen = true

        val root = LayoutInflater.from(this).inflate(R.layout.overlay_lock_mirror, null)
        val card = root.findViewById<View>(R.id.circleMirrorContainer)
        val preview = root.findViewById<PreviewView>(R.id.lockPreviewView)

        // Découpe circulaire stricte
        card.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setOval(0, 0, view.width, view.height)
            }
        }
        card.clipToOutline = true

        // Mode miroir sur la caméra frontale
        preview.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        preview.scaleX = -1f

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
            PixelFormat.TRANSLUCENT
        )

        mirrorContainerWindow = root
        circleCard = card
        previewView = preview
        windowManager.addView(root, params)

        startFrontCamera(preview)

        // Animation qui sort de la caméra frontale (haut-centre du cercle)
        card.post {
            if (!isMirrorOpen) return@post
            card.visibility = View.VISIBLE
            val cx = card.width / 2
            val cy = 0
            val finalRadius = card.height.toFloat()

            ViewAnimationUtils.createCircularReveal(card, cx, cy, 0f, finalRadius).apply {
                duration = 280
                interpolator = AccelerateDecelerateInterpolator()
                start()
            }
        }
    }

    private fun closeMirrorAnimated() {
        if (!isMirrorOpen) return
        isMirrorOpen = false

        val root = mirrorContainerWindow ?: return
        val card = circleCard
        cameraProvider?.unbindAll()

        if (card == null || !card.isAttachedToWindow || card.width == 0) {
            runCatching { windowManager.removeView(root) }
            mirrorContainerWindow = null
            circleCard = null
            previewView = null
            return
        }

        val cx = card.width / 2
        val cy = 0
        val startRadius = card.height.toFloat()

        ViewAnimationUtils.createCircularReveal(card, cx, cy, startRadius, 0f).apply {
            duration = 200
            interpolator = AccelerateDecelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    runCatching { windowManager.removeView(root) }
                    if (mirrorContainerWindow === root) {
                        mirrorContainerWindow = null
                        circleCard = null
                        previewView = null
                    }
                }
            })
            start()
        }
    }

    private fun closeMirrorImmediate() {
        isMirrorOpen = false
        cameraProvider?.unbindAll()
        mirrorContainerWindow?.let { runCatching { windowManager.removeView(it) } }
        mirrorContainerWindow = null
        circleCard = null
        previewView = null
    }

    private fun startFrontCamera(previewView: PreviewView) {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            if (!isMirrorOpen) return@addListener
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
}
