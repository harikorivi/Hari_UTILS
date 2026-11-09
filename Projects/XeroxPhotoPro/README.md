# Xerox Photo Pro 0.11.0 — Telangana House Scheme Photo Copy Format

Native offline Android app for shops: capture/import → validate and crop → automatically fill one A4 sheet → save or share PDF, JPEG or PNG. This update modifies the original Kotlin/Compose/CameraX project. All workflows export a single A4 page. Passport and Smart layouts preserve requested dimensions; the House Scheme three-copy template explicitly uses smaller equal frames to fit A4.

## Install or build

The supplied `XeroxPhotoPro-0.11.0-debug.apk` is a debug build (version code 11). It uses the same application ID as 0.1.0 and can update an installation signed with the same development key. Back up any wanted exported files before troubleshooting installation. Do not uninstall merely to update.

Open this directory in Android Studio with JDK 17 and Android SDK platform/build tools 35. Let Studio create `local.properties` for the local SDK. Dependencies are pinned; internet is needed for initial development setup, not the installed app's processing.

```sh
./gradlew :core:test :app:assembleDebug :app:lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
# With an emulator or a dedicated test phone connected:
./gradlew :app:connectedDebugAndroidTest
```

## House scheme photo sheets

Choose **Telangana House Scheme Photo Copy Format** on Home, upload the original photograph, choose 1/2/3 copies and an outline, then save the selected JPG or all three JPGs to a folder. Black is the default outline; choose **Light grey** for subtle cutting lines. The original image is fitted proportionally without cropping, face processing or enhancements. See [dimensions and printing notes](docs/HOUSE_SCHEME.md).

## Passport operator workflow

1. Tap **Take photo** or **Gallery**. Every new photo starts at **35 × 45 mm**, Auto orientation, maximum copies, 5 mm margins and 3 mm cutting gaps.
2. On **Your A4 sheet**, enter the required **Copies** and tap **Apply copies**. The preview shows the actual rows used. Photos start at the top-left safe margin, leaving unused paper together below.
3. JPEG is the default. Tap **Save JPEG**, which stays at the bottom of the screen. Print on A4 at **Actual size / 100%**; disable Fit to page. Measure the first print.
4. Optional: enter the person's name for the filename, open **Photo checks** to inspect guidance, or use **Share JPEG**.
5. For changes, tap **Custom photo / layout**, then **Custom crop**, **Custom background**, **Custom size**, or **Custom sheet layout**. Extra controls stay hidden until opened. Review the sheet after editing.
6. To use PDF or PNG, open **Custom export** and select the format. To fill the sheet again, open **Custom quantity → Use maximum**.
7. Delete the working photo after the job. Saved files remain where you saved them.

Custom settings and reduced counts stay with the current draft across reopening and activity rotation. New capture/import resets the job to the defaults above. Existing saved drafts retain their selected size until replaced or reset explicitly. The default 35 × 45 mm size is a shop default, not an official document-acceptance guarantee.

## Paper placement and touch controls (0.5.0)

For the reported 15-copy case, photos occupy 5 columns × 3 rows at x/y = 6 mm (5 mm safe margin plus 1 mm cutting-mark allowance). The last photo edge is at 147 mm from the top. This removes the large blank area above the photos while preserving the 35 × 45 mm dimensions. The same placement is used in preview, PDF, JPEG and PNG. An incomplete final row starts at the left, and the maximum capacity remains 30 with default settings.

The export remains one A4 page. Reusing a partially used sheet is not automated; printer feed and cutting limitations still apply. The optional two-photo mode prints 15 copies per person on one A4.

Main buttons have 52–56 dp minimum heights, advanced choices use full-width controls, and border/cutting-mark rows can be tapped anywhere. The default preview has no nested pan/zoom gesture area. Save/Share stay visible in the bottom action bar, and Apply copies dismisses the keyboard.

## Optional person’s name

After importing or capturing a photo, enter **Person’s name (optional)** on the A4 preview screen. Save suggests names such as `XeroxPhotoPro-Hari-Korivi-1792601272866.pdf`; JPEG and PNG use their own extensions. Shared files also include the name and a unique suffix. The Android save dialog still lets you edit the suggested filename.

Names are kept with the current private draft and survive reopening or rotation. Importing a new photo or deleting the working photo clears the name. Leaving it blank retains the default `XeroxPhotoPro-…` filename. International letters are preserved; spaces and unsupported filename characters become hyphens, and the filename portion is bounded. The name is used for file organization and is not printed on the photo sheet.

## Repairing missing ears (0.7.0)

If an ear is visible in Original but disappears with a replacement background, the segmentation mask has classified it as background. Softer hair cannot recover a zero-confidence area, and Cleaner edge may remove more uncertain pixels.

1. Open **Custom photo / layout → Custom background**, select a replacement color, then **Repair ears / edges**.
2. Increase **Zoom**, choose **Move**, and drag to position the ear. Choose a small **Brush size**.
3. Choose **Restore** and draw short strokes over only the missing ear. Release to preview the result. This restores the original pixels; it does not generate or reshape the ear.
4. Use **Erase** if old background is restored accidentally. **Undo stroke**, **Reset repair** and **Compare original** help inspect the result.
5. Tap **Apply repair**, review the sheet, and save/share. **Cancel** discards changes made in the editor.

A narrow feather blends the brush boundary. Fully restored interiors override every edge mode, including Cleaner edge. Repairs remain aligned through crop changes and photo rotation, are saved with the draft, and reach JPEG/PDF/PNG exports. Completed strokes in the open editor survive activity recreation. A new photo or deletion clears them. Up to 64 strokes (256 points per stroke) are supported. Undo or reset if you reach the limit.

