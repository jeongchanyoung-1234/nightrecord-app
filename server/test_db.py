import json
import re
import threading
import urllib.error
import urllib.request
from pathlib import Path

import app as server

def record(run_id, timestamp, character='ashenone', win=True):
    return {
        'record_key': f'{run_id}:{timestamp}',
        'parser_version': 3,
        'payload': {
            'id': run_id,
            'timestamp': timestamp,
            'win': win,
            'jobId': 1,
            'durationMs': 1000,
            'character': character,
        },
    }

def test_archive_and_privacy(tmp_path):
    assert server.tag_from_key('0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef') == '04HMASW9'
    conn = server.connect(Path(tmp_path) / 'night.sqlite')
    server.CONN = conn
    codes = {}
    server.send_mail = lambda email, subject, body: codes.__setitem__(email, body)
    httpd = server.ThreadingHTTPServer(('127.0.0.1', 0), server.Handler)
    thread = threading.Thread(target=httpd.serve_forever, daemon=True)
    thread.start()
    port = httpd.server_address[1]

    def call(path, payload=None, token=None, method=None):
        data = None if payload is None else json.dumps(payload).encode()
        request = urllib.request.Request(
            f'http://127.0.0.1:{port}{path}',
            data=data,
            method=method or ('POST' if payload is not None else 'GET'),
            headers={'Content-Type': 'application/json', **({'Authorization': f'Bearer {token}'} if token else {})},
        )
        try:
            with urllib.request.urlopen(request) as response:
                return response.status, json.loads(response.read().decode())
        except urllib.error.HTTPError as error:
            return error.code, json.loads(error.read().decode())

    def finish_signup(username, password):
        status, session = call('/api/auth/signup', {'username': username, 'password': password})
        assert status == 200 and session['user']['email'] == username.lower()
        return session

    session_a = finish_signup('ashenone', 'night-record')
    status, duplicate = call('/api/auth/signup', {'username': 'AshenOne', 'password': 'night-record'})
    assert status == 409
    status, rejected = call('/api/auth/login', {'username': 'ashenone', 'password': 'wrong-password'})
    assert status == 401
    session_b = finish_signup('ookami', 'other-record')
    key = 'a' * 64
    first = [record(i, 1_700_000_000_000 + i * 1000, 'ashenone', i % 2 == 0) for i in range(1, 101)]
    status, published = call('/api/profiles/publish', {'profile_key': key, 'characters': ['ashenone', 'ookami'], 'records': first}, session_a['access_token'])
    assert status == 200 and published['added'] == 100
    second = [record(i, 1_700_000_000_000 + i * 1000, 'ookami', True) for i in range(81, 121)]
    status, again = call('/api/profiles/publish', {'profile_key': key, 'characters': ['ashenone', 'ookami'], 'records': second}, session_a['access_token'])
    assert status == 200 and again['added'] == 20 and again['total'] == 120
    assert 'ashenone' in again['names'] and 'ookami' in again['names']
    status, denied = call('/api/profiles/publish', {'profile_key': key, 'characters': ['ashenone'], 'records': second}, session_b['access_token'])
    assert status == 403
    status, suggested = call('/api/profiles/suggest', {'query': 'ash'})
    assert status == 200 and len(suggested) == 1 and suggested[0]['name'] == 'ashenone' and 'names' not in suggested[0]
    status, too_short = call('/api/profiles/suggest', {'query': 'as'})
    assert status == 200 and too_short == []
    status, mine = call('/api/me', token=session_a['access_token'], method='GET')
    profile_id = mine['profiles'][0]['id']
    status, tagged = call('/api/me/tag', {'id': profile_id, 'tag': 'nightowl'}, session_a['access_token'])
    assert status == 200 and tagged['tag'] == 'nightowl' and tagged['displayTag'] == 'nightowl' and tagged['saveId'] == again['tag'] and 'ashenone' in tagged['names']
    status, still = call('/api/profiles/suggest', {'query': 'ash'})
    assert status == 200 and still[0]['name'] == 'ashenone' and still[0]['tag'] == 'nightowl' and 'custom' not in still[0]
    status, by_custom = call('/api/profiles/suggest', {'query': 'night'})
    assert status == 200 and by_custom[0]['name'] is None and by_custom[0]['tag'] == 'nightowl'
    status, forbidden = call('/api/me/tag', {'id': profile_id, 'tag': 'other'}, session_b['access_token'])
    assert status == 404
    status, overview = call('/api/me', token=session_a['access_token'], method='GET')
    assert status == 200 and overview['email'] == 'ashenone' and overview['profiles'][0]['tag'] == 'nightowl'
    status, changed = call('/api/me/password', {'current_password': 'night-record', 'password': 'new-record-1'}, session_a['access_token'])
    assert status == 200 and changed == {}
    status, old_login = call('/api/auth/login', {'username': 'ashenone', 'password': 'night-record'})
    assert status == 401
    status, new_login = call('/api/auth/login', {'username': 'ashenone', 'password': 'new-record-1'})
    assert status == 200 and new_login['user']['email'] == 'ashenone'
    _, found = call('/api/profiles/search', {'query': 'ookami#04HMASW9'.replace('04HMASW9', again['tag'])})
    assert found and found[0]['tag'] == 'nightowl' and found[0]['matches'] == 120
    _, detail = call('/api/profiles/detail', {'profile_tag': 'nightowl'})
    assert detail['tag'] == 'nightowl' and len(detail['runs']) == 120
    _, legacy = call('/api/profiles/detail', {'profile_tag': again['tag']})
    assert legacy['tag'] == 'nightowl'
    private = [record(1, 5, 'secret')]
    call('/api/expeditions/merge', {'records': private}, session_a['access_token'])
    _, own = call(f'/api/expeditions?offset=0', token=session_a['access_token'], method='GET')
    _, other = call(f'/api/expeditions?offset=0', token=session_b['access_token'], method='GET')
    assert len(own) == 1 and other == []
    with urllib.request.urlopen(f'http://127.0.0.1:{port}/') as response:
        assert '밤의 기록'.encode() in response.read()
    httpd.shutdown()
    print('db-ok', again['tag'])

if __name__ == '__main__':
    import tempfile
    with tempfile.TemporaryDirectory() as directory:
        test_archive_and_privacy(directory)
