# F-Droid packaging

The application is designed for the official F-Droid repository. The metadata file in this directory is maintained as the upstream reference for the package submission.

Before submitting a merge request to `fdroiddata`, publish the corresponding release tag and replace the build commit in `com.rebelroot.docscanner.yml` with the full commit hash for that tag.

The application contains no account flow, cloud synchronization, Internet permission, analytics, advertising or proprietary runtime SDK. OCR uses Tesseract4Android and is built from source in the F-Droid build through the `tesseract4android` source library. Barcode recognition uses ZXing Core.

Recommended verification commands in an F-Droid build environment are `fdroid lint com.rebelroot.docscanner` and `fdroid build --verbose com.rebelroot.docscanner`. F-Droid performs its own final policy, source, scan and reproducibility review.
