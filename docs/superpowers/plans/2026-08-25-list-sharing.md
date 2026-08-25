# List Sharing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a user send a whole shopping list to another user of the app through any messenger, and let an updated copy come back and replace the original.

**Architecture:** A list is serialised to a gzipped-JSON token embedded in an ordinary `text/plain` share. `ShoppingList` gains a `uuid` so a returning copy is recognised and replaces the local list wholesale (last writer wins). Receiving is a share-target intent filter plus a custom-scheme deep link, both feeding one parser. No network code, no accounts, no backend.

**Tech Stack:** Kotlin, Jetpack Compose, Room 2.8.1 (KSP), `org.json` and `java.util.zip` from the platform, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-08-25-list-sharing-design.md`

## Global Constraints

- Money is integer cents (`Long`) everywhere. Never `Double` for a price.
- No user-visible string in Kotlin. Every one goes in `res/values/strings.xml` **and all eleven** `values-en`, `-it`, `-de`, `-nl`, `-es`, `-pt`, `-pt-rBR`, `-fr`, `-el`, `-pl`, `-hu`. `./gradlew lintDebug` fails the build on a missing translation.
- Format-only strings (separators, symbol layouts) are `translatable="false"` and live only in `values/`.
- Kotlin official style, 4-space indent, trailing commas on multi-line argument lists, no wildcard imports, prefer `val`, never `!!`.
- Comments describe the current code only: no history, no rejected alternatives, no process state.
- Room schema export is on. After any `@Entity` change, build first, then copy the DDL out of `app/schemas/it.robertofichera.myshoppinglist.data.AppDatabase/<version>.json` — the migration SQL must match Room's generated DDL exactly.
- Do not call the DAO from a composable. Reads are Flows collected with `collectAsStateWithLifecycle()`; writes go through `ShoppingViewModel` in `viewModelScope`.
- Build commands: `./gradlew testDebugUnitTest`, `./gradlew assembleDebug`, `./gradlew lintDebug`, `./gradlew installDebug`.

---

### Task 1: The share codec

Pure serialisation, no Android and no Room. `org.json` ships with Android but is an empty stub in JVM unit tests, so a test-only real implementation is added alongside.

**Files:**
- Create: `app/src/main/java/it/robertofichera/myshoppinglist/data/ShareCodec.kt`
- Create: `app/src/test/java/it/robertofichera/myshoppinglist/data/ShareCodecTest.kt`
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts:88`

**Interfaces:**
- Consumes: nothing.
- Produces: `data class SharedList(uuid: String, name: String, budgetCents: Long, items: List<SharedItem>)`, `data class SharedItem(name: String, quantity: Double, priceCents: Long, bought: Boolean)`, `ShareCodec.encode(list: SharedList): String`, `ShareCodec.encodeRaw(json: String): String`, `ShareCodec.decode(text: String): SharedList?`, `ShareCodec.VERSION: Int`.

- [ ] **Step 1: Add the test-only JSON implementation**

In `gradle/libs.versions.toml`, add to `[versions]`:

```toml
json = "20250107"
```

and to `[libraries]`:

```toml
json = { group = "org.json", name = "json", version.ref = "json" }
```

In `app/build.gradle.kts`, next to the existing `testImplementation(libs.junit)`:

```kotlin
    testImplementation(libs.junit)
    // org.json is an empty stub in unit tests; this supplies the real one.
    testImplementation(libs.json)
```

- [ ] **Step 2: Write the failing test**

Create `app/src/test/java/it/robertofichera/myshoppinglist/data/ShareCodecTest.kt`:

