#!/usr/bin/env python3
"""Print the CHANGELOG.md section for one release, or fail if it has none.

CI runs this on every pull request and main push with the version from gradle.properties. On a pull
request it is the check that a version cannot reach main without release notes; on main its output
is the body of the GitHub release. Sections are headed ``## [2.x.yy] - YYYY-MM-DD``; everything up
to the next ``## [`` heading belongs to that release.
"""
import argparse
import re
import sys
from pathlib import Path

VERSION = re.compile(r"2\.(0|[1-9][0-9]*)\.[0-9]{2}")
HEADING = re.compile(r"^## \[(?P<version>[^\]]+)\](?P<rest>.*)$")


def section(text: str, version: str) -> str | None:
    """The body under the heading for ``version``, without the heading itself."""
    lines = text.splitlines()
    body: list[str] | None = None
    for line in lines:
        heading = HEADING.match(line)
        if heading:
            if body is not None:
                break
            if heading.group("version") == version:
                body = []
            continue
        if body is not None:
            body.append(line)
    if body is None:
        return None
    return "\n".join(body).strip()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("version", help="release number, e.g. 2.0.01")
    parser.add_argument("--changelog", default="CHANGELOG.md", help="path to the changelog")
    args = parser.parse_args()

    if not VERSION.fullmatch(args.version):
        print(f"error: '{args.version}' is not a 2.x.yy version", file=sys.stderr)
        return 1
    path = Path(args.changelog)
    if not path.is_file():
        print(f"error: {path} does not exist", file=sys.stderr)
        return 1
    notes = section(path.read_text(encoding="utf-8"), args.version)
    if notes is None:
        print(f"error: {path} has no '## [{args.version}]' section; add release notes for this version",
              file=sys.stderr)
        return 1
    if not notes:
        print(f"error: the '## [{args.version}]' section in {path} is empty", file=sys.stderr)
        return 1
    print(notes)
    return 0


if __name__ == "__main__":
    sys.exit(main())
