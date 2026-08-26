package it.robertofichera.myshoppinglist.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Reads [uri] and returns the items its text spells out, empty when there is nothing to read.
 * Recognition is on-device: the picture never leaves the phone.
 */
/** Everything a picture yielded: what was read, and where each line of it sat. */
data class ScanResult(
    val lines: List<ScannedLine>,
    /** The lines the label was guessed from, marked when the reader points at the picture. */
    val picked: List<ScannedLine>,
    /** The sum the page shouts, marked as the price, or null when it names none. */
    val price: ScannedLine?,
)

suspend fun scanImageForItems(context: Context, uri: Uri): ScanResult {
    val image = runCatching { InputImage.fromFilePath(context, uri) }.getOrNull()
        ?: return ScanResult(emptyList(), emptyList(), null)
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    val recognised = suspendCoroutine { continuation ->
        recognizer.process(image)
            .addOnSuccessListener { continuation.resume(it) }
            .addOnFailureListener { continuation.resume(null) }
    }
    recognizer.close()
    val lines = recognised?.textBlocks.orEmpty().flatMapIndexed { blockIndex: Int, block ->
        block.lines.mapNotNull { line ->
            line.boundingBox?.let { box ->
                ScannedLine(line.text, box.left, box.top, box.right, box.bottom, blockIndex)
            }
        }
    }
    val price = proposedPrice(lines)
    return ScanResult(lines, proposedLabel(lines, price), price)
}

/**
 * Where the camera app writes the picture being read. It is the cache, so nothing lands in the
 * gallery, and one fixed name means each picture replaces the last rather than piling up.
 */
fun newCameraImageUri(context: Context): Uri {
    val dir = File(context.cacheDir, "scans").apply { mkdirs() }
    return FileProvider.getUriForFile(context, "${context.packageName}.files", File(dir, "scan.jpg"))
}
