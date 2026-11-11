import {
  APK_URL,
  APP_PROMO_NOTICE_KEY,
  ONE_STORE_URL,
  PLAY_STORE_URL,
} from './config.js';

export function registerServiceWorker() {
  if (!('serviceWorker' in navigator)) return;
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js').catch(() => {});
  });
}

export function isStandalone() {
  return window.matchMedia('(display-mode: standalone)').matches
    || window.navigator.standalone === true;
}

export function isAndroid() {
  return /Android/i.test(window.navigator.userAgent || '');
}

export function isIos() {
  const ua = window.navigator.userAgent || '';
  if (/iPhone|iPad|iPod/i.test(ua)) return true;
  return window.navigator.platform === 'MacIntel' && (window.navigator.maxTouchPoints || 0) > 1;
}

function primaryStoreUrl() {
  const one = String(ONE_STORE_URL || '').trim();
  if (one) return one;
  const play = String(PLAY_STORE_URL || '').trim();
  if (play) return play;
  return '';
}

function show(el) {
  if (el) el.hidden = false;
}

function hide(el) {
  if (el) el.hidden = true;
}

function wireStoreLink(el, url) {
  if (!el) return;
  if (!url) {
    hide(el);
    return;
  }
  el.href = url;
  el.target = '_blank';
  el.rel = 'noopener noreferrer';
  show(el);
}

function defaultPlatform() {
  if (isIos()) return 'ios';
  return 'android';
}

function setPromoTab(root, platform) {
  const tabs = root.querySelectorAll('[data-promo-tab]');
  const panels = root.querySelectorAll('[data-promo-panel]');
  tabs.forEach((tab) => {
    const active = tab.dataset.promoTab === platform;
    tab.classList.toggle('is-active', active);
    tab.setAttribute('aria-selected', active ? 'true' : 'false');
  });
  panels.forEach((panel) => {
    panel.hidden = panel.dataset.promoPanel !== platform;
  });
  const guide = root.querySelector('[data-promo="ios-guide"]');
  if (platform !== 'ios') hide(guide);
}

/** 배너 + Android/iOS 토글 프로모 카드 */
export function initInstallPrompt() {
  const root = document.querySelector('#appPromo');
  if (!root) return;

  if (isStandalone()) {
    hide(root);
    return;
  }

  show(root);

  const oneUrl = String(ONE_STORE_URL || '').trim();
  const playUrl = String(PLAY_STORE_URL || '').trim();
  const storeBtn = root.querySelector('[data-promo="store"]');
  const playBtn = root.querySelector('[data-promo="play"]');
  const apkBtn = root.querySelector('[data-promo="apk"]');
  const iosAdd = root.querySelector('[data-promo="ios-add"]');
  const iosGuide = root.querySelector('[data-promo="ios-guide"]');

  wireStoreLink(storeBtn, oneUrl || playUrl);
  wireStoreLink(playBtn, playUrl && oneUrl ? playUrl : '');
  if (apkBtn) {
    apkBtn.href = APK_URL;
    // 원스토어가 있으면 APK는 보조 링크로만
    apkBtn.hidden = false;
  }

  root.querySelectorAll('[data-promo-tab]').forEach((tab) => {
    tab.addEventListener('click', () => setPromoTab(root, tab.dataset.promoTab));
  });

  iosAdd?.addEventListener('click', () => {
    if (!iosGuide) return;
    iosGuide.hidden = false;
    iosGuide.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
  });

  setPromoTab(root, defaultPlatform());
}

/**
 * 로그인 유저에게 앱/원스토어 안내를 1회 표시.
 */
export function maybeShowAppPromoNotice({ loggedIn, showNotice, t }) {
  if (!loggedIn || isStandalone()) return;
  try {
    if (localStorage.getItem(APP_PROMO_NOTICE_KEY)) return;
  } catch {
    return;
  }

  const storeUrl = primaryStoreUrl();
  const ios = isIos();
  const android = isAndroid();
  const ctaHref = ios ? '' : (storeUrl || (android ? APK_URL : storeUrl));
  const ctaLabel = ios
    ? ''
    : (storeUrl ? t('promo.ctaStore') : (android ? t('promo.ctaApk') : t('promo.ctaStore')));

  const dismiss = () => {
    try { localStorage.setItem(APP_PROMO_NOTICE_KEY, '1'); } catch { /* ignore */ }
  };

  const title = ios
    ? t('promo.noticeTitleIos')
    : (android ? t('promo.noticeTitle') : t('promo.noticeTitleDesktop'));
  const body = ios
    ? t('promo.noticeBodyIos')
    : (android ? t('promo.noticeBody') : t('promo.noticeBodyDesktop'));

  showNotice(body, {
    title,
    ctaHref: ctaHref || undefined,
    ctaLabel: ctaLabel || undefined,
  });

  const dialog = document.querySelector('#noticeDialog');
  const cta = document.querySelector('#noticeDialogCta');
  if (cta && ctaHref) cta.addEventListener('click', dismiss, { once: true });
  if (dialog) dialog.addEventListener('close', dismiss, { once: true });
  else dismiss();
}

export function initPwa(options = {}) {
  registerServiceWorker();
  initInstallPrompt();
  if (options.loggedIn && typeof options.showNotice === 'function' && typeof options.t === 'function') {
    maybeShowAppPromoNotice(options);
  }
}
