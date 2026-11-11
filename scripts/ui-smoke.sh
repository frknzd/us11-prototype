#!/bin/bash
# Opt-in UI acceptance; operates only on the isolated prototype with generated local traffic.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
if [ -z "${JAVA17_HOME:-}" ]; then
    if [ -x /opt/homebrew/opt/openjdk@17/bin/javac ]; then
        JAVA17_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
    elif [ -x /usr/libexec/java_home ]; then
        JAVA17_HOME=$(/usr/libexec/java_home -v 17)
    else
        JAVA17_HOME=${JAVA_HOME:?Set JAVA17_HOME to JDK 17}
    fi
fi
python3 - "$ROOT/.local/ui-smoke/result.txt" <<'PY'
from pathlib import Path
import sys
Path(sys.argv[1]).unlink(missing_ok=True)
PY
mkdir -p "$ROOT/.local/ui-smoke/classes"
"$JAVA17_HOME/bin/javac" --release 17 -d "$ROOT/.local/ui-smoke/classes" "$ROOT/scripts/PrototypeUiSmoke.java"
printf 'Premain-Class: PrototypeUiSmoke\n\n' > "$ROOT/.local/ui-smoke/MANIFEST.MF"
"$JAVA17_HOME/bin/jar" --create --file "$ROOT/.local/ui-smoke/agent.jar" --manifest "$ROOT/.local/ui-smoke/MANIFEST.MF" -C "$ROOT/.local/ui-smoke/classes" .
export US11_UI_AGENT="$ROOT/.local/ui-smoke/agent.jar=$ROOT/.local/ui-smoke"
exec "$ROOT/scripts/run-prototype.sh"
