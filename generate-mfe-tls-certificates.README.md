# Enhanced MFE TLS Certificate Generation Script — Usage and Best Practices

## Overview

This script creates a clean, industry-standard PKI setup for Java/Helidon:
- CA (root) certificate
- Server key/cert + PKCS12 (`server.p12`) with alias `1` (for max Java/Helidon compatibility)
- Java truststore (`client-truststore.jks`) that trusts the **CA**, not just the leaf cert (futureproofing client trust)
- Instructions, security tips, testing commands, and sanity checks built in

---

## Why these changes?

### 1. **PKCS12 alias: "1" for Interop**
- Java and Helidon (especially with OpenSSL-generated keystores) work best when PKCS12 has alias "1".
- Other aliases (like "server") often break certificate lookup unless explicitly configured. Setting alias "1" is the safest cross-tool default.

### 2. **JKS Truststore Imports the CA**
- Importing the CA root, rather than the leaf server certificate, is best practice.  
  - Allows the server cert to be rotated/renewed without forcing all clients to update their truststores.
  - Is required for compliant PKIX validation (used by most Java, enterprise, and browser stacks).

---

## Usage

1. **Run the script:**
   ```sh
   ./generate-mfe-tls-certificates.sh --force
   ```
   Adjust options (`-p mypassword`, `-d /tmp/certs`, etc.) as needed.

2. **After generation, you have:**
   - `server.p12`  (for Helidon, alias is "1")
   - `client-truststore.jks` (for Java clients, trust CA)
   - `ca-cert.pem`, `server.crt`, `server.key` (for reference/advanced users)

3. **Configure your Java Helidon server:**
   - Use the `server.p12`
   - Set `alias: "1"` in the `application.yaml` keystore config
   - Example:
     ```yaml
     server:
       tls:
         enabled: true
         private-key:
           keystore:
             passphrase: "changeit"
             alias: "1"
             resource:
               path: "server.p12"
     ```

4. **Configure any Java client:**
   - Use `client-truststore.jks` as the truststore:
     ```sh
     java -Djavax.net.ssl.trustStore=client-truststore.jks -Djavax.net.ssl.trustStorePassword=changeit ProductionTLSClient https://localhost:9443
     ```
   - No handshake or hostname/CN/SAN errors if connecting to `localhost:9443`

---

## FAQ

**Q: Why not import the server certificate directly in the JKS truststore?**  
A: If the server certificate is rotated/expired, every client must be re-provisioned. Using the CA allows independent server key/cert rotation with no client changes.

**Q: Why alias "1"?**  
A: Some Java code and tooling (especially OpenSSL PKCS12 exports) default to this alias. Java KeyStore APIs look for an alias, and "1" is the most reliable default. Run `keytool -list -keystore server.p12 -storetype PKCS12 -storepass changeit` to confirm.

**Q: What about mutual TLS (client certs)?**  
A: You can add additional sections to this script to generate and sign client certs if mTLS is needed — contact your security team or ask for an extension.

**Q: Can I use these certs with non-Java clients and servers?**  
A: Yes — all of these are standard PEM / PKCS12 / JKS artifacts.

---

## Security Tips

- Use strong keystore/truststore passwords outside dev/test.
- **Keep `ca-key.pem` and `server.key` private!**
- Rotate certificates before expiry (90 days server, 1 year CA by default).
- Never check private keys or keystore passwords into VCS.

---

## What's New In This Version

- PKCS12 (`server.p12`) always has alias "1"
- Sanity check for alias in PKCS12 (warns user if not "1")
- JKS truststore always imports the CA, not the server cert
- Improved output, step-by-step validation, and usage help
- Security reminders and next-step actions printed at the end

---

**The script is now ready for secure DevOps and production Java/Helidon deployments — use it as your trusted baseline.**
