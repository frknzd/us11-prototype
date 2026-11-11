#!/usr/bin/env python3
"""Draws the whole class diagram with the attention on what US11 changes.

    python3 diagrams/highlight_us11.py

Merges "US10 class diagram v3.puml" with the changes-only "US11 class diagram.puml":
- US10 classes that US11 does not touch: faded grey, name only;
- changed classes: yellow; US10 members grey, new or changed members bold green, a member whose
  signature changed is shown struck through in grey above its new version;
- new classes: green with a bold border;
- US10 relations grey, US11 relations bold green; only the US11 notes are kept.
Writes .local/us11-highlight/US11 class diagram highlighted.puml and renders it to
design/us11/US11 class diagram.png.
"""

import importlib.util
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("p2d", ROOT / "diagrams" / "puml2drawio.py")
P2D = importlib.util.module_from_spec(spec)
spec.loader.exec_module(P2D)

V3 = ROOT / "diagrams" / "US10 class diagram v3.puml"
US11 = ROOT / "design" / "us11" / "US11 class diagram.puml"
OUT_DIR = ROOT / ".local" / "us11-highlight"
OUT = OUT_DIR / "US11 class diagram highlighted.puml"
PNG_TARGET = ROOT / "design" / "us11" / "US11 class diagram.png"

GREY = "#A8A8A8"
GREEN = "#1B5E20"
FADED = "#F6F6F6;line:DDDDDD;text:A8A8A8"
CHANGED = "#FFF3C4;line:C9A227;line.bold"
NEW = "#C8E6C9;line:2E7D32;line.bold"
GREY_LINE = "#CCCCCC"
GREEN_LINE = "#2E7D32,bold"

PACKAGES = [  # (alias, title) in drawing order; US11's "ext" package is spread over zap and swing
    ("zap", "ZAP (existing)"),
    ("swing", "Java Swing (existing)"),
    ("adapter", "ZAP adapter layer (input), org.zaproxy.addon.restextraction"),
    ("ui", "Presentation layer (output), org.zaproxy.addon.restextraction.ui"),
    ("model", "Domain layer (org.zaproxy.addon.restextraction.model)"),
]
MOVE = {"JPanel": "swing", "JDialog": "swing", "AbstractTableModel": "swing", "HttpMessage": "zap"}

NOTE_TARGETS = {
    "N_SpecItem": "SpecItemForm", "N_Verify": "OperationSpec",
    "N_Types": "ParameterType", "N_Cache": "Endpoint",
    "N_Parse": "RequestParser", "N_Fields": "FieldName",
}


def read(path):
    classes, packages, notes, relations = P2D.parse(str(path))
    where = {}
    for alias, pkg in packages.items():
        for name in pkg["classes"]:
            where[name] = MOVE.get(name, alias)
    return classes, where, notes, relations


def key(line):
    """Name and parameter count of a member, to find the US10 version of a changed member."""
    text = line.replace("{static}", "").replace("{abstract}", "").strip().lstrip("+-#~ ")
    m = re.match(r"(\w+)\((.*?)\)", text)
    if m:
        args = [a for a in re.split(r",(?![^<]*>)", m.group(2)) if a.strip()]
        return ("m", m.group(1), len(args))
    return ("f", text.split(":")[0].strip(), 0)


def styled(line, colour, bold=False, strike=False):
    text = line.replace("<U+007B>", "{")
    text = text.replace("{abstract}", "").replace("{static}", "")
    text = " ".join(text.split())
    if "{static}" in line:
        text = f"<u>{text}</u>"
    if "{abstract}" in line:
        text = f"<i>{text}</i>"
    if strike:
        text = f"<s>{text}</s>"
    if bold:
        text = f"<b>{text}</b>"
    return f"        <color:{colour}>{text}</color>"


METHOD = re.compile(r"^[+\-#~]\s*(\{\w+\}\s*)?\w+\(")


def split_members(body):
    lines = [b for b in body if b not in ("--", "…") and not b.startswith("'")]
    fields = [b for b in lines if not METHOD.match(b)]
    methods = [b for b in lines if METHOD.match(b)]
    return fields, methods


def removed_keys(body):
    """Members the US11 source marks with "' removed: <member>"."""
    return {key(b.split("removed:", 1)[1].strip()) for b in body if b.startswith("' removed:")}


def header(kind, name, stereo, colour):
    keyword = "abstract class" if kind == "abstract class" else ("interface" if kind == "interface" else ("enum" if kind == "enum" else "class"))
    st = f" <<{stereo}>>" if stereo and stereo not in ("changed",) else ""
    return f"    {keyword} {name}{st} {colour} {{"


def changed_body(old_body, new_body):
    new_keys = {key(b): b for b in new_body if b not in ("--", "…") and not b.startswith("'")}
    removed = removed_keys(new_body)
    old_fields, old_methods = split_members(old_body)
    new_fields, new_methods = split_members(new_body)
    out_fields, out_methods = [], []
    for group_old, group_new, out in ((old_fields, new_fields, out_fields), (old_methods, new_methods, out_methods)):
        seen = set()
        for b in group_old:
            k = key(b)
            if k in new_keys:
                new = new_keys[k]
                if " ".join(new.split()) != " ".join(b.split()):
                    out.append(styled(b, GREY, strike=True))
                out.append(styled(new, GREEN, bold=True))
                seen.add(k)
            elif k in removed:
                out.append(styled(b, GREY, strike=True))
            else:
                out.append(styled(b, GREY))
        for b in group_new:
            if key(b) not in seen:
                out.append(styled(b, GREEN, bold=True))
    return out_fields + (["        --"] if out_fields and out_methods else []) + out_methods


