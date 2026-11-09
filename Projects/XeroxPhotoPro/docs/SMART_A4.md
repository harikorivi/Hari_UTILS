# Smart A4 Photo Layout Maker — 0.10.0

## Operation

The home screen now separates **Passport Photo Maker** and **Smart A4 Photo Layout Maker**. Existing passport drafts, background processing, face framing, two-person sheets and exports retain their own workflow. Home switches workflows without deleting their applied working settings.

Smart workflow:

1. Choose **Select / add images from Gallery** (multiple selection) or **Take photo**. Smart camera uses the existing CameraX module with face analysis and face overlays disabled.
2. Open **Edit Image N**. Enter a label, physical width/height and copies; choose a preset if useful. **Apply image settings** commits these fields before optimization. Each image has independent settings, replacement/removal, quarter-turn rotation and optional automatic 90° packing rotation.
3. Choose a layout mode and optionally adjust A4 orientation, margins, horizontal/vertical gaps and cutting style.
4. **Optimize layout**, inspect the complete A4 preview and per-image counts, then **Save JPEG/PDF/PNG** or Share. JPEG is initially selected.

The source aspect ratio is locked initially; changing one dimension updates the other. Presets unlock the frame ratio. **Fit Inside** preserves all content with white margins; **Fill and Crop** proportionally fills the frame and trims its center. Stretch is intentionally omitted. Transparent sources are composed onto white paper. No face detection, automatic portrait crop or background replacement is applied in Smart mode. Rotation by the operator swaps the physical width/height; packing rotation swaps the placed footprint only when explicitly allowed.

General presets are 20×30, 25×35, 35×45, 40×50, 50×50, 50×75 and 100×150 mm. They are not official document standards. Enter custom dimensions between 10 and 287 mm.

## Copies and optimization

- **Maximum Fit** is the default for a single image. It ignores any previous exact quantity and fills the practical capacity. With multiple images, the copy fields become minimum/ratio units and complete integer batches preserve every entered ratio: A=2 and B=1 yields equal repeated batches, never a page filled only with A.
- **Exact Copies + Best Fit** is the default after adding multiple images. It tries multiple rectangle orders, row/column preferences, short-side placement and permitted rotations. It evaluates portrait and landscape when Auto is selected.
- **Exact Copies** uses input image order with the same safe packing and orientation checks. It never increases or reduces the requested quantities.

The engine uses bounded multi-start MaxRects packing. Uniform rectangles naturally form regular grids. Among complete layouts it prefers a smaller occupied strip of paper, leaving a more useful contiguous blank area. Maximum Fit searches complete ratio batches within an area/count bound. This is a practical heuristic, not a proof of global optimality for arbitrary mixed rectangles; a failed heuristic is not proof that no mathematical packing exists.

If no complete arrangement is found, no partial preview/export is produced. The screen lists unplaced quantities from the best trial and suggests fewer copies, different sizes, Auto orientation or Best Fit. When the other orientation produces a complete exact layout, a button explicitly applies that alternative. No source dimensions or copy counts are silently changed.

## Physical output

Every output is exactly one single-sided A4 page. Preview and raster exports share one renderer and the same placement records. PDF reuses the existing single-page PDF serializer with per-placement image references and rotation transforms. Existing passport serialization tests continue to cover that shared component.

- A4 portrait is 210×297 mm; landscape is 297×210 mm.
- Raster exports are approximately 2480×3508 or 3508×2480 pixels, with 300-DPI JPEG JFIF or PNG pHYs metadata.
- PDF uses fractional point dimensions for precise A4 and image sizes.
- Borders/dashed lines sit inside the finished rectangle; corner marks remain outside it with reserved clearance. Finished image dimensions remain the dimensions entered by the operator.
- Preview reports count, per-image sizes, orientation, margins/gaps and unused sheet area, including margins and gaps.

Print at **Actual size / 100%**. Printer applications can ignore image density or apply fit-to-page scaling. Measure the first printed image before a shop production run.

## Storage, performance and limits

Smart stores original imported file bytes unchanged in its private job directory, including orientation metadata and transparency. Thumbnails and rendering use orientation-aware decoding. Source decoding for Smart is bounded to a 3508-pixel longest edge, matching an A4 300-DPI long edge; the private original is still retained. Very high-resolution sources or tight crop-and-fill cases may need real-phone detail inspection. Passport decoding retains its existing 2400-pixel bound.

Only thumbnails are retained for the image list. One source is decoded at a time, and prepared images are limited to the fitted layout area. Packing, preview generation and export run off the UI thread with a processing indicator. Low-memory failures show a retry message. Representative physical-phone memory and speed testing remains pending.

A working job supports up to 20 different images and 1000 requested total copies/ratio units, 2–30 mm margins and 0–20 mm gaps. Corner marks require at least 2 mm spacing. Applied jobs are restored privately on reopening; unapplied editor fields are not saved as job settings. **Clear Smart job** deletes its sources and temporary Smart shares, but not saved external exports or passport drafts. No cloud processing or new network permission was introduced.

Supported decoding follows Android ImageDecoder (tested JPEG, PNG and WebP). Multi-select uses Android's photo picker/document fallback; actual gallery provider behavior can vary by OS/vendor. Direct printer integration, arbitrary paper sizes, multipage output, drag-to-place layout, off-center manual crop handles and mathematical globally optimal packing are not included.
