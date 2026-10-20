# Aadhaar PrintAssist V5 — Historical Evidence Edition

A local desktop application for customer-authorized e-Aadhaar opening, independent embedded PDF signature verification, preview, print preparation and Windows printing. It does not need Adobe Reader, a subscription, a server or a database.

**Delivery status: working source application, with automated core tests; not a certified or production-qualified Windows release.** Automated fixtures contain synthetic documents only. Customer-authorized PDFs were inspected locally for compatibility; none are included. Windows executable packaging, printer drivers, memory on a 4 GB PC, and compatibility with actual customer-authorized UIDAI PDFs must be checked before daily shop use. A Windows EXE is not included: build it on Windows using the script below.

See [historical validation and the tested Nagamani result](docs/HISTORICAL.md).

## What works

- Select one or several PDFs, processing each customer sequentially with separate authorization and details.
- Generate exactly one password from the first four non-whitespace name characters in uppercase and birth year. Initial punctuation is preserved; names shorter than four characters work. DOB accepts `DD/MM/YYYY`, `YYYY-MM-DD`, or just `YYYY`.
- Open encrypted PDFs in memory and preview individual pages.
- Verify original, untouched bytes with pyHanko: integrity, certificate path, current certificate validity, revocation evidence, full-file coverage and administrator-approved UIDAI signer fingerprint.
- Export current page to JPG/PNG; export all pages to a raster A4 PDF with orientation, 150/200/300 DPI, and fit-to-page scaling. Save the byte-identical original upload separately; optionally work from an explicitly selected and independently verified signed version.
- Save an optional JSON verification report tied to the original SHA-256 hash.
- Print all pages through Windows GDI and clear the customer session after spool submission. No PDF reader is invoked.

By default, a plain green tick is rendered at the actual visible signature widget only when the scoped document and signer checks pass. There is no mandatory “Verified by PrintAssist” label or text footer. Verification details remain in the app and optional report. The uploaded PDF is never rewritten. Unknown widget positions are never guessed. Existing source graphics are preserved even when they cannot be cryptographically verified.

An optional **PrintAssist verification footer** can be enabled outside the rendered document content in preview, PDF/JPG/PNG exports and native printing. It shows a green tick only for a genuinely verified original, amber for incomplete verification, red for failed verification, and gray for unsigned documents. It is explicitly labelled PrintAssist, names the verification scope, includes a timestamp and source hash, and states that the copy has no embedded digital signature. The footer is off by default. Its label is only printed if the operator explicitly enables that footer. A new in-field tick is a rendered viewer result, not a new digital signature.

## Signature feasibility and limits

UIDAI describes e-Aadhaar as a password-protected, digitally signed PDF and documents the password examples implemented here. Its FAQ currently recommends Adobe Reader for validation; this is an independent implementation, not a UIDAI-approved replacement.

Green means all implemented checks passed **under the administrator's configured trust policy**. This installation includes the authenticated historical CCA India 2014 / nCode CA 2014 chain and UIDAI 2021 signer pin (see `trust/historical-provenance.json`), plus the independently checked CCA India 2022 root, authenticated nCode intermediates, an exact UIDAI-published signer certificate pin, and signed issuer CRLs checked on 20 October 2026. See `trust/provenance.json` for sources and fingerprints. Other signers, missing evidence or expired evidence still produce yellow. An issuer name containing “UIDAI”, an embedded certificate, or an unsigned QR image is never sufficient for green.

If strict PDF syntax parsing fails, the app retries signature reading in compatibility mode against exactly the same original bytes. It displays the parser mode and a safe diagnostic code, and continues cryptographic checks where possible. Compatibility results can be yellow or red, never green, even when all cryptographic and trust checks pass. If compatibility mode cannot find a signature, the result stays incomplete rather than claiming conclusively that the document is unsigned. No repair or rewriting is performed.

| Result | Meaning |
|---|---|
| Green VERIFIED | Every document signature passes integrity, full-file or assessed archival-only revision coverage, trusted current certificate path and mandatory fresh revocation evidence, or fully passed trusted-time historical validation, and approved UIDAI signer pin |
| Yellow INCOMPLETE | Unknown trust, unavailable/stale evidence, unsupported/parsing failure, expired/unacceptable certificate, or unapproved signer; do not treat as verified |
| Red INVALID OR MODIFIED | A cryptographic check fails, a certificate is revoked, or the signature does not cover the complete current file |
| Gray NO SIGNATURE | Successful parsing found no embedded regular document signature |

