import {
  parseSave, buildStats, JOB_NAMES, normalizeDeepRating,
  jobLabel, bossLabel, modeLabelFor, stageLabelFor, localizedLabel,
} from './parser.js';
import * as account from './account.js?v=15';
import { avatarById } from './profile-avatars.js';
import { initI18n, t, applyDom, getLocale } from './i18n.js?v=20';
import { initPwa, maybeShowAppPromoNotice } from './pwa.js?v=6';

initI18n();
initPwa();

function loc() {
  return getLocale();
}

function jobNameOf(runOrId) {
  const id = typeof runOrId === 'number' ? runOrId : runOrId?.jobId;
  if (id) return jobLabel(id, loc());
  return runOrId?.jobName || runOrId?.name || '—';
}

function bossNameOf(runOrBoss) {
  const id = runOrBoss?.bossId;
  if (id != null && id >= 0) return bossLabel(id, loc());
  return runOrBoss?.bossName || runOrBoss?.name || '—';
}

function runModeLabel(run) {
  return modeLabelFor(run?.mode, loc()) || run?.modeLabel || '';
}

function runStageLabel(run) {
  if (run?.stage == null && run?.stage !== 0) return '—';
  return stageLabelFor(run.stage, loc());
}

const $ = (selector) => document.querySelector(selector);

const landing = $('#landing');
const statusPanel = $('#status');
const statusText = $('#statusText');
const errorPanel = $('#error');
const errorText = $('#errorText');
const dashboard = $('#dashboard');
const dropzone = $('#dropzone');
const fileInput = $('#fileInput');
const demoBtn = $('#demoBtn');
const demoChip = $('#demoChip');
const whereBtn = $('#whereBtn');
const whereBox = $('#whereBox');
const resetBtn = $('#resetBtn');
const errorReset = $('#errorReset');
const playerTitle = $('#playerTitle');
const playerAliases = $('#playerAliases');
const playerSub = $('#playerSub');
const summaryCards = $('#summaryCards');
const charCards = $('#charCards');
const runsBody = $('#runsBody');
const runsCaption = $('#runsCaption');
const toggleRuns = $('#toggleRuns');
const modeTabs = $('#modeTabs');
const modeCompare = $('#modeCompare');
const insights = $('#insights');
const eloCurrent = $('#eloCurrent');
const mateCards = $('#mateCards');
const mateTable = $('#mateTable');
const bossList = $('#bossList');
const bossFilterEl = $('#bossFilter');
const pageTabs = $('#pageTabs');
const bossMatrix = $('#bossMatrix');
const compTable = $('#compTable');

const chartInstances = {};
function modeLabels() {
  return {
    all: t('mode.all'),
    normal: modeLabelFor('normal', loc()),
    deep: modeLabelFor('deep', loc()),
  };
}
const MODE_ORDER = ['all', 'deep', 'normal'];

let allRuns = [];
let recentRuns = [];
let allRoster = [];
let currentFileName = '';
let currentMode = 'all';
let currentStats = null;
let modeStats = {};
let showAllRuns = false;
let isDemo = false;
let bossFilterValue = 'all';
let scope = 'cumulative';
let currentPage = 'overview';
let accountUser = null;
let accountRuns = [];
let syncBusy = false;
let cumulativeRuns = [];
let profileMeta = null;
let activeProfileKey = null;
let relicSlots = [];
let deepRatings = [];
let profileAvatar = '';

const DEMO_JOBS = [
  { jobId: 1, name: '추적자', nameEn: 'Wylder', weight: 0.14, win: 0.74 },
  { jobId: 2, name: '수호자', nameEn: 'Guardian', weight: 0.07, win: 0.86 },
  { jobId: 3, name: '철의 눈', nameEn: 'Ironeye', weight: 0.1, win: 1.0 },
  { jobId: 4, name: '레이디', nameEn: 'Duchess', weight: 0.11, win: 0.92 },
  { jobId: 5, name: '무뢰한', nameEn: 'Raider', weight: 0.06, win: 0.83 },
  { jobId: 6, name: '복수자', nameEn: 'Revenant', weight: 0.15, win: 0.81 },
  { jobId: 7, name: '은둔자', nameEn: 'Recluse', weight: 0.04, win: 0.5 },
  { jobId: 8, name: '집행자', nameEn: 'Executor', weight: 0.04, win: 1.0 },
  { jobId: 9, name: '학자', nameEn: 'Scholar', weight: 0.02, win: 0.5 },
  { jobId: 10, name: '장의사', nameEn: 'Undertaker', weight: 0.19, win: 0.95 },
];

const DEMO_BOSSES = [
  { id: 0, name: '세목 달린 짐승', en: 'Gladius, Beast of Night' },
  { id: 1, name: '달려드는 턱', en: 'Adel, Baron of Night' },
  { id: 2, name: '지성의 벌레', en: 'Gnoster, Wisdom of Night' },
  { id: 3, name: '징조', en: 'Maris, Fathom of Night' },
  { id: 4, name: '조율의 마물', en: 'Libra, Creature of Night' },
  { id: 5, name: '어둠을 달리는 사냥꾼', en: 'Fulghor, Champion of Nightglow' },
  { id: 6, name: '안개의 틈새', en: 'Caligo, Miasma of Night' },
  { id: 7, name: '밤을 본뜬 자', en: 'Heolstor, the Nightlord' },
  { id: 8, name: '안녕을 가져오는 자들', en: 'Weapon-Bequeathed Harmonia' },
  { id: 9, name: '잔해의 왕', en: 'Traitorous Straghess' },
];

const DEMO_MATES = ['beoom', 'Astra', 'Knox', 'MNGHL', 'V50', 'Juno', 'Player', 'Rook', 'Nia', 'Trisha'];

const PALETTE = ['#c9a227', '#7f9fd4', '#7fbf8a', '#b08bd4', '#c96a5f', '#5fb3b3', '#d59a5f', '#8d8d8d', '#a8c26a', '#d47fa8'];

let ratingCharFilter = '';
let eloCharFilter = '';

function runCharacterLabel(run) {
  const name = String(run?.character || '').trim();
  return name || t('chart.unnamedChar');
}

function sameCharacterName(a, b) {
  const left = String(a || '').trim().toLowerCase();
  const right = String(b || '').trim().toLowerCase();
  return Boolean(left) && left === right;
}

function uniqueCharacterLabels(runs) {
  const order = [];
  const seen = new Set();
  for (const run of runs || []) {
    const label = runCharacterLabel(run);
    if (seen.has(label)) continue;
    seen.add(label);
    order.push(label);
  }
  return order;
}

function syncCharacterSelect(selectEl, runs, current) {
  if (!selectEl) return '';
  const characters = uniqueCharacterLabels(runs);
  const latest = runs?.at(-1) ? runCharacterLabel(runs.at(-1)) : '';
  const matchedCurrent = characters.find((name) => sameCharacterName(name, current));
  const matchedLatest = characters.find((name) => sameCharacterName(name, latest));
  const next = matchedCurrent || matchedLatest || characters[0] || '';
  selectEl.innerHTML = characters.map((name) => (
    `<option value="${escapeHtml(name)}" ${name === next ? 'selected' : ''}>${escapeHtml(name)}</option>`
  )).join('');
  selectEl.hidden = characters.length <= 1;
  selectEl.disabled = characters.length === 0;
  return next;
}

function filterRunsByCharacter(runs, character) {
  if (!character) return runs || [];
  return (runs || []).filter((run) => sameCharacterName(runCharacterLabel(run), character));
}

function escapeHtml(value) {
  return String(value).replace(/[&<>"']/g, (char) => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;',
  }[char]));
}

function fmtPct(value, digits = 1) {
  if (!Number.isFinite(value)) return '—';
  return `${value.toFixed(digits)}%`;
}

function fmtDate(ts) {
  const date = new Date(ts);
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  const hh = String(date.getHours()).padStart(2, '0');
  const mm = String(date.getMinutes()).padStart(2, '0');
  return `${y}.${m}.${d} ${hh}:${mm}`;
}

function fmtDay(ts) {
  const date = new Date(ts);
  return `${date.getMonth() + 1}.${String(date.getDate()).padStart(2, '0')}`;
}