If the ear is already outside the cropped frame, cancel and zoom out in Custom crop. If the original photo itself is missing the ear, retake the photo. The new tool is a manual recovery path; the automatic model can still misclassify ears. Do not brush a broad patch around the face, which would restore unwanted background too.

## Background replacement

Open **Custom photo / layout → Custom background**. Choose a labelled color swatch: Original, White, Light blue, Blue, Light gray or Cream. Selected state is visible as well as the actual color. **Custom color** provides RGB sliders and a live swatch; tap Apply custom color to commit. **Compare with original** switches the preview only; it does not change the export. Tap the portrait to enlarge it and use Detail zoom to inspect edges. Review the A4 sheet, then save/share any format; all use the same edited portrait.

**Refine cutout** provides Natural (default), Cleaner edge and Softer hair. Source-color-guided refinement aligns uncertain mask boundaries with local RGB edges at up to 1024 pixels on the longest side. Natural removes only very faint mask residue; Cleaner edge suppresses more low-confidence halo pixels; Softer hair keeps the full confidence range. Compare the options for the actual portrait. This improves alignment on matching image edges but cannot restore hair or body parts the model confidently missed, or guarantee a studio-quality cutout.

Changing color reuses the refined source mask and original-crop quality checks. Crop changes invalidate the prepared portrait; rotation/import invalidate the source mask. The chosen edge mode stays with the draft and resets for a new photo.

The bundled ML Kit selfie model runs offline. Its beta API may misclassify fine hair, glasses or clothing; inspect edges and use Original when editing is disallowed or the result is poor. No face generation, retouching or facial reshaping is performed. Source-photo quality checks remain visible, with an additional background-edit review reminder. A mask without confident foreground and background is rejected without committing the requested color.

The selected color survives reopening the draft and rotation. Changing crops reuses a mask aligned to the full source; a new import or deletion resets to Original. Background replacement never overwrites the working source. Rotating the source remains the pre-existing separate operation.

## Layout behavior

With 5 mm margins, 3 mm gaps, external marks:

| Photo size | Portrait maximum | Landscape maximum | Auto |
|---|---:|---:|---|
| 35 × 45 mm | 30 | 28 | Portrait, 30 |
| 50 × 50 mm | 15 | 15 | Portrait, 15 |
| 25 × 35 mm | 49 | 50 | Landscape, 50 |

Counts change with dimensions, margins, gaps and guides. The algorithm uses a uniform upright-photo grid with fixed cutting gaps, compares both orientations, and packs the selected grid from the top-left safe margin. It does not mix individually rotated photos. Ties favor portrait for a predictable shop paper-feed workflow; this is a deterministic preference, not a claim that every printer prefers portrait.

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

The app remains a Phase 1 debug build. Direct printer integration remains deferred. Smart A4 supports mixed-image layouts within its documented limits. Real-phone portrait quality, physical printer dimensions, low-memory behavior across vendors, Android 9 and 16 KB page-size devices, accessibility and Play release requirements still need deployment testing. No time-to-completion or government acceptance guarantee is made.

## Two different photos on one A4 (0.8.0)

Open **Custom sheet mode → Two photos · 15 + 15**. Select **Top photo** and import/capture the first person. Select **Bottom photo** and import/capture the second person. Each photo has its own crop, background, ear/edge repair and optional name. Review the A4 sheet, then Save JPEG (default) or choose PDF/PNG in Custom export.

The sheet has five columns and six rows: first photo in the top three rows, second photo in the bottom three rows. All 30 photos are 35 × 45 mm. Printer margins and cutting gaps remain; full sheet means the maximum 30-photo layout, not borderless printing. Both photos must be prepared before saving. This mode fixes the size/layout; switch to Single photo for custom sizes or quantities.

Both drafts are stored privately on-device. Switching to Single photo retains the second draft for later use. Delete working photo(s) clears both drafts, names, repairs and temporary shares; previously saved exports remain. Both optional names appear in the suggested filename, subject to the normal filename length limit.

## Image quality update (0.9.0)

New imports use eye/chin landmark framing. For an existing draft, open **Custom photo / layout → Custom crop → Auto crop**, then Review A4 sheet. Existing manual crops are preserved on upgrade. Apply this separately to each photo. Custom framing targets provide eye-line and eye-to-chin percentages; use the same settings for both people. These are operator guidance, not official document presets, and hair/crown height still needs inspection.

Background replacement now applies conservative local edge-color correction. It does not erode the mask or synthesize missing ears. Original background bypasses composition, and Restore/Erase remains available. New working drafts use lossless PNG; old JPEG drafts remain readable.

See [investigation and limitations](docs/IMAGE_QUALITY_V09.md) for causes, model evaluation and comparison scope. Supplied customer evidence is exported A4 imagery, not original camera files, so original-photo ear/hair accuracy and ordinary-phone performance remain field checks.

## Smart A4 Photo Layout Maker (0.10.0)

The new home screen offers Passport Photo Maker and Smart A4 Photo Layout Maker. Smart supports multiple gallery images or camera capture, independent print dimensions/copies, aspect-preserving Fit Inside or Fill and Crop, rotation, mixed-size packing, cutting styles and one-page PDF/JPEG/PNG exports. Single-image jobs default to Maximum Fit; multi-image jobs default to Exact Copies + Best Fit. Applied Smart jobs persist separately from passport drafts.

See [Smart workflow, algorithm and limits](docs/SMART_A4.md). Packing is heuristic; overflow never creates another page or silently changes copies/sizes. Original imports are retained byte-for-byte, while rendering uses bounded decoding for memory safety. Physical phone, gallery-provider and printer checks remain necessary.
