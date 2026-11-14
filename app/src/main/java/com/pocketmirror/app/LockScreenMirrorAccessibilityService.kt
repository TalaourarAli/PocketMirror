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
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import android.view.Display
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
import androidx.lifecycle.Observer

class LockScreenMirrorAccessibilityService : AccessibilityService(), LifecycleOwner {

    companion object {
        private const val TAG = "PocketMirrorSvc"
        const val ACTION_DEBUG_OPEN = "com.pocketmirror.app.DEBUG_OPEN"
        const val ACTION_DEBUG_CLOSE = "com.pocketmirror.app.DEBUG_CLOSE"

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
    private var revealStarted = false
    private var cameraBound = false
    private var revealFallback: Runnable? = null

    private val systemReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON,
                Intent.ACTION_USER_PRESENT -> updateLockButtonVisibility()
                Intent.ACTION_SCREEN_OFF -> {
                    closeMirrorImmediate()
                    releaseCamera()
                    updateLockButtonVisibility(warmCamera = false)
                }
                MirrorPrefs.ACTION_PREFS_CHANGED -> {
                    if (::windowManager.isInitialized) {
                        applyButtonAppearance()
                        updateLockButtonVisibility()
                        maskedCard?.let { configurePopupLayoutAndShape(it) }
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
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

        // Receiver de debug : permet de tester l'overlay depuis adb sans verrouiller l'écran
        val debugFilter = IntentFilter().apply {
            addAction(ACTION_DEBUG_OPEN)
            addAction(ACTION_DEBUG_CLOSE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(debugReceiver, debugFilter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(debugReceiver, debugFilter)
        }

        // Réagit aux passages écran allumé <-> AOD <-> éteint (AOD = STATE_DOZE)
        getSystemService(DisplayManager::class.java)?.registerDisplayListener(
            displayListener,
            null
        )
    }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {}
        override fun onDisplayRemoved(displayId: Int) {}
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == Display.DEFAULT_DISPLAY) {
                updateLockButtonVisibility()
            }
        }
    }

    private val debugReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_DEBUG_OPEN -> openMirrorOverlay()
                ACTION_DEBUG_CLOSE -> closeMirrorAnimated()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Avec targetSdk 36+, il faut récupérer le WindowManager APRÈS onServiceConnected
        // pour qu'il porte le WindowToken de l'AccessibilityService (évite BadTokenException)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
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
        runCatching { unregisterReceiver(debugReceiver) }
        runCatching {
            getSystemService(DisplayManager::class.java)?.unregisterDisplayListener(displayListener)
        }
        closeMirrorImmediate()
        removeOverlayWindow()
        releaseCamera()
        if (::windowManager.isInitialized) {
            buttonView?.let { runCatching { windowManager.removeView(it) } }
        }
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

    /**
     * Vrai uniquement si l'écran est réellement allumé (STATE_ON et appareil interactif).
     * En Always-On Display (AOD), le display est en STATE_DOZE / DOZE_SUSPEND
     * et PowerManager.isInteractive est false -> on considère l'écran comme éteint.
     */
    private fun isDisplayReallyOn(): Boolean {
        val power = getSystemService(POWER_SERVICE) as PowerManager
        if (!power.isInteractive) return false

        val display = getSystemService(DisplayManager::class.java)
            ?.getDisplay(Display.DEFAULT_DISPLAY) ?: return false

        @Suppress("DEPRECATION")
        val state = display.state
        return state == Display.STATE_ON
    }

    private fun updateLockButtonVisibility(warmCamera: Boolean = true) {
        val btn = buttonView ?: return
        val displayOn = isDisplayReallyOn()
        val shouldShow = displayOn &&
            (keyguardManager.isKeyguardLocked || MirrorPrefs.getTestShowAlways(this))
        btn.visibility = if (shouldShow) View.VISIBLE else View.GONE

        if (shouldShow) {
            // Prépare la fenêtre + pré-chauffe la caméra pendant que l'écran est verrouillé,
            // pour que l'appui ouvre le miroir quasi instantanément.
            ensureOverlayWindow()
            if (warmCamera) preWarmCamera()
        } else {
            // Écran éteint, AOD, ou téléphone déverrouillé : aucune activité caméra
            if (isMirrorOpen) {
                closeMirrorImmediate()
            }
            releaseCamera()
        }
    }

    private fun applyButtonPosition() {
        val view = buttonView ?: return
        val params = buttonParams ?: return
        val (screenW, screenH) = getRealScreenSize()
        val density = resources.displayMetrics.density
        val btnSize = (MirrorPrefs.getButtonSizeDp(this) * density).toInt()

        params.width = btnSize
        params.height = btnSize

        val xRatio = MirrorPrefs.getButtonXRatio(this)
        val yRatio = MirrorPrefs.getButtonYRatio(this)

        params.gravity = Gravity.TOP or Gravity.START
        params.x = ((screenW * xRatio) - btnSize / 2f).toInt().coerceIn(0, screenW - btnSize)
        params.y = ((screenH * yRatio) - btnSize / 2f).toInt().coerceIn(0, screenH - btnSize)

        if (view.isAttachedToWindow) {
            runCatching { windowManager.updateViewLayout(view, params) }
        }
    }

    private fun applyButtonAppearance() {
        val view = buttonView ?: return
        view.alpha = MirrorPrefs.getButtonAlpha(this)
        applyButtonPosition()
    }

    private fun ensureButtonCreated() {
        if (buttonView != null) return

        val view = LayoutInflater.from(this).inflate(R.layout.overlay_lock_button, null)
        val density = resources.displayMetrics.density
        val btnSize = (MirrorPrefs.getButtonSizeDp(this) * density).toInt()

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
        view.alpha = MirrorPrefs.getButtonAlpha(this)
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
        revealStarted = false
        currentAnimator?.cancel()
        Log.d(TAG, "openMirrorOverlay")

        val card = ensureOverlayWindow() ?: return
        val preview = previewView ?: return

        configurePopupLayoutAndShape(card)
        card.revealProgress = 0f

        if (cameraBound && preview.previewStreamState.value == PreviewView.StreamState.STREAMING) {
            // Caméra déjà chaude (pré-chauffée sur l'écran de verrouillage) -> ouverture immédiate
            Log.d(TAG, "camera already streaming -> immediate reveal")
            maybeStartReveal(false)
        } else {
            preWarmCamera()
            revealFallback = Runnable {
                Log.d(TAG, "reveal fallback fired")
                maybeStartReveal(true)
            }.also { card.postDelayed(it, 150L) }
        }
    }

    /**
     * Crée la fenêtre de l'overlay une seule fois et la garde en mémoire (invisible, clip 1px).
     * Évite le coût d'inflation + addView à chaque appui.
     */
    private fun ensureOverlayWindow(): MaskedMirrorContainer? {
        if (mirrorContainerWindow == null) {
            val created = LayoutInflater.from(this).inflate(R.layout.overlay_lock_mirror, null)
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

            val card = created.findViewById<MaskedMirrorContainer>(R.id.circleMirrorContainer)
            val preview = created.findViewById<PreviewView>(R.id.lockPreviewView).apply {
                // COMPATIBLE utilise TextureView -> pas de surface noire rectangulaire
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleX = 1f
            }

            card.revealProgress = 0f
            mirrorContainerWindow = created
            maskedCard = card
            previewView = preview

            configurePopupLayoutAndShape(card)
            windowManager.addView(created, params)
            preview.previewStreamState.observeForever(streamObserver)
            Log.d(TAG, "overlay window created (pre-inflated)")
        }
        return maskedCard
    }

    private fun preWarmCamera() {
        if (cameraBound) return
        val preview = previewView ?: return
        Log.d(TAG, "pre-warming camera")
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            if (cameraBound) return@addListener
            val provider = future.get()
            cameraProvider = provider
            val useCase = Preview.Builder().build().also {
                it.setSurfaceProvider(preview.surfaceProvider)
            }
            runCatching {
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, useCase)
                cameraBound = true
                Log.d(TAG, "camera bound (pre-warm)")
            }.onFailure {
                Log.e(TAG, "preWarm bind failed", it)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun releaseCamera() {
        if (!cameraBound) return
        cameraBound = false
        runCatching { cameraProvider?.unbindAll() }
        Log.d(TAG, "camera released")
    }

    private val streamObserver = Observer<PreviewView.StreamState> { state ->
        Log.d(TAG, "previewStreamState=$state")
        if (state == PreviewView.StreamState.STREAMING) {
            maybeStartReveal(false)
        }
    }

    private fun maybeStartReveal(fromFallback: Boolean) {
        if (!isMirrorOpen || revealStarted) return
        val card = maskedCard ?: return
        revealStarted = true
        revealFallback?.let { card.removeCallbacks(it) }
        revealFallback = null
        if (fromFallback) {
            Log.w(TAG, "STREAMING not received, revealing anyway")
        }
        animateReveal(card, toOpen = true)
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
        revealStarted = false
        Log.d(TAG, "closeMirrorAnimated")

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
        revealStarted = false
        currentAnimator?.cancel()
        currentAnimator = null
        val card = maskedCard
        revealFallback?.let { card?.removeCallbacks(it) }
        revealFallback = null
        // On garde la fenêtre pré-inflatée en mémoire et on cache juste le contenu
        card?.revealProgress = 0f

        // La caméra reste chaude tant que l'écran de verrouillage / le mode test est actif
        val keepWarm = keyguardManager.isKeyguardLocked || MirrorPrefs.getTestShowAlways(this)
        if (!keepWarm) {
            releaseCamera()
        }
    }

    private fun removeOverlayWindow() {
        runCatching { previewView?.previewStreamState?.removeObserver(streamObserver) }
        if (::windowManager.isInitialized) {
            mirrorContainerWindow?.let { runCatching { windowManager.removeView(it) } }
        }
        mirrorContainerWindow = null
        maskedCard = null
        previewView = null
    }
}
