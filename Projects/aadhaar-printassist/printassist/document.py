"""Bounded, in-memory PDF opening and page-at-a-time rendering."""
from .errors import UserError

from dataclasses import dataclass
from contextlib import closing
from io import BytesIO
from hashlib import sha256
from pathlib import Path
import math
import os
import tempfile

import pypdfium2 as pdfium

from .passwords import password_for
from .validation import Trust, verify, extract_signed_version
from .badges import add_verification_footer, draw_verified_tick

MAX_FILE = 20 * 1024 * 1024
MAX_PAGES = 10
MAX_PIXELS = 20_000_000
MAX_PAGE_POINTS = 3000


@dataclass(frozen=True)
class PrintSettings:
    dpi: int = 200
    landscape: bool = False
    scale: int = 100
    include_verification_badge: bool = False
    show_signature_tick: bool = True

    def __post_init__(self):
        if self.dpi not in (150, 200, 300) or not 25 <= self.scale <= 100:
            raise UserError('Choose 150, 200 or 300 DPI and 25–100% scaling.')


def atomic_write(path: Path, write):
    """Private staging file in selected destination; remove on failure."""
    fd, temp = tempfile.mkstemp(prefix='.printassist-', dir=path.parent)
    try:
        with os.fdopen(fd, 'wb') as stream:
            write(stream)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temp, path)
    finally:
        if os.path.exists(temp):
            os.unlink(temp)


class Job:
    def __init__(self, raw, document, report, source_original=None):
        self.original = raw
        self.source_original = raw if source_original is None else source_original
        self.document = document
        self.report = report

    @classmethod
    def open(cls, path: Path, name: str, birth: str, trust: Trust, signed_version=False):
        password = password_for(name, birth)
        document = None
        try:
            with path.open('rb') as stream:
                raw = stream.read(MAX_FILE + 1)
            if len(raw) > MAX_FILE or not raw.startswith(b'%PDF-'):
                raise UserError('Select a PDF smaller than 20 MB.')
            source_original = raw
            if signed_version:
                raw = extract_signed_version(raw, password)
            try:
                document = pdfium.PdfDocument(raw, password=password)
            except pdfium.PdfiumError as exc:
                if exc.err_code == 4:
                    raise UserError('Password did not match. Check the printed name and birth year.') from None
                raise UserError('The PDF could not be opened; it may be damaged or unsupported.') from None
            if not 1 <= len(document) <= MAX_PAGES:
                raise UserError('Only PDFs with 1–10 pages are supported.')
            for i in range(len(document)):
                with closing(document[i]) as page:
                    w, h = page.get_size()
                    if not all(math.isfinite(x) and 0 < x <= MAX_PAGE_POINTS for x in (w, h)):
                        raise UserError('Unsupported PDF page dimensions.')
            report = verify(raw, password, trust)
            report.source_sha256 = sha256(source_original).hexdigest()
            if signed_version:
                report.scope = 'signed_revision'
                report.note = ('This result covers the extracted original signed version. '
                               'Later changes in the uploaded file are excluded from preview and printing. '
                               + report.note)
            return cls(raw, document, report, source_original)
        except Exception:
            if document is not None:
                document.close()
            raise
        finally:
            password = None

    def __len__(self):
        return len(self.document)

    def render(self, index, dpi=150, signature_tick=False):
        with closing(self.document[index]) as page:
            w, h = page.get_size()
            if w * h * (dpi / 72) ** 2 > MAX_PIXELS:
                raise UserError('Page too large at this quality; choose a lower DPI.')
            bitmap = page.render(scale=dpi / 72)
            try:
                image = bitmap.to_pil().convert('RGB')
                if signature_tick and self.report.status == 'valid':
                    converter = bitmap.get_posconv(page)
                    for signature in self.report.signatures:
                        if signature.status != 'valid' or signature.page_index != index or not signature.signature_rect:
                            continue
                        x0,y0,x1,y1 = signature.signature_rect
                        points = [converter.to_bitmap(x,y) for x,y in ((x0,y0),(x1,y1),(x0,y1),(x1,y0))]
                        box = (max(0,min(p[0] for p in points)), max(0,min(p[1] for p in points)),
                               min(image.width,max(p[0] for p in points)), min(image.height,max(p[1] for p in points)))
                        draw_verified_tick(image, box)
                return image
            finally:
                bitmap.close()

    def render_print_copy(self, index, dpi=150, include_badge=False, signature_tick=True):
        image = self.render(index, dpi, signature_tick)
        if not include_badge:
            return image
        try:
            return add_verification_footer(image, self.report, dpi)
        finally:
            image.close()

    def export_image(self, path, index, dpi=200, include_badge=False, signature_tick=True):
        image = self.render_print_copy(index, dpi, include_badge, signature_tick)
        try:
            fmt = 'PNG' if path.suffix.lower() == '.png' else 'JPEG'
            atomic_write(path, lambda f: image.save(f, format=fmt, quality=95, dpi=(dpi, dpi)))
        finally:
            image.close()

    def export_pdf(self, path, settings):
        # A new raster PDF has no signature field; any visual status refers
        # explicitly to verification of the original, not the exported copy.
        # PDFium streams images into pages so full-page bitmaps are not all held.
        with pdfium.PdfDocument.new() as output:
            for i in range(len(self)):
                image = self.render_print_copy(i, settings.dpi, settings.include_verification_badge, settings.show_signature_tick)
                try:
                    jpeg = BytesIO()
                    image.save(jpeg, format='JPEG', quality=95)
                    jpeg.seek(0)
                    width, height = (842, 595) if settings.landscape else (595, 842)
                    with closing(output.new_page(width, height)) as page:
                        obj = pdfium.PdfImage.new(output)
                        obj.load_jpeg(jpeg, inline=True)
                        factor = min((width - 36) / image.width, (height - 36) / image.height) * settings.scale / 100
                        w, h = image.width * factor, image.height * factor
                        obj.set_matrix(pdfium.PdfMatrix(w, 0, 0, h, (width-w)/2, (height-h)/2))
                        page.insert_obj(obj)
                        page.gen_content()
                finally:
                    image.close()
            atomic_write(path, lambda f: output.save(f))

    def save_original(self, path):
        atomic_write(path, lambda f: f.write(self.source_original))

    def close(self):
        if self.document is not None:
            self.document.close()
            self.document = None
        self.original = b''
        self.source_original = b''
        self.report = None
