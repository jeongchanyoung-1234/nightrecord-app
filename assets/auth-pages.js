import * as account from './account.js?v=15';
import { initI18n, t } from './i18n.js?v=20';
import { initPwa } from './pwa.js?v=6';

initI18n();
initPwa();

const page = document.body.dataset.auth;
const form = document.querySelector('#accountForm');
const message = document.querySelector('#accountMessage');
const already = document.querySelector('#alreadyIn');

function say(text) {
  message.textContent = text;
}

function homeUrl() {
  const next = new URLSearchParams(location.search).get('next');
  if (next && next.startsWith('/') && !next.startsWith('//') && !next.startsWith('/\\')) return next;
  return './';
}

if (page !== 'reset') {
  account.restoreSession().then((user) => {
    if (!user) return;
    form.hidden = true;
    already.hidden = false;
    say(t('auth.alreadyIn', { email: user.email }));
  }).catch((error) => say(error.message));
}

form?.addEventListener('submit', async (event) => {
  event.preventDefault();
  const login = document.querySelector('#loginEmail').value.trim();
  const password = document.querySelector('#loginPassword')?.value || '';
  const confirm = document.querySelector('#loginPasswordConfirm');
  const submit = document.querySelector('#authSubmit');
  if (confirm && password !== confirm.value) {
    say(t('auth.passwordMismatch'));
    return;
  }
  submit.disabled = true;
  try {
    if (page === 'signup') {
      await account.signUp(login, password);
      location.href = homeUrl();
      return;
    }
    if (page === 'login') {
      await account.signIn(login, password);
      location.href = homeUrl();
      return;
    }
    say(t('auth.resetUnavailable'));
  } catch (error) {
    say(error.message);
  } finally {
    submit.disabled = false;
  }
});
