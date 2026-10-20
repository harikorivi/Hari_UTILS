# V5 historical validation and V4 migration

V5 integrates the supplied V4 prototype's goal into the existing desktop application. It retains customer-name filenames, PDF/PNG/JPEG export, preview, Windows printing, original-byte preservation and optional unbranded signature-area tick. Run `run.py`; `app.py` is a compatibility entry point. No Adobe license is required. Windows EXE/hardware printing are not qualified by the macOS tests.

## What changed

- Authenticated the missing CCA India 2014 → (n)Code Solutions CA 2014 → UIDAI 05 (2021) issuer signatures. The signer certificate exactly matches UIDAI's published certificate. Public artifacts, source URLs and SHA-256 hashes are in `trust/historical-provenance.json`.
- Added an offline pyHanko AdES historical validation path. It requires validated timestamp evidence, an explicit trusted certificate path, required revocation evidence and an approved signer pin. It never treats an operator date or claimed signing date as independent proof.
- Distinguishes issuer linkage from certificate validity and revocation at a proven historical time.
- Inspects signature timestamps, document timestamps, DSS and authenticated CRL dates. Administrator evidence folders can be selected in the UI; selecting a different folder resets the active result and requires unlocking again.
- Current revocation evidence remains in `crls/` and `ocsps/`; historical evidence belongs in `historical-crls/` and `historical-ocsps/`. Explicit roots, intermediates and approved signer pins remain mandatory. Embedded roots are not automatically trusted. No network requests occur while processing customer documents.

## Actual supplied signed Nagamani PDF

Locally tested on 20 October 2026 without changing its bytes:

| Check | Result |
|---|---|
| Open with provided name and birth year | Passed |
| Signature cryptographic integrity | Passed |
| Signed coverage | Entire file |
| Exact official UIDAI signer pin | Passed |
| Issuer signatures to configured CCA India 2014 root | Authenticated |
| Signer certificate expiry | 27 February 2024 |
| RFC 3161 signature timestamps | None |
| PDF document timestamps / DSS | None / absent |
| Independent historical signing time | Not established |
| Revocation at independently proven signing time | Not established |
| Overall | INDETERMINATE; no added green tick |

The public final issuer CRL is signed correctly but was issued on 5 March 2024, after the leaf certificate expired. Its empty revoked list is not proof of nonrevocation at an independently trusted signing time. The app does not silently backdate validation or turn this into a pass.

This resolves the missing issuer-chain material, but cannot complete historical verification of this particular file using evidence it does not contain. A freshly downloaded signed e-Aadhaar may permit current-time verification if its signer and fresh evidence satisfy the configured policy. Existing historical records with independently authenticated preservation timestamps may require a dedicated external evidence adapter; arbitrary date entry is not supported as proof.

## Green appearance

The tick is a viewer-generated print appearance drawn only when all implemented scoped verification checks pass and the actual signature widget position is known. It is not an Adobe-generated graphic or a new cryptographic signature. Branding/footer is optional and off by default. PDF/JPG/PNG print copies do not retain the original signature; saving the original preserves the upload exactly. Missing historical evidence cannot be corrected by changing the renderer.

## Validation

Synthetic archived PKI tests cover a successful expired-signer historical validation with trusted signature and document timestamps, preserved certificates/CRLs, and revocation occurring after proven signing time. Negative cases cover absent trusted time, absent root trust, missing archival revocation evidence, revocation before signing, and changed signed bytes. Synthetic keys and documents are generated during tests, not shipped as UIDAI examples.

References: [CCA FAQ](https://www.cca.gov.in/faq.html), [CCA roots](https://www.cca.gov.in/root_certificate.html), [nCode repository](https://ncodesolutions.com/repository.php), [pyHanko validation](https://docs.pyhanko.eu/en/latest/api-docs/pyhanko.sign.validation.html).
