# Doc Scanner Pro — Fix batch/edit bugs + PDF tools + notification permission

## Root causes found

1. **Slow "Done"**: `finishBatchAndSave` runs Tesseract OCR on every page *before* navigating (seconds per page), with no progress UI.
2. **Batch pages "not opening" / dead edit screens**: `ScannedPageDraft` is a mutable data class; `applyFilterToEditingPage`, `updateEditingPageCorners` and `rotateEditingPage` mutate it in place and re-assign the *same instance* to the `editingPage` StateFlow → Compose never recomposes, so the filter preview, chips, crop result and rotate all look broken/dead.
3. **Themes (B&W etc.) "not working"**: same state bug as #2; plus B&W uses a single global threshold that looks bad on shadowed pages.
4. **Multi-image import problems**: images are decoded on the main thread at full resolution (30 photos → OOM/ANR), no EXIF rotation, `setScanMode()` resets the "Processing" state set by import, errors are invisible, and the single-image path from Home bounces back to Home (`editingPage` never set).
5. **No notification prompt**: MainActivity already requests `POST_NOTIFICATIONS`, but the manifest never declares the permission → Android silently denies without showing a dialog.

## Fixes

**A. Fast save + background OCR** (`ScanViewModel`, `DocumentViewModel`)
- `finishBatchAndSave` → fast path: write page files, insert document + pages (empty OCR), navigate to Detail immediately, showing a "Saving page X of Y" overlay in ScannerScreen.
- OCR moves to `DocumentViewModel`: when `loadDocument()` finds pages with blank OCR, it OCRs them in the background and updates the DB + `currentPages` as each finishes (resumes on revisit).

**B. Fix the dead edit loop** (`ScanViewModel`, `FilterAndEditScreen`, `ManualCropScreen`)
- Make `ScannedPageDraft` immutable (`val`); all updates via `copy(...)` so StateFlow actually emits; show a spinner while crop/filter applies; surface failures in the filter screen.

**C. Better B&W** (`ImageEnhancer`)
- Replace global-threshold B&W with OpenCV adaptive threshold (handles shadows/uneven lighting); grayscale/vivid unchanged.

**D. Robust multi-image + PDF import** (`ScanViewModel`, `AppNavigation`, `ScannerScreen`, `HomeScreen`)
- New `importImages(uris)` in the ViewModel: decodes off the main thread with `inSampleSize` downscale (max ~2560px — fixes blank crop screen on 12k-px photos and OOM), applies EXIF rotation, closes streams safely, tolerates per-image failures, appends pages incrementally with progress ("Importing 3/12…").
- Call `setScanMode` *before* import so state isn't wiped; navigate to the scanner so errors are visible.
- Single-image import: create draft + set `editingPage` → open ManualCrop properly (fixes bounce-back).
- PDF import: `OpenMultipleDocuments` (application/pdf) → render pages via `PdfRenderer` → each page becomes an editable draft; wired from Home and Scanner import buttons.

**E. Notification permission** (`AndroidManifest.xml`)
- Add `<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>` — the existing request in MainActivity then actually shows the dialog on Android 13+.

## New: PDF tools in Tools tab (all 6)

- Dependency: `com.tom-roush:pdfbox-android` (Apache 2.0) in `libs.versions.toml` + `app/build.gradle.kts` + init (`PDFBoxResourceLoader.init`) + R8 keep rules.
- New `PdfToolsViewModel` + `PdfToolScreen` (one screen, tool-type arg) with file picker → options → progress → result in `files/exports/` + share via FileProvider (existing pattern).
- Tools: **Images→PDF** (reuse PdfExporter with a bitmap-list entry point), **Merge PDFs**, **Split/Extract pages** (range or every page), **PDF→Images** (PdfRenderer → JPEGs), **Compress PDF** (render + rebuild at chosen quality), **Protect PDF** (password, pdfbox).
- `ToolsScreen` gets a "PDF tools" section; new route `pdf_tools/{toolId}` in `AppNavigation`.

## Verification

- `gradlew assembleDebug` + existing unit tests; add a Robolectric test for merge/split.
- Run on the Android emulator: batch capture → Done speed, tap thumbnails → crop/filter/B&W updates, import 3+ photos + a PDF, run each PDF tool, notification prompt on API 33+.