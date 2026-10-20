# Administrator guide

Normal customers provide only the PDF and authorized name/year or password. They do not supply certificates or a trust directory.

Public CCA root certificates, intermediate certificates, selected UIDAI signer fingerprints and CRLs are bundled in `app/src/main/assets/trust`. Provenance files record their sources. Trust anchors and Aadhaar signer pins change only through a reviewed application update. A certificate embedded in a PDF or imported through settings never becomes a root automatically.

CRLs expire. The bundled 2026 evidence has limited validity; the earliest current CRL nextUpdate is 22 October 2026. After expiration, affected checks become UNKNOWN until fresh evidence is provided. The application does not silently bypass this check and does not download from document-supplied URLs.

To update offline evidence:

1. The shop administrator obtains the public CRL or OCSP response from the issuing CA's official service, or a public intermediate certificate needed for the existing chain.
2. Open **Administrator settings → Import public verification evidence**.
3. Select one DER/PEM certificate or CRL, or a DER OCSP response. Each file must be under 5 MB; imported evidence totals at most 20 MB.
4. Reopen the PDF to verify with the imported evidence. Imported records still have to pass issuer, signature, time and authorization checks.

Do not select the customer's Aadhaar PDF, a private key, a keystore or a personal folder here. Importing an intermediate from an unknown authority does not establish trust. General PDF mode uses the same finite CCA trust store but does not enforce the UIDAI signer pin.

Use **Remove imported evidence** to delete administrator imports; bundled roots remain. Imports are kept only in the application's private storage and excluded from backup. Uninstalling the app removes them.

Historical checks need actual archived evidence and a trustworthy timestamp. A currently downloaded CRL or manually chosen date is not automatically evidence of status years ago. Documents lacking this evidence can still be previewed and printed with permission, while their verification result remains indeterminate.
