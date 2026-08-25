package it.robertofichera.myshoppinglist.ui

import android.content.Context
import android.content.Intent

/** Hands [text] to the system share sheet, titled with the list's [name]. */
fun shareList(context: Context, name: String, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, name)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, null))
}