```kotlin
package it.robertofichera.myshoppinglist.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShareCodecTest {

    private val sample = SharedList(
        uuid = "8f14e45fceea167a5a36dedd4bea2543",
        name = "Groceries",
        budgetCents = 5000,
        items = listOf(
            SharedItem(name = "Milk", quantity = 2.0, priceCents = 150, bought = false),
            SharedItem(name = "Ham", quantity = 0.25, priceCents = 1999, bought = true),
        ),
    )

    @Test
    fun `round trips a list unchanged`() {
        assertEquals(sample, ShareCodec.decode(ShareCodec.encode(sample)))
    }

    @Test
    fun `keeps prices as exact cents`() {
        val decoded = ShareCodec.decode(ShareCodec.encode(sample))
        assertEquals(listOf(150L, 1999L), decoded?.items?.map { it.priceCents })
        assertEquals(5000L, decoded?.budgetCents)
    }

    @Test
    fun `round trips a list with no budget and no items`() {
        val empty = SharedList(uuid = "abc", name = "Empty", budgetCents = 0, items = emptyList())
        assertEquals(empty, ShareCodec.decode(ShareCodec.encode(empty)))
    }

    @Test
    fun `finds the token surrounded by chat text`() {
        val message = "Here you go!\n${ShareCodec.encode(sample)}\nSee you later"
        assertEquals(sample, ShareCodec.decode(message))
    }

    @Test
    fun `rejects text with no token`() {
        assertNull(ShareCodec.decode("Remember to buy milk"))
        assertNull(ShareCodec.decode(""))
    }

    @Test
    fun `rejects an unknown version`() {
        val future = ShareCodec.encode(sample).replace("msl:1:", "msl:2:")
        assertNull(ShareCodec.decode(future))
    }

    @Test
    fun `rejects a payload that is not gzip`() {
        assertNull(ShareCodec.decode("msl:1:bm90Z3ppcA"))
    }

    @Test
    fun `rejects a payload that is not json`() {
        assertNull(ShareCodec.decode(ShareCodec.encodeRaw("not json at all")))
    }

    @Test
    fun `rejects json missing the uuid`() {
        val without = ShareCodec.encodeRaw("""{"v":1,"n":"Groceries","b":0,"i":[]}""")
        assertNull(ShareCodec.decode(without))
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests '*ShareCodecTest*'`
Expected: FAIL — unresolved references `SharedList`, `SharedItem`, `ShareCodec`.

- [ ] **Step 4: Write the implementation**

Create `app/src/main/java/it/robertofichera/myshoppinglist/data/ShareCodec.kt`:

```kotlin
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
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests '*ShareCodecTest*'`
Expected: PASS, 9 tests.

- [ ] **Step 6: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts \
  app/src/main/java/it/robertofichera/myshoppinglist/data/ShareCodec.kt \
  app/src/test/java/it/robertofichera/myshoppinglist/data/ShareCodecTest.kt
git commit -m "feat: encode and decode a shared list payload"
```

---

### Task 2: The list uuid and migration 3 to 4

**Files:**
- Modify: `app/src/main/java/it/robertofichera/myshoppinglist/data/Entities.kt:11-17`
- Modify: `app/src/main/java/it/robertofichera/myshoppinglist/data/AppDatabase.kt:10`, `:19-27`, and the `companion object` tail
- Modify: `app/src/main/java/it/robertofichera/myshoppinglist/data/ShoppingDao.kt`

**Interfaces:**
- Consumes: nothing from Task 1.
- Produces: `ShoppingList.uuid: String`, `ShoppingDao.findListByUuid(uuid: String): ShoppingList?`, `ShoppingDao.deleteItemsOfList(listId: Long)`, `AppDatabase.MIGRATION_3_4`.

- [ ] **Step 1: Add the column to the entity**

In `Entities.kt`, replace the `ShoppingList` declaration with:

```kotlin
@Entity(tableName = "shopping_lists", indices = [Index(value = ["uuid"], unique = true)])
data class ShoppingList(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    /** 0 means no budget set. The SQL default lets migration 2→3 add the column in place. */
    @ColumnInfo(defaultValue = "0") val budgetCents: Long = 0,
    /** Identifies the list across devices, so a shared copy coming back updates it in place. */
    @ColumnInfo(defaultValue = "") val uuid: String = UUID.randomUUID().toString(),
)
```

Add `import java.util.UUID` to the import block.

- [ ] **Step 2: Bump the database version and build, to get Room's DDL**

In `AppDatabase.kt`, change the annotation to `version = 4`. Then run:

`./gradlew assembleDebug`

Expected: the build writes `app/schemas/it.robertofichera.myshoppinglist.data.AppDatabase/4.json`. If it fails first with Room's "you must provide a Migration" message, that is fine — the schema file is still written, and Step 4 is the fix.

- [ ] **Step 3: Read the generated DDL**

Run:

```bash
python3 -c "
import json
d = json.load(open('app/schemas/it.robertofichera.myshoppinglist.data.AppDatabase/4.json'))
for e in d['database']['entities']:
    if e['tableName'] == 'shopping_lists':
        print(e['createSql'])
        print(json.dumps(e.get('indices', []), indent=1))
