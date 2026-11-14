#!/usr/bin/env python3
"""이 서버에서 사이트와 전적 데이터베이스를 함께 제공합니다."""

import hashlib
import hmac
import json
import mimetypes
import os
import re
import secrets
import smtplib
import sqlite3
import threading
import time
import uuid
from email.message import EmailMessage
from email.utils import formataddr
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import unquote, urlparse

ROOT = Path(__file__).resolve().parent.parent
DB_PATH = Path(os.environ.get('NIGHTRECORD_DB', ROOT / 'data' / 'nightrecord.sqlite'))
HOST = os.environ.get('NIGHTRECORD_HOST', '127.0.0.1')
PORT = int(os.environ.get('NIGHTRECORD_PORT', '8080'))
TOKEN_TTL = 60 * 60 * 12
REFRESH_TTL = 60 * 60 * 24 * 30
CODE_TTL = 60 * 10
MAX_BODY = 1_500_000
TAG_ALPHABET = '0123456789ABCDEFGHJKMNPQRSTVWXYZ'
LOCK = threading.Lock()
# 비로그인 업로드 프로필의 소유자. '#'이 들어가 로그인 아이디로는 쓸 수 없음.
GUEST_ID = '00000000-0000-0000-0000-000000000000'
GUEST_LOGIN = '#guest'
CONN = None

class ApiError(Exception):
    def __init__(self, status, message):
        super().__init__(message)
        self.status = status
        self.message = message

def now():
    return int(time.time())

def tag_from_key(profile_key):
    if not re.fullmatch(r'[0-9a-f]{64}', profile_key or ''):
        raise ApiError(400, '프로필 키가 올바르지 않습니다.')
    value = int(profile_key[:10], 16)
    tag = ''
    for _ in range(8):
        tag = TAG_ALPHABET[value & 31] + tag
        value >>= 5
    return tag

def connect(path=DB_PATH):
    path.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(path, check_same_thread=False)
    conn.row_factory = sqlite3.Row
    conn.execute('pragma foreign_keys = on')
    conn.execute('pragma journal_mode = wal')
    conn.executescript('''
        create table if not exists users (
          id text primary key,
          email text not null unique,
          password_hash text,
          created_at integer not null
        );
        create table if not exists login_attempts (
          email text primary key,
          failures integer not null,
          window_start integer not null
        );
        create table if not exists sessions (
          token_hash text primary key,
          user_id text not null references users(id) on delete cascade,
          refresh_hash text not null unique,
          expires_at integer not null,
          refresh_expires_at integer not null
        );
        create table if not exists login_codes (
          email text primary key,
          code_hash text not null,
          expires_at integer not null,
          attempts integer not null default 0,
          sent_at integer not null
        );
        create table if not exists pending_signups (
          email text primary key,
          password_hash text not null,
          code_hash text not null,
          expires_at integer not null,
          attempts integer not null default 0,
          sent_at integer not null
        );
        create table if not exists expeditions (
          user_id text not null references users(id) on delete cascade,
          record_key text not null,
          payload text not null,
          parser_version integer not null,
          created_at integer not null,
          updated_at integer not null,
          primary key (user_id, record_key)
        );
        create table if not exists profiles (
          id text primary key,
          profile_key text not null unique,
          owner_id text not null references users(id) on delete cascade,
          tag text not null unique,
          characters text not null,
          aliases text not null,
          matches integer not null default 0,
          wins integer not null default 0,
          last_played integer,
          created_at integer not null,
          updated_at integer not null
        );
        create table if not exists profile_runs (
          profile_id text not null references profiles(id) on delete cascade,
          record_key text not null,
          payload text not null,
          parser_version integer not null,
          created_at integer not null,
          updated_at integer not null,
          primary key (profile_id, record_key)
        );
        create table if not exists relic_inventories (
          user_id text not null references users(id) on delete cascade,
          profile_key text not null,
          payload text not null,
          updated_at integer not null,
          primary key (user_id, profile_key)
        );
        create table if not exists access_events (
          id integer primary key autoincrement,
          ts integer not null,
          path text not null,
          method text not null,
          status integer not null,
          ip text not null,
          host text not null,
          ua text not null,
          kind text not null
        );
    ''')
    columns = {row[1] for row in conn.execute('pragma table_info(users)')}
    if 'password_hash' not in columns:
        conn.execute('alter table users add column password_hash text')
    if 'is_admin' not in columns:
        conn.execute('alter table users add column is_admin integer not null default 0')
    conn.execute('create index if not exists access_events_ts on access_events(ts)')
    conn.execute('create index if not exists access_events_kind_ts on access_events(kind, ts)')
    profile_columns = {row[1] for row in conn.execute('pragma table_info(profiles)')}
    if 'extra_names' not in profile_columns:
        conn.execute("alter table profiles add column extra_names text not null default '[]'")
    if 'hidden_names' not in profile_columns:
        conn.execute("alter table profiles add column hidden_names text not null default '[]'")
    if 'custom_tags' not in profile_columns:
        conn.execute("alter table profiles add column custom_tags text not null default '[]'")
    added_display = 'display_tag' not in profile_columns
    if added_display:
        conn.execute('alter table profiles add column display_tag text')
    conn.execute('create unique index if not exists profiles_display_tag on profiles(display_tag collate nocase)')
    if added_display:
        for row in conn.execute('select id, custom_tags from profiles').fetchall():
            tags = custom_tags_of(row)
            if tags:
                conn.execute('update profiles set display_tag = ? where id = ?', (tags[0], row['id']))
    profile_columns = {row[1] for row in conn.execute('pragma table_info(profiles)')}
    if 'deep_ratings' not in profile_columns:
        conn.execute("alter table profiles add column deep_ratings text not null default '[]'")
    if 'avatar' not in profile_columns:
        conn.execute("alter table profiles add column avatar text not null default ''")
    if 'peak_rating' not in profile_columns:
        conn.execute('alter table profiles add column peak_rating integer not null default 0')
    if 'peak_depth' not in profile_columns:
        conn.execute('alter table profiles add column peak_depth integer not null default 0')
    ensure_guest(conn)
    ensure_admin(conn)
    prune_access_events(conn)
    conn.commit()
    return conn

def ensure_guest(conn):
    conn.execute(
        'insert into users (id, email, password_hash, created_at) values (?, ?, null, ?) on conflict(id) do nothing',
        (GUEST_ID, GUEST_LOGIN, now()),
    )

def ensure_admin(conn):
    username = normalize_login(os.environ.get('NIGHTRECORD_ADMIN_USERNAME', 'jcy1996'))
    password = os.environ.get('NIGHTRECORD_ADMIN_PASSWORD', '')
    if not username or not password:
        return
    if not valid_username(username) or not valid_password(password):
        print('관리자 계정 환경변수가 올바르지 않아 시드를 건너뜁니다.')
        return
    row = conn.execute('select id, password_hash, is_admin from users where lower(email) = ?', (username,)).fetchone()
    if row is None:
        conn.execute(
            'insert into users (id, email, password_hash, created_at, is_admin) values (?, ?, ?, ?, 1)',
            (str(uuid.uuid4()), username, hash_password(password), now()),
        )
        return
    updates = []
    params = []
    if not row['is_admin']:
        updates.append('is_admin = 1')
    if not row['password_hash'] or not check_password(password, row['password_hash']):
        updates.append('password_hash = ?')
        params.append(hash_password(password))
    if updates:
        params.append(row['id'])
        conn.execute(f"update users set {', '.join(updates)} where id = ?", params)

def prune_access_events(conn, keep_days=90):
    cutoff = now() - keep_days * 86400
    conn.execute('delete from access_events where ts < ?', (cutoff,))

