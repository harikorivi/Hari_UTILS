"""Portable save-dialog suggestions derived from the current customer's name."""
import re
import unicodedata


def suggested_filename(name: str, kind: str, page: int = 0, pages: int = 1) -> str:
    stem = unicodedata.normalize('NFC', name)
    stem = re.sub(r'[<>:"/\\|?*\x00-\x1f\x7f]', '_', stem)
    stem = ' '.join(stem.split()).strip(' .').replace(' ', '_')
    # Leave room for a suffix/extension within common filesystem byte limits.
    stem = stem.encode('utf-8')[:180].decode('utf-8', errors='ignore').rstrip(' .') or 'customer'
    reserved = {'CON', 'PRN', 'AUX', 'NUL', 'CONIN$', 'CONOUT$'}
    reserved.update(f'{prefix}{digit}' for prefix in ('COM', 'LPT') for digit in '123456789¹²³')
    if stem.split('.')[0].upper() in reserved:
        stem = '_' + stem
    if kind == 'original':
        return stem + '_original.pdf'
    if kind == 'report':
        return stem + '_verification.json'
    if kind not in ('pdf', 'png', 'jpg', 'jpeg'):
        raise ValueError('Unsupported export type.')
    suffix = f'_page_{page + 1}' if kind != 'pdf' and pages > 1 else ''
    return stem + suffix + '.' + kind
