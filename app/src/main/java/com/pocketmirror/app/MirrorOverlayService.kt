package com.pocketmirror.app

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewAnimationUtils
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageButton
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import kotlin.math.hypot

class MirrorOverlayService : LifecycleService() {

    companion object {
        @Volatile
        var isRunning: Boolean = false
        private const val CHANNEL_ID = "pocket_mirror_channel"
        private const val NOTIFICATION_ID = 1001
    }

    private lateinit var windowManager: WindowManager
    private var floatingButtonView: View? = null
    private var mirrorOverlayView: View? = null
    private var cameraProvider: ProcessCameraProvider? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        showFloatingButton()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        closeMirrorOverlay(immediate = true)
        floatingButtonView?.let {
            runCatching { windowManager.removeView(it) }
        }
        floatingButtonView = null
    }

    private fun showFloatingButton() {
        if (floatingButtonView != null) return

        val view = LayoutInflater.from(this).inflate(R.layout.overlay_button, null)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 24
            y = 320
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isClick = true

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isClick = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (initialTouchX - event.rawX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (kotlin.math.abs(dx) > 10 || kotlin.math.abs(dy) > 10) {
                        isClick = false
                        params.x = initialX + dx
                        params.y = initialY + dy
                        windowManager.updateViewLayout(view, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isClick) {
                        openMirrorOverlay()
                    }
                    true
                }
                else -> false
            }
        }

        floatingButtonView = view
        windowManager.addView(view, params)
    }

    private fun openMirrorOverlay() {
        if (mirrorOverlayView != null) return

        val view = LayoutInflater.from(this).inflate(R.layout.overlay_mirror, null)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
            PixelFormat.TRANSLUCENT
        )

        val mirrorCard = view.findViewById<View>(R.id.mirrorCard)
        val previewView = view.findViewById<PreviewView>(R.id.previewView)
        val btnClose = view.findViewById<ImageButton>(R.id.btnCloseMirror)

        // Mode miroir horizontal sur l'aperçu
        previewView.scaleX = -1f

        btnClose.setOnClickListener {
            closeMirrorOverlay(immediate = false)
        }

        mirrorOverlayView = view
        windowManager.addView(view, params)

        // Animation qui sort de la caméra frontale (haut-centre)
        mirrorCard.post {
            val centerX = mirrorCard.width / 2
            val startY = 0
            val finalRadius = hypot(mirrorCard.width.toDouble(), mirrorCard.height.toDouble()).toFloat()

            val reveal = ViewAnimationUtils.createCircularReveal(
                mirrorCard,
                centerX,
                startY,
                0f,
                finalRadius
            ).apply {
                duration = 320
                interpolator = AccelerateDecelerateInterpolator()
            }
            reveal.start()
        }

        startFrontCamera(previewView)
    }

    private fun closeMirrorOverlay(immediate: Boolean) {
        val view = mirrorOverlayView ?: return
        cameraProvider?.unbindAll()

        if (immediate) {
            runCatching { windowManager.removeView(view) }
            mirrorOverlayView = null
            return
        }

        val mirrorCard = view.findViewById<View>(R.id.mirrorCard)
        val centerX = mirrorCard.width / 2
        val startRadius = hypot(mirrorCard.width.toDouble(), mirrorCard.height.toDouble()).toFloat()

        val hide = ViewAnimationUtils.createCircularReveal(
            mirrorCard,
            centerX,
            0,
            startRadius,
            0f
        ).apply {
            duration = 240
            interpolator = AccelerateDecelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    runCatching { windowManager.removeView(view) }
                    mirrorOverlayView = null
                }
            })
        }
        hide.start()
    }

    private fun startFrontCamera(previewView: PreviewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val provider = cameraProviderFuture.get()
            cameraProvider = provider

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val selector = CameraSelector.DEFAULT_FRONT_CAMERA
            runCatching {
                provider.unbindAll()
                provider.bindToLifecycle(this, selector, preview)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "PocketMirror",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openMirrorIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MirrorLockActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PocketMirror")
            .setContentText("Appuyez pour ouvrir le miroir sur l'écran de verrouillage")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(openMirrorIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .build()
    }
}
