# Feedback review and implementation — 0.2.0

## Supplied evidence

Reviewed both pages of the user's `XeroxPhotoPro-1792601272866.pdf` visually and with a PDF parser. It is a generated photo-sheet output, not a written testing report: both pages contain images/cutting marks, no extracted text, no annotations, and no UI screenshots. The portrait from that file is not included in this source distribution or in generated test fixtures.

Verified from the file:

- Two landscape A4 pages (297 × 210 mm in fractional PDF points).
- 15 photo placements on page 1, 9 on page 2: 24 total.
- Each photo placement is 50 × 50 mm. The embedded image is 591 × 591 pixels.
- The image transforms preserve square dimensions. There is no evidence in this PDF of an A4 MediaBox or photo-size error; physical printing cannot be inferred from the file.
- Rows/gaps start near the top-left rather than centering the used grid; page 2 contains substantial unused area.

The pasted user request supplies the new single-page, default-maximum, orientation, export and workflow requirements. Those are distinguished below from facts directly observed in the PDF. No absent written observations are treated as reported defects.

## Issue / correction matrix

| Issue and evidence | Previous behavior | Expected behavior | Root cause | Fix applied | Verification status |
|---|---|---|---|---|---|
| Extra page (PDF confirmed) | 24 square photos became 15 + 9 across two landscape pages | One A4 page; reject counts above 15 for these settings | `LayoutEngine` chunked placements by capacity; PDF writer emitted every chunk | One `SheetLayout.placements` list, capacity exception, PDF page tree hardcoded to exactly one page | Regression test rejects 24, reports 15, accepts maximum; independent parser confirms one page |
| Maximum not the default (request/code) | Default 12 and remembered arbitrary counts | Dynamically select maximum for each new image | `copies=12` and preference-based fixed count | Nullable count denotes Maximum; import resets it; legacy preferences migrate to Auto/Maximum | Default 30 test and automatic-preview Android test pass |
| No orientation recommendation (request/code) | Portrait/landscape switch only | Compare both; auto-select larger capacity; operator override | Boolean landscape flag with no comparison | AUTO/PORTRAIT/LANDSCAPE; both capacities displayed; portrait tie preference | 35×45: 30/28; 25×35: 49/50; 50×50: 15/15 tests pass |
| Overflow during configuration (request/code) | Any supported count paginated | Clear maximum, offer to use it, never shrink | Capacity not an input constraint | Reject candidate settings; retain valid config; dialog applies pending candidate with maximum only after tap | Android overflow/Use maximum test passes |
| Grid positioning (PDF/code) | Fixed top-left inset, partial second page | Practical single-page use with uniform gaps | Fixed inset placement and pagination | Center selected grid footprint; fixed margins/gaps/photo sizes; no mixed rotations | Geometry combinations and visual synthetic export review pass |
| JPEG/PNG missing (request/code) | PDF only | Complete one-page A4 JPEG and PNG | No sheet raster exporter | Shared sheet renderer, 300 DPI raster, JFIF/pHYs density metadata, sequential temp-file encoding | Both orientations decode at correct dimensions; metadata/CRC tests pass |
| Sharing missing (request/code) | Save PDF only | Save or share each format | No FileProvider/share action | Restricted exports cache path, unique files, read grants, MIME-specific intents | Content URI readable and non-exported provider/read-grant assertions pass; real printer-app chooser behavior pending |
| Custom dimensions missing in UI (request/code) | Preset chips only | Custom width/height with fit validation | Core accepted dimensions but UI had no entry | Decimal size entry, automatic capacity recalculation, no-fit guidance | Fractional sizes, one-fit, no-fit and custom export tests pass |
| Inside border option missing (request/code) | External marks only | Optional border without changing finished size | No border flag or renderer path | 0.2-point gray inside stroke; optional external guides unchanged | PDF rectangle/matrix tests and shared raster renderer; physical trimming pending |
| Limited validation (request/code) | Face hint and source-DPI message | Visible per-check results and review guidance | No structured quality report | Face count/pose/eyes/crop checks plus lighting/sharpness/shadow/background heuristics | Synthetic image/low-resolution/large-source tests pass; real portrait threshold calibration pending |
| Crop/settings persistence and rotation (request/code review) | ViewModel retained activity state, crop lost on process restart | Retain selected geometry and edits | Crop positions not stored | Persist zoom/pan; keep preview destination in ViewModel; retain current count on recreation | Activity recreation and actual landscape switch pass; full process-kill flow remains manual checklist |
| Camera guide placement and compact screen (code review, not reported by PDF) | Guides drawn over whole letterboxed view; tall fixed column | Guides relate to image; landscape controls remain usable | Overlay ignored fitted image area; fixed vertical arrangement | Fit-center guide coordinates, responsive split layout, scrollable controls, eye/light hints, flash-capability check, Settings return refresh | Emulated camera capture passes; vendor front-camera/rotation/alignment validation pending |
| Error/memory paths (code review) | Temporary export behavior limited to PDF | Recover from storage/encoding/memory failures | New full-sheet raster requires bounded allocations and cleanup | One raster at a time, recycle in finally, streaming metadata, scratch cleanup, bounded source decode, memory-error guidance | Six PDF/JPEG/PNG orientation exports plus separate share/custom exports pass without crash; not a formal leak proof |

## Main source changes

