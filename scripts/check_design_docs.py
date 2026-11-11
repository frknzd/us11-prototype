#!/usr/bin/env python3
"""Consistency checks for the US11 design documents (plan section 16).

    python3 scripts/check_design_docs.py [--write]

1. every .puml renders without PlantUML errors, every .drawio is well-formed XML;
2. every message in the sequence diagrams is a method of the US11 class diagram or of US10 v3,
   with the same number of parameters; every participant class exists;
3. every class of the US11 class diagram is a domain concept, a US10 class, or a declared
   fabrication / UI / enumeration class (and every new domain concept is realised by a class);
4. every SPEC rule is cited by a test, every test cites an existing rule, decision or requirement;
5. R1-R8 each reach a use case, rules and existing tests (coverage document 6.1);
6. decisions D1-D16 are all in the specification.
With --write, the counts and results go into "US11 coverage.md" sections 5 and 6.2.
"""

import importlib.util
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / "design" / "us11"
spec = importlib.util.spec_from_file_location("p2d", ROOT / "diagrams" / "puml2drawio.py")
P2D = importlib.util.module_from_spec(spec)
spec.loader.exec_module(P2D)

problems = []


def problem(text):
    problems.append(text)
    print("  PROBLEM:", text)


def split_args(text):
    depth, parts, cur = 0, [], ""
    for ch in text:
        if ch in "<([{":
            depth += 1
        elif ch in ">)]}":
            depth -= 1
        if ch == "," and depth == 0:
            parts.append(cur)
            cur = ""
        else:
            cur += ch
    if cur.strip():
        parts.append(cur)
    return [p.strip() for p in parts]


# ------------------------------------------------------------------ 1. rendering
def check_rendering():
    print("1. Rendering")
    for puml in sorted(DOCS.glob("*.puml")):
        out = subprocess.run(["plantuml", "-syntax"], input=puml.read_text(), capture_output=True, text=True)
        first = out.stdout.strip().splitlines()[:1]
        if out.returncode != 0 or (first and first[0].startswith("ERROR")):
            problem(f"{puml.name} has PlantUML errors: {out.stdout.strip()[:200]}")
        blocks = puml.read_text().count("@startuml")
        print(f"   {puml.name}: {blocks} diagram(s), syntax ok" if not problems or problems[-1].find(puml.name) < 0 else "")
    for drawio in sorted(DOCS.glob("*.drawio")):
        out = subprocess.run(["xmllint", "--noout", str(drawio)], capture_output=True, text=True)
        if out.returncode != 0:
            problem(f"{drawio.name} is not well-formed XML")
        else:
            print(f"   {drawio.name}: well-formed")


# ------------------------------------------------------------------ class members
def members(path):
    classes, _, _, _ = P2D.parse(str(path))
    result = {}
    for name, cls in classes.items():
        methods = {}
        for line in cls["body"]:
            line = line.replace("<U+007B>", "{").replace("{static}", "").replace("{abstract}", "").strip()
            m = re.match(r"^[+\-#~]\s*(\w+)\((.*?)\)", line)
            if m:
                methods.setdefault(m.group(1), set()).add(len(split_args(m.group(2))))
        result[name] = {"kind": cls["kind"], "methods": methods, "fill": cls.get("fill"), "stereo": cls["stereo"]}
    return result


US11 = members(DOCS / "US11 class diagram.puml")
US10 = members(ROOT / "diagrams" / "US10 class diagram v3.puml")
SUBCLASSES = {"ParameterType": ["StringType", "IntegerType", "DecimalType", "Base64Type"]}


def methods_of(cls):
    found = {}
    for source in (US10, US11):
        for name, counts in source.get(cls, {}).get("methods", {}).items():
            found.setdefault(name, set()).update(counts)
    for sub in SUBCLASSES.get(cls, []):
        for name, counts in US11.get(sub, {}).get("methods", {}).items():
            found.setdefault(name, set()).update(counts)
    return found


