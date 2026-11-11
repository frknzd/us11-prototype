#!/usr/bin/env python3
"""Fixed functional regions and obstacle-aware orthogonal routing for US11.

Consumes the highlighted PlantUML; only presentation changes. Graphviz measures
all compartments first, then neato routes around the fixed rectangles. Intermediate
DOT/JSON files live in .local, never beside the published PNGs.
"""
import html
import json
import re
import subprocess
from pathlib import Path

# Columns follow the main collaborations. These are layout regions, not packages.
REGIONS = [
    (0, 0, "Presentation · org.zaproxy.addon.restextraction.ui", [
        ["RestExtractionPanel"],
        ["SpecificationPanel", "UnmatchedPathsPanel"],
        ["SpecItemForm", "PatternBuilderDialog"],
        ["SpecItemTableModel", "SpecField"],
        ["CallTableModel", "ApiDocumentationWriter"],
        ["CheckWriter"],
        ["JPanel", "JDialog", "AbstractTableModel", "AbstractPanel"],
    ]),
    (1, 0, "Coordination & reports · model", [
        ["RestExtractor", "ExtractionObserver"], ["ApiNamespace", "ApiReport"],
        ["Endpoint"], ["EndpointReport"],
    ]),
    (2, 0, "ZAP adapter · org.zaproxy.addon.restextraction", [
        ["ExtensionRestExtraction"], ["RestTrafficListener"],
        ["ExtensionAdaptor", "ExtensionHook", "ExtensionHookView"],
        ["ProxyListener", "HttpMessage"],
    ]),
    (0, 1, "Specification · org.zaproxy.addon.restextraction.model", [
        ["EndpointSpec"], ["OperationSpec"], ["SpecItem", "SpecItemKind"],
        ["ParameterSpec"], ["FieldName"],
    ]),
    (1, 1, "Verification results · model", [
        ["Verdict", "VerdictStatus"], ["Check"],
        ["Violation", "ViolationKind"], ["Warning", "WarningKind"],
    ]),
    (2, 1, "Captured request · model", [
        ["ApiCall"], ["RecordedCall"], ["RequestData"],
        ["Header", "BodyState", "HttpMethod"],
    ]),
    (0, 2, "Parameter types · model", [
        ["ParameterType"], ["StringType", "Base64Type"], ["IntegerType", "DecimalType"],
    ]),
    (1, 2, "Paths & matching · model", [
        ["UnmatchedPaths", "PatternDraft"], ["UrlPath", "EndpointPattern"],
        ["PathSegment"], ["LiteralSegment", "VariableSegment"],
    ]),
    (2, 2, "Request parsing · model", [
        ["RequestParser"], ["JsonReader", "MediaType"], ["ParsedRequest"],
        ["Argument"], ["ArgumentLocation", "JsonKind"],
    ]),
]


def rich(text):
    """Translate only our known Creole HTML, escaping generic type brackets."""
    parts = re.split(r"(</?(?:b|s|i|u)>|<color:#[0-9A-Fa-f]+>|</color>)", text)
    out = []
    for p in parts:
        if p.startswith("<color:"):
            out.append('<FONT COLOR="' + p[7:-1] + '">')
        elif p == "</color>":
            out.append("</FONT>")
        elif re.fullmatch(r"</?[bsiu]>", p):
            out.append(p.upper())
        else:
            out.append(html.escape(p.replace("<U+007B>", "{")))
    return "".join(out)


