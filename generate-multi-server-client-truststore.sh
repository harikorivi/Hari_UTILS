#!/usr/bin/env bash
# ========================================================================================
# Generate a combined Java client truststore (JKS) for multiple outbound servers
# ========================================================================================
#
# Purpose:
#   Given separate server keystores (PKCS12) for different servers (e.g., MFE and RTPProxy),
#   extract their issuing CA/intermediate certificates and build ONE common client
#   truststore (JKS) that WebLogic/Java can use for outbound HTTPS requests to both.
#
# What it does:
#   - Reads certificate chains from provided PKCS12 keystores (no private keys exported)
#   - Detects CA certificates (BasicConstraints: CA:TRUE)
#   - De-duplicates by certificate fingerprint (SHA-256)
#   - Imports the set of CA/intermediate certificates into a single JKS truststore
#   - Optionally also writes a combined PEM bundle of those CA/intermediate certs
#
# Requirements:
#   - keytool (from JDK)
#   - openssl
#
# Example:
#   ./generate-multi-server-client-truststore.sh \
#     --mfe-p12 /path/to/mfe/server.p12 --mfe-pass changeit \
#     --rtp-p12 /path/to/rtpproxy/server.p12 --rtp-pass changeit \
#     --out-jks /path/to/client-truststore.jks --out-pass changeit \
#     --out-pem /path/to/combined-ca-bundle.pem \
#     --force
#
# Notes:
#   - If a PKCS12 keystore lacks intermediates/root, consider passing --extra-ca-pem
#     multiple times to include any missing CA PEM files.
#   - It is safe to import roots and intermediates; Java truststores can contain both.
#
# ========================================================================================

set -euo pipefail

# -------------------------------
# Helpers
# -------------------------------
print_header() {
  echo "================================================================================="
  echo "$1"
  echo "================================================================================="
}

print_step() {
  echo ""
  echo "STEP $1: $2"
  echo "---------------------------------------------------------------------------------"
}

die() {
  echo "ERROR: $*" >&2
  exit 1
}

# -------------------------------
# Defaults / CLI parsing
# -------------------------------
MFE_P12=""
MFE_PASS=""
RTP_P12=""
RTP_PASS=""
OUT_JKS="combined-client-truststore.jks"
OUT_PASS="changeit"
OUT_PEM=""          # optional combined PEM bundle
FORCE_OVERWRITE=""
EXTRA_CA_PEMS=()

usage() {
  cat <<EOF
Usage:
  $(basename "$0") [OPTIONS]

Options:
  --mfe-p12 PATH         Path to MFE server PKCS12 keystore (server.p12)
  --mfe-pass PASS        Password for MFE PKCS12
  --rtp-p12 PATH         Path to RTPProxy server PKCS12 keystore (server.p12)
  --rtp-pass PASS        Password for RTPProxy PKCS12
  --out-jks PATH         Output JKS truststore path (default: $OUT_JKS)
  --out-pass PASS        Output JKS truststore password (default: $OUT_PASS)
  --out-pem PATH         Also write a combined CA PEM bundle at PATH (optional)
  --extra-ca-pem PATH    Additional CA certificate PEM to include (can repeat)
  --force                Overwrite existing output truststore
  -h, --help             Show this help

Examples:
  $(basename "$0") \\
    --mfe-p12 /certs/mfe/server.p12 --mfe-pass changeit \\
    --rtp-p12 /certs/rtpproxy/server.p12 --rtp-pass changeit \\
    --out-jks /certs/client-truststore.jks --out-pass changeit \\
    --out-pem /certs/combined-ca-bundle.pem --force

EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --mfe-p12)   MFE_P12="${2:-}"; shift 2 ;;
    --mfe-pass)  MFE_PASS="${2:-}"; shift 2 ;;
    --rtp-p12)   RTP_P12="${2:-}"; shift 2 ;;
    --rtp-pass)  RTP_PASS="${2:-}"; shift 2 ;;
    --out-jks)   OUT_JKS="${2:-}"; shift 2 ;;
    --out-pass)  OUT_PASS="${2:-}"; shift 2 ;;
    --out-pem)   OUT_PEM="${2:-}"; shift 2 ;;
    --extra-ca-pem) EXTRA_CA_PEMS+=("${2:-}"); shift 2 ;;
    --force)     FORCE_OVERWRITE=1; shift ;;
    -h|--help)   usage; exit 0 ;;
    *) die "Unknown option: $1 (use -h for help)";;
  esac
done

# Validate dependencies
command -v keytool >/dev/null 2>&1 || die "keytool not found (install JDK)"
command -v openssl >/dev/null 2>&1 || die "openssl not found"