"
```

The migration in Step 4 must match this output exactly. If the generated column definition or index name differs from what is written below, the generated one wins — edit the migration to match it.

- [ ] **Step 4: Write the migration**

In `AppDatabase.kt`, add `MIGRATION_3_4` to the builder:

```kotlin
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build().also { instance = it }
```

and add this at the end of the `companion object`, after `MIGRATION_2_3`:

```kotlin
        /**
         * Identifies each list across devices. Existing rows are filled in so a list created
         * before sharing existed can still be shared and recognised when it comes back.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `shopping_lists` ADD COLUMN `uuid` TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE `shopping_lists` SET `uuid` = lower(hex(randomblob(16)))")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_shopping_lists_uuid` ON `shopping_lists` (`uuid`)"
                )
            }
        }
```

- [ ] **Step 5: Add the DAO queries the import will need**

In `ShoppingDao.kt`, after `deleteList`:

```kotlin
    @Query("SELECT * FROM shopping_lists WHERE uuid = :uuid LIMIT 1")
    suspend fun findListByUuid(uuid: String): ShoppingList?

    @Query("DELETE FROM items WHERE listId = :listId")
    suspend fun deleteItemsOfList(listId: Long)
```

- [ ] **Step 6: Build and run the existing tests**

Run: `./gradlew assembleDebug testDebugUnitTest`
Expected: PASS. `addList` needs no change — `ShoppingList(name = …, budgetCents = …)` picks up the Kotlin default uuid.

- [ ] **Step 7: Verify the migration against real data**

There is no `androidTest` source set, so this check is manual and is the only thing standing between a bad migration and a crash on every existing install:

```bash
git stash                     # back to the version 3 app
./gradlew installDebug
# on the device: create two lists with a few items each
git stash pop
./gradlew installDebug        # upgrade in place, do NOT uninstall
```

On the device, confirm both lists and their items are still there. Then:

```bash
adb shell "run-as it.robertofichera.myshoppinglist sqlite3 databases/shopping.db \
  'SELECT id, name, uuid FROM shopping_lists; PRAGMA foreign_key_check;'"
```

Expected: every row has a distinct non-empty uuid, and `foreign_key_check` prints nothing.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/it/robertofichera/myshoppinglist/data/Entities.kt \
  app/src/main/java/it/robertofichera/myshoppinglist/data/AppDatabase.kt \
  app/src/main/java/it/robertofichera/myshoppinglist/data/ShoppingDao.kt \
  app/schemas
git commit -m "feat: identify lists by uuid across devices"
```

---

### Task 3: Sharing a list out

**Files:**
- Modify: `app/src/main/java/it/robertofichera/myshoppinglist/ShoppingViewModel.kt`
- Modify: `app/src/main/java/it/robertofichera/myshoppinglist/ui/ListDetailScreen.kt` (the `TopAppBar` at lines 78-103)
- Modify: `app/src/main/res/values/strings.xml` and the eleven `values-*` folders

**Interfaces:**
- Consumes: `SharedList`, `SharedItem`, `ShareCodec.encode` (Task 1); `ShoppingList.uuid` (Task 2).
- Produces: `ShoppingViewModel.shareText(entry: ListWithItems, money: MoneyFormat): String`.

- [ ] **Step 1: Add the strings**

In `app/src/main/res/values/strings.xml`, add under the Actions block:

```xml
    <string name="action_share">Share</string>
```

and at the end of the format-only block:

```xml
    <string name="format_share_line" translatable="false">- %1$s × %2$s = %3$s</string>
```

Then add `action_share` to every other folder — `format_share_line` stays in `values/` only:

| folder | value |
|---|---|
| `values-en` | `Share` |
| `values-it` | `Condividi` |
| `values-de` | `Teilen` |
| `values-nl` | `Delen` |
| `values-es` | `Compartir` |
| `values-pt` | `Partilhar` |
| `values-pt-rBR` | `Compartilhar` |
| `values-fr` | `Partager` |
| `values-el` | `Κοινή χρήση` |
| `values-pl` | `Udostępnij` |
| `values-hu` | `Megosztás` |

- [ ] **Step 2: Build the share text in the ViewModel**

In `ShoppingViewModel.kt`, add this method. It is synchronous — it only reads the list already in hand:

```kotlin
    /**
     * Readable on its own for someone without the app; the token on the last line is what the
     * app reads back. Quantity and price always travel, whatever Settings currently renders.
     */
    fun shareText(entry: ListWithItems, money: MoneyFormat): String {
        val app = getApplication<Application>()
        val lines = entry.items.map { row ->
            app.getString(
                R.string.format_share_line,
                row.product.name,
                formatQuantity(row.item.quantity),
                money.format(row.lineTotalCents),
            )
        }
        val total = app.getString(
            R.string.format_dot_pair,
            app.getString(R.string.summary_to_spend),
            money.format(entry.totalCents),
        )
        val payload = ShareCodec.encode(
            SharedList(
                uuid = entry.list.uuid,
                name = entry.list.name,
                budgetCents = entry.list.budgetCents,
                items = entry.items.map { row ->
                    SharedItem(
                        name = row.product.name,
                        quantity = row.item.quantity,
                        priceCents = row.item.priceCents,
                        bought = row.item.bought,
                    )
                },
            ),
        )
        return (listOf(entry.list.name) + lines + listOf(total, "", payload)).joinToString("\n")
    }
