#!/usr/bin/env python3
"""Prints a compact, human-readable rendering of one Athena snapshot: what a reviewer would
see in the Change Map, Semantic Change Explorer, Semantic Canvas and Review Briefing focus
areas, without evidence bodies. Meant for the protocol's "Athena pass".

Usage: evaluation/tools/summarize.py <snapshot-dir> [--evidence]
"""
import json
import sys
from collections import Counter
from pathlib import Path


def main(directory, show_evidence):
    load = lambda name: json.loads((directory / name).read_text())
    change_map = load("change-map.json")
    profile = load("semantic-profile.json")["dimensions"]
    topology = load("topology.json")
    focus = load("focus-areas.json")

    print(f"# {directory.name}: {change_map['prTitle']}")
    counts = ", ".join(f"{k.lower()} {v}" for k, v in change_map["categoryCounts"].items() if v)
    print(f"\n## Change Map ({len(change_map['changes'])} changes: {counts or 'none'})")
    for group in change_map["classGroups"]:
        print(f"  [{group['enclosingType']}]")
        for entry in group["entries"]:
            extra = f" ×{entry['occurrenceCount']}" if entry["occurrenceCount"] > 1 else ""
            print(f"    - {entry['category'].lower():10} {entry['kind']:26} {entry['description']}{extra}")

    print(f"\n## Semantic Change Explorer ({len(profile)} cards)")
    cards = Counter()
    for entry in profile:
        tag = "inferred %d%%" % entry["confidencePercent"] if entry["inferred"] else "observed"
        grouped = f" (groups {entry['groupedMoveCount']})" if entry["groupedMoveCount"] else ""
        cards[(entry["dimension"], entry["conceptName"], tag + grouped)] += 1
    for (dimension, concept, tag), n in cards.items():
        print(f"  - {dimension:15} {concept:40} {tag}{'  ×%d' % n if n > 1 else ''}")
    if show_evidence:
        for entry in profile:
            print(f"\n  ### {entry['dimension']} / {entry['conceptName']}: {entry['conceptDescription']}")
            for evidence in entry["evidence"][:3]:
                print("    " + evidence[:600].replace("\n", "\n    "))

    print(f"\n## Semantic Canvas ({len(topology['territories'])} territories, "
          f"{len(topology['dependencies'])} dependency rails)")
    for territory in topology["territories"]:
        print(f"  - {territory['moduleName']:30} {territory['status']:10} {territory['techStackLabel']:10} "
              f"{territory['statusSummary']}")
    for dependency in topology["dependencies"]:
        print(f"    {dependency['from']} -> {dependency['to']}")

    print(f"\n## Review Briefing focus areas ({len(focus)})")
    for item in focus:
        print(f"  - {item['description']}")


if __name__ == "__main__":
    main(Path(sys.argv[1]), "--evidence" in sys.argv[2:])
