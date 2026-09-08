/**
 * Ritham ERP — Client Router (router.js)
 * Simple hash-based route guard and navigation helpers.
 */

'use strict';

const Router = (() => {

  /**
   * Call at the top of every protected page.
   * Redirects to login if unauthenticated, or if role is not allowed.
   *
   * @param {string[]} allowedRoles - Leave empty to allow any authenticated user.
   * @returns {boolean} false if redirect occurred
   */
  function protect(allowedRoles = []) {
    return Auth.guard(allowedRoles);
  }

  /**
   * Navigate to a page by ROUTES key or direct path.
   * @param {string} path
   */
  function navigate(path) {
    window.location.href = path;
  }

  /**
   * Navigate back in history.
   */
  function goBack() {
    window.history.back();
  }

  /**
   * Build a URL with query parameters.
   * @param {string} base - Base URL
   * @param {Object} params - Key-value pairs
   */
  function buildUrl(base, params = {}) {
    const url = new URL(base, window.location.origin + window.location.pathname);
    Object.entries(params).forEach(([k, v]) => {
      if (v !== null && v !== undefined && v !== '') {
        url.searchParams.set(k, v);
      }
    });
    return url.toString();
  }

  /**
   * Get the current page identifier from the URL path.
   * e.g. ".../pages/customer/customer.html" → "customer"
   */
  function getCurrentPage() {
    const parts = window.location.pathname.split('/').filter(Boolean);
    const filename = parts[parts.length - 1] || '';
    return filename.replace('.html', '');
  }

  return { protect, navigate, goBack, buildUrl, getCurrentPage };

})();
