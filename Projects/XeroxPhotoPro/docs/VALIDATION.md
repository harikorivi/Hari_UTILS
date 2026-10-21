# Version 0.4.0 validation — 2026-10-21

19 JVM tests passed, including international-name handling, blank/invalid fallback, path-character removal and filename length. Android API 35 emulator: 15 tests passed; the optional private-portrait test skipped because no portrait fixture was supplied. The new workflow test entered the name through Compose UI, checked save suggestions and FileProvider display names for PDF/JPEG/PNG, persisted the name across activity recreation and rotation, and verified new-photo/deletion resets. Existing export, background, camera and layout regressions passed. Build and lint passed with zero errors and 26 warnings. Real-phone and printer checks remain pending.

# Version 0.3.0 validation — 2026-10-21

- JVM layout/crop/PDF/raster/quality suite: 18 tests passed (unchanged core; Gradle cached results).
- API 35 arm64 emulator: 14 tests passed, including the 10 existing workflow/camera tests and four new background tests.
- Background tests cover bilinear mask sampling, offset crop alignment, source pixel preservation, replacement colors in all three export formats, draft persistence/reset, and bundled-model rejection of a blank image.
- Separate private portrait workflow: passed using the portrait extracted from the user-supplied PDF. White and light-blue edits, original restoration, rotation, new-photo reset, all three saves, and the UI color control were exercised. The private portrait and its generated images are not bundled in source or APK.
- Visually inspected the real portrait A4 PNG: background replaced consistently across the single sheet; face and shirt retained. Fine hair segmentation remains a manual review item.
- Build and Android lint passed: zero errors; 25 warnings (dependency/version and convenience recommendations). Network permissions remain removed from the application.
- Physical printer and real-phone performance remain unverified. This is a debug build; segmentation is a beta ML Kit API. Multiple-person portraits, fine hair, glasses, low contrast and complex clothing require real-device quality evaluation.

Optional real-portrait test: place a consented portrait at the app external-files path `portrait-input.jpg`, then run `WorkflowTest#realPortraitBackgroundWorkflow`. This test skips when no private fixture is supplied.

## Previous 0.2.0 evidence

# Validation record — 0.2.0, 21 October 2026

| Check | Result |
|---|---|
| Android debug build | Passed; version 0.2.0 / code 2 |
| JVM tests | 18 passed, 0 failed |
| Android API 35 emulator tests | 10 passed, 0 failed |
| Android lint | 0 errors, 24 warnings |
| APK signature | Valid; same debug certificate as delivered 0.1.0 |
| Merged manifest | No INTERNET or ACCESS_NETWORK_STATE permission |
| Supplied PDF regression | 50×50 mm / 24 landscape copies rejected at capacity 15 |
| Export page count | One page for every inspected/tested PDF |
| Export dimensions/density | Portrait 2480×3508; landscape 3508×2480; JPEG 300 DPI, PNG 299.9994 DPI |
| Actual phone/printer output | Not verified |

## Commands executed

```sh
./gradlew :core:test :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
ANDROID_SERIAL=emulator-5558 ./gradlew :app:connectedDebugAndroidTest :app:lintDebug
```

The existing API 35 arm64 AVD was run read-only with no snapshot save and an emulated rear camera. CameraX capture produced a decodable JPEG. Tests used synthetic images, not the supplied portrait. Selected tests were rerun to retrieve visual evidence after Gradle's test cleanup.

## JVM coverage (18 tests)

- Eight layout tests: maximum default, both orientations, ties, the supplied overflow case, reduced counts, placement/mark bounds across combinations, custom one-fit/no-fit/exact-fit edges, invalid inputs and occupied/remaining geometry.
- Three crop tests: aspect ratio and boundaries across zoom/pan extremes, edge face crop, normal headroom.
- Three PDF tests: independent PDFBox parse of single-page A4 and every placement matrix across orientations/sizes/borders, rendering/shared image resource, a large custom single-photo sheet.
- Two density tests: PNG pHYs CRC/uniqueness/pixel preservation and JPEG JFIF 300 DPI/decode preservation, including repeat tagging.
- Two image-heuristic tests: dark/bright/flat imagery and uneven brightness/background corners.

PDF comparisons allow 0.0001 mm for independent parser numeric precision; emitted physical coordinates have six decimal places in points. This is file-coordinate validation, not a printer measurement.

## Emulator coverage (10 tests)

1. CameraX emulated capture/decode.
2. Launch and maximum selection.
3. Automatic maximum preview and quality checks after import.
4. Overflow rejection plus Use maximum on pending square/landscape settings.
5. PDF/JPEG/PNG saves in both orientations, opening every file, metadata and repeated export cleanup.
6. Shared PNG content URI readability, MIME, temporary read flag and non-exported provider configuration.
7. Activity recreation and actual landscape screen rotation preserve crop, orientation and reduced count.
8. Low-resolution warning and bounded 4000×3000 source decode.
9. Custom single-photo export with minimum margins.
10. Legacy 0.1 preferences migrate to Auto/Maximum; fractional custom dimensions and crop controls reload correctly.

## Independent inspection

Poppler/pdfinfo and pypdf confirm the app-generated portrait PDF is one A4 page with 30 image draws, and landscape is one A4 page with 28. Pillow independently opens the exported JPEG/PNG files at the expected sizes and reports 300 DPI (JPEG) / 299.9994 DPI (PNG). Rendered sheets show uniform upright photos, consistent gaps, centered placement and cutting marks. The final Compose screen was visually checked with synthetic imagery.

`evidence/` contains test XML, the lint report and synthetic app exports. `screenshots/` contains the final maximum-preview screen. These artifacts contain no customer portrait.

## Remaining verification limits

The 24 lint warnings concern pinned older dependencies and Kotlin convenience suggestions. They were not hidden with a blanket suppression.

The emulator validates API paths and state handling, not real portrait quality, printer scaling, save-provider compatibility across vendors, or sustained low-memory leak freedom. Repeated exports completed without crash and temporary encoding files were removed; this is not a long-running memory profile. Full process-kill/relaunch, external printer-app chooser behavior, physical trimming, accessibility, Android 9 and 16 KB page-size devices remain on `DEVICE_CHECKLIST.md`.

A standalone PDF/image cannot prove printed physical dimensions. Print at 100% and measure actual output before shop deployment.
