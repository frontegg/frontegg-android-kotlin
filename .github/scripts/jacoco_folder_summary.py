#!/usr/bin/env python3
"""
Read JaCoCo XML and print a Markdown coverage table (by path under com/frontegg/android),
suitable for $GITHUB_STEP_SUMMARY.
"""
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict


def local_name(tag: str) -> str:
    return tag.split("}", 1)[-1] if tag.startswith("{") else tag


def folder_key(package_path: str) -> str:
    prefix = "com/frontegg/android"
    if not package_path or package_path == prefix:
        return "(root)"
    if package_path.startswith(prefix + "/"):
        return package_path[len(prefix) + 1 :]
    return package_path


def is_sdk_package(package_path: str) -> bool:
    prefix = "com/frontegg/android"
    return package_path == prefix or package_path.startswith(prefix + "/")


def main() -> int:
    if len(sys.argv) < 2:
        print("usage: jacoco_folder_summary.py <report.xml>", file=sys.stderr)
        return 1
    path = sys.argv[1]
    tree = ET.parse(path)
    root = tree.getroot()

    by_folder = defaultdict(lambda: {"covered": 0, "missed": 0})
    total_covered = 0
    total_missed = 0

    for c in root:
        if local_name(c.tag) == "counter" and c.attrib.get("type") == "LINE":
            total_missed = int(c.attrib.get("missed", "0"))
            total_covered = int(c.attrib.get("covered", "0"))
            break

    for elem in root.iter():
        if local_name(elem.tag) != "package":
            continue
        pkg_name = elem.attrib.get("name", "")
        if not is_sdk_package(pkg_name):
            continue
        key = folder_key(pkg_name)
        for child in elem:
            if local_name(child.tag) != "counter":
                continue
            if child.attrib.get("type") != "LINE":
                continue
            missed = int(child.attrib.get("missed", "0"))
            covered = int(child.attrib.get("covered", "0"))
            by_folder[key]["missed"] += missed
            by_folder[key]["covered"] += covered
            break

    total_lines = total_covered + total_missed
    pct = (100.0 * total_covered / total_lines) if total_lines else 0.0

    def status_emoji(p: float) -> str:
        if p >= 80:
            return ":large_green_circle:"
        if p >= 50:
            return ":large_yellow_circle:"
        return ":red_circle:"

    rows = []
    for folder in sorted(by_folder.keys(), key=lambda x: (x != "(root)", x.lower())):
        cov = by_folder[folder]["covered"]
        mis = by_folder[folder]["missed"]
        t = cov + mis
        p = (100.0 * cov / t) if t else 0.0
        rows.append(f"| {folder} | {status_emoji(p)} | {p:.1f}% | {cov}/{t} |")

    md = []
    md.append("### Code Coverage (`:android`, unit tests)")
    md.append("")
    md.append(
        f"**Overall:** {pct:.1f}% ({total_covered} of {total_lines} lines covered) {status_emoji(pct)}"
    )
    md.append("")
    md.append("| Folder | | Coverage | Lines |")
    md.append("| :--- | :---: | :---: | :--- |")
    md.extend(rows)
    md.append("")
    print("\n".join(md))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
