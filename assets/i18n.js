import { LOCALES, LANGS } from './locales.js?v=24';

const STORAGE_KEY = 'nightrecord.locale';
const SUPPORTED = new Set(LANGS.map((entry) => entry.id));

let current = 'ko';
let catalog = LOCALES.ko;

function detectLocale() {
  try {
    const saved = localStorage.getItem(STORAGE_KEY);
    if (SUPPORTED.has(saved)) return saved;
  } catch { /* ignore */ }
  const nav = String(navigator.language || navigator.userLanguage || 'ko').toLowerCase();
  if (nav.startsWith('ja')) return 'ja';
  if (nav.startsWith('zh')) return 'zh';
  if (nav.startsWith('en')) return 'en';
  if (nav.startsWith('ko')) return 'ko';
  return 'ko';
}

function interpolate(template, vars = {}) {
  return String(template ?? '').replace(/\{(\w+)\}/g, (_, key) => (
    vars[key] == null ? '' : String(vars[key])
  ));
}

export function getLocale() {
  return current;
}

export function t(key, vars) {
  const raw = catalog[key] ?? LOCALES.ko[key] ?? key;
  return vars ? interpolate(raw, vars) : raw;
}

export function applyDom(root = document) {
  for (const node of root.querySelectorAll('[data-i18n]')) {
    const key = node.getAttribute('data-i18n');
    if (!key) continue;
    node.textContent = t(key);
  }
  for (const node of root.querySelectorAll('[data-i18n-html]')) {
    const key = node.getAttribute('data-i18n-html');
    if (!key) continue;
    node.innerHTML = t(key);
  }
  for (const node of root.querySelectorAll('[data-i18n-placeholder]')) {
    const key = node.getAttribute('data-i18n-placeholder');
    if (key) node.setAttribute('placeholder', t(key));
  }
  for (const node of root.querySelectorAll('[data-i18n-aria]')) {
    const key = node.getAttribute('data-i18n-aria');
    if (key) node.setAttribute('aria-label', t(key));
  }
  for (const node of root.querySelectorAll('[data-i18n-title]')) {
    const key = node.getAttribute('data-i18n-title');
    if (key) node.setAttribute('title', t(key));
  }
  const titleKey = document.documentElement.getAttribute('data-i18n-title-key');
  if (titleKey) document.title = t(titleKey);
  for (const button of root.querySelectorAll('.lang-switch [data-lang]')) {
    button.classList.toggle('is-active', button.dataset.lang === current);
  }
}

export function setLocale(next, { persist = true, silent = false } = {}) {
  const locale = SUPPORTED.has(next) ? next : 'ko';
  current = locale;
  catalog = LOCALES[locale] || LOCALES.ko;
  document.documentElement.lang = locale === 'zh' ? 'zh-Hans' : locale;
  if (persist) {
    try { localStorage.setItem(STORAGE_KEY, locale); } catch { /* ignore */ }
  }
  applyDom(document);
  if (!silent) {
    window.dispatchEvent(new CustomEvent('nightrecord:locale', { detail: { locale } }));
  }
}

export function mountLanguageSwitchers(root = document) {
  for (const host of root.querySelectorAll('[data-lang-switch]')) {
    host.setAttribute('role', 'group');
    host.setAttribute('aria-label', t('footer.lang'));
    host.innerHTML = LANGS.map((entry) => (
      `<button type="button" class="lang-btn ${entry.id === current ? 'is-active' : ''}" data-lang="${entry.id}">${entry.label}</button>`
    )).join('');
  }
  if (!root.__nightrecordLangBound) {
    root.addEventListener('click', (event) => {
      const button = event.target.closest('[data-lang]');
      if (!button || !button.closest('[data-lang-switch]')) return;
      setLocale(button.dataset.lang);
    });
    root.__nightrecordLangBound = true;
  }
}

export function initI18n() {
  current = detectLocale();
  catalog = LOCALES[current] || LOCALES.ko;
  document.documentElement.lang = current === 'zh' ? 'zh-Hans' : current;
  mountLanguageSwitchers(document);
  applyDom(document);
}

export { LANGS };
