/**
 * Ritham ERP — HTTP Client (api.js)
 *
 * Central fetch() wrapper that:
 *  - Automatically attaches Authorization: Bearer header
 *  - Parses JSON responses and unwraps ApiResponse<T>
 *  - Handles 401 → attempts silent token refresh → retries once
 *  - On repeated 401 → clears session and redirects to login
 *  - Throws ApiError with message from server for all non-2xx responses
 */

'use strict';

class ApiError extends Error {
  constructor(message, status, data) {
    super(message);
    this.name    = 'ApiError';
    this.status  = status;
    this.data    = data;
  }
}

const Api = (() => {

  let _isRefreshing    = false;
  let _refreshQueue    = [];

  // ── Core request function ─────────────────────────────────────────────────

  async function request(method, url, body = null, isRetry = false) {
    const headers = {
      'Content-Type': 'application/json; charset=utf-8',
      'Accept':       'application/json',
    };

    const token = Storage.getToken();
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }

    const activeBranchId = Storage.getActiveBranchId();
    if (activeBranchId && activeBranchId !== 'ALL') {
      headers[APP.BRANCH_HEADER || 'X-Branch-Id'] = String(activeBranchId);
    }

    const options = { method, headers };
    if (body !== null) {
      options.body = JSON.stringify(body);
    }

    let response;
    try {
      response = await fetch(url, options);
    } catch (networkError) {
      throw new ApiError('Cannot connect to server. Please check if the application is running.', 0);
    }

    // ── 401 handling with silent refresh ─────────────────────────────────

    if (response.status === HTTP.UNAUTHORIZED && !isRetry) {
      const refreshToken = Storage.getRefreshToken();
      if (!refreshToken) {
        _redirectToLogin();
        throw new ApiError('Session expired. Please log in again.', 401);
      }

      if (_isRefreshing) {
        // Queue this request until refresh completes
        return new Promise((resolve, reject) => {
          _refreshQueue.push({ resolve, reject, method, url, body });
        });
      }

      _isRefreshing = true;
      try {
        const refreshData = await _doRefresh(refreshToken);
        Storage.saveSession(refreshData);
        _processQueue(null);
        _isRefreshing = false;
        // Retry original request with new token
        return request(method, url, body, true);
      } catch (err) {
        _processQueue(err);
        _isRefreshing = false;
        Storage.clearSession();
        _redirectToLogin();
        throw new ApiError('Session expired. Please log in again.', 401);
      }
    }

    // ── Parse response ────────────────────────────────────────────────────

    let json;
    const contentType = response.headers.get('content-type') || '';
    if (contentType.includes('application/json')) {
      json = await response.json();
    } else {
      json = null;
    }

    if (!response.ok) {
      const message = json?.message || `Error ${response.status}`;
      throw new ApiError(message, response.status, json);
    }

    // Unwrap ApiResponse<T> envelope
    if (json && typeof json === 'object' && 'data' in json) {
      return json.data;
    }

    return json;
  }

  // ── Refresh helper ────────────────────────────────────────────────────────

  async function _doRefresh(refreshToken) {
    const response = await fetch(API.REFRESH, {
      method:  'POST',
      headers: { 'Content-Type': 'application/json' },
      body:    JSON.stringify({ refreshToken }),
    });

    if (!response.ok) throw new Error('Refresh failed');

    const json = await response.json();
    return json.data ?? json;
  }

  function _processQueue(error) {
    _refreshQueue.forEach(({ resolve, reject, method, url, body }) => {
      if (error) {
        reject(error);
      } else {
        resolve(request(method, url, body, true));
      }
    });
    _refreshQueue = [];
  }

  function _redirectToLogin() {
    // Always use absolute path — depth calculation was fragile and caused
    // redirects to /pages/login/login.html (wrong) instead of /desks/login/login.html
    Storage.clearSession();
    window.location.href = ROUTES.LOGIN;
  }

  // ── Public API ────────────────────────────────────────────────────────────

  function get(url)              { return request('GET',    url); }
  function post(url, body)       { return request('POST',   url, body); }
  function put(url, body)        { return request('PUT',    url, body); }
  function patch(url, body)      { return request('PATCH',  url, body); }
  function del(url)              { return request('DELETE', url); }

  /**
   * POST without JWT — used for login endpoint.
   */
  async function postPublic(url, body) {
    const response = await fetch(url, {
      method:  'POST',
      headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
      body:    JSON.stringify(body),
    });

    const json = await response.json().catch(() => null);

    if (!response.ok) {
      const message = json?.message || `Error ${response.status}`;
      throw new ApiError(message, response.status, json);
    }

    return json?.data ?? json;
  }

  return { get, post, put, patch, del, delete: del, postPublic, ApiError };

})();
