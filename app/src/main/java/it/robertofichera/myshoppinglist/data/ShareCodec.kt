package it.robertofichera.myshoppinglist.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** One list as it travels between devices: products by name, because ids are local to a device. */
data class SharedList(
    val uuid: String,
    val name: String,
    val budgetCents: Long,
    val items: List<SharedItem>,
    /** The card colour as ARGB; 0 leaves it to the reader's theme. */
    val colorArgb: Int = 0,
)

data class SharedItem(
    val name: String,
    val quantity: Double,
    val priceCents: Long,
    val bought: Boolean,
)

/**
 * A shared list rides inside an ordinary chat message, so the token is looked for anywhere in the
 * text. Every malformed input decodes to null: what arrives from a messenger is untrusted.
 */
object ShareCodec {

    const val VERSION = 1

    private val TOKEN = Regex("""msl:(\d+):([A-Za-z0-9_-]+)""")

    fun encode(list: SharedList): String {
        val items = JSONArray()
        list.items.forEach { item ->
            items.put(
                JSONObject()
                    .put("n", item.name)
                    .put("q", item.quantity)
                    .put("p", item.priceCents)
                    .put("bought", item.bought),
            )
        }
        return encodeRaw(
            JSONObject()
                .put("v", VERSION)
                .put("u", list.uuid)
                .put("n", list.name)
                .put("b", list.budgetCents)
                .put("c", list.colorArgb)
                .put("i", items)
                .toString(),
        )
    }

    /** Wraps already-serialised JSON in the token, so a test can hand it something malformed. */
    fun encodeRaw(json: String): String =
        "msl:$VERSION:" + Base64.getUrlEncoder().withoutPadding().encodeToString(gzip(json))

    fun decode(text: String): SharedList? {
        val match = TOKEN.find(text) ?: return null
        if (match.groupValues[1].toIntOrNull() != VERSION) return null
        val decoded = runCatching {
            val json = JSONObject(ungzip(Base64.getUrlDecoder().decode(match.groupValues[2])))
            val items = json.getJSONArray("i")
            SharedList(
                uuid = json.getString("u"),
                name = json.getString("n"),
                budgetCents = json.getLong("b"),
                // Optional, so a share written before colours existed still reads, and one
                // written now still reads on an app that predates them. Bumping the version
                // instead would make those apps refuse the share outright.
                colorArgb = json.optInt("c", 0),
                items = List(items.length()) { index ->
                    val item = items.getJSONObject(index)
                    SharedItem(
                        name = item.getString("n"),
                        quantity = item.getDouble("q"),
                        priceCents = item.getLong("p"),
                        bought = item.getBoolean("bought"),
                    )
                },
            )
        }.getOrNull() ?: return null
        return decoded.takeIf { it.uuid.isNotBlank() && it.name.isNotBlank() }
    }

    private fun gzip(text: String): ByteArray {
        val bytes = ByteArrayOutputStream()
        GZIPOutputStream(bytes).use { it.write(text.toByteArray()) }
        return bytes.toByteArray()
    }

    private fun ungzip(bytes: ByteArray): String =
        GZIPInputStream(bytes.inputStream()).use { it.readBytes().decodeToString() }
}
