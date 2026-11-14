package com.pocketmirror.app

import android.content.Context

/** Mode d'interaction avec le bouton de l'écran de verrouillage. */
enum class InteractionMode(val key: String) {
    /** Le miroir reste ouvert tant que le doigt est posé. */
    HOLD("HOLD"),

    /** Un appui ouvre, l'appui suivant ferme. */
    TOGGLE("TOGGLE");

    companion object {
        fun fromKey(key: String?): InteractionMode =
            entries.firstOrNull { it.key == key } ?: HOLD
    }
}

/** Forme géométrique de la fenêtre du miroir. */
enum class MirrorShape(val key: String) {
    CIRCLE("CIRCLE"),
    ROUNDED_SQ("ROUNDED_SQ"),
    PILL("PILL");

    /** Hauteur exprimée en fraction de la largeur (l'ovale est plus haut que large). */
    val heightRatio: Float
        get() = if (this == PILL) 1.32f else 1f

    companion object {
        fun fromKey(key: String?): MirrorShape =
            entries.firstOrNull { it.key == key } ?: CIRCLE
    }
}

/**
 * Les réglages qui positionnent et dimensionnent la fenêtre du miroir.
 * Regroupés pour qu'ils ne circulent plus séparément de SharedPreferences
 * jusqu'au WindowManager.
 */
data class MirrorGeometry(
    val shape: MirrorShape,
    val sizeDp: Int,
    val offsetXDp: Int,
    val offsetYDp: Int,
)

/** Conversion dp -> pixels, pour éviter de répéter `x * displayMetrics.density` partout. */
fun Context.pxFromDp(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
