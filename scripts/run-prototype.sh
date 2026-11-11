#!/bin/bash
# Run only this prototype; never install into the shared ZAP installation.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
CORE=${ZAP_CORE_DIR:-"$ROOT/../zaproxy"}
export US11_RUNTIME="$ROOT/.local/runtime"
export US11_PROFILE="$ROOT/.local/zap-profile"
export US11_PROXY_PORT=${US11_PROXY_PORT:-8091}
python3 - "$US11_PROXY_PORT" <<'PY'
import socket, sys
with socket.socket() as s:
    try:
        s.bind(('127.0.0.1', int(sys.argv[1])))
    except OSError:
        sys.exit('Prototype proxy port is occupied. Stop only the prototype or choose US11_PROXY_PORT.')
PY
cd "$ROOT"
./gradlew :addOns:restextraction:jarZapAddOn
mkdir -p "$US11_RUNTIME/plugin" "$US11_PROFILE"
# Stage assets only, not the shared plugin folder or a user's profile.
for asset in db lang license xml; do
    cp -R "$CORE/zap/src/main/dist/$asset" "$US11_RUNTIME/"
done
python3 - "$ROOT" "$CORE" <<'PY'
from pathlib import Path
import shutil, sys
root, core = map(Path, sys.argv[1:])
plugins = root / '.local/runtime/plugin'
for name in ('callhome', 'network'):
    archives = list((core / 'zap/src/main/dist/plugin').glob(name + '-*.zap'))
    if len(archives) != 1:
        sys.exit('Expected one existing mandatory archive for ' + name)
    shutil.copy2(archives[0], plugins / archives[0].name)
archives = list((root / 'addOns/restextraction/build/zapAddOn/bin').glob('restextraction-*.zap'))
if len(archives) != 1:
    sys.exit('Expected exactly one prototype archive')
for old in plugins.glob('restextraction-*.zap'):
    old.unlink()
shutil.copy2(archives[0], plugins / archives[0].name)
if {p.name.split('-')[0] for p in plugins.glob('*.zap')} != {'callhome', 'network', 'restextraction'}:
    sys.exit('Unexpected add-ons in isolated install; inspect before launching')
PY
cd "$CORE"
exec ./gradlew -I "$ROOT/scripts/zap-runtime.init.gradle" :zap:run
