#!/usr/bin/env bash
# Runs only the opt-in installed-Baritone acceptance entrypoint, without bundling the dependency.
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/test-dependencies
baritone_jar=build/test-dependencies/baritone-api-fabric-1.19.0.jar
if [[ ! -f "$baritone_jar" ]]; then
    curl --fail --location --retry 3 https://github.com/cabaletta/baritone/releases/download/v1.19.0/baritone-api-fabric-1.19.0.jar -o "$baritone_jar"
fi
printf '%s  %s\n' eca6e2fdf43c6657fe9fea10a8f9a7572d78dc91ad389b998b808bd28fa5b5d1 "$baritone_jar" | sha256sum --check
export MESA_GL_VERSION_OVERRIDE="${MESA_GL_VERSION_OVERRIDE:-4.5}"
export MESA_GLSL_VERSION_OVERRIDE="${MESA_GLSL_VERSION_OVERRIDE:-450}"
export GALLIUM_DRIVER="${GALLIUM_DRIVER:-llvmpipe}"
export LIBGL_ALWAYS_SOFTWARE="${LIBGL_ALWAYS_SOFTWARE:-1}"
if ! timeout 900 xvfb-run -a --server-args="-screen 0 1280x720x24" ./gradlew runClientGameTest -PbaritoneTestJar="$baritone_jar" --stacktrace > build/baritone-client.log 2>&1; then
    tail -n 100 build/baritone-client.log
    exit 1
fi
grep -F 'Real Baritone 26.2: 384-block travel' build/baritone-client.log
