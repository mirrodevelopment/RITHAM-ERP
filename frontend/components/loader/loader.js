/**
 * Ritham ERP — Loader Component
 * Usage:
 *   Loader.show()       → fullscreen overlay
 *   Loader.hide()
 *   Loader.skeleton(n)  → returns n skeleton rows HTML string
 */

'use strict';

const Loader = (() => {

  let _overlay = null;

  // ── Fullscreen overlay ────────────────────────────────────────────────────

  function show(message = '') {
    if (_overlay) return;

    _overlay = document.createElement('div');
    _overlay.id = 'global-loader';
    _overlay.style.cssText = `
      position: fixed; inset: 0;
      background: rgba(10,10,10,0.85);
      display: flex; flex-direction: column;
      align-items: center; justify-content: center;
      z-index: 9999; gap: 16px;
    `;
    _overlay.innerHTML = `
      <div style="
        width: 36px; height: 36px;
        border: 2px solid rgba(255,255,255,0.1);
        border-top-color: #f0f0f0;
        border-radius: 50%;
        animation: spin 0.7s linear infinite;
      "></div>
      ${message ? `<p style="color: var(--text-secondary); font-size: var(--text-sm);">${message}</p>` : ''}
    `;
    document.body.appendChild(_overlay);
  }

  function hide() {
    if (_overlay) {
      _overlay.remove();
      _overlay = null;
    }
  }

  // ── Section loaders ───────────────────────────────────────────────────────

  /**
   * Show a centered spinner inside a container element.
   * @param {HTMLElement} container
   */
  function spin(container) {
    container.innerHTML = `
      <div style="display:flex;align-items:center;justify-content:center;padding:48px;">
        <div class="spinner" style="
          width:28px;height:28px;
          border:2px solid rgba(255,255,255,0.1);
          border-top-color:#888;
          border-radius:50%;
          animation:spin 0.7s linear infinite;
        "></div>
      </div>
    `;
  }

  // ── Skeleton rows ─────────────────────────────────────────────────────────

  /**
   * Generate skeleton placeholder rows for a table.
   * @param {number} rows
   * @param {number} cols
   */
  function skeletonTableRows(rows = 5, cols = 5) {
    return Array.from({ length: rows }, () => `
      <tr>
        ${Array.from({ length: cols }, (_, i) => `
          <td>
            <div class="skeleton skeleton-text" style="width: ${60 + (i * 7 % 30)}%"></div>
          </td>
        `).join('')}
      </tr>
    `).join('');
  }

  /**
   * Generate card skeleton placeholders.
   * @param {number} count
   */
  function skeletonCards(count = 4) {
    return Array.from({ length: count }, () => `
      <div class="card" style="padding:24px;display:flex;flex-direction:column;gap:12px;">
        <div class="skeleton" style="height:12px;width:40%;border-radius:4px;"></div>
        <div class="skeleton" style="height:32px;width:60%;border-radius:6px;"></div>
        <div class="skeleton" style="height:10px;width:80%;border-radius:4px;"></div>
      </div>
    `).join('');
  }

  return { show, hide, spin, skeletonTableRows, skeletonCards };

})();