Post-signature changes are assessed instead of being automatically labelled cryptographic failures. Strictly parsed unchanged/archival-only updates may pass when pyHanko's difference checks and signature policy accept them. Changed page content or unexplained trailing bytes fail; unassessed revisions, form edits and annotation edits require review for this Aadhaar workflow. Compatibility parsing still prevents green. Expired certificates can also be evaluated using pyHanko AdES historical validation with independently validated timestamps and archival revocation evidence. An operator-entered date or signer-claimed date is never trusted time. See `docs/HISTORICAL.md`. PDF signature checks do not prove that the entered name/DOB matches the visible document or verify the Aadhaar Secure QR payload. Treat the name/DOB as password inputs only.

Converting to an image or making a new print PDF **removes the cryptographic signature**. “Save original uploaded PDF” preserves the uploaded byte sequence and its password protection. If signed-version mode is used, this save still preserves the complete upload, while the report records both the selected version hash and uploaded source hash. Reports apply to that original hash, not to exports or paper copies. Prints can carry existing static “signature unknown” artwork: the app leaves that artwork intact and puts its own clearly labelled result in the optional footer.

## Desktop icon and easy launch

See `WINDOWS-QUICKSTART.txt`. Extract the complete source package into a permanent folder, then double-click `Create-Desktop-Shortcut.bat`. It prepares the Python environment and creates **Aadhaar PrintAssist** shortcuts on the current user's Desktop and Start menu with the included printer icon. Python 3.11+ with Tcl/Tk must be installed for source setup, and the first dependency installation needs internet. Subsequent shortcut launches use `pythonw.exe` without a terminal window. Keep the folder in place; rerun setup after moving it. Shortcut setup needs no administrator rights.

The Windows build embeds `assets/printassist.ico` into the EXE and includes the same shortcut setup. Recipients of the complete built EXE folder need no Python installation. Build and shortcut execution require Windows and have not been run on this macOS host. The icon is original printer artwork and is not a verification indicator or official UIDAI emblem. Recreate it with `python tools/make_icon.py` if desired.

## Run on Windows from source

Install Python 3.11 or newer from python.org, including Tcl/Tk and the Python launcher. Use a maintained Python release. Extract this project into a private local folder, not OneDrive/a shared customer folder.

Double-click `start-windows.bat`. Internet access is needed to install dependencies on first setup; document processing itself is offline. Or run:

```powershell
py -3 -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe run.py
```

If dependency installation was interrupted, rerun the `pip install` command manually. On macOS/Linux, use a Python with Tk support, create a venv, install requirements, and run `python run.py`; direct printing is Windows-only.

## Shop workflow

1. Choose customer PDF(s). There is a native multi-file picker; drag-and-drop is not implemented.
2. Enter the English name as printed and birth year/DOB; confirm customer authorization.
3. For a file with later revisions, optionally select **Use original signed version on unlock (exclude later changes)**. This explicitly extracts the latest regular signed revision, omits all later changes from preview/exports/printing, and verifies that selected version independently. Inspect the preview before printing: later changes may be important. The upload itself remains untouched. Leave the option off to assess the complete uploaded file. Then click **Unlock & verify original**. Name/DOB fields clear after submission; review the result and preview. A failed attempt is reported without password guessing.
4. Choose paper orientation, DPI and scale. **Show green tick in signature area when verified** is enabled by default. **Include PrintAssist verification footer on print copies** is off by default and can be enabled if wanted. New ticks and optional footer results reflect the actual scoped verification result; warnings/failures cannot be changed into a green tick by selecting the checkbox. JPG/PNG exports contain the current page at selected DPI; A4 and scaling apply to the PDF and native printing. Images are not put on an A4 canvas.
5. Export filenames automatically use the Full Name entered for unlocking, for example `Keerthi.pdf`, `Keerthi.png` or `Keerthi.jpg`; no additional field is needed. Spaces in the name become underscores: `Devarakonda Keerthi` becomes `Devarakonda_Keerthi.pdf`, `.png` or `.jpg`. The Save dialog remains editable. Image exports from multi-page PDFs include a page number. Original saves use `_original.pdf` and reports use `_verification.json`. Invalid filename characters are replaced for Windows compatibility. The name is kept only in the current customer session and clears with that session. Export only with customer permission. The app asks for confirmation before writing unencrypted copies. Delete them when no longer required. Reports also contain certificate details and a document hash, so keep them private.
6. Select a printer and print. The app confirms the result before submitting even unsigned/unverified documents. Submission does not prove paper printed successfully. The session clears after submission and the next queued customer can begin.
7. Use **Clear customer / next queued PDF** after exports or an abandoned job. Close the app when done. Closing is blocked while work is in progress, so a print submission is not interrupted by cleanup.