# Validate inputs
[[ -n "$MFE_P12" ]] || die "--mfe-p12 is required"
[[ -n "$MFE_PASS" ]] || die "--mfe-pass is required"
[[ -n "$RTP_P12" ]] || die "--rtp-p12 is required"
[[ -n "$RTP_PASS" ]] || die "--rtp-pass is required"
[[ -f "$MFE_P12" ]] || die "File not found: $MFE_P12"
[[ -f "$RTP_P12" ]] || die "File not found: $RTP_P12"

# Output overwrite handling
if [[ -f "$OUT_JKS" && -z "${FORCE_OVERWRITE:-}" ]]; then
  die "Output truststore exists: $OUT_JKS (use --force to overwrite)"
fi
[[ -f "$OUT_JKS" ]] && rm -f "$OUT_JKS"

# Temp workspace
WORKDIR="$(mktemp -d 2>/dev/null || mktemp -d -t tmp)"
cleanup() {
  rm -rf "$WORKDIR"
}
trap cleanup EXIT

print_header "Generate combined client truststore (JKS) for MFE + RTPProxy"

echo "MFE PKCS12: $MFE_P12"
echo "RTP PKCS12: $RTP_P12"
echo "Output JKS : $OUT_JKS"
[[ -n "$OUT_PEM" ]] && echo "Output PEM : $OUT_PEM"
echo ""

# -------------------------------
# Functions: extract certs from PKCS12 and filter CA:true
# -------------------------------
extract_p12_rfc() {
  local p12="$1"
  local pass="$2"
  local out_rfc="$3"

  keytool -list -rfc \
    -keystore "$p12" \
    -storetype PKCS12 \
    -storepass "$pass" > "$out_rfc"
}

split_rfc_to_pems() {
  # Splits RFC output into separate PEM files in $4 directory
  local rfc_file="$1"
  local prefix="$2"
  local dest_dir="$3"
  awk '
    /-----BEGIN CERTIFICATE-----/ { in_cert=1; fname=sprintf("'"$dest_dir"'/cert_%s_%04d.pem","'"$prefix"'",++n); print > fname; next }
    /-----END CERTIFICATE-----/   { print >> fname; close(fname); in_cert=0; next }
    { if(in_cert) print >> fname }
  ' "$rfc_file"
}

is_ca_cert() {
  # returns 0 if BasicConstraints CA:TRUE, otherwise 1
  local pem="$1"
  # robust parse: search Basic Constraints and CA:TRUE
  if openssl x509 -in "$pem" -noout -text 2>/dev/null | grep -A2 -i "Basic Constraints" | grep -qi "CA:TRUE"; then
    return 0
  fi
  return 1
}

fingerprint_sha256() {
  local pem="$1"
  openssl x509 -in "$pem" -noout -fingerprint -sha256 | awk -F= '{gsub(":","",$2); print tolower($2)}'
}

subject_cn() {
  local pem="$1"
  # Extract CN from subject if available
  openssl x509 -in "$pem" -noout -subject | sed -E 's#.*/CN=([^/]+).*#\1#' 2>/dev/null || true
}

# -------------------------------
# Step 1: Extract RFC and split to PEMs
# -------------------------------
print_step "1" "Extracting certificate chains from PKCS12 keystores"

MFE_RFC="$WORKDIR/mfe.rfc"
RTP_RFC="$WORKDIR/rtp.rfc"
MFE_DIR="$WORKDIR/mfe_pems"
RTP_DIR="$WORKDIR/rtp_pems"
mkdir -p "$MFE_DIR" "$RTP_DIR"

extract_p12_rfc "$MFE_P12" "$MFE_PASS" "$MFE_RFC"
extract_p12_rfc "$RTP_P12" "$RTP_PASS" "$RTP_RFC"

split_rfc_to_pems "$MFE_RFC" "mfe" "$MFE_DIR"
split_rfc_to_pems "$RTP_RFC" "rtp" "$RTP_DIR"

echo "Extracted PEMs:"
echo " - $(ls -1 "$MFE_DIR"/*.pem 2>/dev/null | wc -l | tr -d ' ') from MFE keystore"
echo " - $(ls -1 "$RTP_DIR"/*.pem 2>/dev/null | wc -l | tr -d ' ') from RTP keystore"

# -------------------------------
# Step 2: Filter CA certs and de-duplicate
# -------------------------------
print_step "2" "Selecting CA/intermediate certificates (BasicConstraints: CA:TRUE) and de-duplicating"

COMBINED_DIR="$WORKDIR/combined_ca"
mkdir -p "$COMBINED_DIR"
declare -A FP_INDEX=()
COUNT_ADDED=0

