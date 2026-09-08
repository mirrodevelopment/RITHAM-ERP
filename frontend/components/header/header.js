/**
 * Ritham ERP — Header Component
 *
 * Features:
 *  - System status badge: checks Internet → Backend → DB on every page
 *  - Live digital clock
 *  - Logged-in user info + dropdown
 *  - Mobile menu toggle
 *  - Logout with confirm
 *
 * Usage:
 *   Header.init({ title: 'Page Title', subtitle: 'Optional subtitle' });
 *
 * Mount point: <header class="header" id="header"></header>
 */

'use strict';

/* ── Global Theme Manager (available on every page via header) ── */
window.ThemeManager = window.ThemeManager || (function() {
  const THEMES = [
    { id: 'white-black', name: 'White & Black (Luxe)',  icon: '⚪' },
    { id: 'dark',        name: 'Black & White (Dark)',  icon: '🌙' },
    { id: 'rosegold',    name: 'Rose Gold & Cashmere',  icon: '🌸' },
    { id: 'midnight',    name: 'Midnight Royal',        icon: '🌌' }
  ];

  function getSavedTheme() {
    try {
      return localStorage.getItem('ritham_theme') || 'white-black';
    } catch (e) {
      return 'white-black';
    }
  }

  function applyTheme(themeId) {
    const validTheme = THEMES.find(t => t.id === themeId) ? themeId : 'white-black';
    document.documentElement.setAttribute('data-theme', validTheme);
    try {
      localStorage.setItem('ritham_theme', validTheme);
    } catch (e) {}

    // Update active state on any theme buttons
    document.querySelectorAll('.theme-option-btn').forEach(btn => {
      const isActive = btn.dataset.themeId === validTheme;
      btn.classList.toggle('active', isActive);
      btn.style.backgroundColor = isActive ? 'var(--bg-hover, rgba(255,255,255,0.08))' : 'transparent';
    });

    document.querySelectorAll('.theme-check-icon').forEach(chk => {
      chk.style.opacity = (chk.dataset.themeCheck === validTheme) ? '1' : '0';
    });

    const themeLabelEl = document.getElementById('currentThemeLabel');
    if (themeLabelEl) {
      const cur = THEMES.find(t => t.id === validTheme) || THEMES[0];
      themeLabelEl.textContent = `${cur.icon} ${cur.name}`;
    }

    // Broadcast theme change event so components/charts can update colors if needed
    window.dispatchEvent(new CustomEvent('themeChanged', { detail: { theme: validTheme } }));
  }

  function init() {
    applyTheme(getSavedTheme());
  }

  // Execute immediately to prevent theme flash
  init();

  return { THEMES, getSavedTheme, applyTheme, init };
})();

