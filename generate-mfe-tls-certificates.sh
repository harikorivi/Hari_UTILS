#!/bin/bash

# ========================================================================================
# Media Flow Engine TLS Certificate Generation Script
# ========================================================================================
#
# This script generates all necessary TLS certificates for the Media Flow Engine (MFE)
# including CA, server, and client certificates with proper truststores and keystores.
#
# Generated Files:
#   - ca-cert.pem          : CA certificate (PEM format)
#   - ca-key.pem           : CA private key (keep secure!)
#   - server.crt           : Server certificate (PEM format)
#   - server.key           : Server private key (keep secure!)
#   - server.p12           : Server keystore (PKCS12 format)
#   - client-truststore.jks: Client truststore (JKS format)
#   - helidon-ca-cert.pem  : CA certificate for Postman/testing
#
# Usage: ./generate-mfe-tls-certificates.sh [options]
#
# ========================================================================================

set -e  # Exit on any error

# ========================================================================================
# CONFIGURATION SECTION
# ========================================================================================

# Certificate validity periods (in days)
CA_VALIDITY=365
SERVER_VALIDITY=90

# Default certificate parameters
COUNTRY="US"
STATE="CA"
CITY="Redwood City"
ORGANIZATION="Oracle"
ORG_UNIT="MFE"
COMMON_NAME_CA="MFE-CA"
COMMON_NAME_SERVER="localhost"

# Default passwords (can be overridden via command line)
KEYSTORE_PASSWORD="${KEYSTORE_PASSWORD:-changeit}"
TRUSTSTORE_PASSWORD="${TRUSTSTORE_PASSWORD:-changeit}"

# Output directory (can be overridden)
OUTPUT_DIR="${OUTPUT_DIR:-$(pwd)}"

# ========================================================================================
# FUNCTIONS
# ========================================================================================

print_header() {
    echo "================================================================================="
    echo "$1"
    echo "================================================================================="
}

print_step() {
    echo ""
    echo "📋 STEP $1: $2"
    echo "---------------------------------------------------------------------------------"
}

show_help() {
    cat << 'EOF'
Media Flow Engine TLS Certificate Generation Script

DESCRIPTION:
    This script generates a complete TLS certificate chain for the Media Flow Engine,
    including Certificate Authority (CA), server certificates, and client truststores.

USAGE:
    ./generate-mfe-tls-certificates.sh [OPTIONS]

OPTIONS:
    -h, --help              Show this help message
    -d, --output-dir DIR    Output directory for certificates (default: current directory)
    -p, --password PASS     Password for keystores and truststores (default: changeit)
    --ca-validity DAYS      CA certificate validity in days (default: 365)
    --server-validity DAYS  Server certificate validity in days (default: 90)
    --country CODE          Country code for certificates (default: US)
    --state STATE           State for certificates (default: CA)
    --city CITY             City for certificates (default: Redwood City)
    --org ORG               Organization for certificates (default: Oracle)
    --org-unit UNIT         Organizational unit (default: MFE)
    --ca-cn NAME            CA common name (default: MFE-CA)
    --server-cn NAME        Server common name (default: localhost)
    --force                 Overwrite existing certificates without prompting

GENERATED FILES:
    ca-cert.pem              CA certificate (PEM format)
    ca-key.pem               CA private key (keep secure!)
    server.crt               Server certificate (PEM format)
    server.key               Server private key (keep secure!)
    server.p12               Server keystore (PKCS12 format for MFE)
    client-truststore.jks    Client truststore (JKS format for Java clients)
    helidon-ca-cert.pem      CA certificate copy for Postman/testing

CERTIFICATE USAGE GUIDE:

1. SERVER CERTIFICATES (Required for MFE):
   Location: media-flow-engine/target/.../bin/
   Files: server.p12, ca-cert.pem
   Purpose: Server authentication for HTTPS connections
   Configuration: Set in application.yaml and environment variables

2. CLIENT TRUSTSTORE (Required for Java clients):
   Location: Same directory as client applications
   File: client-truststore.jks
   Purpose: Trust server certificates during TLS handshake
   Usage: -Djavax.net.ssl.trustStore=client-truststore.jks

3. CA CERTIFICATE (For testing/debugging):
   Location: Available in multiple locations for testing
   File: helidon-ca-cert.pem or ca-cert.pem
   Purpose: Import into browsers/Postman for certificate trust
   Usage: Upload to Postman certificate settings

4. PRIVATE KEYS (Keep Secure!):
   Files: ca-key.pem, server.key
   Purpose: Certificate signing and server authentication
   Storage: Secure location with restricted access
   Never share these files!

ENVIRONMENT SETUP:
    After generating certificates, set these environment variables:

    export TLS_KEYSTORE_CLASSPATH_PATH="server.p12"
    export TLS_KEYSTORE_PASSPHRASE="changeit"
    export TLS_KEY_ALIAS="server"
    export TLS_KEYSTORE_TYPE="PKCS12"

MFE CONFIGURATION:
    The server.p12 keystore should be placed in:
    media-flow-engine/target/media-flow-engine-8.3.0.0-SNAPSHOT-MFE/ORACLE_HOME/bin/

    Update application.yaml:
    server:
      tls:
        enabled: true
        private-key:
          keystore:
            type: "PKCS12"
            alias: "server"
            passphrase: "changeit"
            resource:
              path: "server.p12"

TESTING:
    1. Start MFE server with TLS enabled
    2. Test with Java client:
       java -Djavax.net.ssl.trustStore=client-truststore.jks \
            -Djavax.net.ssl.trustStorePassword=changeit \
            ProductionTLSClient https://localhost:9443

    3. Test with Postman:
       - Settings → Certificates → Add Certificate
       - Host: localhost:9443
       - CRT file: helidon-ca-cert.pem

SECURITY NOTES:
    - Keep private keys (ca-key.pem, server.key) secure and never share them
    - Use strong passwords in production
    - Rotate certificates before expiration
    - Store certificates in secure locations with proper permissions

EXAMPLES:
    # Generate with default settings
    ./generate-mfe-tls-certificates.sh

    # Generate with custom output directory and password
    ./generate-mfe-tls-certificates.sh -d /path/to/certs -p mypassword

    # Generate with custom validity periods
    ./generate-mfe-tls-certificates.sh --ca-validity 730 --server-validity 365

EOF
}