AVATAR_IDS = {
    'wylder', 'guardian', 'ironeye', 'dutchess', 'raider',
    'revenant', 'recluse', 'executor', 'scholar', 'undertaker',
}

def hash_text(value):
    return hashlib.sha256(value.encode()).hexdigest()

def json_body(handler):
    length = int(handler.headers.get('Content-Length', '0'))
    if length < 0 or length > MAX_BODY:
        raise ApiError(413, '요청이 너무 큽니다.')
    raw = handler.rfile.read(length) if length else b''
    if not raw:
        return {}
    try:
        data = json.loads(raw)
    except json.JSONDecodeError:
        raise ApiError(400, 'JSON 형식이 아닙니다.')
    if not isinstance(data, dict):
        raise ApiError(400, '요청 형식이 올바르지 않습니다.')
    return data

def bearer(handler):
    header = handler.headers.get('Authorization', '')
    if not header.startswith('Bearer '):
        raise ApiError(401, '먼저 이메일로 로그인해 주세요.')
    return header[7:].strip()

def user_payload(user):
    return {
        'id': user['id'],
        'email': user['email'],
        'isAdmin': bool(user['is_admin']) if 'is_admin' in user.keys() else False,
    }

def user_from_token(conn, token):
    row = conn.execute(
        'select user_id, expires_at from sessions where token_hash = ?',
        (hash_text(token),),
    ).fetchone()
    if row is None or row['expires_at'] < now():
        raise ApiError(401, '로그인이 만료되었습니다.')
    user = conn.execute('select id, email, is_admin from users where id = ?', (row['user_id'],)).fetchone()
    if user is None:
        raise ApiError(401, '로그인이 만료되었습니다.')
    return user_payload(user)

def require_admin(conn, token):
    user = user_from_token(conn, token)
    if not user.get('isAdmin'):
        raise ApiError(403, '관리자만 접근할 수 있습니다.')
    return user

def issue_session(conn, user_id):
    access = secrets.token_urlsafe(32)
    refresh = secrets.token_urlsafe(32)
    current = now()
    conn.execute(
        'insert into sessions (token_hash, user_id, refresh_hash, expires_at, refresh_expires_at) values (?, ?, ?, ?, ?)',
        (hash_text(access), user_id, hash_text(refresh), current + TOKEN_TTL, current + REFRESH_TTL),
    )
    user = conn.execute('select id, email, is_admin from users where id = ?', (user_id,)).fetchone()
    return {
        'access_token': access,
        'refresh_token': refresh,
        'expires_at': current + TOKEN_TTL,
        'user': user_payload(user),
    }

def client_ip(handler):
    forwarded = (handler.headers.get('X-Real-IP') or '').strip()
    if forwarded:
        return forwarded[:64]
    xff = (handler.headers.get('X-Forwarded-For') or '').split(',')[0].strip()
    if xff:
        return xff[:64]
    return (handler.client_address[0] if handler.client_address else '')[:64]

def should_log_path(path, kind):
    if kind == 'api':
        if path.startswith('/api/admin/'):
            return False
        return True
    if path in ('', '/', '/index.html', '/me.html', '/login.html', '/signup.html', '/relics.html', '/admin.html', '/reset.html'):
        return True
    if path.endswith('.html'):
        return True
    return False

def record_access(handler, path, status, kind):
    if not should_log_path(path, kind):
        return
    try:
        ua = (handler.headers.get('User-Agent') or '')[:240]
        host = (handler.headers.get('X-Forwarded-Host') or handler.headers.get('Host') or '')[:120]
        CONN.execute(
            'insert into access_events (ts, path, method, status, ip, host, ua, kind) values (?, ?, ?, ?, ?, ?, ?, ?)',
            (now(), path[:200], handler.command[:12], int(status), client_ip(handler), host, ua, kind),
        )
    except Exception:
        pass

def admin_stats(conn):
    current = now()
    day = 86400
    users = conn.execute('select count(*) as n from users where id != ?', (GUEST_ID,)).fetchone()['n']
    profiles = conn.execute('select count(*) as n from profiles').fetchone()['n']
    matches = conn.execute('select coalesce(sum(matches), 0) as n from profiles').fetchone()['n']
    wins = conn.execute('select coalesce(sum(wins), 0) as n from profiles').fetchone()['n']
    expeditions = conn.execute('select count(*) as n from expeditions').fetchone()['n']
    profile_runs = conn.execute('select count(*) as n from profile_runs').fetchone()['n']
    sessions = conn.execute('select count(*) as n from sessions where expires_at > ?', (current,)).fetchone()['n']

    def page_count(since):
        return conn.execute(
            "select count(*) as n from access_events where kind = 'page' and ts >= ?",
            (since,),
        ).fetchone()['n']

    views_today = page_count(current - day)
    views_7d = page_count(current - 7 * day)
    views_30d = page_count(current - 30 * day)

    daily_rows = conn.execute(
        """
        select strftime('%Y-%m-%d', ts, 'unixepoch') as day, count(*) as n
        from access_events
        where kind = 'page' and ts >= ?
        group by day
        order by day
        """,
        (current - 14 * day,),
    ).fetchall()
    daily = [{'day': row['day'], 'views': row['n']} for row in daily_rows]

    top_paths = [
        {'path': row['path'], 'views': row['n']}
        for row in conn.execute(
            """
            select path, count(*) as n from access_events
            where kind = 'page' and ts >= ?
            group by path order by n desc limit 12
            """,
            (current - 7 * day,),
        ).fetchall()
    ]
    top_hosts = [
        {'host': row['host'] or '(unknown)', 'views': row['n']}
        for row in conn.execute(
            """
            select host, count(*) as n from access_events
            where kind = 'page' and ts >= ?
            group by host order by n desc limit 8
            """,
            (current - 7 * day,),
        ).fetchall()
    ]
    recent = [
        {
            'ts': row['ts'],
            'path': row['path'],
            'ip': row['ip'],
            'host': row['host'],
            'status': row['status'],
            'ua': row['ua'],
        }
        for row in conn.execute(
            """
            select ts, path, ip, host, status, ua from access_events
            where kind = 'page'
            order by id desc limit 40
            """
        ).fetchall()
    ]
    recent_users = [
        {'email': row['email'], 'createdAt': row['created_at'], 'isAdmin': bool(row['is_admin'])}
        for row in conn.execute(
            'select email, created_at, is_admin from users where id != ? order by created_at desc limit 15',
            (GUEST_ID,),
        ).fetchall()
    ]
    api_top = [
        {'path': row['path'], 'hits': row['n']}
        for row in conn.execute(
            """
            select path, count(*) as n from access_events
            where kind = 'api' and ts >= ?
            group by path order by n desc limit 12
            """,
            (current - 7 * day,),
        ).fetchall()
    ]
    return {
        'generatedAt': current,
        'overview': {
            'users': users,
            'profiles': profiles,
            'matches': matches,
            'wins': wins,
            'expeditions': expeditions,
            'profileRuns': profile_runs,
            'activeSessions': sessions,
            'pageViewsToday': views_today,
            'pageViews7d': views_7d,
            'pageViews30d': views_30d,
        },
        'dailyPageViews': daily,
        'topPaths': top_paths,
        'topHosts': top_hosts,
        'topApis': api_top,
        'recentPageViews': recent,
        'recentUsers': recent_users,
    }

def valid_email(email):
    return isinstance(email, str) and re.fullmatch(r'[^@\s]{1,64}@[^@\s]{1,190}', email) and len(email) <= 254

def valid_username(username):
    # 영문으로 시작, 3~24자 영문·숫자·_
    return isinstance(username, str) and re.fullmatch(r'[A-Za-z][A-Za-z0-9_]{2,23}', username) is not None