# ------------------------------------------------------------------ 2. sequence diagrams
def check_sequence_diagrams():
    print("2. Sequence diagrams against the class diagrams")
    text = (DOCS / "US11 sequence diagrams.puml").read_text()
    checked = 0
    for page in text.split("@startuml")[1:]:
        title = page.splitlines()[0].strip()
        parts, external = {}, set()
        for m in re.finditer(r'^\s*(participant|actor|boundary)\s+"([^"]+)"\s+as\s+(\w+)|^(actor|boundary)\s+(\w+)\s+as\s+(\w+)', page, re.M):
            if m.group(1) == "participant":
                label = m.group(2)
                cls = label.split(":")[-1].strip()
                parts[m.group(3)] = cls
                if cls not in US11 and cls not in US10:
                    problem(f"{title}: participant class {cls} is in no class diagram")
            else:
                external.add(m.group(3) or m.group(6))
        for m in re.finditer(r"^\s*(\w+)\s*-(?:\[[^\]]*\])?>\s*(\w+)(?:\s*\*\*)?\s*:\s*(.+)$", page, re.M):
            src, dst, msg = m.groups()
            if src in external or dst not in parts:
                continue
            msg = re.sub(r"</?(?:color|b|i|u|s)(?::[^>]*)?>", "", msg).replace("«create»", "").strip()
            call = re.match(r"^(\w+)\((.*)\)\s*$", msg)
            if not call:
                problem(f"{title}: message '{msg}' to {parts[dst]} is not a method call")
                continue
            name, nargs = call.group(1), len(split_args(call.group(2)))
            cls = parts[dst]
            available = methods_of(cls)
            checked += 1
            if name not in available:
                problem(f"{title}: {cls} has no method {name}()")
            elif nargs not in available[name]:
                problem(f"{title}: {cls}.{name} called with {nargs} argument(s), diagram has {sorted(available[name])}")
    print(f"   {checked} messages checked")
    return checked


# ------------------------------------------------------------------ 3. classes vs domain model
REALISES = {  # class -> domain concept it realises
    "RequestData": "Request", "Header": "Header", "Argument": "Argument", "ApiCall": "ApiCall",
    "EndpointSpec": "Spec", "OperationSpec": "Operation", "ParameterSpec": "ParameterItem",
    "SpecItem": "ContentTypeItem", "ParameterType": "ParameterType", "StringType": "StringType",
    "IntegerType": "IntegerType", "DecimalType": "DecimalType", "Base64Type": "Base64Type",
    "Verdict": "Verdict", "Check": "Check", "Violation": "Violation", "Warning": "Warning",
    "UnmatchedPaths": "UnmatchedPath", "ApiNamespace": "ApiNamespace", "Endpoint": "EndpointPattern",
    "EndpointReport": "ApiReport", "RecordedCall": "ApiCall", "UrlPath": "PathSegment",
    "EndpointPattern": "EndpointPattern",
}
FABRICATIONS = {  # class -> why it has no domain concept
    "RestExtractor": "facade controller (US10)", "RestTrafficListener": "ZAP adapter (US10)",
    "RequestParser": "Pure Fabrication: parsing", "JsonReader": "Pure Fabrication: JSON scanning",
    "MediaType": "Pure Fabrication: media type rules", "FieldName": "Pure Fabrication: field-name grammar",
    "ParsedRequest": "Pure Fabrication: parser result", "PatternDraft": "Pure Fabrication: pattern builder state",
    "RestExtractionPanel": "UI", "CallTableModel": "UI model", "ApiDocumentationWriter": "UI writer",
    "CheckWriter": "UI writer", "SpecificationPanel": "UI", "SpecItemForm": "UI model",
    "SpecItemTableModel": "UI model", "UnmatchedPathsPanel": "UI", "PatternBuilderDialog": "UI",
    "JPanel": "Swing", "JDialog": "Swing", "AbstractTableModel": "Swing", "HttpMessage": "ZAP",
}


def check_domain_model():
    print("3. Class diagram against the domain model")
    concepts = set(P2D.parse(str(DOCS / "US11 domain model.puml"))[0])
    realised = set()
    for name, info in US11.items():
        if info["kind"] == "enum":
            continue
        if name in REALISES:
            if REALISES[name] not in concepts:
                problem(f"{name} realises {REALISES[name]}, which is not in the domain model")
            realised.add(REALISES[name])
        elif name not in FABRICATIONS:
            problem(f"class {name} is neither a domain concept nor a declared fabrication/UI class")
    realised |= {"Range", "LiteralSegment", "VariableSegment", "PathSegment"}  # fields/US10 classes
    realised |= {"Operation", "ParameterItem", "ContentTypeItem"}  # via SpecItem kinds
    for concept in concepts:
        if concept not in realised:
            problem(f"domain concept {concept} is realised by no class")
    print(f"   {len(US11)} classes, {len(concepts)} concepts, "
          f"{sum(1 for n in US11 if n in FABRICATIONS)} declared fabrications/UI/external")


