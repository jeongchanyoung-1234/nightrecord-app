import { API_BASE } from './config.js';

export const PARSER_VERSION = 3;
export const RELIC_STORAGE_VERSION = 1;
export const configured = true;
const storageKey = `nightreign.auth:${API_BASE || 'local'}`;
const historyPrefix = 'nightreign.profile:';
const relicsPrefix = 'nightreign.relics:';
const lastProfileKey = 'nightreign.lastProfile';
const TAG_ALPHABET = '0123456789ABCDEFGHJKMNPQRSTVWXYZ';
let session = null;
let refreshTask = null;

export function tagFromKey(profileKey) {
  if (!/^[0-9a-f]{64}$/.test(profileKey)) throw new Error('프로필 키가 올바르지 않습니다.');
  let value = BigInt(`0x${profileKey.slice(0, 10)}`);
  let tag = '';
  for (let i = 0; i < 8; i++) {
    tag = TAG_ALPHABET[Number(value & 31n)] + tag;
    value >>= 5n;
  }
  return tag;
}

export function slimRun(run) {
  const { elo, rollingWinRate, cumulativeWinRate, sourceOffset, rawHeader, ...payload } = run;
  return payload;
}

export function loadLocalHistory(profileKey) {
  if (!profileKey) return { tag: '', names: [], runs: [], updatedAt: null, deepRatings: [], avatar: '' };
  try {
    const data = JSON.parse(localStorage.getItem(historyPrefix + profileKey) || 'null');
    if (!data || !Array.isArray(data.runs)) return { tag: '', names: [], runs: [], updatedAt: null, deepRatings: [], avatar: '' };
    return {
      tag: typeof data.tag === 'string' ? data.tag : '',
      names: Array.isArray(data.names) ? data.names.filter((name) => typeof name === 'string') : [],
      updatedAt: data.updatedAt || null,
      runs: data.runs.filter((run) => Number.isSafeInteger(run?.id) && Number.isSafeInteger(run?.timestamp)),
      deepRatings: Array.isArray(data.deepRatings) ? data.deepRatings : [],
      avatar: typeof data.avatar === 'string' ? data.avatar : '',
    };
  } catch {
    return { tag: '', names: [], runs: [], updatedAt: null, deepRatings: [], avatar: '' };
  }
}

export function saveLocalHistory(profileKey, data) {
  if (!profileKey) return false;
  try {
    localStorage.setItem(historyPrefix + profileKey, JSON.stringify({
      tag: data.tag || '',
      names: data.names || [],
      updatedAt: data.updatedAt || null,
      runs: (data.runs || []).map(slimRun),
      deepRatings: Array.isArray(data.deepRatings) ? data.deepRatings : [],
      avatar: typeof data.avatar === 'string' ? data.avatar : '',
    }));
    rememberLastProfile(profileKey);
    return true;
  } catch {
    return false;
  }
}

export function rememberLastProfile(profileKey) {
  if (!profileKey) return;
  try { localStorage.setItem(lastProfileKey, profileKey); } catch {}
}

export function loadLastProfileKey() {
  try { return localStorage.getItem(lastProfileKey) || ''; } catch { return ''; }
}

export function slimRelicSlots(slots) {
  return (slots || []).map((slot) => ({
    index: Number(slot.index) || 0,
    name: typeof slot.name === 'string' ? slot.name : '',
    relics: (slot.relics || []).map((relic) => ({
      handle: Number(relic.handle) || 0,
      itemId: Number(relic.itemId) || 0,
      effects: Array.isArray(relic.effects) ? relic.effects : [],
      sortKey: Number(relic.sortKey) || 0,
    })),
  }));
}

export function loadLocalRelics(profileKey) {
  if (!profileKey) return { slots: [], updatedAt: null };
  try {
    const data = JSON.parse(localStorage.getItem(relicsPrefix + profileKey) || 'null');
    if (!data || !Array.isArray(data.slots)) return { slots: [], updatedAt: null };
    return {
      slots: slimRelicSlots(data.slots),
      updatedAt: data.updatedAt || null,
    };
  } catch {
    return { slots: [], updatedAt: null };
  }
}

