"""Expose observed JUnit XML totals, without treating source annotation counts as execution."""
import os
from pathlib import Path
import xml.etree.ElementTree as ET

reports = sorted(Path('build/test-results/test').glob('TEST-*.xml'))
if not reports:
    raise SystemExit('No JUnit reports: cannot claim unit-test execution')
totals = dict(tests=0, failures=0, errors=0, skipped=0)
for report in reports:
    suite = ET.parse(report).getroot()
    for key in totals:
        totals[key] += int(suite.attrib.get(key, 0))
message = 'JUnit XML: ' + ', '.join(f'{value} {key}' for key, value in totals.items())
print(message)
if summary := os.environ.get('GITHUB_STEP_SUMMARY'):
    with open(summary, 'a') as output:
        output.write('### Observed unit-test results\n\n' + message + '\n\n'
                     'Client game tests and mixin checks run separately; unit totals are not multiplayer or visual acceptance.\n')
if totals['tests'] == 0 or totals['failures'] or totals['errors']:
    raise SystemExit('Unit suite did not pass')