def valid_login(login):
    # 신규는 아이디, 기존 계정은 이메일 로그인 유지
    return valid_username(login) or valid_email(login)

def normalize_login(login):
    return (login or '').strip().lower()

def mail_text(purpose, code):
    site = os.environ.get('NIGHTRECORD_PUBLIC_URL', 'https://nightrecord.app').rstrip('/')
    return (
        f'밤의 기록 {purpose} 코드입니다.\n\n'
        f'{code}\n\n'
        '이 코드는 10분 동안 사용할 수 있습니다.\n'
        '직접 요청하지 않았다면 이 메일을 무시해 주세요.\n\n'
        f'{site}\n'
    )

def send_mail(email, subject, body):
    host = os.environ.get('NIGHTRECORD_SMTP_HOST', '')
    if not host:
        raise ApiError(503, '인증 메일을 보내려면 SMTP 설정이 필요합니다.')
    sender = os.environ.get('NIGHTRECORD_SMTP_FROM', os.environ.get('NIGHTRECORD_SMTP_USER', 'nightrecord@localhost'))
    message = EmailMessage()
    message['Subject'] = subject
    message['From'] = formataddr(('밤의 기록', sender))
    message['To'] = email
    message.set_content(body)
    port = int(os.environ.get('NIGHTRECORD_SMTP_PORT', '587'))
    with smtplib.SMTP(host, port, timeout=20) as smtp:
        if port != 25:
            smtp.starttls()
        user = os.environ.get('NIGHTRECORD_SMTP_USER', '')
        password = os.environ.get('NIGHTRECORD_SMTP_PASSWORD', '')
        if user:
            smtp.login(user, password)
        smtp.send_message(message)

def valid_password(password):
    return isinstance(password, str) and 8 <= len(password) <= 72

def hash_password(password):
    salt = secrets.token_bytes(16)
    digest = hashlib.scrypt(password.encode(), salt=salt, n=2**14, r=8, p=1, dklen=32)
    return f'scrypt${salt.hex()}${digest.hex()}'

def check_password(password, stored):
    try:
        scheme, salt_hex, digest_hex = stored.split('$')
        if scheme != 'scrypt':
            return False
        digest = hashlib.scrypt(password.encode(), salt=bytes.fromhex(salt_hex), n=2**14, r=8, p=1, dklen=32)
        return hmac.compare_digest(digest.hex(), digest_hex)
    except (ValueError, TypeError):
        return False

def note_failure(conn, email):
    current = now()
    row = conn.execute('select failures, window_start from login_attempts where email = ?', (email,)).fetchone()
    if row is None or current - row['window_start'] > 900:
        conn.execute(
            'insert into login_attempts (email, failures, window_start) values (?, 1, ?) on conflict(email) do update set failures = 1, window_start = excluded.window_start',
            (email, current),
        )
        return
    if row['failures'] >= 8:
        raise ApiError(429, '시도가 너무 많습니다. 15분 뒤에 다시 로그인해 주세요.')
    conn.execute('update login_attempts set failures = failures + 1 where email = ?', (email,))

def clear_failures(conn, email):
    conn.execute('delete from login_attempts where email = ?', (email,))

def guard_attempts(conn, email):
    row = conn.execute('select failures, window_start from login_attempts where email = ?', (email,)).fetchone()
    if row and now() - row['window_start'] <= 900 and row['failures'] >= 8:
        raise ApiError(429, '시도가 너무 많습니다. 15분 뒤에 다시 로그인해 주세요.')

def register(conn, username, password):
    if not valid_username(username):
        raise ApiError(400, '아이디는 영문으로 시작해 3~24자의 영문·숫자·_ 만 사용할 수 있습니다.')
    if not valid_password(password):
        raise ApiError(400, '비밀번호는 8자 이상 72자 이하로 입력해 주세요.')
    login = normalize_login(username)
    if conn.execute('select id from users where lower(email) = ?', (login,)).fetchone():
        raise ApiError(409, '이미 사용 중인 아이디입니다. 로그인해 주세요.')
    user_id = str(uuid.uuid4())
    conn.execute(
        'insert into users (id, email, password_hash, created_at) values (?, ?, ?, ?)',
        (user_id, login, hash_password(password), now()),
    )
    return issue_session(conn, user_id)

# 하위 호환: 예전 클라이언트의 이메일 인증 가입 경로
def request_signup(conn, email, password):
    return register(conn, email, password)

def verify_signup(conn, email, code):
    raise ApiError(400, '이메일 인증 가입은 더 이상 사용하지 않습니다. 아이디로 바로 가입해 주세요.')

def sign_in(conn, email, password):
    if not valid_login(email) or not isinstance(password, str):
        raise ApiError(401, '아이디 또는 비밀번호가 올바르지 않습니다.')
    login = normalize_login(email)
    guard_attempts(conn, login)
    user = conn.execute('select id, password_hash from users where lower(email) = ?', (login,)).fetchone()
    if user is None or not user['password_hash'] or not check_password(password, user['password_hash']):
        note_failure(conn, login)
        conn.commit()
        raise ApiError(401, '아이디 또는 비밀번호가 올바르지 않습니다.')
    clear_failures(conn, login)
    return issue_session(conn, user['id'])

def request_reset(conn, email):
    if not valid_email(email):
        raise ApiError(400, '이메일 형식이 올바르지 않습니다.')
    email = email.lower()
    if not os.environ.get('NIGHTRECORD_SMTP_HOST', ''):
        raise ApiError(503, '인증 메일을 보내려면 SMTP 설정이 필요합니다.')
    current = now()
    row = conn.execute('select sent_at from login_codes where email = ?', (email,)).fetchone()
    if row and current - row['sent_at'] < 60:
        raise ApiError(429, '잠시 뒤에 다시 요청해 주세요.')
    user = conn.execute('select id from users where email = ?', (email,)).fetchone()
    if user is None:
        return
    code = f'{secrets.randbelow(1000000):06d}'
    conn.execute(
        '''insert into login_codes (email, code_hash, expires_at, attempts, sent_at) values (?, ?, ?, 0, ?)
           on conflict(email) do update set code_hash = excluded.code_hash, expires_at = excluded.expires_at,
           attempts = 0, sent_at = excluded.sent_at''',
        (email, hash_text(code), current + CODE_TTL, current),
    )
    send_mail(email, '[밤의 기록] 비밀번호 재설정 코드', mail_text('비밀번호 재설정', code))

def reset_password(conn, email, code, password):
    if not valid_email(email) or not re.fullmatch(r'\d{6}', code or ''):
        raise ApiError(400, '재설정 코드가 올바르지 않습니다.')
    if not valid_password(password):
        raise ApiError(400, '비밀번호는 8자 이상 72자 이하로 입력해 주세요.')
    email = email.lower()
    row = conn.execute('select code_hash, expires_at, attempts from login_codes where email = ?', (email,)).fetchone()
    if row is None or row['expires_at'] < now() or row['attempts'] >= 8:
        raise ApiError(401, '재설정 코드가 만료되었거나 올바르지 않습니다.')
    if not hmac.compare_digest(row['code_hash'], hash_text(code)):
        conn.execute('update login_codes set attempts = attempts + 1 where email = ?', (email,))
        conn.commit()
        raise ApiError(401, '재설정 코드가 만료되었거나 올바르지 않습니다.')
    user = conn.execute('select id from users where email = ?', (email,)).fetchone()
    if user is None:
        raise ApiError(401, '재설정 코드가 만료되었거나 올바르지 않습니다.')
    conn.execute('update users set password_hash = ? where id = ?', (hash_password(password), user['id']))
    conn.execute('delete from login_codes where email = ?', (email,))
    conn.execute('delete from sessions where user_id = ?', (user['id'],))
    clear_failures(conn, email)

