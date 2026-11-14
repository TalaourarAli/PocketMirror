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

/** Habillage du cadre dessiné autour de la découpe caméra. */
enum class MirrorFrame(
    val key: String,
    val enabled: Boolean,
    val hasScallops: Boolean,
    val colors: IntArray,
) {
    /** Aucun cadre : juste un liseré fin sur le bord de la forme. */
    NONE("NONE", enabled = false, hasScallops = false, colors = intArrayOf()),

    /** Miroir de poche : bord festonné bleu. */
    SCALLOPED_BLUE(
        "SCALLOPED_BLUE",
        enabled = true,
        hasScallops = true,
        colors = intArrayOf(
            0xFF8FD3FF.toInt(),
            0xFF2F79C4.toInt(),
            0xFFA9E0FF.toInt(),
            0xFF1F5C9E.toInt(),
            0xFF6FB7EC.toInt(),
            0xFF8FD3FF.toInt(),
        ),
    ),

    /** Anneau lisse bleu, dégradé balayé. */
    SMOOTH_BLUE(
        "SMOOTH_BLUE",
        enabled = true,
        hasScallops = false,
        colors = intArrayOf(
            0xFF8FD3FF.toInt(),
            0xFF2F79C4.toInt(),
            0xFFA9E0FF.toInt(),
            0xFF1F5C9E.toInt(),
            0xFF6FB7EC.toInt(),
            0xFF8FD3FF.toInt(),
        ),
    ),

    /** Anneau doré, façon miroir de sac. */
    GOLD(
        "GOLD",
        enabled = true,
        hasScallops = false,
        colors = intArrayOf(
            0xFFFFE7A3.toInt(),
            0xFFC9962F.toInt(),
            0xFFFFF1C2.toInt(),
            0xFF8F6A1E.toInt(),
            0xFFE0B356.toInt(),
            0xFFFFE7A3.toInt(),
        ),
    );

    companion object {
        fun fromKey(key: String?): MirrorFrame =
            entries.firstOrNull { it.key == key } ?: SCALLOPED_BLUE
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
    val frame: MirrorFrame = MirrorFrame.SCALLOPED_BLUE,
)

/** Conversion dp -> pixels, pour éviter de répéter `x * displayMetrics.density` partout. */
fun Context.pxFromDp(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