function fmtDuration(ms) {
  if (!Number.isFinite(ms) || ms <= 0) return '—';
  const totalSeconds = Math.round(ms / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  if (minutes >= 60) {
    const hours = Math.floor(minutes / 60);
    return t('fmt.hm', { h: hours, m: minutes % 60 });
  }
  return t('fmt.ms', { m: minutes, s: String(seconds).padStart(2, '0') });
}

function fmtAvg(ms) {
  if (!Number.isFinite(ms) || ms <= 0) return '—';
  return t('fmt.avgMin', { n: (ms / 60000).toFixed(1) });
}

function fmtWl(wins, losses) {
  return t('fmt.wl', { w: wins, l: losses });
}

function setVisible(section) {
  for (const node of [landing, statusPanel, errorPanel, dashboard]) node.hidden = node !== section;
}

function showNotice(message, options = {}) {
  const dialog = $('#noticeDialog');
  const text = $('#noticeDialogText');
  const title = $('#noticeDialogTitle');
  const cta = $('#noticeDialogCta');
  const ok = $('#noticeDialogOk');
  const value = String(message || '').trim();
  if (!value) return;
  if (dialog && text && typeof dialog.showModal === 'function') {
    text.textContent = value;
    if (title) {
      const heading = String(options.title || '').trim();
      title.hidden = !heading;
      title.textContent = heading;
    }
    if (cta) {
      const href = String(options.ctaHref || '').trim();
      const label = String(options.ctaLabel || '').trim();
      if (href) {
        cta.hidden = false;
        cta.href = href;
        if (label) cta.textContent = label;
        cta.className = 'btn btn-solid';
        if (ok) ok.className = 'btn btn-ghost';
      } else {
        cta.hidden = true;
        cta.removeAttribute('href');
        if (ok) ok.className = 'btn btn-solid';
      }
    }
    dialog.showModal();
    return;
  }
  window.alert(value);
}

function showGuestSignupNotice() {
  showNotice(t('msg.guestOneShot'), {
    title: t('msg.guestOneShotTitle'),
    ctaHref: 'signup.html',
    ctaLabel: t('msg.guestOneShotCta'),
  });
}

function updateRelicsNav() {
  const link = $('#relicsNavLink');
  if (!link) return;
  const canUse = !isDemo && relicInventoryCount(relicSlots) > 0;
  link.hidden = !canUse;
  // 예전 duckdns 캐시/북마크와 섞이지 않도록 항상 현재 오리진으로 고정
  link.href = `${location.origin}/relics.html`;
}

function showStatus(message) {
  statusText.textContent = message;
  setVisible(statusPanel);
}

function showError(message) {
  errorText.textContent = message;
  setVisible(errorPanel);
}

function destroyCharts() {
  for (const chart of Object.values(chartInstances)) chart.destroy();
  for (const key of Object.keys(chartInstances)) delete chartInstances[key];
}

function resetView() {
  currentStats = null;
  modeStats = {};
  allRuns = [];
  recentRuns = [];
  cumulativeRuns = [];
  profileMeta = null;
  activeProfileKey = null;
  allRoster = [];
  relicSlots = [];
  deepRatings = [];
  profileAvatar = '';
  currentMode = 'all';
  isDemo = false;
  showAllRuns = false;
  destroyCharts();
  modeTabs.innerHTML = '';
  modeCompare.innerHTML = '';
  fileInput.value = '';
  demoChip.hidden = true;
  accountMessage('');
  const url = new URL(location.href);
  if (url.searchParams.has('player')) {
    url.searchParams.delete('player');
    history.replaceState({}, '', url);
  }
  updateRelicsNav();
  setVisible(landing);
  window.scrollTo({ top: 0, behavior: 'smooth' });
}

function relicInventoryCount(slots) {
  return (slots || []).reduce((sum, slot) => sum + (slot.relics?.length || 0), 0);
}

async function persistRelicInventory(profileKey, slots) {
  // 유물도 계정 DB에만 저장. 비로그인은 저장하지 않음.
  if (!profileKey || !slots?.length || !account.configured || !accountUser) {
    return { local: false, remote: false, error: null };
  }
  const updatedAt = new Date().toISOString();
  try {
    await account.saveRelics(profileKey, slots, updatedAt);
    account.saveLocalRelics(profileKey, slots, updatedAt); // 캐시
    return { local: true, remote: true, error: null, updatedAt };
  } catch (error) {
    return { local: false, remote: false, error, updatedAt };
  }
}

async function syncDisplayTagFromServer(profileKey = activeProfileKey) {
  if (!account.configured || !profileKey) return '';
  const origin = account.tagFromKey(profileKey);
  try {
    const detail = await account.fetchProfile(origin);
    const publicTag = detail?.tag || '';
    if (!publicTag) return '';
    const local = account.loadLocalHistory(profileKey);
    account.saveLocalHistory(profileKey, {
      ...local,
      tag: publicTag,
      names: Array.isArray(detail.names) && detail.names.length ? detail.names : local.names,
    });
    if (profileMeta) profileMeta = { ...profileMeta, tag: publicTag };
    const url = new URL(location.href);
    const player = url.searchParams.get('player');
    if (player === origin || player === publicTag) {
      url.searchParams.set('player', publicTag);
      history.replaceState({ tag: publicTag }, '', url);
    }
    return publicTag;
  } catch {
    return '';
  }
}

async function restorePersistedSession() {
  // 비로그인은 누적/복원 없음 (단발 분석만)
  if (!account.configured || !accountUser) return false;

  try {
    const me = await account.fetchMe();
    const profiles = Array.isArray(me?.profiles) ? me.profiles : [];
    let key = account.loadLastProfileKey();
    let summary = profiles.find((row) => row.profileKey === key) || profiles[0] || null;

    const remoteList = await account.fetchRelics(key || null);
    const remoteRelic = Array.isArray(remoteList)
      ? (key
        ? remoteList.find((row) => row.profileKey === key) || remoteList[0]
        : remoteList[0])
      : null;
    if (!summary && remoteRelic?.profileKey) {
      key = remoteRelic.profileKey;
      summary = profiles.find((row) => row.profileKey === key) || null;
    }
    if (!summary && !remoteRelic) return false;

    key = summary?.profileKey || remoteRelic?.profileKey || key;
    if (!key) return false;
    account.rememberLastProfile(key);
    activeProfileKey = key;

    const lookup = summary?.tag || summary?.saveId || account.tagFromKey(key);
    const detail = await account.fetchProfile(lookup);
    const runs = Array.isArray(detail?.runs) ? detail.runs : [];
    const relics = {
      slots: remoteRelic?.slots || [],
      updatedAt: remoteRelic?.updatedAt || null,
    };
    if (relics.slots.length) {
      account.saveLocalRelics(key, relics.slots, relics.updatedAt || new Date().toISOString());
    }

    // 예전 브라우저 아바타가 있고 서버가 비어 있으면 한 번 이관
    const local = account.loadLocalHistory(key);
    let avatar = detail?.avatar || summary?.avatar || '';
    if (!avatar && local.avatar && summary?.id) {
      try {
        const saved = await account.updateAvatar(summary.id, local.avatar);
        avatar = saved?.avatar || local.avatar;
      } catch { /* ignore migrate failure */ }
    }

    account.saveLocalHistory(key, {
      tag: detail?.tag || summary?.tag || account.tagFromKey(key),
      names: detail?.names || summary?.names || local.names || [],
      updatedAt: detail?.updatedAt || summary?.updatedAt || null,
      runs,
      deepRatings: detail?.deepRatings || [],
      avatar,
    });

    if (!runs.length && !relics.slots.length) return false;

    const recent = runs.slice(-100);
    loadData([], recent, recent, t('msg.accountStore'), false, null, {
      cumulative: runs,
      profile: {
        tag: detail?.tag || summary?.tag || account.tagFromKey(key),
        names: detail?.names || summary?.names || [],
        updatedAt: detail?.updatedAt || summary?.updatedAt || null,
        peakRating: detail?.peakRating || summary?.peakRating || 0,
        peakDepth: detail?.peakDepth || summary?.peakDepth || 0,
      },
      scope: 'cumulative',
      relicSlots: relics.slots,
      deepRatings: detail?.deepRatings || summary?.deepRatings || [],
      avatar,
    });
    accountMessage('');
    return true;
  } catch {
    return false;
  }
}

function uniqueNames(list, limit = 24) {
  const names = [];
  const seen = new Set();
  for (const name of list) {
    if (typeof name !== 'string') continue;
    const trimmed = name.trim();
    if (trimmed.length < 1 || trimmed.length > 32 || seen.has(trimmed)) continue;
    seen.add(trimmed);
    names.push(trimmed);
    if (names.length >= limit) break;
  }
  return names;
}

function applyScope(nextScope) {
  // UI에서는 누적 기록만 사용. 데이터가 없으면 최근/전체로 폴백.
  if (nextScope === 'cumulative' || cumulativeRuns.length) scope = 'cumulative';
  else if (nextScope === 'account' && accountRuns.length) scope = 'account';
  else scope = recentRuns.length ? 'recent' : 'all';
}

async function handleFile(file) {
  if (!file || syncBusy) return;
  if (!/\.(sl2|co2)$/i.test(file.name)) {
    showError(t('msg.badExt'));
    return;
  }
  if (!globalThis.crypto?.subtle) {
    showError(t('msg.noCrypto'));
    return;
  }
  syncBusy = true;
  showStatus(t('status.decrypting'));
  try {
    const buffer = await file.arrayBuffer();
    const parsed = await parseSave(buffer);
    await applyParsedSave(parsed, file.name);
    if (accountUser) await syncCurrentRecords({ nested: true });
  } catch (error) {
    showError(error?.message || t('msg.parseFail'));
  } finally {
    syncBusy = false;
  }
}

function mulberry32(seed) {
  return function random() {
    let t = (seed += 0x6d2b79f5);
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

function weightedPick(random, weights) {
  const total = weights.reduce((sum, value) => sum + value, 0);
  let roll = random() * total;
  for (let i = 0; i < weights.length; i++) {
    roll -= weights[i];
    if (roll <= 0) return i;
  }
  return weights.length - 1;
}

function buildDemo() {
  const random = mulberry32(20261107);
  const jobs = DEMO_JOBS.map((job) => ({ ...job }));
  const runs = [];
  let ts = Date.now() - 92 * 86400000;
  for (let id = 1; id <= 100; id++) {
    ts += Math.round((8 + random() * 26) * 3600000);
    const index = weightedPick(random, jobs.map((job) => job.weight));
    const job = jobs[index];
    const deep = random() < 0.42;
    const win = random() < (deep ? job.win * 0.94 : job.win);
    let stage = 6;
    if (!win) {
      const roll = random();
      stage = roll < 0.34 ? 2 : roll < 0.68 ? 3 : 6;
    }
    const durationMs = win
      ? Math.round((30 + random() * 11 + (deep ? 2 : 0)) * 60000)
      : Math.round((stage === 6 ? 28 : 9 + random() * 16) * 60000);
    const party = [{
      index: 1,
      name: 'Self',
      jobId: job.jobId,
      jobName: job.name,
      jobNameEn: job.nameEn,
      level: 15,
      self: true,
    }];
    const mateCount = random() < 0.18 ? 0 : random() < 0.32 ? 1 : 2;
    for (let mateIndex = 0; mateIndex < mateCount; mateIndex++) {
      const mate = jobs[Math.floor(random() * jobs.length)];
      party.push({
        index: mateIndex + 2,
        name: DEMO_MATES[Math.floor(random() * DEMO_MATES.length)],
        jobId: mate.jobId,
        jobName: mate.name,
        jobNameEn: mate.nameEn,
        level: 15,
        self: false,
      });
    }
    const boss = DEMO_BOSSES[Math.floor(random() * DEMO_BOSSES.length)];
    const variantRoll = random();
    const bossVariant = variantRoll < 0.35 && ![7, 9].includes(boss.id) ? 'everdark' : 'normal';
    const demoChars = ['AshenOne', 'Nightfarer', 'Wanderer'];
    const character = demoChars[Math.floor(random() * demoChars.length)];
    const depthCandidate = deep ? (1 + Math.floor(random() * 5)) : 0;
    const ratingCandidate = deep
      ? Math.min(9999, Math.round((800 + depthCandidate * 900 + random() * 700) / 25) * 25)
      : 0;
    runs.push({
      id,
      slot: demoChars.indexOf(character),
      character,
      jobId: job.jobId,
      jobName: job.name,
      jobNameEn: job.nameEn,
      level: 15,
      win,
      mode: deep ? 'deep' : 'normal',
      modeLabel: deep ? modeLabelFor('deep', 'ko') : modeLabelFor('normal', 'ko'),
      durationMs,
      timestamp: ts,
      stage,
      stageLabel: stageLabelFor(stage === 6 ? 6 : stage === 3 ? 3 : 2, 'ko'),
      bossId: boss.id,
      bossName: boss.name,
      bossNameEn: boss.en,
      bossVariant,
      bossVariantLabel: bossVariant === 'everdark' ? 'Everdark' : '',
      party,
      teammates: party.filter((member) => !member.self).map((member) => member.name),
      partyKey: party.map((member) => member.jobId).sort((a, b) => a - b).join('-'),
      partySize: party.length,
      depthCandidate,
      ratingCandidate,
      ratingRaw: ratingCandidate,
    });
  }
  return runs;
}

function scopeRuns() {
  if (cumulativeRuns.length) return cumulativeRuns;
  if (scope === 'account' && accountRuns.length) return accountRuns;
  if (recentRuns.length) return recentRuns;
  return allRuns;
}

function scopeLabel() {
  const count = scopeRuns().length;
  return count ? t('fmt.runs', { n: count }) : t('stat.none');
}

function profileHeading() {
  if (isDemo) return { title: t('dash.demoTitle'), tag: '', roster: [] };
  const latest = [...scopeRuns()].sort((a, b) => a.timestamp - b.timestamp).at(-1);
  const names = uniqueNames([
    ...(profileMeta?.names || []),
    ...allRoster.filter(Boolean).map((entry) => entry.name),
  ]);
  const primary = latest?.character || names[0] || t('dash.myRecord');
  const rawTag = String(profileMeta?.tag || '').trim().replace(/^#/, '');
  const shown = profileMeta?.focusName || primary;
  const title = profileMeta?.searched && !profileMeta.focusName ? (rawTag || shown) : shown;
  const tag = rawTag && title !== rawTag ? rawTag : '';
  const roster = names.filter((name) => name && name !== title);
  return { title, tag, roster };
}

/** 심도 레이팅 구간: 1=0–999, 2=1000–1999, 3=2000–3999, 4=4000–5999, 5=6000–9999 */
function depthTierFromRating(rating) {
  const value = Number(rating);
  if (!Number.isFinite(value) || value < 0) return null;
  if (value < 1000) return 1;
  if (value < 2000) return 2;
  if (value < 4000) return 3;
  if (value < 6000) return 4;
  return 5;
}

/**
 * 심도 레이팅 표시:
 * - 캐릭터 슬롯에 저장된 현재값이 있으면 그걸 우선 (인게임 “지금 점수”)
 * - 없으면 해당 캐릭터 심도 원정 스냅샷의 최근/최고값
 * 계정 전체를 max로 섞지 않는다. 캐릭터마다 점수가 다르다.
 */
const DEEP_RATING_CAP = 9999;
const DEEP_WIN_GAIN_MAX = 300;

function slotDeepRatingForCharacter(characterName, slotRatings = deepRatings) {
  const label = String(characterName || '').trim();
  if (!label) return null;
  let best = null;
  for (const slot of slotRatings || []) {
    if (!sameCharacterName(slot?.name, label)) continue;
    const rating = normalizeDeepRating(slot?.rating);
    if (rating <= 0) continue;
    if (!best || rating > best.rating) {
      best = {
        rating,
        depth: Number(slot.depth) || depthTierFromRating(rating),
        source: 'slot',
        name: String(slot.name || '').trim() || label,
      };
    }
  }
  return best;
}

/** 표시용 현재 점수: 슬롯 저장값 우선, 없으면 해당 캐릭터 최근 심도 스냅샷(추정 +300 없음). */
function characterDeepRating(characterName, runs = scopeRuns(), slotRatings = deepRatings) {
  const label = String(characterName || '').trim();
  const fromSlot = slotDeepRatingForCharacter(label, slotRatings);
  if (fromSlot) return fromSlot;

  let latest = null;
  for (const run of runs || []) {
    if (run?.mode !== 'deep') continue;
    if (label && !sameCharacterName(runCharacterLabel(run), label)) continue;
    const rating = normalizeDeepRating(run.ratingCandidate ?? run.ratingRaw);
    if (rating <= 0) continue;
    if (!latest || (run.timestamp || 0) >= (latest.timestamp || 0)) {
      latest = {
        rating,
        depth: Number(run.depthCandidate) || depthTierFromRating(rating),
        source: 'runs',
        timestamp: run.timestamp || 0,
        name: runCharacterLabel(run),
      };
    }
  }
  return latest;
}

/** 차트용: 원정 스냅샷 뒤에 슬롯 현재 점수를 이어서 실제 현재값이 끝점에 오게 한다. */
function ratingSeriesForCharacter(runs, characterName, slotRatings = deepRatings) {
  const snapshots = filterRunsByCharacter(runs, characterName)
    .filter((run) => run.mode === 'deep' && Number.isFinite(run.ratingCandidate) && run.ratingCandidate > 0)
    .slice()
    .sort((a, b) => (a.timestamp || 0) - (b.timestamp || 0));
  const current = slotDeepRatingForCharacter(characterName, slotRatings);
  if (!current) {
    return snapshots.map((run) => ({
      timestamp: run.timestamp,
      rating: run.ratingCandidate,
      depth: run.depthCandidate,
      jobId: run.jobId,
      character: runCharacterLabel(run),
      source: 'run',
      run,
    }));
  }
  const points = snapshots.map((run) => ({
    timestamp: run.timestamp,
    rating: run.ratingCandidate,
    depth: run.depthCandidate,
    jobId: run.jobId,
    character: runCharacterLabel(run),
    source: 'run',
    run,
  }));
  const lastTs = points.at(-1)?.timestamp || Date.now();
  const lastRating = points.at(-1)?.rating;
  if (lastRating !== current.rating) {
    points.push({
      timestamp: lastTs + 1,
      rating: current.rating,
      depth: current.depth,
      jobId: points.at(-1)?.jobId,
      character: current.name || characterName,
      source: 'slot',
      run: null,
    });
  } else if (points.length) {
    points[points.length - 1] = {
      ...points[points.length - 1],
      rating: current.rating,
      depth: current.depth,
      source: 'slot',
    };
  } else {
    points.push({
      timestamp: Date.now(),
      rating: current.rating,
      depth: current.depth,
      character: current.name || characterName,
      source: 'slot',
      run: null,
    });
  }
  return points;
}

/** 계정 최고 심도 점수: 슬롯 현재값 max → 서버 peak → 원정 스냅샷 max */
function peakDeepRating(runs = scopeRuns(), slotRatings = deepRatings) {
  let best = null;
  for (const slot of slotRatings || []) {
    const rating = normalizeDeepRating(slot?.rating);
    if (rating <= 0) continue;
    const depth = Number(slot.depth) || depthTierFromRating(rating);
    if (!best || rating > best.rating) best = { rating, depth, source: 'slot' };
  }
  if (best) return best;

  const storedRating = normalizeDeepRating(profileMeta?.peakRating);
  const storedDepth = Number(profileMeta?.peakDepth) || depthTierFromRating(storedRating);
  if (storedRating > 0 && storedDepth) {
    return { rating: storedRating, depth: storedDepth, source: 'profile' };
  }

  for (const run of runs || []) {
    if (run?.mode !== 'deep') continue;
    const rating = normalizeDeepRating(run.ratingCandidate ?? run.ratingRaw);
    if (rating <= 0) continue;
    const depth = Number(run.depthCandidate) || depthTierFromRating(rating);
    if (!best || rating > best.rating) best = { rating, depth, source: 'runs' };
  }
  return best;
}

function playerRatingBadgeHtml(peak) {
  if (!peak?.rating || !peak.depth) return '';
  const value = Math.min(DEEP_RATING_CAP, Math.round(peak.rating)).toLocaleString('en-US');
  return `
    <span class="player-rating depth-${peak.depth}" title="${escapeHtml(t('dash.ratingPeak', { depth: peak.depth }))}">
      <span class="player-rating-mark" aria-hidden="true">◆</span>
      <span class="player-rating-depth">${escapeHtml(t('dash.depth', { n: peak.depth }))}</span>
      <span class="player-rating-value">${value}</span>
    </span>
  `;
}

async function applyParsedSave(parsed, fileName) {
  const identity = parsed.identity || {};
  const slotDeepRatings = parsed.deepRatings || [];
  const fileNames = uniqueNames([
    ...(parsed.identity?.names || []),
    ...parsed.runs.map((run) => run.character),
  ]);

  // 비로그인 / Steam 키 없음: 이번 파일만 단발 표시, 누적·서버 저장 없음
  if (!accountUser || !identity.profileKey) {
    activeProfileKey = '';
    loadData(parsed.roster, parsed.runs, parsed.recentRuns, fileName, false, parsed.diagnostics, {
      cumulative: parsed.runs,
      profile: identity.profileKey
        ? { tag: account.tagFromKey(identity.profileKey), names: fileNames, updatedAt: null }
        : null,
      scope: 'cumulative',
      relicSlots: parsed.relicSlots || [],
      deepRatings: slotDeepRatings,
      avatar: '',
    });
    if (!identity.profileKey) showNotice(t('msg.noSteam'));
    else showGuestSignupNotice();
    return;
  }

  activeProfileKey = identity.profileKey;
  const originTag = account.tagFromKey(identity.profileKey);
  let previousRuns = [];
  let previousNames = [];
  let previousAvatar = profileAvatar || '';
  let publicTag = originTag;
  try {
    const remote = await account.fetchProfile(originTag);
    previousRuns = Array.isArray(remote?.runs) ? remote.runs : [];
    previousNames = Array.isArray(remote?.names) ? remote.names : [];
    previousAvatar = remote?.avatar || previousAvatar;
    publicTag = remote?.tag || originTag;
  } catch {
    // 첫 공개 등록이거나 아직 프로필 없음
  }

  const merged = account.mergeRecords(previousRuns, parsed.runs);
  const names = uniqueNames([...fileNames, ...previousNames]);
  let peakRating = 0;
  let peakDepth = 0;
  for (const slot of slotDeepRatings) {
    const rating = normalizeDeepRating(slot?.rating);
    if (rating <= peakRating) continue;
    peakRating = rating;
    peakDepth = Number(slot.depth) || depthTierFromRating(rating);
  }
  const profile = {
    tag: publicTag,
    names,
    updatedAt: new Date().toISOString(),
    peakRating,
    peakDepth,
  };
  const cumulative = merged.runs;
  account.saveLocalHistory(identity.profileKey, {
    tag: publicTag,
    names,
    updatedAt: profile.updatedAt,
    runs: cumulative,
    deepRatings: slotDeepRatings,
    avatar: previousAvatar,
  });
  const relicPersist = await persistRelicInventory(identity.profileKey, parsed.relicSlots || []);
  const fresh = merged.added;
  loadData(parsed.roster, parsed.runs, parsed.recentRuns, fileName, false, parsed.diagnostics, {
    cumulative,
    profile,
    scope: 'cumulative',
    relicSlots: parsed.relicSlots || [],
    deepRatings: slotDeepRatings,
    avatar: previousAvatar,
  });
  if (relicPersist.error) {
    showNotice(t('msg.updatedRelicFail', { n: fresh, error: relicPersist.error.message }));
  } else {
    showNotice(fresh > 0 ? t('msg.updated', { n: fresh }) : t('msg.noNew'));
  }
  await publishCurrentProfile({ nested: true });
  await syncDisplayTagFromServer(identity.profileKey);
  if (currentStats) render(currentStats);
}

async function publishCurrentProfile(options = {}) {
  if (!account.configured || !accountUser || !activeProfileKey || isDemo || !cumulativeRuns.length) return;
  if (syncBusy && !options.nested) return;
  const ownLock = !options.nested;
  if (ownLock) syncBusy = true;
  try {
    const published = await account.publishProfile(
      activeProfileKey,
      (profileMeta?.names || []).slice(0, 10),
      cumulativeRuns,
      deepRatings,
      profileAvatar || '',
    );
    const remote = await account.fetchProfile(published.tag);
    const remoteRuns = Array.isArray(remote?.runs) ? remote.runs : [];
    const remoteMerge = account.mergeRecords(cumulativeRuns, remoteRuns);
    const nextAvatar = remote?.avatar || profileAvatar || published.avatar || '';
    const profile = {
      tag: published.tag || profileMeta?.tag,
      names: uniqueNames([...(remote?.names || []), ...(profileMeta?.names || [])]),
      updatedAt: published.updatedAt || new Date().toISOString(),
      peakRating: remote?.peakRating || published.peakRating || 0,
      peakDepth: remote?.peakDepth || published.peakDepth || 0,
    };
    account.saveLocalHistory(activeProfileKey, {
      ...profile,
      runs: remoteMerge.runs,
      deepRatings: Array.isArray(remote?.deepRatings) && remote.deepRatings.length ? remote.deepRatings : deepRatings,
      avatar: nextAvatar,
    });
    loadData(allRoster, allRuns, recentRuns, currentFileName, false, null, {
      cumulative: remoteMerge.runs,
      profile,
      scope: 'cumulative',
      relicSlots, // 공개 등록 후에도 유물 인벤 유지
      deepRatings: Array.isArray(remote?.deepRatings) && remote.deepRatings.length ? remote.deepRatings : deepRatings,
      avatar: nextAvatar,
    });
    const url = new URL(location.href);
    url.searchParams.set('player', profile.tag);
    url.searchParams.delete('demo');
    history.replaceState({ tag: profile.tag }, '', url);
  } catch (error) {
    showNotice(t('msg.publishFail', { error: error.message }));
  } finally {
    if (ownLock) syncBusy = false;
  }
}

function rebuildStats() {
  const runs = scopeRuns();
  const allStats = buildStats(allRoster, runs);
  modeStats = {
    all: allStats,
    normal: buildStats(allRoster, allStats.runs.filter((run) => run.mode === 'normal'), { keepElo: true }),
    deep: buildStats(allRoster, allStats.runs.filter((run) => run.mode === 'deep'), { keepElo: true }),
    unknown: buildStats(allRoster, allStats.runs.filter((run) => run.mode === 'unknown'), { keepElo: true }),
  };
}

function loadData(roster, runs, recent, fileName, demo, diagnostics = null, options = {}) {
  allRoster = roster;
  allRuns = runs;
  recentRuns = recent?.length ? recent : runs;
  currentFileName = fileName;
  isDemo = demo;
  if (options.cumulative) cumulativeRuns = options.cumulative;
  else if (!options.keepCumulative) cumulativeRuns = [];
  profileMeta = options.profile === undefined ? null : options.profile;
  if (options.relicSlots !== undefined) {
    relicSlots = options.relicSlots || [];
  } else if (demo) {
    relicSlots = [];
  }
  if (options.deepRatings !== undefined) {
    deepRatings = options.deepRatings || [];
  } else if (demo) {
    deepRatings = [];
  }
  if (options.avatar !== undefined) {
    profileAvatar = options.avatar || '';
  } else if (demo) {
    profileAvatar = '';
  }
  currentPage = 'overview';
  showAllRuns = false;
  bossFilterValue = 'all';
  applyScope('cumulative');
  if (bossFilterEl) {
    for (const button of bossFilterEl.querySelectorAll('button')) {
      button.classList.toggle('active', button.dataset.variant === 'all');
    }
  }
  rebuildStats();
  renderModeTabs();
  renderModeCompare();
  setMode('all');
  setPage('overview', false);
  updateRelicsNav();
}

function renderModeTabs() {
  const labels = modeLabels();
  modeTabs.innerHTML = MODE_ORDER.map((mode) => {
    const stats = modeStats[mode];
    return `
      <button class="mode-tab ${mode === currentMode ? 'active' : ''}" data-mode="${mode}" type="button">
        <strong>${labels[mode]}</strong>
        <span>${t('ui.runsUnit', { n: stats.total })} · ${fmtPct(stats.winRate, 0)}</span>
      </button>
    `;
  }).join('');
}

function renderModeCompare() {
  const labels = modeLabels();
  modeCompare.innerHTML = ['normal', 'deep'].map((mode) => {
    const stats = modeStats[mode];
    const top = stats.jobs[0];
    return `
      <div class="mode-card ${mode}">
        <div class="mode-card-head">
          <strong>${labels[mode]}</strong>
          <span>${t('ui.runsUnit', { n: stats.total })}</span>
        </div>
        <div class="mode-card-rate">${fmtPct(stats.winRate)}</div>
        <div class="mode-card-meta">
          <span>${fmtWl(stats.wins, stats.losses)}</span>
          <span>${t('ui.clearAvg', { avg: fmtAvg(stats.winMs) })}</span>
          <span>${t('ui.finalDay', { rate: stats.finalRuns ? fmtPct(stats.finalRate, 0) : '—', reached: stats.finalReached, total: stats.finalRuns })}</span>
          <span>${top ? escapeHtml(t('stat.most', { name: jobNameOf(top) })) : escapeHtml(t('stat.none'))}</span>
        </div>
      </div>
    `;
  }).join('');
}

function setMode(mode) {
  currentMode = mode;
  showAllRuns = false;
  currentStats = modeStats[mode];
  for (const button of modeTabs.querySelectorAll('.mode-tab')) {
    button.classList.toggle('active', button.dataset.mode === mode);
  }
  render(currentStats);
}

function setPage(page, scroll = true) {
  currentPage = page;
  if (pageTabs) {
    for (const button of pageTabs.querySelectorAll('button')) {
      button.classList.toggle('active', button.dataset.page === page);
    }
  }
  for (const section of document.querySelectorAll('.page')) {
    section.hidden = section.dataset.page !== page;
  }
  if (currentStats && !currentStats.empty) {
    requestAnimationFrame(() => {
      if (currentStats && !currentStats.empty) renderCharts(currentStats);
    });
  }
  if (scroll) window.scrollTo({ top: 0, behavior: 'smooth' });
}

function statCard(label, value, sub, className = '') {
  return `
    <article class="stat-card">
      <div class="stat-label">${label}</div>
      <div class="stat-value ${className}">${value}</div>
      <div class="stat-sub">${sub}</div>
    </article>
  `;
}

function renderProfileAvatar() {
  const frame = $('#avatarFrame');
  const img = $('#playerAvatar');
  const avatar = avatarById(profileAvatar);
  if (frame) {
    frame.hidden = !avatar;
    frame.title = avatar ? jobLabel(avatar.jobId, loc()) : t('dash.avatar');
  }
  if (img) {
    if (avatar) {
      img.hidden = false;
      img.src = avatar.file;
      img.alt = avatar.name;
    } else {
      img.hidden = true;
      img.removeAttribute('src');
      img.alt = '';
    }
  }
}

function render(stats) {
  demoChip.hidden = !isDemo;
  const heading = profileHeading();
  const accountPeak = peakDeepRating(scopeRuns());
  const focusRating = characterDeepRating(heading.title, scopeRuns());
  playerTitle.innerHTML = `${escapeHtml(heading.title)}${
    heading.tag ? ` <span class="player-tag">#${escapeHtml(heading.tag)}</span>` : ''
  }${playerRatingBadgeHtml(accountPeak)}`;
  renderProfileAvatar();
  if (heading.roster?.length) {
    playerAliases.hidden = false;
    playerAliases.innerHTML = `
      <details class="player-roster">
        <summary>${escapeHtml(t('dash.roster', { n: heading.roster.length + 1 }))}</summary>
        <ul>
          <li class="is-current">${escapeHtml(heading.title)}${playerRatingBadgeHtml(focusRating)}</li>
          ${heading.roster.map((name) => {
            const peak = characterDeepRating(name, scopeRuns());
            return `<li>${escapeHtml(name)}${playerRatingBadgeHtml(peak)}</li>`;
          }).join('')}
        </ul>
      </details>
    `;
  } else {
    playerAliases.hidden = true;
    playerAliases.innerHTML = '';
  }
  const played = stats.jobs.length;
  const modeLabel = modeLabels()[currentMode];
  const ratingPool = stats.runs.filter((run) => (
    run.mode === 'deep' && Number.isFinite(run.ratingCandidate) && run.ratingCandidate > 0
  ));
  // 드롭다운은 심도 전적이 있는 캐릭터만 (슬롯 점수만 있는 캐릭터는 제외)
  ratingCharFilter = syncCharacterSelect($('#ratingCharSelect'), ratingPool, ratingCharFilter);
  eloCharFilter = syncCharacterSelect($('#eloCharSelect'), stats.runs, eloCharFilter);

  const eloFiltered = filterRunsByCharacter(stats.runs, eloCharFilter);
  const latestElo = eloFiltered.at(-1);
  if (eloCurrent) {
    eloCurrent.textContent = latestElo?.elo
      ? t('stat.current', { n: latestElo.elo })
      : '';
  }
  const selectedCharRating = characterDeepRating(ratingCharFilter, scopeRuns());
  const ratings = filterRunsByCharacter(ratingPool, ratingCharFilter);
  const latestRating = ratings.at(-1);
  if (selectedCharRating?.rating) {
    $('#ratingCurrent').textContent = t('stat.current', {
      n: Math.round(selectedCharRating.rating).toLocaleString(),
    });
  } else if (latestRating) {
    $('#ratingCurrent').textContent = t('stat.latest', {
      n: latestRating.ratingCandidate.toLocaleString(),
    });
  } else {
    $('#ratingCurrent').textContent = '';
  }
  $('#ratingEmpty').hidden = ratingPool.length > 0;
  if (playerSub) {
    playerSub.hidden = true;
    playerSub.textContent = '';
  }
  if (stats.empty) {
    summaryCards.innerHTML = `<div class="empty-card">${escapeHtml(t('msg.emptyMode', { mode: modeLabel }))}</div>`;
    charCards.innerHTML = '';
    runsBody.innerHTML = '';
    insights.innerHTML = '';
    if (mateCards) mateCards.innerHTML = `<div class="empty-card">${escapeHtml(t('msg.emptyMates'))}</div>`;
    if (mateTable) mateTable.innerHTML = '';
    if (bossList) bossList.innerHTML = `<div class="empty-card">${escapeHtml(t('msg.emptyBoss'))}</div>`;
    if (bossMatrix) bossMatrix.innerHTML = `<div class="empty-card">${escapeHtml(t('msg.emptyMatrix'))}</div>`;
    if (compTable) compTable.innerHTML = `<div class="empty-card">${escapeHtml(t('msg.emptyComp'))}</div>`;
    destroyCharts();
    setVisible(dashboard);
    window.scrollTo({ top: 0, behavior: 'smooth' });
    return;
  }

  summaryCards.innerHTML = [
    statCard(t('stat.winRate'), fmtPct(stats.winRate), fmtWl(stats.wins, stats.losses), stats.winRate >= 50 ? 'win' : 'loss'),
    statCard(t('stat.record'), t('fmt.recordShort', { w: stats.wins, l: stats.losses }), `${currentMode === 'all' ? '' : `${modeLabel} · `}${t('fmt.runs', { n: stats.total })}`),
    statCard(t('stat.recent10'), `${stats.recent10}<small>/${stats.recent10Total}</small>`, t('stat.recent10Sub')),
    statCard(t('stat.streak'), `${stats.currentStreak}<small>${escapeHtml(t('unit.run'))}</small>`, t('stat.streakSub', { n: stats.bestStreak }), stats.currentStreak > 0 ? 'win' : ''),
    statCard(t('stat.avgClear'), fmtAvg(stats.winMs), t('stat.avgClearSub', { avg: fmtAvg(stats.avgMs) })),
    statCard(t('stat.finalRate'), stats.finalRuns ? fmtPct(stats.finalRate) : '—', t('stat.finalSub', { reached: stats.finalReached, total: stats.finalRuns, missing: stats.total - stats.finalRuns })),
    statCard(t('stat.playTime'), t('fmt.hours', { n: (stats.totalMs / 3600000).toFixed(1) }), t('stat.playTimeSub')),
    statCard(t('stat.jobs'), `${played}<small>${escapeHtml(t('unit.count'))}</small>`, stats.jobs[0] ? escapeHtml(t('stat.jobsSub', { name: jobNameOf(stats.jobs[0]) })) : '—'),
  ].join('');

  renderJobCards(stats);
  renderMates(stats);
  renderBosses(stats);
  renderBossMatrix(stats);
  renderCompositions(stats);
  renderTable(stats, false);
  renderForm(stats);
  renderInsights(stats);
  setVisible(dashboard);
  renderCharts(stats);
  window.scrollTo({ top: 0, behavior: 'smooth' });
}

const JOB_ICONS = {
  1: 'wylder.webp',
  2: 'guardian.webp',
  3: 'ironeye.webp',
  4: 'dutchess.webp',
  5: 'raider.webp',
  6: 'revenant.webp',
  7: 'recluse.webp',
  8: 'executor.webp',
  9: 'scholar.webp',
  10: 'undertaker.webp',
};

function winIndex(job) {
  return job.runs ? (job.wins * job.wins) / job.runs : 0;
}

function renderJobCards(stats) {
  const jobs = [...stats.jobs].sort((a, b) => winIndex(b) - winIndex(a) || b.winRate - a.winRate || b.runs - a.runs || a.jobId - b.jobId);
  const bestIndex = jobs.reduce((best, job) => Math.max(best, winIndex(job)), 0);
  charCards.innerHTML = jobs.map((job) => {
    const best = winIndex(job) === bestIndex && bestIndex > 0;
    return `
    <div class="char-card${best ? ' is-best' : ''}">
      <div class="char-top">
        ${JOB_ICONS[job.jobId] ? `<img class="char-icon" src="assets/img/${JOB_ICONS[job.jobId]}" alt="" width="48" height="48" />` : ''}
        <div class="char-id">
          <div class="char-name">${best ? '<span class="char-star" title="★">★</span>' : ''}${escapeHtml(jobNameOf(job))}</div>
          <div class="char-slot">${escapeHtml(job.nameEn || `JOB ${job.jobId}`)}</div>
        </div>
        <div class="char-rate">${fmtPct(job.winRate)}</div>
      </div>
      <div class="char-meta">
        <span>${fmtWl(job.wins, job.losses)} · ${t('ui.runsUnit', { n: job.runs })}</span>
        <span>${t('ui.winAvg', { win: fmtAvg(job.avgWinMs), all: fmtAvg(job.avgDurationMs) })}</span>
      </div>
      <div class="bar"><span style="width:${Math.max(0, Math.min(100, job.winRate))}%"></span></div>
    </div>
  `;
  }).join('');
}

function mateCard(mate, index) {
  return `
    <div class="mate-card">
      <div class="mate-top">
        <span class="mate-rank">${index + 1}</span>
        <span class="mate-name">${escapeHtml(mate.name)}</span>
        <span class="mate-rate">${fmtPct(mate.winRate)}</span>
      </div>
      <div class="mate-meta">${t('ui.runsUnit', { n: mate.runs })} · ${fmtWl(mate.wins, mate.losses)}</div>
      <div class="mate-meta">${t('ui.recentDay', { date: fmtDate(mate.lastPlayed).slice(0, 10) })}</div>
    </div>
  `;
}

function renderMates(stats) {
  if (!mateCards) return;
  const mates = stats.teammates || [];
  if (!mates.length) {
    mateCards.innerHTML = `<div class="empty-card">${escapeHtml(t('msg.emptySolo'))}</div>`;
    if (mateTable) mateTable.innerHTML = '';
    return;
  }
  mateCards.innerHTML = mates.slice(0, 3).map(mateCard).join('');
  if (!mateTable) return;
  const repeated = mates.filter((mate) => mate.runs >= 2);
  const singles = mates.filter((mate) => mate.runs < 2).length;
  mateTable.innerHTML = repeated.length
    ? `
      <table class="mate-table">
        <thead>
          <tr><th>${escapeHtml(t('mate.player'))}</th><th>${escapeHtml(t('mate.together'))}</th><th>${escapeHtml(t('mate.record'))}</th><th>${escapeHtml(t('mate.rate'))}</th><th>${escapeHtml(t('mate.recent'))}</th></tr>
        </thead>
        <tbody>
          ${repeated.map((mate) => `
            <tr>
              <td>${escapeHtml(mate.name)}</td>
              <td>${t('ui.runsUnit', { n: mate.runs })}</td>
              <td>${fmtWl(mate.wins, mate.losses)}</td>
              <td>${fmtPct(mate.winRate)}</td>
              <td>${fmtDate(mate.lastPlayed).slice(0, 10)}</td>
            </tr>
          `).join('')}
        </tbody>
      </table>
      ${singles ? `<p class="mate-singles">${escapeHtml(t('mate.once', { n: singles }))}</p>` : ''}
    `
    : `<p class="mate-singles">${escapeHtml(t('mate.noneMulti', { n: singles }))}</p>`;
}

function miniRow(label, runs, wins, losses, winRate) {
  return `
    <div class="mini-row">
      <span class="mini-label">${escapeHtml(label)}</span>
      <span>${t('ui.runsUnit', { n: runs })}</span>
      <span>${wins}-${losses}</span>
      <strong>${fmtPct(winRate)}</strong>
    </div>
  `;
}

function renderBosses(stats) {
  if (!bossList) return;
  const bosses = (stats.bosses || []).filter((boss) => bossFilterValue === 'all' || boss.variant === bossFilterValue);
  if (!bosses.length) {
    bossList.innerHTML = `<div class="empty-card">${escapeHtml(t('msg.emptyBossMode', { mode: modeLabels()[currentMode] }))}</div>`;
    return;
  }
  bossList.innerHTML = bosses.map((boss) => {
    const variantBadge = boss.variant === 'everdark'
      ? `<span class="variant-badge everdark">${escapeHtml(t('boss.everdark'))}</span>`
      : '';
    const dlcBadge = boss.bossId >= 8 ? '<span class="variant-badge dlc">DLC</span>' : '';
    const ownRows = boss.ownJobs.map((job) => miniRow(jobNameOf(job), job.runs, job.wins, job.losses, job.winRate)).join('');
    const compRows = boss.compositions.slice(0, 6).map((comp) => miniRow(comp.label, comp.runs, comp.wins, comp.losses, comp.winRate)).join('');
    const more = boss.compositions.length > 6 ? `<p class="mini-more">${escapeHtml(t('boss.moreComps', { n: boss.compositions.length - 6 }))}</p>` : '';
    return `
      <details class="boss-card" data-boss="${boss.bossId}" data-variant="${boss.variant}">
        <summary>
          <span class="boss-name" title="ID ${boss.bossId} · ${boss.variant}">${escapeHtml(bossNameOf(boss))} ${dlcBadge}${variantBadge}</span>
          <span class="boss-summary-meta">${t('fmt.runs', { n: boss.runs })} · ${fmtWl(boss.wins, boss.losses)} · ${fmtPct(boss.winRate)}${boss.avgWinMs ? ` · ${t('boss.avg', { avg: fmtAvg(boss.avgWinMs) })}` : ''}</span>
        </summary>
        <div class="boss-body">
          <section class="boss-col">
            <h4>${escapeHtml(t('boss.myJob'))}</h4>
            <div class="mini-list">${ownRows}</div>
          </section>
          <section class="boss-col">
            <h4>${escapeHtml(t('boss.partyComps'))}</h4>
            <div class="mini-list">${compRows}${more}</div>
          </section>
        </div>
      </details>
    `;
  }).join('');
}

function renderBossMatrix(stats) {
  if (!bossMatrix) return;
  const bosses = (stats.bosses || []).filter((boss) => bossFilterValue === 'all' || boss.variant === bossFilterValue);
  if (!bosses.length) {
    bossMatrix.innerHTML = `<div class="empty-card">${escapeHtml(t('msg.emptyMatrix'))}</div>`;
    return;
  }
  const jobIds = Array.from({ length: 10 }, (_, index) => index + 1);
  const head = `<tr><th class="matrix-boss">${escapeHtml(t('matrix.boss'))}</th>${jobIds.map((id) => `<th>${escapeHtml(jobLabel(id, loc()))}</th>`).join('')}</tr>`;
  const rows = bosses.map((boss) => {
    const byJob = new Map(boss.ownJobs.map((job) => [job.jobId, job]));
    const cells = jobIds.map((id) => {
      const job = byJob.get(id);
      if (!job) return `<td class="matrix-cell empty">·</td>`;
      const tone = job.winRate >= 70 ? 'high' : job.winRate >= 50 ? 'mid' : 'low';
      return `<td class="matrix-cell ${tone}" title="${t('ui.runsUnit', { n: job.runs })} · ${fmtWl(job.wins, job.losses)}"><span>${job.wins}-${job.losses}</span><small>${Math.round(job.winRate)}%</small></td>`;
    }).join('');
    const badge = boss.variant === 'everdark' ? `<span class="variant-badge everdark">${escapeHtml(t('boss.everdark'))}</span>` : '';
    return `<tr><th class="matrix-boss" title="ID ${boss.bossId} · ${boss.variant}"><span>${escapeHtml(bossNameOf(boss))}</span>${badge}</th>${cells}</tr>`;
  }).join('');
  bossMatrix.innerHTML = `<table class="matrix"><thead>${head}</thead><tbody>${rows}</tbody></table>`;
}

function renderCompositions(stats) {
  if (!compTable) return;
  const all = stats.compositions || [];
  const repeated = all.filter((comp) => comp.runs >= 2);
  const shown = (repeated.length ? repeated : all).slice(0, 12);
  if (!shown.length) {
    compTable.innerHTML = `<div class="empty-card">${escapeHtml(t('msg.emptyComp'))}</div>`;
    return;
  }
  compTable.innerHTML = `
    <table class="mate-table comp-table">
      <thead><tr><th>${escapeHtml(t('comp.party'))}</th><th>${escapeHtml(t('comp.runs'))}</th><th>${escapeHtml(t('comp.record'))}</th><th>${escapeHtml(t('comp.rate'))}</th></tr></thead>
      <tbody>
        ${shown.map((comp) => `
          <tr>
            <td>${escapeHtml(comp.label)}</td>
            <td>${t('ui.runsUnit', { n: comp.runs })}</td>
            <td>${fmtWl(comp.wins, comp.losses)}</td>
            <td>${fmtPct(comp.winRate)}</td>
          </tr>
        `).join('')}
      </tbody>
    </table>
    ${all.length > shown.length ? `<p class="mate-singles">${escapeHtml(t('comp.more', { n: all.length - shown.length }))}</p>` : ''}
  `;
}

function renderTable(stats, all) {
  const rows = [...stats.runs].reverse();
  const shown = all ? rows : rows.slice(0, 20);
  runsBody.innerHTML = shown.map((run) => `
    <tr>
      <td title="${run.source === 'recovered' ? '캐릭터 영역 잔존 기록' : '저장 목록'} · USERDATA_${String(run.sourceEntry ?? 10).padStart(2, '0')} · 0x${(run.sourceOffset ?? 0).toString(16)} · ${fmtDate(run.timestamp)}">${fmtDate(run.timestamp).slice(0, 10)}<small class="record-id">#${run.id}${run.source === 'recovered' ? ` · ${t('runs.recovered')}` : ''}</small></td>
      <td title="${run.level ? escapeHtml(t('runs.level', { n: run.level })) : ''}">${escapeHtml(jobNameOf(run))}</td>
      <td title="ID ${run.bossId ?? '—'} · ${escapeHtml(run.character || 'demo')}">${escapeHtml(bossNameOf(run))}${run.bossVariant === 'everdark' ? ` <span class="variant-badge everdark">${escapeHtml(t('boss.everdark'))}</span>` : ''}</td>
      <td title="mode ${run.rawMode ?? 'demo'} · depth ${run.depthCandidate ?? '—'} · rating ${run.ratingCandidate ?? '—'}"><span class="mode-pill ${run.mode}">${escapeHtml(runModeLabel(run))}</span></td>
      <td><span class="result-pill ${run.win ? 'win' : 'loss'}">${escapeHtml(run.win ? t('runs.win') : t('runs.loss'))}</span></td>
      <td><span class="stage-pill">${escapeHtml(runStageLabel(run))}</span></td>
      <td>${fmtDuration(run.durationMs)}</td>
      <td class="elo-cell">${run.elo ?? '—'}</td>
    </tr>
  `).join('');
  const modeLabel = modeLabels()[currentMode];
  runsCaption.textContent = all
    ? t('msg.runsCaptionAll', { scope: scopeLabel(), mode: modeLabel, n: stats.total })
    : t('msg.runsCaptionRecent', { scope: scopeLabel(), mode: modeLabel });
  toggleRuns.textContent = all ? t('runs.toggleRecent') : t('runs.toggleAll', { n: stats.total });
}

function renderForm(stats) {
  const target = $('#recentForm');
  if (!target) return;
  const last10 = stats.runs.slice(-10);
  const wins = last10.filter((run) => run.win).length;
  const stages = { 1: 0, 2: 0, 3: 0, 6: 0 };
  for (const run of stats.runs) {
    if (run.stage in stages) stages[run.stage]++;
  }
  target.innerHTML = `
    <div class="form-strip">
      ${last10.map((run) => `
        <span class="form-dot ${run.win ? 'win' : 'loss'}" title="${escapeHtml(jobNameOf(run))} · ${escapeHtml(runModeLabel(run))} · ${escapeHtml(run.win ? t('runs.win') : t('runs.loss'))} · ${escapeHtml(runStageLabel(run))}">${run.win ? 'W' : 'L'}</span>
      `).join('')}
    </div>
    <p class="form-caption">${escapeHtml(t('form.caption', { final: stats.finalRuns, missing: stats.total - stats.finalRuns }))}</p>
    <div class="form-summary">
      <div><span class="stat-label">${escapeHtml(t('form.recent10'))}</span><strong>${fmtWl(wins, last10.length - wins)}</strong></div>
      <div><span class="stat-label">${escapeHtml(t('form.finalReach'))}</span><strong>${t('ui.runsUnit', { n: stages[6] })}</strong></div>
      <div><span class="stat-label">${escapeHtml(t('form.day1End'))}</span><strong>${t('ui.runsUnit', { n: stages[2] })}</strong></div>
      <div><span class="stat-label">${escapeHtml(t('form.day2End'))}</span><strong>${t('ui.runsUnit', { n: stages[3] })}</strong></div>
    </div>
    <div class="stage-bar" aria-hidden="true">
      <span class="stage-seg day1" style="width:${stats.finalRuns ? (stages[2] / stats.finalRuns) * 100 : 0}%"></span>
      <span class="stage-seg day2" style="width:${stats.finalRuns ? (stages[3] / stats.finalRuns) * 100 : 0}%"></span>
      <span class="stage-seg final" style="width:${stats.finalRuns ? (stages[6] / stats.finalRuns) * 100 : 0}%"></span>
    </div>
  `;
}

function renderInsights(stats) {
  const best = [...stats.jobs].filter((job) => job.runs >= 5).sort((a, b) => b.winRate - a.winRate || b.runs - a.runs)[0];
  const most = stats.jobs[0];
  const recentPct = stats.recent10Total ? (stats.recent10 / stats.recent10Total) * 100 : 0;
  const diff = recentPct - stats.winRate;
  const trend = diff >= 0
    ? t('insight.above', { diff: diff.toFixed(1) })
    : t('insight.below', { diff: diff.toFixed(1) });
  const items = [];
  if (best) {
    items.push(t('insight.bestJob', { name: escapeHtml(jobNameOf(best)), runs: best.runs, wins: best.wins, rate: fmtPct(best.winRate) }));
  }
  items.push(t('insight.recent', { total: stats.recent10Total, wins: stats.recent10, losses: stats.recent10Total - stats.recent10, rate: fmtPct(recentPct, 0), trend }));
  if (most) {
    items.push(t('insight.mostPlayed', { name: escapeHtml(jobNameOf(most)), runs: most.runs, rate: fmtPct(most.winRate) }));
  }
  items.push(t('insight.final', { reached: stats.finalReached, total: stats.finalRuns, rate: fmtPct(stats.finalRate), avg: fmtAvg(stats.winMs) }));
  items.push(t('insight.streak', { current: stats.currentStreak, best: stats.bestStreak }));
  items.push(t('insight.total', { runs: stats.total, hours: (stats.totalMs / 3600000).toFixed(1), jobs: stats.jobs.length }));
  insights.innerHTML = items.map((item) => `<div class="insight"><span class="insight-mark">◈</span><span>${item}</span></div>`).join('');
}

function baseOptions() {
  return {
    responsive: true,
    maintainAspectRatio: false,
    animation: { duration: 500 },
    interaction: { mode: 'index', intersect: false },
    plugins: {
      legend: {
        labels: {
          color: '#9b948a',
          boxWidth: 10,
          boxHeight: 10,
          usePointStyle: true,
          pointStyle: 'circle',
          font: { size: 11 },
        },
      },
      tooltip: {
        backgroundColor: 'rgba(12, 12, 16, 0.94)',
        borderColor: 'rgba(201, 162, 39, 0.3)',
        borderWidth: 1,
        titleColor: '#ece7dd',
        bodyColor: '#c9c3b8',
        padding: 10,
        displayColors: false,
      },
    },
    scales: {
      x: {
        grid: { color: 'rgba(255,255,255,0.04)' },
        ticks: { color: '#7d766c', maxTicksLimit: 8, font: { size: 10 } },
      },
      y: {
        grid: { color: 'rgba(255,255,255,0.05)' },
        ticks: { color: '#7d766c', font: { size: 10 } },
      },
    },
  };
}

function runTitle(run) {
  return `${jobNameOf(run)} · ${runModeLabel(run)}`;
}

function renderCharts(stats) {
  if (typeof Chart === 'undefined') return;
  destroyCharts();

  Chart.defaults.color = '#9b948a';
  Chart.defaults.font.family = '"Segoe UI", "Malgun Gothic", system-ui, sans-serif';

  const labels = stats.runs.map((run) => fmtDay(run.timestamp));
  const trendOptions = baseOptions();
  trendOptions.scales.y.min = 0;
  trendOptions.scales.y.max = 100;
  trendOptions.scales.y.ticks.callback = (value) => `${value}%`;
  trendOptions.plugins.tooltip.callbacks = {
    title: (items) => `${items[0].label} · ${runTitle(stats.runs[items[0].dataIndex])}`,
    label: (item) => `${item.dataset.label}: ${item.formattedValue}%`,
  };

  const trendCanvas = $('#trendChart');
  if (trendCanvas) {
    chartInstances.trend = new Chart(trendCanvas, {
      type: 'line',
      data: {
        labels,
        datasets: [
          {
            label: t('chart.recent10'),
            data: stats.runs.map((run) => Number(run.rollingWinRate.toFixed(1))),
            borderColor: '#c9a227',
            backgroundColor: 'rgba(201, 162, 39, 0.14)',
            fill: true,
            borderWidth: 2,
            pointRadius: 0,
            pointHoverRadius: 4,
            tension: 0.28,
          },
          {
            label: t('chart.cumulative'),
            data: stats.runs.map((run) => Number(run.cumulativeWinRate.toFixed(1))),
            borderColor: '#7f9fd4',
            backgroundColor: 'transparent',
            borderWidth: 1.5,
            borderDash: [5, 4],
            pointRadius: 0,
            pointHoverRadius: 3,
            tension: 0.2,
          },
        ],
      },
      options: trendOptions,
    });
  }

  const eloRuns = filterRunsByCharacter(stats.runs, eloCharFilter);
  const eloOptions = baseOptions();
  eloOptions.scales.y.ticks.maxTicksLimit = 5;
  eloOptions.plugins.legend.display = false;
  eloOptions.plugins.tooltip.callbacks = {
    title: (items) => {
      const run = eloRuns[items[0].dataIndex];
      return `${fmtDate(run.timestamp)} · ${runTitle(run)}`;
    },
    label: (item) => `${t('chart.elo')}: ${item.formattedValue}`,
  };
  const eloCanvas = $('#eloChart');
  if (eloCanvas) {
    chartInstances.elo = new Chart(eloCanvas, {
      type: 'line',
      data: {
        labels: eloRuns.map((run) => fmtDay(run.timestamp)),
        datasets: [{
          label: t('chart.elo'),
          data: eloRuns.map((run) => run.elo),
          borderColor: '#b08bd4',
          backgroundColor: 'rgba(176, 139, 212, 0.08)',
          fill: true,
          borderWidth: 2,
          pointRadius: 0,
          pointHoverRadius: 4,
          tension: 0.3,
        }],
      },
      options: eloOptions,
    });
  }

  const ratingPoints = ratingSeriesForCharacter(
    stats.runs.filter((run) => run.mode === 'deep'),
    ratingCharFilter,
  );
  const ratingOptions = baseOptions();
  ratingOptions.plugins.legend.display = false;
  ratingOptions.plugins.tooltip.callbacks = {
    title: (items) => {
      const point = ratingPoints[items[0].dataIndex];
      if (!point) return '';
      if (point.source === 'slot') return `${fmtDate(point.timestamp)} · ${t('chart.ratingNow')}`;
      return `${fmtDate(point.timestamp)} · ${point.run ? jobNameOf(point.run) : ''}`;
    },
    label: (item) => t('chart.ratingPoint', {
      value: item.formattedValue,
      depth: ratingPoints[item.dataIndex]?.depth,
    }),
  };
  if ($('#ratingChart')) {
    chartInstances.rating = new Chart($('#ratingChart'), {
      type: 'line',
      data: {
        labels: ratingPoints.map((point) => (
          point.source === 'slot' ? t('chart.ratingNowShort') : fmtDay(point.timestamp)
        )),
        datasets: [{
          label: t('chart.storedRating'),
          data: ratingPoints.map((point) => point.rating),
          borderColor: '#c9a227',
          borderWidth: 2,
          pointRadius: ratingPoints.map((point) => (point.source === 'slot' ? 4 : 2)),
          pointBackgroundColor: ratingPoints.map((point) => (
            point.source === 'slot' ? '#ece7dd' : '#c9a227'
          )),
          tension: 0,
        }],
      },
      options: ratingOptions,
    });
  }

  const charOptions = baseOptions();
  charOptions.indexAxis = 'y';
  charOptions.interaction = { mode: 'nearest', axis: 'y', intersect: false };
  charOptions.scales.x.min = 0;
  charOptions.scales.x.max = 100;
  charOptions.scales.x.ticks.callback = (value) => `${value}%`;
  charOptions.plugins.legend.display = false;
  charOptions.plugins.tooltip.callbacks = {
    title: (items) => items[0]?.label || '',
    label: (item) => {
      const job = stats.jobs[item.dataIndex];
      return t('chart.jobTooltip', { name: jobNameOf(job), wl: fmtWl(job.wins, job.losses), runs: t('ui.runsUnit', { n: job.runs }), rate: fmtPct(job.winRate) });
    },
  };
  const charCanvas = $('#charChart');
  if (charCanvas) {
    chartInstances.char = new Chart(charCanvas, {
      type: 'bar',
      data: {
        labels: stats.jobs.map((job) => job.name),
        datasets: [{
          label: t('chart.winRate'),
          data: stats.jobs.map((job) => Number(job.winRate.toFixed(1))),
          backgroundColor: stats.jobs.map((_, index) => `${PALETTE[index % PALETTE.length]}bb`),
          borderColor: stats.jobs.map((_, index) => PALETTE[index % PALETTE.length]),
          borderWidth: 1,
          borderRadius: 5,
          barThickness: 18,
        }],
      },
      options: charOptions,
    });
  }

  const usageOptions = baseOptions();
  usageOptions.interaction = { mode: 'nearest', intersect: true };
  usageOptions.scales = {};
  usageOptions.plugins.legend.position = 'right';
  usageOptions.plugins.legend.labels.boxWidth = 8;
  usageOptions.plugins.legend.labels.font = { size: 10 };
  usageOptions.plugins.tooltip.callbacks = {
    label: (item) => {
      const job = stats.jobs[item.dataIndex];
      return t('chart.usageTooltip', { name: jobNameOf(job), runs: t('ui.runsUnit', { n: job.runs }), rate: fmtPct((job.runs / stats.total) * 100, 0) });
    },
  };
  const meetMap = new Map();
  for (const boss of stats.bosses || []) {
    const current = meetMap.get(boss.bossId) || { name: boss.name, runs: 0 };
    current.runs += boss.runs;
    meetMap.set(boss.bossId, current);
  }
  const meet = [...meetMap.values()].sort((a, b) => b.runs - a.runs || a.name.localeCompare(b.name));
  const meetOptions = baseOptions();
  meetOptions.interaction = { mode: 'nearest', intersect: true };
  meetOptions.scales = {};
  meetOptions.plugins.legend.position = 'right';
  meetOptions.plugins.legend.labels.boxWidth = 8;
  meetOptions.plugins.legend.labels.font = { size: 11 };
  meetOptions.plugins.tooltip.callbacks = {
    label: (item) => {
      const boss = meet[item.dataIndex];
      const rate = stats.total ? (boss.runs / stats.total) * 100 : 0;
      return t('chart.bossTooltip', { name: bossNameOf(boss), runs: t('ui.runsUnit', { n: boss.runs }), rate: fmtPct(rate) });
    },
  };
  const meetCanvas = $('#bossMeetChart');
  if (meetCanvas && meet.length) {
    chartInstances.bossMeet = new Chart(meetCanvas, {
      type: 'pie',
      data: {
        labels: meet.map((boss) => boss.name),
        datasets: [{
          data: meet.map((boss) => boss.runs),
          backgroundColor: meet.map((_, index) => `${PALETTE[index % PALETTE.length]}cc`),
          borderColor: '#101014',
          borderWidth: 2,
          hoverOffset: 6,
        }],
      },
      options: meetOptions,
    });
  }

  // 보스별 영원한 밤의 왕(Everdark) 조우율 — 강화 버전이 있는 보스만
  const EVERDARK_EXCLUDED = new Set([7, 9]); // 밤을 본뜬 자, 잔해의 왕
  const everdarkMap = new Map();
  for (const boss of stats.bosses || []) {
    if (EVERDARK_EXCLUDED.has(Number(boss.bossId))) continue;
    if (boss.variant !== 'normal' && boss.variant !== 'everdark') continue;
    const current = everdarkMap.get(boss.bossId) || {
      bossId: boss.bossId,
      total: 0,
      everdark: 0,
    };
    current.total += boss.runs;
    if (boss.variant === 'everdark') current.everdark += boss.runs;
    everdarkMap.set(boss.bossId, current);
  }
  const everdarkRows = [...everdarkMap.values()]
    .filter((row) => row.total > 0)
    .map((row) => ({
      ...row,
      rate: (row.everdark / row.total) * 100,
    }))
    .sort((a, b) => b.rate - a.rate || b.total - a.total || bossNameOf(a).localeCompare(bossNameOf(b), loc()));
  const everdarkOptions = baseOptions();
  everdarkOptions.indexAxis = 'y';
  everdarkOptions.interaction = { mode: 'nearest', axis: 'y', intersect: true };
  everdarkOptions.scales.x.min = 0;
  everdarkOptions.scales.x.max = 100;
  everdarkOptions.scales.x.ticks.callback = (value) => `${value}%`;
  everdarkOptions.plugins.legend.display = false;
  everdarkOptions.plugins.tooltip = {
    ...everdarkOptions.plugins.tooltip,
    position: 'nearest',
    callbacks: {
      title: (items) => {
        const item = items?.[0];
        if (!item) return '';
        const row = everdarkRows[item.dataIndex];
        return row ? bossNameOf(row) : '';
      },
      label: (item) => {
        const row = everdarkRows[item.dataIndex];
        if (!row) return '';
        return t('chart.everdarkTooltip', { everdark: row.everdark, total: row.total, rate: fmtPct(row.rate) });
      },
    },
  };
  const fiftyLinePlugin = {
    id: 'everdarkFiftyLine',
    afterDraw(chart) {
      const { ctx, chartArea, scales } = chart;
      if (!chartArea || !scales?.x) return;
      const x = scales.x.getPixelForValue(50);
      if (!Number.isFinite(x)) return;
      ctx.save();
      ctx.beginPath();
      ctx.setLineDash([5, 5]);
      ctx.strokeStyle = 'rgba(236, 231, 221, 0.42)';
      ctx.lineWidth = 1;
      ctx.moveTo(x, chartArea.top);
      ctx.lineTo(x, chartArea.bottom);
      ctx.stroke();
      ctx.fillStyle = 'rgba(155, 148, 138, 0.85)';
      ctx.font = '500 10px "Segoe UI", "Malgun Gothic", sans-serif';
      ctx.textAlign = 'center';
      ctx.fillText('50%', x, chartArea.top + 12);
      ctx.restore();
    },
  };
  const everdarkCanvas = $('#bossEverdarkChart');
  if (everdarkCanvas && everdarkRows.length) {
    chartInstances.bossEverdark = new Chart(everdarkCanvas, {
      type: 'bar',
      data: {
        labels: everdarkRows.map((row) => bossNameOf(row)),
        datasets: [{
          data: everdarkRows.map((row) => Number(row.rate.toFixed(1))),
          backgroundColor: everdarkRows.map((row) => (
            row.rate >= 50 ? 'rgba(196, 92, 64, 0.78)' : 'rgba(168, 120, 72, 0.72)'
          )),
          borderColor: 'rgba(220, 140, 100, 0.35)',
          borderWidth: 1,
          borderRadius: 4,
        }],
      },
      options: everdarkOptions,
      plugins: [fiftyLinePlugin],
    });
  }

  const usageCanvas = $('#usageChart');
  if (usageCanvas) {
    chartInstances.usage = new Chart(usageCanvas, {
      type: 'doughnut',
      data: {
        labels: stats.jobs.map((job) => job.name),
        datasets: [{
          data: stats.jobs.map((job) => job.runs),
          backgroundColor: stats.jobs.map((_, index) => `${PALETTE[index % PALETTE.length]}cc`),
          borderColor: '#101014',
          borderWidth: 2,
          hoverOffset: 6,
        }],
      },
      options: usageOptions,
    });
  }
}

if (bossFilterEl) {
  bossFilterEl.addEventListener('click', (event) => {
    const button = event.target.closest('button[data-variant]');
    if (!button) return;
    bossFilterValue = button.dataset.variant;
    for (const item of bossFilterEl.querySelectorAll('button')) {
      item.classList.toggle('active', item === button);
    }
    if (currentStats) {
      renderBosses(currentStats);
      renderBossMatrix(currentStats);
    }
  });
}

dropzone.addEventListener('click', () => fileInput.click());
dropzone.addEventListener('keydown', (event) => {
  if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault();
    fileInput.click();
  }
});

fileInput.addEventListener('change', () => handleFile(fileInput.files?.[0]));

for (const eventName of ['dragenter', 'dragover']) {
  dropzone.addEventListener(eventName, (event) => {
    event.preventDefault();
    dropzone.classList.add('drag');
  });
}

for (const eventName of ['dragleave', 'drop']) {
  dropzone.addEventListener(eventName, (event) => {
    event.preventDefault();
    dropzone.classList.remove('drag');
  });
}

dropzone.addEventListener('drop', (event) => {
  const file = event.dataTransfer?.files?.[0];
  handleFile(file);
});

demoBtn.addEventListener('click', () => {
  const demoRuns = buildDemo();
  loadData([], demoRuns, demoRuns, 'demo', true);
});

whereBtn.addEventListener('click', () => {
  whereBox.hidden = !whereBox.hidden;
});

window.addEventListener('nightrecord:locale', () => {
  applyDom(document);
  if (currentStats) {
    renderModeTabs();
    renderModeCompare();
    setMode(currentMode);
  }
});

resetBtn.addEventListener('click', resetView);
errorReset.addEventListener('click', resetView);

function refreshCharacterCharts() {
  if (!currentStats || currentStats.empty) return;
  const ratingSelect = $('#ratingCharSelect');
  const eloSelect = $('#eloCharSelect');
  if (ratingSelect && !ratingSelect.hidden) ratingCharFilter = ratingSelect.value;
  if (eloSelect && !eloSelect.hidden) eloCharFilter = eloSelect.value;

  const ratingPool = currentStats.runs.filter((run) => (
    run.mode === 'deep' && Number.isFinite(run.ratingCandidate) && run.ratingCandidate > 0
  ));
  const selectedCharRating = characterDeepRating(ratingCharFilter, scopeRuns());
  const ratings = filterRunsByCharacter(ratingPool, ratingCharFilter);
  const latestRating = ratings.at(-1);
  const ratingCurrent = $('#ratingCurrent');
  if (ratingCurrent) {
    if (selectedCharRating?.rating) {
      ratingCurrent.textContent = t('stat.current', {
        n: Math.round(selectedCharRating.rating).toLocaleString(),
      });
    } else if (latestRating) {
      ratingCurrent.textContent = t('stat.latest', {
        n: latestRating.ratingCandidate.toLocaleString(),
      });
    } else {
      ratingCurrent.textContent = '';
    }
  }
  const eloFiltered = filterRunsByCharacter(currentStats.runs, eloCharFilter);
  const latestElo = eloFiltered.at(-1);
  if (eloCurrent) {
    eloCurrent.textContent = latestElo?.elo
      ? t('stat.current', { n: latestElo.elo })
      : '';
  }
  renderCharts(currentStats);
}

$('#ratingCharSelect')?.addEventListener('change', refreshCharacterCharts);
$('#eloCharSelect')?.addEventListener('change', refreshCharacterCharts);

modeTabs.addEventListener('click', (event) => {
  const button = event.target.closest('.mode-tab');
  if (!button || !modeStats[button.dataset.mode]) return;
  setMode(button.dataset.mode);
});


if (pageTabs) {
  pageTabs.addEventListener('click', (event) => {
    const button = event.target.closest('button[data-page]');
    if (button) setPage(button.dataset.page);
  });
}

toggleRuns.addEventListener('click', () => {
  if (!currentStats || currentStats.empty) return;
  showAllRuns = !showAllRuns;
  renderTable(currentStats, showAllRuns);
});

function accountMessage(text) {
  const node = $('#accountMessage');
  if (!node) return;
  const value = String(text || '').trim();
  node.textContent = value;
  node.hidden = !value;
}

function renderAccount() {
  const loggedIn = Boolean(accountUser);
  const loginLink = $('#loginLink');
  const signupLink = $('#signupLink');
  const myPage = $('#myPageLink');
  const signOut = $('#signOut');
  if (loginLink) loginLink.hidden = loggedIn;
  if (signupLink) signupLink.hidden = loggedIn;
  if (myPage) myPage.hidden = !loggedIn;
  if (signOut) signOut.hidden = !loggedIn;
}

async function syncCurrentRecords(options = {}) {
  if ((syncBusy && !options.nested) || !accountUser || isDemo) return;
  const ownLock = !options.nested;
  if (ownLock) syncBusy = true;
  if ($('#signOut')) $('#signOut').disabled = true;
  demoBtn.disabled = true;
  $('#resetBtn').disabled = true;
  fileInput.disabled = true;
  const userId = accountUser.id;
  const snapshot = allRuns;
  try {
    if (snapshot.length) await account.saveRecords(snapshot);
    const saved = await account.fetchRecords();
    if (accountUser?.id !== userId) return;
    accountRuns = saved;
    if (saved.length && !cumulativeRuns.length) {
      if (!currentStats) loadData([], [], [], t('msg.accountStore'), false, null, { keepCumulative: true, profile: profileMeta });
      applyScope('account');
      rebuildStats();
      renderModeTabs();
      renderModeCompare();
      setMode('all');
    }
  } catch (error) {
    showNotice(t('msg.syncFail', { error: error.message }));
  } finally {
    if (ownLock) syncBusy = false;
    if ($('#signOut')) $('#signOut').disabled = false;
    demoBtn.disabled = false;
    $('#resetBtn').disabled = false;
    fileInput.disabled = false;
  }
}

$('#signOut')?.addEventListener('click', async () => {
  if (syncBusy) return;
  try { await account.signOut(); }
  catch (error) { accountMessage(error.message); }
  finally {
    accountUser = null;
    accountRuns = [];
    resetView();
    renderAccount();
    accountMessage(t('msg.loggedOut'));
  }
});

renderAccount();

function searchIdentity(row) {
  const same = row.name && row.tag && row.name.toLowerCase() === row.tag.toLowerCase();
  const title = !row.name || same ? (row.tag || '') : row.name;
  const name = title ? `<strong class="hit-name">${escapeHtml(title)}</strong>` : '';
  const tag = row.name && !same && row.tag ? `<span class="hit-tag">${escapeHtml(row.tag)}</span>` : '';
  return `<span class="hit-id">${name}${tag}</span>`;
}

function renderSearchResults(rows) {
  const box = $('#searchResults');
  if (!rows.length) {
    box.hidden = false;
    box.innerHTML = `<p class="muted">${escapeHtml(t('search.noMatch'))}</p>`;
    return;
  }
  box.hidden = false;
  box.innerHTML = rows.map((row) => {
    const rate = row.matches ? `${fmtWl(row.wins, row.matches - row.wins)} · ${((row.wins / row.matches) * 100).toFixed(1)}%` : t('stat.none');
    const updated = row.updatedAt ? fmtDate(row.updatedAt) : '—';
    return `<button type="button" class="search-hit" data-tag="${escapeHtml(row.tag)}" data-name="${escapeHtml(row.name || '')}">${searchIdentity(row)}<span>${rate}</span><span>${escapeHtml(t('search.updated', { date: updated }))}</span></button>`;
  }).join('');
}

async function openPublicProfile(tag, options = {}) {
  if (!account.configured) {
    accountMessage(t('msg.publicNeedServer'));
    return;
  }
  if (syncBusy) return;
  syncBusy = true;
  showStatus(t('msg.loadingPublic'));
  try {
    const detail = await account.fetchProfile(tag);
    if (!detail?.tag) throw new Error(t('search.notFound'));
    const runs = Array.isArray(detail.runs) ? detail.runs : [];
    const names = Array.isArray(detail.names) ? detail.names : [];
    const roster = names.map((name, slot) => ({ slot, name }));
    const recent = runs.filter((run) => run.source === 'saved');
    const profile = {
      tag: detail.tag,
      names,
      updatedAt: detail.updatedAt || null,
      searched: true,
      focusName: options.name || '',
      peakRating: detail.peakRating || 0,
      peakDepth: detail.peakDepth || 0,
    };
    const ownOrigin = activeProfileKey ? account.tagFromKey(activeProfileKey) : '';
    const detailOrigin = detail.saveId || '';
    const viewingSelf = Boolean(
      (ownOrigin && (detailOrigin === ownOrigin || detail.tag === ownOrigin))
      || (profileMeta?.tag && (detail.tag === profileMeta.tag || detailOrigin === profileMeta.tag)),
    );
    let ownRelics = relicSlots;
    if (viewingSelf && !ownRelics.length) {
      const localRelics = account.loadLocalRelics(activeProfileKey);
      ownRelics = localRelics.slots || [];
      if (!ownRelics.length && accountUser) {
        try {
          const remoteList = await account.fetchRelics(activeProfileKey);
          const remote = Array.isArray(remoteList) ? remoteList[0] : null;
          if (remote?.slots?.length) {
            ownRelics = remote.slots;
            account.saveLocalRelics(activeProfileKey, remote.slots, remote.updatedAt || new Date().toISOString());
          }
        } catch {
          // 공개 기록만 표시
        }
      }
    }
    const remoteDeep = Array.isArray(detail.deepRatings) ? detail.deepRatings : [];
    const remoteAvatar = detail.avatar || '';
    loadData(roster, runs, recent.length ? recent : runs, `${options.name || names[0] || 'player'}#${detail.tag}`, false, null, {
      cumulative: runs,
      profile,
      scope: 'cumulative',
      // 타인 공개 프로필에는 유물이 없고, 본인이면 보관 인벤을 유지/복원
      relicSlots: viewingSelf ? ownRelics : [],
      deepRatings: remoteDeep,
      avatar: remoteAvatar,
    });
    accountMessage('');
    const url = new URL(location.href);
    url.searchParams.set('player', detail.tag);
    url.searchParams.delete('demo');
    if (options.replace) history.replaceState({ tag: detail.tag }, '', url);
    else history.pushState({ tag: detail.tag }, '', url);
  } catch (error) {
    accountMessage(error.message);
    setVisible(landing);
  } finally {
    syncBusy = false;
  }
}