def label(name, body, colour, kind="class", stereo=None):
    title = f'<I>{name}</I>' if kind == 'abstract class' else name
    rows = []
    stereotype = stereo or ('interface' if kind == 'interface' else None)
    if stereotype:
        rows.append(f'<TR><TD ALIGN="CENTER">«{html.escape(stereotype)}»</TD></TR>')
    rows.append(f'<TR><TD ALIGN="CENTER"><B>{title}</B></TD></TR>')
    # Keep every member, but wrap long signatures at argument boundaries.
    for line in body:
        if line == "--":
            rows.append('<HR/>')
            continue
        if line.startswith("'") or not line.strip():
            continue
        plain = re.sub(r"<[^>]+>", "", line)
        if len(plain) > 95 and ", " in line:
            # Keep formatting tags balanced on each wrapped continuation.
            prefix = re.match(r"^(<color:[^>]+>)?(<b>)?", line).group()
            suffix = ("</b>" if "<b>" in prefix else "") + ("</color>" if "<color:" in prefix else "")
            text = line[len(prefix):]
            if suffix:
                text = text[:-len(suffix)]
            chunks = re.split(r"(?<=,) ", text)
            wrapped, current = [], ""
            for chunk in chunks:
                if len(current) + len(chunk) > 90 and current:
                    wrapped.append(current)
                    current = "    " + chunk
                else:
                    current += (" " if current else "") + chunk
            wrapped.append(current)
            for chunk in wrapped:
                rows.append(f'<TR><TD ALIGN="LEFT">{rich(prefix + chunk + suffix)}</TD></TR>')
        else:
            rows.append(f'<TR><TD ALIGN="LEFT">{rich(line)}</TD></TR>')
    fill, border, font = colour
    border_width = 1 if fill == '#F6F6F6' else 2
    return (f'<<TABLE BORDER="{border_width}" CELLBORDER="0" CELLSPACING="0" CELLPADDING="5" '
            f'BGCOLOR="{fill}" COLOR="{border}"><TR><TD><FONT COLOR="{font}">'
            '<TABLE BORDER="0" CELLBORDER="0" CELLSPACING="0" CELLPADDING="2">'
            + "".join(rows) + '</TABLE></FONT></TD></TR></TABLE>>')


