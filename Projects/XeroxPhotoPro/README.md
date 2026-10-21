# Xerox Photo Pro 0.4.0 — single-sheet Phase 1

Native offline Android app for shops: capture/import → validate and crop → automatically fill one A4 sheet → save or share PDF, JPEG or PNG. This update modifies the original Kotlin/Compose/CameraX project. It never creates a second page or shrinks photos to satisfy a copy count.

## Install or build

The supplied `XeroxPhotoPro-0.4.0-debug.apk` is a debug build (version code 4). It uses the same application ID as 0.1.0 and can update an installation signed with the same development key. Back up any wanted exported files before troubleshooting installation. Do not uninstall merely to update.

Open this directory in Android Studio with JDK 17 and Android SDK platform/build tools 35. Let Studio create `local.properties` for the local SDK. Dependencies are pinned; internet is needed for initial development setup, not the installed app's processing.

```sh
./gradlew :core:test :app:assembleDebug :app:lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
# With an emulator or a dedicated test phone connected:
./gradlew :app:connectedDebugAndroidTest
```

## Operator workflow

1. Select a studio/custom photo size if needed, then **Take photo** or **Gallery**.
2. The app auto-crops, runs guidance checks, selects maximum capacity and opens the complete A4 preview. **Auto · maximum** chooses the best orientation; explicit Portrait/Landscape remains available.
3. Read **Review required** results. Use **Adjust photo / layout** to inspect crop, zoom/pan, rotate, change size, margins, gaps, border or guides. Tap **Review A4 sheet** to refresh the checks and preview.
4. Keep **Maximum** or reduce copies with −/+, or type a count and Apply. Overflow is rejected with the current limit and a **Use maximum** action. That action applies the pending settings and their maximum; cancel retains the previous valid settings.
5. Select **PDF**, **JPEG** or **PNG**, then **Save** (Android document picker) or **Share** (Android share chooser). Sharing creates a new private temporary export with a read-only content URI.
6. Print on A4 at **Actual size / 100%**. Disable Fit to page and Shrink to fit. PDF is preferred for exact physical size. Image metadata is advisory to printer apps. Measure the first print and adjust printer settings/margins as necessary.
7. Delete the working photo after the job. Saved files and files already copied by recipient apps must be managed separately.

Every new capture/import selects maximum copies. An explicitly selected orientation is remembered. A reduced count belongs to the current draft and survives activity rotation/recreation and draft reopening. On first upgrade from 0.1, old fixed copy/orientation settings migrate to Auto/Maximum, retaining size, margins and gaps.

## Optional person’s name

After importing or capturing a photo, enter **Person’s name (optional)** on the setup or A4 preview screen. Save suggests names such as `XeroxPhotoPro-Hari-Korivi-1792601272866.pdf`; JPEG and PNG use their own extensions. Shared files also include the name and a unique suffix. The Android save dialog still lets you edit the suggested filename.

Names are kept with the current private draft and survive reopening or rotation. Importing a new photo or deleting the working photo clears the name. Leaving it blank retains the default `XeroxPhotoPro-…` filename. International letters are preserved; spaces and unsupported filename characters become hyphens, and the filename portion is bounded. The name is used for file organization and is not printed on the photo sheet.

## Background replacement

In **Adjust photo / layout**, scroll below crop controls to **Background**. Choose Original, White, Light blue, or enter a six-digit RGB hex color and Apply. The original crop remains above the edited portrait for comparison. Review the A4 sheet, then save/share any format; all use the same edited portrait.

The bundled ML Kit selfie model runs offline. Its beta API may misclassify fine hair, glasses or clothing; inspect edges and use Original when editing is disallowed or the result is poor. No face generation, retouching or facial reshaping is performed. Source-photo quality checks remain visible, with an additional background-edit review reminder. A mask without confident foreground and background is rejected without committing the requested color.

The selected color survives reopening the draft and rotation. Changing crops reuses a mask aligned to the full source; a new import or deletion resets to Original. Background replacement never overwrites the working source. Rotating the source remains the pre-existing separate operation.

## Layout behavior

With 5 mm margins, 3 mm gaps, external marks:

| Photo size | Portrait maximum | Landscape maximum | Auto |
|---|---:|---:|---|
| 35 × 45 mm | 30 | 28 | Portrait, 30 |
| 50 × 50 mm | 15 | 15 | Portrait, 15 |
| 25 × 35 mm | 49 | 50 | Landscape, 50 |

Counts change with dimensions, margins, gaps and guides. The algorithm uses a uniform upright-photo grid with fixed cutting gaps, compares both orientations, and centers the selected grid footprint. It does not mix individually rotated photos. Ties favor portrait for a predictable shop paper-feed workflow; this is a deterministic preference, not a claim that every printer prefers portrait.

Custom photo width/height accept 10–287 mm; the full geometry must actually fit. External marks reserve 1 mm within the safe area. Optional thin gray border ink is entirely inside the photo; cut at the outer edge to retain the requested size. White separation is provided by the configured gap.

## Exports and accuracy

- PDF: exactly one page, fractional-point A4 MediaBox, original physical photo dimensions, shared embedded RGB JPEG. No document-authority approval claim.
- JPEG: one 2480 × 3508 pixel portrait sheet, or 3508 × 2480 landscape, quality 97, 300 DPI JFIF metadata.
- PNG: the same sheet dimensions, lossless encoding, 11811 pixels/meter pHYs metadata (approximately 300 DPI).
- Raster edges have normal integer-pixel quantization: at 300 DPI one pixel is approximately 0.0847 mm. The nominal raster paper extent differs from exact A4 by less than half a pixel. PDF retains fractional physical coordinates. Printer software can still rescale either format.
- Low source resolution is reported. The prepared portrait is not needlessly upsampled to 300 DPI; the raster sheet is always generated at the 300 DPI target. Upscaling cannot create detail.

## Validation and privacy

Bundled ML Kit checks face count, head pose, eye landmarks/open probabilities and crop bounds. Additional local heuristics check exposure, sharpness, left/right brightness and background-corner variation. PASS means a heuristic passed, not that a photo meets official standards. Background/lighting/blur thresholds can produce false positives or miss problems; inspect the face, hair, chin, shoulders and full background.

Image decode is bounded to a 2400-pixel longest edge. All core processing is local; the merged application has no INTERNET or ACCESS_NETWORK_STATE permission. A destination selected through Android's save/share UI may itself upload data, under that app's control.

One working photo, crop adjustments and shop settings are private. Explicit deletion also removes temporary shared sheets. Unique share files expire during cleanup after 24 hours; no background scheduler is promised. Exports saved elsewhere are not deleted. Backup is disabled; Android 12+ extraction exclusions are configured. Camera permission is requested only when used; there is no broad storage permission.

## Evidence and remaining work

See [feedback and change summary](docs/FEEDBACK_AND_CHANGES.md), [validation record](docs/VALIDATION.md), [phone/printer checklist](docs/DEVICE_CHECKLIST.md), and [architecture](docs/ARCHITECTURE.md).

The app remains a Phase 1 debug build. Direct printer integration and multi-customer sheets remain deferred. Real-phone portrait quality, physical printer dimensions, low-memory behavior across vendors, Android 9 and 16 KB page-size devices, accessibility and Play release requirements still need deployment testing. No time-to-completion or government acceptance guarantee is made.