def refresh_session(conn, refresh_token):
    row = conn.execute(
        'select user_id, refresh_expires_at from sessions where refresh_hash = ?',
        (hash_text(refresh_token or ''),),
    ).fetchone()
    if row is None or row['refresh_expires_at'] < now():
        raise ApiError(401, '로그인이 만료되었습니다.')
    conn.execute('delete from sessions where refresh_hash = ?', (hash_text(refresh_token),))
    return issue_session(conn, row['user_id'])

def logout(conn, token):
    conn.execute('delete from sessions where token_hash = ?', (hash_text(token),))

def valid_name(name):
    return isinstance(name, str) and 1 <= len(name) <= 32 and not any(ord(ch) < 32 for ch in name)

def validate_record(item):
    if not isinstance(item, dict):
        raise ApiError(400, '원정 형식이 올바르지 않습니다.')
    payload = item.get('payload')
    if not isinstance(payload, dict):
        raise ApiError(400, '원정 형식이 올바르지 않습니다.')
    for key in ('id', 'timestamp', 'win', 'jobId', 'durationMs'):
        if key not in payload:
            raise ApiError(400, '원정 형식이 올바르지 않습니다.')
    if not isinstance(payload['id'], int) or not isinstance(payload['timestamp'], int):
        raise ApiError(400, '원정 ID 또는 날짜가 올바르지 않습니다.')
    if item.get('record_key') != f"{payload['id']}:{payload['timestamp']}":
        raise ApiError(400, '원정 형식이 올바르지 않습니다.')
    if len(item['record_key']) > 100 or len(json.dumps(item)) > 32768:
        raise ApiError(400, '원정이 너무 큽니다.')
    if payload['win'] not in (True, False):
        raise ApiError(400, '원정 형식이 올바르지 않습니다.')
    job_id = payload['jobId']
    if isinstance(job_id, bool) or not isinstance(job_id, int) or not 1 <= job_id <= 10:
        raise ApiError(400, '원정 형식이 올바르지 않습니다.')
    version = item.get('parser_version')
    if isinstance(version, bool) or not isinstance(version, int):
        raise ApiError(400, '원정 형식이 올바르지 않습니다.')
    return item

def merge_runs(conn, table, owner_column, owner_id, records):
    if not isinstance(records, list) or len(records) > 100:
        raise ApiError(400, '한 번에 100개까지만 저장할 수 있습니다.')
    added = 0
    current = now()
    for item in records:
        item = validate_record(item)
        payload = json.dumps(item['payload'], ensure_ascii=False, separators=(',', ':'))
        cur = conn.execute(
            f'insert into {table} ({owner_column}, record_key, payload, parser_version, created_at, updated_at) values (?, ?, ?, ?, ?, ?) on conflict do nothing',
            (owner_id, item['record_key'], payload, item['parser_version'], current, current),
        )
        if cur.rowcount:
            added += 1
            continue
        old = conn.execute(
            f'select payload, parser_version from {table} where {owner_column} = ? and record_key = ?',
            (owner_id, item['record_key']),
        ).fetchone()
        if old is None or old['parser_version'] > item['parser_version']:
            continue
        previous = json.loads(old['payload'])
        merged = {**previous, **item['payload']}
        if item['payload'].get('stage') is None and previous.get('stage') is not None:
            merged['stage'] = previous.get('stage')
            merged['stageLabel'] = previous.get('stageLabel')
        if previous.get('source') == 'saved':
            merged['source'] = 'saved'
        conn.execute(
            f'update {table} set payload = ?, parser_version = ?, updated_at = ? where {owner_column} = ? and record_key = ?',
            (json.dumps(merged, ensure_ascii=False, separators=(',', ':')), item['parser_version'], current, owner_id, item['record_key']),
        )
    return added

def names_from(values, limit):
    names = []
    for name in values:
        if valid_name(name) and name not in names:
            names.append(name)
        if len(names) >= limit:
            break
    return names

def stored_names(raw):
    try:
        values = json.loads(raw or '[]')
    except json.JSONDecodeError:
        return []
    return [name for name in values if valid_name(name)]

def visible_names(row):
    hidden = {name.casefold() for name in stored_names(row['hidden_names'])}
    names = []
    for name in [*stored_names(row['aliases']), *stored_names(row['extra_names'])]:
        if name.casefold() in hidden or any(name.casefold() == item.casefold() for item in names):
            continue
        names.append(name)
        if len(names) >= 24:
            break
    return names

def matched_name(names, needle):
    folded = (needle or '').casefold()
    if not folded:
        return None
    for name in names:
        if name.casefold() == folded:
            return name
    for name in names:
        if name.casefold().startswith(folded):
            return name
    for name in names:
        if folded in name.casefold():
            return name
    return None

def valid_custom_tag(value):
    return isinstance(value, str) and re.fullmatch(r'[0-9A-Za-z가-힣_-]{2,16}', value) is not None

def custom_tags_of(row):
    try:
        values = json.loads(row['custom_tags'] or '[]')
    except (KeyError, TypeError, json.JSONDecodeError):
        return []
    return [tag for tag in values if valid_custom_tag(tag)]

def display_tag_of(row):
    try:
        value = (row['display_tag'] or '').strip()
    except (KeyError, IndexError, TypeError):
        return ''
    return value if valid_custom_tag(value) else ''

def public_tag(row):
    return display_tag_of(row) or row['tag']

def search_hit(row, name):
    played = row['last_played'] if 'last_played' in row.keys() else None
    peak = peak_of(row)
    return {
        'tag': public_tag(row),
        'name': name,
        'matches': row['matches'],
        'wins': row['wins'],
        'peakRating': peak['rating'] if peak else 0,
        'peakDepth': peak['depth'] if peak else 0,
        'lastPlayed': time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime(played)) if played else None,
        'updatedAt': time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime(row['updated_at'])),
    }

def profile_summary(row):
    keys = row.keys()
    peak = peak_of(row)
    return {
        'id': row['id'],
        'profileKey': row['profile_key'] if 'profile_key' in keys else '',
        'tag': public_tag(row),
        'saveId': row['tag'],
        'displayTag': display_tag_of(row) or None,
        'avatar': avatar_of(row),
        'deepRatings': deep_ratings_of(row) if 'deep_ratings' in keys else [],
        'peakRating': peak['rating'] if peak else 0,
        'peakDepth': peak['depth'] if peak else 0,
        'names': visible_names(row),
        'matches': row['matches'],
        'wins': row['wins'],
        'updatedAt': time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime(row['updated_at'])),
    }

def sanitize_deep_ratings(raw):
    if raw is None:
        return None
    if not isinstance(raw, list):
        raise ApiError(400, '심도 점수 목록이 올바르지 않습니다.')
    out = []
    for item in raw[:20]:
        if not isinstance(item, dict):
            continue
        name = str(item.get('name') or '').strip()
        if not valid_name(name):
            continue
        rating = item.get('rating')
        rating_raw = item.get('ratingRaw', rating)
        try:
            rating = int(rating)
            rating_raw = int(rating_raw)
        except (TypeError, ValueError):
            continue
        if rating <= 0 or rating > 9999:
            continue
        depth = item.get('depth')
        try:
            depth = int(depth) if depth is not None else None
        except (TypeError, ValueError):
            depth = None
        if depth is not None and (depth < 1 or depth > 5):
            depth = None
        try:
            slot = int(item.get('slot') or 0)
        except (TypeError, ValueError):
            slot = 0
        out.append({
            'slot': slot,
            'name': name,
            'depth': depth,
            'rating': rating,
            'ratingRaw': rating_raw,
        })
    return out

