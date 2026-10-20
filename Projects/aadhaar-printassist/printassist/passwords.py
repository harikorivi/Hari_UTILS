"""One deterministic password attempt; no guessing or brute force."""
from .errors import UserError

from datetime import date, datetime
import re


def password_for(name: str, birth: str) -> str:
    compact = ''.join(name.split()).upper()
    if not compact or len(compact) > 200 or not compact.isascii():
        raise UserError('Enter the English name exactly as shown on Aadhaar.')
    if not re.fullmatch(r"[A-Z.'-]+", compact) or not any(c.isalpha() for c in compact):
        raise UserError('Check the name; letters and name punctuation are supported.')
    birth = birth.strip()
    if re.fullmatch(r'[0-9]{4}', birth):
        year = int(birth)
    else:
        parsed = None
        for fmt in ('%d/%m/%Y', '%Y-%m-%d'):
            try:
                parsed = datetime.strptime(birth, fmt).date()
                break
            except ValueError:
                pass
        if parsed is None or parsed > date.today():
            raise UserError('Enter a birth year or a valid DD/MM/YYYY date.')
        year = parsed.year
    if not 1850 <= year <= date.today().year:
        raise UserError('Check the birth year.')
    return compact[:4] + str(year)
