package it.robertofichera.myshoppinglist.ui

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import it.robertofichera.myshoppinglist.R

/** 0 means the card follows the theme, which is where every list starts. */
const val COLOR_DEFAULT = 0

/**
 * Mid-tone throughout, so a swatch reads the same against a light or a dark background
 * and the text drawn over it keeps its contrast either way.
 */
val LIST_COLORS = listOf(
    0xFF6E6E73.toInt(),
    0xFFB3261E.toInt(),
    0xFFB2601A.toInt(),
    0xFF8A6B00.toInt(),
    0xFF3B6E3F.toInt(),
    0xFF00696E.toInt(),
    0xFF2F5EA8.toInt(),
    0xFF6650A4.toInt(),
    0xFF8E4A63.toInt(),
)

/**
 * Black or white, whichever the eye can read on [background]. A custom colour can be
 * anything at all, so the contrast has to be derived rather than themed.
 */
fun readableOn(background: Int): Color =
    if (Color(background).luminance() > 0.45f) Color.Black else Color.White

private fun isCustom(color: Int) = color != COLOR_DEFAULT && color !in LIST_COLORS

/** The swatch row, with the sliders revealed only once a custom colour is being chosen. */
@Composable
fun ColorChoice(
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var customOpen by remember { mutableStateOf(isCustom(selected)) }
    val hsv = remember { FloatArray(3) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.field_color),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Swatch(
                color = COLOR_DEFAULT,
                selected = selected == COLOR_DEFAULT && !customOpen,
                description = stringResource(R.string.color_default),
                onClick = { customOpen = false; onSelect(COLOR_DEFAULT) },
            )
            LIST_COLORS.forEachIndexed { index, color ->
                Swatch(
                    color = color,
                    selected = selected == color && !customOpen,
                    description = stringResource(R.string.color_swatch, index + 1),
                    onClick = { customOpen = false; onSelect(color) },
                )
            }
            Swatch(
                color = if (isCustom(selected)) selected else LIST_COLORS.first(),
                selected = customOpen,
                description = stringResource(R.string.color_custom),
                dashed = true,
                onClick = {
                    customOpen = true
                    val start = if (isCustom(selected)) selected else LIST_COLORS.first()
                    AndroidColor.colorToHSV(start, hsv)
                    onSelect(start)
                },
            )
        }

        if (customOpen) {
            CustomSliders(
                selected = if (isCustom(selected)) selected else LIST_COLORS.first(),
                hsv = hsv,
                onSelect = onSelect,
            )
        }
    }
}

@Composable
private fun CustomSliders(selected: Int, hsv: FloatArray, onSelect: (Int) -> Unit) {
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var saturation by remember { mutableFloatStateOf(hsv[1]) }
    var value by remember { mutableFloatStateOf(hsv[2]) }

    fun emit() = onSelect(AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value)))

    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Slider(
                value = hue,
                onValueChange = { hue = it; emit() },
                valueRange = 0f..360f,
            )
            Slider(
                value = saturation,
                onValueChange = { saturation = it; emit() },
            )
            Slider(
                value = value,
                onValueChange = { value = it; emit() },
            )
        }
        Swatch(color = selected, selected = false, description = null, onClick = {})
    }
}

@Composable
private fun Swatch(
    color: Int,
    selected: Boolean,
    description: String?,
    onClick: () -> Unit,
    dashed: Boolean = false,
) {
    val fill = if (color == COLOR_DEFAULT) MaterialTheme.colorScheme.surfaceVariant else Color(color)
    val tick = if (color == COLOR_DEFAULT) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        readableOn(color)
    }

    Row(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(fill)
            .border(
                width = if (dashed) 2.dp else 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = CircleShape,
            )
            .clickable(onClick = onClick)
            .then(
                if (description == null) Modifier else Modifier.semantics {
                    contentDescription = description
                }
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Icon(Icons.Default.Check, contentDescription = null, tint = tick)
        }
    }
}