check_dependencies() {
    local missing_deps=()

    if ! command -v openssl &> /dev/null; then
        missing_deps+=("openssl")
    fi

    if ! command -v keytool &> /dev/null; then
        missing_deps+=("keytool (from JDK)")
    fi

    if [ ${#missing_deps[@]} -ne 0 ]; then
        echo "❌ ERROR: Missing required dependencies:"
        printf '   - %s\n' "${missing_deps[@]}"
        echo ""
        echo "Please install the missing dependencies and try again."
        exit 1
    fi
}

generate_ca_certificate() {
    print_step "1" "Generating Certificate Authority (CA) Certificate"

    # Generate CA private key
    echo "   Generating CA private key..."
    openssl genrsa -out "$OUTPUT_DIR/ca-key.pem" 2048

    # Create CA certificate with robust v3_ca (always validated by openssl _and_ Java)
    echo "   Creating CA certificate..."
    cat > "$OUTPUT_DIR/ca-cert.conf" << EOF
[req]
distinguished_name = req_distinguished_name
x509_extensions = v3_ca
prompt = no

[req_distinguished_name]
C = $COUNTRY
ST = $STATE
L = $CITY
O = $ORGANIZATION
OU = $ORG_UNIT
CN = $COMMON_NAME_CA

[v3_ca]
subjectKeyIdentifier = hash
authorityKeyIdentifier = keyid:always,issuer:always
basicConstraints = critical,CA:true,pathlen:2
keyUsage = critical,keyCertSign,cRLSign
EOF

    openssl req -new -x509 -days $CA_VALIDITY \
        -key "$OUTPUT_DIR/ca-key.pem" \
        -out "$OUTPUT_DIR/ca-cert.pem" \
        -config "$OUTPUT_DIR/ca-cert.conf" \
        -extensions v3_ca \
        -sha256

    # Verification: Show Basic Constraints are present and CA:TRUE
    echo "   [INFO] CA cert X.509 Extensions:"
    openssl x509 -in "$OUTPUT_DIR/ca-cert.pem" -text -noout | grep -A8 "X509v3 Basic Constraints"

    # Clean up config file
    rm -f "$OUTPUT_DIR/ca-cert.conf"

    echo "   ✅ CA certificate created: $OUTPUT_DIR/ca-cert.pem (with CA:TRUE!)"
    echo "   ✅ CA private key created: $OUTPUT_DIR/ca-key.pem"
}

generate_server_certificate() {
    print_step "2" "Generating Server Certificate"

    # Prompt for SAN DNS/IP entries
    SAN_LIST="DNS.1=localhost"
    SAN_INDEX=2

    # Option: Prepopulate local IP(s)
    LOCAL_IPS=""
    if command -v hostname &>/dev/null; then
      LOCAL_IPS=$(hostname -I 2>/dev/null || hostname -i 2>/dev/null || echo "")
    fi

    echo ""
    echo "   Enter comma-separated DNS/IP for the server certificate Subject Alternative Name (SAN)."
    echo "   - At minimum, always include the public IP or hostname used by external clients!"
    echo "   - Example: DNS.1=localhost,IP.1=127.0.0.1,IP.2=10.0.0.10,DNS.2=yourserver.internal"
    if [[ -n "$LOCAL_IPS" ]]; then
      echo "   (Detected local IPs for reference: $LOCAL_IPS)"
    fi
    read -p "   San entries [default: DNS.1=localhost,IP.1=127.0.0.1]: " USER_SAN
    if [[ -z "$USER_SAN" ]]; then
      USER_SAN="DNS.1=localhost,IP.1=127.0.0.1"
    fi
    # sanitize/parse list into config lines
    IFS=',' read -ra SAN_ARRAY <<< "$USER_SAN"
    SAN_CONFIG=""
    for SAN in "${SAN_ARRAY[@]}"; do
      SAN=$(echo "$SAN" | xargs)
      [[ -n "$SAN" ]] && SAN_CONFIG+="    $SAN"$'\n'
    done

    # Generate server private key
    echo "   Generating server private key..."
    openssl genrsa -out "$OUTPUT_DIR/server.key" 2048

    # Create config with user-supplied SANs
    echo "   Creating certificate signing request..."
    cat > "$OUTPUT_DIR/server-cert.conf" << EOF
[req]
distinguished_name = req_distinguished_name
req_extensions = v3_req
prompt = no

[req_distinguished_name]
C = $COUNTRY
ST = $STATE
L = $CITY
O = $ORGANIZATION
OU = $ORG_UNIT
CN = $COMMON_NAME_SERVER

[v3_req]
basicConstraints = CA:FALSE
keyUsage = nonRepudiation,digitalSignature,keyEncipherment
extendedKeyUsage = serverAuth
subjectKeyIdentifier = hash
subjectAltName = @alt_names

[alt_names]
$SAN_CONFIG
EOF

    openssl req -new \
        -key "$OUTPUT_DIR/server.key" \
        -out "$OUTPUT_DIR/server.csr" \
        -config "$OUTPUT_DIR/server-cert.conf"

    # Sign server certificate with CA
    echo "   Signing server certificate with CA..."
    openssl x509 -req -days $SERVER_VALIDITY \
        -in "$OUTPUT_DIR/server.csr" \
        -CA "$OUTPUT_DIR/ca-cert.pem" \
        -CAkey "$OUTPUT_DIR/ca-key.pem" \
        -CAcreateserial \
        -out "$OUTPUT_DIR/server.crt" \
        -extfile "$OUTPUT_DIR/server-cert.conf" \
        -extensions v3_req \
        -sha256

    # Clean up temporary files
    rm -f "$OUTPUT_DIR/server-cert.conf" "$OUTPUT_DIR/server.csr"

    echo "   ✅ Server certificate created: $OUTPUT_DIR/server.crt"
    echo "   ✅ Server private key created: $OUTPUT_DIR/server.key"
}

create_server_keystore() {
    print_step "3" "Creating Server Keystore (PKCS12)"

    echo "   Creating PKCS12 keystore for MFE server..."

    # Create PKCS12 keystore with server certificate and private key
    openssl pkcs12 -export \
        -in "$OUTPUT_DIR/server.crt" \
        -inkey "$OUTPUT_DIR/server.key" \
        -out "$OUTPUT_DIR/server.p12" \
        -name 1 \
        -passout pass:$KEYSTORE_PASSWORD \
        -certfile "$OUTPUT_DIR/ca-cert.pem"

    # Verify PKCS12 alias
    local alias_out
    alias_out=$(keytool -list -keystore "$OUTPUT_DIR/server.p12" -storepass $KEYSTORE_PASSWORD -storetype PKCS12 2>/dev/null | grep "Alias name" || true)
    echo "   Aliases in PKCS12: $alias_out"
    if [[ "$alias_out" != *"Alias name: 1"* ]]; then
        echo "   ⚠️  WARNING: PKCS12 alias is not \"1\". Please configure your application.yaml/server config to match or regenerate using alias \"1\" for best compatibility."
    else
        echo "   ✅ PKCS12 keystore alias is '1' (recommended for Helidon/Java interop)."
    fi

    echo "   ✅ Server keystore created: $OUTPUT_DIR/server.p12"
    echo "      Alias: 1"
    echo "      Password: $KEYSTORE_PASSWORD"
}

create_client_truststore() {
    print_step "4" "Creating Client Truststore (JKS)"

    echo "   Creating JKS truststore for Java clients..."

    # Remove truststore if it exists and --force was given (for true idempotency)
    if [[ -f "$OUTPUT_DIR/client-truststore.jks" && -n "$FORCE_OVERWRITE" ]]; then
      echo "   Removing existing client-truststore.jks (--force active)..."
      rm -f "$OUTPUT_DIR/client-truststore.jks"
    fi

    # Import CA cert as trustedCertEntry in JKS truststore (best for real Java clients!)
    keytool -importcert \
        -file "$OUTPUT_DIR/ca-cert.pem" \
        -alias mfe-rootca \
        -keystore "$OUTPUT_DIR/client-truststore.jks" \
        -storepass $TRUSTSTORE_PASSWORD \
        -noprompt

    echo "   ✅ Client truststore created: $OUTPUT_DIR/client-truststore.jks"
    echo "      Contains: CA certificate (mfe-rootca alias, trustedCertEntry)"
    echo "      Password: $TRUSTSTORE_PASSWORD"
}

create_postman_certificate() {
    print_step "5" "Creating Postman/Test Certificate"

    echo "   Creating CA certificate copy for Postman testing..."

    cp "$OUTPUT_DIR/ca-cert.pem" "$OUTPUT_DIR/helidon-ca-cert.pem"

    echo "   ✅ Postman certificate created: $OUTPUT_DIR/helidon-ca-cert.pem"
}

validate_certificates() {
    print_step "6" "Validating Generated Certificates"

    echo "   Validating CA certificate..."
    openssl x509 -in "$OUTPUT_DIR/ca-cert.pem" -text -noout | grep -E "(Subject|Issuer|Not Before|Not After)" | sed 's/^/      /'

    echo ""
    echo "   Validating server certificate..."
    openssl x509 -in "$OUTPUT_DIR/server.crt" -text -noout | grep -E "(Subject|Issuer|Not Before|Not After)" | sed 's/^/      /'

    echo ""
    echo "   Validating server keystore..."
    keytool -list -keystore "$OUTPUT_DIR/server.p12" -storepass $KEYSTORE_PASSWORD -v | grep -E "(Alias name|Entry type)" | sed 's/^/      /'

    echo ""
    echo "   Validating client truststore..."
    keytool -list -keystore "$OUTPUT_DIR/client-truststore.jks" -storepass $TRUSTSTORE_PASSWORD | grep -E "(mfe-server|Certificate)" | sed 's/^/      /'

    echo ""
    echo "   ✅ All certificates validated successfully!"
}

show_summary() {
    print_header "CERTIFICATE GENERATION COMPLETED SUCCESSFULLY"

    echo ""
    echo "📁 GENERATED FILES:"
    echo "   $OUTPUT_DIR/ca-cert.pem              - CA certificate (PEM)"
    echo "   $OUTPUT_DIR/ca-key.pem               - CA private key (SECURE!)"
    echo "   $OUTPUT_DIR/server.crt               - Server certificate (PEM)"
    echo "   $OUTPUT_DIR/server.key               - Server private key (SECURE!)"
    echo "   $OUTPUT_DIR/server.p12               - Server keystore (PKCS12)"
    echo "   $OUTPUT_DIR/client-truststore.jks    - Client truststore (JKS)"
    echo "   $OUTPUT_DIR/helidon-ca-cert.pem      - CA cert for Postman"
    echo ""

    echo "🔐 CERTIFICATE PASSWORDS:"
    echo "   Keystore password: $KEYSTORE_PASSWORD"
    echo "   Truststore password: $TRUSTSTORE_PASSWORD"
    echo ""

    echo "📍 FILE LOCATIONS:"
    echo ""
    echo "   SERVER FILES (for MFE):"
    echo "   ├── Copy to: media-flow-engine/target/.../bin/"
    echo "   ├── server.p12 (keystore)"
    echo "   └── ca-cert.pem (CA certificate)"
    echo ""
    echo "   CLIENT FILES (for Java applications):"
    echo "   ├── Location: Application directory"
    echo "   └── client-truststore.jks"
    echo ""
    echo "   TEST FILES (for Postman/browser testing):"
    echo "   ├── Location: Any accessible directory"
    echo "   └── helidon-ca-cert.pem"
    echo ""
    echo "   SECURE FILES (keep private!):"
    echo "   ├── ca-key.pem (CA private key)"
    echo "   └── server.key (server private key)"
    echo ""

    echo "🚀 NEXT STEPS:"
    echo "   1. Copy server.p12 to MFE bin directory"
    echo "   2. Update application.yaml with TLS configuration"
    echo "   3. Set environment variables (TLS_KEYSTORE_*)"
    echo "   4. Start MFE server and test connections"
    echo ""

    echo "🧪 TESTING COMMANDS:"
    echo "   # Test with Java client:"
    echo "   java -Djavax.net.ssl.trustStore=client-truststore.jks \\"
    echo "        -Djavax.net.ssl.trustStorePassword=$TRUSTSTORE_PASSWORD \\"
    echo "        ProductionTLSClient https://localhost:9443"
    echo ""
    echo "   # Test with curl:"
    echo "   curl -k https://localhost:9443"
    echo ""
    echo "   # Test with Postman:"
    echo "   - Import helidon-ca-cert.pem in Postman Settings → Certificates"
    echo "   - Make request to https://localhost:9443"
    echo ""

    echo "⚠️  SECURITY REMINDERS:"
    echo "   • Keep private keys (ca-key.pem, server.key) secure!"
    echo "   • Never share private keys or commit them to version control"
    echo "   • Use strong passwords in production environments"
    echo "   • Rotate certificates before they expire"
    echo ""
}

# ========================================================================================
# MAIN SCRIPT
# ========================================================================================

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -h|--help)
            show_help
            exit 0
            ;;
        -d|--output-dir)
            OUTPUT_DIR="$2"
            shift 2
            ;;
        -p|--password)
            KEYSTORE_PASSWORD="$2"
            TRUSTSTORE_PASSWORD="$2"
            shift 2
            ;;
        --ca-validity)
            CA_VALIDITY="$2"
            shift 2
            ;;
        --server-validity)
            SERVER_VALIDITY="$2"
            shift 2
            ;;
        --country)
            COUNTRY="$2"
            shift 2
            ;;
        --state)
            STATE="$2"
            shift 2
            ;;
        --city)
            CITY="$2"
            shift 2
            ;;
        --org)
            ORGANIZATION="$2"
            shift 2
            ;;
        --org-unit)
            ORG_UNIT="$2"
            shift 2
            ;;
        --ca-cn)
            COMMON_NAME_CA="$2"
            shift 2
            ;;
        --server-cn)
            COMMON_NAME_SERVER="$2"
            shift 2
            ;;
        --force)
            FORCE_OVERWRITE=1
            shift
            ;;
        *)
            echo "❌ Unknown option: $1"
            echo "Use -h or --help for usage information"
            exit 1
            ;;
    esac
