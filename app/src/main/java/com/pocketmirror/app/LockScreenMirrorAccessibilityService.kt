package com.pocketmirror.app

import android.accessibilityservice.AccessibilityService
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.animation.DecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.widget.FrameLayout
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
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private lateinit var windowManager: WindowManager
    private lateinit var keyguardManager: KeyguardManager

    private var buttonView: View? = null
    private var buttonParams: WindowManager.LayoutParams? = null

    private var mirrorContainerWindow: View? = null
    private var maskedCard: MaskedMirrorContainer? = null
    private var previewView: PreviewView? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var currentAnimator: ValueAnimator? = null
    private var isMirrorOpen = false
    private var waitingFirstFrame = false

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
                    maskedCard?.let { configurePopupLayoutAndShape(it) }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager

        // Pré-initialise ProcessCameraProvider pour accélérer l'ouverture
        ProcessCameraProvider.getInstance(this)

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

    /**
     * Trouve le bord supérieur (ou bas selon besoin) du poinçon caméra frontale.
     * Collé à la caméra = top du poinçon (ou 0 si pas de cutout détecté).
     */
    private fun getCameraCutoutAnchorY(): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val cutout = windowManager.maximumWindowMetrics.windowInsets.displayCutout
            if (cutout != null && cutout.boundingRects.isNotEmpty()) {
                val topCutout = cutout.boundingRects.minByOrNull { it.top }
                if (topCutout != null && topCutout.top < 200) {
                    // On colle le haut du cercle juste au niveau du poinçon caméra
                    return topCutout.top
                }
            }
        }
        return 0
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

    private fun configurePopupLayoutAndShape(card: MaskedMirrorContainer) {
        val density = resources.displayMetrics.density
        val shape = MirrorPrefs.getMirrorShape(this)
        val baseSizePx = (MirrorPrefs.getPopupSizeDp(this) * density).toInt()
        val offsetXPx = (MirrorPrefs.getPopupOffsetXDp(this) * density).toInt()
        val offsetYPx = (MirrorPrefs.getPopupOffsetYDp(this) * density).toInt()
        val cutoutTopPx = getCameraCutoutAnchorY()

        val lp = card.layoutParams as FrameLayout.LayoutParams
        lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL

        when (shape) {
            MirrorPrefs.SHAPE_PILL -> {
                lp.width = baseSizePx
                lp.height = (baseSizePx * 1.32f).toInt()
            }
            else -> {
                lp.width = baseSizePx
                lp.height = baseSizePx
            }
        }

        // Collé au poinçon caméra par défaut + offset choisi par l'utilisateur
        lp.topMargin = (cutoutTopPx + offsetYPx).coerceAtLeast(0)
        card.translationX = offsetXPx.toFloat()
        card.layoutParams = lp
        card.shapeMode = shape
    }

    private fun openMirrorOverlay() {
        if (isMirrorOpen) return
        isMirrorOpen = true
        currentAnimator?.cancel()

        val root = mirrorContainerWindow ?: LayoutInflater.from(this)
            .inflate(R.layout.overlay_lock_mirror, null).also { created ->
                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
                    PixelFormat.TRANSLUCENT
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    params.layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
                mirrorContainerWindow = created
                maskedCard = created.findViewById(R.id.circleMirrorContainer)
                previewView = created.findViewById<PreviewView>(R.id.lockPreviewView).apply {
                    // COMPATIBLE utilise TextureView -> zéro SurfaceView noire rectangulaire !
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleX = 1f
                }
                windowManager.addView(created, params)
            }

        val card = maskedCard ?: return
        val preview = previewView ?: return

        configurePopupLayoutAndShape(card)

        // Reste invisible (progress = 0) tant que la 1ère frame vidéo n'est pas prête -> aucun flash noir !
        card.revealProgress = 0f
        waitingFirstFrame = true

        preview.previewStreamState.observe(this) { state ->
            if (isMirrorOpen && waitingFirstFrame && state == PreviewView.StreamState.STREAMING) {
                waitingFirstFrame = false
                animateReveal(card, toOpen = true)
            }
        }

        startFrontCamera(preview)
    }

    private fun animateReveal(card: MaskedMirrorContainer, toOpen: Boolean, onEnd: (() -> Unit)? = null) {
        currentAnimator?.cancel()
        val start = card.revealProgress
        val end = if (toOpen) 1f else 0f

        currentAnimator = ValueAnimator.ofFloat(start, end).apply {
            duration = if (toOpen) 240L else 180L
            interpolator = if (toOpen) DecelerateInterpolator(1.6f) else AccelerateInterpolator(1.4f)
            addUpdateListener { anim ->
                card.revealProgress = anim.animatedValue as Float
            }
            if (onEnd != null) {
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        onEnd()
                    }
                })
            }
            start()
        }
    }

    private fun closeMirrorAnimated() {
        if (!isMirrorOpen) return
        isMirrorOpen = false
        waitingFirstFrame = false

        val card = maskedCard
        if (card == null || card.revealProgress <= 0.02f) {
            closeMirrorImmediate()
            return
        }

        animateReveal(card, toOpen = false) {
            if (!isMirrorOpen) {
                closeMirrorImmediate()
            }
        }
    }

    private fun closeMirrorImmediate() {
        isMirrorOpen = false
        waitingFirstFrame = false
        currentAnimator?.cancel()
        currentAnimator = null
        cameraProvider?.unbindAll()
        previewView?.previewStreamState?.removeObservers(this)
        mirrorContainerWindow?.let { runCatching { windowManager.removeView(it) } }
        mirrorContainerWindow = null
        maskedCard = null
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
