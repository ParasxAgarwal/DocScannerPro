# F-Droid Readiness

The release is designed for the official F-Droid repository.

## Dependency policy

The application has no proprietary runtime SDK, advertising SDK, analytics SDK or cloud service dependency. Barcode recognition uses ZXing Core. OCR uses Tesseract4Android, with the library built from source through the F-Droid source-library mechanism. OpenCV is consumed from Maven Central as free software.

## Network policy

The Android manifest declares only camera access. The application has no Internet permission and does not require an account, server or cloud service.

## Binary assets

OCR language data is bundled under `app/src/main/assets/tessdata/`. The included English and Hindi traineddata files are distributed under the Apache License 2.0. Their license text is included in `docs/TESSDATA_APACHE-2.0.txt`.

## F-Droid metadata

`fdroid/com.rebelroot.docscanner.yml` uses `tesseract4android@4.9.0` as a source library and compiles it during the F-Droid build before assembling the application. This avoids shipping a prebuilt Tesseract binary.

## Submission status

The repository is source-build ready for F-Droid review. Official inclusion still requires the public repository to contain the release tag and the exact full commit hash referenced by the metadata, followed by F-Droid's own lint, scan, isolated build and maintainer review.
