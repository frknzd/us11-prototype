"""One-time creation of small immutable request model types; never overwrites files."""
from pathlib import Path
root = Path(__file__).resolve().parents[1] / 'addOns/restextraction/src/main/java/org/zaproxy/addon/restextraction/model'
header = '/* SPDX-License-Identifier: Apache-2.0 */\npackage org.zaproxy.addon.restextraction.model;\n\n'
for name, values in {
    'BodyState': 'NONE, TEXT, CUT, BINARY',
    'ArgumentLocation': 'PATH, QUERY, BODY, HEADER',
    'JsonKind': 'STRING, NUMBER, BOOLEAN, NULL, OBJECT, ARRAY',
    'WarningKind': 'REPEATED_PARAMETER, PRECISION, BODY_NOT_CHECKED, TOO_MANY_ARGUMENTS, TOO_DEEP',
}.items():
    with (root / (name + '.java')).open('x') as f:
        f.write(header + 'public enum ' + name + ' { ' + values + ' }\n')