def render(source, target, regions=REGIONS, title="US11 · Class diagram", work_dir=None):
    source, target = Path(source), Path(target)
    text = source.read_text()
    classes = {}
    for m in re.finditer(r'^\s*(abstract class|class|enum|interface) (\w+)(?: <<([^>]+)>>)? (#[^ ]+) \{\n(.*?)^\s*\}', text, re.M | re.S):
        kind, name, stereo, style, body = m.groups()
        if style.startswith("#C8E6C9"):
            colours = ("#C8E6C9", "#2E7D32", "#111111")
        elif style.startswith("#FFF3C4"):
            colours = ("#FFF3C4", "#C9A227", "#111111")
        else:
            colours = ("#F6F6F6", "#DDDDDD", "#A8A8A8")
        classes[name] = label(name, [s.strip() for s in body.splitlines()], colours, kind, stereo)
    assigned = [n for _, _, _, rows in regions for row in rows for n in row]
    assert len(set(assigned)) == len(assigned)
    assert set(assigned) == set(classes), (set(classes) - set(assigned), set(assigned) - set(classes))
    work = Path(work_dir) if work_dir else source.parent
    work.mkdir(parents=True, exist_ok=True)
    notes = {}
    for i, m in enumerate(re.finditer(r'^note (?:right of|as) (\w+)\n(.*?)^end note', text, re.M | re.S)):
        target_name, body = m.groups()
        lines = [html.escape(s.strip().replace('""', '').replace('**', '')) for s in body.splitlines()]
        notes[f'note_{i}'] = ('<<TABLE BORDER="1" CELLBORDER="0" CELLPADDING="8" BGCOLOR="#F0F7F0" COLOR="#91B496">'
                              f'<TR><TD ALIGN="LEFT"><B>Design note · {target_name}</B></TD></TR>'
                              + ''.join(f'<TR><TD ALIGN="LEFT">{s or " "}</TD></TR>' for s in lines)
                              + '</TABLE>>')
    legend = ('<TR><TD>Bold green member: new/changed · Grey strike-through: replaced/removed</TD></TR>'
              '<TR><TD>Functional regions are layout guides, not additional Java packages.</TD></TR>'
              if regions is REGIONS else '<TR><TD>Regions are layout guides, not additional domain concepts.</TD></TR>')
    notes['diagram_title'] = ('<<TABLE BORDER="0" CELLBORDER="0"><TR><TD><B>' + html.escape(title) + '</B></TD></TR>'
                              '<TR><TD>Green: new · Yellow: changed · Grey: unchanged US10</TD></TR>'
                              + legend + '</TABLE>>')
    labels = {**classes, **notes}
    nodes = [f'"{n}" [label={lab}];' for n, lab in labels.items()]
    common = 'node [shape=plain, fontname="Helvetica", fontsize=12];'
    measure = 'digraph G { ' + common + "\n" + "\n".join(nodes) + '\n}'
    data = json.loads(subprocess.check_output(['dot', '-Tjson'], input=measure.encode()))
    sizes = {o['name']: (float(o['width']) * 72, float(o['height']) * 72) for o in data['objects']}
    gap, pad, title_h, region_gap = 65, 28, 40, 160
    region_sizes = {}
    for col, row, title, shelves in regions:
        width = max(sum(sizes[n][0] for n in shelf) + gap * (len(shelf) - 1) for shelf in shelves) + pad * 2
        height = sum(max(sizes[n][1] for n in shelf) for shelf in shelves) + gap * (len(shelves) - 1) + pad * 2 + title_h
        region_sizes[col, row] = (width, height)
    cols = max(c for c, _ in region_sizes) + 1
    rows = max(r for _, r in region_sizes) + 1
    widths = [max(region_sizes.get((c, r), (0, 0))[0] for r in range(rows)) for c in range(cols)]
    heights = [max(region_sizes.get((c, r), (0, 0))[1] for c in range(cols)) for r in range(rows)]
    xs = [sum(widths[:c]) + region_gap * c for c in range(cols)]
    ys = [sum(heights[:r]) + region_gap * r + 110 for r in range(rows)]
    positions = {}
    frames = []
    for col, row, title, shelves in regions:
        x, y = xs[col], ys[row]
        width, height = region_sizes[col, row]
        frames.append((x, y, widths[col], height, title))
        cy = y + pad + title_h
        for shelf in shelves:
            shelf_w = sum(sizes[n][0] for n in shelf) + gap * (len(shelf) - 1)
            cx = x + (widths[col] - shelf_w) / 2
            shelf_h = max(sizes[n][1] for n in shelf)
            for n in shelf:
                w, h = sizes[n]
                positions[n] = (cx + w / 2, -(cy + h / 2))
                cx += w + gap
            cy += shelf_h + gap
    positions['diagram_title'] = (sum(widths) / 2 + region_gap, -35)
    note_y = sum(heights) + region_gap * rows + 110
    note_names = [n for n in notes if n != 'diagram_title']
    # Independent shelves avoid wide prose notes colliding with neighbouring notes.
    page_width = sum(widths) + region_gap * (cols - 1)
    shelf, shelf_width, shelves = [], 0, []
    for n in note_names:
        if shelf and (len(shelf) == 3 or shelf_width + sizes[n][0] + gap > page_width):
            shelves.append(shelf)
            shelf, shelf_width = [], 0
        shelf.append(n)
        shelf_width += sizes[n][0] + gap
    if shelf:
        shelves.append(shelf)
    for shelf in shelves:
        shelf_width = sum(sizes[n][0] for n in shelf) + gap * (len(shelf) - 1)
        cx = (page_width - shelf_width) / 2
        for n in shelf:
            w, h = sizes[n]
            positions[n] = (cx + w / 2, -(note_y + h / 2))
            cx += w + gap
        note_y += max(sizes[n][1] for n in shelf) + gap
    positioned = [f'"{n}" [pos="{positions[n][0]},{positions[n][1]}!", label={lab}];' for n, lab in labels.items()]
    edges = []
    # All visible relationships are copied, including both multiplicities and labels.
    for line in text.splitlines():
        m = re.match(r'^(\w+)(?: "([^"]*)")? ([^ ]*\[[^\]]+\][^ ]*)(?: "([^"]*)")? (\w+)(?: : (.*))?$', line)
        if not m:
            continue
        src, tail, arrow, head, dst, caption = m.groups()
        if 'hidden' in arrow:
            continue
        assert src in classes and dst in classes
        old = '#CCCCCC' in arrow
        colour = '#CCCCCC' if old else '#2E7D32'
        attrs = [f'color="{colour}"', f'fontcolor="{colour}"', f'penwidth={1 if old else 1.6}', 'arrowsize=0.7', 'arrowhead=none']
        if '..' in re.sub(r'\[[^\]]*\]', '', arrow):
            attrs.append('style=dashed')
        if '|>' in arrow:
            attrs.append('arrowhead=empty')
        elif arrow.endswith('>'):
            attrs.append('arrowhead=vee')
        if arrow.startswith('*'):
            attrs.extend(['dir=both', 'arrowtail=diamond'])
        elif arrow.startswith('o'):
            attrs.extend(['dir=both', 'arrowtail=odiamond'])
        if tail:
            attrs.append('taillabel=' + json.dumps(tail, ensure_ascii=False))
        if head:
            attrs.append('headlabel=' + json.dumps(head, ensure_ascii=False))
        if caption:
            caption = re.sub(r'<[^>]+>', '', caption)
            attrs.append('xlabel=' + json.dumps(caption, ensure_ascii=False))
        edges.append(f'"{src}" -> "{dst}" [{", ".join(attrs)}];')
    graph = ('digraph G { graph [splines=ortho, overlap=true, outputorder=edgesfirst, bgcolor="transparent", pad=0.9];\n'
             + common + '\nedge [fontname="Helvetica", fontsize=10, labelfontsize=10, labeldistance=2.5];\n'
             + '\n'.join(positioned + edges) + '\n}')
    (work / 'layout.dot').write_text(graph)
    svg = subprocess.check_output(['neato', '-n2', '-Tsvg'], input=graph.encode()).decode()
    # Background panels and headings do not take part in edge routing. Coordinates
    # share neato's point system (its graph group supplies the page transform).
    panels = []
    for x, y, w, h, title in frames:
        panels.append(f'<rect x="{x-12}" y="{y-12}" width="{w+24}" height="{h+24}" rx="8" fill="#FAFAFA" stroke="#D9DDE2"/>')
        panels.append(f'<text x="{x+14}" y="{y+22}" font-family="Helvetica" font-size="16" font-weight="bold" fill="#536170">{html.escape(title)}</text>')
    # In Graphviz SVG y is inverted, matching the negative fixed node positions.
    # neato normalizes positions: recover translation using a known node's polygon.
    import xml.etree.ElementTree as ET
    root = ET.fromstring(svg)
    ns = {'s': 'http://www.w3.org/2000/svg'}
    node_groups = [g for g in root.findall('.//s:g', ns) if g.get('class') == 'node']
    boxes = {}
    for g in node_groups:
        polygon = g.find('.//s:polygon', ns)
        if polygon is None:
            continue  # Borderless diagram heading.
        points = [tuple(map(float, p.split(','))) for p in polygon.get('points').split()]
        boxes[g.find('s:title', ns).text] = (min(x for x, _ in points), min(y for _, y in points),
                                              max(x for x, _ in points), max(y for _, y in points))
    for i, (name, (x1, y1, x2, y2)) in enumerate(boxes.items()):
        for other, (a1, b1, a2, b2) in list(boxes.items())[i + 1:]:
            assert min(x2, a2) <= max(x1, a1) or min(y2, b2) <= max(y1, b1), f'Overlapping boxes: {name}, {other}'
    hits = []
    for g in root.findall('.//s:g', ns):
        if g.get('class') != 'edge':
            continue
        endpoints = g.find('s:title', ns).text.split('->')
        path = g.find('s:path', ns)
        values = list(map(float, re.findall(r'-?\d+(?:\.\d+)?', path.get('d'))))
        points = list(zip(values[::2], values[1::2]))
        for name, (x1, y1, x2, y2) in boxes.items():
            if name in endpoints:
                continue
            for (a, b), (c, d) in zip(points, points[1:]):
                if ((abs(a-c) < 0.1 and x1 + 1 < a < x2 - 1 and min(max(b,d), y2-1) > max(min(b,d), y1+1)) or
                    (abs(b-d) < 0.1 and y1 + 1 < b < y2 - 1 and min(max(a,c), x2-1) > max(min(a,c), x1+1))):
                    hits.append((endpoints, name))
                    break
    assert not hits, f'Connectors cross unrelated boxes: {hits}'
    print('Geometry check: no overlapping boxes or connectors through unrelated boxes')
    first = node_groups[0]
    name = first.find('s:title', ns).text
    poly = first.find('.//s:polygon', ns)
    points = [tuple(map(float, p.split(','))) for p in poly.get('points').split()]
    # The outer table polygon encloses the measured node.
    dx = (min(p[0] for p in points) + max(p[0] for p in points))/2 - positions[name][0]
    dy = (min(p[1] for p in points) + max(p[1] for p in points))/2 + positions[name][1]
    panel_group = f'<g transform="translate({dx},{dy})">' + ''.join(panels) + '</g>'
    # Insert before edges so that every connector remains visible above the panels.
    marker = re.search(r'<g id="edge\d+"', svg)
    svg = svg[:marker.start()] + panel_group + svg[marker.start():]
    (work / 'layout.svg').write_text(svg)
    subprocess.run(['rsvg-convert', '-b', 'white', '-o', str(target), str(work / 'layout.svg')], check=True)
    print(f'Fixed layout: {len(classes)} classes, {len(edges)} relationships -> {target}')
