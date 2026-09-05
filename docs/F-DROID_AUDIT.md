# F-Droid audit

This release is prepared for review against the official F-Droid repository requirements.

| Area | Result | Implementation |
| --- | --- | --- |
| Runtime licensing | Ready for review | Application code is MIT; dependencies are free-software licensed. |
| Proprietary SDKs | Removed | No Google Play Services, Firebase, ML Kit, analytics or advertising SDK is present. |
| OCR | Replaced | Tesseract4Android 4.9.0, built from source as an F-Droid source library. |
| Barcode scanning | Replaced | ZXing Core 3.5.4. |
| Computer vision | Free software | OpenCV 4.14.0 from Maven Central. |
| Internet access | Disabled | The manifest declares camera access only. |
| Accounts | Not required | No sign-in or account state exists. |
| Cloud storage | Not required | Documents and OCR data are kept locally. |
| Tracking | Absent | No analytics, crash reporting or telemetry SDK. |
| Advertising | Absent | No advertising SDK or ad service. |
| OCR data licensing | Documented | English and Hindi traineddata are Apache-2.0 and the license text is included. |
| Source build | Prepared | F-Droid metadata compiles Tesseract4Android from source before the application build. |
| Release pinning | Prepared | F-Droid metadata references an immutable 40-character release commit. |
| Metadata | Prepared | YAML package metadata and Fastlane store metadata are included. |
| Reproducibility | Ready for review | The project contains no bundled proprietary binary SDKs; final reproducibility is verified by F-Droid infrastructure. |

## Remaining external checks

Official inclusion is decided by F-Droid maintainers. Before submission, publish the release tag and exact commit referenced by the metadata to the public source repository, then run `fdroid lint`, `fdroid scan` and an isolated `fdroid build` in a configured F-Droid environment.
