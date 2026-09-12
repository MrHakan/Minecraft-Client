#!/usr/bin/env bash
# Prove the regression test rejects the original bug, then restore the exact source bytes.
set -euo pipefail
cd "$(dirname "$0")/.."
source_file=src/main/java/me/mrhakan/agalarhack/services/ContainerTransferController.java
saved_source=$(mktemp)
cp "$source_file" "$saved_source"
trap 'cp "$saved_source" "$source_file"; rm -f "$saved_source"' EXIT
mkdir -p build
python3 - <<'PY'
from pathlib import Path
path = Path('src/main/java/me/mrhakan/agalarhack/services/ContainerTransferController.java')
text = path.read_text()
fixed = 'if (!controls.ready() || !controls.cursorEmpty()) { plan = new int[0]; step = 0; recovering = true; return; }'
broken = 'if (controls.ready() && !controls.cursorEmpty()) { plan = new int[0]; step = 0; recovering = true; return; }'
assert text.count(fixed) == 1, 'Recovery source changed; update the regression control explicitly'
path.write_text(text.replace(fixed, broken))
PY
report=build/test-results/test/TEST-me.mrhakan.agalarhack.services.ContainerTransferControllerTest.xml
# A previous run of this script leaves a report containing exactly the failure checked for below.
# If the build then breaks for an unrelated reason - a JDK that cannot target 25 is enough - the test
# never runs, the stale report is still there, and the checks below would certify a control that did
# not happen. So the report is removed first and its absence afterwards is a distinct failure.
rm -f "$report"
set +e
./gradlew test --tests '*ContainerTransferControllerTest.disablingWhileAScreenIsOpenRetainsRecoveryUntilPlayResumes' \
    --rerun-tasks > build/inventory-regression-control.log 2>&1
status=$?
set -e
if [ "$status" -eq 0 ]; then
    echo 'FAIL: the original cursor-recovery bug passed the regression test'
    exit 1
fi
if [ ! -f "$report" ]; then
    echo 'FAIL: the regression test never ran, so nothing was proven. The build failed before it:'
    tail -20 build/inventory-regression-control.log
    exit 1
fi
python3 - <<'PY'
import xml.etree.ElementTree as ET
from pathlib import Path
report = Path('build/test-results/test/TEST-me.mrhakan.agalarhack.services.ContainerTransferControllerTest.xml')
assert report.is_file(), 'The report is gone; the shell guard above should have caught this'
root = ET.parse(report).getroot()
case = next((case for case in root.findall('testcase') if case.attrib['name'].startswith(
    'disablingWhileAScreenIsOpenRetainsRecoveryUntilPlayResumes')), None)
assert case is not None and case.find('failure') is not None, 'Failure was not the intended regression assertion'
assert 'AssertionFailedError' in case.find('failure').attrib.get('type', ''), 'Unexpected runtime failure'
print('Regression control passed: the original release bug fails the open-screen recovery assertion')
PY
