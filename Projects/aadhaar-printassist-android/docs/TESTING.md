# Validation test record — 20 October 2026

Built on macOS with JDK 17, Gradle 8.11.1, AGP 8.9.2 and Android SDK 35. Runtime tests used an isolated Android 15/API 35 arm64 emulator with 1536 MB configured RAM. This is not a physical low-end-device benchmark or Android 9 certification.

## Automated results

- **26 JVM tests passed:** four Aadhaar password tests and 22 verification/filename tests. Coverage includes valid CMS, CAdES ESS binding, signed-byte tampering, exact Contents-gap binding, trailing bytes, unknown roots, missing intermediates, missing/stale/wrongly signed CRLs, revoked certificates, Aadhaar signer allowlisting, an expired signer with only a claimed date, valid trusted historical timestamps, invalid timestamps, missing historical CRLs, historical revocation, good/revoked/stale/unauthorized OCSP responses, and sanitized customer filenames.
- **14 Android instrumentation tests passed:** AES-128/AES-256 opening, wrong passwords, page rendering/navigation, input limits and resource closure; Compose home/mode/disabled-open checks; strict PDF signature detection and byte-identical original saving; tampering; rejection of a synthetic signer by the real bundled trust store; PNG/JPG and print-PDF output; cancellation; unsigned PDFs; and the native adapter's page selection, customer job name, completion and temporary test-file cleanup.
- Debug APK, instrumentation APK and unsigned release AAB built successfully.
- Android lint: **0 errors, 10 warnings**. Nine flag newer Android/Kotlin dependency versions; one flags a trust-all helper within Bouncy Castle's unused PKIX TLS classes. No warning is suppressed. The application has no INTERNET permission and does not call those TLS classes. This is not a complete vulnerability assessment.
- Bouncy Castle provider/PKIX/util resolved to **1.86**. All tests passed after the upgrade from the PDF library's transitive 1.72 dependency.
- APK signature verified with `apksigner`: development/debug signing identity, APK Signature Scheme v2. Release bundle is unsigned; publishing credentials are not included.

## Appearance comparison

A synthetic PDF with an embedded signature widget near the right page edge was exported at 200 DPI on Android and independently rendered with PDFium 5.3.0 with form rendering enabled. The widget border/artwork and text positions remained within the original rectangle in both engines. Full-page sizes differed by one pixel due to rounding (1652×2338 versus 1653×2339). The aligned widget mean absolute RGB difference was 9.74/255; font substitution and antialiasing differ. See `synthetic-appearance-comparison.png`.

This verifies that fixture's layout, not universal font fidelity or Adobe-generated appearance equivalence. No Adobe or reference-app result was used as a cryptographic oracle. No verification graphic was added to the PDF. The empty home screenshot is captured from the real emulator; test code temporarily disables screenshot protection only for that empty screen.

## Reproduce

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:bundleRelease
```

Use `gradlew.bat` on Windows. Select a dedicated test device with `ANDROID_SERIAL` when several devices are connected. Synthetic public test certificates, CRLs, OCSP responses and PDFs are included under `app/src/androidTest/assets/fixtures`; JVM tests share those resources. They are never in the production APK. No private key or customer document is included. `tools/generate_fixtures.py` documents optional fixture regeneration with Python/pyHanko; normal Android builds do not need Python.

## Remaining acceptance and production checks

Actual SAF cloud/provider workflows, a physical printer, Android 9 hardware, physical 2 GB phones, large fonts/accessibility, process death, malformed-file fuzzing, parser isolation/time limits, complete dependency security review, more Aadhaar variants and independent PDF signature-wrapping review remain outstanding. LTA timestamp chains and semantic acceptance of later signed revisions are outside the implemented profile and stay indeterminate. Passing these tests does not certify arbitrary documents or regulatory compliance.

## Version 0.2.1 regression checks

Added tests for signature-widget omission in preview, PNG, JPG and print PDF after invalid/incomplete verification; preservation of verified artwork; unchanged original exports; policy reset between customers; and password-protected signed PDFs. The loaded-screen UI test opens a synthetic encrypted signed PDF, scrolls controls, swipes the preview, asserts unchanged preview bounds and clears the session. The earlier appearance comparison above records the original-widget rendering behavior from 0.2.0; 0.2.1 preserves that widget only after verification passes.

0.2.1 result: 18 Android instrumentation tests passed; 26 JVM tests passed. Debug APK built successfully. Tests use synthetic documents only, including an encrypted signed PDF.
