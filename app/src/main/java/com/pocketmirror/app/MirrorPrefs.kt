package com.pocketmirror.app

import android.content.Context
import android.content.Intent

object MirrorPrefs {
    private const val PREFS_NAME = "pocket_mirror_prefs"
    const val ACTION_PREFS_CHANGED = "com.pocketmirror.app.ACTION_PREFS_CHANGED"

    private const val KEY_MODE = "interaction_mode"
    private const val KEY_SHAPE = "mirror_shape"
    private const val KEY_FRAME = "mirror_frame"
    private const val KEY_BTN_X_RATIO = "button_x_ratio"
    private const val KEY_BTN_Y_RATIO = "button_y_ratio"
    private const val KEY_POPUP_OFFSET_X_DP = "popup_offset_x_dp"
    private const val KEY_POPUP_OFFSET_Y_DP = "popup_offset_y_dp"
    private const val KEY_POPUP_SIZE_DP = "popup_size_dp"
    private const val KEY_BTN_SIZE_DP = "button_size_dp"
    private const val KEY_BTN_ALPHA = "button_alpha"
    private const val KEY_TEST_ALWAYS = "test_show_always"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Prévient le service d'accessibilité qu'un réglage a changé.
     * Centralisé ici : deux activités émettaient le même broadcast à la main.
     */
    fun notifyChanged(context: Context) {
        context.sendBroadcast(Intent(ACTION_PREFS_CHANGED).setPackage(context.packageName))
    }

    fun getInteractionMode(context: Context): InteractionMode =
        InteractionMode.fromKey(prefs(context).getString(KEY_MODE, null))

    fun setInteractionMode(context: Context, mode: InteractionMode) {
        prefs(context).edit().putString(KEY_MODE, mode.key).apply()
    }

    fun getMirrorShape(context: Context): MirrorShape =
        MirrorShape.fromKey(prefs(context).getString(KEY_SHAPE, null))

    fun setMirrorShape(context: Context, shape: MirrorShape) {
        prefs(context).edit().putString(KEY_SHAPE, shape.key).apply()
    }

    fun getMirrorFrame(context: Context): MirrorFrame =
        MirrorFrame.fromKey(prefs(context).getString(KEY_FRAME, null))

    fun setMirrorFrame(context: Context, frame: MirrorFrame) {
        prefs(context).edit().putString(KEY_FRAME, frame.key).apply()
    }

    /** Tous les réglages de la fenêtre du miroir, lus en une fois. */
    fun getGeometry(context: Context) = MirrorGeometry(
        shape = getMirrorShape(context),
        sizeDp = getPopupSizeDp(context),
        offsetXDp = getPopupOffsetXDp(context),
        offsetYDp = getPopupOffsetYDp(context),
        frame = getMirrorFrame(context),
    )

    fun getButtonXRatio(context: Context): Float =
        prefs(context).getFloat(KEY_BTN_X_RATIO, 0.5f)

    fun getButtonYRatio(context: Context): Float =
        prefs(context).getFloat(KEY_BTN_Y_RATIO, 0.90f)

    fun setButtonPositionRatio(context: Context, xRatio: Float, yRatio: Float) {
        prefs(context).edit()
            .putFloat(KEY_BTN_X_RATIO, xRatio.coerceIn(0.06f, 0.94f))
            .putFloat(KEY_BTN_Y_RATIO, yRatio.coerceIn(0.08f, 0.95f))
            .apply()
    }

    // Décalage de la popup par rapport au poinçon caméra (0 dp = collé pile sous la caméra)
    fun getPopupOffsetXDp(context: Context): Int =
        prefs(context).getInt(KEY_POPUP_OFFSET_X_DP, 0)

    fun setPopupOffsetXDp(context: Context, dp: Int) {
        prefs(context).edit().putInt(KEY_POPUP_OFFSET_X_DP, dp.coerceIn(-120, 120)).apply()
    }

    fun getPopupOffsetYDp(context: Context): Int =
        prefs(context).getInt(KEY_POPUP_OFFSET_Y_DP, 0)

    fun setPopupOffsetYDp(context: Context, dp: Int) {
        prefs(context).edit().putInt(KEY_POPUP_OFFSET_Y_DP, dp.coerceIn(-40, 200)).apply()
    }

    fun getPopupSizeDp(context: Context): Int =
        prefs(context).getInt(KEY_POPUP_SIZE_DP, 300)

    fun setPopupSizeDp(context: Context, dp: Int) {
        prefs(context).edit().putInt(KEY_POPUP_SIZE_DP, dp.coerceIn(180, 380)).apply()
    }

    fun getTestShowAlways(context: Context): Boolean =
        prefs(context).getBoolean(KEY_TEST_ALWAYS, false)

    fun setTestShowAlways(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_TEST_ALWAYS, value).apply()
    }

    // Taille du bouton flottant sur l'écran de verrouillage (en dp)
    fun getButtonSizeDp(context: Context): Int =
        prefs(context).getInt(KEY_BTN_SIZE_DP, 54)

    fun setButtonSizeDp(context: Context, dp: Int) {
        prefs(context).edit().putInt(KEY_BTN_SIZE_DP, dp.coerceIn(36, 84)).apply()
    }

    // Transparence du bouton (1.0 = opaque, 0.2 = très discret)
    fun getButtonAlpha(context: Context): Float =
        prefs(context).getFloat(KEY_BTN_ALPHA, 1f)

    fun setButtonAlpha(context: Context, alpha: Float) {
        prefs(context).edit().putFloat(KEY_BTN_ALPHA, alpha.coerceIn(0.2f, 1f)).apply()
    }
}
