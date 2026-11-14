package com.pocketmirror.app

import android.content.Context

object MirrorPrefs {
    private const val PREFS_NAME = "pocket_mirror_prefs"
    const val ACTION_PREFS_CHANGED = "com.pocketmirror.app.ACTION_PREFS_CHANGED"

    private const val KEY_MODE = "interaction_mode"
    private const val KEY_SHAPE = "mirror_shape"
    private const val KEY_BTN_X_RATIO = "button_x_ratio"
    private const val KEY_BTN_Y_RATIO = "button_y_ratio"
    private const val KEY_TEST_ALWAYS = "test_show_always"

    const val MODE_HOLD = "HOLD"       // Appuyer pour afficher, relâcher pour fermer
    const val MODE_TOGGLE = "TOGGLE"   // 1 clic ouvre, 1 clic ferme

    const val SHAPE_CIRCLE = "CIRCLE"         // Cercle
    const val SHAPE_ROUNDED_SQ = "ROUNDED_SQ" // Carré arrondi
    const val SHAPE_PILL = "PILL"             // Ovale / Portrait

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getInteractionMode(context: Context): String =
        prefs(context).getString(KEY_MODE, MODE_HOLD) ?: MODE_HOLD

    fun setInteractionMode(context: Context, mode: String) {
        prefs(context).edit().putString(KEY_MODE, mode).apply()
    }

    fun getMirrorShape(context: Context): String =
        prefs(context).getString(KEY_SHAPE, SHAPE_CIRCLE) ?: SHAPE_CIRCLE

    fun setMirrorShape(context: Context, shape: String) {
        prefs(context).edit().putString(KEY_SHAPE, shape).apply()
    }

    // Ratios entre 0.0 et 1.0 par rapport à la largeur/hauteur de l'écran (par défaut bas-centre)
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

    fun getTestShowAlways(context: Context): Boolean =
        prefs(context).getBoolean(KEY_TEST_ALWAYS, false)

    fun setTestShowAlways(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_TEST_ALWAYS, value).apply()
    }
}
