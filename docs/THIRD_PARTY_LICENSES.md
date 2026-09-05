# Third-party licenses

Doc Scanner is released under the MIT License. Third-party components retain their own licenses.

| Component | Version | License | Source |
| --- | --- | --- | --- |
| AndroidX | Project managed | Apache-2.0 | https://developer.android.com/jetpack |
| Jetpack Compose | BOM 2024.09.00 | Apache-2.0 | https://developer.android.com/jetpack/compose |
| CameraX | 1.5.0 | Apache-2.0 | https://developer.android.com/media/camera/camerax |
| Coil | 2.7.0 | Apache-2.0 | https://github.com/coil-kt/coil |
| Kotlin Coroutines | 1.10.2 | Apache-2.0 | https://github.com/Kotlin/kotlinx.coroutines |
| OpenCV | 4.14.0 | Apache-2.0 | https://opencv.org/ |
| Room | 2.7.0 | Apache-2.0 | https://developer.android.com/training/data-storage/room |
| DataStore | 1.1.7 | Apache-2.0 | https://developer.android.com/topic/libraries/architecture/datastore |
| ZXing Core | 3.5.4 | Apache-2.0 | https://github.com/zxing/zxing |
| Tesseract4Android | 4.9.0 | Apache-2.0 | https://github.com/adaptech-cz/Tesseract4Android |
| Tesseract trained data | eng, hin | Apache-2.0 | https://github.com/tesseract-ocr/tessdata |

The Tesseract4Android library is built from source for the official F-Droid build through the `tesseract4android` F-Droid source library. No prebuilt Tesseract binary is required in the application source tree.

The bundled trained-data files are distributed locally so OCR does not require a network connection.

The icon is authored for Doc Scanner and stored as Android vector resources.
