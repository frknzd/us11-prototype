#!/usr/bin/env python3
"""Takes the box positions PlantUML chose and writes them as a layout JSON for puml2drawio.py.

    python3 svg2layout.py "US11 class diagram.svg" "US11 class diagram.layout.json"

Classes are keyed by their name, notes by "note:<alias>", packages by "pkg:<alias>", like
puml2drawio.py expects. The draw.io file then starts with PlantUML's layout, which can be
changed by hand afterwards.
"""

import json
import re
import sys

GROUP = re.compile(r'<g class="(entity|cluster)" data-qualified-name="([^"]+)"[^>]*>')
RECT = re.compile(r'<rect[^>]*?height="([\d.]+)"[^>]*?width="([\d.]+)"[^>]*?x="([\d.]+)"[^>]*?y="([\d.]+)"')
RECT_ANY = re.compile(r"<rect([^>]*)>")
PATH = re.compile(r'<path[^>]*?d="([^"]+)"')
POLYGON = re.compile(r'<polygon[^>]*?points="([^"]+)"')


def attrs(text):
    return dict(re.findall(r'(\w+)="([^"]*)"', text))


def bbox_of(block):
    rect = RECT_ANY.search(block)
    if rect:
        a = attrs(rect.group(1))
        if all(k in a for k in ("x", "y", "width", "height")):
            return [float(a["x"]), float(a["y"]), float(a["width"]), float(a["height"])]
    shape = PATH.search(block) or POLYGON.search(block)
    if shape:
        numbers = [float(n) for n in re.findall(r"-?[\d.]+", shape.group(1))]
        xs, ys = numbers[0::2], numbers[1::2]
        return [min(xs), min(ys), max(xs) - min(xs), max(ys) - min(ys)]
    return None


def main(svg_path, out_path):
    svg = open(svg_path, encoding="utf-8").read()
    size = attrs(re.search(r"<svg([^>]*)>", svg).group(1))
    width = float(size.get("width", "3000px").rstrip("px"))
    height = float(size.get("height", "3000px").rstrip("px"))
    groups = list(GROUP.finditer(svg))
    layout = {"page": [0, 0, round(width) + 40, round(height) + 40]}
    for i, m in enumerate(groups):
        end = groups[i + 1].start() if i + 1 < len(groups) else len(svg)
        box = bbox_of(svg[m.end():end])
        if not box:
            continue
        kind, name = m.group(1), m.group(2).split(".")[-1]
        key = "pkg:" + name if kind == "cluster" else ("note:" + name if name.startswith("N_") else name)
        layout[key] = [round(v) for v in box]
    json.dump(layout, open(out_path, "w"), indent=1)
    print(f"{len(layout) - 1} boxes -> {out_path}")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2])
