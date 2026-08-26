package it.robertofichera.myshoppinglist.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val TIMEOUT_MS = 10_000

/**
 * Reads a product name out of an Open Food Facts reply, preferring the reader's own language and
 * putting the brand in front of a name that does not already carry it: their names run from
 * "Nutella" to "Latte UHT Parz. Scremato", and a bare "Latte" on a list says too little.
 *
 * Returns null for a barcode the database does not know, which is common enough outside food to
 * be ordinary rather than an error.
 */
fun productNameFrom(json: String, language: String): String? {
    val reply = runCatching { JSONObject(json) }.getOrNull() ?: return null
    if (reply.optInt("status", 0) != 1) return null
    val product = reply.optJSONObject("product") ?: return null

    val name = listOf("product_name_$language", "product_name", "generic_name")
        .firstNotNullOfOrNull { key -> product.optString(key).takeIf { it.isNotBlank() } }
        ?: return null

    val brand = product.optString("brands")
        .split(',')
        .firstOrNull()
        ?.trim()
        .orEmpty()

    val quantity = product.optString("quantity").trim()

    return listOf(brand.takeIf { it.isNotEmpty() && !name.contains(it, ignoreCase = true) }, name, quantity.takeIf { it.isNotEmpty() })
        .filterNotNull()
        .joinToString(" ")
        .replace(Regex("""\s+"""), " ")
        .trim()
}

/**
 * Asks Open Food Facts what [barcode] is. This is the only request the app makes that is not to
 * GitHub, and it carries a barcode and nothing else — no list, no product, no identifier.
 */
suspend fun lookUpBarcode(barcode: String, language: String, userAgent: String): String? {
    val url = "https://world.openfoodfacts.org/api/v2/product/$barcode.json" +
        "?fields=product_name,product_name_$language,generic_name,brands,quantity"
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = TIMEOUT_MS
        readTimeout = TIMEOUT_MS
        // Their conditions ask that a client say what it is.
        setRequestProperty("User-Agent", userAgent)
    }
    val body = try {
        if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
        connection.inputStream.bufferedReader().readText()
    } catch (e: java.io.IOException) {
        return null
    } finally {
        connection.disconnect()
    }
    return productNameFrom(body, language)
}
