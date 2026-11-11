#!/usr/bin/env python3
"""Stop only the listener whose command identifies this repository's isolated runtime/profile."""
import argparse
import os
from pathlib import Path
import signal
import subprocess
import time

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--proxy-port', type=int, default=int(os.environ.get('US11_PROXY_PORT', '8091')))
args = parser.parse_args()
if not 1 <= args.proxy_port <= 65535:
    parser.error('port must be between 1 and 65535')
found = subprocess.run(['lsof', '-nP', '-t', f'-iTCP:{args.proxy_port}', '-sTCP:LISTEN'], capture_output=True, text=True)
pids = set(found.stdout.split())
if not pids:
    print('No prototype listener is running on the selected port.')
for pid in pids:
    command = subprocess.check_output(['ps', '-p', pid, '-o', 'command='], text=True)
    if not all(marker in command for marker in ('org.zaproxy.zap.ZAP', str(root / '.local/zap-profile'), str(root / '.local/runtime'))):
        parser.exit(2, 'Refusing to stop a process that is not this isolated prototype.\n')
    os.kill(int(pid), signal.SIGTERM)
    print(f'Sent shutdown to prototype process {pid}; no other ZAP process was touched.')
for _ in range(50):
    if not any(subprocess.run(['ps', '-p', pid], capture_output=True).returncode == 0 for pid in pids):
        break
    time.sleep(0.2)
