#!/bin/bash
# Script to generate Beans.yaml files for all beans in this directory via WebLogic REST API

API_URL_PREFIX="http://127.0.0.1:7001/management/weblogic/latest/serverConfig/consoleBackend/beanTypeDescriptions"
PACKAGE_PREFIX="com.bea.wcp.media.descriptor.beans"

# Credentials for curl
CREDENTIALS="weblogic:Weblogic1"

# Directory to output YAMLs (current directory)
OUTPUT_DIR="$(dirname "$0")"

cd "$OUTPUT_DIR" || exit 1

for java_file in *.java; do
  # Skip if not a regular file or file is this script itself
  [ ! -f "$java_file" ] && continue
  [ "$java_file" == "generate-beans-yaml.sh" ] && continue

  bean_name="${java_file%.java}"
  fqbean="$PACKAGE_PREFIX.$bean_name"
  yaml_file="$bean_name.yaml"
  echo "curl --fail --user \"$CREDENTIALS\" \"$API_URL_PREFIX/$fqbean\" -o \"$yaml_file\""
  curl --fail --user "$CREDENTIALS" "$API_URL_PREFIX/$fqbean" -o "$yaml_file"
  if [ $? -eq 0 ]; then
    echo "Generated $yaml_file for $fqbean"
  else
    echo "Failed to generate $yaml_file for $fqbean" >&2
  fi
done
