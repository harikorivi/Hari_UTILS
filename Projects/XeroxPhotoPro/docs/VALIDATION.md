# Version 0.11.0 validation — 2026-11-09

- 39 JVM tests passed, including three new house-layout tests covering exact 6 × 4-inch frames, centering, largest equal three-copy frames, spacing, bounds and supported choices.
- Android API 35 full suite: 32 tests, zero failures/errors, two optional private-reference tests skipped because private fixtures were not supplied. The 30 passing tests include both new house-flow tests and existing passport, background, camera, image-quality and Smart A4 regressions.
- House tests exercise copy/outline controls, byte-for-byte source preservation, square-image Fit Inside without cropping, single JPEG export, document-provider batch export of three unique files, persistent settings, deletion, output quadrant placement, 2480 × 3508 pixel dimensions and JFIF 300 DPI metadata.
- The batch-export test fixture was corrected to let a test-only picker activity grant access to the synthetic destination, matching Android's document permission model. This provider exists only in the instrumentation APK, never in the delivered app.
- Preview screenshot and all three synthetic JPEG exports were visually inspected. No customer photo is included in the samples or application.
- Build and lint completed: zero lint errors, 37 warnings (including existing warnings and a SharedPreferences KTX style suggestion). Version code 11 / version name 0.11.0.
- Physical paper measurement, vendor gallery/document providers, real phone memory limits and printer scaling remain deployment checks. The three-copy frames are intentionally smaller than 6 × 4 inches; non-6:4 sources have white space inside the finished frames. See HOUSE_SCHEME.md.

Historical release validation follows.

# Version 0.10.0 validation — 2026-10-25

- 36 JVM tests passed, including eight new Smart layout/PDF tests. Coverage includes single-image maximum and exact counts, the supplied two/three/mixed-size examples, five images, complete ratio batches, automatic/manual orientation, consented rotation, alternative orientation, overflow, all cutting styles and randomized non-overlap/bounds/count checks. Single-image Maximum Fit also ignores a prior exact quantity. PDFBox verifies exactly one physically accurate A4 page and each mixed image's color/placement; existing passport PDF tests still pass after serializer reuse.
- Full Android API 35 suite: 30 tests, zero failures/errors, two optional private-reference tests skipped. Both optional tests were then supplied their local fixtures and passed in a three-test private/visual run (the third was a repeated Smart mixed-output check). Thus all 30 distinct Android tests have passing evidence. Customer fixtures are excluded from APK/source deliverables.
- Final Smart group passed 6/6 after final mode-switching changes. Covers real Compose preset/count controls and maximum↔exact behavior; three-image preview/export and passport-state isolation; overflow blocking export with explicit alternative orientation; matching raster/PDF quarter-turns; transparency; JPEG EXIF orientation; unchanged original file bytes; Fit Inside versus Fill and Crop; saved-job reload; share URI density; large WebP import; replace/remove; and capture through the Smart camera with face guidance disabled.
- JPEG and PNG export dimensions/density and single-page PDF rendering passed. Preview and exported synthetic sample were visually inspected. Final preview shows a visible A4 boundary and per-image count/size summary. Synthetic examples are delivered separately and contain no customer images.
- An initial UI test exposed a nested horizontal preset row whose button could remain vertically offscreen. It was replaced by large two-column buttons; assertions now require the preset to be displayed and its exact dimensions to apply. A final single-image mode-switch test guards against treating a previous exact copy count as a ratio.
- Build and lint passed with zero errors and 36 warnings. Source code, APK and sample exports correspond to versionCode 10 / versionName 0.10.0.
- Digital validation is not physical printing validation. Real phone/gallery-provider behavior, low-memory devices, printer scaling and measured cut sizes remain deployment checks. Packing is bounded and heuristic, not mathematically globally optimal. Smart job limits and source-decoding bounds are documented in SMART_A4.md.

Historical release validation follows.

# Version 0.9.0 validation — 2026-10-25

- 28 JVM tests passed. New tests cover landmark-normalized eye/chin ratios across camera distances and portrait/landscape source geometry, configurable targets, missing-landmark fallback, insufficient-source detection and conservative color unmixing.
- Final Android API 35 emulator runs passed all 24 tests in two explicit groups: WorkflowTest 14/14 and background/camera/image-quality/reference tests 10/10. Both optional local reference fixtures were supplied for these final runs. Fixtures are omitted from the deliverable APK/source; normal source-only runs skip those two optional tests.
- Workflow tests cover new target fields, Apply/reset and guides, existing manual ear repair including rotation/recreation, original-background restoration, independent two-photo editing, 15 + 15 placement, JPEG default, save/share filenames, real save/share density metadata and PDF/JPEG/PNG output.
- Controlled matte absolute red-channel error decreased from 143521 to 52233 (about 64%); opaque interior pixels remained identical. This establishes bounded color correction, not original-photo ear/fine-hair recovery. Lossless draft round-trip and target persistence at maximum valid values passed.
- Inspected export-derived customer reference comparisons and the synthetic edge figure. Customer reference edge differences are subtle (1313 pixels changed). New eye/chin framing increased headroom in this example. Actual camera originals were not supplied; the top person's halo cannot be certified fixed.
- Build and lint passed with zero errors and 32 warnings. An initial geometry fixture exceeded the intentional 5× crop limit; the unconstrained fixture was corrected. The private repair test now waits for preview completion before Apply. One later emulator run restarted without a final result; it is not counted as passed. The final two groups above both returned explicit OK summaries.
- Emulator timing is diagnostic only. No ordinary handset performance, physical print dimensions or full original-photo acceptance test was available. IMAGE_QUALITY_V09.md documents model limits, source-resolution caps, metadata uncertainty and remaining checks.