function hideSuggest() {
  const box = $('#searchSuggest');
  if (!box) return;
  box.hidden = true;
  box.innerHTML = '';
}

function renderSuggest(rows) {
  const box = $('#searchSuggest');
  if (!rows.length) {
    box.hidden = false;
    box.innerHTML = `<p class="muted">${escapeHtml(t('search.noMatch'))}</p>`;
    return;
  }
  box.hidden = false;
  box.innerHTML = rows.map((row) => {
    const rate = row.matches ? fmtWl(row.wins, row.matches - row.wins) : t('stat.none');
    return `<button type="button" class="search-hit" role="option" data-tag="${escapeHtml(row.tag)}" data-name="${escapeHtml(row.name || '')}">${searchIdentity(row)}<span>${rate}</span></button>`;
  }).join('');
}

let suggestTimer = 0;
let suggestSeq = 0;
$('#playerQuery').addEventListener('input', () => {
  const query = $('#playerQuery').value.trim();
  clearTimeout(suggestTimer);
  if (query.length < 3) {
    suggestSeq += 1;
    hideSuggest();
    return;
  }
  suggestTimer = setTimeout(async () => {
    const seq = ++suggestSeq;
    try {
      const rows = await account.suggestProfiles(query);
      if (seq !== suggestSeq) return;
      renderSuggest(Array.isArray(rows) ? rows : []);
    } catch (error) {
      if (seq !== suggestSeq) return;
      $('#searchSuggest').hidden = false;
      $('#searchSuggest').innerHTML = `<p class="muted">${escapeHtml(error.message)}</p>`;
    }
  }, 250);
});