```

Add these imports to `ShoppingViewModel.kt`:

```kotlin
import it.robertofichera.myshoppinglist.data.ShareCodec
import it.robertofichera.myshoppinglist.data.SharedItem
import it.robertofichera.myshoppinglist.data.SharedList
import it.robertofichera.myshoppinglist.data.lineTotalCents
import it.robertofichera.myshoppinglist.data.totalCents
```

- [ ] **Step 3: Add the share action to the list detail top bar**

In `ListDetailScreen.kt`, add an `actions` block to the `TopAppBar`, immediately after the existing `navigationIcon` block:

```kotlin
                actions = {
                    val context = LocalContext.current
                    val money = LocalMoneyFormat.current
                    IconButton(
                        onClick = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, entry.list.name)
                                putExtra(Intent.EXTRA_TEXT, viewModel.shareText(entry, money))
                            }
                            context.startActivity(Intent.createChooser(send, null))
                        },
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = stringResource(R.string.action_share),
                        )
                    }
                },
```

Add these imports:

```kotlin
import android.content.Intent
import androidx.compose.material.icons.filled.Share
```

- [ ] **Step 4: Build and lint**

Run: `./gradlew assembleDebug lintDebug`
Expected: PASS. A `MissingTranslation` failure here means one of the eleven folders in Step 1 was missed.

- [ ] **Step 5: Check it on a device**

Run: `./gradlew installDebug`

Open a list with a few items, tap Share, send it to a notes app. Confirm the text reads sensibly, the total matches the list's "To spend", and the last line starts with `msl:1:`. Keep that text — Task 4 imports it.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/it/robertofichera/myshoppinglist/ShoppingViewModel.kt \
  app/src/main/java/it/robertofichera/myshoppinglist/ui/ListDetailScreen.kt \
  app/src/main/res
git commit -m "feat: share a list through the system share sheet"
```

---

### Task 4: Importing a shared list

**Files:**
- Modify: `app/src/main/java/it/robertofichera/myshoppinglist/ShoppingViewModel.kt`
- Create: `app/src/main/java/it/robertofichera/myshoppinglist/ui/ImportDialog.kt`
- Modify: `app/src/main/java/it/robertofichera/myshoppinglist/MainActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/values/strings.xml` and the eleven `values-*` folders

**Interfaces:**
- Consumes: `ShareCodec.decode`, `SharedList` (Task 1); `findListByUuid`, `deleteItemsOfList`, `ShoppingList.uuid` (Task 2).
- Produces: `ShoppingViewModel.pendingImport: StateFlow<ImportState>`, `ShoppingViewModel.offerImport(text: String?)`, `ShoppingViewModel.confirmImport()`, `ShoppingViewModel.dismissImport()`, and `sealed interface ImportState` with `ImportState.None`, `ImportState.Offered(shared: SharedList, existing: ShoppingList?)`, `ImportState.Failed`.

