#!/usr/bin/env python3
"""Render the domain model with fixed regions and orthogonal routing."""
from pathlib import Path
from layout_us11 import render

ROOT = Path(__file__).resolve().parents[1]
REGIONS = [
    (0, 0, "Observed API", [["ApiReport", "ApiNamespace"], ["EndpointPattern", "UnmatchedPath"]]),
    (2, 0, "Specification", [["Spec"], ["Operation"], ["ContentTypeItem", "ParameterItem"]]),
    (1, 1, "Request", [["ApiCall"], ["Request", "Header"], ["Argument"]]),
    (0, 1, "Path structure", [["PathSegment"], ["LiteralSegment", "VariableSegment"]]),
    (2, 1, "Value types", [["ParameterType"], ["StringType", "Base64Type"], ["IntegerType", "DecimalType"], ["Range"]]),
    (1, 0, "Verification results", [["Verdict"], ["Check"], ["Violation", "Warning"]]),
]

if __name__ == "__main__":
    render(ROOT / 'design/us11/US11 domain model.puml',
           ROOT / 'design/us11/US11 domain model.png', REGIONS,
           'US11 · Domain model', ROOT / '.local/us11-domain-layout')
