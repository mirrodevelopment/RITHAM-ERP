/**
 * Ritham ERP — Utility Functions (utils.js)
 */

'use strict';

const Utils = (() => {

  // ── Date & Time ───────────────────────────────────────────────────────────

  /**
   * Format ISO date string to DD MMM YYYY
   * e.g. "2026-08-06T10:30:00" → "06 Aug 2026"
   */
  function formatDate(isoString) {
    if (!isoString) return '—';
    const date = new Date(isoString);
    if (isNaN(date)) return '—';
    return date.toLocaleDateString('en-IN', {
      day:   '2-digit',
      month: 'short',
      year:  'numeric',
    });
  }

  /**
   * Format ISO date string to DD MMM YYYY, HH:MM
   */
  function formatDateTime(isoString) {
    if (!isoString) return '—';
    const date = new Date(isoString);
    if (isNaN(date)) return '—';
    return date.toLocaleDateString('en-IN', {
      day:    '2-digit',
      month:  'short',
      year:   'numeric',
      hour:   '2-digit',
      minute: '2-digit',
      hour12: true,
    });
  }

  /**
   * Get relative time string: "2 hours ago", "just now"
   */
  function timeAgo(isoString) {
    if (!isoString) return '—';
    const seconds = Math.floor((Date.now() - new Date(isoString)) / 1000);

    if (seconds < 60)    return 'just now';
    if (seconds < 3600)  return `${Math.floor(seconds / 60)} min ago`;
    if (seconds < 86400) return `${Math.floor(seconds / 3600)} hr ago`;
    if (seconds < 604800) return `${Math.floor(seconds / 86400)} days ago`;
    return formatDate(isoString);
  }

  // ── Number / Currency ─────────────────────────────────────────────────────

  /**
   * Format number as Indian Rupee: ₹1,23,456.00
   */
  function formatCurrency(amount) {
    if (amount == null || isNaN(amount)) return '—';
    return new Intl.NumberFormat('en-IN', {
      style:    'currency',
      currency: 'INR',
      minimumFractionDigits: 0,
      maximumFractionDigits: 0,
    }).format(amount);
  }

  /**
   * Format plain number with commas: 1,23,456
   */
  function formatNumber(n) {
    if (n == null) return '—';
    return new Intl.NumberFormat('en-IN').format(n);
  }

  // ── Code generation ───────────────────────────────────────────────────────

  /**
   * Generate order number: ORD-20260806-0001
   */
  function generateOrderNumber() {
    const date = new Date();
    const dateStr = date.toISOString().slice(0, 10).replace(/-/g, '');
    const random = String(Math.floor(Math.random() * 9999)).padStart(4, '0');
    return `ORD-${dateStr}-${random}`;
  }

  // ── String utilities ──────────────────────────────────────────────────────

  function capitalize(str) {
    if (!str) return '';
    return str.charAt(0).toUpperCase() + str.slice(1).toLowerCase();
  }

  function initials(fullName) {
    if (!fullName) return '?';
    return fullName
      .split(' ')
      .filter(Boolean)
      .slice(0, 2)
      .map(n => n[0].toUpperCase())
      .join('');
  }

  function truncate(str, length = 40) {
    if (!str) return '';
    return str.length > length ? str.slice(0, length) + '…' : str;
  }

  function slugify(str) {
    return str.toLowerCase().replace(/\s+/g, '-').replace(/[^a-z0-9-]/g, '');
  }

  function escapeHtml(str) {
    if (!str) return '';
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }

  // ── Role label helper ─────────────────────────────────────────────────────

  function getRoleLabel(role) {
    return ROLE_LABELS[role] || role;
  }

  // ── DOM helpers ───────────────────────────────────────────────────────────

  function $(selector, context = document) {
    return context.querySelector(selector);
  }

  function $$(selector, context = document) {
    return [...context.querySelectorAll(selector)];
  }

  function el(tag, attrs = {}, ...children) {
    const element = document.createElement(tag);
    Object.entries(attrs).forEach(([key, val]) => {
      if (key === 'class') element.className = val;
      else if (key === 'html') element.innerHTML = val;
      else if (key === 'text') element.textContent = val;
      else element.setAttribute(key, val);
    });
    children.forEach(child => {
      if (typeof child === 'string') element.appendChild(document.createTextNode(child));
      else if (child instanceof Node) element.appendChild(child);
    });
    return element;
  }

  function showEl(...selectors) {
    selectors.forEach(s => {
      const el = typeof s === 'string' ? $(s) : s;
      if (el) el.classList.remove('hidden');
    });
  }

  function hideEl(...selectors) {
    selectors.forEach(s => {
      const el = typeof s === 'string' ? $(s) : s;
      if (el) el.classList.add('hidden');
    });
  }

  function setLoading(button, loading, loadingText = 'Loading...') {
    if (!button) return;
    button.disabled = loading;
    const textEl   = button.querySelector('[data-text]') || button;
    const spinner  = button.querySelector('.spinner');

    if (loading) {
      button.setAttribute('data-original', textEl.textContent);
      if (!spinner) {
        const sp = document.createElement('span');
        sp.className = 'spinner';
        button.appendChild(sp);
      } else {
        spinner.classList.remove('hidden');
      }
      if (textEl !== button) textEl.textContent = loadingText;
    } else {
      const original = button.getAttribute('data-original');
      if (original && textEl !== button) textEl.textContent = original;
      if (spinner) spinner.classList.add('hidden');
    }
  }

  // ── Debounce ──────────────────────────────────────────────────────────────

  function debounce(fn, delay = 300) {
    let timer;
    return function (...args) {
      clearTimeout(timer);
      timer = setTimeout(() => fn.apply(this, args), delay);
    };
  }

  function throttle(fn, limit = 300) {
    let lastCall = 0;
    return function (...args) {
      const now = Date.now();
      if (now - lastCall >= limit) {
        lastCall = now;
        return fn.apply(this, args);
      }
    };
  }

  // ── Query param helpers ───────────────────────────────────────────────────

  function getQueryParam(key) {
    return new URLSearchParams(window.location.search).get(key);
  }

  function setQueryParam(key, value) {
    const url = new URL(window.location.href);
    url.searchParams.set(key, value);
    window.history.replaceState({}, '', url);
  }

  function isReadOnlyUser() {
    const user = Storage.getUser();
    return user?.role === 'ROLE_OPERATIONS_MANAGER';
  }

  function canAdvanceProductionStage() {
    const user = Storage.getUser();
    const role = (user?.role || '').toUpperCase();
    return role.includes('PRODUCTION') && !role.includes('ADMIN') && !role.includes('MANAGER');
  }

  const BLOUSE_MEASUREMENT_KEY_ALIASES = {
    'HS': 'H.S.', 'H.S': 'H.S.',
    'HL': 'H.L.', 'H.L': 'H.L.',
    'HLO': 'H.LO', 'H.LO.': 'H.LO',
    'DP-1-1': 'DP-1', 'DP1_1': 'DP-1',
    'DP-1-2': 'DP-2', 'DP1_2': 'DP-2',
    'B1': 'B-1', 'B2': 'B-2', 'B3': 'B-3',
    'F-HOOK': 'F.HOOK', 'F_HOOK': 'F.HOOK', 'F.HOOK.': 'F.HOOK',
    'B-HOOK': 'B.HOOK', 'B_HOOK': 'B.HOOK', 'B.HOOK.': 'B.HOOK',
    'AV': 'AV.',
  };

  const CHUDI_MEASUREMENT_KEY_ALIASES = {
    'FN': 'F.N.', 'F.N': 'F.N.',
    'BN': 'B.N.', 'B.N': 'B.N.',
    'HB': 'H.B.', 'H.B': 'H.B.',
    'L_1': 'L', 'L-1': 'L',
    'SS': 'S.S.', 'S.S': 'S.S.',
    'SL-1': 'S.L.', 'SL_1': 'S.L.',
    'SL-2': 'S.LO', 'SL_2': 'S.LO',
    'TS': 'T.S.', 'T.S': 'T.S.',
    'PL': 'P.L.', 'P.L': 'P.L.',
    'S': 'S.',
    'L-2': 'L.', 'L_2': 'L.',
    'SCUT': 'S.CUT', 'S_CUT': 'S.CUT',
    'BD': 'B.D.', 'B.D': 'B.D.',
  };

  /**
   * Normalizes backend OCR/migration measurement keys to the ERP UI form's `data-key`
   * format for BLOUSE or CHUDI without losing existing entries.
   */
  function normalizeMeasurementMap(garmentType, map) {
    if (!map || typeof map !== 'object') return {};
    const aliases = String(garmentType || '').toUpperCase().includes('CHUDI')
      ? CHUDI_MEASUREMENT_KEY_ALIASES
      : BLOUSE_MEASUREMENT_KEY_ALIASES;
    const result = {};
    for (const [rawKey, val] of Object.entries(map)) {
      if (rawKey == null) continue;
      const trimmed = String(rawKey).trim();
      const canonical = aliases[trimmed] || aliases[trimmed.toUpperCase()] || trimmed;
      if (!(canonical in result) || result[canonical] === '' || result[canonical] == null) {
        result[canonical] = val;
      }
    }
    return result;
  }

  return {
    formatDate, formatDateTime, timeAgo,
    formatCurrency, formatNumber,
    generateOrderNumber,
    capitalize, initials, truncate, slugify, escapeHtml,
    normalizeMeasurementMap,
    getRoleLabel, isReadOnlyUser, canAdvanceProductionStage,
    $, $$, el, showEl, hideEl, setLoading,
    debounce, throttle,
    getQueryParam, setQueryParam,
  };

})();

// ── Prevent mouse scroll from changing number / amount input values ──────────
if (typeof document !== 'undefined') {
  document.addEventListener('wheel', (e) => {
    if (
      (e.target && e.target.tagName === 'INPUT' && e.target.type === 'number') ||
      (document.activeElement && document.activeElement.tagName === 'INPUT' && document.activeElement.type === 'number')
    ) {
      if (e.target && e.target.tagName === 'INPUT' && e.target.type === 'number') {
        e.preventDefault();
      }
      if (document.activeElement && document.activeElement.tagName === 'INPUT' && document.activeElement.type === 'number' && e.target !== document.activeElement) {
        document.activeElement.blur();
      }
    }
  }, { passive: false });

  document.addEventListener('focusin', (e) => {
    if (e.target && e.target.tagName === 'INPUT' && e.target.type === 'number') {
      e.target.addEventListener('wheel', (we) => we.preventDefault(), { passive: false });
    }
  });
}
