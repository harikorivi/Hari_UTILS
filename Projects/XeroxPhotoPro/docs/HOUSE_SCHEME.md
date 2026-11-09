# Telangana House Scheme Photo Copy Format — 0.11.0

This is an additional workflow in the existing application. Passport Photo Maker and Smart A4 remain independently accessible from Home. This is a shop layout template, not a claim of official scheme certification.

## Operation

Upload one original photo, select 1, 2 or 3 copies and choose Black (default) or Light grey. The preview updates automatically. Save selected JPG uses Android's file picker. Save all 3 JPGs to folder creates three separate, uniquely named documents in the chosen folder. No permission to access all photos or shared storage is required. A failed batch reports the number saved; retry creates a new set rather than overwriting earlier exports.

The source is copied byte-for-byte into private working storage. There is no AI generation, face detection, background replacement, color enhancement, stretching or cropping. EXIF orientation is respected. Proportional raster resampling and JPEG encoding are necessary for printing; the exported pixels are not byte-identical to the source. Non-6:4 images are fitted entirely inside the specified frame, leaving white space. Transparent image areas are printed white.

## Geometry

All measurements below are millimeters from the top-left of portrait A4 (210 × 297). The border identifies the finished frame dimensions, not the content bounds of a letterboxed photograph.

| Copies | Each frame width × height | X | Y positions | Gap |
|---|---|---|---|---|
| 1 | 152.4 × 101.6 | 28.8 | 97.7 | — |
| 2 | 152.4 × 101.6 | 28.8 | 45.4, 150.0 | 3 |
| 3 | 140.5 × 93.6667 | 34.75 | 5, 101.6667, 198.3333 | 3 |

Three true 6 × 4-inch photographs would require 304.8 mm before gaps, exceeding A4 height. Three-copy mode explicitly uses smaller equal 6:4 frames: the largest possible for this vertical arrangement with 5 mm safe margins and 3 mm gaps. One and two copies remain exactly 6 × 4 inches. The thin 0.2-point outline stays within the frame. No captions, logos or watermarks are printed.

JPEG output is 2480 × 3508 pixels with JFIF 300 DPI metadata, white background and high-quality encoding. Preview and export use the same geometry and renderer. Raster dimensions have unavoidable subpixel rounding (below one 300-DPI pixel, about 0.085 mm). Print A4 at Actual Size / 100%, disabling Fit to page. Measure the first cut sheet; printer drivers and paper feed may introduce scaling or alignment error.

## Implementation and limits

`core/HouseLayout.kt` contains deterministic layout geometry. `house/HouseViewModel.kt` owns isolated source/settings and export actions. `house/HouseScreen.kt` supplies upload, copy count, outline, preview and save controls. The existing PhotoProcessor decoder and SmartRenderer handle orientation, proportional image fitting and JPEG density metadata. Existing passport processing is not invoked or changed.

Settings and the private source persist across activity recreation and app restarts. Delete working photograph removes this flow's private source; saved exports and other workflows are unaffected. Export requires a writable Android document destination. Maximum source decode edge is 3508 pixels; very large inputs are sampled for bounded memory. JPEG is the output format for this specific flow. Physical printing, vendor-specific document providers and low-memory phones still need operator validation.

Automated tests verify exact geometry, maximum three-frame size, margins, gaps, no overlap/clipping, original source bytes, proportional fit, controls, persisted preferences, selected export and three-file document-provider export, pixel dimensions, quadrant placement and density metadata. The exported examples used for validation are synthetic, not customer photographs.
