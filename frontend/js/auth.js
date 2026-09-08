/**
 * Ritham ERP — Authentication Service (auth.js)
 *
 * Handles login, logout, session checks, and route guards.
 */

'use strict';

const Auth = (() => {

  // ── Login ─────────────────────────────────────────────────────────────────

  /**
   * Authenticate with username/password.
   * Stores session data and returns the logged-in user.
   *
   * @param {string} username
   * @param {string} password
   * @returns {Promise<Object>} user profile
   */
  async function login(username, password) {
    const data = await Api.postPublic(API.LOGIN, { username, password });
    Storage.saveSession(data);
    return Storage.getUser();
  }

  // ── Logout ─────────────────────────────────────────────────────────────────

  /**
   * Revoke refresh tokens on server, clear local session,
   * redirect to login page.
   */
  async function logout() {
    try {
      await Api.post(API.LOGOUT, {});
    } catch (_) {
      // Continue logout even if server call fails
    } finally {
      Storage.clearSession();
      redirectToLogin();
    }
  }

  // ── Session checks ─────────────────────────────────────────────────────────

  /**
   * Returns true if a valid token exists in storage.
   * (Client-side only — does not call the server.)
   */
  function isAuthenticated() {
    return Storage.hasSession();
  }

  /**
   * Get the currently logged-in user from storage.
   * @returns {Object|null}
   */
  function getUser() {
    return Storage.getUser();
  }

  /**
   * Get the current user's role.
   * @returns {string|null}
   */
  function getRole() {
    const user = Storage.getUser();
    return user ? user.role : null;
  }

  /**
   * Returns true if the current user has the given role.
   * @param {string} role - e.g. ROLES.ADMIN
   */
  function hasRole(role) {
    return getRole() === role;
  }

  /**
   * Returns true if the current user has any of the given roles.
   * @param {string[]} roles
   */
  function hasAnyRole(...roles) {
    return roles.includes(getRole());
  }

  // ── Route Guards ───────────────────────────────────────────────────────────

  /**
   * Guard for protected pages.
   * Call at the top of every protected page's JS file.
   * Redirects to login if not authenticated.
   *
   * @param {string[]} [allowedRoles] - Optional list of allowed roles.
   *   If omitted, any authenticated user can access.
   */
  function guard(allowedRoles = []) {
    if (!isAuthenticated()) {
      redirectToLogin();
      return false;
    }

    if (allowedRoles.length > 0 && !hasAnyRole(...allowedRoles)) {
      redirectToUnauthorized();
      return false;
    }

    return true;
  }

  // ── Redirect helpers ───────────────────────────────────────────────────────

  function redirectToLogin() {
    window.location.href = ROUTES.LOGIN;
  }

  function redirectToUnauthorized() {
    redirectToDashboard();
  }

  /**
   * After login, redirect to the appropriate dashboard based on role.
   */
  function redirectToDashboard() {
    const role   = getRole();
    const target = ROLE_DEFAULT_DASHBOARD[role] || ROUTES.ORDER_DESK;
    window.location.href = target;
  }

  /**
   * If user is already logged in and visits login page, redirect to dashboard.
   */
  function redirectIfAuthenticated() {
    if (isAuthenticated()) {
      redirectToDashboard();
    }
  }

  // ── Token info ─────────────────────────────────────────────────────────────

  /**
   * Decode the JWT payload without verification (display only).
   */
  function getTokenPayload() {
    const token = Storage.getToken();
    if (!token) return null;

    try {
      const payload = token.split('.')[1];
      return JSON.parse(atob(payload.replace(/-/g, '+').replace(/_/g, '/')));
    } catch {
      return null;
    }
  }

  /**
   * Check if the access token appears to be expired (client-side only).
   */
  function isTokenExpired() {
    const payload = getTokenPayload();
    if (!payload || !payload.exp) return true;
    return Date.now() >= payload.exp * 1000;
  }

  return {
    login, logout,
    isAuthenticated, getUser, getRole,
    hasRole, hasAnyRole,
    guard,
    redirectToLogin, redirectToDashboard, redirectIfAuthenticated,
    getTokenPayload, isTokenExpired,
  };

})();