- `core/Layout.kt`: orientation comparison, capacity/occupied/remaining geometry, maximum sentinel, centered one-sheet placements, explicit overflow.
- `core/PhotoPdf.kt`: one-page writer, optional inside border, precise photo transforms retained.
- `core/RasterMetadata.kt`: PNG pHYs chunk/CRC and JPEG JFIF density rewriting for freshly encoded images.
- `core/Quality.kt`: deterministic, testable image heuristics.
- `export/SheetRenderer.kt`: common preview/raster drawing; `SheetExporter.kt`: PDF/image encoding and secure share intents.
- `PhotoViewModel.kt`: automatic preview preparation, validation report, overflow candidate state, export/share serialization and lifecycle state.
- `storage/DraftStore.kt`: legacy migration, custom geometry/crop persistence and temporary-share deletion.
- `ui/PhotoApp.kt`: maximum-first workflow, custom sizes, orientation comparison, copy controls, quality details and all save/share actions.
- `camera/CameraScreen.kt`: image-relative guides, landscape layout, permission refresh and bounded failure handling.
- Manifest/resources: restricted export FileProvider. Version code 2, version 0.2.0; existing app ID and development signing retained.

## Pending issues and limits

No known failing automated test remains in this build. The supplied PDF does not support diagnosing any additional unrecorded camera, touch, save-picker or printer malfunction. If other observations were intended, their reproduction steps/device details are still needed.

Physical A4 dimensions, print scaling, cutter alignment, rear/front portrait quality, flash behavior, vendor permission flows, quality-threshold reliability, Android 9/low-memory/16 KB page-size support and performance require actual devices/printers. A passing emulator run does not verify these. Raster dimensions are subject to pixel rounding, and printer apps may ignore density metadata.

Deferred by the user's Phase 1 boundary: direct printing and mixed-customer sheets. Regional translations, named project history, automatic retention scheduling, signed release distribution and advanced tone/rotation editing remain future work. No automatic facial/identity alteration was added.

## 0.3.0 background replacement

User-authorized background replacement is now implemented with Original, White, Light blue and custom RGB. Multi-customer sheets remain deferred. See README for operation and limits.

## 0.4.0 optional person name

Added a draft-scoped optional name on setup and preview. PDF/JPEG/PNG save suggestions and share display names use a common safe filename prefix. New-photo import and draft deletion reset the name; rotation preserves it. Multi-customer sheets remain deferred.

## 0.5.0 mobile feedback — 2026-10-25

The supplied screenshot showed 15 photos centered on A4 and a misleading six-row label. Layout now starts at the top-left safe inset; actual rows and columns describe the selected count. New jobs reset to 35 × 45 mm. Custom controls disclose crop, background, size, sheet layout and export settings only when opened. Save/Share remain visible in a bottom action bar. Large labelled touch targets replace horizontal option strips, and entire switch rows toggle. Multi-customer sheets remain deferred.

## 0.6.0 JPEG default and background experience

JPEG now defaults for save/share. Background selection uses named swatches with an explicit selected state, before/after comparison, detail zoom and RGB custom mixing. Source-color-guided mask refinement and optional Natural/Cleaner/Softer edge treatment replace plain mask upsampling. A perfect cutout is not guaranteed; original mode remains available. Multi-customer sheets stay deferred.

## 0.7.0 missing-ear feedback

Added manual original-pixel recovery for ears/edges that segmentation removed. The repair editor supplies Restore, Erase, Move/zoom, comparison, Undo/Reset and explicit Apply/Cancel. Repairs override cleanup strength and persist in the current draft. This does not claim to fix automatic ear segmentation for every photo. JPEG default, 35 × 45 mm new-photo default and top-left A4 packing remain. Multi-customer sheets remain deferred.

## 0.8.0 explicit two-person authorization

The latest request supersedes the earlier multi-customer deferral for a bounded two-photo layout: 15 copies at the top and 15 at the bottom. Added optional sheet-mode selection, independent drafts and edits, two-name filenames and consistent JPEG/PNG/PDF output. Single-photo mode and JPEG remain defaults. Arbitrary customer counts and mixed photo sizes are outside this feature.

## 0.9.0 image quality priorities

Replaced box-only automatic framing with eye/chin landmarks and configurable operator targets, explicit fallback/insufficient-space warnings, optional guides, and independent target persistence. Added conservative local foreground-color unmixing at uncertain boundaries, leaving alpha and opaque interiors unchanged. New drafts are lossless with old JPEG compatibility. Preserved the existing two-photo layout and export geometry. Added save/share metadata, control, crop-geometry, halo-error and lossless-draft tests. Real reference comparison uses an already exported outdoor portrait; it cannot certify original-photo ear preservation. Details: IMAGE_QUALITY_V09.md.

## 0.10.0 Smart A4 Photo Layout Maker

Added the explicitly requested separate general-image workflow inside the existing app. Supports one to twenty images, mixed sizes, exact quantities, ratio-based maximum batches, permitted rotation, five cutting styles and precise one-page exports. No face processing is applied in Smart mode. Original input bytes are kept privately; a 3508-pixel decode bound protects rendering memory. Existing passport layout algorithms are unchanged; PDF serialization is shared. Large two-column preset buttons replaced a nested horizontal selector after a UI test exposed inaccessible offscreen taps.