- [ ] **Step 1: Add the strings**

In `app/src/main/res/values/strings.xml`, add a new block after the List detail block:

```xml
    <!-- Import -->
    <string name="import_title">Import %1$s?</string>
    <string name="import_message">It will be added as a new list.</string>
    <string name="import_update_title">Update %1$s?</string>
    <string name="import_update_message">Your copy of this list will be replaced with the shared one.</string>
    <string name="import_failed">That message does not contain a shared list</string>
```

The confirm and cancel buttons reuse the existing `action_continue` and `action_cancel`. Translations for all eleven other folders:

**`import_title`** — en `Import %1$s?` · it `Importare %1$s?` · de `%1$s importieren?` · nl `%1$s importeren?` · es `¿Importar %1$s?` · pt `Importar %1$s?` · pt-rBR `Importar %1$s?` · fr `Importer %1$s ?` · el `Εισαγωγή %1$s;` · pl `Zaimportować %1$s?` · hu `Importálja a(z) %1$s listát?`

**`import_message`** — en `It will be added as a new list.` · it `Verrà aggiunta come nuova lista.` · de `Sie wird als neue Liste hinzugefügt.` · nl `Deze wordt als nieuwe lijst toegevoegd.` · es `Se añadirá como una lista nueva.` · pt `Será adicionada como uma nova lista.` · pt-rBR `Será adicionada como uma nova lista.` · fr `Elle sera ajoutée comme nouvelle liste.` · el `Θα προστεθεί ως νέα λίστα.` · pl `Zostanie dodana jako nowa lista.` · hu `Új listaként lesz hozzáadva.`

**`import_update_title`** — en `Update %1$s?` · it `Aggiornare %1$s?` · de `%1$s aktualisieren?` · nl `%1$s bijwerken?` · es `¿Actualizar %1$s?` · pt `Atualizar %1$s?` · pt-rBR `Atualizar %1$s?` · fr `Mettre à jour %1$s ?` · el `Ενημέρωση %1$s;` · pl `Zaktualizować %1$s?` · hu `Frissíti a(z) %1$s listát?`

**`import_update_message`** — en `Your copy of this list will be replaced with the shared one.` · it `La tua copia di questa lista verrà sostituita con quella condivisa.` · de `Deine Kopie dieser Liste wird durch die geteilte ersetzt.` · nl `Je kopie van deze lijst wordt vervangen door de gedeelde versie.` · es `Tu copia de esta lista se sustituirá por la compartida.` · pt `A tua cópia desta lista será substituída pela partilhada.` · pt-rBR `Sua cópia desta lista será substituída pela compartilhada.` · fr `Votre copie de cette liste sera remplacée par celle partagée.` · el `Το αντίγραφό σας αυτής της λίστας θα αντικατασταθεί από την κοινόχρηστη.` · pl `Twoja kopia tej listy zostanie zastąpiona udostępnioną.` · hu `A lista saját másolatát felülírja a megosztott változat.`

**`import_failed`** — en `That message does not contain a shared list` · it `Il messaggio non contiene una lista condivisa` · de `Diese Nachricht enthält keine geteilte Liste` · nl `Dit bericht bevat geen gedeelde lijst` · es `Ese mensaje no contiene ninguna lista compartida` · pt `Essa mensagem não contém uma lista partilhada` · pt-rBR `Essa mensagem não contém uma lista compartilhada` · fr `Ce message ne contient aucune liste partagée` · el `Αυτό το μήνυμα δεν περιέχει κοινόχρηστη λίστα` · pl `Ta wiadomość nie zawiera udostępnionej listy` · hu `Ez az üzenet nem tartalmaz megosztott listát`

- [ ] **Step 2: Add the import state and logic to the ViewModel**

In `ShoppingViewModel.kt`, add above the class:

```kotlin
/** [Offered.existing] is the local list this share replaces, or null when it is new here. */
sealed interface ImportState {
    data object None : ImportState
    data class Offered(val shared: SharedList, val existing: ShoppingList?) : ImportState
    data object Failed : ImportState
}
```

and inside the class, next to the other state:

