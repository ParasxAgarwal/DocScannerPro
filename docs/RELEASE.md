# Release checklist

## Source

- Publish the release from a clean working tree.
- Create the `v2.1` tag from the exact source used for the release build.
- Keep all source dependencies pinned to immutable versions or tags.
- Keep the upstream repository publicly accessible.

## Android

- Build with the checked-in Gradle wrapper.
- Use JDK 17.
- Build with Android SDK 36.
- Verify camera capture, import, PDF export, OCR, document search and local storage on physical devices.
- Confirm that the release manifest requests only the camera permission.

## F-Droid

- Run `fdroid build --verbose com.rebelroot.docscanner` from a current fdroiddata checkout.
- Run the license and source scanners.
- Verify that all source dependencies are built from source or obtained through approved F-Droid source libraries.
- Confirm that there are no proprietary SDKs, tracking libraries, advertising SDKs, embedded secrets or hidden network services.
- Check reproducibility before submission.
