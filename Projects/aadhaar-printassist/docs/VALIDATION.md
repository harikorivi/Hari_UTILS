# Validation evidence and rollout checklist

## Automated verification in this delivery

71 core tests pass (one optional GUI test is skipped by default) using Python 3.11 in an isolated macOS environment. These tests cover:

- UIDAI's four published password examples, extra whitespace and DOB formats.
- Invalid dates, unsupported names and bad birth years.
- Opening encrypted documents and rejecting wrong passwords.
- Genuine encrypted PDF signing and verification with an ephemeral test CA.
- Green only with authenticated synthetic trust, an approved signer pin and a fresh valid CRL.
- Missing evidence, missing identity pin, embedded untrusted CA and stale evidence all stay yellow.
- Revoked certificates and altered signed bytes never turn green. Genuine archival-only incremental updates can pass document-revision checks, while changed pages and unexplained trailing bytes fail. PDF encryption MAC failures also fail verification.
- No signature versus malformed/unknown signature state.
- Two-page image/PDF rendering, portrait/landscape A4 output, and original-byte preservation.
- Exported raster PDFs contain no embedded document signature.
- Resource limits, release of job references, atomic export rollback and temporary-file removal after controlled errors.
- Malformed administrator pin configuration fails closed.
- Customer-name filename suggestions cover PDF/JPG/JPEG/PNG, original/report suffixes, multi-page images, Windows reserved names, invalid characters and Unicode length bounds. The desktop smoke check confirms that actual Save dialog suggestions use the entered name and session clearing removes it.
- Strict cross-reference syntax rejection triggers a compatibility retry against unchanged bytes. A real signed synthetic PDF reproduces a non-initial free xref entry with generation zero. Signature checks run successfully in compatibility mode, but cannot produce green; altered bytes still produce red.
- Encrypted compatibility validation uses the same original bytes and provided password without exposing the password in reports. Unknown parser errors are not retried, and compatibility failures show safe diagnostic codes without leaking raw exception messages.

These are synthetic tests of actual cryptographic operations, not proof of current UIDAI production trust or real Aadhaar template compatibility. Test keys and certificates are generated at test time, never installed as real application trust.

A separate Tk 9 / Python 3.12 desktop smoke test also passed on macOS: startup, background loading, verification result handoff, preview, field clearing, consent reset and shutdown. The generated image and A4 PDF were rendered and visually inspected using synthetic geometric content. Windows printing adapter contract tests cover normal submission and abort/handle cleanup on failure using simulated APIs; they do not exercise a real Windows driver.

To run the optional GUI test on an interactive Windows desktop:

```powershell
$env:PRINTASSIST_GUI_TEST="1"
.\.venv\Scripts\python.exe -m pytest -q tests/test_gui.py
```

## Public trust provisioning verification

On 20 October 2026, the independently downloaded UIDAI certificate exactly matched the embedded public signer certificate. All three issuer signatures were checked to CCA’s independently downloaded 2022 root. The three public CRLs had valid issuer signatures, were current, and did not revoke the corresponding certificates. pyHanko’s certificate path validation with `revocation_mode=require` passed, including through the app’s installed `Trust.load` configuration. These checks authenticate public trust material; the customer PDF must still pass the full application verification after opening.

Six additional tests cover refresh acceptance and rejection of stale, revoked, corrupt, missing or unapproved-signer evidence.

## Windows acceptance checks before shop use

1. Build on maintained Windows Python 3.11+ with Tcl/Tk. Run all tests and record actual resolved dependencies. Repeat on Windows 10 and 11 with the intended architectures and drivers.
2. Start the source application and packaged EXE on a clean standard-user machine with no Python/Adobe installed for the EXE test. Verify startup and dependency notices. No antivirus exceptions should be required without investigating a specific detection.
3. Use only customer-authorized original e-Aadhaar samples. Test the official name examples and current PDF variants against known customer details. Confirm a wrong password does not leave a previous customer's preview or verification result visible.
4. Have an administrator authenticate roots, intermediates, UIDAI PDF signer fingerprints and fresh issuer evidence. Compare app results with an independently trusted verifier. Record certificate identities and policy provenance outside customer reports. Do not alter trust from a customer's embedded certificate alone.
5. With missing/revoked/stale certificates or evidence and a tampered copy, confirm no green result. Compare document SHA-256 values with a separate tool. Verify a saved original is byte-identical.
6. Check scrolling, small-screen layout, navigation, queue transitions and consent reset. Verify the app's result is outside the document, with no fake signature tick. Confirm exported copies are accurately labeled in the workflow.
7. Print to an installed A4 physical printer in both orientations and at each DPI/scale. Test two pages, printer offline, paper out, cancellation and driver failures. Confirm copies are readable, QR codes scan where appropriate and content is not cropped. Physical print completion is not monitored by the app.
8. Test explicit export cancellation, overwrite confirmation, protected destinations, disk full and crash recovery. Inspect `.printassist-*` staging files in chosen export folders after abnormal termination. Never store exported customer copies in public or cloud-synced folders by default.
9. Configure OS and printer spool retention, local-account ACLs, encryption and an appropriate retention process. Verify the original customer file, Downloads/USB paths and exports are deleted by the shop when no longer needed; the app does not delete user source files.
10. Measure cold start, idle/peak RAM, CPU and export/print time on the actual 4 GB PC. Verify quality and limits on large PDFs. Set a measured acceptance target before promising performance or small installation size.

No daily-use production certification or Windows executable is asserted by this delivery. Complete and record these checks before rollout.

## Revision handling and print-copy footer update

Additional tests verify a real signed PDF with a later DSS archival update, rejected page replacement, unassessed revisions, form edits requiring review, signed-version recovery with separate source/selected hashes, preservation of the complete original upload, and failure to recover trust for tampered signed contents.

Footer tests cover all four actual statuses, absence of a green tick for warning/invalid/unsigned results, preserving all original image pixels, and PDF/image exports that still contain no embedded signature. Synthetic green-footer and A4 PDF output were rendered and visually inspected. Two desktop smoke checks passed: normal preview/export naming, and modified-upload rejection followed by explicit signed-version recovery, successful scoped verification, footer toggling, and clearing the selection for the next customer.

The Android reference was inspected through its Google Play description only; its cryptographic engine, exported files and trust decisions were not independently audited. Adobe documents valid signatures with permitted changes. This implementation only accepts assessed unchanged/archival-only updates, and keeps compatibility-mode results incomplete.

Four tests additionally cover visible-widget location, unbranded tick placement on normal/rotated pages, absence of new ticks for an untrusted source, original-byte preservation, and opening the supplied reference page dimensions while keeping the render pixel budget. The reference file’s origin is not inferred from the lack of an embedded signature.
