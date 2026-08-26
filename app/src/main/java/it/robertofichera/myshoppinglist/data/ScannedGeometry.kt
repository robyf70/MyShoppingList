package it.robertofichera.myshoppinglist.data

/**
 * One recognised line and where it sat on the picture. A flyer says what it means by layout
 * rather than by wording, so the geometry has to survive as far as the parser.
 */
data class ScannedLine(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val height: Int get() = bottom - top
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2
}
