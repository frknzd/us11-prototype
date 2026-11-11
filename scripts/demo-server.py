#!/usr/bin/env python3
"""Deterministic loopback-only REST fixture. No third-party packages or external requests."""
import argparse
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlsplit


class Fixture(BaseHTTPRequestHandler):
    protocol_version = 'HTTP/1.1'

    def respond(self, status=200, head=False):
        length = int(self.headers.get('Content-Length', '0'))
        if not 0 <= length <= 131072:
            self.send_error(413, 'Demo fixture accepts at most 128 KiB')
            return
        body = self.rfile.read(length) if length else b''
        data = {'method': self.command, 'path': urlsplit(self.path).path,
                'query': urlsplit(self.path).query, 'bodyLength': len(body), 'ok': True}
        encoded = json.dumps(data, ensure_ascii=False).encode('utf-8') if status != 204 else b''
        self.send_response(status)
        self.send_header('Content-Type', 'application/json; charset=utf-8')
        self.send_header('Content-Length', str(len(encoded)))
        self.end_headers()
        if not head:
            self.wfile.write(encoded)

    def do_GET(self):
        self.respond()

    def do_HEAD(self):
        self.respond(head=True)

    def do_POST(self):
        self.respond(201)

    def do_PUT(self):
        self.respond()

    def do_PATCH(self):
        self.respond()

    def do_DELETE(self):
        self.respond()

    def do_OPTIONS(self):
        self.respond(204)

    def log_message(self, fmt, *args):
        # Never log request headers or bodies.
        print(f'fixture: {self.command} {urlsplit(self.path).path}', flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--port', type=int, default=8092)
    args = parser.parse_args()
    if not 1 <= args.port <= 65535:
        parser.error('port must be between 1 and 65535')
    try:
        server = ThreadingHTTPServer(('127.0.0.1', args.port), Fixture)
    except OSError as error:
        parser.exit(2, f'Cannot bind loopback demo port {args.port}: {error}\n')
    print(f'Demo fixture: http://127.0.0.1:{args.port} (Ctrl-C to stop)', flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == '__main__':
    main()
