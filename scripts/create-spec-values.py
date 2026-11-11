"""One-time creation of immutable specification result values; refuses overwrites."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]/'addOns/restextraction/src/main/java/org/zaproxy/addon/restextraction/model'
header='/* SPDX-License-Identifier: Apache-2.0 */\npackage org.zaproxy.addon.restextraction.model;\n\n'
for name,values in {
    'ViolationKind':'UNSPECIFIED_METHOD, UNSPECIFIED_CONTENT_TYPE, MALFORMED_BODY, EXTRA_ARGUMENT, INVALID_TYPE, OUT_OF_RANGE, MISSING_ARGUMENT',
    'VerdictStatus':'VALID, INVALID, NOT_VERIFIED',
    'SpecItemKind':'METHOD, CONTENT_TYPE, PATH_VARIABLE, QUERY_PARAMETER, BODY_FIELD, HEADER',
}.items():
    with (root/(name+'.java')).open('x') as f:f.write(header+'public enum '+name+' { '+values+' }\n')
with (root/'Violation.java').open('x') as f:f.write(header+'''import java.util.List;
import java.util.Objects;
public record Violation(ViolationKind kind, ArgumentLocation location, String name, String value, List<String> details) {
    public Violation { Objects.requireNonNull(kind, "kind"); Objects.requireNonNull(name, "name"); Objects.requireNonNull(value, "value"); details = List.copyOf(details); }
    public ViolationKind getKind() { return kind; }
    public ArgumentLocation getLocation() { return location; }
    public String getName() { return name; }
    public String getValue() { return value; }
    public List<String> getDetails() { return details; }
}
''')
with (root/'Check.java').open('x') as f:f.write(header+'''import java.util.Objects;
public record Check(Argument argument, ParameterSpec parameter, Violation violation) {
    public Check { Objects.requireNonNull(argument, "argument"); Objects.requireNonNull(parameter, "parameter"); }
    public boolean isMatched() { return violation == null; }
    public Argument getArgument() { return argument; }
    public ParameterSpec getParameter() { return parameter; }
    public Violation getViolation() { return violation; }
}
''')
