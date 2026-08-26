package it.robertofichera.myshoppinglist.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import it.robertofichera.myshoppinglist.R
import it.robertofichera.myshoppinglist.data.ScannedLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Wide enough to read on any phone, small enough that a leaflet photograph is not held twice. */
private const val PREVIEW_WIDTH = 1080

/**
 * The picture with every recognised line drawn over it, for the reader to tap the ones that name
 * the product. What was guessed starts marked, so a right guess costs nothing and a wrong one
 * costs a tap — which is why no rule for finding a label has to be right, only useful.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanPickScreen(
    uri: Uri,
    lines: List<ScannedLine>,
    initiallyPicked: List<ScannedLine>,
    onConfirm: (List<ScannedLine>) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var preview by remember(uri) { mutableStateOf<Preview?>(null) }
    val picked = remember(lines) { initiallyPicked.toMutableStateList() }

    LaunchedEffect(uri) { preview = withContext(Dispatchers.IO) { loadPreview(context, uri) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.scan_pick_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Text(
                stringResource(R.string.scan_pick_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            val shown = preview
            if (shown != null) {
                val outline = MaterialTheme.colorScheme.outline
                val chosen = MaterialTheme.colorScheme.primary
                val chosenFill = chosen.copy(alpha = 0.25f)

                Box(modifier = Modifier.fillMaxWidth().weight(1f).clipToBounds()) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(shown, lines) {
                                detectTapGestures { tap ->
                                    val scale = fit(shown, size.width.toFloat(), size.height.toFloat())
                                    hit(lines, tap, scale)?.let { line ->
                                        if (!picked.remove(line)) picked.add(line)
                                    }
                                }
                            },
                    ) {
                        val scale = fit(shown, size.width, size.height)
                        drawImage(
                            image = shown.bitmap.asImageBitmap(),
                            dstOffset = androidx.compose.ui.unit.IntOffset(scale.dx.toInt(), scale.dy.toInt()),
                            dstSize = androidx.compose.ui.unit.IntSize(
                                (shown.bitmap.width * scale.factor).toInt(),
                                (shown.bitmap.height * scale.factor).toInt(),
                            ),
                        )
                        lines.forEach { line ->
                            val marked = line in picked
                            val topLeft = Offset(
                                scale.dx + line.left * scale.perSource,
                                scale.dy + line.top * scale.perSource,
                            )
                            val boxSize = Size(
                                (line.right - line.left) * scale.perSource,
                                line.height * scale.perSource,
                            )
                            if (marked) drawRect(chosenFill, topLeft, boxSize)
                            drawRect(
                                color = if (marked) chosen else outline,
                                topLeft = topLeft,
                                size = boxSize,
                                style = Stroke(width = if (marked) 4f else 2f),
                            )
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxWidth().weight(1f))
            }

            Button(
                onClick = { onConfirm(picked.sortedWith(compareBy({ it.top }, { it.left }))) },
                enabled = picked.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) { Text(stringResource(R.string.action_continue)) }
        }
    }
}

/** The preview bitmap, and how many source pixels each of its own pixels stands for. */
private class Preview(val bitmap: Bitmap, val perSource: Float)

/** Where the preview sits inside the canvas, and how source coordinates map onto it. */
private class Fit(val dx: Float, val dy: Float, val factor: Float, val perSource: Float)

private fun fit(preview: Preview, width: Float, height: Float): Fit {
    val factor = minOf(width / preview.bitmap.width, height / preview.bitmap.height)
    val dx = (width - preview.bitmap.width * factor) / 2f
    val dy = (height - preview.bitmap.height * factor) / 2f
    return Fit(dx, dy, factor, preview.perSource * factor)
}

private fun hit(lines: List<ScannedLine>, tap: Offset, fit: Fit): ScannedLine? {
    val x = (tap.x - fit.dx) / fit.perSource
    val y = (tap.y - fit.dy) / fit.perSource
    return lines.firstOrNull { x >= it.left && x <= it.right && y >= it.top && y <= it.bottom }
}

/**
 * A leaflet photograph runs to several thousand pixels a side; it is shown at a size a screen can
 * use. Recognition read the full-size picture, so the shrinking is recorded and the line positions
 * are scaled to match rather than the picture being held twice over.
 */
private fun loadPreview(context: Context, uri: Uri): Preview? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0) return null

    val options = BitmapFactory.Options().apply {
        inSampleSize = maxOf(1, bounds.outWidth / PREVIEW_WIDTH)
    }
    val bitmap = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, options)
    } ?: return null
    return Preview(bitmap, bitmap.width.toFloat() / bounds.outWidth)
}