add_if_unique() {
  local pem="$1"
  local fp
  fp="$(fingerprint_sha256 "$pem")"
  if [[ -z "$fp" ]]; then
    return
  fi
  if [[ -n "${FP_INDEX[$fp]:-}" ]]; then
    # duplicate
    return
  fi
  FP_INDEX["$fp"]=1
  local cn
  cn="$(subject_cn "$pem")"
  local base="ca_${cn:-unknown}_$(echo "$fp" | cut -c1-12).pem"
  cp "$pem" "$COMBINED_DIR/$base"
  COUNT_ADDED=$((COUNT_ADDED+1))
}

# From MFE
for pem in "$MFE_DIR"/*.pem; do
  [[ -f "$pem" ]] || continue
  if is_ca_cert "$pem"; then
    add_if_unique "$pem"
  fi
done

# From RTP
for pem in "$RTP_DIR"/*.pem; do
  [[ -f "$pem" ]] || continue
  if is_ca_cert "$pem"; then
    add_if_unique "$pem"
  fi
done

# From user-provided extra CA PEMs
for extra in "${EXTRA_CA_PEMS[@]:-}"; do
  [[ -f "$extra" ]] || die "--extra-ca-pem points to missing file: $extra"
  if is_ca_cert "$extra"; then
    add_if_unique "$extra"
  else
    echo "NOTE: extra CA PEM provided but does not appear to be CA:TRUE (skipping): $extra"
  fi
done

if [[ $COUNT_ADDED -eq 0 ]]; then
  echo "WARNING: No CA certificates were found in provided keystores."
  echo "         Consider supplying --extra-ca-pem for missing CA/intermediate certs."
fi

echo "Selected $COUNT_ADDED CA/intermediate certificate(s) to include in truststore."
ls -1 "$COMBINED_DIR"/*.pem 2>/dev/null || true

# -------------------------------
# Step 3: Create the JKS truststore and import all CA/intermediates
# -------------------------------
print_step "3" "Creating JKS truststore and importing CA/intermediates"

# Initialize empty truststore by importing the first cert; subsequent imports append entries.
COUNT_IMPORTED=0
for pem in "$COMBINED_DIR"/*.pem; do
  [[ -f "$pem" ]] || continue
  alias_cn="$(subject_cn "$pem")"
  fp="$(fingerprint_sha256 "$pem")"
  alias="ca_${alias_cn:-unknown}_$(echo "$fp" | cut -c1-12)"

  keytool -importcert \
    -file "$pem" \
    -alias "$alias" \
    -keystore "$OUT_JKS" \
    -storepass "$OUT_PASS" \
    -noprompt

  COUNT_IMPORTED=$((COUNT_IMPORTED+1))
done

if [[ $COUNT_IMPORTED -eq 0 ]]; then
  echo "No CA certificates imported into $OUT_JKS (no CA files found)."
else
  echo "Imported $COUNT_IMPORTED certificate(s) into $OUT_JKS"
fi

# -------------------------------
# Step 4: Optional combined PEM bundle
# -------------------------------
if [[ -n "${OUT_PEM:-}" ]]; then
  print_step "4" "Writing combined PEM bundle"
  : > "$OUT_PEM"
  for pem in "$COMBINED_DIR"/*.pem; do
    [[ -f "$pem" ]] || continue
    cat "$pem" >> "$OUT_PEM"
    echo "" >> "$OUT_PEM"
  done
  echo "Combined CA PEM bundle: $OUT_PEM"
fi

# -------------------------------
# Step 5: Validation summary
# -------------------------------
print_step "5" "Validation"

if [[ -f "$OUT_JKS" ]]; then
  echo "Truststore entries:"
  keytool -list -keystore "$OUT_JKS" -storepass "$OUT_PASS" -v | \
    awk '/Alias name:|Owner:|Issuer:/{print}'
else
  echo "No truststore created."
fi

print_header "COMPLETED"
echo "Output truststore: $OUT_JKS"
echo "Truststore password: $OUT_PASS"
[[ -n "${OUT_PEM:-}" ]] && echo "Combined CA bundle: $OUT_PEM"
echo ""
echo "Use with JVM:"
echo "  -Djavax.net.ssl.trustStore=$OUT_JKS"
echo "  -Djavax.net.ssl.trustStorePassword=$OUT_PASS"
echo "  -Djavax.net.ssl.trustStoreType=JKS"
echo ""
echo "Notes:"
echo " - This truststore contains CA/intermediate certs extracted from both provided"
echo "   server PKCS12 keystores, suitable for outbound TLS to both servers."
echo " - If any server does not embed intermediates in its PKCS12, pass missing"
echo "   intermediates via --extra-ca-pem to include them."
echo " - SAN/hostname verification is separate from trust. Ensure the certificate"
echo "   presented by each server contains the IP/DNS you connect to."