def deep_ratings_of(row):
    try:
        raw = row['deep_ratings']
    except (KeyError, IndexError, TypeError):
        return []
    if not raw:
        return []
    try:
        data = json.loads(raw) if isinstance(raw, str) else raw
    except (TypeError, ValueError, json.JSONDecodeError):
        return []
    return data if isinstance(data, list) else []

def avatar_of(row):
    try:
        value = (row['avatar'] or '').strip()
    except (KeyError, IndexError, TypeError):
        return ''
    return value if value in AVATAR_IDS else ''

def sanitize_avatar(raw):
    if raw is None:
        return None
    if not isinstance(raw, str):
        raise ApiError(400, '프로필 이미지가 올바르지 않습니다.')
    value = raw.strip()
    if not value:
        return ''
    if value not in AVATAR_IDS:
        raise ApiError(400, '프로필 이미지가 올바르지 않습니다.')
    return value

def peak_from_deep_ratings(ratings):
    best_rating = 0
    best_depth = 0
    for item in ratings or []:
        if not isinstance(item, dict):
            continue
        try:
            rating = int(item.get('rating') or 0)
        except (TypeError, ValueError):
            continue
        if rating <= 0 or rating > 9999:
            continue
        depth = item.get('depth')
        try:
            depth = int(depth) if depth is not None else None
        except (TypeError, ValueError):
            depth = None
        if depth is None or depth < 1 or depth > 5:
            if rating < 1000:
                depth = 1
            elif rating < 2000:
                depth = 2
            elif rating < 4000:
                depth = 3
            elif rating < 6000:
                depth = 4
            else:
                depth = 5
        if rating > best_rating:
            best_rating = rating
            best_depth = depth
    return best_rating, best_depth

def peak_of(row):
    try:
        rating = int(row['peak_rating'] or 0)
        depth = int(row['peak_depth'] or 0)
    except (KeyError, IndexError, TypeError, ValueError):
        return None
    if rating <= 0 or depth < 1 or depth > 5:
        # 예전 행: deep_ratings에서 계산
        rating, depth = peak_from_deep_ratings(deep_ratings_of(row))
    if rating <= 0 or depth < 1:
        return None
    return {'rating': rating, 'depth': depth}

def publish_profile(conn, user_id, profile_key, characters, records, deep_ratings=None, avatar=None):
    # user_id가 None이면 비로그인 업로드. 세이브 내부 SteamID 해시(profile_key)로 프로필을 식별해 누적한다.
    guest = user_id is None
    owner_id = GUEST_ID if guest else user_id
    if not isinstance(characters, list) or len(characters) > 10:
        raise ApiError(400, '캐릭터 목록이 올바르지 않습니다.')
    if not isinstance(records, list) or len(records) > 100:
        raise ApiError(400, '한 번에 100개까지만 저장할 수 있습니다.')
    for name in characters:
        if not valid_name(name):
            raise ApiError(400, '캐릭터 이름이 올바르지 않습니다.')
    sanitized_deep = sanitize_deep_ratings(deep_ratings)
    sanitized_avatar = sanitize_avatar(avatar)
    tag = tag_from_key(profile_key)
    row = conn.execute('select id, owner_id, aliases from profiles where profile_key = ?', (profile_key,)).fetchone()
    if row is None:
        if not records:
            raise ApiError(400, '저장할 원정이 없습니다.')
        profile_id = str(uuid.uuid4())
        try:
            conn.execute(
                'insert into profiles (id, profile_key, owner_id, tag, characters, aliases, matches, wins, created_at, updated_at) values (?, ?, ?, ?, ?, ?, 0, 0, ?, ?)',
                (profile_id, profile_key, owner_id, tag, json.dumps(characters, ensure_ascii=False), '[]', now(), now()),
            )
        except sqlite3.IntegrityError:
            raise ApiError(409, '이미 같은 태그의 프로필이 있습니다.')
        aliases = []
    else:
        if guest:
            # 비로그인 업로드는 원정·이름·심도 점수만 누적하고, 계정이 꾸민 아바타는 건드리지 않는다.
            if row['owner_id'] != GUEST_ID:
                sanitized_avatar = None
        elif row['owner_id'] == GUEST_ID:
            # 비로그인으로 쌓인 프로필은 같은 세이브를 올린 첫 로그인 계정이 이어받는다.
            conn.execute('update profiles set owner_id = ? where id = ?', (user_id, row['id']))
        elif row['owner_id'] != user_id:
            raise ApiError(403, '다른 계정에 등록된 프로필입니다.')
        profile_id = row['id']
        aliases = json.loads(row['aliases'])
    added = merge_runs(conn, 'profile_runs', 'profile_id', profile_id, records)
    found = list(characters)
    for item in records:
        character = item['payload'].get('character')
        if valid_name(character):
            found.append(character)
    found.extend(aliases)
    aliases = names_from(found, 24)
    totals = conn.execute(
        'select count(*) as matches, coalesce(sum(case when json_extract(payload, \'$.win\') = 1 then 1 else 0 end), 0) as wins, max(json_extract(payload, \'$.timestamp\')) as last_ms from profile_runs where profile_id = ?',
        (profile_id,),
    ).fetchone()
    current = now()
    chars_json = json.dumps(characters, ensure_ascii=False) if characters else conn.execute('select characters from profiles where id = ?', (profile_id,)).fetchone()['characters']
    aliases_json = json.dumps(aliases, ensure_ascii=False)
    last_played = int(totals['last_ms'] / 1000) if totals['last_ms'] else None
    sets = ['characters = ?', 'aliases = ?', 'matches = ?', 'wins = ?', 'last_played = ?', 'updated_at = ?']
    values = [chars_json, aliases_json, totals['matches'], totals['wins'], last_played, current]
    if sanitized_deep is not None:
        peak_rating, peak_depth = peak_from_deep_ratings(sanitized_deep)
        sets.extend(['deep_ratings = ?', 'peak_rating = ?', 'peak_depth = ?'])
        values.extend([json.dumps(sanitized_deep, ensure_ascii=False), peak_rating, peak_depth])
    if sanitized_avatar is not None:
        sets.append('avatar = ?')
        values.append(sanitized_avatar)
    values.append(profile_id)
    conn.execute(f"update profiles set {', '.join(sets)} where id = ?", values)
    saved = conn.execute(
        'select id, profile_key, tag, display_tag, aliases, extra_names, hidden_names, avatar, deep_ratings, peak_rating, peak_depth from profiles where id = ?',
        (profile_id,),
    ).fetchone()
    peak = peak_of(saved)
    return {
        'tag': public_tag(saved),
        'saveId': saved['tag'],
        'added': added,
        'total': totals['matches'],
        'updatedAt': time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime(current)),
        'names': visible_names(saved),
        'avatar': avatar_of(saved),
        'deepRatings': deep_ratings_of(saved),
        'peakRating': peak['rating'] if peak else 0,
        'peakDepth': peak['depth'] if peak else 0,
    }

def split_lookup(query):
    if '#' not in query:
        return query.strip(), None, None
    name, raw = query.rsplit('#', 1)
    raw = raw.strip()
    if re.fullmatch(r'[0-9A-HJKMNP-TV-Z]{8}', raw.upper()):
        return name.strip(), raw.upper(), None
    return name.strip(), None, raw

def match_rank(value, needle):
    if not value or not needle:
        return 0
    folded = value.casefold()
    query = needle.casefold()
    if folded == query:
        return 3
    if folded.startswith(query):
        return 2
    if query in folded:
        return 1
    return 0

def prefix_match(values, needle):
    hit = matched_name(values, needle)
    if hit is None:
        return None
    folded = needle.casefold()
    if hit.casefold() == folded or hit.casefold().startswith(folded):
        return hit
    return None

