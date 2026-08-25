# Sharing a shopping list between users

Status: approved design, not yet implemented.

## Goal

Let one user hand a whole shopping list to another user of the app, and let the
result come back — "here is the list" / "here is what I actually bought" — over
whatever messenger the two already use. No server, no accounts, no network code.

Explicitly out of scope: live collaboration. Two phones do not see each other's
ticks in real time, and there is no per-item merge. That needs a backend and is
a different feature.

## Model

A shared list is a **copy with a stable identity**. `ShoppingList` gains a
`uuid`; the payload carries it. Importing a uuid the device has never seen
creates a list, importing one it already has replaces that list's name, budget
and items wholesale. Merge granularity is the whole list, last writer wins.

That rule is the whole conflict story. It is understandable without reading any
code: whoever imports last has what the sender had.

## Transport

Sending is `Intent.ACTION_SEND` with `text/plain` through the system share
sheet, so the app never touches the network and inherits every messenger the
user has installed. The text is readable on its own, with the machine-readable
payload on the last line:

```
Groceries
- 2 x Milk   3,00 EUR
- Bread      1,20 EUR
Total        4,20 EUR

msl:1:H4sIAAAA...
```

A recipient without the app still gets a usable list. The `msl:` line is inert
noise to them.

Receiving registers two intent filters on `MainActivity`:

1. `ACTION_SEND` / `text/plain` — the primary path. The recipient shares the
   received message *into* My Shopping List from the messenger's own share
   sheet. This works everywhere and depends on nothing.
2. `ACTION_VIEW` on `myshoppinglist://list/<payload>` — a convenience where the
   messenger linkifies custom schemes. Many do not, which is why it is not the
   primary path.

Both hand raw text to the same parser, which scans for the `msl:` token
anywhere in the input. Pasting a message into an import field would therefore
work with no extra code, though no such field is part of this design.

`MainActivity` becomes `android:launchMode="singleTop"` and implements
`onNewIntent`, so an incoming share while the app is open does not stack a
second activity.

## Payload format

`msl:<version>:<base64url of gzipped UTF-8 JSON>`

```json
{
  "v": 1,
  "u": "8f14e45fceea167a5a36dedd4bea2543",
  "n": "Groceries",
  "b": 5000,
  "i": [
    { "n": "Milk", "q": 2.0, "p": 150, "bought": false }
  ]
}
```

- `u` — the list uuid. `n` — list name. `b` — `budgetCents`, 0 for none.
- Items carry the product **name**, not an id. Ids are local to a device and
  mean nothing on another one.
- `q` is the quantity (`Double`, decimal quantities are supported), `p` is
  `priceCents` (integer cents, as everywhere else in the app).
- Quantity and price always travel, even when Settings hides them. Settings
  change what is rendered, never what is stored, and a share is storage.
- The version prefix appears twice deliberately: outside so a malformed body
  never has to be parsed to be rejected, inside so the JSON stands alone.

JSON goes through `org.json` and compression through `java.util.zip` — both are
part of the Android platform. No new dependency.

`decode` returns null rather than throwing for every failure mode: no token,
unknown version, bad Base64, bad gzip, malformed JSON, missing required field.
A share arriving from a chat is untrusted input.

## Storage change

Database version 3 -> 4. `shopping_lists` gains:

```sql
ALTER TABLE `shopping_lists` ADD COLUMN `uuid` TEXT NOT NULL DEFAULT ''
UPDATE `shopping_lists` SET `uuid` = lower(hex(randomblob(16)))
CREATE UNIQUE INDEX IF NOT EXISTS `index_shopping_lists_uuid` ON `shopping_lists` (`uuid`)
```

Existing rows are backfilled with SQLite's own randomness, so an upgraded
device can share a list it created before this feature existed. New lists get
`UUID.randomUUID()` from Kotlin at insert; the entity declares
`@ColumnInfo(defaultValue = "")` so the generated DDL matches the migration
exactly — a Kotlin default is not a SQL `DEFAULT`.

The DDL above must be re-checked against `app/schemas/.../4.json` after the
first build, per the project's standing migration rule.

## Import behaviour

`ShoppingViewModel.importFrom(text)` decodes, then looks the uuid up:

- **Known uuid** — confirm "Update <name>?", then replace name, budget and the
  entire item set of that list.
- **New uuid** — confirm "Import <name>?", then insert a new list.

Either way the work runs inside `db.withTransaction { }` (already used in the
ViewModel), so a partially imported list cannot exist.

Products resolve through the existing `findProductByName` NOCASE lookup and are
inserted when absent, which is exactly what naming a product in the item dialog
already does. Import does **not** rewrite an existing product's
`defaultPriceCents`: that field is the last price *this* user entered, and a
list arriving from someone else is not that. Item prices come from the payload.

Decoding failure surfaces as a message, not silence, and never as a partial
import.

## Files

New:
- `data/ShareCodec.kt` — `encode(ListWithItems, MoneyFormat): String` and
  `decode(text: String): SharedList?`, both pure and free of Android context.
- `ui/ImportDialog.kt` — the import/update confirmation.
- `app/src/test/.../ShareCodecTest.kt`

Changed:
- `data/Entities.kt` — `uuid` on `ShoppingList`, unique index.
- `data/AppDatabase.kt` — version 4, `MIGRATION_3_4`.
- `data/ShoppingDao.kt` — `findListByUuid`, `deleteItemsOfList`.
- `ShoppingViewModel.kt` — `shareText(listId)`, `importFrom(text)`, pending
  import state.
- `MainActivity.kt` — `onNewIntent`, intent reading, import dialog wiring.
- `ui/ListDetailScreen.kt` — the share action.
- `AndroidManifest.xml` — two intent filters, `singleTop`.
- `res/values*/strings.xml` — twelve folders, new strings and plurals.

## Testing

`ShareCodecTest` covers, as pure JVM tests:

- round trip: encode then decode returns an equal list, including a decimal
  quantity and a zero budget;
- cents survive exactly — no `Double` anywhere in the money path;
- the token is found when surrounded by chat text before and after it;
- rejection returns null for: no token, `msl:2:` (unknown version), invalid
  Base64, valid Base64 that is not gzip, gzip that is not JSON, and JSON
  missing `u`.

The migration gets the project's standing manual check: seed a version 3
database on a device, `installDebug` over it without uninstalling, confirm the
lists survived with non-empty distinct uuids and `PRAGMA foreign_key_check` is
clean.

Import wiring and the share sheet are verified by running the app: share a list
to a note-taking app, share the text back, confirm the list appears; then edit
it, share again, confirm the second import updates rather than duplicates.

## Deliberate simplifications

- Whole-list replace instead of per-item merge. Add per-item merge only with a
  backend behind it; without one it produces a worse result than the simple
  rule, because neither side knows which edit is newer.
- Custom scheme instead of verified App Links. App Links would need a hosted
  `assetlinks.json` and a domain, which this app does not have; the
  `ACTION_SEND` path already covers the case reliably.
- No compression tuning or size cap. A hundred-item list is a few hundred bytes
  gzipped; revisit only if a real list ever exceeds what a messenger will send.
