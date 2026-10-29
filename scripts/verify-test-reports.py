import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


def verify(root):
    reports = sorted((root / "backend/target/surefire-reports").glob("TEST-*.xml"))
    if not reports:
        raise ValueError("No Surefire test reports found")

    suites = {}
    totals = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0}
    for report in reports:
        suite = ET.parse(report).getroot()
        suites[suite.attrib["name"]] = int(suite.attrib.get("tests", 0))
        for field in totals:
            totals[field] += int(suite.attrib.get(field, 0))

    missing = []
    for source in (root / "backend/src/test/java").rglob("*IntegrationTest.java"):
        package = re.search(r"^package\s+([\w.]+);", source.read_text(encoding="utf-8"), re.MULTILINE)
        if package is None:
            raise ValueError(f"Cannot determine package: {source.name}")
        name = f"{package.group(1)}.{source.stem}"
        if suites.get(name, 0) == 0:
            missing.append(name)

    print(
        f"Tests: {totals['tests']}, failures: {totals['failures']}, "
        f"errors: {totals['errors']}, skipped: {totals['skipped']}"
    )
    if missing:
        raise ValueError("Integration suites did not execute: " + ", ".join(sorted(missing)))
    if totals["tests"] == 0 or any(totals[field] for field in ("failures", "errors", "skipped")):
        raise ValueError("All tests must execute successfully without skips")


if __name__ == "__main__":
    try:
        verify(Path(__file__).resolve().parent.parent)
    except (ValueError, ET.ParseError, OSError) as error:
        print(f"Test verification failed: {error}", file=sys.stderr)
        sys.exit(1)
