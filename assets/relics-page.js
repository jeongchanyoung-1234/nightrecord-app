import * as account from './account.js?v=15';
import { bindRelicControls, renderRelicRecommender, relicState } from './relic-ui.js?v=6';
import { initI18n, t, applyDom } from './i18n.js?v=17';

initI18n();

window.addEventListener('nightrecord:locale', () => {
  applyDom(document);
  void refresh();
});

const $ = (selector) => document.querySelector(selector);

let accountUser = null;
let relicSlots = [];
let profileKey = '';

function renderAccount() {
  const loggedIn = Boolean(accountUser);
  const loginLink = $('#loginLink');
  const myPage = $('#myPageLink');
  const signOut = $('#signOut');
  if (loginLink) loginLink.hidden = loggedIn;
  if (myPage) myPage.hidden = !loggedIn;
  if (signOut) signOut.hidden = !loggedIn;
}

async function loadOwnerRelics() {
  let key = account.loadLastProfileKey();
  let relics = key ? account.loadLocalRelics(key) : { slots: [], updatedAt: null };

  if (account.configured && accountUser) {
    try {
      const remoteList = await account.fetchRelics(key || null);
      const remote = Array.isArray(remoteList)
        ? (key
          ? remoteList.find((row) => row.profileKey === key) || remoteList[0]
          : remoteList[0])
        : null;
      if (remote?.profileKey && !key) {
        key = remote.profileKey;
        account.rememberLastProfile(key);
        relics = account.loadLocalRelics(key);
      }
      if (remote?.slots?.length) {
        const remoteAt = Date.parse(remote.updatedAt || '') || 0;
        const localAt = Date.parse(relics.updatedAt || '') || 0;
        if (remoteAt >= localAt || !relics.slots.length) {
          relics = { slots: remote.slots, updatedAt: remote.updatedAt || null };
          if (key) account.saveLocalRelics(key, remote.slots, remote.updatedAt || new Date().toISOString());
        }
      }
    } catch {
      // 로컬 보관분으로 계속
    }
  }

  profileKey = key || '';
  relicSlots = relics.slots || [];
  if (relicSlots.length) {
    const first = relicSlots.find((slot) => slot.relics?.length) || relicSlots[0];
    relicState.slotIndex = first?.index ?? 0;
  }
  return relicSlots;
}

async function refresh() {
  const note = $('#relicsOwnerNote');
  const allowed = relicSlots.length > 0;
  if (note) {
    note.textContent = allowed ? t('relics.note') : t('relics.needOwner');
  }
  await renderRelicRecommender({
    relicSlots,
    allowed,
    deniedMessage: t('relics.denied'),
  });
}

bindRelicControls(() => { void refresh(); });

$('#signOut')?.addEventListener('click', async () => {
  try { await account.signOut(); }
  catch { /* ignore */ }
  accountUser = null;
  relicSlots = [];
  profileKey = '';
  renderAccount();
  await refresh();
});

async function boot() {
  if (account.configured) {
    try {
      accountUser = await account.restoreSession();
    } catch {
      accountUser = null;
    }
  }
  renderAccount();
  await loadOwnerRelics();
  await refresh();
}

boot();
