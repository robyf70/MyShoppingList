# My Shopping List

<https://github.com/robyf70/MyShoppingList>

An Android app for keeping several named shopping lists, each holding products with a quantity
and a price. Products live in a catalogue shared across lists, so a product you bought last week
is offered as autocomplete this week, with the price you last paid.

Everything is stored on the device. There is no account, no backend, and the app requests no
permissions — it has no network access at all.

## Download

**[Latest release](https://github.com/robyf70/MyShoppingList/releases/latest)** — download the APK
and open it on the device. Android 12 (API 31) or newer. Sideloading needs "install unknown apps"
allowed for whichever app opens the file; the app itself is not on any store.

## Features

- **Multiple lists**, each with its own items, and a **Duplicate** action for the shop you repeat every week
- **Decimal quantities** (1.5 kg, 0.75 L) and per-item prices, with a running total
- **Tick items off** as you shop
- **Reusable product catalogue** with autocomplete; picking a product prefills its last price
- **Optional per-list budget** — turn it on in Settings, and the app warns before a tick pushes
  you over, showing what you have left as you shop
- **Eleven languages** — English, Italian, German, Dutch, Spanish, Portuguese (European and
  Brazilian), French, Greek, Polish and Hungarian. Prices follow the device locale too, so the same
  list reads `$1.50`, `1,50 €` or `5,55 zł` depending on where you are.

## Requirements

| | |
|---|---|
| Android device | 12 (API 31) or newer |
| Android SDK | API 37 platform installed |
| JDK | Provisioned automatically — Gradle downloads the JDK 25 toolchain on first build |

```bash
git clone https://github.com/robyf70/MyShoppingList.git
cd MyShoppingList
```

No Android Studio needed to build; the Gradle wrapper is checked in. Point Gradle at your SDK by
creating `local.properties` in the project root (Android Studio writes this for you):

```properties
sdk.dir=/path/to/Android/Sdk
```

Alternatively export `ANDROID_HOME` instead.

## Building

```bash
./gradlew assembleDebug        # debug APK -> app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # unit tests (money, budget and autocomplete logic)
./gradlew lintDebug            # Android lint, including translation completeness
./gradlew clean
```

### Signing a release build

`assembleRelease` works without any setup, but produces `app-release-unsigned.apk`, which no device
will install. To get a signed build, create a key and point the build at it.

```bash
keytool -genkeypair -v \
    -keystore ~/keys/myshoppinglist-release.jks \
    -alias myshoppinglist -keyalg RSA -keysize 4096 -validity 10000

cp keystore.properties.example keystore.properties   # then fill it in
./gradlew assembleRelease                            # -> app-release.apk, signed
```

Keep the `.jks` **outside the repository** and back it up somewhere durable. Every future update of
a published app must be signed with the same key: lose it and you cannot update the app, only
publish a new one under a different application id. Leak it and someone else can publish updates
that Android will accept as yours.

`keystore.properties`, `*.jks` and `*.keystore` are all git-ignored. CI can supply
`MSL_STORE_FILE`, `MSL_STORE_PASSWORD`, `MSL_KEY_ALIAS` and `MSL_KEY_PASSWORD` as environment
variables instead; they apply when `keystore.properties` is absent.

Verify what you produced before shipping it:

```bash
$ANDROID_HOME/build-tools/36.0.0/apksigner verify --print-certs \
    app/build/outputs/apk/release/app-release.apk
```

Release builds are minified and shrunk by R8, which takes the APK from about 30 MB to 2 MB. Debug
builds are not, so an R8 problem can only appear in a release artifact — smoke-test one before
shipping. Project-specific keep rules belong in `app/src/main/keepRules/*.keep`, which AGP combines
automatically; none are needed today.

## Installing

With a device connected over USB (with USB debugging on) or an emulator running:

```bash
./gradlew installDebug
```

Or install a built APK directly:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

**If more than one device is attached**, `adb` and Gradle will refuse to pick one. Target it
explicitly:

```bash
adb devices -l                      # find the serial
export ANDROID_SERIAL=emulator-5554 # Gradle and adb both honour this
```

### Running on an emulator

`minSdk` is 31, and API 31+ system images are **x86_64 only** — the older 32-bit `x86` images stop
at API 30 and cannot run this app. Create a suitable AVD with:

```bash
sdkmanager "system-images;android-36;google_apis;x86_64" "platforms;android-36"
avdmanager create avd -n shoppinglist -k "system-images;android-36;google_apis;x86_64" -d pixel_6
emulator -avd shoppinglist
```

On Linux, hardware acceleration needs `/dev/kvm` to be readable by your user.

### Trying another language

The app follows the system language. To check a specific translation without changing the whole
device (Android 13+):

```bash
adb shell cmd locale set-app-locales it.robertofichera.myshoppinglist --locales fr-FR
adb shell cmd locale set-app-locales it.robertofichera.myshoppinglist --locales ""   # back to system
```

## Project layout

A single Gradle module, `:app`. Jetpack Compose for UI, Room for storage; no dependency-injection
framework and no navigation library.

```
app/src/main/java/it/robertofichera/myshoppinglist/
  MainActivity.kt        Activity + root composable (owns the back stack)
  ShoppingViewModel.kt   the app's only ViewModel
  Money.kt               price/quantity parsing and formatting
  data/                  Room entities, DAO, database + migrations, settings, pure helpers
  ui/                    screens, dialogs, theme
app/src/main/res/values-*/strings.xml   translations
app/schemas/                            exported Room schemas, one per version
```

Prices are stored as **integer cents**, never floating point, and line totals are rounded once per
line before summing — the same way a receipt adds up.

`CLAUDE.md` holds the working conventions for this repository: the data model's invariants, the
migration procedure, and the code style.

## Licence

GNU General Public License v3 or later — [LICENSE](LICENSE) — **plus additional terms** under
[section 7](https://www.gnu.org/licenses/gpl-3.0.html#section7), in
[ADDITIONAL_TERMS.md](ADDITIONAL_TERMS.md).

The name **My Shopping List** and the basket icon are reserved trademarks and are not licensed with
the code. You may fork, modify and redistribute this app — including on app stores — under the GPL,
but you must do so under your own name and icon: change `app_name` in every
`res/values*/strings.xml`, replace the launcher drawables, and change the `applicationId`.
