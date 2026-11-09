# Image quality investigation and implementation — 0.9.0

## Evidence and root causes

The supplied three JPEGs are 2480 × 3508 with JFIF unit 0 and density 1 × 1: their physical print resolution is unspecified. The Keerthi sheet has approximately 414 × 414-pixel square photos and three widely spaced columns. It is not the fixed 35 × 45 mm two-photo layout. The file alone cannot identify the app version or settings that produced it. The two Shiva sheets have the expected five columns, six rows, 15 + 15 placement. No layout code was changed in this release.

Framing previously used the maximum of face-box height and width constraints, positioned the box at a fixed top offset, and silently clamped the crop to source bounds and zoom limits. It never used the eye landmarks already available from detection. Box shape and camera distance could consequently produce different final eye lines and face sizes.

The bundled background model remains ML Kit selfie segmentation 16.0.0-beta6 in single-image mode. Raw model confidence is sampled at pixel centers, bilinearly interpolated, then conservatively aligned to source RGB at a maximum 1024-pixel edge. This is low-resolution semantic person segmentation, not a ground-truth hair alpha matte. Interpolation is coordinate-consistent; it cannot recreate details absent from the raw mask. Natural mode remaps confidence continuously between .02 and .98; it is not a binary threshold or broad Gaussian feather. Cleaner mode can suppress weak edges, so manual ear recovery must retain priority.

A concrete compositing weakness was the use of original edge RGB directly as foreground. Such a pixel may already contain the old background: C = aF + (1−a)B. Compositing C again onto the replacement retains some B, producing a halo. Whether this explains every visible halo in the supplied top portrait cannot be established without its original. Model uncertainty and already-processed inputs also contribute.

Working drafts were saved as JPEG quality 98 on import and rotation. Although the live in-memory import avoided that round trip, restoring or rotating drafts could accumulate compression changes. New drafts are lossless PNG, with read compatibility for older JPEG drafts. The original external file is never overwritten. Decode still caps the longest edge at 2400 for memory safety; this is not unlimited full-resolution processing.

## Implemented changes

- Accurate face detection adds face contours. Frontal, reliable eye landmarks and the lower face contour define eye-center and chin anchors. Crop scale uses eye-to-chin distance; translation aligns the eye line and horizontal center. No face warping or generative fill is used.
- Defaults are operator guidance: eye line 40% from the top, eye-to-chin span 32% of photo height. Custom crop exposes configurable targets, Apply/reset controls and optional eye/chin/center guides. They are stored separately for each working photo and reset on new import. They are not official government presets. Use matching targets for both customers.
- Crown/hair height is not a reliable face landmark. A conservative crown estimate only detects likely insufficient space; it does not certify head-height compliance. Missing landmarks produce an explicit approximate-crop warning. Source bounds, maximum zoom or clipped headroom produce the requested insufficient-space warning, visible on review. Manual zoom/pan, Auto crop and reset remain available.
- Edge-color correction searches small local rings for confident background and foreground anchors. It rejects textured background, low contrast, implausible unmixing, and pixels inconsistent with the local compositing model. Only uncertain edge RGB is corrected, with bounded channel changes. Alpha is unchanged; opaque interior colors and brush-modified alpha regions are bypassed. This is conservative color decontamination, not a replacement matting network or a guarantee of fine-hair reconstruction.
- Original background bypasses segmentation/composition. Segmentation remains on the uncropped source so changing crop does not change model context, and source-coordinate manual repairs stay aligned. The cached mask is reused. Composition runs only on the output portrait to limit memory and repeated work. Heavy work remains on background dispatchers.

## Model evaluation

[ML Kit documentation](https://developers.google.com/ml-kit/vision/selfie-segmentation/android) describes raw masks smaller than the input (for example 256 × 256). Merely requesting an upscaled mask does not add model detail. [MediaPipe's model catalogue](https://developers.google.com/edge/mediapipe/solutions/vision/image_segmenter) includes hair and multiclass semantic models, but semantic class labels are not automatically a higher-quality alpha matte. [MODNet](https://github.com/ZHKKKe/MODNet) is a relevant portrait-matting candidate. No converted model was bundled without validating conversion correctness, weight licensing, device memory, speed and original-photo quality. The current release retains the existing offline model and implements measurable bounded improvements around it. No cloud upload or internet permission was added.

## Export and layout verification

The current exporter writes one 300-DPI JFIF segment for JPEG and a PNG pHYs chunk at 11811 pixels/metre. Tests read actual save output and FileProvider share streams, including density units, rather than only calling a metadata helper. PDFBox independently verifies one exact A4 MediaBox and photo transforms. Both portrait and landscape remain supported in single-photo mode; the existing 15 + 15 mode remains portrait A4. Arbitrary two-photo landscape layouts were never part of the implemented 5 × 6 contract.

No evidence establishes when the user's three files lost density metadata. Current direct save/share tests cannot prove that another application preserves it after receiving the file. Physical printing still requires Actual size / 100% and ruler verification.

## Comparison scope and limitations

The user supplied exported sheets again when asked for original photos. The outdoor-background bottom portrait from the third sheet is used as an **export-derived reference**, not an original camera photo. Its crop and scale are already fixed, and any earlier losses cannot be recovered or validated. Comparison images are delivered separately from the source archive; customer images are not bundled into the APK. A controlled synthetic soft-edge fixture provides quantitative before/after color-error evidence independently of that limitation.

Real original-photo hair/ear preservation, document-specific crown/chin compliance and representative-phone performance remain unverified. Similar foreground/background colors, complex backgrounds and confidently wrong segmentation can still need manual Restore/Erase. The algorithm does not promise to recover ears the model missed. Automatic framing on several real people at different camera distances needs original-photo testing beyond synthetic geometry and the export-derived reference. This debug build is for that next test round, not a claim that every quality acceptance criterion is proven.
