# Phase 1 verification checklist — 0.2.0

## Automated checks completed

- 35 × 45 mm exact PDF transforms; portrait 30, landscape 28, Auto portrait.
- 50 × 50 mm 24-copy request rejected with maximum 15; Use maximum applies pending settings.
- 25 × 35 mm Auto landscape 50 versus portrait 49; deterministic ties.
- Maximum on new import, reduced counts, dimensions/margins/gaps/border recalculation.
- All permitted placements and external strokes within margins; no overlapping photos.
- Decimal custom sizes, minimum margins, no-fit sizes, exactly-one-photo layouts.
- One-page A4 PDF parse/render, both JPEG/PNG orientations, 300 DPI density metadata/PNG CRC.
- CameraX emulated capture, import, visible quality results, low-resolution warnings, bounded large-image decode.
- Save/export to local files and restricted read-only content-URI sharing.
- Repeated exports, activity recreation, actual screen orientation change, preference migration/custom-size reload.

## Real-phone checks still required

Use Android 9 and at least one current vendor phone. Record model, Android version, app version, source image type and exact reproduction steps.

1. Update the existing debug installation and confirm Auto/Maximum migration with the previous photo size. Reopen the working draft; verify crop, selected count and settings. Test a process kill independently of screen rotation.
2. Deny camera permission, permanently deny, open Settings and return. Use Gallery when permission remains denied. Cancel photo picker without losing the current draft.
3. Test rear/front cameras, missing front camera, flash-capable/flashless devices, portrait/landscape capture and app background/foreground. Check camera guide alignment on each vendor.
4. Capture a real portrait with plain background and even light. Compare face alignment/eye/blur/shadow/background feedback against visual judgment. Include multiple/no-face, glare, uneven light and motion blur samples. Do not treat PASS as authority approval.
5. Import rotated-EXIF JPEG, PNG, HEIC, very large, low-resolution and damaged files. Confirm natural aspect ratio and orientation. Verify hair/chin/shoulder room after auto/manual crop.
6. Try preset/custom sizes, maximum/reduced counts, Auto/explicit orientations, all margin/gap extremes. No output may ever contain a second page. Invalid fits/counts must retain a valid working layout or explicitly apply Use maximum.
7. Preview the full sheet at default zoom, then zoom/scroll. Test small-screen landscape, large accessibility text, TalkBack and narrow custom crops.
8. Save each format to local Downloads, cancel the save picker, and test full/unavailable storage or a provider failure. Open the saved files in independent apps. A failed write can leave a partial destination file; discard it and retry.
9. Share PDF/JPEG/PNG to the actual shop's printer/transfer application. Confirm the receiving app can read the selected file and that a later customer export does not replace it. Do not share customer files during development unless intended.
10. Repeat captures/imports/exports under low-memory conditions and observe Android memory tooling for sustained leaks. Emulator repetition alone is not a leak proof. Check Android 16 KB page-size compatibility before release.
11. Repeat the core workflow in airplane mode from first launch after installation. Saving/sharing to cloud apps is governed by those apps and may require internet.
12. Delete the working photo and verify draft/share-cache removal; user-saved and recipient-owned exports remain separately managed.

## Physical printer checks still required

1. Open the PDF in the intended printer workflow. Select A4 and Actual size / 100%. Disable Fit to page / Shrink to fit.
2. Print the synthetic reference sheet in `docs/evidence/portrait.pdf` first; its colored outer rectangles are 35 × 45 mm. Measure both axes of several rectangles, including opposite page corners, using a ruler or caliper.
3. Confirm cutting marks are visible and not clipped. Increase printer-safe margins if the printer requires more than the configured value; verify the new maximum.
4. Repeat landscape and JPEG/PNG. Confirm the printer app honors 300 DPI or allows explicit physical paper size without rescaling photo content. Pixel quantization is normal; arbitrary fit-to-page scaling is not.
5. Test optional inside border and external guides. Cut at the intended photo outer edge and measure finished photos. Record paper, printer, driver/plugin, scale setting and measured deviations.
6. If dimensions differ, investigate printer media/scaling/feed behavior. Do not compensate by stretching the source image or silently changing requested dimensions.

No physical printing, portrait acceptance, vendor-wide compatibility or one-minute performance guarantee has been verified by the automated tests.