$('#searchSuggest').addEventListener('click', (event) => {
  const button = event.target.closest('.search-hit');
  if (!button) return;
  hideSuggest();
  $('#playerQuery').value = '';
  openPublicProfile(button.dataset.tag, { name: button.dataset.name || '' });
});

document.addEventListener('click', (event) => {
  if (!event.target.closest('#playerSearch')) hideSuggest();
});

$('#playerSearch').addEventListener('submit', async (event) => {
  event.preventDefault();
  hideSuggest();
  const query = $('#playerQuery').value.trim();
  if (!query) return;
  if (!account.configured) {
    accountMessage('공개 검색은 서버 연결 후 사용할 수 있습니다.');
    return;
  }
  $('#searchResults').hidden = false;
  $('#searchResults').innerHTML = `<p class="muted">${escapeHtml(t('search.loading'))}</p>`;
  try {
    const rows = await account.searchProfiles(query);
    renderSearchResults(Array.isArray(rows) ? rows : []);
  } catch (error) {
    $('#searchResults').innerHTML = `<p class="muted">${escapeHtml(error.message)}</p>`;
  }
});

$('#searchResults').addEventListener('click', (event) => {
  const button = event.target.closest('.search-hit');
  if (!button) return;
  $('#searchResults').hidden = true;
  openPublicProfile(button.dataset.tag, { name: button.dataset.name || '' });
});

