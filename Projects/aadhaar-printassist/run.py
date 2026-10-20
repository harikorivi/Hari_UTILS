"""Desktop entry point for Python and PyInstaller."""
import sys
import logging
logging.disable(logging.CRITICAL)
if sys.platform == 'win32':
    import ctypes
    ctypes.windll.shell32.SetCurrentProcessExplicitAppUserModelID('PrintAssist.Desktop.V5')
from printassist.gui import main
if __name__ == '__main__':
    main()