const Header = (() => {

  const ICONS = {
    menu:        `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="3" y1="6" x2="21" y2="6"/><line x1="3" y1="12" x2="21" y2="12"/><line x1="3" y1="18" x2="21" y2="18"/></svg>`,
    bell:        `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"><path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.73 21a2 2 0 0 1-3.46 0"/></svg>`,
    chevronDown: `<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="6 9 12 15 18 9"></polyline></svg>`,
    user:        `<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>`,
    logout:      `<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/></svg>`,
    clock:       `<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>`,
  };

  /* ── Internal intervals (cleared on re-init) ── */
  let _healthInterval = null;
  let _clockInterval  = null;

  /* ── Status levels ── */
  const STATUS = {
    LIVE:     { color: '#10B981', shadow: 'rgba(16,185,129,0.7)',  label: 'System Live',     tip: 'Internet ✔  Backend ✔  Database ✔' },
    DB_WARN:  { color: '#F59E0B', shadow: 'rgba(245,158,11,0.7)',  label: 'DB Warning',      tip: 'Backend is up but database reports a warning' },
    OFFLINE:  { color: '#EF4444', shadow: 'rgba(239,68,68,0.7)',   label: 'Server Offline',  tip: 'Cannot reach the backend server' },
    NET_WARN: { color: '#F59E0B', shadow: 'rgba(245,158,11,0.7)',  label: 'Network Offline', tip: 'No internet connection detected' },
    CHECKING: { color: '#6B7280', shadow: 'rgba(107,114,128,0.4)', label: 'Checking…',       tip: 'Checking system connectivity…' },
  };

  /* ─────────────────────────────────────────────────────────────────────
     PUBLIC: init
  ───────────────────────────────────────────────────────────────────── */

  function init({ title = 'Ritham ERP', subtitle = '' } = {}) {
    const header = document.getElementById('header');
    if (!header) return;

    // Stop any previous intervals from a prior init on this page
    _clearIntervals();

    const user     = Storage.getUser();
    const fullName = user?.fullName || 'User';
    const role     = user?.role     || '';
    const initials = Utils.initials(fullName);

    header.innerHTML = `
      <!-- Left: mobile menu + page title -->
      <div class="header-left">
        <button class="btn btn-ghost btn-icon mobile-menu-btn" id="mobileMenuBtn" aria-label="Toggle menu">
          ${ICONS.menu}
        </button>
        <div>
          <div class="header-title">${title}</div>
          ${subtitle ? `<div class="header-breadcrumb">${subtitle}</div>` : ''}
        </div>
      </div>

      <!-- Right: status + clock + bell + user -->
      <div class="header-right" style="display:flex;align-items:center;gap:10px;">

        <!-- ── System Live/Offline Status Badge ── -->
        <div id="sysBadge" title="${STATUS.CHECKING.tip}"
          style="
            display:inline-flex;align-items:center;gap:7px;
            padding:5px 12px;
            background:rgba(255,255,255,0.03);
            border:1px solid var(--border-default,#2A2A2A);
            border-radius:9999px;
            font-size:11px;font-weight:600;
            color:var(--text-secondary,#A0A0A0);
            cursor:default;
            transition:border-color 0.3s;
          ">
          <!-- Animated dot -->
          <span id="sysDot" style="
            position:relative;
            width:9px;height:9px;
            border-radius:50%;
            background:${STATUS.CHECKING.color};
            box-shadow:0 0 0 0 ${STATUS.CHECKING.shadow};
            display:inline-block;
            transition:background 0.4s,box-shadow 0.4s;
          "></span>
          <span id="sysLabel">${STATUS.CHECKING.label}</span>
        </div>

        <!-- ── Live Clock ── -->
        <div id="headerClock" title="Current local time"
          style="
            display:inline-flex;align-items:center;gap:6px;
            padding:5px 12px;
            background:rgba(255,255,255,0.03);
            border:1px solid var(--border-default,#2A2A2A);
            border-radius:9999px;
            font-size:11px;font-weight:500;
            color:var(--text-primary,#FFF);
            font-family:var(--font-mono,monospace);
          ">
          ${ICONS.clock}
          <span id="clockText">--:--:--</span>
        </div>

        <!-- ── Company Branch Selector ── -->
        ${user?.isGlobalAdmin || (role === ROLES.ADMIN && !user?.branchId) ? `
          <div class="dropdown" id="branchDropdown" style="position:relative;">
            <button class="btn btn-ghost" id="branchDropdownBtn" title="Switch Company Branch" style="gap:6px;padding:5px 12px;border:1px solid rgba(99,102,241,0.3);border-radius:9999px;font-size:11px;font-weight:600;color:var(--text-primary);cursor:pointer;background:rgba(99,102,241,0.08);">
              <span style="color:#818cf8;">🏢</span>
              <span id="currentBranchLabel">${Storage.getActiveBranchName()}</span>
              ${ICONS.chevronDown}
            </button>
            <div class="dropdown-menu hidden" id="branchMenu" style="min-width:280px;position:absolute;right:0;top:100%;margin-top:6px;z-index:1000;background:var(--bg-surface);border:1px solid var(--border-strong);border-radius:10px;box-shadow:var(--shadow-dropdown);overflow:hidden;padding:4px 0;">
              <div style="padding:8px 14px;font-size:10px;font-weight:700;color:var(--text-muted);text-transform:uppercase;letter-spacing:0.06em;border-bottom:1px solid var(--border-default);display:flex;justify-content:space-between;align-items:center;">
                <span>Active Branch Context</span>
                <a href="/desks/branch/branch.html" style="color:var(--color-primary,#818cf8);font-size:10px;font-weight:600;text-decoration:none;">Manage Branches →</a>
              </div>
              <div id="branchListItems" style="max-height:250px;overflow-y:auto;">
                <div style="padding:10px 14px;font-size:11px;color:var(--text-muted);">Loading branches...</div>
              </div>
            </div>
          </div>
        ` : `
          <div title="Assigned Company Branch" style="display:inline-flex;align-items:center;gap:6px;padding:5px 12px;background:rgba(99,102,241,0.08);border:1px solid rgba(99,102,241,0.3);border-radius:9999px;font-size:11px;font-weight:600;color:var(--text-primary);">
            <span style="color:#818cf8;">🏢</span>
            <span>${user?.branchName || 'Main Branch'}</span>
          </div>
        `}

        <!-- ── Theme Switcher Dropdown Button ── -->
        <div class="dropdown" id="themeDropdown" style="position:relative;">
          <button class="btn btn-ghost" id="themeDropdownBtn" title="Switch Theme" style="gap:6px;padding:5px 12px;border:1px solid var(--border-default);border-radius:9999px;font-size:11px;font-weight:500;color:var(--text-primary);cursor:pointer;">
            <span id="currentThemeLabel">🌙 Black & White (Dark)</span>
            ${ICONS.chevronDown}
          </button>
          <div class="dropdown-menu hidden" id="themeMenu" style="min-width:220px;position:absolute;right:0;top:100%;margin-top:6px;z-index:1000;background:var(--bg-surface);border:1px solid var(--border-strong);border-radius:10px;box-shadow:var(--shadow-dropdown);overflow:hidden;padding:4px 0;">
            <div style="padding:8px 14px;font-size:10px;font-weight:700;color:var(--text-muted);text-transform:uppercase;letter-spacing:0.06em;border-bottom:1px solid var(--border-default);">Select Appearance</div>
            <button type="button" class="dropdown-item theme-option-btn" data-theme-id="white-black" style="display:flex;align-items:center;justify-content:space-between;width:100%;padding:10px 14px;border:none;background:transparent;color:var(--text-primary);cursor:pointer;font-size:12px;text-align:left;">
              <span style="display:flex;align-items:center;gap:8px;"><span>⚪</span> White & Black (Luxe)</span>
              <span class="theme-check-icon" data-theme-check="white-black" style="font-size:11px;color:var(--status-completed);opacity:0;">✔</span>
            </button>
            <button type="button" class="dropdown-item theme-option-btn" data-theme-id="dark" style="display:flex;align-items:center;justify-content:space-between;width:100%;padding:10px 14px;border:none;background:transparent;color:var(--text-primary);cursor:pointer;font-size:12px;text-align:left;">
              <span style="display:flex;align-items:center;gap:8px;"><span>🌙</span> Black & White (Dark)</span>
              <span class="theme-check-icon" data-theme-check="dark" style="font-size:11px;color:var(--status-completed);opacity:0;">✔</span>
            </button>
            <button type="button" class="dropdown-item theme-option-btn" data-theme-id="rosegold" style="display:flex;align-items:center;justify-content:space-between;width:100%;padding:10px 14px;border:none;background:transparent;color:var(--text-primary);cursor:pointer;font-size:12px;text-align:left;">
              <span style="display:flex;align-items:center;gap:8px;"><span>🌸</span> Rose Gold & Cashmere</span>
              <span class="theme-check-icon" data-theme-check="rosegold" style="font-size:11px;color:var(--status-completed);opacity:0;">✔</span>
            </button>
            <button type="button" class="dropdown-item theme-option-btn" data-theme-id="midnight" style="display:flex;align-items:center;justify-content:space-between;width:100%;padding:10px 14px;border:none;background:transparent;color:var(--text-primary);cursor:pointer;font-size:12px;text-align:left;">
              <span style="display:flex;align-items:center;gap:8px;"><span>🌌</span> Midnight Royal</span>
              <span class="theme-check-icon" data-theme-check="midnight" style="font-size:11px;color:var(--status-completed);opacity:0;">✔</span>
            </button>
          </div>
        </div>

        <!-- ── Notification Bell ── -->
        <button class="btn btn-ghost btn-icon" aria-label="Notifications" title="Notifications">
          ${ICONS.bell}
        </button>


        <!-- ── User Dropdown ── -->
        <div class="dropdown" id="userDropdown">
          <button class="btn btn-ghost" id="userDropdownBtn" style="gap:10px;padding:6px 10px;">
            <div style="
              width:28px;height:28px;
              background:var(--bg-surface-3);
              border:1px solid var(--border-strong);
              border-radius:50%;
              display:flex;align-items:center;justify-content:center;
              font-size:var(--text-xs);font-weight:var(--weight-semibold);
              color:var(--text-secondary);flex-shrink:0;
            ">${initials}</div>
            <div style="text-align:left;display:flex;flex-direction:column;justify-content:center;line-height:1.2;" class="header-user-info-expanded">
              <div style="font-size:var(--text-xs,12px);font-weight:var(--weight-semibold,600);color:var(--text-primary,#FFF);white-space:nowrap;">${fullName}</div>
              <div style="font-size:10px;color:var(--text-muted,#888);white-space:nowrap;">${Utils.getRoleLabel(role)}</div>
            </div>
            ${ICONS.chevronDown}
          </button>

          <div class="dropdown-menu hidden" id="userMenu">
            <div style="padding:12px 16px;border-bottom:1px solid var(--border-default);">
              <div style="font-size:var(--text-sm);font-weight:var(--weight-semibold);color:var(--text-primary)">${fullName}</div>
              <div style="font-size:var(--text-xs);color:var(--text-muted);margin-top:2px">${Utils.getRoleLabel(role)}</div>
            </div>
            <div class="dropdown-item danger" id="headerLogoutBtn">
              ${ICONS.logout}
              Logout
            </div>
          </div>

        </div>

      </div><!-- /header-right -->
    `;

    _attachEvents();
    _startClock();
    _startHealthCheck();   // immediate + recurring on THIS page
  }

  /* ─────────────────────────────────────────────────────────────────────
     Clock
  ───────────────────────────────────────────────────────────────────── */

  function _startClock() {
    _tick();
    _clockInterval = setInterval(_tick, 1000);
  }

  function _tick() {
    const el = document.getElementById('clockText');
    if (!el) { clearInterval(_clockInterval); return; }
    el.textContent = new Date().toLocaleTimeString([], {
      hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: true,
    });
  }

  /* ─────────────────────────────────────────────────────────────────────
     Health Check — Internet → Backend → DB
  ───────────────────────────────────────────────────────────────────── */

  function _startHealthCheck() {
    // Immediate first check
    _checkHealth();
    // Then every 8 seconds
    _healthInterval = setInterval(_checkHealth, 8000);

    // Also react instantly to browser going online/offline
    window.addEventListener('online',  _checkHealth);
    window.addEventListener('offline', _checkHealth);
  }

  async function _checkHealth() {
    const dot   = document.getElementById('sysDot');
    const label = document.getElementById('sysLabel');
    const badge = document.getElementById('sysBadge');
    if (!dot || !label) return;

    // ── Step 1: Browser reports network offline ─────────────────────
    if (!navigator.onLine) {
      _applyStatus(dot, label, badge, STATUS.NET_WARN);
      return;
    }

    // ── Step 2: Try to reach the backend health endpoint ────────────
    try {
      const controller = new AbortController();
      const timer = setTimeout(() => controller.abort(), 5000); // 5-sec timeout

      const res = await fetch('/actuator/health', {
        method:  'GET',
        cache:   'no-store',
        signal:  controller.signal,
      });
      clearTimeout(timer);

      if (!res.ok) {
        _applyStatus(dot, label, badge, STATUS.OFFLINE);
        return;
      }

      // ── Step 3: Parse backend health JSON ──────────────────────────
      const data = await res.json().catch(() => null);

      if (data?.status === 'UP') {
        _applyStatus(dot, label, badge, STATUS.LIVE);
      } else if (data?.status === 'DOWN') {
        _applyStatus(dot, label, badge, STATUS.OFFLINE);
      } else {
        // Status is UNKNOWN / partial — likely DB warning
        _applyStatus(dot, label, badge, STATUS.DB_WARN);
      }

    } catch (_) {
      // Fetch threw — network error or timeout
      _applyStatus(dot, label, badge, STATUS.OFFLINE);
    }
  }

  function _applyStatus(dot, label, badge, s) {
    dot.style.background  = s.color;
    dot.style.boxShadow   = `0 0 0 3px ${s.shadow}`;
    label.textContent     = s.label;
    if (badge) badge.title = s.tip;

    // Animate the pulse ring on LIVE only
    if (s === STATUS.LIVE) {
      dot.style.animation = 'headerPulse 2s infinite';
    } else {
      dot.style.animation = 'none';
      dot.style.boxShadow = `0 0 8px ${s.shadow}`;
    }
  }

  /* ─────────────────────────────────────────────────────────────────────
     Events
  ───────────────────────────────────────────────────────────────────── */

  function _attachEvents() {
    // Mobile menu
    document.getElementById('mobileMenuBtn')?.addEventListener('click', () => {
      Sidebar.toggleMobile();
    });

    // User dropdown
    const btn  = document.getElementById('userDropdownBtn');
    const menu = document.getElementById('userMenu');
    btn?.addEventListener('click', (e) => {
      e.stopPropagation();
      menu?.classList.toggle('hidden');
      document.getElementById('themeMenu')?.classList.add('hidden');
    });

    // Theme dropdown
    const themeBtn  = document.getElementById('themeDropdownBtn');
    const themeMenu = document.getElementById('themeMenu');
    themeBtn?.addEventListener('click', (e) => {
      e.stopPropagation();
      themeMenu?.classList.toggle('hidden');
      menu?.classList.add('hidden');
    });

    document.querySelectorAll('.theme-option-btn').forEach(b => {
      b.addEventListener('click', (e) => {
        e.stopPropagation();
        const id = e.currentTarget.dataset.themeId;
        ThemeManager.applyTheme(id);
        themeMenu?.classList.add('hidden');
      });
    });

    // Branch dropdown (Admin only)
    const branchBtn = document.getElementById('branchDropdownBtn');
    const branchMenu = document.getElementById('branchMenu');
    if (branchBtn && branchMenu) {
      branchBtn.addEventListener('click', async (e) => {
        e.stopPropagation();
        branchMenu.classList.toggle('hidden');
        menu?.classList.add('hidden');
        themeMenu?.classList.add('hidden');

        if (!branchMenu.classList.contains('hidden')) {
          await _loadBranchesList();
        }
      });
    }

    document.addEventListener('click', () => {
      menu?.classList.add('hidden');
      themeMenu?.classList.add('hidden');
      branchMenu?.classList.add('hidden');
    });

    // Apply saved theme on header render
    ThemeManager.applyTheme(ThemeManager.getSavedTheme());


    // Logout
    document.getElementById('headerLogoutBtn')?.addEventListener('click', () => {
      Modal.confirm('Are you sure you want to log out?', () => Auth.logout(), {
        title: 'Logout', confirmText: 'Logout', danger: true,
      });
    });
  }

  async function _loadBranchesList() {
    const listContainer = document.getElementById('branchListItems');
    if (!listContainer) return;

    try {
      const res = await Api.get(API.BRANCHES);
      const branches = res.data || res || [];
      const currentActiveId = Storage.getActiveBranchId();

      let html = '';

      // All Branches (Consolidated) Option
      const isAllActive = currentActiveId === 'ALL';
      html += `
        <button type="button" class="dropdown-item branch-option-btn" data-branch-id="ALL" data-branch-name="All Branches (Consolidated)"
          style="display:flex;align-items:center;justify-content:space-between;width:100%;padding:10px 14px;border:none;background:${isAllActive ? 'var(--bg-hover, rgba(255,255,255,0.08))' : 'transparent'};color:var(--text-primary);cursor:pointer;font-size:12px;text-align:left;border-bottom:1px dashed var(--border-default);">
          <div style="display:flex;align-items:center;gap:8px;">
            <span>🌐</span>
            <div>
              <div style="font-weight:600;">All Branches</div>
              <div style="font-size:10px;color:var(--text-muted);">Consolidated Company Overview</div>
            </div>
          </div>
          <span style="color:#818cf8;font-weight:bold;opacity:${isAllActive ? '1' : '0'};">✔</span>
        </button>
      `;

      // Each Branch
      branches.forEach(b => {
        const isBranchActive = String(currentActiveId) === String(b.id);
        html += `
          <button type="button" class="dropdown-item branch-option-btn" data-branch-id="${b.id}" data-branch-name="${b.name}"
            style="display:flex;align-items:center;justify-content:space-between;width:100%;padding:10px 14px;border:none;background:${isBranchActive ? 'var(--bg-hover, rgba(255,255,255,0.08))' : 'transparent'};color:var(--text-primary);cursor:pointer;font-size:12px;text-align:left;">
            <div style="display:flex;align-items:center;gap:8px;">
              <span style="color:#818cf8;">🏢</span>
              <div>
                <div style="font-weight:600;">${b.name}</div>
                <div style="font-size:10px;color:var(--text-muted);">${b.city || ''} ${b.branchCode ? '• ' + b.branchCode : ''}</div>
              </div>
            </div>
            <span style="color:#818cf8;font-weight:bold;opacity:${isBranchActive ? '1' : '0'};">✔</span>
          </button>
        `;
      });

      listContainer.innerHTML = html;

      // Click handlers for branch buttons
      listContainer.querySelectorAll('.branch-option-btn').forEach(btn => {
        btn.addEventListener('click', (e) => {
          e.stopPropagation();
          const branchId = e.currentTarget.dataset.branchId;
          const branchName = e.currentTarget.dataset.branchName;
          Storage.setActiveBranchId(branchId, branchName);
          const label = document.getElementById('currentBranchLabel');
          if (label) label.textContent = branchName;
          document.getElementById('branchMenu')?.classList.add('hidden');
          // Reload page to reflect active branch across all components
          window.location.reload();
        });
      });

    } catch (err) {
      listContainer.innerHTML = '<div style="padding:10px 14px;font-size:11px;color:var(--color-danger,#ef4444);">Failed to load branches</div>';
    }
  }

  /* ─────────────────────────────────────────────────────────────────────
     Cleanup
  ───────────────────────────────────────────────────────────────────── */

  function _clearIntervals() {
    if (_healthInterval) { clearInterval(_healthInterval); _healthInterval = null; }
    if (_clockInterval)  { clearInterval(_clockInterval);  _clockInterval  = null; }
  }

  /* ─────────────────────────────────────────────────────────────────────
     Inject pulse keyframe once into document
  ───────────────────────────────────────────────────────────────────── */

  (function _injectPulseAnimation() {
    if (document.getElementById('_headerPulseStyle')) return;
    const style = document.createElement('style');
    style.id = '_headerPulseStyle';
    style.textContent = `
      @keyframes headerPulse {
        0%   { box-shadow: 0 0 0 0 rgba(16,185,129,0.7); }
        60%  { box-shadow: 0 0 0 6px rgba(16,185,129,0); }
        100% { box-shadow: 0 0 0 0 rgba(16,185,129,0); }
      }
    `;
    document.head.appendChild(style);
  })();

  return { init, checkHealth: _checkHealth };

})();
