# CLAUDE.md

Guidance for Claude Code (claude.ai/code) when working in this repository.

## Overview

**My Shopping List** — a single-module Android app for keeping multiple named shopping lists, each holding products with a quantity and a price. Products live in a reusable catalog shared across lists, with autocomplete when adding an item. Jetpack Compose UI, Room for persistence, no backend and no network access.

It is deliberately small. There is no DI framework, no navigation library, no repository layer over the DAO, and one ViewModel for the whole app. Before adding any of those, check that a concrete need exists — the app is currently well under the size where they pay for themselves.

## Commands

```bash
./gradlew assembleDebug        # build the debug APK
./gradlew testDebugUnitTest    # run unit tests (MoneyTest)
./gradlew installDebug         # install onto a running device/emulator
./gradlew clean
```

There is no lint/format task wired up, and no CI.

## Project layout

Single Gradle module, `:app`. Everything lives under
`app/src/main/java/it/robertofichera/myshoppinglist/`:

```
MainActivity.kt        Activity + ShoppingApp() root composable (owns the back stack)
ShoppingViewModel.kt   the app's only ViewModel; holds the DAO and SettingsStore
Money.kt               price/quantity parsing and formatting
data/
  Entities.kt          ShoppingList + Product + Item @Entity, ItemWithProduct,
                       ListWithItems, ProductWithUsage, total helpers
  ShoppingDao.kt       @Dao, returns Flows for reads
  AppDatabase.kt       @Database(version = 3) + getInstance() + MIGRATION_1_2, MIGRATION_2_3
  Settings.kt          Settings data class + SharedPreferences-backed SettingsStore
  ProductSuggestions.kt  filterProducts / isSettledOn — pure, unit-tested
  Budget.kt            spentCents / remainingCents / overspendCents — pure, unit-tested
ui/
  ListsScreen.kt       list overview, EmptyState
  ListDetailScreen.kt  items within one list
  SettingsScreen.kt    field-visibility toggles + link to Products
  ProductsScreen.kt    catalog manager: add, edit, delete-when-unused
  ListDialog.kt        create/edit a list: name + optional max budget
  ProductDialog.kt     add/edit a product: name + default price, uniqueness enforced
  ItemDialog.kt        add/edit an item, with product autocomplete
  theme/               Material 3 theme (from the Android Studio template)
```

The launcher icon is a hand-authored adaptive icon: `res/drawable/ic_launcher_background.xml`
(flat `#6650a4`) and `ic_launcher_foreground.xml` (white basket + ticked rows). There are no
raster `mipmap-*dpi` fallbacks — `mipmap-anydpi` outranks every density qualifier and minSdk
is 31, so they would be dead weight. Keep the foreground a **single colour**: it doubles as
the `<monochrome>` layer, and a second fill would silently break the themed icon. Keep all
artwork within the centred 72×72 safe zone (corners inside radius 36 of 54,54) or masks crop it.

Unit tests: `app/src/test/java/it/robertofichera/myshoppinglist/`. There is no `androidTest` source set — instrumented-test dependencies were removed along with the template stubs. Re-add both together if you write an instrumented test.

## Key decisions

**Money is integer cents (`Long`), never `Double`.** `Item.priceCents` is the stored form; `Money.kt` parses and formats it. Parsing goes through `BigDecimal` so `"1.005"` becomes `101` rather than `100`. Both `.` and `,` are accepted as decimal separators.

**Line totals round once per line, then sum** (`Item.lineTotalCents`, `ListWithItems.totalCents`). Quantities are `Double` because decimal quantities (1.5 kg) are supported, so `quantity * priceCents` needs rounding — doing it per line and summing matches how a receipt adds up. Don't sum unrounded products.

**Products are a shared catalog, referenced by id.** They are created either from `ProductsScreen` directly or implicitly by naming one while adding a list item; the two are indistinguishable afterwards. `Item.productId` points at a `Product`; the name lives only in the catalog, so renaming a product renames it on every list including past ones. Editing a product's `defaultPriceCents` does **not** rewrite existing items — they keep the price of the trip they belong to. Deleting a product that is still referenced is blocked — `ProductsScreen` disables the button and shows the usage count, with `ForeignKey.RESTRICT` as the database-level backstop. Product lookup is `COLLATE NOCASE`, so typing "milk" reuses an existing "Milk" instead of creating a twin. `Product.defaultPriceCents` is the last price entered for it and only prefills the dialog; each item still records what that trip actually cost.

**Navigation is three saveable values, not a library.** `ShoppingApp()` holds `openListId: Long?`, `showSettings: Boolean` and `showProducts: Boolean` in `rememberSaveable`, with a `BackHandler` unwinding products → settings → lists. All three types are natively saveable, so no custom `Saver` is needed. The hierarchy is a strict linear drill-down; adopt `navigation-compose` when deep links, screen-to-screen arguments, or transition animations arrive — the screen count alone is not the trigger.

**The budget is measured against what has been spent, not what is planned.** `Remaining = budget − spent` (bought items only), and the over-budget prompt fires when *ticking an item as bought* would push spend past `ShoppingList.budgetCents`. Unticking never prompts. `budgetCents = 0` means no budget. If a list is already over, every further tick asks again — deliberately, so the warning does not go quiet once breached. Editing a bought item's price can still push spend over without prompting; the check is on ticking only.

