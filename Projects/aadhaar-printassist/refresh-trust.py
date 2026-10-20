"""Administrator maintenance command. Downloads public CRLs only."""
import logging
from pathlib import Path

logging.disable(logging.CRITICAL)
from printassist.errors import UserError
from printassist.trust_refresh import refresh

if __name__ == '__main__':
    try:
        refresh(Path(__file__).resolve().parent / 'trust')
        print('Current issuer CRLs verified and installed. Reopen the customer job to recheck.')
    except UserError as error:
        print(error)
        raise SystemExit(1)
    except Exception:
        print('Trust refresh failed. Ask the administrator to check the trust installation.')
        raise SystemExit(1)
