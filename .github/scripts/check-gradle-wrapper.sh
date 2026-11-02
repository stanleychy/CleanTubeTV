#!/usr/bin/env bash
# Stops the build when gradle-wrapper.jar is not the official file for the Gradle version in
# gradle-wrapper.properties. The official SHA-256 comes from services.gradle.org.
set -euo pipefail
props=gradle/wrapper/gradle-wrapper.properties
version=$(sed -n 's#^distributionUrl=.*/gradle-\([0-9][0-9.]*\)-bin\.zip$#\1#p' "$props")
[[ "$version" =~ ^[0-9]+(\.[0-9]+){1,2}$ ]] || { echo "Cannot read the Gradle version from $props"; exit 1; }
expected=$(curl -fsSL "https://services.gradle.org/distributions/gradle-${version}-wrapper.jar.sha256")
[[ "$expected" =~ ^[0-9a-f]{64}$ ]] || { echo "Cannot get the official wrapper checksum"; exit 1; }
actual=$(sha256sum gradle/wrapper/gradle-wrapper.jar | cut -d' ' -f1)
if [[ "$actual" != "$expected" ]]; then
  echo "gradle-wrapper.jar is not the official file for Gradle $version"
  echo "expected $expected"
  echo "actual   $actual"
  exit 1
fi
grep -q '^distributionSha256Sum=[0-9a-f]\{64\}$' "$props" || { echo "distributionSha256Sum is missing in $props"; exit 1; }
echo "Gradle wrapper $version: OK"
