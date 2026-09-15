# Raktár Kereső (Warehouse Locator) — v2 update

Native Kotlin Android app, MVVM, Room/SQLite, fully offline. This is a
**feature update** over the previous version — same `applicationId`
(`com.example.raktarkereso`), same tech stack, `versionCode` 1 → 2,
`versionName` "1.0" → "2.0", with a real Room migration so existing
on-device data survives the update (no destructive migration anywhere).

## ⚠️ Same honest caveat as before, about the Gradle Wrapper jar

`gradle/wrapper/gradle-wrapper.jar` is a compiled **binary**. This was built
in a sandbox with no internet access and no Java compiler (`javac` — only a
JRE is present), so there was no legitimate way to fetch or produce that
exact binary here. Rather than ship a hand-fabricated jar that might be
silently broken, it's generated automatically by the CI workflow itself,
which runs on infrastructure that *does* have internet access.

`.github/workflows/android-ci.yml` runs, in order:
1. `actions/checkout@v4`
2. `actions/setup-java@v4` (Temurin 17)
3. `gradle wrapper --gradle-version 8.7` — regenerates a correct, verified
   `gradlew`, `gradlew.bat`, and `gradle-wrapper.jar` using the Gradle
   installation pre-installed on GitHub's `ubuntu-latest` runners
4. `chmod +x ./gradlew`
5. `./gradlew assembleDebug --stacktrace --no-daemon`
6. `actions/upload-artifact@v4` — uploads `app-debug.apk`

Push this project to a GitHub repo and it builds automatically, no manual
steps required. Opening the project in Android Studio also regenerates a
working wrapper automatically on first sync, for local development.

## What changed in this update

### 1. Edit and delete
- Every result card now has **"Szerkesztés"** and **"Törlés"** buttons.
- Edit reuses `AddItemActivity` in an edit mode (pre-filled from the existing
  row) and calls Room's `@Update`, so it modifies the same primary-key row —
  never a duplicate insert.
- Delete asks **"Biztosan törölni szeretnéd ezt a terméket?"** with
  **"Mégse"** / **"Törlés"** buttons, then shows **"Termék sikeresen
  törölve"** and refreshes the list immediately.
- Successful edits show **"Termék sikeresen módosítva"**.

### 2. Inventory fields
`InventoryItem` now has: stable `id`, `productName`, optional `productCode`,
`warehouseName`, `storageUnitName`, optional `quantity` (Double), optional
`unit` (free text, with kg/liter/db suggestions), optional `notes`,
`createdAt`, and `modifiedAt`. Only `productName`, `warehouseName`, and
`storageUnitName` are required.

### 3. Duplicate handling
Before inserting or renaming a product, the app checks for existing rows
with the same product name or product code (in *any* location) and — if
found — shows a **"Figyelem"** dialog asking whether to save anyway
(**"Mentés mégis"** / **"Mégse"**). It never silently blocks the save, since
the same product legitimately can live in multiple storage units.

### 4. Search and filtering
- Search matches partial product name **or** product code.
- Matching is done in Kotlin with a Hungarian-aware locale
  (`Locale.forLanguageTag("hu")`), not SQLite's default `NOCASE`, so accented
  characters (á, é, í, ó, ö, ő, ú, ü, ű) compare correctly.
- Two spinners filter by warehouse and storage unit independently, both
  combinable with the text search.
- **"Összes megjelenítése"** shows every record (also reachable from the
  Settings screen).

### 5 & 6. CSV export / import
- **"Adatok exportálása CSV-fájlba"** uses `ActivityResultContracts.CreateDocument("text/csv")`
  (Storage Access Framework), suggests `raktar_export.csv`, writes UTF-8,
  and includes every field (ID, name, code, warehouse, storage unit,
  quantity, unit, notes, created/modified timestamps).
- **"Adatok importálása CSV-fájlból"** uses `OpenDocument()`, reads UTF-8,
  validates every row (required name, warehouse/storage-unit must be one of
  the fixed values, quantity must parse if present). If **any** row is
  invalid, nothing is written and a Hungarian message lists every bad row
  number and reason. On success, you're asked **"Adatok hozzáadása"** vs
  **"Meglévő adatok cseréje"** before anything touches the database.