**Settings change what is rendered, never what is stored.** `showQuantity` / `showPrice` hide fields in the dialogs and rows; values already on an `Item` are preserved and reappear when the toggle goes back on. With price shown but quantity hidden, rows display the line total (not the unit price), so a "3 × €2" line isn't misrepresented.

**`Item.listId` has a CASCADE foreign key**, so deleting a list drops its items in the database. Don't delete items manually first.

**Reads are Flows from the DAO**, collected with `collectAsStateWithLifecycle()`. Writes go through `ShoppingViewModel` in `viewModelScope`. Don't call the DAO from a composable.

## Strings

**No user-visible string belongs in Kotlin.** Everything goes through `res/values/strings.xml` and
`stringResource` / `pluralStringResource`. The app ships twelve `values*` folders: the default plus
`values-en`, `-it`, `-de`, `-nl`, `-es`, `-pt`, `-pt-rBR`, `-fr`, `-el`, `-pl`, `-hu`. Adding a
string means adding it to **all** of them; `./gradlew lintDebug` fails the build on a missing
translation.

Anything countable uses `<plurals>`, never `if (n == 1)` in code — the number of forms is per
language, not universal. Most locales here need `one`/`other`, **Polish needs `one`/`few`/`many`/
`other`** (1 / 2–4 / 5+ / fractions), and Hungarian keeps the noun singular after any number so both
its forms are deliberately identical. Never assume two forms is enough.

Format-only strings (separators, symbol layouts like `%1$s × %2$s = %3$s`) are marked
`translatable="false"` and live only in the default folder.

Currency and number formatting comes from `Money.kt` via `NumberFormat`, which follows the device
locale on its own — do not hand-format money, or an Italian device will show `$` and a full stop.

## Code style

- Kotlin official style, 4-space indent — this is what `kotlin.code.style=official` in `gradle.properties` declares. Match the existing files.
- Trailing commas on multi-line argument and parameter lists.
- No wildcard imports.
- Prefer `val`; never `!!` — use `?.`, `?:`, or a local `val` with a null check for smart casting.

### Naming

- Composables: PascalCase (`ListDetailScreen`, `ItemRow`)
- Regular functions: camelCase
- ViewModels: `{Feature}ViewModel`
- Constants: `UPPER_SNAKE_CASE`

### Comments

Comments must describe the **current** code and stand on their own. Apply the test: *would this make sense to someone reading the file cold, with no knowledge of the conversation or what was decided against?* Don't reference:

- **History / migration** — "used to be…", "replaces…"
- **Rejected alternatives** — "instead of X", "rather than Y"
- **Process state** — "for now", "TBD", "pending"

When tempted to write "X instead of Y", drop the Y half and justify X on its own terms. If that leaves nothing, the code was self-explanatory — delete the comment.

Say only what the code doesn't. Don't restate a signature or a type. Keep it to the non-obvious point, usually the *why*. Prefer no comment over a redundant one.

## Testing

`MoneyTest.kt` covers the money path — parsing, rejection of bad input, per-line rounding, and that totals sum rounded lines. Money and rounding logic must stay covered; that is where a silent bug costs the user real money.

`ProductSuggestionsTest.kt` covers `filterProducts` / `isSettledOn`. Keep autocomplete logic in `ProductSuggestions.kt` as pure functions so it stays testable without an emulator.

UI and Room wiring are verified by running the app, not by tests. If you add a Flow-level test, add Turbine at that point.

## Gotchas

**`android.disallowKotlinSourceSets=false` in `gradle.properties` is required.** KSP registers Room's generated sources through `kotlin.sourceSets`, which AGP 9's built-in Kotlin support rejects by default. Without the flag, configuration fails with "Using kotlin.sourceSets DSL to add Kotlin sources is not allowed with built-in Kotlin". Remove it once KSP registers generated sources through `android.sourceSets`.

**`material-icons-core` is an explicit dependency.** Recent Compose BOMs no longer pull it in transitively via material3, so `Icons.Default.*` fails to resolve without it.

**minSdk is 31.** API 28/30 emulator images cannot run this app, and API 31+ requires an `x86_64` system image (the 32-bit `x86` images stop at 30).

**KSP's version must track Kotlin's.** `ksp = "2.2.10-2.0.2"` pairs with `kotlin = "2.2.10"`. Bumping Kotlin without bumping KSP fails at configuration time.

**Room schema export is on**, written to `app/schemas/` via the `room.schemaLocation` ksp arg. When writing a migration, build first and copy the DDL out of the exported JSON — the migration's SQL must match Room's generated DDL exactly or the app throws `IllegalStateException: Migration didn't properly handle…` at open. Kotlin default values do **not** become SQL `DEFAULT` clauses.

**Migrations are hand-written and must be tested against real data.** There is no `MigrationTestHelper` suite (no `androidTest` source set), so the guard is: seed the old version on a device, `installDebug` over it without uninstalling, then check the data survived and `PRAGMA foreign_key_check` is clean. `MIGRATION_1_2` rebuilds `items` to add the `productId` foreign key and seeds `products` from the distinct names already in use.
