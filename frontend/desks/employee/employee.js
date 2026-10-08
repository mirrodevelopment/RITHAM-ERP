/**
 * Ritham ERP — Employee Management Page Controller (employee.js)
 */

'use strict';

document.addEventListener('DOMContentLoaded', async () => {

  // Auth Guard: Admin & Operations Manager
  if (!Router.protect([ROLES.ADMIN, ROLES.OPERATIONS_MANAGER])) return;

  // Initialize Layout Components
  Sidebar.init({ activePage: 'employee' });
  Header.init({ title: 'Employee Management', subtitle: 'Staff accounts and assignments' });

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
    Utils.hideEl('#addEmployeeBtn');
  }

  // DOM Elements
  const tbody = document.getElementById('employeeTableBody');
  const searchInput = document.getElementById('searchInput');
  const roleFilter = document.getElementById('roleFilter');
  const stageFilter = document.getElementById('stageFilter');
  const deptFilter = document.getElementById('deptFilter');
  const statusFilter = document.getElementById('statusFilter');
  const refreshBtn = document.getElementById('refreshBtn');
  const addEmployeeBtn = document.getElementById('addEmployeeBtn');

  const modalBackdrop = document.getElementById('employeeModalBackdrop');
  const modalCloseBtn = document.getElementById('modalCloseBtn');
  const modalCancelBtn = document.getElementById('modalCancelBtn');
  const employeeForm = document.getElementById('employeeForm');
  const modalTitle = document.getElementById('modalTitle');
  const saveBtnText = document.getElementById('saveBtnText');
  const passwordRow = document.getElementById('passwordRow');

  // Input Fields
  const employeeIdInput = document.getElementById('employeeId');
  const fullNameInput = document.getElementById('fullName');
  const mobileInput = document.getElementById('mobileNumber');
  const emailInput = document.getElementById('email');
  const passwordInput = document.getElementById('password');
  const confirmPasswordInput = document.getElementById('confirmPassword');
  const deptSelect = document.getElementById('departmentId');
  const stageSelect = document.getElementById('stageSelect');
  const advanceInput = document.getElementById('advance');
  const joiningDateInput = document.getElementById('joiningDate');

  // Initialize stage registry and metadata safely
  try {
    if (typeof StageRegistry !== 'undefined') {
      await StageRegistry.init();
    }
  } catch (e) {
    console.warn('StageRegistry init non-fatal warning:', e);
  }

  try {
    await loadMetadata();
  } catch (e) {
    console.warn('loadMetadata non-fatal warning:', e);
  }

  // Load Employee List
  await loadEmployees();

  // ── Event Listeners ───────────────────────────────────────────────────────

  // Search input debounced
  searchInput.addEventListener('input', Utils.debounce((e) => {
    currentSearch = e.target.value.trim();
    currentPage = 0;
    loadEmployees();
  }, 300));

  // Filters
  roleFilter?.addEventListener('change', () => { currentPage = 0; loadEmployees(); });
  stageFilter?.addEventListener('change', () => { currentPage = 0; loadEmployees(); });
  deptFilter?.addEventListener('change', () => { currentPage = 0; loadEmployees(); });
  statusFilter?.addEventListener('change', () => { currentPage = 0; loadEmployees(); });

  refreshBtn.addEventListener('click', async () => {
    Toast.info('Refreshing employees...');
    await loadEmployees();
    Toast.success('Employee list updated');
  });

  document.getElementById('exportEmployeesExcelBtn')?.addEventListener('click', async () => {
    try {
      Toast.info('Preparing employees export…');
      const url = `${API.EMPLOYEES}?page=0&size=500&search=${encodeURIComponent(currentSearch)}`;
      const data = await Api.get(url);
      const items = data.content || data || [];

      if (!items.length) {
        Toast.warning('No employee records available to export.');
        return;
      }

      const columns = [
        { key: 'sno', header: 'S.No' },
        { key: 'employeeCode', header: 'Employee Code', transform: (v, e) => v || `EMP-${e.id}` },
        { key: 'fullName', header: 'Full Name', transform: v => v || '—' },
        { key: 'mobileNumber', header: 'Mobile Number', transform: v => v || '—' },
        { key: 'email', header: 'Email Address', transform: v => v || '—' },
        { key: 'username', header: 'Username', transform: v => v || '—' },
        { key: 'role', header: 'Role', transform: (v, e) => e.roleLabel || v || '—' },
        { key: 'department', header: 'Department', transform: v => v || '—' },
        { key: 'branchName', header: 'Branch', transform: (v, e) => e.branchName || (e.branchId ? `Branch ${e.branchId}` : 'Main Branch') },
        { key: 'stage', header: 'Assigned Stage', transform: v => v || 'General' },
        { key: 'advance', header: 'Advance Balance (Rs)', transform: v => Number(v || 0) },
        { key: 'isActive', header: 'Active Status', transform: v => v !== false ? 'Active' : 'Inactive' },
        { key: 'joiningDate', header: 'Joining Date', transform: v => ExcelExport.formatDate(v) },
      ];

      await ExcelExport.exportData({
        data: items,
        fileName: 'ritham-employees',
        sheetName: 'Staff Directory',
        columns,
      });
    } catch (err) {
      Toast.error('Failed to export employees: ' + (err.message || 'Error'));
    }
  });

  // Modal Open / Close
  if (addEmployeeBtn) {
    addEmployeeBtn.addEventListener('click', openCreateModal);
  }
  modalCloseBtn.addEventListener('click', closeModal);
  modalCancelBtn.addEventListener('click', closeModal);

  modalBackdrop.addEventListener('click', (e) => {
    if (e.target === modalBackdrop) closeModal();
  });

  // View Modal Close
  const viewModalCloseBtn = document.getElementById('viewModalCloseBtn');
  const viewModalCloseFooterBtn = document.getElementById('viewModalCloseFooterBtn');
  const viewBackdropEl = document.getElementById('viewEmployeeModalBackdrop');

  viewModalCloseBtn?.addEventListener('click', closeViewModal);
  viewModalCloseFooterBtn?.addEventListener('click', closeViewModal);
  viewBackdropEl?.addEventListener('click', (e) => {
    if (e.target === viewBackdropEl) closeViewModal();
  });

  // Form Submission
  employeeForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    if (!validateForm()) return;

    if (isEditing) {
      await updateEmployee();
    } else {
      await createEmployee();
    }
  });

  // ── Functions ─────────────────────────────────────────────────────────────

  async function loadMetadata() {
    try {
      const [rolesRes, deptsRes] = await Promise.all([
        Api.get(API.EMPLOYEES + '/roles'),
        Api.get(API.EMPLOYEES + '/departments')
      ]);

      allRoles = rolesRes || [];
      allDepartments = deptsRes || [];

      // Populate Role select options
      const roleOptionsHtml = allRoles.map(r => `
        <option value="${r.id}">${r.description || r.name}</option>
      `).join('');

      if (roleFilter) roleFilter.innerHTML = '<option value="">All Roles</option>' + roleOptionsHtml;

      // Populate Department select options
      const deptOptionsHtml = allDepartments.map(d => `
        <option value="${d.id}">${d.name}</option>
      `).join('');

      if (deptSelect) deptSelect.innerHTML = '<option value="">Select Department (Optional)</option>' + deptOptionsHtml;
      if (deptFilter) deptFilter.innerHTML = '<option value="">All Departments</option>' + deptOptionsHtml;

      // Populate Stage select options dynamically from database table
      const stages = (typeof StageRegistry !== 'undefined') ? StageRegistry.getAll() : [];
      if (stageSelect && stages.length > 0) {
        stageSelect.innerHTML = '<option value="">Select Production Stage (Optional)</option>' +
          stages.map(s => `<option value="${s.stageKey}">${s.title}</option>`).join('');
      }
      if (stageFilter && stages.length > 0) {
        stageFilter.innerHTML = '<option value="">All Production Stages</option>' +
          stages.map(s => `<option value="${s.stageKey}">${s.title}</option>`).join('');
      }

    } catch (err) {
      Toast.error('Failed to load roles and departments');
    }
  }

  async function loadEmployees() {
    tbody.innerHTML = `
      <tr>
        <td colspan="6" style="padding:48px; text-align:center;">
          <div class="spinner" style="margin:0 auto; width:28px; height:28px; border:2px solid rgba(255,255,255,0.1); border-top-color:#888; border-radius:50%; animation:spin 0.7s linear infinite;"></div>
        </td>
      </tr>
    `;

    try {
      const url = `${API.EMPLOYEES}?page=${currentPage}&size=100&search=${encodeURIComponent(currentSearch)}`;
      const data = await Api.get(url);

      const items = data.content || [];
      totalPages = data.totalPages || 0;

      // Client-side filtering for dropdown filters
      const selectedRole = roleFilter?.value || '';
      const selectedStage = stageFilter?.value || '';
      const selectedDept = deptFilter?.value || '';
      const selectedStatus = statusFilter?.value || '';

      // Filter out system login accounts from Employee Directory page
      const SYSTEM_ACCOUNTS = ['EMP-001', 'EMP-002', 'EMP-003', 'EMP-004', '9000000001', '9000000002', '9000000003', '9000000004'];
      let filteredItems = items.filter(e => !SYSTEM_ACCOUNTS.includes(e.employeeCode) && !SYSTEM_ACCOUNTS.includes(e.mobileNumber));

      if (selectedStage) {
        filteredItems = filteredItems.filter(e => (e.stage || '').toUpperCase() === selectedStage.toUpperCase());
      }
      if (selectedRole) {
        filteredItems = filteredItems.filter(e => String(e.roleId) === selectedRole);
      }
      if (selectedDept) {
        filteredItems = filteredItems.filter(e => String(e.departmentId) === selectedDept);
      }
      if (selectedStatus) {
        filteredItems = filteredItems.filter(e => String(e.isActive) === selectedStatus);
      }

      renderTable(filteredItems);
      // Use filteredItems.length when client-side filters are active to show the correct visible count
      const hasActiveFilters = selectedRole || selectedDept || selectedStatus || selectedStage;
      renderPagination(hasActiveFilters ? filteredItems.length : (data.totalElements || filteredItems.length));

    } catch (err) {
      tbody.innerHTML = `
        <tr>
          <td colspan="6">
            <div class="empty-state">
              <div class="empty-state-title">Error loading employees</div>
              <div class="empty-state-desc">${err.message || 'Please try again later'}</div>
            </div>
          </td>
        </tr>
      `;
    }
  }

  function renderTable(employees) {
    if (!employees || employees.length === 0) {
      tbody.innerHTML = `
        <tr>
          <td colspan="6">
            <div class="empty-state">
              <div class="empty-state-title">No employees found</div>
              <div class="empty-state-desc">Try clearing filters or adding a new employee.</div>
            </div>
          </td>
        </tr>
      `;
      return;
    }

    tbody.innerHTML = employees.map(emp => {
      const stageBadge = emp.stage ? StageRegistry.getBadgeHtml(emp.stage) : '<span style="color:var(--text-muted);">—</span>';

      return `
        <tr>
          <td><span class="emp-code-badge">${emp.employeeCode}</span></td>
          <td>
            <div style="font-weight:var(--weight-medium); color:var(--text-primary);">${emp.fullName}</div>
          </td>
          <td>${emp.mobileNumber}</td>
          <td>${stageBadge}</td>
          <td>${emp.department || '—'}</td>
          <td style="text-align:right;">
            <div class="table-actions" style="justify-content: flex-end;">
              <button class="btn btn-ghost btn-sm" onclick="handleView(${emp.id})" title="View Employee Details">View</button>
              ${isAdmin ? `
                <button class="btn btn-ghost btn-sm" onclick="handleEdit(${emp.id})" title="Edit">Edit</button>
                <button class="btn btn-ghost btn-sm ${emp.isActive ? 'btn-danger' : ''}" onclick="handleToggleStatus(${emp.id}, ${emp.isActive})" title="${emp.isActive ? 'Deactivate' : 'Activate'}">
                  ${emp.isActive ? 'Deactivate' : 'Activate'}
                </button>
              ` : ''}
            </div>
          </td>
        </tr>
      `;
    }).join('');
  }

  function renderPagination(totalElements) {
    const info = document.getElementById('paginationInfo');
    const controls = document.getElementById('paginationControls');

    if (info) {
      info.textContent = `Total ${totalElements} employee${totalElements === 1 ? '' : 's'}`;
    }

    if (!controls) return;

    if (totalPages <= 1) {
      controls.innerHTML = '';
      return;
    }

    let buttonsHtml = `
      <button class="pagination-btn" ${currentPage === 0 ? 'disabled' : ''} onclick="changePage(${currentPage - 1})">&laquo;</button>
    `;

    for (let i = 0; i < totalPages; i++) {
      buttonsHtml += `
        <button class="pagination-btn ${i === currentPage ? 'active' : ''}" onclick="changePage(${i})">${i + 1}</button>
      `;
    }

    buttonsHtml += `
      <button class="pagination-btn" ${currentPage === totalPages - 1 ? 'disabled' : ''} onclick="changePage(${currentPage + 1})">&raquo;</button>
    `;

    controls.innerHTML = buttonsHtml;
  }

  window.changePage = (page) => {
    if (page < 0 || page >= totalPages) return;
    currentPage = page;
    loadEmployees();
  };

  // ── Modal Actions ─────────────────────────────────────────────────────────

  function openCreateModal() {
    isEditing = false;
    modalTitle.textContent = 'Add New Employee';
    saveBtnText.textContent = 'Save Employee';

    clearForm();
    if (advanceInput) advanceInput.value = '';
    if (joiningDateInput) joiningDateInput.value = new Date().toISOString().split('T')[0];
    if (passwordRow) passwordRow.style.display = 'grid';

    modalBackdrop.classList.remove('hidden');
    fullNameInput.focus();
  }

  window.handleEdit = async (id) => {
    try {
      const emp = await Api.get(`${API.EMPLOYEES}/${id}`);
      isEditing = true;
      modalTitle.textContent = `Edit Employee — ${emp.employeeCode}`;
      saveBtnText.textContent = 'Update Employee';

      clearForm();

      employeeIdInput.value = emp.id;
      fullNameInput.value = emp.fullName || '';
      mobileInput.value = emp.mobileNumber || '';
      if (emailInput) emailInput.value = emp.email || '';
      if (deptSelect) deptSelect.value = emp.departmentId || '';
      if (stageSelect) stageSelect.value = emp.stage || '';
      if (advanceInput) advanceInput.value = emp.advance != null ? emp.advance : 0;
      if (joiningDateInput) joiningDateInput.value = emp.joiningDate || '';

      // Password not editable in edit modal
      if (passwordRow) passwordRow.style.display = 'none';

      modalBackdrop.classList.remove('hidden');
      fullNameInput.focus();

    } catch (err) {
      Toast.error('Failed to load employee details');
    }
  };

  window.handleView = async (id) => {
    try {
      const emp = await Api.get(`${API.EMPLOYEES}/${id}`);
      const viewBackdrop = document.getElementById('viewEmployeeModalBackdrop');
      if (!viewBackdrop) return;

      const codeEl = document.getElementById('viewModalCode');
      if (codeEl) codeEl.textContent = emp.employeeCode || '';

      const nameEl = document.getElementById('viewFullName');
      if (nameEl) nameEl.textContent = emp.fullName || '—';

      const avatarEl = document.getElementById('viewAvatar');
      if (avatarEl) avatarEl.textContent = Utils.initials(emp.fullName || 'E');

      const roleEl = document.getElementById('viewRoleLabel');
      if (roleEl) roleEl.textContent = emp.roleLabel || emp.role || 'Staff Member';

      const mobileEl = document.getElementById('viewMobile');
      if (mobileEl) mobileEl.textContent = emp.mobileNumber || '—';

      const deptEl = document.getElementById('viewDept');
      if (deptEl) deptEl.textContent = emp.department || '—';

      const stageEl = document.getElementById('viewStage');
      if (stageEl) {
        stageEl.innerHTML = emp.stage ? StageRegistry.getBadgeHtml(emp.stage) : '<span style="color:var(--text-muted);">—</span>';
      }

      const emailEl = document.getElementById('viewEmail');
      if (emailEl) emailEl.textContent = emp.email || '—';

      const advanceEl = document.getElementById('viewAdvance');
      if (advanceEl) advanceEl.textContent = `₹${Number(emp.advance || 0).toLocaleString('en-IN')}`;

      const joinEl = document.getElementById('viewJoiningDate');
      if (joinEl) joinEl.textContent = emp.joiningDate ? Utils.formatDate(emp.joiningDate) : 'Not specified';

      const statusEl = document.getElementById('viewStatusBadge');
      if (statusEl) {
        statusEl.innerHTML = `
          <span class="badge ${emp.isActive ? 'badge-completed' : 'badge-neutral'}">
            ${emp.isActive ? 'Active' : 'Inactive'}
          </span>
        `;
      }

      const editBtn = document.getElementById('viewModalEditBtn');
      if (editBtn) {
        if (isAdmin) {
          editBtn.style.display = 'inline-flex';
          editBtn.onclick = () => {
            closeViewModal();
            handleEdit(emp.id);
          };
        } else {
          editBtn.style.display = 'none';
        }
      }

      viewBackdrop.classList.remove('hidden');
    } catch (err) {
      Toast.error('Failed to load employee details');
    }
  };

  function closeViewModal() {
    const viewBackdrop = document.getElementById('viewEmployeeModalBackdrop');
    if (viewBackdrop) viewBackdrop.classList.add('hidden');
  }

  window.handleToggleStatus = (id, currentStatus) => {
    const action = currentStatus ? 'deactivate' : 'activate';
    Modal.confirm(
      `Are you sure you want to ${action} this employee?`,
      async () => {
        try {
          await Api.patch(`${API.EMPLOYEES}/${id}/status`);
          Toast.success(`Employee ${action}d successfully`);
          loadEmployees();
        } catch (err) {
          Toast.error(err.message || `Failed to ${action} employee`);
        }
      },
      {
        title: `${action.charAt(0).toUpperCase() + action.slice(1)} Employee`,
        confirmText: action.charAt(0).toUpperCase() + action.slice(1),
        danger: currentStatus
      }
    );
  };

  function closeModal() {
    modalBackdrop.classList.add('hidden');
    clearForm();
  }

  function clearForm() {
    employeeForm.reset();
    employeeIdInput.value = '';
    if (advanceInput) advanceInput.value = '';
    if (joiningDateInput) joiningDateInput.value = '';
    Validation.clearForm(employeeForm);
  }

  function validateForm() {
    Validation.clearForm(employeeForm);
    let valid = true;

    // Full Name
    const nameErr = Validation.required(fullNameInput.value, 'Full name');
    if (nameErr) { Validation.setFieldError(fullNameInput, nameErr); valid = false; }

    // Mobile
    const mobileErr = Validation.mobile(mobileInput.value);
    if (mobileErr) { Validation.setFieldError(mobileInput, mobileErr); valid = false; }

    return valid;
  }

  async function createEmployee() {
    const rawName = fullNameInput.value.trim();

    // Generate a secure random temp password (admin should share this with the employee)
    const chars = 'ABCDEFGHJKMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789@#!';
    const tempPassword = Array.from({ length: 10 }, () => chars[Math.floor(Math.random() * chars.length)]).join('');

    const payload = {
      fullName: rawName,
      mobileNumber: mobileInput.value.trim(),
      email: emailInput?.value?.trim() || null,
      password: tempPassword,
      departmentId: deptSelect?.value ? Number(deptSelect.value) : null,
      stage: stageSelect?.value || null,
      advance: advanceInput?.value ? parseInt(advanceInput.value, 10) : 0,
      joiningDate: joiningDateInput?.value || null
    };

    const saveBtn = document.getElementById('saveEmployeeBtn');
    Utils.setLoading(saveBtn, true, 'Saving...');

    try {
      await Api.post(API.EMPLOYEES, payload);
      closeModal();
      await loadEmployees();
      // Show temp credentials to admin so they can share with the new employee
      Toast.success(`Employee created — Mobile: ${payload.mobileNumber} | Temp Password: ${tempPassword}`, 10000);
    } catch (err) {
      const msg = err.message || 'Failed to create employee';
      Toast.error(msg);
      if (msg.toLowerCase().includes('mobile')) {
        Validation.setFieldError(mobileInput, msg);
      }
    } finally {
      Utils.setLoading(saveBtn, false);
    }
  }


  async function updateEmployee() {
    const id = employeeIdInput.value;
    const payload = {
      fullName: fullNameInput.value.trim(),
      mobileNumber: mobileInput.value.trim(),
      email: emailInput?.value?.trim() || null,
      departmentId: deptSelect?.value ? Number(deptSelect.value) : null,
      stage: stageSelect?.value || null,
      advance: advanceInput?.value ? parseInt(advanceInput.value, 10) : 0,
      joiningDate: joiningDateInput?.value || null
    };

    const saveBtn = document.getElementById('saveEmployeeBtn');
    Utils.setLoading(saveBtn, true, 'Updating...');

    try {
      await Api.put(`${API.EMPLOYEES}/${id}`, payload);
      Toast.success('Employee updated successfully');
      closeModal();
      loadEmployees();
    } catch (err) {
      Toast.error(err.message || 'Failed to update employee');
    } finally {
      Utils.setLoading(saveBtn, false);
    }
  }

});
