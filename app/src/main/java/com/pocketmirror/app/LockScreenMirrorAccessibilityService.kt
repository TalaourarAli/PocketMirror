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
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewAnimationUtils
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlin.math.hypot

class LockScreenMirrorAccessibilityService : AccessibilityService(), LifecycleOwner {

    companion object {
        @Volatile
        var isConnectedService: Boolean = false
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private lateinit var windowManager: WindowManager
    private lateinit var keyguardManager: KeyguardManager

    private var buttonView: View? = null
    private var buttonParams: WindowManager.LayoutParams? = null

    private var mirrorContainerWindow: View? = null
    private var circleCard: View? = null
    private var previewView: PreviewView? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var isMirrorOpen = false

    private val systemReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON,
                Intent.ACTION_USER_PRESENT -> updateLockButtonVisibility()
                Intent.ACTION_SCREEN_OFF -> {
                    closeMirrorImmediate()
                    updateLockButtonVisibility()
                }
                MirrorPrefs.ACTION_PREFS_CHANGED -> {
                    applyButtonPosition()
                    updateLockButtonVisibility()
                    if (isMirrorOpen) {
                        // Recharge la forme si le miroir est ouvert
                        closeMirrorImmediate()
                    }
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
            addAction(MirrorPrefs.ACTION_PREFS_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(systemReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(systemReceiver, filter)
        }
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
        runCatching { unregisterReceiver(systemReceiver) }
        closeMirrorImmediate()
        buttonView?.let { runCatching { windowManager.removeView(it) } }
        buttonView = null
        buttonParams = null
        super.onDestroy()
    }

    private fun getRealScreenSize(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.maximumWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            val dm = resources.displayMetrics
            dm.widthPixels to dm.heightPixels
        }
    }

    private fun updateLockButtonVisibility() {
        val btn = buttonView ?: return
        val shouldShow = keyguardManager.isKeyguardLocked || MirrorPrefs.getTestShowAlways(this)
        btn.visibility = if (shouldShow) View.VISIBLE else View.GONE
        if (!shouldShow && isMirrorOpen) {
            closeMirrorImmediate()
        }
    }

    private fun applyButtonPosition() {
        val view = buttonView ?: return
        val params = buttonParams ?: return
        val (screenW, screenH) = getRealScreenSize()
        val density = resources.displayMetrics.density
        val btnSize = (54 * density).toInt()

        val xRatio = MirrorPrefs.getButtonXRatio(this)
        val yRatio = MirrorPrefs.getButtonYRatio(this)

        params.gravity = Gravity.TOP or Gravity.START
        params.x = ((screenW * xRatio) - btnSize / 2f).toInt().coerceIn(0, screenW - btnSize)
        params.y = ((screenH * yRatio) - btnSize / 2f).toInt().coerceIn(0, screenH - btnSize)

        if (view.isAttachedToWindow) {
            runCatching { windowManager.updateViewLayout(view, params) }
        }
    }

    private fun ensureButtonCreated() {
        if (buttonView != null) return

        val view = LayoutInflater.from(this).inflate(R.layout.overlay_lock_button, null)
        val density = resources.displayMetrics.density
        val btnSize = (54 * density).toInt()

        val params = WindowManager.LayoutParams(
            btnSize,
            btnSize,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
            PixelFormat.TRANSLUCENT
        )

        buttonView = view
        buttonParams = params
        applyButtonPosition()

        view.setOnTouchListener { v, event ->
            val mode = MirrorPrefs.getInteractionMode(this)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(80).start()
                    if (mode == MirrorPrefs.MODE_HOLD) {
                        openMirrorOverlay()
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                    if (mode == MirrorPrefs.MODE_HOLD) {
                        closeMirrorAnimated()
                    } else {
                        if (isMirrorOpen) {
                            closeMirrorAnimated()
                        } else {
                            openMirrorOverlay()
                        }
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                    if (mode == MirrorPrefs.MODE_HOLD) {
                        closeMirrorAnimated()
                    }
                    true
                }
                else -> false
            }
        }

        windowManager.addView(view, params)
    }

    private fun configureShape(card: View) {
        val density = resources.displayMetrics.density
        val shape = MirrorPrefs.getMirrorShape(this)
        val lp = card.layoutParams as FrameLayout.LayoutParams

        when (shape) {
            MirrorPrefs.SHAPE_ROUNDED_SQ -> {
                val size = (310 * density).toInt()
                lp.width = size
                lp.height = size
                card.layoutParams = lp
                card.setBackgroundResource(R.drawable.bg_rounded_mirror)
                val radius = 42f * density
                card.outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, radius)
                    }
                }
            }
            MirrorPrefs.SHAPE_PILL -> {
                lp.width = (290 * density).toInt()
                lp.height = (400 * density).toInt()
                card.layoutParams = lp
                card.setBackgroundResource(R.drawable.bg_pill_mirror)
                card.outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        val r = view.width / 2f
                        outline.setRoundRect(0, 0, view.width, view.height, r)
                    }
                }
            }
            else -> { // SHAPE_CIRCLE
                val size = (310 * density).toInt()
                lp.width = size
                lp.height = size
                card.layoutParams = lp
                card.setBackgroundResource(R.drawable.bg_circle_mirror)
                card.outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setOval(0, 0, view.width, view.height)
                    }
                }
            }
        }
        card.clipToOutline = true
    }

    private fun openMirrorOverlay() {
        if (isMirrorOpen) return
        isMirrorOpen = true

        val root = LayoutInflater.from(this).inflate(R.layout.overlay_lock_mirror, null)
        val card = root.findViewById<View>(R.id.circleMirrorContainer)
        val preview = root.findViewById<PreviewView>(R.id.lockPreviewView)

        configureShape(card)

        // CameraX PreviewView inverse déjà automatiquement la caméra frontale en mode miroir naturel !
        // On laisse scaleX = 1f pour ne pas ré-inverser l'image.
        preview.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        preview.scaleX = 1f

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

        card.post {
            if (!isMirrorOpen) return@post
            card.visibility = View.VISIBLE
            val cx = card.width / 2
            val cy = 0
            val finalRadius = hypot(card.width.toDouble(), card.height.toDouble()).toFloat()

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
        val startRadius = hypot(card.width.toDouble(), card.height.toDouble()).toFloat()

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