export function saveLocalRelics(profileKey, slots, updatedAt = null) {
  if (!profileKey) return false;
  try {
    const stamp = updatedAt || new Date().toISOString();
    localStorage.setItem(relicsPrefix + profileKey, JSON.stringify({
      version: RELIC_STORAGE_VERSION,
      updatedAt: stamp,
      slots: slimRelicSlots(slots),
    }));
    rememberLastProfile(profileKey);
    return true;
  } catch {
    return false;
  }
}

export async function fetchRelics(profileKey = null) {
  const token = await accessToken();
  const query = profileKey ? `?profile_key=${encodeURIComponent(profileKey)}` : '';
  return request(`/api/relics${query}`, { token });
}

export async function saveRelics(profileKey, slots, updatedAt = null) {
  const token = await accessToken();
  const stamp = updatedAt || new Date().toISOString();
  return request('/api/relics/save', {
    method: 'POST',
    token,
    body: {
      profile_key: profileKey,
      slots: slimRelicSlots(slots),
      updated_at: stamp,
    },
  });
}

export function recordKey(run) {
  if (!Number.isSafeInteger(run.id) || !Number.isSafeInteger(run.timestamp)) throw new Error('원정 ID 또는 날짜가 올바르지 않습니다.');
  return `${run.id}:${run.timestamp}`;
}

export function mergeRecords(previous, incoming) {
  const records = new Map(previous.map(run => [recordKey(run), run]));
  let added = 0;
  for (const run of incoming) {
    const key = recordKey(run);
    const old = records.get(key);
    if (!old) added++;
    records.set(key, {
      ...old, ...run,
      stage: run.stage ?? old?.stage ?? null,
      stageLabel: run.stage == null && old?.stage != null ? old.stageLabel : run.stageLabel,
      source: old?.source === 'saved' ? 'saved' : run.source,
    });
  }
  return { runs: [...records.values()].sort((a,b) => a.timestamp - b.timestamp), added };
}

function persist(value) {
  session = value;
  if (value) localStorage.setItem(storageKey, JSON.stringify(value));
  else localStorage.removeItem(storageKey);
}

export async function request(path, { method = 'GET', body, token, headers = {} } = {}) {
  let response;
  try {
    response = await fetch(`${API_BASE}${path}`, {
      method,
      headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}), ...headers },
      ...(body === undefined ? {} : { body: JSON.stringify(body) }),
    });
  } catch {
    const error = new Error('서버에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.');
    error.status = 0;
    throw error;
  }
  const text = await response.text();
  let data = null;
  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      const error = new Error(
        response.ok
          ? '서버 응답 형식이 올바르지 않습니다.'
          : (response.status >= 500
            ? '서버가 잠시 응답하지 않습니다. 잠시 후 다시 시도해 주세요.'
            : '계정 서버 요청에 실패했습니다.'),
      );
      error.status = response.status;
      throw error;
    }
  }
  if (!response.ok) {
    const error = new Error(data?.msg || data?.message || data?.error_description || '계정 서버 요청에 실패했습니다.');
    error.status = response.status;
    throw error;
  }
  return data;
}

export async function accessToken() {
  if (!session) throw new Error('먼저 로그인해 주세요.');
  if (session.expires_at > Date.now() / 1000 + 60) return session.access_token;
  if (!refreshTask) {
    refreshTask = request('/api/auth/refresh', { method: 'POST', body: { refresh_token: session.refresh_token } })
      .then(value => { persist(value); return value.access_token; })
      .catch(error => { if (error.status === 400 || error.status === 401) persist(null); throw error; })
      .finally(() => { refreshTask = null; });
  }
  return refreshTask;
}

export async function restoreSession() {
  if (!configured) return null;
  try { session = JSON.parse(localStorage.getItem(storageKey) || 'null'); } catch { persist(null); }
  if (!session) return null;
  const token = await accessToken();
  try {
    const user = await request('/api/auth/user', { token });
    session.user = user;
    return user;
  } catch (error) {
    if (error.status === 401) persist(null);
    throw error;
  }
}

export async function signUp(username, password) {
  const value = await request('/api/auth/signup', {
    method: 'POST',
    body: { username, password },
  });
  persist(value);
  return value.user;
}

/** @deprecated 이메일 인증 가입은 제거됨. signUp 사용 */
export async function requestSignUp(username, password) {
  return signUp(username, password);
}

export async function verifySignUp() {
  throw new Error('이메일 인증 가입은 더 이상 지원하지 않습니다.');
}

export async function signIn(username, password) {
  const value = await request('/api/auth/login', {
    method: 'POST',
    body: { username, password },
  });
  persist(value);
  return value.user;
}

