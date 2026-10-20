# Technical feasibility and architecture decision

Research date: 20 October 2026. Decision applies to the Phase 2 development build; no production qualification is implied.

## Reference application review

The [public Play listing](https://play.google.com/store/apps/details?id=com.minhackerz.pdfsigncheck_app&hl=en) was inspected, including its screenshots and visible reviews. Its home screenshot offers signature verification; the next screenshot shows a verification report, signer details, document preview and save/share actions. The listing advertises CMS/PAdES checks, configured roots, revocation lookups and PDF utilities. It describes some cryptographic tasks as cloud processing. Visible reviews praise usability and Aadhaar handling; one reports a timeout, which the developer says was fixed.

These are vendor claims and user observations, not independent cryptographic test results. No source code or network implementation was inspected. The screenshots do not establish whether a displayed stamp was originally embedded or viewer-generated. We have not reproduced its branding, artwork or verification algorithm. Our intended later workflow is select → authorized unlock → independently evaluate integrity/trust/revocation → report → preview → explicit print/export.

## Options evaluated

| Option | Useful capability | Limitation / licensing | Decision |
|---|---|---|---|
| Android PdfRenderer | Platform page renderer, avoids shipping another renderer | Password load parameters are API 35-era functionality, unavailable across the Android 9 baseline; it is not a CMS/PKIX verifier | Potential second renderer for supported devices |
| PDFium Android bindings | Native encrypted-document rendering and annotations | Native ABI footprint and update/16 KB-page compatibility need validation; rendering is not signature verification | Candidate for later renderer comparison; old 1.9.0 bindings are not selected |
| PdfBox-Android 2.0.27.0 | Android PDF parser, password opening and rendering; future access to signature dictionaries | Apache-2.0. Based on older upstream PDFBox 2.0.27; no assurance all later upstream fixes are backported. Optional JPX decoder absent | Selected for scoped Phase 2 prototype; production maintenance review required |
| Bouncy Castle | CMS, ASN.1, CRL, OCSP and timestamp building blocks | Permissive BC/MIT terms; does not by itself perform PDF revision analysis, rendering or complete historical validation | Candidate for dedicated verification implementation, not claimed implemented |
| iText | PDF signature-related APIs | AGPLv3/commercial dual licensing; exact Android compatibility and distribution obligations must be reviewed | Not included |

Sources: [PdfRenderer](https://developer.android.com/reference/android/graphics/pdf/PdfRenderer), [LoadParams](https://developer.android.com/reference/android/graphics/pdf/LoadParams.Builder), [PDFium binding source](https://github.com/johngray1965/PdfiumAndroid), [PdfBox-Android README/license](https://github.com/TomRoush/PdfBox-Android), [Bouncy Castle documentation](https://www.bouncycastle.org/documentation/documentation-java/), [BC license](https://github.com/bcgit/bc-java/blob/main/LICENSE.html), [iText license](https://itextpdf.com/how-buy/AGPLv3-license).

## Implementation boundaries

Compose owns transient input and presentation. `AadhaarPassword` contains pure password rules. `PrintAssistViewModel` serializes PDF work on a dedicated background executor and ignores stale results after session clearing. `PdfSession` owns the parsed document and original byte buffer. It never saves a decrypted PDF and wipes its owned source buffer after closing. Password Strings and library-internal copies remain subject to managed-runtime garbage collection; secure memory erasure is not guaranteed.

A single module and constructor-created session avoid unnecessary DI/framework overhead. No database, account, analytics SDK, remote service or trust-folder picker is needed for Phase 2. Future verification must operate on original bytes before any export. The desktop V5 trust findings are useful evidence but the Python validator is not assumed to run on Android.

## Verification contract for later phases

Green will require intact signed bytes, acceptable revision coverage, an authenticated certificate path, required valid revocation evidence, supported algorithm/policy constraints, and appropriate trusted-time proof for historical validation. Unknown evidence remains indeterminate. General PDF mode must use an explicitly defined general trust policy; an Aadhaar signer pin is not appropriate for every signed PDF.

The historical Nagamani desktop case had cryptographic integrity and authenticated issuer linkage but no trusted timestamp. The Android implementation cannot repair missing historical evidence by copying a certificate or changing rendering engines. CRLs published after expiry and an untrusted signing date are not automatically proof of historical validity.

Widget appearance tests must cover /AP /N, /Rect, /BBox and transformations, page rotation, clipping, print/hidden flags and missing appearances. Viewer-generated status stays separate from the original artwork. No new checkmark is inserted into the document.
