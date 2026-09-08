/**
 * Ritham ERP — User Management Page Controller (user.js)
 */

'use strict';

document.addEventListener('DOMContentLoaded', async () => {

  // Auth Guard: Admin & Operations Manager
  if (!Router.protect([ROLES.ADMIN, ROLES.OPERATIONS_MANAGER])) return;

  // Initialize Layout Components
  Sidebar.init({ activePage: 'user' });
  Header.init({ title: 'User Management', subtitle: 'System users, staff accounts, and roles' });

  // State
  let currentPage = 0;
  let totalPages = 0;
  let allRoles = [];
  let allDepartments = [];
  let currentSearch = '';
  let isEditing = false;

  const isAdmin = Auth.hasRole(ROLES.ADMIN);

  // Hide Add button if not Admin
  if (!isAdmin) {
    Utils.hideEl('#addUserBtn');
  }

  // DOM Elements
  const tbody = document.getElementById('userTableBody');
  const searchInput = document.getElementById('searchInput');
  const roleFilter = document.getElementById('roleFilter');
  const deptFilter = document.getElementById('deptFilter');
  const statusFilter = document.getElementById('statusFilter');
  const refreshBtn = document.getElementById('refreshBtn');
  const addUserBtn = document.getElementById('addUserBtn');

  const modalBackdrop = document.getElementById('userModalBackdrop');
  const modalCloseBtn = document.getElementById('modalCloseBtn');
  const modalCancelBtn = document.getElementById('modalCancelBtn');
  const userForm = document.getElementById('userForm');
  const modalTitle = document.getElementById('modalTitle');
  const saveBtnText = document.getElementById('saveBtnText');
  const usernameGroup = document.getElementById('usernameGroup');
  const passwordRow = document.getElementById('passwordRow');

  // Input Fields
  const userIdInput = document.getElementById('userId');
  const fullNameInput = document.getElementById('fullName');
  const mobileInput = document.getElementById('mobileNumber');
  const emailInput = document.getElementById('email');
  const usernameInput = document.getElementById('username');
  const passwordInput = document.getElementById('password');
  const confirmPasswordInput = document.getElementById('confirmPassword');
  const roleSelect = document.getElementById('roleId');
  const deptSelect = document.getElementById('departmentId');

  // Load Metadata (Roles & Departments)
  async function loadMetadata() {
    try {
      const [rolesRes, deptsRes] = await Promise.all([
        Api.get(API.EMPLOYEES + '/roles'),
        Api.get(API.EMPLOYEES + '/departments'),
      ]);

      allRoles = rolesRes || [];
      allDepartments = deptsRes || [];

      if (roleFilter) roleFilter.innerHTML = '<option value="">All Roles</option>' +
        allRoles.map(r => `<option value="${r.id}">${r.description || r.name}</option>`).join('');

      if (deptFilter) deptFilter.innerHTML = '<option value="">All Departments</option>' +
        allDepartments.map(d => `<option value="${d.id}">${d.name}</option>`).join('');

      if (roleSelect) roleSelect.innerHTML = '<option value="">Select Role</option>' +
        allRoles.map(r => `<option value="${r.id}">${r.description || r.name}</option>`).join('');

      if (deptSelect) deptSelect.innerHTML = '<option value="">Select Department</option>' +
        allDepartments.map(d => `<option value="${d.id}">${d.name}</option>`).join('');

    } catch (err) {
      Toast.error('Failed to load roles and departments');
    }
  }

  // Helper: Determine if employee is a system dashboard account
  function isDashboardUser(e) {
    if (!e.username) return false;
    // Exclude factory floor piece-rate workers who have an assigned stage
    if (e.stage && e.stage.trim() !== '') return false;

    const u = (e.username || '').toLowerCase();
    return u === 'admin' ||
           u.includes('manager') ||
           u.includes('reception') ||
           u.includes('production') ||
           ['ROLE_ADMIN', 'ROLE_OPERATIONS_MANAGER', 'ROLE_RECEPTION', 'ROLE_PRODUCTION_EMPLOYEE'].includes(e.role);
  }

  // Helper: Sort order for dashboard accounts
  // Global Admin on top, then grouped by branch (Main Branch before City Branches),
  // and within each branch in desk workflow order: Reception -> Production -> Manager
  function getDashboardSortWeight(u) {
    const username = (u.username || '').toLowerCase();
    if (username === 'admin') return 0;
    const branchWeight = (u.branchId || 1) * 10;
    let roleWeight = 4;
    if (username.includes('reception') || u.roleId === 4) roleWeight = 1;
    else if (username.includes('production') || u.roleId === 3) roleWeight = 2;
    else if (username.includes('manager') || u.roleId === 2) roleWeight = 3;
    return branchWeight + roleWeight;
  }

  // Helper: User display name resolution
  function getDashboardName(user) {
    const DASHBOARD_NAMES = {
      'reception': 'Order Desk',
      'production': 'Production Floor',
      'manager': 'Operations Manager',
      'admin': 'System Administrator'
    };
    const u = (user.username || '').toLowerCase();
    if (DASHBOARD_NAMES[u]) return DASHBOARD_NAMES[u];
    if (user.fullName && user.fullName.trim()) return user.fullName;
    if (u.includes('manager')) return 'Branch Manager';
    if (u.includes('reception')) return 'Order Desk';
    if (u.includes('production')) return 'Production Floor';
    return user.username || 'System User';
  }

  // Load User Accounts List (Strictly System Dashboard Logins)
  async function loadUsers() {
    try {
      Loader.show(tbody, { cols: 5, rows: 4 });

      const res = await Api.get(`${API.EMPLOYEES}?size=100&sort=id,asc`);
      const items = res.content || [];

      // Filter strictly for System Dashboard Accounts (including branch logins)
      let dashboardUsers = items.filter(isDashboardUser);

      // Sort in dashboard order
      dashboardUsers.sort((a, b) => getDashboardSortWeight(a) - getDashboardSortWeight(b));

      // Search & status filtering
      if (currentSearch) {
        const q = currentSearch.toLowerCase();
        dashboardUsers = dashboardUsers.filter(u =>
          (u.fullName || '').toLowerCase().includes(q) ||
          (u.username || '').toLowerCase().includes(q) ||
          (u.branchName || '').toLowerCase().includes(q)
        );
      }
      if (roleFilter?.value) {
        dashboardUsers = dashboardUsers.filter(u => String(u.roleId) === roleFilter.value);
      }
      if (statusFilter?.value !== '' && statusFilter?.value != null) {
        dashboardUsers = dashboardUsers.filter(u => String(u.isActive) === statusFilter.value);
      }

      renderTable(dashboardUsers, dashboardUsers.length);

    } catch (err) {
      tbody.innerHTML = `<tr><td colspan="5" class="text-center py-6 text-danger">Failed to load system accounts: ${err.message}</td></tr>`;
    } finally {
      Loader.hide(tbody);
    }
  }

  // Render Table Rows
  function renderTable(users, totalElements) {
    document.getElementById('totalCountText').textContent = `Total ${totalElements} system dashboard account${totalElements === 1 ? '' : 's'}`;

    if (!users.length) {
      tbody.innerHTML = `<tr><td colspan="5" class="text-center py-8 text-muted">No dashboard accounts found</td></tr>`;
      return;
    }

    tbody.innerHTML = users.map(user => {
      const dashboardName = getDashboardName(user);

      const statusBadge = user.isActive
        ? `<span class="badge badge-success">● Active</span>`
        : `<span class="badge badge-muted">● Inactive</span>`;

      const roleDisplay = Utils.getRoleLabel(user.roleName || user.role);
      const roleBadge = `<span class="badge badge-info">${roleDisplay}</span>`;

      const branchLabel = user.branchName
        ? `🏢 ${user.branchName}`
        : (user.username === 'admin' ? `🌐 All Branches (Global)` : '🏢 Main Branch');

      return `
        <tr>
          <td>
            <div class="font-semibold text-primary">${dashboardName}</div>
            <div class="text-xs text-muted" style="margin-top:2px;">${branchLabel}</div>
          </td>
          <td class="font-mono text-sm text-secondary">${user.username}</td>
          <td>${roleBadge}</td>
          <td>${statusBadge}</td>
          <td class="text-right">
            <button class="btn btn-ghost btn-xs edit-password-btn" data-id="${user.id}" style="color:var(--color-primary, #6366f1); font-weight:600;">
              🔑 Change Password
            </button>
          </td>
        </tr>
      `;
    }).join('');

    attachRowEvents(users);
  }

  // Attach Action Events
  function attachRowEvents(users) {
    document.querySelectorAll('.edit-password-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        const id = parseInt(btn.dataset.id, 10);
        const user = users.find(u => u.id === id);
        if (user) openModalForPasswordChange(user);
      });
    });
  }

  // Open Modal for Password Change
  function openModalForPasswordChange(user) {
    const dashboardName = getDashboardName(user);

    modalTitle.textContent = `Change Password — ${dashboardName}`;
    saveBtnText.textContent = '🔑 Update Password';
    userIdInput.value = user.id;

    fullNameInput.value = dashboardName;
    usernameInput.value = user.username || '';

    passwordInput.value = '';
    confirmPasswordInput.value = '';

    modalBackdrop.classList.remove('hidden');
    passwordInput.focus();
  }

  // Close Modal
  function closeModal() {
    modalBackdrop.classList.add('hidden');
    userForm.reset();
  }

  // Form Submit Handler (Password Update)
  userForm.addEventListener('submit', async (e) => {
    e.preventDefault();

    const id = userIdInput.value;
    const password = passwordInput.value;
    const confirmPwd = confirmPasswordInput.value;

    if (!password || password.length < 6) {
      Toast.error('New password must be at least 6 characters long');
      return;
    }

    if (password !== confirmPwd) {
      Toast.error('New password and confirm password do not match');
      return;
    }

    try {
      const saveBtn = document.getElementById('saveUserBtn');
      saveBtn.disabled = true;
      saveBtnText.textContent = 'Updating...';

      await Api.put(`${API.EMPLOYEES}/${id}`, { password });
      Toast.success('Password updated successfully! 🔑');
      closeModal();
      loadUsers();

    } catch (err) {
      Toast.error(err.message || 'Failed to update password');
    } finally {
      const saveBtn = document.getElementById('saveUserBtn');
      saveBtn.disabled = false;
      saveBtnText.textContent = '🔑 Update Password';
    }
  });

  // Filter & Search Event Listeners
  searchInput.addEventListener('input', Utils.debounce(() => {
    currentSearch = searchInput.value.trim();
    loadUsers();
  }, 300));

  roleFilter?.addEventListener('change', () => loadUsers());
  statusFilter?.addEventListener('change', () => loadUsers());
  refreshBtn?.addEventListener('click', () => loadUsers());

  modalCloseBtn?.addEventListener('click', closeModal);
  modalCancelBtn?.addEventListener('click', closeModal);

  // Initialize
  await loadMetadata();
  await loadUsers();
});
