/**
 * Ritham ERP — Company Branches Management Controller
 */

'use strict';

document.addEventListener('DOMContentLoaded', () => {
  // Guard access: Admin only
  if (!Router.protect([ROLES.ADMIN])) return;

  // Initialize Shell
  Header.init({
    title: 'Company Branches',
    subtitle: 'Manage branch locations & floor staff',
  });
  Sidebar.init({ activePage: 'branch' });

  // State
  let branchesList = [];

  // DOM Elements
  const branchesTableBody   = document.getElementById('branchesTableBody');
  const branchCountBadge    = document.getElementById('branchCountBadge');
  const statTotalBranches   = document.getElementById('statTotalBranches');
  const statActiveBranches  = document.getElementById('statActiveBranches');
  const statTotalWorkers    = document.getElementById('statTotalWorkers');
  const statTotalOrders     = document.getElementById('statTotalOrders');
  const branchSearchInput   = document.getElementById('branchSearchInput');
  const refreshBranchesBtn  = document.getElementById('refreshBranchesBtn');
  const addNewBranchBtn     = document.getElementById('addNewBranchBtn');

  // Modal Elements
  const branchModalBackdrop = document.getElementById('branchModalBackdrop');
  const branchModalTitle    = document.getElementById('branchModalTitle');
  const branchModalSubtitle = document.getElementById('branchModalSubtitle');
  const branchForm          = document.getElementById('branchForm');
  const editBranchId        = document.getElementById('editBranchId');
  const branchCodeInput     = document.getElementById('branchCodeInput');
  const branchNameInput     = document.getElementById('branchNameInput');
  const branchCityInput     = document.getElementById('branchCityInput');
  const branchAddressInput  = document.getElementById('branchAddressInput');
  const branchPhoneInput    = document.getElementById('branchPhoneInput');
  const branchEmailInput    = document.getElementById('branchEmailInput');
  const branchGstInput      = document.getElementById('branchGstInput');
  const closeBranchModalBtn = document.getElementById('closeBranchModalBtn');
  const cancelBranchModalBtn= document.getElementById('cancelBranchModalBtn');

  // Load Data
  loadBranches();

  // Event Listeners
  if (refreshBranchesBtn)  refreshBranchesBtn.addEventListener('click', loadBranches);
  if (branchSearchInput)   branchSearchInput.addEventListener('input', renderTable);
  if (addNewBranchBtn)     addNewBranchBtn.addEventListener('click', openCreateModal);
  if (closeBranchModalBtn) closeBranchModalBtn.addEventListener('click', closeModal);
  if (cancelBranchModalBtn)cancelBranchModalBtn.addEventListener('click', closeModal);
  if (branchModalBackdrop) {
    branchModalBackdrop.addEventListener('click', (e) => {
      if (e.target === branchModalBackdrop) closeModal();
    });
  }
  const exportBranchesExcelBtn = document.getElementById('exportBranchesExcelBtn');
  if (exportBranchesExcelBtn) {
    exportBranchesExcelBtn.addEventListener('click', () => {
      if (!branchesList || branchesList.length === 0) {
        Toast.warning('No branches found to export');
        return;
      }
      const data = branchesList.map((b, idx) => ({
        'S.No': idx + 1,
        'Branch Code': b.branchCode || '—',
        'Branch Name': b.name || '—',
        'City': b.city || '—',
        'Address': b.address || '—',
        'Phone': b.phone || '—',
        'Email': b.email || '—',
        'GST Number': b.gstNumber || '—',
        'Status': b.isActive !== false ? 'Active' : 'Inactive',
        'Created Date': ExcelExport.formatDate(b.createdAt)
      }));
      ExcelExport.exportData(data, {
        filename: 'Ritham_Company_Branches',
        sheetName: 'Branches',
        columnWidths: [8, 14, 25, 18, 32, 16, 25, 18, 12, 18]
      });
    });
  }

  if (branchForm) branchForm.addEventListener('submit', handleFormSubmit);

  async function loadBranches() {
    try {
      branchesTableBody.innerHTML = `
        <tr>
          <td colspan="6" style="padding:40px; text-align:center; color:var(--text-muted);">
            Loading company branches...
          </td>
        </tr>
      `;

      const res = await Api.get(API.BRANCHES);
      branchesList = res.data || res || [];
      renderStats();
      renderTable();
    } catch (err) {
      Toast.error(err.message || 'Failed to load company branches');
      branchesTableBody.innerHTML = `
        <tr>
          <td colspan="6" style="padding:40px; text-align:center; color:var(--color-danger,#ef4444);">
            Failed to load company branches. Please check backend connection.
          </td>
        </tr>
      `;
    }
  }

  function renderStats() {
    const total = branchesList.length;
    if (branchCountBadge) branchCountBadge.textContent = `${total} Branches`;
    const totalCountText = document.getElementById('totalCountText');
    if (totalCountText) totalCountText.textContent = `Total ${total} company branches`;
  }

  function renderTable() {
    const q = (branchSearchInput?.value || '').trim().toLowerCase();
    const activeBranchId = Storage.getActiveBranchId();

    const filtered = branchesList.filter(b => {
      if (q) {
        const matchName = (b.name || '').toLowerCase().includes(q);
        const matchCode = (b.branchCode || '').toLowerCase().includes(q);
        const matchCity = (b.city || '').toLowerCase().includes(q);
        const matchPhone= (b.phone || '').includes(q);
        const matchGst  = (b.gstNumber || '').toLowerCase().includes(q);
        if (!matchName && !matchCode && !matchCity && !matchPhone && !matchGst) {
          return false;
        }
      }
      return true;
    });

    const totalCountText = document.getElementById('totalCountText');
    if (totalCountText) totalCountText.textContent = `Showing ${filtered.length} of ${branchesList.length} company branches`;

    if (filtered.length === 0) {
      branchesTableBody.innerHTML = `
        <tr>
          <td colspan="6" style="padding:40px; text-align:center; color:var(--text-muted);">
            No company branches match your search criteria.
          </td>
        </tr>
      `;
      return;
    }

    branchesTableBody.innerHTML = filtered.map(b => {
      const isCurrentContext = String(activeBranchId) === String(b.id);

      return `
        <tr class="${isCurrentContext ? 'branch-row-active' : ''}">
          <td>
            <span class="branch-code-badge">${b.branchCode}</span>
          </td>

          <td>
            <div class="branch-title-wrap">
              <div class="branch-name">
                ${b.name}
                ${isCurrentContext ? '<span class="badge badge-primary" style="font-size:10px;">Active Context</span>' : ''}
              </div>
              <div class="branch-address">
                ${b.city ? `<b>${b.city}</b> • ` : ''}${b.address || 'Address not configured'}
              </div>
            </div>
          </td>

          <td>
            <div class="branch-contact-wrap">
              <div class="branch-contact-phone">
                ${b.phone ? `📞 ${b.phone}` : '<span style="color:var(--text-muted);">No phone</span>'}
              </div>
              <div class="branch-contact-gst">
                ${b.gstNumber ? `GST: ${b.gstNumber}` : 'GST: N/A'}
              </div>
            </div>
          </td>

          <td style="text-align:center;">
            <span class="branch-count-chip accent">
              ${b.employeeCount || 0} Staff
            </span>
          </td>

          <td style="text-align:center;">
            <span class="branch-count-chip">
              ${b.orderCount || 0} Orders
            </span>
          </td>

          <td style="text-align:right;">
            <div class="branch-actions-wrap">
              ${!isCurrentContext ? `
                <button class="btn btn-outline btn-sm" onclick="window.switchToBranch(${b.id}, '${b.name.replace(/'/g, "\\'")}')" title="Switch active branch to this location">
                  Switch
                </button>
              ` : `
                <span class="badge badge-primary" style="font-size:11px; padding:4px 10px;">
                  Active
                </span>
              `}
              <button class="btn btn-ghost btn-sm" onclick="window.openEditModal(${b.id})" title="Edit Branch">
                Edit
              </button>
            </div>
          </td>
        </tr>
      `;
    }).join('');
  }

  // Window actions
  window.switchToBranch = (branchId, branchName) => {
    Storage.setActiveBranchId(branchId, branchName);
    Toast.success(`Switched active branch to: ${branchName}`);
    setTimeout(() => {
      window.location.reload();
    }, 500);
  };

  window.openEditModal = (id) => {
    const branch = branchesList.find(b => b.id === id);
    if (!branch) return;

    editBranchId.value = branch.id;
    branchCodeInput.value = branch.branchCode;
    branchCodeInput.disabled = true; // Code cannot be edited
    branchNameInput.value = branch.name || '';
    branchCityInput.value = branch.city || '';
    branchAddressInput.value = branch.address || '';
    branchPhoneInput.value = branch.phone || '';
    branchEmailInput.value = branch.email || '';
    branchGstInput.value = branch.gstNumber || '';

    branchModalTitle.textContent = 'Edit Company Branch';
    branchModalSubtitle.textContent = `Update details for ${branch.name}`;
    branchModalBackdrop.classList.remove('hidden');
  };

  window.toggleBranchStatus = async (id) => {
    try {
      await Api.patch(`/api/branches/${id}/toggle-status`);
      Toast.success('Branch status updated successfully');
      loadBranches();
    } catch (err) {
      Toast.error(err.message || 'Failed to update branch status');
    }
  };

  function openCreateModal() {
    branchForm.reset();
    editBranchId.value = '';
    branchCodeInput.disabled = false;
    branchModalTitle.textContent = 'Add New Branch';
    branchModalSubtitle.textContent = 'Create a new company branch location';
    branchModalBackdrop.classList.remove('hidden');
    branchCodeInput.focus();
  }

  function closeModal() {
    branchModalBackdrop.classList.add('hidden');
    branchForm.reset();
  }

  async function handleFormSubmit(e) {
    e.preventDefault();

    const isEdit = !!editBranchId.value;
    const body = {
      name:       branchNameInput.value.trim(),
      city:       branchCityInput.value.trim(),
      address:    branchAddressInput.value.trim(),
      phone:      branchPhoneInput.value.trim(),
      email:      branchEmailInput.value.trim(),
      gstNumber:  branchGstInput.value.trim().toUpperCase(),
    };

    try {
      if (isEdit) {
        await Api.put(API.BRANCH(editBranchId.value), body);
        Toast.success('Branch updated successfully');
      } else {
        body.branchCode = branchCodeInput.value.trim().toUpperCase();
        await Api.post(API.BRANCHES, body);
        Toast.success('New branch created successfully');
      }

      closeModal();
      loadBranches();
    } catch (err) {
      Toast.error(err.message || 'Failed to save branch');
    }
  }
});