export async function requestPasswordReset(email) {
  await request('/api/auth/forgot', { method: 'POST', body: { email } });
}

export async function resetPassword(email, token, password) {
  await request('/api/auth/reset', { method: 'POST', body: { email, token, password } });
}

export async function signOut() {
  try {
    if (session) await request('/api/auth/logout', { method: 'POST', token: await accessToken() });
  } finally { persist(null); }
}

export async function fetchRecords() {
  const token = await accessToken();
  const runs = [];
  for (let offset = 0; ; offset += 500) {
    const page = await request(`/api/expeditions?offset=${offset}`, { token });
    runs.push(...page);
    if (page.length < 500) break;
  }
  return runs;
}

export async function saveRecords(runs) {
  const token = await accessToken();
  const unique = mergeRecords([], runs).runs;
  let added = 0;
  for (let offset = 0; offset < unique.length; offset += 100) {
    const batch = unique.slice(offset, offset + 100).map(run => {
      const { elo, rollingWinRate, cumulativeWinRate, ...payload } = run;
      return { record_key: recordKey(run), payload, parser_version: PARSER_VERSION };
    });
    const result = await request('/api/expeditions/merge', { method: 'POST', token, body: { records: batch } });
    added += result.added;
  }
  return { added, duplicate: unique.length - added };
}

export async function publishProfile(profileKey, names, runs, deepRatings = [], avatar = undefined) {
  if (!configured) return null;
  const token = await accessToken();
  const unique = mergeRecords([], runs).runs.map(slimRun);
  let added = 0;
  let last = null;
  const batches = Math.max(1, Math.ceil(unique.length / 100));
  const slimDeep = (Array.isArray(deepRatings) ? deepRatings : [])
    .filter((row) => row && typeof row.name === 'string')
    .map((row) => ({
      slot: Number(row.slot) || 0,
      name: String(row.name || '').trim(),
      depth: Number(row.depth) || null,
      rating: Number(row.rating) || 0,
      ratingRaw: Number(row.ratingRaw) || Number(row.rating) || 0,
    }))
    .filter((row) => row.name && row.rating > 0)
    .slice(0, 20);
  const avatarValue = typeof avatar === 'string' ? avatar : undefined;
  for (let index = 0; index < batches; index++) {
    const batch = unique.slice(index * 100, index * 100 + 100).map((run) => ({
      record_key: recordKey(run),
      payload: run,
      parser_version: PARSER_VERSION,
    }));
    const lastBatch = index === batches - 1;
    last = await request('/api/profiles/publish', {
      method: 'POST',
      token,
      body: {
        profile_key: profileKey,
        characters: names,
        records: batch,
        // 마지막 배치에만 메타 저장. 빈 심도 목록은 보내지 않아 서버 값을 지우지 않음
        deep_ratings: lastBatch && slimDeep.length ? slimDeep : undefined,
        // 빈 문자열은 보내지 않아 기존 아바타를 지우지 않음 (해제는 /api/me/avatar)
        avatar: lastBatch && avatarValue ? avatarValue : undefined,
      },
    });
    added += last.added || 0;
  }
  return { ...last, added };
}

export async function searchProfiles(query) {
  if (!configured) throw new Error('공개 검색은 서버 연결 후 사용할 수 있습니다.');
  return request('/api/profiles/search', { method: 'POST', body: { query } });
}

export async function suggestProfiles(query) {
  if (!configured) return [];
  return request('/api/profiles/suggest', { method: 'POST', body: { query } });
}

export async function fetchMe() {
  return request('/api/me', { token: await accessToken() });
}

export async function changePassword(currentPassword, password) {
  await request('/api/me/password', { method: 'POST', token: await accessToken(), body: { current_password: currentPassword, password } });
}

export async function updateDisplayTag(id, tag) {
  return request('/api/me/tag', { method: 'POST', token: await accessToken(), body: { id, tag } });
}

export async function updateAvatar(id, avatar) {
  return request('/api/me/avatar', { method: 'POST', token: await accessToken(), body: { id, avatar: avatar || '' } });
}

export async function fetchProfile(tag) {
  if (!configured) throw new Error('공개 기록은 서버 연결 후 볼 수 있습니다.');
  return request('/api/profiles/detail', { method: 'POST', body: { profile_tag: tag } });
}
