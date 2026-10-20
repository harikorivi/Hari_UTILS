import logging

# Third-party validation diagnostics can include certificate/document details.
# The shop application deliberately emits no customer logs.
logging.disable(logging.CRITICAL)
from .gui import main
main()