window.addEventListener('popstate', () => {
  const tag = new URLSearchParams(location.search).get('player');
  if (tag) openPublicProfile(tag, { replace: true });
});

const startupParams = new URLSearchParams(location.search);
async function boot() {
  if (account.configured) {
    try {
      accountUser = await account.restoreSession();
      renderAccount();
      maybeShowAppPromoNotice({ loggedIn: Boolean(accountUser), showNotice, t });
    } catch (error) {
      accountMessage(t('msg.loginRestoreFail', { error: error.message }));
    }
  }
  if (startupParams.has('demo')) {
    const demoRuns = buildDemo();
    loadData([], demoRuns, demoRuns, 'demo', true);
    return;
  }

  // 로그인 시 계정 누적 기록을 먼저 복원. ?player=가 있으면 공개/본인 전적을 항상 연다.
  const restored = await restorePersistedSession();
  if (restored && accountUser && activeProfileKey) {
    const publicTag = await syncDisplayTagFromServer(activeProfileKey);
    if (publicTag && currentStats) render(currentStats);
  }

  let playerTag = startupParams.get('player');
  // 마이페이지 «전적 보기» 등으로 들어왔는데 복원이 실패해도, 로그인 계정의 공개 태그로 전적을 연다.
  if (!playerTag && accountUser && !restored) {
    try {
      const me = await account.fetchMe();
      playerTag = me?.profiles?.[0]?.tag || me?.profiles?.[0]?.saveId || '';
      if (playerTag) {
        const url = new URL(location.href);
        url.searchParams.set('player', playerTag);
        history.replaceState({ tag: playerTag }, '', url);
      }
    } catch {
      // 공개 전적 조회는 아래에서도 시도
    }
  }
  if (playerTag) {
    await openPublicProfile(playerTag, { replace: true });
    return;
  }
  if (accountUser) await syncCurrentRecords({ nested: restored });
}
boot();
