/**
 * Ritham ERP — Sidebar Component
 *
 * Usage:
 *   Sidebar.init({ activePage: 'dashboard1' });
 *
 * Requires: constants.js, storage.js, auth.js, utils.js
 * Mount point: <aside class="sidebar" id="sidebar"></aside>
 */

'use strict';

const Sidebar = (() => {

  // ── SVG Icons ─────────────────────────────────────────────────────────────

  const ICONS = {
    dashboard1:  `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/></svg>`,
    dashboard2:  `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"><path d="M2 20h.01M7 20v-4"/><path d="M12 20V10"/><path d="M17 20V4"/><path d="M22 20h.01"/></svg>`,
    dashboard3:  `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"><path d="M3 3v18h18"/><path d="M18 17V9"/><path d="M13 17V5"/><path d="M8 17v-3"/></svg>`,
    customer:    `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>`,
    order:       `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/><polyline points="10 9 9 9 8 9"/></svg>`,
    production:  `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="3"/><path d="M19.07 4.93l-1.41 1.41"/><path d="M4.93 4.93l1.41 1.41"/><path d="M22 12h-2"/><path d="M4 12H2"/><path d="M19.07 19.07l-1.41-1.41"/><path d="M4.93 19.07l1.41-1.41"/><path d="M12 22v-2"/><path d="M12 4V2"/></svg>`,
    payment:     `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"><rect x="1" y="4" width="22" height="16" rx="2" ry="2"/><line x1="1" y1="10" x2="23" y2="10"/></svg>`,
    delivery:    `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"><rect x="1" y="3" width="15" height="13"/><polygon points="16 8 20 8 23 11 23 16 16 16 16 8"/><circle cx="5.5" cy="18.5" r="2.5"/><circle cx="18.5" cy="18.5" r="2.5"/></svg>`,
    employee:    `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>`,
    report:      `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"><line x1="18" y1="20" x2="18" y2="10"/><line x1="12" y1="20" x2="12" y2="4"/><line x1="6" y1="20" x2="6" y2="14"/></svg>`,
    measurement: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"><path d="M21.3 15.3a2.4 2.4 0 0 1 0 3.4l-2.6 2.6a2.4 2.4 0 0 1-3.4 0L2.7 8.7a2.41 2.41 0 0 1 0-3.4l2.6-2.6a2.41 2.41 0 0 1 3.4 0l12.6 12.6z"/><path d="m14.5 5.5 2 2"/><path d="m11.5 8.5 2 2"/><path d="m8.5 11.5 2 2"/><path d="m5.5 14.5 2 2"/></svg>`,
    branch:      `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"><path d="M3 21h18"/><path d="M9 8h1"/><path d="M9 12h1"/><path d="M9 16h1"/><path d="M14 8h1"/><path d="M14 12h1"/><path d="M14 16h1"/><path d="M5 21V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v16"/></svg>`,
    migration:   `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"><path d="M4 22h14a2 2 0 0 0 2-2V7.5L14.5 2H6a2 2 0 0 0-2 2v4"/><polyline points="14 2 14 8 20 8"/><path d="M2 15h10"/><path d="m9 18 3-3-3-3"/></svg>`,
    backup:      `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"><ellipse cx="12" cy="5" rx="9" ry="3"/><path d="M3 5v14c0 1.66 4 3 9 3s9-1.34 9-3V5"/><path d="M3 12c0 1.66 4 3 9 3s9-1.34 9-3"/></svg>`,
    chevronLeft: `<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="15 18 9 12 15 6"></polyline></svg>`,
    chevronRight:`<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="9 18 15 12 9 6"></polyline></svg>`,
    logout:      `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/></svg>`,
  };

  // ── Navigation Config ─────────────────────────────────────────────────────

  const ALL_ROLES = [ROLES.ADMIN, ROLES.RECEPTION, ROLES.OPERATIONS_MANAGER, ROLES.PRODUCTION_EMPLOYEE];

  const NAV_GROUPS = [
    {
      label: 'DASHBOARDS',
      items: [
        {
          id: 'dashboard1', label: 'Order Desk',
          icon: ICONS.dashboard1,
          href: '/desks/order-desk/order-desk.html',
          roles: [ROLES.ADMIN, ROLES.RECEPTION, ROLES.OPERATIONS_MANAGER],
        },
        {
          id: 'dashboard2', label: 'Production',
          icon: ICONS.dashboard2,
          href: '/desks/production-desk/production-desk.html',
          roles: [ROLES.ADMIN, ROLES.PRODUCTION_EMPLOYEE, ROLES.OPERATIONS_MANAGER],
        },
        {
          id: 'dashboard3', label: 'Operations',
          icon: ICONS.dashboard3,
          href: '/desks/operations-desk/operations-desk.html',
          roles: [ROLES.ADMIN, ROLES.OPERATIONS_MANAGER],
        },
      ],
    },
    {
      label: 'MODULES',
      items: [
        {
          id: 'customer', label: 'Customers',
          icon: ICONS.customer,
          href: '/desks/customer/customer.html',
          roles: [ROLES.ADMIN, ROLES.RECEPTION, ROLES.OPERATIONS_MANAGER],
        },
        {
          id: 'order', label: 'Orders',
          icon: ICONS.order,
          href: '/desks/order/order.html',
          roles: ALL_ROLES,
        },
        {
          id: 'production', label: 'Production Workstations',
          icon: ICONS.production,
          href: '/desks/production/production.html',
          roles: [ROLES.ADMIN, ROLES.PRODUCTION_EMPLOYEE, ROLES.OPERATIONS_MANAGER],
        },
        {
          id: 'delivery', label: 'Deliveries',
          icon: ICONS.delivery,
          href: '/desks/delivery/delivery.html',
          roles: [ROLES.ADMIN, ROLES.RECEPTION, ROLES.OPERATIONS_MANAGER],
        },
        {
          id: 'measurement', label: 'Measurements',
          icon: ICONS.measurement,
          href: '/desks/measurement/measurement.html',
          roles: ALL_ROLES,
        },
      ],
    },
    {
      label: 'MANAGEMENT',
      items: [
        {
          id: 'user', label: 'Users',
          icon: ICONS.employee,
          href: '/desks/user/user.html',
          roles: [ROLES.ADMIN, ROLES.OPERATIONS_MANAGER],
        },
        {
          id: 'employee', label: 'Employees',
          icon: ICONS.employee,
          href: '/desks/employee/employee.html',
          roles: [ROLES.ADMIN, ROLES.OPERATIONS_MANAGER],
        },
        {
          id: 'stage', label: 'Production Stages',
          icon: ICONS.production,
          href: '/desks/stage/stage.html',
          roles: [ROLES.ADMIN, ROLES.OPERATIONS_MANAGER],
        },
        {
          id: 'branch', label: 'Company Branches',
          icon: ICONS.branch,
          href: '/desks/branch/branch.html',
          roles: [ROLES.ADMIN],
        },
        {
          id: 'migration', label: 'Historical Migration',
          icon: ICONS.migration,
          href: '/desks/migration/migration.html',
          roles: [ROLES.ADMIN],
        },
        {
          id: 'report', label: 'Reports',
          icon: ICONS.report,
          href: '/desks/report/report.html',
          roles: [ROLES.ADMIN, ROLES.OPERATIONS_MANAGER],
        },
        {
          id: 'backup', label: 'Database Backup',
          icon: ICONS.backup,
          href: '/desks/backup/backup.html',
          roles: [ROLES.ADMIN],
        },
      ],
    },
  ];

  // ── Render ────────────────────────────────────────────────────────────────

  function init({ activePage = '' } = {}) {
    const sidebar = document.getElementById('sidebar');
    if (!sidebar) return;

    const user = Storage.getUser();
    if (!user) return;

    const role     = user.role;
    const initials = Utils.initials(user.fullName);

    sidebar.innerHTML = `
      <!-- Brand -->
      <div class="sidebar-brand">
        <div class="sidebar-brand-mark"><img src="/images/logo/logo.jpg" alt="Ritham Designs Logo"></div>
        <div class="sidebar-brand-text">
          <div class="sidebar-brand-name">RITHAM ERP</div>
          <div class="sidebar-brand-sub" title="${Storage.getActiveBranchName()}" style="color:#818cf8; font-weight:600; font-size:11px; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; max-width:140px;">
            🏢 ${Storage.getActiveBranchName()}
          </div>
        </div>
      </div>

      <!-- Toggle button -->
      <button class="sidebar-toggle" id="sidebarToggle" aria-label="Toggle sidebar">
        <span id="sidebarToggleIcon">${ICONS.chevronLeft}</span>
      </button>

      <!-- Navigation -->
      <nav class="sidebar-nav" id="sidebarNav">
        ${_renderGroups(NAV_GROUPS, role, activePage)}
      </nav>

      <!-- Footer / User -->
      <div class="sidebar-footer">
        <div class="sidebar-user" id="sidebarUser">
          <div class="sidebar-user-avatar">${initials}</div>
          <div class="sidebar-user-info">
            <div class="sidebar-user-name">${user.fullName}</div>
            <div class="sidebar-user-role">${Utils.getRoleLabel(role)}</div>
          </div>
        </div>
        <a class="sidebar-item" id="logoutBtn" style="margin-top:4px;" data-tooltip="Logout">
          <span class="sidebar-item-icon">${ICONS.logout}</span>
          <span class="sidebar-item-label">Logout</span>
        </a>
      </div>
    `;

    _attachEvents(sidebar);
  }

  function _renderGroups(groups, userRole, activePage) {
    return groups.map(group => {
      const visibleItems = group.items.filter(item =>
        !item.roles || item.roles.includes(userRole)
      );
      if (visibleItems.length === 0) return '';

      return `
        <div class="sidebar-section">
          <div class="sidebar-section-label">${group.label}</div>
          ${visibleItems.map(item => _renderItem(item, activePage)).join('')}
        </div>
      `;
    }).join('');
  }

  function _renderItem(item, activePage) {
    const isActive = item.id === activePage;
    let subHtml = '';

    if (isActive && item.subItems && item.subItems.length > 0) {
      const fullUrl = window.location.href;
      subHtml = `
        <div class="sidebar-subitems">
          ${item.subItems.map(sub => {
            const isSubActive = fullUrl.includes(sub.href);
            return `
              <a href="${sub.href}" class="sidebar-subitem ${isSubActive ? 'active' : ''}">
                <span style="width:4px;height:4px;border-radius:50%;background:currentColor;display:inline-block;flex-shrink:0;"></span>
                <span>${sub.label}</span>
              </a>
            `;
          }).join('')}
        </div>
      `;
    }

    return `
      <div class="sidebar-item-wrap">
        <a href="${item.href}"
           class="sidebar-item ${isActive ? 'active' : ''}"
           data-tooltip="${item.label}">
          <span class="sidebar-item-icon">${item.icon}</span>
          <span class="sidebar-item-label">${item.label}</span>
        </a>
        ${subHtml}
      </div>
    `;
  }

  function _attachEvents(sidebar) {
    // Collapse/expand toggle
    const toggle    = sidebar.querySelector('#sidebarToggle');
    const toggleIcon = sidebar.querySelector('#sidebarToggleIcon');

    const isCollapsed = localStorage.getItem('sidebar_collapsed') === 'true';
    if (isCollapsed) {
      sidebar.classList.add('collapsed');
      toggleIcon.innerHTML = ICONS.chevronRight;
    }

    toggle?.addEventListener('click', () => {
      const collapsed = sidebar.classList.toggle('collapsed');
      toggleIcon.innerHTML = collapsed ? ICONS.chevronRight : ICONS.chevronLeft;
      localStorage.setItem('sidebar_collapsed', collapsed);
    });

    // Mobile overlay
    const overlay = document.querySelector('.sidebar-overlay');
    overlay?.addEventListener('click', () => {
      sidebar.classList.remove('mobile-open');
    });

    // Logout
    const logoutBtn = sidebar.querySelector('#logoutBtn');
    logoutBtn?.addEventListener('click', async (e) => {
      e.preventDefault();
      Modal.confirm('Are you sure you want to log out?', () => Auth.logout(), {
        title:       'Logout',
        confirmText: 'Logout',
        cancelText:  'Cancel',
        danger:       true,
      });
    });
  }

  // ── Mobile toggle (call from hamburger button) ─────────────────────────────

  function toggleMobile() {
    const sidebar = document.getElementById('sidebar');
    sidebar?.classList.toggle('mobile-open');
  }

  return { init, toggleMobile };

})();