```kotlin
    private val _pendingImport = MutableStateFlow<ImportState>(ImportState.None)
    val pendingImport: StateFlow<ImportState> = _pendingImport.asStateFlow()

    /** Decodes a share and asks before touching anything; [text] is whatever the intent carried. */
    fun offerImport(text: String?) = viewModelScope.launch {
        val shared = text?.let { ShareCodec.decode(it) }
        _pendingImport.value = if (shared == null) {
            ImportState.Failed
        } else {
            ImportState.Offered(shared, dao.findListByUuid(shared.uuid))
        }
    }

    fun dismissImport() {
        _pendingImport.value = ImportState.None
    }

    /**
     * One transaction, so a half-imported list cannot exist. An existing list keeps its row and
     * loses its items; the shared copy wins outright.
     */
    fun confirmImport() = viewModelScope.launch {
        val offered = _pendingImport.value as? ImportState.Offered ?: return@launch
        _pendingImport.value = ImportState.None
        db.withTransaction {
            val listId = offered.existing?.let { existing ->
                dao.updateList(
                    existing.copy(name = offered.shared.name, budgetCents = offered.shared.budgetCents),
                )
                dao.deleteItemsOfList(existing.id)
                existing.id
            } ?: dao.insertList(
                ShoppingList(
                    name = offered.shared.name,
                    budgetCents = offered.shared.budgetCents,
                    uuid = offered.shared.uuid,
                ),
            )
            offered.shared.items.forEach { item ->
                // An existing product's defaultPriceCents is the last price this user entered,
                // which someone else's list is not, so it is left alone.
                val productId = dao.findProductByName(item.name)?.id
                    ?: dao.insertProduct(Product(name = item.name, defaultPriceCents = item.priceCents))
                dao.insertItem(
                    Item(
                        listId = listId,
                        productId = productId,
                        quantity = item.quantity,
                        priceCents = item.priceCents,
                        bought = item.bought,
                    ),
                )
            }
        }
    }
```

- [ ] **Step 3: Write the import dialog**

Create `app/src/main/java/it/robertofichera/myshoppinglist/ui/ImportDialog.kt`:

```kotlin
package it.robertofichera.myshoppinglist.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import it.robertofichera.myshoppinglist.ImportState
import it.robertofichera.myshoppinglist.R

@Composable
fun ImportDialog(
    state: ImportState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (state) {
        is ImportState.None -> Unit

        is ImportState.Failed -> AlertDialog(
            onDismissRequest = onDismiss,
            text = { Text(stringResource(R.string.import_failed)) },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            },
        )

        is ImportState.Offered -> AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    stringResource(
                        if (state.existing == null) R.string.import_title else R.string.import_update_title,
                        state.shared.name,
                    )
                )
            },
            text = {
                Text(
                    stringResource(
                        if (state.existing == null) R.string.import_message else R.string.import_update_message,
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_continue)) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}
```

- [ ] **Step 4: Register the two intent filters**

In `AndroidManifest.xml`, replace the `<activity>` element with:

```xml
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:label="@string/app_name"
            android:launchMode="singleTop"
            android:theme="@style/Theme.MyShoppingList"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />

                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>

            <!-- Share a received message into the app: works from every messenger. -->
            <intent-filter>
                <action android:name="android.intent.action.SEND" />

                <category android:name="android.intent.category.DEFAULT" />

                <data android:mimeType="text/plain" />
            </intent-filter>

            <!-- Tapping the payload where a messenger turns it into a link. -->
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />

                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />

                <data android:scheme="myshoppinglist" />
            </intent-filter>
        </activity>
```

`launchMode="singleTop"` keeps an incoming share from stacking a second copy of the activity on the one already open.

- [ ] **Step 5: Read the intent in MainActivity**

In `MainActivity.kt`, replace the class body with:

```kotlin
class MainActivity : ComponentActivity() {

    private val viewModel: ShoppingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        offerImport(intent)
        setContent {
            MyShoppingListTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    ShoppingApp(viewModel)
                }
            }
        }
    }

    /** singleTop, so a share arriving while the app is open lands here instead of a new activity. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        offerImport(intent)
    }

    /** Both filters carry the payload as text; the codec finds the token wherever it sits. */
    private fun offerImport(intent: Intent) {
        val text = when (intent.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> intent.dataString
            else -> return
        }
        viewModel.offerImport(text)
    }
}
```

