# F-Droid release

## Requirements

The release is intended to be built entirely from source. Runtime dependencies are free software, and no network service is required by the application.

Tesseract4Android 4.9.0 is referenced as an F-Droid source library. The F-Droid build compiles the library from its source before assembling the application.

## Release process

1. Update the application version and version code.
2. Run the test suite and release checks.
3. Create an annotated `v<version>` tag.
4. Push the commit and tag to the public source repository.
5. Record the full commit hash in `fdroid/com.rebelroot.docscanner.yml`.
6. Run F-Droid lint, scan and build checks in a configured build environment.
7. Submit the metadata file to the F-Droid `fdroiddata` repository.

The upstream repository does not ship generated APKs as source artifacts. F-Droid builds the APK from the public revision referenced by its metadata.

## Donations

The project publishes its donation page at https://ko-fi.com/rebelroot. The F-Droid metadata includes this HTTPS donation link, and the same link is available from the application Settings screen.
