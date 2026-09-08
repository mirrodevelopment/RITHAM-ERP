/**
 * Ritham ERP — Modal Component
 * Usage:
 *   Modal.confirm('Delete this order?', onConfirm)
 *   Modal.alert('Order saved!')
 *   Modal.open({ title, body, footer, size })
 */

'use strict';

const Modal = (() => {

  let _activeModal = null;

  // ── Core open ─────────────────────────────────────────────────────────────

  /**
   * Open a modal with custom content.
   * @param {Object} options
   * @param {string} options.title
   * @param {string|HTMLElement} options.body - HTML string or DOM element
   * @param {string|HTMLElement} [options.footer] - Footer HTML
   * @param {'sm'|''|'lg'|'xl'} [options.size]
   * @param {Function} [options.onClose]
   * @returns {HTMLElement} backdrop element
   */
  function open({ title, body, footer = '', size = '', onClose }) {
    close(); // Close any existing modal first

    const backdrop = document.createElement('div');
    backdrop.className = 'modal-backdrop';

    const sizeClass = size ? `modal-${size}` : '';
    backdrop.innerHTML = `
      <div class="modal ${sizeClass}" role="dialog" aria-modal="true">
        <div class="modal-header">
          <h3 class="modal-title">${title}</h3>
          <button class="btn btn-ghost btn-icon modal-close" aria-label="Close">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
              <line x1="18" y1="6" x2="6" y2="18"></line><line x1="6" y1="6" x2="18" y2="18"></line>
            </svg>
          </button>
        </div>
        <div class="modal-body"></div>
        ${footer ? '<div class="modal-footer"></div>' : ''}
      </div>
    `;

    const modal     = backdrop.querySelector('.modal');
    const bodyEl    = backdrop.querySelector('.modal-body');
    const footerEl  = backdrop.querySelector('.modal-footer');
    const closeBtn  = backdrop.querySelector('.modal-close');

    if (typeof body === 'string') bodyEl.innerHTML = body;
    else bodyEl.appendChild(body);

    if (footer && footerEl) {
      if (typeof footer === 'string') footerEl.innerHTML = footer;
      else footerEl.appendChild(footer);
    }

    closeBtn.addEventListener('click', () => {
      close();
      onClose?.();
    });

    backdrop.addEventListener('click', (e) => {
      if (e.target === backdrop) {
        close();
        onClose?.();
      }
    });

    document.addEventListener('keydown', _handleEsc);
    document.body.appendChild(backdrop);
    document.body.style.overflow = 'hidden';

    _activeModal = backdrop;
    return backdrop;
  }

  // ── Confirm dialog ─────────────────────────────────────────────────────────

  /**
   * Show a confirmation dialog.
   * @param {string} message
   * @param {Function} onConfirm
   * @param {Object} [opts] - { title, confirmText, cancelText, danger }
   */
  function confirm(message, onConfirm, opts = {}) {
    const {
      title       = 'Confirm',
      confirmText = 'Confirm',
      cancelText  = 'Cancel',
      danger      = false,
    } = opts;

    const footer = `
      <button class="btn btn-secondary modal-cancel">${cancelText}</button>
      <button class="btn ${danger ? 'btn-danger' : 'btn-primary'} modal-confirm">${confirmText}</button>
    `;

    const backdrop = open({
      title,
      body:   `<p style="color: var(--text-secondary); font-size: var(--text-sm); line-height: var(--leading-relaxed);">${message}</p>`,
      footer,
      size:   'sm',
    });

    backdrop.querySelector('.modal-cancel').addEventListener('click', close);
    backdrop.querySelector('.modal-confirm').addEventListener('click', () => {
      close();
      onConfirm?.();
    });
  }

  // ── Alert dialog ───────────────────────────────────────────────────────────

  function alert(message, title = 'Notice') {
    const footer = `<button class="btn btn-primary modal-ok">OK</button>`;
    const backdrop = open({ title, body: `<p style="color:var(--text-secondary);font-size:var(--text-sm)">${message}</p>`, footer, size: 'sm' });
    backdrop.querySelector('.modal-ok').addEventListener('click', close);
  }

  // ── Close ──────────────────────────────────────────────────────────────────

  function close() {
    if (_activeModal) {
      _activeModal.remove();
      _activeModal = null;
      document.body.style.overflow = '';
      document.removeEventListener('keydown', _handleEsc);
    }
  }

  function _handleEsc(e) {
    if (e.key === 'Escape') close();
  }

  /**
   * Get a reference to the currently open modal body element.
   * Useful for adding dynamic content after open().
   */
  function getBody() {
    return _activeModal?.querySelector('.modal-body') ?? null;
  }

  return { open, confirm, alert, close, getBody };

})();
