/**
 * Ritham ERP — Local Storage Service
 * Type-safe wrappers for storing and retrieving tokens and user data.
 */

'use strict';

const Storage = (() => {

  // ── Access Token ────────────────────────────────────────────────────────

  function getToken() {
    return localStorage.getItem(APP.TOKEN_KEY);
  }

  function setToken(token) {
    localStorage.setItem(APP.TOKEN_KEY, token);
  }

  function removeToken() {
    localStorage.removeItem(APP.TOKEN_KEY);
  }

  // ── Refresh Token ────────────────────────────────────────────────────────

  function getRefreshToken() {
    return localStorage.getItem(APP.REFRESH_KEY);
  }

  function setRefreshToken(token) {
    localStorage.setItem(APP.REFRESH_KEY, token);
  }

  function removeRefreshToken() {
    localStorage.removeItem(APP.REFRESH_KEY);
  }

  // ── User Profile ─────────────────────────────────────────────────────────

  function getUser() {
    try {
      const raw = localStorage.getItem(APP.USER_KEY);
      return raw ? JSON.parse(raw) : null;
    } catch {
      return null;
    }
  }

  function setUser(user) {
    localStorage.setItem(APP.USER_KEY, JSON.stringify(user));
  }

  function removeUser() {
    localStorage.removeItem(APP.USER_KEY);
  }

  // ── Session Management ───────────────────────────────────────────────────

  /**
   * Save the full login response: tokens + user info.
   * @param {Object} loginData - Response from POST /auth/login
   */
  const ACTIVE_BRANCH_KEY      = 'ritham_active_branch_id';
  const ACTIVE_BRANCH_NAME_KEY = 'ritham_active_branch_name';

  function saveSession(loginData) {
    setToken(loginData.accessToken);
    setRefreshToken(loginData.refreshToken);
    const isGlobalAdmin = loginData.isGlobalAdmin || (loginData.role === ROLES.ADMIN && !loginData.branchId);
    setUser({
      employeeId:    loginData.employeeId,
      employeeCode:  loginData.employeeCode,
      fullName:      loginData.fullName,
      username:      loginData.username,
      role:          loginData.role,
      branchId:      loginData.branchId,
      branchName:    loginData.branchName,
      branchCode:    loginData.branchCode,
      isGlobalAdmin: isGlobalAdmin,
    });

    if (isGlobalAdmin) {
      if (!localStorage.getItem(ACTIVE_BRANCH_KEY)) {
        setActiveBranchId(1, 'Ritham Designs — Main Branch');
      }
    } else {
      setActiveBranchId(loginData.branchId, loginData.branchName);
    }
  }

  function getActiveBranchId() {
    const user = getUser();
    if (user && !user.isGlobalAdmin && user.branchId) {
      return user.branchId;
    }
    const val = localStorage.getItem(ACTIVE_BRANCH_KEY);
    return val !== null && val !== undefined ? val : (user && user.branchId ? user.branchId : 1);
  }

  function getActiveBranchName() {
    const user = getUser();
    if (user && !user.isGlobalAdmin && user.branchName) {
      return user.branchName;
    }
    return localStorage.getItem(ACTIVE_BRANCH_NAME_KEY) || (user && user.branchName ? user.branchName : 'Main Branch');
  }

  function setActiveBranchId(branchId, branchName = '') {
    if (branchId === null || branchId === undefined || branchId === 'ALL') {
      localStorage.setItem(ACTIVE_BRANCH_KEY, 'ALL');
      localStorage.setItem(ACTIVE_BRANCH_NAME_KEY, 'All Branches (Consolidated)');
    } else {
      localStorage.setItem(ACTIVE_BRANCH_KEY, String(branchId));
      if (branchName) localStorage.setItem(ACTIVE_BRANCH_NAME_KEY, branchName);
    }
    window.dispatchEvent(new CustomEvent('branchChanged', { detail: { branchId, branchName } }));
  }

  /**
   * Clear all auth data from storage. Called on logout.
   */
  function clearSession() {
    removeToken();
    removeRefreshToken();
    removeUser();
    localStorage.removeItem(ACTIVE_BRANCH_KEY);
    localStorage.removeItem(ACTIVE_BRANCH_NAME_KEY);
  }

  /**
   * Returns true if an access token exists in storage.
   */
  function hasSession() {
    return !!getToken();
  }

  // ── Generic ───────────────────────────────────────────────────────────────

  function get(key) {
    try {
      const val = localStorage.getItem(key);
      return val ? JSON.parse(val) : null;
    } catch {
      return localStorage.getItem(key);
    }
  }

  function set(key, value) {
    const serialized = typeof value === 'string' ? value : JSON.stringify(value);
    localStorage.setItem(key, serialized);
  }

  function remove(key) {
    localStorage.removeItem(key);
  }

  function clear() {
    localStorage.clear();
  }

  return {
    getToken, setToken, removeToken,
    getRefreshToken, setRefreshToken, removeRefreshToken,
    getUser, setUser, removeUser,
    saveSession, clearSession, hasSession,
    getActiveBranchId, getActiveBranchName, setActiveBranchId,
    get, set, remove, clear,
  };

})();
