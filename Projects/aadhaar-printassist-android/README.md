# Aadhaar PrintAssist Android

Working Kotlin / Jetpack Compose application for **Unlock → Verify → Preview → Print / Export**. Android 9 (API 28) minimum; tested on an Android 15/API 35 emulator. Version 0.2.1 is a validation preview, not a production-certified or Google Play release.

## Implemented

- Aadhaar name + birth-year password generation, manual password fallback, and general PDF mode.
- Local password-protected PDF opening, including AES-128 and AES-256. No password guessing.
- CMS signature verification, exact Contents/ByteRange binding, SHA-2 policy and CAdES SigningCertificateV2 binding.
- Explicit PKIX certificate trust; authenticated CRL and OCSP checks; RFC 3161 signature timestamps and supported historical validation.
- Signature widget diagnostics (Rect, AP/N, BBox and Matrix). Original widget artwork is retained only when verification passes; otherwise identifiable signature widgets are omitted from preview and print/image copies. No inserted verification artwork.
- Green status in the application only when the supported validation checks pass. Unknown evidence never means verified.
- Fixed, fitted preview outside the scrolling controls, page navigation and detailed exportable verification reports.
- Android Print Framework, print PDF export, current-page PNG/JPG export, and exact-byte original PDF saving.
- Filenames reuse the entered customer name: `Devarakonda_Keerthi.pdf`, `Devarakonda_Keerthi_page_1.jpg`, etc.
- App launcher icon, no account, no Internet or broad-storage permission, no document uploads or analytics.
- Explicit export/print consent, clear-session action, disabled backups and screenshot protection.

## Install and use

Install the supplied debug APK on a test Android device, then tap the **Aadhaar PrintAssist** launcher icon. Android may ask you to allow installation from your chosen file manager for this development APK.

1. Choose Aadhaar or General PDF and select a PDF through the Android file picker.
2. Enter the customer's name and birth year, or the PDF password. Confirm permission to open it.
3. Tap **Unlock & verify original** and review the result. Unlocking alone is not verification.
4. Inspect the fixed print preview, then print or save a PDF, PNG, JPG, original, or verification report.
5. Confirm permission in the export dialog. Use **Clear customer session** when finished.

The name is captured before the input fields clear and is reused for saved filenames. Selecting another PDF requires new customer details. Leaving the app clears the session, except while using the system save/print/evidence dialogs. Rotation may retain the session.

**Customers do not choose a trust folder.** Bundled CCA roots and public evidence load automatically. Administrator settings can import updated public intermediate certificates, CRLs or OCSP responses; they cannot add trusted roots. See [administrator instructions](docs/ADMIN.md).

## Verification boundaries

Only the original bytes are checked. Print PDFs and images follow the preview: signature widgets are omitted when verification fails or is incomplete. These copies do not retain the original cryptographic signature. **Save original PDF** keeps the original bytes, including encryption and signatures.

Supported signatures are `adbe.pkcs7.detached` and the implemented CMS/ESS profile of `ETSI.CAdES.detached`. PKIX/CRL/OCSP policy supports SHA-2 RSA and ECDSA certificate/evidence signatures. Unsupported algorithms and incomplete evidence remain indeterminate. This is not a claim of complete PAdES or ETSI conformance.

Historical verification requires a valid RFC 3161 signature timestamp, a currently trusted/unrevoked timestamp authority, and certificate/revocation evidence applicable at the timestamp time. A PDF signing date alone is insufficient. Expired timestamp authorities, LTA document-timestamp chains, delta/indirect/partitioned CRLs and acceptable-change analysis of later PDF revisions are not implemented. Later bytes keep the result indeterminate even if the earlier signature is intact. Compatibility parsing also disables overall green.

The configured trust store covers the bundled CCA chains, not every certificate authority in the world. Aadhaar mode additionally requires an approved signing-certificate fingerprint. No Aadhaar identity, QR-code or UIDAI authentication is performed. A missing historical timestamp or missing archived evidence cannot be reconstructed by this app.

## Limits and build

20 MB input; 100 pages; lazy previews capped at 2 million pixels; image exports capped at 8 million pixels; up to 10 pages per print operation / print-PDF export, up to 200 DPI. Image export offers 150/200/300 DPI within the pixel limit. Original-file saving has no 10-page restriction.

Open this directory in Android Studio with JDK 17 and Android SDK 35. Android Studio creates `local.properties`. Gradle downloads build dependencies; the installed app itself has no network access.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:bundleRelease
```

On Windows use `gradlew.bat` with the same arguments. This builds an Android app on Windows; it is not a Windows executable. The existing desktop application is a separate project.

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. Unsigned release bundle: `app/build/outputs/bundle/release/app-release.aab`. A production signing identity is deliberately not included.

See [test evidence](docs/TESTING.md), [validation policy](docs/VALIDATION.md), [privacy](docs/PRIVACY.md), [release preparation](docs/RELEASE.md) and the earlier [technical feasibility study](docs/PHASE1.md). Physical budget-device/printer testing, parser isolation/fuzzing and an independent security review remain production release gates.

### 0.2.1 preview/export update

The preview stays pinned above the scrolling controls and no longer translates or zooms on touch. Signature omission is annotation-based: it suppresses identifiable signature widgets without painting over underlying page content. It does not erase a signature already flattened into a page image or guess a crop rectangle. Such artwork cannot reliably be separated from the document automatically. Verification warnings remain visible in the app and report. The exact-original save is deliberately unchanged.
