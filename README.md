# Doc Scanner

Doc Scanner is an offline-first Android application for capturing, processing, organizing and exporting physical documents.

It is designed as a local document workstation rather than a cloud service. Scans, OCR results, notes, metadata and exports remain on the device unless the user explicitly chooses an Android share, export or print action.

## Features

### Capture

- Automatic and manual document capture
- Single-page and multi-page scanning
- Import from Android Photo Picker and document providers
- Document, book, receipt, ID card and business-card workflows
- QR and barcode recognition
- Flash control and capture guidance

### Book scanning

Book mode is designed for open spreads. It detects the spread, applies perspective correction, separates the two pages and keeps them in a single scan session. The processing layer is structured so stronger curvature-aware dewarping can be added without changing document storage.

### Image processing

- Perspective correction
- Four-corner cropping
- Rotation
- Grayscale
- Black and white conversion
- Contrast and brightness adjustment
- Document enhancement
- Shadow reduction
- Non-destructive editing where supported

### OCR

OCR runs locally using Tesseract 5 through Tesseract4Android. English and Hindi trained data are bundled with the application so the initial OCR workflows do not require network access.

The OCR layer is isolated behind `OcrEngine`, allowing additional language packs and processing strategies to be introduced without coupling the rest of the document system to a particular OCR implementation.

### PDF and export

- Single-page and multi-page PDF creation
- Searchable PDF output
- PDF page reordering
- PDF merge and split operations
- Page extraction
- OCR text export
- Text and Markdown export
- DOCX generation
- CSV and JSON export paths

### Organization

- Recent documents
- Folders
- Favorites
- Tags
- Private local vault
- List and grid views
- Sorting and filtering
- Local full-text search
- Contextual document actions

## Privacy

The application does not require an account, cloud storage, application backend, analytics SDK or advertising SDK. No account, cloud service or Internet permission is required.

The Android manifest does not request Internet access. Document capture, image processing, OCR and storage are performed locally.

Documents are stored in the application-managed file area with metadata in Room. The private vault uses Android Keystore-backed security primitives.

## Architecture

```text
app
├── core
│   ├── cv
│   ├── database
│   ├── export
│   ├── model
│   ├── ocr
│   └── security
└── ui
    ├── navigation
    ├── screens
    ├── theme
    └── viewmodel
```

The main processing path is:

```text
CameraX
  -> frame analysis
  -> document geometry detection
  -> perspective correction
  -> image enhancement
  -> OCR
  -> document storage
  -> PDF / document export
```

## Technology

- Kotlin
- Jetpack Compose
- Material 3
- CameraX
- OpenCV
- Tesseract 5
- Tesseract4Android
- ZXing Core
- Room
- DataStore
- Android Keystore
- Kotlin Coroutines

Third-party licensing information is maintained in `docs/THIRD_PARTY_LICENSES.md`.

## Build

### Requirements

- Android Studio with an Android SDK
- JDK 17
- Android SDK 36
- Android NDK 27.2.12479018 for the Tesseract4Android build

### OCR dependency for a normal developer checkout

The official F-Droid build obtains Tesseract4Android as an F-Droid source library and compiles it before building the application. A normal developer checkout can use the same source project by publishing the library to the local Maven repository first.

A convenient setup is to keep the pinned Tesseract4Android source under `third_party/Tesseract4Android` and run:

```bash
cd third_party/Tesseract4Android
./gradlew :tesseract4android:publishToMavenLocal
cd ../..
./gradlew assembleDebug
```

The application resolves the resulting `cz.adaptech:tesseract4android:4.9.0` artifact from `mavenLocal()`. For ordinary local Android Studio builds, Tesseract4Android is resolved from its published 4.9.0 JitPack coordinate; F-Droid builds force the source-built local Maven coordinate.

### Build the application

```bash
./gradlew assembleDebug
```

```bash
./gradlew assembleRelease
```

### Tests

```bash
./gradlew test
```

```bash
./gradlew connectedCheck
```

## F-Droid

The project uses ZXing for barcode recognition and Tesseract4Android for OCR.

The official F-Droid metadata builds Tesseract4Android from source through the existing `tesseract4android` F-Droid source library before building the application. No prebuilt Tesseract AAR is required in the source tree.

The application requests only the camera permission and contains no Internet permission, sign-in flow, cloud integration, analytics or advertising SDK.

The project is structured for an upstream F-Droid submission. The final F-Droid merge request must still be built and reviewed by F-Droid from the public upstream repository.

Packaging files are in `fdroid/`.

## Release process

Releases are versioned with Git tags. Each release should be built from a clean checkout of the tagged source.

Before publishing:

1. Run the application tests.
2. Build the release APK.
3. Validate the scanner and OCR on physical devices.
4. Run the F-Droid build locally against a current `fdroiddata` checkout.
5. Check the F-Droid license and source scanners.
6. Check the reproducibility result.
7. Publish the exact source tag used for the release.

## Support the project

Doc Scanner Pro is developed as free and open-source software. If it is useful to you, you can support continued development:

https://ko-fi.com/rebelroot

Support is optional and the application remains fully functional without an account, subscription or donation.

## License

Doc Scanner Pro is released under the MIT License. Third-party components retain their own licenses.

## Contributing

See `CONTRIBUTING.md` for development and review guidelines.

## Security

See `SECURITY.md` for vulnerability reporting guidance.