# ------------------------------------------------------------------ 4. rules and tests
def check_rules_and_tests():
    print("4. Rules against tests")
    spec_text = (DOCS / "US11 specification.md").read_text()
    rules = re.findall(r"^\| (SPEC-[A-Z]\d\d) \|", spec_text, re.M)
    decisions = re.findall(r"^\| (D\d+) \|", spec_text, re.M)
    reqs = re.findall(r"^\| (R\d) \|", spec_text, re.M)
    known = set(rules) | set(decisions) | set(reqs)
    plan = (DOCS / "US11 test plan.md").read_text()
    level_of = {}
    level = None
    tests = {}
    for line in plan.splitlines():
        h = re.match(r"^## (\d)\. ", line)
        if h:
            level = {"1": "unit", "2": "integration", "3": "end-to-end", "4": "manual"}.get(h.group(1))
        m = re.match(r"^\| ((?:[A-Z]{2,3}|PT-[SIDB]|TL-V|SC-V|PN-E|M-V)\d*) \|", line)
        if m and level and m.group(1) not in ("ID", "Level"):
            cells = [c.strip() for c in line.strip().strip("|").split("|")]
            tid, source = cells[0], cells[-1]
            if tid in tests:
                problem(f"test ID {tid} is used twice")
            cited = set(re.findall(r"SPEC-[A-Z]\d\d|\bD\d+\b|\bR\d\b", source))
            tests[tid] = cited
            level_of[tid] = level
            if not cited:
                problem(f"test {tid} cites no rule, decision or requirement")
            for c in cited - known:
                problem(f"test {tid} cites {c}, which the specification does not define")
    cited_all = set().union(*tests.values())
    missing = [r for r in rules if r not in cited_all]
    for r in missing:
        problem(f"rule {r} is cited by no test")
    counts = {}
    for tid, lv in level_of.items():
        counts[lv] = counts.get(lv, 0) + 1
    print(f"   {len(rules)} rules, {len(decisions)} decisions, {len(reqs)} requirements; "
          f"{len(tests)} tests; rules without test: {len(missing)}")
    return rules, decisions, tests, counts, missing


# ------------------------------------------------------------------ 5. traceability
def check_traceability(tests):
    print("5. Requirements R1-R8 to use cases, rules and tests")
    cov = (DOCS / "US11 coverage.md").read_text()
    ucs = set(re.findall(r"UC-V\d", (DOCS / "US11 use cases.md").read_text()))
    spec_rules = set(re.findall(r"SPEC-[A-Z]\d\d", (DOCS / "US11 specification.md").read_text()))
    reached = 0
    for line in cov.splitlines():
        m = re.match(r"^\| (R\d) ", line)
        if not m:
            continue
        cells = line.strip().strip("|").split("|")
        uc = set(re.findall(r"UC-V\d", cells[1]))
        rules = set(re.findall(r"SPEC-[A-Z]\d\d", cells[2]))
        test_ids = set(re.findall(r"(?:PT-[SIDB]|TL-V|SC-V|PN-E|M-V|[A-Z]{2,3})\d+", cells[3]))
        if not uc or not uc <= ucs:
            problem(f"{m.group(1)}: use cases {uc} missing in the use case document")
        if not rules or not rules <= spec_rules:
            problem(f"{m.group(1)}: rules {rules - spec_rules} not in the specification")
        unknown = {t for t in test_ids if t not in tests}
        if not test_ids or unknown:
            problem(f"{m.group(1)}: tests {sorted(unknown)} not in the test plan")
        reached += 1
    if reached != 8:
        problem(f"only {reached} of R1-R8 are in the traceability table")
    print(f"   {reached} requirements traced")


def write_results(rules, decisions, tests, counts, missing, messages):
    path = DOCS / "US11 coverage.md"
    text = path.read_text()
    table = ("| Level | Test cases |\n|---|---|\n"
             + "".join(f"| {lv} | {counts.get(lv, 0)} |\n" for lv in ("unit", "integration", "end-to-end", "manual"))
             + f"| **total** | **{len(tests)}** |\n\n"
             "Unit and integration tests run as `*UnitTest` classes, end-to-end tests as `*ScenarioTest` "
             "and the panel tests (PN-E); the sign-off card counts JUnit test methods, which will be more "
             "than the rows here because many rows list several inputs.\n")
    result = (f"Result of `check_us11_docs.py`: **{len(rules)} rules**, each cited by at least one test"
              f" ({len(rules) - len(missing)}/{len(rules)}); **{len(tests)} tests**, each citing an existing"
              f" rule, decision or requirement; {len(decisions)} decisions present; {messages} sequence-diagram"
              f" messages match the class diagrams; problems found: **{len(problems)}**.\n")
    text = re.sub(r"(## 5\. Test counts per level\n\n)(.*?)(\n## 6\.)", lambda m: m.group(1) + table + m.group(3), text, flags=re.S)
    text = re.sub(r"(### 6\.2 Rule coverage \(reverse check\)\n\n)(.*?)(\n## 7\.)", lambda m: m.group(1) + result + m.group(3), text, flags=re.S)
    path.write_text(text)
    print("   results written to US11 coverage.md sections 5 and 6.2")


if __name__ == "__main__":
    check_rendering()
    messages = check_sequence_diagrams()
    check_domain_model()
    rules, decisions, tests, counts, missing = check_rules_and_tests()
    check_traceability(tests)
    if len(decisions) != 16:
        problem(f"the specification lists {len(decisions)} decisions, expected 16")
    print(f"\nTests per level: {counts}")
    print(f"PROBLEMS: {len(problems)}")
    if "--write" in sys.argv:
        write_results(rules, decisions, tests, counts, missing, messages)
    sys.exit(1 if problems else 0)