A long workflow panel scrolls on small displays. If a render exceeds the pixel budget, choose a lower DPI. Supported bounds are 20 MB, 1–10 pages, page dimensions up to 3000 points and 20 million rendered pixels per page. These are shop resource limits, not a malware sandbox.

## Configure genuine trust

See [docs/TRUST.md](docs/TRUST.md). The included public certificate bundle was authenticated independently of the customer document: the signer matched UIDAI’s published certificate byte-for-byte, including its document-signing key usages, and the complete issuer chain and required revocation evidence validated to CCA’s published root. This does not automatically approve other XML/QR certificates or other signers. Current evidence must be maintained.

## Refresh public issuer evidence

The earliest currently installed CRL expires on **22 October 2026 at 11:05 UTC (16:35 IST)**. Refresh before then, and regularly thereafter, from the source installation’s Python environment:

```powershell
.\.venv\Scripts\python.exe refresh-trust.py
```

This administrator command downloads only three fixed public issuer CRL URLs, validates signatures, freshness, certificate revocation and the complete configured path, then replaces evidence. It never uploads Aadhaar data or changes trusted roots or signer pins. nCode’s HTTPS endpoint failed TLS chain validation during setup; its public CRLs are fetched over HTTP and authenticated by issuer signatures. TLS validation is never disabled. Errors preserve existing evidence. A packaged EXE installation can receive these refreshed public CRL files from the administrator’s source/maintenance environment; the separate refresh script needs Python. Reopen the customer job after refreshing.

## Tests

```powershell
.\.venv\Scripts\python.exe -m pip install pytest==8.4.2
.\.venv\Scripts\python.exe -m pytest -q
```

Tests use an ephemeral synthetic CA and signing key, encrypted PDFs, signed data, fresh/stale/revoked CRLs, untrusted certificates, altered bytes and exports. They do not simulate trust with a UI flag. See [docs/VALIDATION.md](docs/VALIDATION.md) for the Windows and real-document checklist.

## Build a Windows executable

On Windows, run `powershell -File .\build-windows.ps1` in a shell whose execution policy permits your reviewed local script. The script installs development dependencies, runs tests and invokes PyInstaller. Distribute the **entire** `dist\AadhaarPrintAssist` folder. Target machines then need no Python installation. A folder build avoids single-file startup extraction and is easier to inspect and update; a one-file build is not the default.

Configure the external `trust` folder beside the EXE. Keep it writable only by the administrator; ordinary operators should not be able to change trust policy. Test the package on clean Windows 10/11 machines. Build size, memory, installation privileges, antivirus behavior and physical printer compatibility are unmeasured here. Include required dependency notices and PyInstaller-collected PDFium license files with distribution. Source use requires no paid license; no claim is made that packaging or approval is already complete.

## Privacy

No telemetry, cloud uploads, database, customer log files or password files are implemented. Validation network fetching is disabled. Ordinary preview and printing keep document data in process memory. Explicit exports use a temporary staging file in the chosen output directory and remove it on normal success/failure. A crash during export can leave a `.printassist-*` file there; inspect the export folder after a crash.

Python and native libraries cannot guarantee zeroization of strings, PDFium buffers, swap or crash dumps. Clearing a session drops references and closes PDF handles; it is not certified secure erasure. Windows temporary-file ACLs inherit destination policy, so use private folders and a restricted local Windows account. BitLocker, disabling inappropriate crash dumps and OS/printer spool retention configuration are deployment concerns. Printer spools and printer storage are controlled by Windows/device policy and cannot be promised deleted by the app. Original Downloads, USB files, chosen exports, OS recent-file metadata, backups and customer queue paths remain outside in-memory cleanup.

The application is a PDF printing utility, not an Aadhaar authentication service, KYC service, official signature approval or legal-compliance certification. Establish customer consent, retention and handling procedures suitable for your shop before rollout.

## Architecture and implementation phases

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for design, folder structure, rationale and phase completion.

## Primary references

- [UIDAI e-Aadhaar FAQ: password examples and signature guidance](https://uidai.gov.in/kn/aadhaar-online-services)
- [pyHanko validation guide](https://docs.pyhanko.eu/en/v0.33.0/lib-guide/validation.html)
- [pyHanko certificate validation API](https://docs.pyhanko.eu/en/latest/api-docs/pyhanko_certvalidator.html)
- [PDFium/pypdfium2 Python API](https://pypdfium2.readthedocs.io/en/stable/python_api.html)
- [pypdfium2 licensing](https://pypi.org/project/pypdfium2/)
- [pywin32 native printer example](https://github.com/mhammond/pywin32/blob/main/win32/Demos/print_desktop.py)
