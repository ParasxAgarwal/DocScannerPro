# Changelog

## 1.1

- Added a native Ko-fi support link in Settings.
- Added the Ko-fi donation page to F-Droid metadata.
- Rebranded to Doc Scanner Pro with new launcher icon and app name.
- Fix: batch import of multiple images now works reliably — downscaled decode, EXIF orientation, page-by-page progress.
- Fix: "Done" no longer blocks on OCR; document opens instantly, text recognized in the background.
- Fix: B&W / Grayscale / Vivid filter chips and preview update immediately (were dead due to StateFlow same-reference mutation).
- Fix: B&W uses OpenCV adaptive threshold for better results on shadowed pages.
- Fix: tapping a page thumbnail in the scanner now opens the crop editor properly.
- Fix: single-image import from Home no longer bounces back to Home.
- Fix: notification permission prompt now actually appears on Android 13+ (was missing manifest declaration).
- New: PDF import — convert PDF pages into editable scan pages.
- New: 6 PDF tools in Tools tab — Images→PDF, Merge PDFs, Split/Extract, PDF→Images, Compress, Password-protect.
- New: import PDF pages from the scanner and Home.
- New: "Saving…" overlay during batch save with page progress.
- New: pdfbox-android dependency for PDF merge/split/protect.

## 1.0

- Replaced the previous OCR integration with Tesseract4Android.
- Added local English and Hindi OCR models.
- Removed proprietary OCR dependencies from the application build.
- Kept barcode recognition on ZXing Core.
- Prepared the F-Droid build for source-built Tesseract4Android.
- Refined release metadata and third-party license documentation.
