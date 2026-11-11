#!/usr/bin/env python3
"""Send generated requests explicitly through the loopback prototype proxy; ignores proxy env vars."""
import argparse
import http.client

CASES = {
    'baseline': [('GET', '/posts/1/comments?_limit=5', '', [])],
    'valid': [('GET', '/posts/1/comments?_limit=5', '', []),
              ('POST', '/posts/1/comments', '{"title":"Hello","userId":3}', [('Content-Type', 'application/json')])],
    'invalid': [('GET', '/posts/abc/comments?_limit=500&debug=1', '', []),
                ('PUT', '/posts/1/comments', '', []),
                ('POST', '/posts/1/comments', '{"title":null,"userId":"3"}', [('Content-Type', 'application/json')]),
                ('POST', '/posts/1/comments', 'title=Hi', [('Content-Type', 'text/plain')])],
    'malformed': [('POST', '/posts/1/comments', '{"title":"Hi",}', [('Content-Type', 'application/json')]),
                  ('GET', '/posts/1/comments?_limit=%FF', '', [])],
    'form': [('POST', '/posts/1/comments', 'title=Hi+there&userId=3', [('Content-Type', 'application/x-www-form-urlencoded')])],
    'nested': [('POST', '/posts/1/comments', '{"title":"Hi","user":{"id":3},"items":[{"id":1},{"id":2}],"scores":{"alice":0.3,"bob":0.30000000000000004}}', [('Content-Type', 'application/json')])],
    'duplicates': [('GET', '/posts/1/comments?_limit=5&_limit=x', '', [('X-Request-Id', '1'), ('x-request-id', '2')])],
    'precision': [('POST', '/posts/1/comments', '{"title":"Hi","userId":9007199254740993}', [('Content-Type', 'application/json')])],
    'preflight': [('OPTIONS', '/posts/1/comments', '', [('Origin', 'http://localhost'), ('Access-Control-Request-Method', 'POST')])],
    'unmatched': [('GET', '/new-resources/42', '', []), ('GET', '/new-resources/42', '', [])],
    'limits': [('POST', '/posts/1/comments', 'x' * 65537, [('Content-Type', 'application/json')]),
               ('GET', '/posts/1/comments?' + '&'.join(f'p{i}=1' for i in range(205)), '', [])],
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--proxy-port', type=int, default=8091)
    parser.add_argument('--fixture-port', type=int, default=8092)
    parser.add_argument('--case', choices=[*CASES, 'all'], default='baseline')
    args = parser.parse_args()
    if any(not 1 <= p <= 65535 for p in (args.proxy_port, args.fixture_port)):
        parser.error('ports must be between 1 and 65535')
    selected = CASES if args.case == 'all' else {args.case: CASES[args.case]}
    for group, cases in selected.items():
        for method, path, body, headers in cases:
            target = f'http://127.0.0.1:{args.fixture_port}{path}'
            payload = body.encode('utf-8')
            connection = http.client.HTTPConnection('127.0.0.1', args.proxy_port, timeout=10)
            try:
                connection.putrequest(method, target, skip_accept_encoding=True)
                for name, value in headers:
                    connection.putheader(name, value)
                if payload:
                    connection.putheader('Content-Length', str(len(payload)))
                connection.endheaders(payload)
                response = connection.getresponse()
                response.read()
                # Bound console output for the long-query limit fixture.
                shown = path if len(path) < 160 else path[:157] + '...'
                print(f'{group}: {method} {shown} -> HTTP {response.status}')
                if response.status >= 500:
                    parser.exit(1, 'Unexpected proxy/fixture failure; check isolated runtime logs.\n')
            except OSError as error:
                parser.exit(2, f'Loopback request failed: {error}. Start the prototype and demo server first.\n')
            finally:
                connection.close()


if __name__ == '__main__':
    main()