done

# Create output directory if it doesn't exist
mkdir -p "$OUTPUT_DIR"

# Backup then Check for existing certificates (modern devops safety)
existing_files=()
backup_dir=""
if [[ -n "$FORCE_OVERWRITE" ]]; then
    timestamp=$(date +"%Y%m%d_%H%M%S")
    backup_dir="$OUTPUT_DIR/.certificates_backup_$timestamp"
    mkdir -p "$backup_dir"
fi
for file in ca-cert.pem ca-key.pem server.crt server.key server.p12 client-truststore.jks; do
    if [[ -f "$OUTPUT_DIR/$file" ]]; then
        existing_files+=("$file")
        if [[ -n "$FORCE_OVERWRITE" ]]; then
            echo "   Backing up $file to $backup_dir/"
            cp "$OUTPUT_DIR/$file" "$backup_dir/"
        fi
    fi
done

if [[ ${#existing_files[@]} -gt 0 && -z "$FORCE_OVERWRITE" ]]; then
    echo "⚠️  WARNING: The following certificate files already exist:"
    printf '   - %s\n' "${existing_files[@]}"
    echo ""
    echo "Use --force to overwrite existing certificates, or specify a different output directory with -d."
    exit 1
fi

if [[ ${#existing_files[@]} -gt 0 && -n "$FORCE_OVERWRITE" ]]; then
    echo "   A backup of existing cert files was saved to $backup_dir for safety."
fi

# Check dependencies
check_dependencies

# Main execution
print_header "MEDIA FLOW ENGINE TLS CERTIFICATE GENERATION"
echo "Output Directory: $OUTPUT_DIR"
echo "CA Validity: $CA_VALIDITY days"
echo "Server Validity: $SERVER_VALIDITY days"
echo "Keystore Password: $KEYSTORE_PASSWORD"
echo ""

generate_ca_certificate
generate_server_certificate
create_server_keystore
create_client_truststore
create_postman_certificate
validate_certificates
show_summary

echo ""
echo "🎉 Certificate generation completed successfully!"
echo "📖 See the summary above for next steps and usage instructions."

# ========================================================================================
# END OF SCRIPT
# ========================================================================================