Add these imports:

```kotlin
import android.content.Intent
import androidx.activity.viewModels
```

- [ ] **Step 6: Show the dialog**

`ShoppingApp`'s signature is unchanged — it already takes a `ShoppingViewModel` with a default, and `MainActivity` now passes its own instance.

Collect the state next to the others:

```kotlin
    val pendingImport by viewModel.pendingImport.collectAsStateWithLifecycle()
```

Inside the `CompositionLocalProvider`, wrap the existing `when { … }` screen selector and the new dialog in one `Box`, so both are children of a single composable:

```kotlin
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                // …the existing screen selection, unchanged…
            }
            ImportDialog(
                state = pendingImport,
                onConfirm = viewModel::confirmImport,
                onDismiss = viewModel::dismissImport,
            )
        }
```

Add `import it.robertofichera.myshoppinglist.ui.ImportDialog`. `Box`, `fillMaxSize` and `Modifier` are already imported.

- [ ] **Step 7: Build and lint**

Run: `./gradlew assembleDebug lintDebug testDebugUnitTest`
Expected: PASS.

- [ ] **Step 8: Check the round trip on a device**

Run: `./gradlew installDebug`

1. Share a list to a notes app. In the notes app, share that text back into My Shopping List. Expect "Import <name>?" and, on Continue, a new list identical to the original — same items, quantities, prices and tick states.
2. Tick an item in the imported list, share it again, and import that text once more. Expect "Update <name>?" this time, and after Continue **one** list, not two, carrying the new tick.
3. Share a message with no `msl:` token into the app. Expect the "does not contain a shared list" dialog and no change to any list.
4. Confirm the products from the imported list appear in Settings → Products, and that an existing product's default price was not overwritten.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/it/robertofichera/myshoppinglist/ShoppingViewModel.kt \
  app/src/main/java/it/robertofichera/myshoppinglist/MainActivity.kt \
  app/src/main/java/it/robertofichera/myshoppinglist/ui/ImportDialog.kt \
  app/src/main/AndroidManifest.xml app/src/main/res
git commit -m "feat: import a shared list, updating it when it is already known"
```

---

### Task 5: Release smoke test and documentation

**Files:**
- Modify: `CLAUDE.md`

**Interfaces:**
- Consumes: everything above.
- Produces: nothing code depends on.

- [ ] **Step 1: Smoke-test a release build**

Release builds are minified by R8 and debug builds are not, so a missing keep rule only ever shows up here, at runtime:

```bash
./gradlew assembleRelease
```

Install the release APK and repeat the round trip from Task 4 Step 8: share a list, import it back, confirm the update path. `org.json` and `java.util.zip` are platform classes and need no keep rules, but this is where that assumption gets tested.

- [ ] **Step 2: Document the feature in CLAUDE.md**

Add to the Key decisions section, after the budget paragraph:

```markdown
**A list is shared as a copy with a stable identity.** `ShoppingList.uuid` travels
with the share, so re-importing a list the device already has replaces it rather than
duplicating it; merge granularity is the whole list, last writer wins. The payload is
gzipped JSON behind an `msl:<version>:` token inside an ordinary `text/plain` share, so
the text stays readable to someone without the app and the app never touches the network
to send it. Items travel by product *name* — ids are local to a device. Receiving is a
`text/plain` share target plus a `myshoppinglist://` scheme; both hand raw text to
`ShareCodec.decode`, which returns null for anything malformed because a chat message is
untrusted. Import never rewrites an existing product's `defaultPriceCents`: that is the
last price *this* user entered.
```

Add `ShareCodec.kt` and `ImportDialog.kt` to the project layout tree, note `@Database(version = 4)` and `MIGRATION_3_4` on the `AppDatabase.kt` line, and add to the Testing section:

```markdown
`ShareCodecTest.kt` covers `ShareCodec`. Every malformed input must decode to null —
a share arrives from a messenger, so it is untrusted, and a partial import is worse
than no import.
```

- [ ] **Step 3: Commit**

```bash
git add CLAUDE.md
git commit -m "docs: record how list sharing works"
```
