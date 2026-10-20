"""Synchronous GDI raster printing; no decrypted PDF or image files on disk."""
from .errors import UserError

import sys


def printers():
    if sys.platform != 'win32':
        return []
    import win32print
    return [p[2] for p in win32print.EnumPrinters(
        win32print.PRINTER_ENUM_LOCAL | win32print.PRINTER_ENUM_CONNECTIONS)]


def print_job(job, printer, settings):
    if sys.platform != 'win32':
        raise UserError('Native printing is available on Windows. Export a print PDF on this platform.')
    import win32con
    import win32gui
    import win32print
    import win32ui
    from PIL import ImageWin

    handle = win32print.OpenPrinter(printer)
    dc = None
    started = False
    try:
        mode = win32print.GetPrinter(handle, 2)['pDevMode']
        if mode is None:
            raise UserError('Printer driver does not expose paper settings.')
        mode.PaperSize = 9  # DMPAPER_A4
        mode.Orientation = 2 if settings.landscape else 1
        mode.Fields |= win32con.DM_PAPERSIZE | win32con.DM_ORIENTATION
        if win32print.DocumentProperties(0, handle, printer, mode, mode,
                win32con.DM_IN_BUFFER | win32con.DM_OUT_BUFFER) < 0:
            raise UserError('Printer driver rejected the selected paper settings.')
        hdc = win32gui.CreateDC('WINSPOOL', printer, mode)
        dc = win32ui.CreateDCFromHandle(hdc)
        dc.StartDoc('PrintAssist customer print')
        started = True
        for i in range(len(job)):
            image = job.render_print_copy(i, settings.dpi, settings.include_verification_badge, settings.show_signature_tick)
            try:
                dc.StartPage()
                w, h = dc.GetDeviceCaps(8), dc.GetDeviceCaps(10)  # HORZRES, VERTRES
                factor = min(w / image.width, h / image.height) * settings.scale / 100
                iw, ih = int(image.width * factor), int(image.height * factor)
                left, top = (w-iw)//2, (h-ih)//2
                ImageWin.Dib(image).draw(dc.GetHandleOutput(), (left, top, left+iw, top+ih))
                dc.EndPage()
            finally:
                image.close()
        dc.EndDoc()
        started = False
    finally:
        if dc is not None:
            if started:
                dc.AbortDoc()
            dc.DeleteDC()
        win32print.ClosePrinter(handle)
