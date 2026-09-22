#!/usr/bin/env python3
"""Objective, re-computable coverage metrics for one Athena snapshot directory.

Compares the conventional diff (pr.diff) against what Athena represented (changes.json,
change-map.json, semantic-profile.json, topology.json) and writes metrics.json next to them.
These numbers are the part of an evaluation that can be diffed mechanically between two
Athena versions; the qualitative judgement lives in the report.

Usage: evaluation/tools/coverage.py <snapshot-dir> [<snapshot-dir> ...]
"""
import json
import re
import sys
from collections import Counter
from pathlib import Path

COPYRIGHT_ONLY = re.compile(r"^[-+]\s*\*?\s*Copyright\b.*\d{4}", re.IGNORECASE)


def parse_diff(text):
    """Per file: added/removed line counts, hunk count, and whether every changed line is a
    copyright-header line (a purely mechanical edit)."""
    files = {}
    current = None
    for line in text.splitlines():
        if line.startswith("diff --git "):
            path = line.split(" b/", 1)[1]
            current = files.setdefault(path, {"added": 0, "removed": 0, "hunks": 0, "non_copyright_lines": 0})
        elif current is None or line.startswith(("+++", "---")):
            continue
        elif line.startswith("@@"):
            current["hunks"] += 1
        elif line.startswith(("+", "-")):
            current["added" if line[0] == "+" else "removed"] += 1
            if not COPYRIGHT_ONLY.match(line):
                current["non_copyright_lines"] += 1
    return files


def is_test(path):
    return "/src/test/" in path or path.startswith("src/test/") or "/tests/" in path or "/testsuite/" in path


def metrics_for(snapshot):
    diff_files = parse_diff((snapshot / "pr.diff").read_text(errors="replace"))
    changes = json.loads((snapshot / "changes.json").read_text())
    profile = json.loads((snapshot / "semantic-profile.json").read_text())["dimensions"]
    topology = json.loads((snapshot / "topology.json").read_text())
    focus = json.loads((snapshot / "focus-areas.json").read_text())

    java = {p: s for p, s in diff_files.items() if p.endswith(".java")}
    substantive_java = {p for p, s in java.items() if s["non_copyright_lines"] > 0}
    production_java = {p for p in substantive_java if not is_test(p)}
    represented = {f for change in changes for f in change["files"]}

    def covered(paths):
        return sorted(p for p in paths if p in represented)

    return {
        "diff": {
            "files": len(diff_files),
            "java_files": len(java),
            "non_java_files": len(diff_files) - len(java),
            "substantive_java_files": len(substantive_java),
            "copyright_only_java_files": len(java) - len(substantive_java),
            "production_java_files": len(production_java),
            "java_hunks": sum(s["hunks"] for s in java.values()),
            "java_lines_changed": sum(s["added"] + s["removed"] for s in java.values()),
        },
        "athena": {
            "changes": len(changes),
            "changes_by_kind": dict(Counter(c["kind"] for c in changes)),
            "changes_by_category": dict(Counter(c["category"] for c in changes)),
            "semantic_entries": len(profile),
            "semantic_entries_by_dimension": dict(Counter(e["dimension"] for e in profile)),
            "inferred_entries": sum(1 for e in profile if e["inferred"]),
            "module_territories": len(topology["territories"]),
            "module_dependencies": len(topology["dependencies"]),
            "focus_areas": len(focus),
        },
        "coverage": {
            "substantive_java_files_represented": len(covered(substantive_java)),
            "substantive_java_file_coverage": ratio(len(covered(substantive_java)), len(substantive_java)),
            "production_java_files_represented": len(covered(production_java)),
            "production_java_file_coverage": ratio(len(covered(production_java)), len(production_java)),
            "unrepresented_production_java_files": sorted(production_java - represented),
            "unrepresented_test_java_files": sorted((substantive_java - production_java) - represented),
        },
    }


def ratio(numerator, denominator):
    return round(numerator / denominator, 2) if denominator else None


if __name__ == "__main__":
    for arg in sys.argv[1:]:
        directory = Path(arg)
        result = metrics_for(directory)
        (directory / "metrics.json").write_text(json.dumps(result, indent=2) + "\n")
        print(f"{directory.name}: {json.dumps(result['coverage'])}")
