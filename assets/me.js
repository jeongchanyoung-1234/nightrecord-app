import * as account from './account.js?v=15';
import { PROFILE_AVATARS, avatarById } from './profile-avatars.js';
import { initI18n, t, getLocale, applyDom } from './i18n.js';
import { jobLabel } from './parser.js';

initI18n();

window.addEventListener('nightrecord:locale', () => {
  applyDom(document);
  renderRecords();
  renderTags();
  renderAvatars();
});

const recordsPanel = document.querySelector('#recordsPanel');
const settingsPanel = document.querySelector('#settingsPanel');
const recordList = document.querySelector('#recordList');
const tagList = document.querySelector('#tagList');
const tagMessage = document.querySelector('#tagMessage');
const avatarList = document.querySelector('#avatarList');
const avatarMessage = document.querySelector('#avatarMessage');
const avatarDialog = document.querySelector('#avatarDialog');
const avatarGrid = document.querySelector('#avatarGrid');
let profiles = [];
let avatarEditIndex = -1;

function escapeHtml(value) {
  return String(value).replace(/[&<>"']/g, (char) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[char]));
}

function showTab(name) {
  const settings = name === 'settings';
  recordsPanel.hidden = settings;
  settingsPanel.hidden = !settings;
  document.querySelector('#tabRecords').className = settings ? 'btn btn-ghost' : 'btn btn-solid';
  document.querySelector('#tabSettings').className = settings ? 'btn btn-solid' : 'btn btn-ghost';
}

function profileLabel(profile) {
  return profile.displayTag || profile.saveId || t('me.mySave');
}

function resolveProfileKey(profile) {
  if (profile?.profileKey) return profile.profileKey;
  const saveId = profile?.saveId;
  if (!saveId) return '';
  try {
    const prefix = 'nightreign.profile:';
    for (let i = 0; i < localStorage.length; i++) {
      const key = localStorage.key(i);
      if (!key?.startsWith(prefix)) continue;
      const profileKey = key.slice(prefix.length);
      try {
        if (account.tagFromKey(profileKey) === saveId) return profileKey;
      } catch { /* ignore invalid keys */ }
    }
  } catch { /* localStorage unavailable */ }
  return '';
}

function currentAvatarId(profile) {
  if (profile?.avatar) return profile.avatar;
  const profileKey = resolveProfileKey(profile);
  if (!profileKey) return '';
  return account.loadLocalHistory(profileKey).avatar || '';
}

async function persistAvatar(profile, nextId) {
  if (!profile?.id) return false;
  const saved = await account.updateAvatar(profile.id, nextId || '');
  profile.avatar = saved?.avatar || nextId || '';
  const profileKey = resolveProfileKey(profile) || saved?.profileKey;
  if (profileKey) {
    const local = account.loadLocalHistory(profileKey);
    account.saveLocalHistory(profileKey, {
      tag: local.tag || profile.displayTag || profile.saveId || '',
      names: local.names?.length ? local.names : (profile.names || []),
      updatedAt: new Date().toISOString(),
      runs: local.runs || [],
      deepRatings: local.deepRatings || [],
      avatar: profile.avatar,
    });
  }
  return true;
}

function renderRecords() {
  if (!profiles.length) {
    recordList.className = 'me-card';
    recordList.innerHTML = `<p class="muted">${escapeHtml(t('me.emptyRecords'))}</p>`;
    return;
  }
  recordList.className = 'record-list';
  recordList.innerHTML = profiles.map((profile) => {
    const names = (profile.names || []).map((name) => `<li class="name-chip">${escapeHtml(name)}</li>`).join('');
    const losses = (profile.matches || 0) - (profile.wins || 0);
    const rate = profile.matches ? `${profile.wins}${t('unit.win')} ${losses}${t('unit.loss')} · ${((profile.wins / profile.matches) * 100).toFixed(1)}%` : t('stat.none');
    return `<article class="me-card record-card">
      <div class="record-head">
        <p class="hit-tag">${profile.displayTag ? escapeHtml(profile.displayTag) : escapeHtml(t('me.noTag'))}</p>
        <a class="btn btn-ghost" href="./?player=${encodeURIComponent(profile.tag || profile.saveId || '')}">${escapeHtml(t('me.viewRecord'))}</a>
      </div>
      <p class="record-rate">${t('me.matches', { n: profile.matches || 0 })} · ${rate}</p>
      <ul class="name-list">${names || `<li class="muted">${escapeHtml(t('me.noNames'))}</li>`}</ul>
    </article>`;
  }).join('');
}

function renderTags() {
  if (!profiles.length) {
    tagList.innerHTML = `<p class="muted">${escapeHtml(t('me.noTagHint'))}</p>`;
    return;
  }
  tagList.innerHTML = profiles.map((profile, index) => {
    return `<div class="me-profile">
      <strong>${escapeHtml(profile.saveId || t('me.mySave'))}</strong>
      <div class="name-add">
        <input data-input="${index}" maxlength="16" value="${escapeHtml(profile.displayTag || '')}" placeholder="${escapeHtml(t('me.tagPlaceholder'))}" />
        <button class="btn btn-solid" type="button" data-save="${index}">${escapeHtml(t('me.save'))}</button>
      </div>
    </div>`;
  }).join('');
}

function renderAvatars() {
  if (!avatarList) return;
  if (!profiles.length) {
    avatarList.innerHTML = `<p class="muted">${escapeHtml(t('me.noAvatarHint'))}</p>`;
    return;
  }
  avatarList.innerHTML = profiles.map((profile, index) => {
    const avatar = avatarById(currentAvatarId(profile));
    const preview = avatar
      ? `<img class="me-avatar-preview" src="${escapeHtml(avatar.file)}" alt="${escapeHtml(avatar.name)}" />`
      : `<span class="me-avatar-empty">${escapeHtml(t('me.avatarNone'))}</span>`;
    return `<div class="me-profile me-avatar-row">
      <div class="me-avatar-meta">
        ${preview}
        <div>
          <strong>${escapeHtml(profileLabel(profile))}</strong>
          <p class="muted">${avatar ? escapeHtml(jobLabel(avatar.jobId, getLocale())) : escapeHtml(t('me.avatarHidden'))}</p>
        </div>
      </div>
      <button class="btn btn-solid" type="button" data-avatar-edit="${index}">${escapeHtml(t('me.avatarPick'))}</button>
    </div>`;
  }).join('');
}

function openAvatarPicker(index) {
  const profile = profiles[index];
  if (!profile?.id || !avatarDialog || !avatarGrid) {
    if (avatarMessage) {
      avatarMessage.textContent = t('me.avatarNeedSave');
    }
    return;
  }
  avatarEditIndex = index;
  const selected = currentAvatarId(profile);
  avatarGrid.innerHTML = PROFILE_AVATARS.map((entry) => `
    <button type="button" class="avatar-option ${selected === entry.id ? 'is-selected' : ''}" data-avatar="${escapeHtml(entry.id)}">
      <img src="${escapeHtml(entry.file)}" alt="${escapeHtml(entry.name)}" loading="lazy" />
      <span>${escapeHtml(jobLabel(entry.jobId, getLocale()))}</span>
    </button>
  `).join('');
  if (typeof avatarDialog.showModal === 'function') avatarDialog.showModal();
}

async function applyAvatarSelection(nextId) {
  const profile = profiles[avatarEditIndex];
  if (!profile?.id) return;
  if (avatarMessage) avatarMessage.textContent = t('me.saving');
  try {
    await persistAvatar(profile, nextId);
    if (avatarMessage) {
      const avatar = avatarById(nextId);
      avatarMessage.textContent = avatar
        ? t('me.avatarSaved', { label: profileLabel(profile), name: jobLabel(avatar.jobId, getLocale()) })
        : t('me.avatarCleared', { label: profileLabel(profile) });
    }
    renderAvatars();
  } catch (error) {
    if (avatarMessage) avatarMessage.textContent = error.message || t('me.avatarSaveFail');
  }
}

async function saveTag(index) {
  const input = tagList.querySelector(`[data-input="${index}"]`);
  const tag = input?.value.trim() || '';
  if (tag && !/^[0-9A-Za-z가-힣_-]{2,16}$/.test(tag)) {
    tagMessage.textContent = t('me.tagInvalid');
    return;
  }
  tagMessage.textContent = t('me.saving');
  try {
    const saved = await account.updateDisplayTag(profiles[index].id, tag);
    profiles[index] = { ...profiles[index], ...saved };
    renderRecords();
    renderTags();
    renderAvatars();
    tagMessage.textContent = tag ? t('me.tagSaved') : t('me.tagCleared');
  } catch (error) {
    tagMessage.textContent = error.message;
  }
}

document.querySelector('#tabRecords').addEventListener('click', () => showTab('records'));
document.querySelector('#tabSettings').addEventListener('click', () => showTab('settings'));

tagList.addEventListener('click', (event) => {
  const save = event.target.closest('[data-save]');
  if (save) saveTag(Number(save.dataset.save));
});

tagList.addEventListener('keydown', (event) => {
  if (event.key !== 'Enter') return;
  const input = event.target.closest('[data-input]');
  if (!input) return;
  event.preventDefault();
  saveTag(Number(input.dataset.input));
});

avatarList?.addEventListener('click', (event) => {
  const edit = event.target.closest('[data-avatar-edit]');
  if (!edit) return;
  openAvatarPicker(Number(edit.dataset.avatarEdit));
});

avatarGrid?.addEventListener('click', (event) => {
  const option = event.target.closest('[data-avatar]');
  if (!option) return;
  applyAvatarSelection(option.dataset.avatar || '');
  avatarDialog?.close('picked');
});

avatarDialog?.addEventListener('close', () => {
  if (avatarDialog.returnValue === 'clear' && avatarEditIndex >= 0) {
    applyAvatarSelection('');
  }
  avatarEditIndex = -1;
});

document.querySelector('#passwordForm').addEventListener('submit', async (event) => {
  event.preventDefault();
  const message = document.querySelector('#passwordMessage');
  const next = document.querySelector('#newPassword').value;
  if (next !== document.querySelector('#confirmPassword').value) {
    message.textContent = t('me.passwordMismatch');
    return;
  }
  message.textContent = t('me.changing');
  try {
    await account.changePassword(document.querySelector('#currentPassword').value, next);
    event.target.reset();
    message.textContent = t('me.passwordChanged');
  } catch (error) {
    message.textContent = error.message;
  }
});

document.querySelector('#signOut').addEventListener('click', async () => {
  try { await account.signOut(); } catch { /* 세션이 이미 없을 수 있습니다. */ }
  location.href = './';
});

let user = null;
try {
  user = await account.restoreSession();
} catch (error) {
  recordList.innerHTML = `<p class="muted">${escapeHtml(error.message)}</p>`;
}
if (!user) location.href = 'login.html?next=/me.html';
else {
  document.querySelector('#accountEmail').textContent = user.email;
  try {
    const me = await account.fetchMe();
    profiles = me.profiles || [];
    renderRecords();
    renderTags();
    renderAvatars();
  } catch (error) {
    recordList.innerHTML = `<p class="muted">${escapeHtml(error.message)}</p>`;
  }
}