def search_profiles(conn, query):
    query = (query or '').strip()
    if not 1 <= len(query) <= 40:
        raise ApiError(400, '검색어가 올바르지 않습니다.')
    name, origin, custom_query = split_lookup(query)
    rows = conn.execute(
        'select tag, display_tag, aliases, extra_names, hidden_names, matches, wins, last_played, updated_at, deep_ratings, peak_rating, peak_depth from profiles order by updated_at desc',
    ).fetchall()
    found = []
    needle = name.casefold()
    for row in rows:
        if origin and row['tag'] != origin:
            continue
        name_hit = prefix_match(visible_names(row), needle) if needle else None
        custom_source = custom_query or (name if not origin else '')
        display = display_tag_of(row)
        custom_hit = prefix_match([display], custom_source) if display and custom_source else None
        if custom_query and custom_hit is None:
            continue
        if needle and name_hit is None and custom_hit is None:
            continue
        found.append(search_hit(row, None if custom_hit and name_hit is None else name_hit))
        if len(found) >= 20:
            break
    return found

def find_profile(conn, key):
    key = (key or '').strip()
    if not key or len(key) > 16:
        raise ApiError(400, '태그가 올바르지 않습니다.')
    if re.fullmatch(r'[0-9A-HJKMNP-TV-Z]{8}', key.upper()):
        row = conn.execute('select * from profiles where tag = ?', (key.upper(),)).fetchone()
        if row is not None:
            return row
    if not valid_custom_tag(key):
        raise ApiError(400, '태그가 올바르지 않습니다.')
    return conn.execute('select * from profiles where lower(display_tag) = lower(?)', (key,)).fetchone()

def profile_detail(conn, tag):
    row = find_profile(conn, tag)
    if row is None:
        return None
    runs = conn.execute(
        'select payload from profile_runs where profile_id = ? order by json_extract(payload, \'$.timestamp\') limit 5000',
        (row['id'],),
    ).fetchall()
    return {
        'tag': public_tag(row),
        'saveId': row['tag'],
        'names': visible_names(row),
        'matches': row['matches'],
        'wins': row['wins'],
        'lastPlayed': time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime(row['last_played'])) if row['last_played'] else None,
        'updatedAt': time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime(row['updated_at'])),
        'deepRatings': deep_ratings_of(row),
        'avatar': avatar_of(row),
        'peakRating': (peak_of(row) or {}).get('rating', 0),
        'peakDepth': (peak_of(row) or {}).get('depth', 0),
        'runs': [json.loads(item['payload']) for item in runs],
    }

def suggest_profiles(conn, query):
    query = (query or '').strip()
    if len(query) < 3 or len(query) > 40:
        return []
    if '#' in query:
        name, tag = query.rsplit('#', 1)
        name = name.strip().casefold()
        tag = tag.strip().upper()
    else:
        name, tag = query.casefold(), ''
    found = []
    rows = conn.execute(
        'select tag, display_tag, aliases, extra_names, hidden_names, matches, wins, last_played, updated_at, deep_ratings, peak_rating, peak_depth from profiles order by updated_at desc',
    ).fetchall()
    for row in rows:
        display = display_tag_of(row)
        if tag and not row['tag'].startswith(tag) and not display.upper().startswith(tag):
            continue
        name_hit = matched_name(visible_names(row), name) if name else None
        custom_hit = matched_name([display], name) if name and display else None
        if name and name_hit is None and custom_hit is None and name.upper() not in row['tag']:
            continue
        if match_rank(custom_hit, name) > match_rank(name_hit, name):
            found.append(search_hit(row, None))
        else:
            found.append(search_hit(row, name_hit))
        if len(found) >= 5:
            break
    return found

def account_overview(conn, user_id):
    user = conn.execute('select id, email from users where id = ?', (user_id,)).fetchone()
    rows = conn.execute(
        'select id, profile_key, tag, display_tag, aliases, extra_names, hidden_names, matches, wins, updated_at, avatar, deep_ratings, peak_rating, peak_depth from profiles where owner_id = ? order by updated_at desc',
        (user_id,),
    ).fetchall()
    return {'email': user['email'], 'profiles': [profile_summary(row) for row in rows]}

def change_password(conn, user_id, current_password, password):
    if not valid_password(password):
        raise ApiError(400, '비밀번호는 8자 이상 72자 이하로 입력해 주세요.')
    user = conn.execute('select email, password_hash from users where id = ?', (user_id,)).fetchone()
    if user is None or not user['password_hash']:
        raise ApiError(401, '먼저 이메일로 로그인해 주세요.')
    guard_attempts(conn, user['email'])
    if not isinstance(current_password, str) or not check_password(current_password, user['password_hash']):
        note_failure(conn, user['email'])
        conn.commit()
        raise ApiError(401, '현재 비밀번호가 올바르지 않습니다.')
    clear_failures(conn, user['email'])
    conn.execute('update users set password_hash = ? where id = ?', (hash_password(password), user_id))

def set_display_tag(conn, user_id, profile_id, tag):
    if not isinstance(profile_id, str) or not profile_id:
        raise ApiError(400, '프로필이 올바르지 않습니다.')
    row = conn.execute('select * from profiles where id = ?', (profile_id,)).fetchone()
    if row is None or row['owner_id'] != user_id:
        raise ApiError(404, '내 프로필이 아닙니다.')
    value = (tag or '').strip() if isinstance(tag, str) else ''
    if value:
        if not valid_custom_tag(value):
            raise ApiError(400, '검색 태그는 2~16자의 한글, 영문, 숫자로 입력해 주세요.')
        taken = conn.execute('select id from profiles where tag = ?', (value.upper(),)).fetchone()
        if taken is not None:
            raise ApiError(409, '이미 사용 중인 검색 태그입니다.')
        other = conn.execute(
            'select id from profiles where lower(display_tag) = lower(?) and id != ?',
            (value, row['id']),
        ).fetchone()
        if other is not None:
            raise ApiError(409, '이미 사용 중인 검색 태그입니다.')
        conn.execute('update profiles set display_tag = ? where id = ?', (value, row['id']))
    else:
        conn.execute('update profiles set display_tag = null where id = ?', (row['id'],))
    saved = conn.execute(
        'select id, profile_key, tag, display_tag, aliases, extra_names, hidden_names, matches, wins, updated_at, avatar from profiles where id = ?',
        (row['id'],),
    ).fetchone()
    return profile_summary(saved)

def set_avatar(conn, user_id, profile_id, avatar):
    if not isinstance(profile_id, str) or not profile_id:
        raise ApiError(400, '프로필이 올바르지 않습니다.')
    row = conn.execute('select * from profiles where id = ?', (profile_id,)).fetchone()
    if row is None or row['owner_id'] != user_id:
        raise ApiError(404, '내 프로필이 아닙니다.')
    value = sanitize_avatar(avatar if isinstance(avatar, str) else '')
    if value is None:
        value = ''
    conn.execute('update profiles set avatar = ?, updated_at = ? where id = ?', (value, now(), row['id']))
    saved = conn.execute(
        'select id, profile_key, tag, display_tag, aliases, extra_names, hidden_names, matches, wins, updated_at, avatar from profiles where id = ?',
        (row['id'],),
    ).fetchone()
    return profile_summary(saved)

