package com.pocketmirror.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class ButtonPositionActivity : AppCompatActivity() {

    private lateinit var rootLayout: View
    private lateinit var draggableButton: View
    private var currentXRatio = 0.5f
    private var currentYRatio = 0.90f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_button_position)

        // Plein écran pour correspondre aux dimensions réelles de l'écran de verrouillage
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            window.insetsController?.systemBarsBehavior =
                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        rootLayout = findViewById(R.id.buttonConfigRoot)
        draggableButton = findViewById(R.id.draggableButtonPreview)

        // Aperçu à la taille réelle configurée
        val btnSizePx = (MirrorPrefs.getButtonSizeDp(this) * resources.displayMetrics.density).toInt()
        draggableButton.layoutParams = draggableButton.layoutParams.apply {
            width = btnSizePx
            height = btnSizePx
        }
        draggableButton.alpha = MirrorPrefs.getButtonAlpha(this)
        val btnReset = findViewById<Button>(R.id.btnResetPosition)
        val btnSave = findViewById<Button>(R.id.btnSavePosition)

        currentXRatio = MirrorPrefs.getButtonXRatio(this)
        currentYRatio = MirrorPrefs.getButtonYRatio(this)

        rootLayout.post {
            applyRatioToPreview()
        }

        var dX = 0f
        var dY = 0f

        draggableButton.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dX = view.x - event.rawX
                    dY = view.y - event.rawY
                    view.animate().scaleX(1.12f).scaleY(1.12f).setDuration(80).start()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val maxW = (rootLayout.width - view.width).toFloat().coerceAtLeast(1f)
                    val maxH = (rootLayout.height - view.height).toFloat().coerceAtLeast(1f)
                    val newX = (event.rawX + dX).coerceIn(0f, maxW)
                    val newY = (event.rawY + dY).coerceIn(0f, maxH)
                    view.x = newX
                    view.y = newY

                    currentXRatio = (newX + view.width / 2f) / rootLayout.width.toFloat()
                    currentYRatio = (newY + view.height / 2f) / rootLayout.height.toFloat()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                    true
                }
                else -> false
            }
        }

        btnReset.setOnClickListener {
            currentXRatio = 0.5f
            currentYRatio = 0.90f
            applyRatioToPreview()
        }

        btnSave.setOnClickListener {
            MirrorPrefs.setButtonPositionRatio(this, currentXRatio, currentYRatio)
            sendBroadcast(Intent(MirrorPrefs.ACTION_PREFS_CHANGED).setPackage(packageName))
            Toast.makeText(this, "Emplacement enregistré", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun applyRatioToPreview() {
        val w = rootLayout.width
        val h = rootLayout.height
        if (w == 0 || h == 0) return
        draggableButton.x = (w * currentXRatio - draggableButton.width / 2f)
            .coerceIn(0f, (w - draggableButton.width).toFloat())
        draggableButton.y = (h * currentYRatio - draggableButton.height / 2f)
            .coerceIn(0f, (h - draggableButton.height).toFloat())
    }
}
