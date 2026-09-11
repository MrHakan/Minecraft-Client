#!/usr/bin/env bash
#
# Starts the client headless and checks two things a normal build cannot:
#   1. it starts at all, and
#   2. every mixin injection is actually applied to the running bytecode.
#
# Both matter because `./gradlew build` passes either way. A mixin target is named
# in an annotation string, so a missing one compiles fine and fails at launch; and a
# null dereference during client initialisation is a crash no unit test reaches.
#
# The client is killed by a timeout once it has done enough to prove those two
# things. It never reaches a playable state here: there is no audio device and no
# narrator library, and that is fine.
set -uo pipefail

cd "$(dirname "$0")/.."

LOG="${SMOKE_LOG:-build/smoke-client.log}"
TIMEOUT="${SMOKE_TIMEOUT:-420}"
mkdir -p "$(dirname "$LOG")"
rm -rf run/.mixin.out

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
            tasks.matching { it.name == 'runClient' }.configureEach {
                jvmArgs '-Dmixin.debug.export=true'
            }
        }
    }
}
GRADLE

echo "Starting the client headless (timeout ${TIMEOUT}s); log: $LOG"
timeout "$TIMEOUT" xvfb-run -a --server-args="-screen 0 1280x720x24" \
    ./gradlew runClient --init-script "$INIT_SCRIPT" > "$LOG" 2>&1
echo "gradle exited with $? (a timeout kill is expected and fine)"

failed=0
note() { echo "FAIL: $*"; failed=1; }

# A crash report is fatal however far the run got.
if [ "$(grep -c 'Game crashed' "$LOG")" -ne 0 ]; then
    note "the client crashed"
    sed -n '/Caused by/,/^$/p' "$LOG" | head -20
fi

# Transformation happens at class load, long before most startup work, so a
# complete .mixin.out says nothing on its own about whether the run was healthy.
# This is the check whose absence once hid a startup crash for several commits.
if ! grep -q 'blocks.png-atlas' "$LOG"; then
    note "startup did not reach texture atlas stitching (crash, or SMOKE_TIMEOUT too short for this runner)"
fi

if grep -qiE 'mixin.*(error applying|failed to apply)' "$LOG"; then
    note "a mixin failed to apply"
    grep -iE 'mixin.*(error applying|failed to apply)' "$LOG" | head
fi

# Every mixin listed in the config must have produced a transformed class. A class
# that is never loaded produces no export, which is why the config is the source of
# truth here rather than a hand-kept list.
OUT=run/.mixin.out/class
while read -r target; do
    [ -z "$target" ] && continue
    if [ ! -f "$OUT/$target.class" ]; then
        note "$target was never transformed"
        continue
    fi
    if ! javap -p -c "$OUT/$target.class" | grep -q 'agalarhack'; then
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

if [ "$failed" -ne 0 ]; then
    echo "Smoke check FAILED; see $LOG"
    exit 1
fi
echo "Smoke check passed: the client starts and every mixin is applied."
