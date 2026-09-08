/**
 * Ritham ERP — Login Page Controller (login.js)
 *
 * Handles:
 *  - Redirect if already authenticated
 *  - Form validation
 *  - API login call
 *  - Role-based redirect after login
 *  - Password visibility toggle
 */

'use strict';

document.addEventListener('DOMContentLoaded', () => {

  // Always show login page (allow user to choose login / role)

  // ── Elements ───────────────────────────────────────────────────────────

  const form         = document.getElementById('loginForm');
  const usernameEl   = document.getElementById('username');
  const passwordEl   = document.getElementById('password');
  const usernameErr  = document.getElementById('usernameError');
  const passwordErr  = document.getElementById('passwordError');
  const loginError   = document.getElementById('loginError');
  const loginBtn     = document.getElementById('loginBtn');
  const loginBtnText = document.getElementById('loginBtnText');
  const loginBtnArrow= document.getElementById('loginBtnArrow');
  const loginSpinner = document.getElementById('loginSpinner');
  const togglePwd    = document.getElementById('togglePassword');
  const eyeIcon      = document.getElementById('eyeIcon');

  // ── Password visibility toggle ─────────────────────────────────────────

  const EYE_OPEN = `
    <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path>
    <circle cx="12" cy="12" r="3"></circle>
  `;

  const EYE_CLOSED = `
    <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"></path>
    <line x1="1" y1="1" x2="23" y2="23"></line>
  `;

  togglePwd?.addEventListener('click', () => {
    const isPassword = passwordEl.type === 'password';
    passwordEl.type = isPassword ? 'text' : 'password';
    eyeIcon.innerHTML = isPassword ? EYE_CLOSED : EYE_OPEN;
  });

  // ── Validation helpers ─────────────────────────────────────────────────

  function clearErrors() {
    usernameErr.textContent = '';
    passwordErr.textContent  = '';
    loginError.style.display = 'none';
    usernameEl.classList.remove('error');
    passwordEl.classList.remove('error');
  }

  function showFieldError(input, errorEl, message) {
    input.classList.add('error');
    errorEl.textContent = message;
  }

  function showServerError(message) {
    loginError.textContent   = message;
    loginError.style.display = 'block';
  }

  function validateInputs() {
    let valid = true;

    if (!usernameEl.value.trim()) {
      showFieldError(usernameEl, usernameErr, 'Username is required');
      valid = false;
    }

    if (!passwordEl.value) {
      showFieldError(passwordEl, passwordErr, 'Password is required');
      valid = false;
    } else if (passwordEl.value.length < 6) {
      showFieldError(passwordEl, passwordErr, 'Password must be at least 6 characters');
      valid = false;
    }

    return valid;
  }

  // ── Loading state ──────────────────────────────────────────────────────

  function setLoading(loading) {
    loginBtn.disabled = loading;
    loginSpinner.classList.toggle('hidden', !loading);
    loginBtnText.textContent = loading ? 'Signing in...' : 'Sign In';
    if (loginBtnArrow) {
      loginBtnArrow.style.display = loading ? 'none' : 'block';
    }
  }

  // ── Clear error on input ───────────────────────────────────────────────

  usernameEl.addEventListener('input', () => {
    usernameEl.classList.remove('error');
    usernameErr.textContent = '';
  });

  passwordEl.addEventListener('input', () => {
    passwordEl.classList.remove('error');
    passwordErr.textContent = '';
    loginError.style.display = 'none';
  });

  // ── Form submit ────────────────────────────────────────────────────────

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    clearErrors();

    if (!validateInputs()) return;

    const username = usernameEl.value.trim();
    const password = passwordEl.value;

    setLoading(true);

    try {
      const user = await Auth.login(username, password);
      Auth.redirectToDashboard();
    } catch (err) {
      setLoading(false);

      if (err?.status === 401) {
        showServerError('Incorrect username or password. Please try again.');
        passwordEl.value = '';
        passwordEl.focus();
      } else if (err?.status === 0) {
        showServerError('Cannot connect to the server. Please ensure the application is running.');
      } else {
        showServerError(err?.message || 'Login failed. Please try again.');
      }
    }
  });

  // ── Quick Demo Login Handlers ──────────────────────────────────────────

  document.querySelectorAll('.quick-login-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      const u = btn.getAttribute('data-user');
      const p = btn.getAttribute('data-pass');
      usernameEl.value = u;
      passwordEl.value = p;
      clearErrors();
      form.requestSubmit();
    });
  });

  // Focus username field on load
  usernameEl?.focus();

});
