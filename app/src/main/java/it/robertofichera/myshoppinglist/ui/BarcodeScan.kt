package it.robertofichera.myshoppinglist.ui

import android.content.Context
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/**
 * Play Services scans the barcode in its own screen, so the app needs no camera permission and no
 * viewfinder of its own. A phone without Play Services simply never succeeds here, which is why
 * reading a picture stays the way that always works.
 */
fun scanBarcode(context: Context, onScanned: (String) -> Unit, onUnavailable: () -> Unit) {
    val options = GmsBarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
        .build()
    GmsBarcodeScanning.getClient(context, options)
        .startScan()
        .addOnSuccessListener { barcode -> barcode.rawValue?.let(onScanned) }
        // Cancelling is a decision, so it passes without a word; failing is not, and a scanner
        // that quietly does nothing is the worst of both.
        .addOnCanceledListener { }
        .addOnFailureListener { onUnavailable() }
}
