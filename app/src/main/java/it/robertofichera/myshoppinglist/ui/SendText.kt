package it.robertofichera.myshoppinglist.ui

import android.content.Context
import android.content.Intent

/** Hands [text] to the system share sheet, titled [subject]. */
fun sendText(context: Context, subject: String, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, null))
}
