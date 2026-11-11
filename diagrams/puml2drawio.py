#!/usr/bin/env python3
"""Turns a PlantUML class diagram into an editable draw.io file.

The classes, members, notes and relations come from the .puml file. Positions come from an
optional layout JSON (name -> [x, y, w, h]); boxes without a position are put on a grid, so new
classes show up and can be dragged into place in draw.io.

    python3 puml2drawio.py "US10 class diagram v2.puml" "US10 class diagram v2.layout.json" out.drawio
"""

import html
import json
import re
import sys
import xml.etree.ElementTree as ET

FONT = "Helvetica"
MONO = "Menlo, Consolas, monospace"
CLASS_FONT_SIZE = 10.5
NOTE_FONT_SIZE = 12

CLASS_RE = re.compile(
    r"^\s*(abstract class|class|interface|enum)\s+(\w+)(?:\s+<<(\w+)>>)?(?:\s+(#[\w;:.,]+))?\s*\{\s*$"
)
PACKAGE_RE = re.compile(r'^\s*package\s+"([^"]+)"\s+as\s+(\w+)\s*\{\s*$')
NOTE_OF_RE = re.compile(r"^\s*note\s+(left|right|top|bottom)\s+of\s+(\w+)\s*$")
NOTE_AS_RE = re.compile(r"^\s*note\s+as\s+(\w+)\s*$")
REL_RE = re.compile(
    r'^\s*(\w+)\s*(?:"([^"]*)")?\s*([-.*<|o][-.\w\[\]|<>*#,]*)\s*(?:"([^"]*)")?\s*(\w+)\s*(?::\s*(.*))?$'
)


def parse(path):
    classes, packages, notes, relations = {}, {}, {}, []
    package_stack = []
    lines = open(path, encoding="utf-8").read().splitlines()
    i = 0
    while i < len(lines):
        line = lines[i]
        stripped = line.strip()
        if not stripped or stripped.startswith("'") or stripped.startswith("@") or stripped.startswith("skinparam"):
            i += 1
            continue
        if m := PACKAGE_RE.match(line):
            packages[m.group(2)] = {"title": m.group(1), "classes": []}
            package_stack.append(m.group(2))
        elif m := CLASS_RE.match(line):
            kind, name, stereo, fill = m.groups()
            body = []
            i += 1
            while lines[i].strip() != "}":
                body.append(lines[i].strip())
                i += 1
            classes[name] = {"kind": kind, "stereo": stereo, "body": body, "fill": fill}
            if package_stack:
                packages[package_stack[-1]]["classes"].append(name)
        elif stripped == "}" and package_stack:
            package_stack.pop()
        elif (m := NOTE_OF_RE.match(line)) or (m2 := NOTE_AS_RE.match(line)):
            key = m.group(2) if m else m2.group(1)
            text = []
            i += 1
            while lines[i].strip() != "end note":
                text.append(lines[i].strip())
                i += 1
            notes["note:" + key] = text
        elif (m := REL_RE.match(line)) and m.group(1) in classes and m.group(5) in classes:
            relations.append(m.groups())
        i += 1
    return classes, packages, notes, relations


# ---------------------------------------------------------------- labels

def creole(text):
    """**bold**, ""mono"" and <<x>> of PlantUML to HTML, everything else escaped."""
    out = html.escape(text, quote=False)
    out = re.sub(r"\*\*(.+?)\*\*", r"<b>\1</b>", out)
    out = re.sub(r'""(.*?)""(?!")', lambda m: f'<font face="{MONO}">{m.group(1)}</font>', out)
    return out


def member(text):
    # <U+007B> is how the .puml spells a literal "{" that PlantUML must not read as a modifier.
    text = text.replace("<U+007B>", "{")
    abstract = "{abstract}" in text
    static = "{static}" in text
    text = " ".join(text.replace("{abstract}", "").replace("{static}", "").split())
    label = html.escape(text, quote=False)
    if static:
        label = f"<u>{label}</u>"
    # Italic and spelled out, as in the original diagram.
    return f"<i>{label} {{abstract}}</i>" if abstract else label


def class_label(name, cls):
    stereo = {"abstract class": "«abstract»", "interface": "«interface»", "enum": "«enumeration»"}.get(cls["kind"])
    head = f"«{cls['stereo']}»<br>" if cls["stereo"] else (f"{stereo}<br>" if stereo else "")
    title = f"<i>{name}</i>" if cls["kind"] == "abstract class" else name
    body = [b for b in cls["body"] if not b.startswith("'")]
    if "--" in body:
        cut = body.index("--")
        attrs, methods = body[:cut], body[cut + 1:]
    elif cls["kind"] == "enum":
        attrs, methods = body, []
    else:
        attrs = [b for b in body if "(" not in b]
        methods = [b for b in body if "(" in b]
    sections = [f'<p style="margin:4px 0 0 0;text-align:center;">{head}<b style="font-size:12px;">{title}</b></p>']
    for part in (attrs, methods):
        if part or cls["kind"] != "enum":
            rows = "<br>".join(member(b) for b in part) or "&nbsp;"
            sections.append(f'<hr size="1" style="border-style:solid;"><p style="margin:0 0 0 6px;">{rows}</p>')
    return "".join(sections)


