# Administrator trust setup

This installation was provisioned on 20 October 2026 with authenticated public trust material. The signer certificate is an exact DER match to the certificate independently published by UIDAI at its official 2026 download URL. Its key usages include digital signature and non-repudiation, its extended usages include Microsoft document signing and Adobe authentic documents, and its certificate policies include document signing. The root was independently downloaded from CCA’s official HTTPS publication. The two embedded intermediates were authenticated by issuer-signature verification to that root, and pyHanko’s mandatory revocation path checks passed. See `provenance.json` for hashes, publication sources and the transport details. This is not a blanket approval of certificates labeled UIDAI.

The generic verifier still has no implicit OS roots or auto-approved embedded signers. All future provisioning must follow the process below. The earliest current CRL expiry is 22 October 2026 at 11:05 UTC. Run `python refresh-trust.py` from the source installation to download and validate current public CRLs; then reopen the customer job.

## Directory layout

Create these folders under `trust/` beside `run.py` or beside the packaged EXE:

```text
trust/
  roots/                  # CA trust anchors: one PEM/DER certificate per file
  intermediates/          # intermediate CA certificates, not anchors
  crls/                   # current signed DER/PEM CRLs
  ocsps/                  # current DER/PEM OCSP responses
  signers/                # independently authenticated public UIDAI certificate
  provenance.json         # sources, hashes and provisioning checks
  approved-signers.txt     # UIDAI PDF leaf certificate SHA-256 pins
```

Root/intermediate file suffixes: `.pem`, `.cer`, `.crt`, `.der`. CRL/OCSP folders must contain only evidence files. A malformed trust file stops opening with a configuration error; malformed evidence cannot cause a green result. Do not place documentation in evidence folders. Multi-certificate PEM bundles are not supported: split them into separate files.

## Provisioning process

1. Obtain current and, where needed, historical CA chain material from authenticated official CA/CCA distribution channels. Verify CA fingerprints through an independent trusted channel. A broad operating-system/browser root store is not automatically imported.
2. Determine which leaf certificates are genuinely authorized for UIDAI e-Aadhaar PDF signing. Verify their complete certificates/fingerprints independently through UIDAI or its issuing CA, outside the customer PDF. The provisioned pin was derived from the independently published UIDAI certificate, not from trusting its display name. To add another signer, authenticate that new certificate independently.
3. Put only chosen CA trust anchors in `roots/` and any needed issuing CAs in `intermediates/`. Do not “fix” validation by promoting a customer-provided leaf certificate into a trust anchor.
4. Calculate each approved leaf certificate's SHA-256 over its DER encoding. For example:

   ```powershell
   openssl x509 -in uidai-pdf-signer.pem -noout -fingerprint -sha256
   ```

   Add the resulting 64 hex digits to `approved-signers.txt`, one per line. Colons and `#` comments are supported. This fingerprint calculation does not establish authenticity; step 2 does.
5. Obtain current signed CRLs or OCSP responses from the genuine certificate issuers using a separately controlled maintenance process. The verifier checks the signatures and applicability of this evidence, not merely the presence of a file. Put the evidence in the corresponding folders and refresh it before `nextUpdate`/freshness limits. Intermediate CAs also need applicable revocation evidence, except for trust anchors.
6. Restrict trust-folder modification with Windows ACLs to the administrator. Treat a CA or pin change as a trust-policy deployment. Close and reopen a customer job to reload policy. Keep provenance and maintenance records separately from customer reports.
7. Verify a customer-authorized known-good original against an independently trusted verifier. Missing material must result in yellow; wrong, revoked or altered signatures must never become green.

Never assume that an XML/QR key or an Aadhaar authentication TLS certificate is a PDF signing certificate. In this specific provisioning, exact DER equality between UIDAI’s official publication and the PDF signer was established, document-signing key usages/policies were inspected, and issuer trust/revocation were independently validated. An issuer string or similar expiry date alone is insufficient.

## Validation policy

`trust_roots=[]` explicitly disables default OS trust. `allow_fetching=False` prevents PDF-provided AIA/CRL/OCSP URLs from causing network requests. Embedded intermediate certificates can assist path building, but cannot become trust anchors. The signer pin adds UIDAI authorization on top of generic certificate trust.

The basic pass separates integrity and path trust from unavailable revocation evidence. A second pass uses pyHanko's `revocation_mode='require'` to require applicable evidence for end-entity and intermediate certificates. Green also requires `bottom_line`, `trusted`, `intact`, `valid`, full-file coverage or explicitly accepted archival-only updates, and an approved signer pin for every regular document signature. Full-file coverage remains reported separately; it is not conflated with whether later changes are acceptable. The first pass uses the current time. Timestamp and seed constraints are included in the library's bottom-line result.

V5 adds offline AdES historical validation with independently authenticated timestamp evidence and required archival revocation checks. See HISTORICAL.md. The `validation_basis` field identifies current-time versus trusted historical-time validation; `chain_trusted` alone never proves mandatory revocation checks. A signer-created date or operator date is not proof of existence. A historically expired signature without sufficient evidence remains indeterminate.

Strict PDF syntax remains a requirement for green. A `PdfStrictReadError` during signature reading triggers a compatibility-mode retry against unchanged input bytes so integrity and certificate results can still be inspected. That mode never promotes a result to green, regardless of configured trust, and does not repair the PDF. Cryptographic failures remain invalid. Successful compatibility parsing with no signatures remains incomplete because strict parsing failed. Generic parser errors and cryptographic failures do not trigger retries.

The app does not download trust updates automatically. Offline operation means stale or absent evidence causes yellow. The shop must plan certificate rotation and evidence refreshes; do not weaken the policy to obtain a green tick.

## Signed-version selection and printable results

The optional signed-version mode reads the signature byte ranges to select the latest signed prefix, then runs the complete normal verification on those selected bytes. Discovery alone never approves a file. Later uploaded changes are explicitly excluded from preview/printing and declared in the report/footer. The original upload remains available byte-for-byte.

The optional footer is application-generated print-copy information. Green requires a valid scoped report; a warning, unsigned document, revoked certificate, changed signed content or incomplete compatibility result cannot generate the green tick. The footer does not modify the uploaded PDF or its signature appearance. Exported raster PDFs have no embedded digital signature.

## Unbranded viewer tick

A plain green tick can be rendered in the actual visible signature-widget rectangle only when both the overall scoped report and that signature are valid. The app locates the widget page/rectangle from the parsed PDF and uses PDFium coordinate conversion, including page rotation. It does not guess a location if geometry is missing. All text branding is optional: the footer starts disabled. Existing source graphics are not erased or reinterpreted as successful validation. Exporting a viewer appearance does not give the exported raster PDF/JPG/PNG an embedded cryptographic signature. The original uploaded file remains unchanged.