def update_search_names(conn, user_id, tag, names):
    tag = (tag or '').strip().upper()
    if not re.fullmatch(r'[0-9A-HJKMNP-TV-Z]{8}', tag):
        raise ApiError(400, '태그가 올바르지 않습니다.')
    if not isinstance(names, list) or len(names) > 24:
        raise ApiError(400, '검색 이름은 24개까지 등록할 수 있습니다.')
    desired = []
    for name in names:
        if not valid_name(name):
            raise ApiError(400, '검색 이름이 올바르지 않습니다.')
        if any(name.casefold() == item.casefold() for item in desired):
            continue
        desired.append(name)
    row = conn.execute('select * from profiles where tag = ?', (tag,)).fetchone()
    if row is None or row['owner_id'] != user_id:
        raise ApiError(404, '내 프로필이 아닙니다.')
    aliases = stored_names(row['aliases'])
    alias_fold = {name.casefold(): name for name in aliases}
    desired_fold = {name.casefold() for name in desired}
    hidden = [name for name in aliases if name.casefold() not in desired_fold]
    extra = []
    for name in desired:
        alias = alias_fold.get(name.casefold())
        if alias is None or alias != name:
            extra.append(name)
        if alias and alias != name:
            hidden.append(alias)
    conn.execute(
        'update profiles set extra_names = ?, hidden_names = ? where id = ?',
        (json.dumps(extra, ensure_ascii=False), json.dumps(hidden, ensure_ascii=False), row['id']),
    )
    saved = conn.execute(
        'select id, profile_key, tag, display_tag, aliases, extra_names, hidden_names, matches, wins, updated_at, avatar from profiles where id = ?',
        (row['id'],),
    ).fetchone()
    return profile_summary(saved)

def list_expeditions(conn, user_id, offset):
    rows = conn.execute(
        'select payload from expeditions where user_id = ? order by record_key limit 500 offset ?',
        (user_id, offset),
    ).fetchall()
    return [json.loads(row['payload']) for row in rows]

def sanitize_relic_slots(slots):
    if not isinstance(slots, list):
        raise ApiError(400, '유물 목록이 올바르지 않습니다.')
    if len(slots) > 20:
        raise ApiError(400, '유물 슬롯이 너무 많습니다.')
    cleaned = []
    total = 0
    for slot in slots:
        if not isinstance(slot, dict):
            raise ApiError(400, '유물 슬롯 형식이 올바르지 않습니다.')
        try:
            index = int(slot.get('index'))
        except (TypeError, ValueError):
            raise ApiError(400, '유물 슬롯 번호가 올바르지 않습니다.')
        name = slot.get('name')
        if not isinstance(name, str) or not (1 <= len(name) <= 64):
            raise ApiError(400, '캐릭터 이름이 올바르지 않습니다.')
        relics = slot.get('relics')
        if not isinstance(relics, list) or len(relics) > 3000:
            raise ApiError(400, '유물 개수가 올바르지 않습니다.')
        total += len(relics)
        if total > 12000:
            raise ApiError(400, '유물 개수가 너무 많습니다.')
        slim_relics = []
        for relic in relics:
            if not isinstance(relic, dict):
                raise ApiError(400, '유물 형식이 올바르지 않습니다.')
            try:
                handle = int(relic.get('handle'))
                item_id = int(relic.get('itemId'))
                sort_key = int(relic.get('sortKey', 0))
            except (TypeError, ValueError):
                raise ApiError(400, '유물 값이 올바르지 않습니다.')
            effects = relic.get('effects')
            if not isinstance(effects, list) or len(effects) > 8:
                raise ApiError(400, '유물 효과가 올바르지 않습니다.')
            slim_effects = []
            for effect in effects:
                if not isinstance(effect, list) or not (1 <= len(effect) <= 2):
                    raise ApiError(400, '유물 효과가 올바르지 않습니다.')
                try:
                    slim_effects.append([int(value) for value in effect])
                except (TypeError, ValueError):
                    raise ApiError(400, '유물 효과가 올바르지 않습니다.')
            slim_relics.append({
                'handle': handle,
                'itemId': item_id,
                'effects': slim_effects,
                'sortKey': sort_key,
            })
        cleaned.append({'index': index, 'name': name, 'relics': slim_relics})
    return cleaned

def save_relic_inventory(conn, user_id, profile_key, slots, updated_at=None):
    tag_from_key(profile_key)
    cleaned = sanitize_relic_slots(slots)
    stamp = now()
    iso = time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime(stamp))
    if isinstance(updated_at, str) and updated_at.strip():
        iso = updated_at.strip()
    payload = json.dumps({'slots': cleaned, 'updatedAt': iso}, ensure_ascii=False)
    conn.execute(
        '''
        insert into relic_inventories (user_id, profile_key, payload, updated_at)
        values (?, ?, ?, ?)
        on conflict(user_id, profile_key) do update set
          payload = excluded.payload,
          updated_at = excluded.updated_at
        ''',
        (user_id, profile_key, payload, stamp),
    )
    return {
        'profileKey': profile_key,
        'updatedAt': iso,
        'slotCount': len(cleaned),
        'relicCount': sum(len(slot['relics']) for slot in cleaned),
    }

def list_relic_inventories(conn, user_id, profile_key=None):
    if profile_key:
        profile_key = unquote(profile_key)
        tag_from_key(profile_key)
        rows = conn.execute(
            'select profile_key, payload, updated_at from relic_inventories where user_id = ? and profile_key = ?',
            (user_id, profile_key),
        ).fetchall()
    else:
        rows = conn.execute(
            'select profile_key, payload, updated_at from relic_inventories where user_id = ? order by updated_at desc',
            (user_id,),
        ).fetchall()
    items = []
    for row in rows:
        data = json.loads(row['payload'])
        items.append({
            'profileKey': row['profile_key'],
            'slots': data.get('slots') or [],
            'updatedAt': data.get('updatedAt') or time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime(row['updated_at'])),
        })
    return items