- The CSV reader/writer is hand-written (proper quoting for commas/quotes/
  newlines) — no extra library, to keep the build dependency-light.

### 7. Full backup & restore
- **"Teljes biztonsági mentés"** writes a portable JSON file (`raktar_backup.json`)
  with every field, via `CreateDocument("application/json")`.
- **"Biztonsági mentés visszaállítása"** opens a JSON file, fully validates
  its structure and every warehouse/storage-unit value *before* touching the
  database, then asks for confirmation
  ("Ez felülírja a jelenlegi adatokat... Biztosan folytatod?") before
  replacing existing data.
- Uses `org.json` (bundled with Android) — no new dependency.

### 8. Settings / Adatkezelés screen
New `SettingsActivity` with: show all records, CSV export, CSV import, full
backup, restore backup, and **"Összes adat törlése"**, which requires
explicit confirmation ("Ez a művelet nem vonható vissza.").

### 9. Database safety
- `id` remains a stable auto-generated primary key, never reused.
- `AppDatabase` bumped from version 1 → 2 with an explicit `Migration(1, 2)`
  (adds the new columns via `ALTER TABLE`, backfills `modifiedAt`) —
  `fallbackToDestructiveMigration()` is never used anywhere.
- All DB and file I/O runs via coroutines on `Dispatchers.IO`.

### 10. Update compatibility
`applicationId` is unchanged; `versionCode`/`versionName` bumped. Installing
this APK over the previous one (signed with the same key) is a normal
update — the Room migration means existing inventory data is kept, not reset.

### 11. Hungarian throughout
Every screen title, button, label, validation message, confirmation dialog,
and success/error message is Hungarian — see `strings.xml`.

## A note on how "test migration" was handled here

This sandbox has no Android SDK/emulator to actually run an instrumented
migration test. `MIGRATION_1_2` was written carefully by hand (simple
additive `ALTER TABLE` statements, which is the lowest-risk kind of Room
migration), and the whole project was checked with `xmllint`, a Kotlin
brace-balance check, an `R.string.*` cross-reference against `strings.xml`,
and a `binding.*` cross-reference against every layout's view IDs — all of
which passed. If you want to be fully sure before shipping, Room's official
`MigrationTestHelper` (androidx.room:room-testing) with a `schemas/1.json`
export is the standard tool for this; wiring that up is a good next step
once you have a real Android build environment.

## Project structure (new/changed vs the previous zip)

```
app/src/main/java/com/example/raktarkereso/
├── MainActivity.kt                  (updated: filters, edit/delete, settings nav)
├── AddItemActivity.kt               (updated: add + edit modes, duplicate dialog)
├── SettingsActivity.kt              (NEW: CSV/backup/restore/delete-all, SAF-based)
├── data/
│   ├── WarehouseConstants.kt        (updated: "Komido" label, unit suggestions)
│   ├── AppLocale.kt                 (NEW: shared Hungarian locale)
│   ├── InventoryItem.kt             (updated: new optional fields)
│   ├── InventoryDao.kt              (updated: update/delete/deleteAll/replaceAll)
│   ├── AppDatabase.kt               (updated: v2 + MIGRATION_1_2)
│   └── InventoryRepository.kt       (updated: filtered search, duplicate check)
├── io/
│   ├── CsvManager.kt                (NEW: CSV export/import + validation)
│   └── BackupManager.kt             (NEW: JSON backup/restore + validation)
├── viewmodel/
│   ├── MainViewModel.kt             (updated: filters, delete, refresh)
│   ├── AddItemViewModel.kt          (updated: edit mode, duplicate detection)
│   └── ViewModelFactory.kt          (unchanged)
└── adapter/
    └── SearchResultAdapter.kt       (updated: edit/delete buttons, new fields)

app/src/main/res/layout/
├── activity_main.xml                (updated: filters, show-all, settings button)
├── activity_add_item.xml            (updated: code/quantity/unit/notes fields)
├── activity_settings.xml            (NEW)
└── item_search_result.xml           (updated: code/quantity/notes, edit/delete)
```

## Data model

- **4 warehouses**: I. raktár, II. raktár, IV. raktár, Komido
- **8 storage units per warehouse**: Jobb fent, Jobb lent, Középső jobb fent,
  Középső jobb lent, Középső bal fent, Középső bal lent, Bal fent, Bal lent