def class_height(cls):
    """Height the box needs: title (plus stereotype), the rows and the two separators."""
    rows = len([b for b in cls["body"] if b != "--" and not b.startswith("'")]) or 1
    stereo = cls["stereo"] or cls["kind"] in ("abstract class", "interface", "enum")
    return 30 + (12 if stereo else 0) + 15 * rows + 16


def note_height(text, width):
    """Rough height of a note: wrapped text lines of about 6 px per character, 15 px each."""
    lines = 0
    for line in text:
        mono = line.startswith('""')
        chars = max(1, int((width - 24) / (7.0 if mono else 6.0)))
        lines += max(1, -(-len(line.replace('""', "").replace("**", "")) // chars)) if line else 0.6
    return int(28 + 14.5 * lines)


def note_label(text):
    blocks, para = [], []
    for line in text + [""]:
        if not line:
            if para:
                blocks.append(para)
            para = []
        else:
            para.append(line)
    out = []
    for block in blocks:
        if all(l.startswith("* ") or l.startswith("  ") or l.startswith("    ") for l in block) and block[0].startswith("* "):
            items, current = [], []
            for l in block:
                if l.startswith("* ") and current:
                    items.append(" ".join(current))
                    current = []
                current.append(l[2:] if l.startswith("* ") else l.strip())
            items.append(" ".join(current))
            out.append('<ul style="margin:0;padding-left:16px;">' + "".join(f"<li>{creole(t)}</li>" for t in items) + "</ul>")
        elif all(l.startswith('""') for l in block):
            code = "<br>".join(creole(l).replace("  ", "&nbsp;&nbsp;") for l in block)
            out.append(f'<p style="margin:0 0 6px 0;">{code}</p>')
        else:
            out.append(f'<p style="margin:0 0 6px 0;">{creole(" ".join(block))}</p>')
    return "".join(out)


# ---------------------------------------------------------------- relations

def edge_style(arrow):
    a = re.sub(r"\[[^\]]*\]|up|down|left|right", "", arrow)
    dashed = a.startswith(".") or a.endswith(".")
    style = {
        "edgeStyle": "orthogonalEdgeStyle",
        "rounded": "1",
        "html": "1",
        "jumpStyle": "arc",
        "jumpSize": "8",
        "strokeColor": "#6E6E6B",
        "fontSize": "10",
        "fontColor": "#555555",
        "endSize": "10",
        "startSize": "12",
    }
    if dashed:
        style["dashed"] = "1"
    if a.endswith("|>"):
        style.update(endArrow="block", endFill="0")
    elif a.endswith(">"):
        style.update(endArrow="open", endFill="0")
    else:
        style.update(endArrow="none")
    if a.startswith("*"):
        style.update(startArrow="diamondThin", startFill="1")
    elif a.startswith("o"):
        style.update(startArrow="diamondThin", startFill="0")
    else:
        style.update(startArrow="none")
    return style


NAMED_COLOURS = {"white": "#FFFFFF"}


def colour(fill):
    """A PlantUML fill like #E8F5E9 or #white as a draw.io colour, white when none."""
    if not fill:
        return "#FFFFFF"
    value = fill.split(";")[0].lstrip("#")
    return NAMED_COLOURS.get(value.lower(), "#" + value if re.fullmatch(r"[0-9A-Fa-f]{6}", value) else "#FFFFFF")


def style_str(d):
    return ";".join(f"{k}={v}" for k, v in d.items()) + ";"


# ---------------------------------------------------------------- output

def build(puml, layout_path, out):
    classes, packages, notes, relations = parse(puml)
    layout = json.load(open(layout_path)) if layout_path else {}

    root = ET.Element("mxfile", host="puml2drawio")
    diagram = ET.SubElement(root, "diagram", name="Class diagram", id="class-diagram")
    model = ET.SubElement(diagram, "mxGraphModel", grid="1", gridSize="10", guides="1", tooltips="1",
                          connect="1", arrows="1", fold="1", page="1", pageScale="1",
                          pageWidth=str(layout.get("page", [0, 0, 3330, 2990])[2]),
                          pageHeight=str(layout.get("page", [0, 0, 3330, 2990])[3]), math="0", shadow="0")
    cells = ET.SubElement(model, "root")
    ET.SubElement(cells, "mxCell", id="0")
    ET.SubElement(cells, "mxCell", id="1", parent="0")

    free = {"x": 40, "y": 3100}

    def geometry(key, w=300, h=150):
        if key in layout:
            return layout[key]
        g = [free["x"], free["y"], w, h]
        free["x"] += w + 40
        if free["x"] > 3000:
            free["x"], free["y"] = 40, free["y"] + h + 60
        return g

    def vertex(cid, value, style, g, parent="1"):
        c = ET.SubElement(cells, "mxCell", id=cid, value=value, style=style, vertex="1", parent=parent)
        x, y, w, h = g
        ET.SubElement(c, "mxGeometry", x=str(x), y=str(y), width=str(w), height=str(h), **{"as": "geometry"})

    # Packages first, so they are drawn behind everything else. They are not containers, so a
    # class keeps its absolute position and can be dragged across layers freely.
    for pid, pkg in packages.items():
        vertex("pkg-" + pid, html.escape(pkg["title"], quote=False),
               style_str({"rounded": "0", "whiteSpace": "wrap", "html": "1", "fillColor": "#F7F7F7",
                          "strokeColor": "#C9C9C4", "align": "left", "verticalAlign": "top",
                          "spacingLeft": "10", "spacingTop": "4", "fontSize": "11", "fontColor": "#666666",
                          "fontFamily": FONT, "container": "0"}),
               geometry("pkg:" + pid, 1200, 600))

    for name, cls in classes.items():
        g = list(geometry(name))
        g[3] = max(g[3], class_height(cls))
        vertex(name, class_label(name, cls),
               style_str({"verticalAlign": "top", "align": "left", "overflow": "fill", "html": "1",
                          "whiteSpace": "wrap", "fillColor": colour(cls.get("fill")), "strokeColor": "#C9C9C4",
                          "fontSize": str(CLASS_FONT_SIZE), "fontFamily": FONT}),
               g)

    for key, text in notes.items():
        g = list(geometry(key, 460, 200))
        g[3] = max(g[3], note_height(text, g[2]))
        vertex(key, note_label(text),
               style_str({"shape": "note", "size": "14", "whiteSpace": "wrap", "html": "1",
                          "fillColor": "#FFF5DC", "strokeColor": "#C9C9C4", "align": "left",
                          "verticalAlign": "top", "spacing": "10", "fontSize": str(NOTE_FONT_SIZE),
                          "fontFamily": FONT}),
               g)

    for n, (src, src_mult, arrow, dst_mult, dst, label) in enumerate(relations):
        if "hidden" in arrow:
            continue
        # A <|-- B style arrows point the other way; turn them around so the head is at dst.
        if arrow.startswith("<|") or arrow.startswith("<"):
            src, dst, src_mult, dst_mult = dst, src, dst_mult, src_mult
            arrow = arrow[::-1].replace(">|", "|>").replace("<", ">")
        eid = f"rel-{n}"
        value = html.escape(label.replace("<<", "«").replace(">>", "»"), quote=False) if label else ""
        e = ET.SubElement(cells, "mxCell", id=eid, value=value, style=style_str(edge_style(arrow)),
                          edge="1", parent="1", source=src, target=dst)
        ET.SubElement(e, "mxGeometry", relative="1", **{"as": "geometry"})
        for end, mult in ((-1, src_mult), (1, dst_mult)):
            if not mult:
                continue
            lc = ET.SubElement(cells, "mxCell", id=f"{eid}-{'src' if end < 0 else 'dst'}",
                               value=html.escape(mult, quote=False),
                               style=style_str({"edgeLabel": "", "resizable": "0", "html": "1",
                                                "align": "left" if end < 0 else "right",
                                                "verticalAlign": "bottom", "fontSize": "10",
                                                "fontColor": "#555555"}),
                               vertex="1", connectable="0", parent=eid)
            g = ET.SubElement(lc, "mxGeometry", x=str(end), relative="1", **{"as": "geometry"})
            ET.SubElement(g, "mxPoint", x="6" if end < 0 else "-6", y="-2", **{"as": "offset"})

    ET.indent(root)
    ET.ElementTree(root).write(out, encoding="utf-8", xml_declaration=False)
    unplaced = [k for k in list(classes) + list(notes) + ["pkg:" + p for p in packages] if k not in layout]
    for k in list(classes) + list(notes):
        if k in layout:
            want = class_height(classes[k]) if k in classes else note_height(notes[k], layout[k][2])
            if want > layout[k][3]:
                print(f"  {k}: taller than in the layout ({layout[k][3]} -> {want}), check overlaps")
    print(f"{len(classes)} classes, {len(notes)} notes, {len(packages)} packages, "
          f"{sum('hidden' not in r[2] for r in relations)} relations -> {out}")
    if layout_path and unplaced:
        print("not in the layout (put on a grid below the page):", ", ".join(unplaced))


if __name__ == "__main__":
    if len(sys.argv) not in (3, 4):
        sys.exit(__doc__)
    build(sys.argv[1], sys.argv[2] if len(sys.argv) == 4 else None, sys.argv[-1])