Historical release validation follows.

# Version 0.8.0 validation — 2026-10-25

- 23 JVM tests passed. New independent PDFBox rendering test confirms one exact A4 page, two image resources, 15 red top placements and 15 blue bottom placements; rejects a two-photo layout with only 15 placements.
- API 35 emulator full suite: 19 passed, 1 optional private-portrait test skipped. The new test selects two-photo mode, verifies save is absent before both photos exist, switches slots, checks independent names/drafts/crop, confirms JPEG default and both names in the filename, and checks all 30 placement centers in JPEG, PNG and PDF output. Activity recreation retains the draft.
- Targeted two-photo test passed again after adding a full-preview scroll and screenshot check. Inspected docs/screenshots/v08/two-photo-sheet.png; it uses synthetic color fixtures only.
- Build and lint passed: zero errors, 31 warnings. The first new Android test expected ceiling-based A4 width instead of the renderer's correct rounded 2480 pixels; the test was corrected, and the full suite rerun successfully.
- No physical printer or real handset was tested for this release. Print one sheet at Actual size / 100% and measure both rows before production use. The new mode preserves safe margins/gaps; it is not borderless. Existing ear-repair behavior is retained; this release does not change segmentation accuracy.

Historical release validation follows.

# Version 0.7.0 validation — 2026-10-25

- 22 JVM tests passed. New mask-repair tests cover recovery from zero confidence, untouched remote background, feathered boundaries, continuous segments, ordered erase, source aspect ratio, clockwise rotation and invalid coordinates.
- API 35 emulator suite: 18 passed, 1 optional private-portrait fixture test skipped. New Android pixel tests show restored interiors match original RGB exactly in Natural/Cleaner/Softer modes while untouched background keeps the replacement color. Repair serialization, rotation-save preservation, new-photo reset and delete reset passed.
- Private portrait workflow passed: draw strokes, Cancel without committing, Undo, Apply, persisted repairs, activity recreation preserving unfinished strokes, image rotation, new-photo reset, original restoration and PDF/JPEG/PNG saves.
- Added an assertion that Apply is displayed. It exposed a full-screen dialog inset issue; final editor is bounded by the activity's visible frame, with clipped canvas and fixed portrait actions. The final test passed in portrait and landscape; landscape canvas remained at least 100 dp high and Apply was reachable through the controls panel. Screenshots were inspected locally; private portrait images are not included in deliverables.
- Final build and lint passed with zero errors and 30 warnings. No real handset or physical printer was available.
- This release adds manual original-pixel repair. It does not claim automatic ear detection is fixed. Restoring too broad an area also restores the old background; use Erase/Undo and compare before applying. No generated facial detail is introduced.

# Version 0.6.0 validation — 2026-10-25

- JPEG is the initial Save/Share format; UI regression asserts the visible Save JPEG action. PDF and PNG remain available under Custom export.
- Existing 20 JVM tests remain passing (unchanged core). API 35 emulator suite: 17 passed, 1 optional private-portrait test skipped. An initial run started before emulator credential storage unlocked and was rerun after confirmed boot.
- New controlled RGB-edge test checks refinement moves uncertain confidence toward the actual color boundary, preserves confident foreground/background, stays finite/in range, and Cleaner edge never increases alpha over Natural. Draft tests cover edge-mode persistence, rotation preservation and new-photo reset.
- Private portrait workflow passed with the supplied portrait: white/light-blue selection, before/after, enlarged detail dialog, Natural/Cleaner selection, Original restoration, rotation, import reset and PDF/JPEG/PNG saves. Private images are not distributed in the source or app.
- Visually compared the plain-mask baseline and refined portrait. Strong RGB correction picked up unrelated background structure; final refinement conservatively blends 40% and caps confidence adjustment to ±0.12. Fine hair and similar-colored background remnants still require inspection; no claim of universal accuracy improvement or perfect matting is made.
- Final five background tests and JPEG/15-copy workflow test passed after the final cleanup curve change (6 tests). Final build and lint passed: zero errors, 28 warnings. No handset/performance or physical-printer verification was possible.
- Color changes reuse the mask and source checks. The app remains offline, and 35 × 45 mm defaults plus top-left sheet packing are retained. Multi-customer sheets remain deferred.

# Version 0.5.0 validation — 2026-10-25

- 20 JVM tests passed. Added 1/4/6/15/16/29/30-copy top-left placement checks, actual used rows/columns, guide-safe margins and fixed 35 × 45 mm dimensions. Existing PDFBox physical-coordinate and layout combinations passed.
- API 35 emulator suite: 16 passed, 1 optional private-portrait fixture test skipped. New UI regression enters 15 copies through the visible control, verifies 5 columns × 3 rows, checks the sticky Save button, opens Custom size, applies 50 × 50 mm, toggles a switch by tapping its label, and confirms a new import returns to 35 × 45 mm.
- The same regression renders PDF/JPEG/PNG: verifies image pixels near the top-left safe area and blank lower-page pixels. PDF remains one page.
- Repeated the new interaction test at normal display and 960 × 1920 with 130% text. Final keyboard changes and the initial screen passed two targeted tests at the latter size. Inspected screenshots and the synthetic 15-copy PNG.
- Final build/lint passed: zero errors, 26 warnings. Existing dependency/tooling warnings remain.
- No physical printer or the user's particular handset was available. Real-phone tap behavior and a measured print remain the next field checks. Partial-sheet feed/reuse and multi-customer printing are not implemented.

Screenshots in `docs/screenshots/v05/` contain only synthetic test imagery. Prior validation below describes historical releases.

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
