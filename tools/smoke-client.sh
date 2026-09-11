#!/usr/bin/env bash
#
# Runs the client game tests and checks the mixins they loaded.
#
# `./gradlew build` passes whether or not the client can start. A mixin target is
# named in an annotation string, so a missing one compiles fine and fails at launch;
# a null dereference during client initialisation is a crash no unit test reaches;
# and a module that throws on its first tick in a real world does neither. This is
# the check for all three.
#
# The client drives itself here: it creates a world, opens every screen, toggles
# every module and shuts itself down. Gradle's exit code is therefore the verdict
# on the game tests, and the timeout below is a genuine failure rather than the
# normal way the run ends.
set -uo pipefail

cd "$(dirname "$0")/.."

LOG="${SMOKE_LOG:-build/smoke-client.log}"
TIMEOUT="${SMOKE_TIMEOUT:-900}"
RUN_DIR=build/run/clientGameTest
mkdir -p "$(dirname "$LOG")"

# Minecraft 26.2 needs an OpenGL 3.3 core context. Asking llvmpipe for 3.2 makes
# window creation fail with a misleading error, so these are not optional.
export MESA_GL_VERSION_OVERRIDE="${MESA_GL_VERSION_OVERRIDE:-4.5}"
export MESA_GLSL_VERSION_OVERRIDE="${MESA_GLSL_VERSION_OVERRIDE:-450}"
export GALLIUM_DRIVER="${GALLIUM_DRIVER:-llvmpipe}"
export LIBGL_ALWAYS_SOFTWARE="${LIBGL_ALWAYS_SOFTWARE:-1}"

INIT_SCRIPT="$(mktemp -t mixindbg-XXXXXX.gradle)"
trap 'rm -f "$INIT_SCRIPT"' EXIT
cat > "$INIT_SCRIPT" <<'GRADLE'
gradle.projectsLoaded {
    rootProject {
        afterEvaluate {
            tasks.matching { it.name == 'runClientGameTest' }.configureEach {
                jvmArgs '-Dmixin.debug.export=true'
            }
        }
    }
}
GRADLE

echo "Running the client game tests headless (timeout ${TIMEOUT}s); log: $LOG"
timeout "$TIMEOUT" xvfb-run -a --server-args="-screen 0 1280x720x24" \
    ./gradlew runClientGameTest --init-script "$INIT_SCRIPT" > "$LOG" 2>&1
status=$?

failed=0
note() { echo "FAIL: $*"; failed=1; }

if [ "$status" -eq 124 ]; then
    note "the client game tests did not finish within ${TIMEOUT}s"
elif [ "$status" -ne 0 ]; then
    note "the client game tests failed (gradle exit $status)"
    # The assertion, not the 40 lines of loader plumbing wrapped around it.
    grep -A6 -m2 -E 'AssertionError|Game crashed' "$LOG" | head -30
fi

if grep -qiE 'mixin.*(error applying|failed to apply)' "$LOG"; then
    note "a mixin failed to apply"
    grep -iE 'mixin.*(error applying|failed to apply)' "$LOG" | head
fi

# Every mixin listed in the config must have produced a transformed class. A class
# that is never loaded produces no export, which is why the config is the source of
# truth here rather than a hand-kept list.
OUT="$RUN_DIR/.mixin.out/class"
while read -r target; do
    [ -z "$target" ] && continue
    if [ ! -f "$OUT/$target.class" ]; then
        note "$target was never transformed"
        continue
    fi
    if ! javap -p -c "$OUT/$target.class" 2>/dev/null | grep -q 'agalarhack'; then
        note "$target was transformed but carries no agalarhack injection"
    else
        echo "ok: $target"
    fi
done < <(python3 - <<'PY'
import json, pathlib, re
config = json.load(open("src/main/resources/agalarhack.mixins.json"))
for name in config["client"]:
    source = pathlib.Path("src/main/java/me/mrhakan/agalarhack/mixin", name + ".java").read_text()
    target = re.search(r"@Mixin\(\s*(\w+)\.class", source).group(1)
    imported = re.search(r"import ([\w.]+\." + target + ");", source).group(1)
    print(imported.replace(".", "/"))
PY
)

# What the game tests themselves reported, so a passing CI log says what was covered
# rather than only that nothing blew up.
grep -F '(agalarhack-gametest)' "$LOG" | grep -vE ' ok$' | sed 's/.*(agalarhack-gametest) /  /'

if [ "$failed" -ne 0 ]; then
    echo "Smoke check FAILED; see $LOG"
    exit 1
fi
echo "Smoke check passed: the client runs, every module survives a world, and every mixin is applied."