def coloured_arrow(arrow, colour):
    a = re.sub(r"\[hidden\]|up|down|left|right", "", arrow)
    for i in range(len(a) - 1):
        if a[i] in "-." and a[i + 1] == a[i]:
            return a[: i + 1] + f"[{colour}]" + a[i + 1:]
    return a


def relation_key(src, arrow, dst):
    a = re.sub(r"\[hidden\]|up|down|left|right", "", arrow)
    return (src, a, dst)


def main():
    c10, w10, _, r10 = read(V3)
    c11, w11, n11, r11 = read(US11)
    lines = [
        "@startuml US11 class diagram highlighted",
        "' Generated by diagrams/highlight_us11.py from US10 v3 and the US11 changes. Do not edit.",
        "title US11 class diagram: what US11 adds and changes (grey = US10, unchanged)",
        "top to bottom direction",
        "skinparam shadowing false",
        "skinparam backgroundColor white",
        'skinparam defaultFontName "Segoe UI"',
        "skinparam classAttributeIconSize 0",
        "skinparam linetype polyline",
        "skinparam nodesep 45",
        "skinparam ranksep 75",
        "skinparam padding 8",
        "skinparam class {",
        "    BackgroundColor white",
        "    BorderColor #999999",
        "}",
        "skinparam package {",
        "    BackgroundColor #FBFBFB",
        "    BorderColor #DDDDDD",
        "    FontColor #A8A8A8",
        "    FontStyle normal",
        "    Style rectangle",
        "}",
        "skinparam note {",
        "    BackgroundColor #E8F5E9",
        "    BorderColor #2E7D32",
        "}",
        "hide circle",
        "hide empty members",
        "",
    ]
    names = list(dict.fromkeys(list(c10) + list(c11)))
    for alias, title in PACKAGES:
        members = [n for n in names if (w11.get(n) if n in c11 and c11[n].get("fill") else w10.get(n, w11.get(n))) == alias]
        if not members:
            continue
        lines.append(f'package "{title}" as {alias} {{')
        for name in members:
            emit_class(lines, name, c10, c11)
            for note, target in NOTE_TARGETS.items():
                if target == name:
                    lines.append(f"note right of {target}")
                    lines.extend("    " + t for t in n11[f"note:{note}"])
                    lines.append("end note")
        lines.append("}")
        lines.append("")

    lines += [
        "legend top right",
        "  <back:#C8E6C9> green </back> new class   <back:#FFF3C4> yellow </back> changed class   "
        "<back:#F6F6F6><color:#A8A8A8> grey </color></back> US10, unchanged",
        "  <color:#1B5E20><b>bold green member</b></color> new or changed   "
        "<color:#A8A8A8><s>struck through</s></color> replaced   <color:#A8A8A8>grey member</color> unchanged",
        "  bold green line: new relation   grey line: US10 relation",
        "endlegend",
        "",
    ]

    old_rel = {relation_key(s, a, d) for s, _, a, _, d, _ in r10}
    done = set()
    for rels, us11 in ((r10, False), (r11, True)):
        for src, sm, arrow, dm, dst, label in rels:
            if "hidden" in arrow:
                continue
            k = relation_key(src, arrow, dst)
            if k in done:
                continue
            done.add(k)
            is_new = us11 and k not in old_rel
            colour = GREEN_LINE if is_new else GREY_LINE
            text_colour = GREEN if is_new else GREY
            left = f'{src} "{sm}" ' if sm else f"{src} "
            right = f' "{dm}" {dst}' if dm else f" {dst}"
            lab = ""
            if label:
                lab = f" : <color:{text_colour}>{label.replace('<<', '«').replace('>>', '»')}</color>"
            lines.append(f"{left}{coloured_arrow(arrow, colour)}{right}{lab}")
    lines.append("@enduml")

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    OUT.write_text("\n".join(lines) + "\n")
    from layout_us11 import render

    render(OUT, PNG_TARGET)
    print(f"{OUT} -> {PNG_TARGET}")


def emit_class(lines, name, c10, c11):
    new = c11.get(name)
    old = c10.get(name)
    fill = (new or {}).get("fill")
    if new and fill and fill.upper() == "#E8F5E9":
        lines.append(header(new["kind"], name, new["stereo"], NEW))
        for b in new["body"]:
            lines.append("        --" if b == "--" else "        " + b)
        lines.append("    }")
    elif new and new["stereo"] == "changed":
        lines.append(header(old["kind"], name, None, CHANGED))
        lines.extend(changed_body(old["body"], new["body"]))
        lines.append("    }")
    else:
        info = old or new
        lines.append(header(info["kind"], name, None, FADED))
        lines.append("    }")


if __name__ == "__main__":
    main()