class Handler(BaseHTTPRequestHandler):
    server_version = 'Nightrecord'

    def log_message(self, fmt, *args):
        print(f'{self.address_string()} {fmt % args}')

    def end_headers(self):
        allowed = {
            part.strip()
            for part in os.environ.get('NIGHTRECORD_CORS_ORIGIN', '').split(',')
            if part.strip()
        }
        request_origin = self.headers.get('Origin', '')
        if allowed and request_origin and ('*' in allowed or request_origin in allowed):
            self.send_header('Access-Control-Allow-Origin', request_origin)
            self.send_header('Access-Control-Allow-Headers', 'Authorization, Content-Type')
            self.send_header('Access-Control-Allow-Methods', 'GET, POST, OPTIONS')
            self.send_header('Vary', 'Origin')
        self.send_header('X-Content-Type-Options', 'nosniff')
        self.send_header('Referrer-Policy', 'strict-origin-when-cross-origin')
        super().end_headers()

    def do_OPTIONS(self):
        self.send_response(204)
        self.end_headers()

    def do_GET(self):
        path = urlparse(self.path).path
        if path.startswith('/api/'):
            self.handle_api()
            return
        self.serve_static(path, head_only=False)

    def do_HEAD(self):
        path = urlparse(self.path).path
        if path.startswith('/api/'):
            self.send_error(405)
            return
        self.serve_static(path, head_only=True)

    def do_POST(self):
        if urlparse(self.path).path.startswith('/api/'):
            self.handle_api()
            return
        self.send_error(404)

    def send_json(self, status, data, log_path=None):
        body = json.dumps(data, ensure_ascii=False).encode()
        self.send_response(status)
        self.send_header('Content-Type', 'application/json; charset=utf-8')
        self.send_header('Content-Length', str(len(body)))
        self.send_header('Cache-Control', 'no-store')
        self.end_headers()
        self.wfile.write(body)
        if log_path:
            with LOCK:
                record_access(self, log_path, status, 'api')
                CONN.commit()

    def handle_api(self):
        path = urlparse(self.path).path
        try:
            with LOCK:
                result = self.route(path)
                CONN.commit()
            if result is None:
                self.send_json(200, None, log_path=path)
            else:
                self.send_json(200, result, log_path=path)
        except ApiError as error:
            with LOCK:
                CONN.rollback()
            self.send_json(error.status, {'message': error.message}, log_path=path)
        except Exception:
            with LOCK:
                CONN.rollback()
            self.send_json(500, {'message': '서버 오류가 발생했습니다.'}, log_path=path)
            raise

    def route(self, path):
        if path == '/api/auth/signup' and self.command == 'POST':
            body = json_body(self)
            login = body.get('username') or body.get('email') or ''
            return register(CONN, login, body.get('password', ''))
        if path == '/api/auth/verify' and self.command == 'POST':
            body = json_body(self)
            return verify_signup(CONN, body.get('email', ''), str(body.get('code', '')))
        if path == '/api/auth/login' and self.command == 'POST':
            body = json_body(self)
            login = body.get('username') or body.get('email') or ''
            return sign_in(CONN, login, body.get('password', ''))
        if path == '/api/auth/forgot' and self.command == 'POST':
            body = json_body(self)
            request_reset(CONN, body.get('email', ''))
            return {}
        if path == '/api/auth/reset' and self.command == 'POST':
            body = json_body(self)
            reset_password(CONN, body.get('email', ''), str(body.get('token', '')), body.get('password', ''))
            return {}
        if path == '/api/auth/refresh' and self.command == 'POST':
            body = json_body(self)
            return refresh_session(CONN, body.get('refresh_token', ''))
        if path == '/api/auth/logout' and self.command == 'POST':
            logout(CONN, bearer(self))
            return {}
        if path == '/api/auth/user' and self.command == 'GET':
            return user_from_token(CONN, bearer(self))
        if path == '/api/admin/stats' and self.command == 'GET':
            require_admin(CONN, bearer(self))
            return admin_stats(CONN)
        if path == '/api/me' and self.command == 'GET':
            user = user_from_token(CONN, bearer(self))
            return account_overview(CONN, user['id'])
        if path == '/api/me/password' and self.command == 'POST':
            user = user_from_token(CONN, bearer(self))
            body = json_body(self)
            change_password(CONN, user['id'], body.get('current_password', ''), body.get('password', ''))
            return {}
        if path == '/api/me/tag' and self.command == 'POST':
            user = user_from_token(CONN, bearer(self))
            body = json_body(self)
            return set_display_tag(CONN, user['id'], body.get('id', ''), body.get('tag', ''))
        if path == '/api/me/avatar' and self.command == 'POST':
            user = user_from_token(CONN, bearer(self))
            body = json_body(self)
            return set_avatar(CONN, user['id'], body.get('id', ''), body.get('avatar', ''))
        if path == '/api/profiles/suggest' and self.command == 'POST':
            return suggest_profiles(CONN, json_body(self).get('query', ''))
        if path == '/api/expeditions' and self.command == 'GET':
            user = user_from_token(CONN, bearer(self))
            query = urlparse(self.path).query
            offset = 0
            for part in query.split('&'):
                if part.startswith('offset='):
                    try:
                        offset = int(part.split('=', 1)[1] or 0)
                    except ValueError:
                        raise ApiError(400, '조회 범위가 올바르지 않습니다.')
            if offset < 0 or offset > 100000:
                raise ApiError(400, '조회 범위가 올바르지 않습니다.')
            return list_expeditions(CONN, user['id'], offset)
        if path == '/api/expeditions/merge' and self.command == 'POST':
            user = user_from_token(CONN, bearer(self))
            body = json_body(self)
            added = merge_runs(CONN, 'expeditions', 'user_id', user['id'], body.get('records'))
            return {'added': added}
        if path == '/api/profiles/publish' and self.command == 'POST':
            has_token = self.headers.get('Authorization', '').startswith('Bearer ')
            user = user_from_token(CONN, bearer(self)) if has_token else None
            body = json_body(self)
            return publish_profile(
                CONN,
                user['id'] if user else None,
                body.get('profile_key', ''),
                body.get('characters'),
                body.get('records'),
                body.get('deep_ratings'),
                body.get('avatar'),
            )
        if path == '/api/profiles/search' and self.command == 'POST':
            return search_profiles(CONN, json_body(self).get('query', ''))
        if path == '/api/profiles/detail' and self.command == 'POST':
            return profile_detail(CONN, json_body(self).get('profile_tag', ''))
        if path == '/api/relics' and self.command == 'GET':
            user = user_from_token(CONN, bearer(self))
            query = urlparse(self.path).query
            profile_key = None
            for part in query.split('&'):
                if part.startswith('profile_key='):
                    profile_key = part.split('=', 1)[1]
            return list_relic_inventories(CONN, user['id'], profile_key or None)
        if path == '/api/relics/save' and self.command == 'POST':
            user = user_from_token(CONN, bearer(self))
            body = json_body(self)
            return save_relic_inventory(
                CONN,
                user['id'],
                body.get('profile_key', ''),
                body.get('slots'),
                body.get('updated_at'),
            )
        raise ApiError(404, '없는 주소입니다.')

    def serve_static(self, path, head_only=False):
        rel = 'index.html' if path in ('', '/') else path.lstrip('/')
        parts = [part for part in rel.split('/') if part]
        blocked_roots = {'data', 'server', 'deploy', 'android-twa', 'node_modules', '.git', '.cursor', '.cache'}
        if not parts or parts[0] in blocked_roots or '..' in parts:
            self.send_error(404)
            return
        if any(part.endswith('.keystore') or part.startswith('.keystore') for part in parts):
            self.send_error(404)
            return
        rel = '/'.join(parts)
        candidate = (ROOT / rel).resolve()
        if not str(candidate).startswith(str(ROOT)) or not candidate.is_file() or candidate.suffix == '.sqlite':
            self.send_error(404)
            return
        data = candidate.read_bytes()
        mime = mimetypes.guess_type(candidate.name)[0] or 'application/octet-stream'
        if candidate.suffix == '.xml':
            mime = 'application/xml; charset=utf-8'
        elif candidate.suffix == '.txt':
            mime = 'text/plain; charset=utf-8'
        elif candidate.suffix == '.js':
            mime = 'text/javascript; charset=utf-8'
        elif candidate.suffix == '.webp':
            mime = 'image/webp'
        elif candidate.suffix == '.svg':
            mime = 'image/svg+xml'
        elif candidate.suffix == '.webmanifest' or candidate.name.endswith('.webmanifest'):
            mime = 'application/manifest+json; charset=utf-8'
        elif candidate.suffix == '.apk':
            mime = 'application/vnd.android.package-archive'
        elif candidate.suffix == '.json' and '.well-known' in parts:
            mime = 'application/json; charset=utf-8'
        elif mime == 'text/html' or candidate.suffix in {'.html', '.htm'}:
            mime = 'text/html; charset=utf-8'
        self.send_response(200)
        self.send_header('Content-Type', mime)
        self.send_header('Content-Length', str(len(data)))
        if candidate.suffix == '.apk':
            self.send_header('Content-Disposition', f'attachment; filename="{candidate.name}"')
        self.end_headers()
        if not head_only:
            self.wfile.write(data)
        log_path = '/' if path in ('', '/') else path
        # 확인·크롤 파일은 페이지뷰에서 제외
        if not head_only and not candidate.name.startswith('naver') and not candidate.name.startswith('google') and candidate.name not in {'robots.txt', 'sitemap.xml'}:
            with LOCK:
                record_access(self, log_path, 200, 'page')
                CONN.commit()

def main():
    global CONN
    CONN = connect()
    server = ThreadingHTTPServer((HOST, PORT), Handler)
    print(f'밤의 기록 http://{HOST}:{PORT}')
    server.serve_forever()

if __name__ == '__main__':
    main()
