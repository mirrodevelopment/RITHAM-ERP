/**
 * Ritham ERP — Customer Management Controller (customer.js)
 *
 * Features:
 *  - Load paginated customer list from GET /api/customers
 *  - Search (debounced) by name or mobile
 *  - Add new customer via POST /api/customers
 *  - Pagination controls (prev / page numbers / next)
 *  - Row #, Avatar initials, Name, Mobile, Created date
 */

'use strict';

/* ── Constants ──────────────────────────────────────────────────────── */

const PAGE_SIZE  = 20;
const SEARCH_DEBOUNCE_MS = 400;

/* ── State ──────────────────────────────────────────────────────────── */

let currentPage   = 0;
let currentSearch = '';
let totalPages    = 0;
let totalElements = 0;
let searchTimer   = null;

/* ── DOM refs ───────────────────────────────────────────────────────── */

const tableBody     = document.getElementById('customerTableBody');
const pagination    = document.getElementById('pagination');
const searchInput   = document.getElementById('searchInput');
const countLabel    = document.getElementById('customerCountLabel');
const tableSubtitle = document.getElementById('tableSubtitle');

// Modal
const modalBackdrop  = document.getElementById('customerModalBackdrop');
const modalTitle     = document.getElementById('customerModalTitle');
const fieldMobile    = document.getElementById('fieldMobile');
const fieldName      = document.getElementById('fieldName');
const errorMobile    = document.getElementById('errorMobile');
const errorName      = document.getElementById('errorName');
const saveBtn        = document.getElementById('saveCustomerBtn');

/* ── Helper: avatar initials ────────────────────────────────────────── */

function initials(name) {
  if (!name) return '?';
  const words = name.trim().split(/\s+/);
  return words.length >= 2
    ? (words[0][0] + words[1][0]).toUpperCase()
    : words[0].substring(0, 2).toUpperCase();
}

/* ── Helper: highlight search term ─────────────────────────────────── */

function highlight(text, query) {
  if (!query || !text) return text || '';
  const safe = query.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  return text.replace(new RegExp(safe, 'gi'), m => `<span class="highlight">${m}</span>`);
}

/* ── Load customers ─────────────────────────────────────────────────── */

async function loadCustomers() {
  // Skeleton rows
  renderSkeleton();

  try {
    const params = new URLSearchParams({
      page:    currentPage,
      size:    PAGE_SIZE,
      sortBy:  'createdAt',
      sortDir: 'desc',
    });
    if (currentSearch) params.set('search', currentSearch);

    // Api.get already unwraps ApiResponse.data → PageResponse<CustomerResponse>
    const page = await Api.get(`/api/customers?${params}`);

    totalPages    = page.totalPages   ?? 0;
    totalElements = page.totalElements ?? 0;

    const customers = page.content || [];

    renderTable(customers);
    renderPagination();
    updateLabels();

  } catch (err) {
    renderError(err.message || 'Failed to load customers.');
  }
}

/* ── Render: skeleton rows ──────────────────────────────────────────── */

function renderSkeleton() {
  tableBody.innerHTML = Array.from({ length: 6 }, (_, i) => `
    <tr>
      <td><span class="skeleton skeleton-text" style="width:24px;"></span></td>
      <td>
        <div class="customer-name-cell">
          <span class="skeleton" style="width:32px;height:32px;border-radius:50%;display:inline-block;"></span>
          <span class="skeleton skeleton-text" style="width:140px;"></span>
        </div>
      </td>
      <td><span class="skeleton skeleton-text" style="width:110px;"></span></td>
      <td style="text-align:center;"><span class="skeleton skeleton-text" style="width:36px;margin:0 auto;display:inline-block;"></span></td>
      <td><span class="skeleton skeleton-text" style="width:90px;"></span></td>
      <td style="text-align:right;"><span class="skeleton skeleton-text" style="width:120px;margin-left:auto;display:inline-block;"></span></td>
    </tr>
  `).join('');
}

/* ── Render: customer rows ──────────────────────────────────────────── */

function renderTable(customers) {
  if (!customers.length) {
    tableBody.innerHTML = `
      <tr>
        <td colspan="6">
          <div class="customer-empty">
            <svg class="customer-empty-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
              <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
              <circle cx="9" cy="7" r="4"/>
              <path d="M23 21v-2a4 4 0 0 0-3-3.87"/>
              <path d="M16 3.13a4 4 0 0 1 0 7.75"/>
            </svg>
            <h3>${currentSearch ? 'No customers found' : 'No customers yet'}</h3>
            <p>${currentSearch
                ? `No results for "<strong>${currentSearch}</strong>". Try a different search.`
                : 'Add your first customer using the "New Customer" button above.'}</p>
          </div>
        </td>
      </tr>
    `;
    return;
  }

  const startIndex = currentPage * PAGE_SIZE;
  tableBody.innerHTML = customers.map((c, i) => {
    const idx      = startIndex + i + 1;
    const name     = c.customerName  || '—';
    const mobile   = c.customerMobile || '—';
    const orders   = c.totalOrders   ?? 0;
    const created  = c.createdAt
      ? new Date(c.createdAt).toLocaleDateString('en-IN', { day:'2-digit', month:'short', year:'numeric' })
      : '—';
    const av       = initials(name);
    const hName    = highlight(name,   currentSearch);
    const hMobile  = highlight(mobile, currentSearch);

    const orderBadge = orders > 0
      ? `<span style="
          display:inline-flex;align-items:center;justify-content:center;
          min-width:28px;height:22px;padding:0 8px;
          background:rgba(99,102,241,0.15);
          border:1px solid rgba(99,102,241,0.3);
          border-radius:9999px;
          font-size:11px;font-weight:600;
          color:#818CF8;
        ">${orders}</span>`
      : `<span style="
          display:inline-flex;align-items:center;justify-content:center;
          min-width:28px;height:22px;padding:0 8px;
          background:rgba(255,255,255,0.03);
          border:1px solid var(--border-default);
          border-radius:9999px;
          font-size:11px;font-weight:500;
          color:var(--text-muted);
        ">0</span>`;

    return `
      <tr>
        <td class="text-muted" style="font-size:12px;">${idx}</td>
        <td>
          <div class="customer-name-cell">
            <div class="customer-avatar" title="${name}">${av}</div>
            <span>${hName}</span>
          </div>
        </td>
        <td>
          <span style="font-family:monospace;font-size:13px;">${hMobile}</span>
        </td>
        <td style="text-align:center;">${orderBadge}</td>
        <td class="text-muted" style="font-size:12px;">${created}</td>
        <td style="text-align:right;">
          <div style="display:inline-flex;gap:6px;">
            <button class="btn btn-secondary btn-sm" onclick="window.openCustomerMeasurements('${mobile.replace(/'/g,"\\'")}','${name.replace(/'/g,"\\'")}')" title="Customer Tailoring Measurements">📐 Measurements</button>
            <button class="btn btn-ghost btn-sm" onclick="viewCustomer('${mobile.replace(/'/g,"\\'")}',' ${name.replace(/'/g,"\\'")}')">View</button>
          </div>
        </td>
      </tr>
    `;
  }).join('');
}

/* ── Render: error state ────────────────────────────────────────────── */

function renderError(msg) {
  tableBody.innerHTML = `
    <tr>
      <td colspan="6">
        <div class="customer-empty">
          <svg class="customer-empty-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/>
          </svg>
          <h3>Failed to load customers</h3>
          <p>${msg}</p>
        </div>
      </td>
    </tr>
  `;
}

/* ── Render: pagination ─────────────────────────────────────────────── */

function renderPagination() {
  if (totalPages <= 1) {
    pagination.innerHTML = '';
    return;
  }

  const from = currentPage * PAGE_SIZE + 1;
  const to   = Math.min((currentPage + 1) * PAGE_SIZE, totalElements);

  let pagesHtml = '';

  const start = Math.max(0, currentPage - 2);
  const end   = Math.min(totalPages - 1, currentPage + 2);

  if (start > 0) {
    pagesHtml += `<button class="pagination-btn" onclick="goToPage(0)">1</button>`;
    if (start > 1) pagesHtml += `<span style="color:var(--text-muted);padding:0 4px;">…</span>`;
  }

  for (let p = start; p <= end; p++) {
    pagesHtml += `<button class="pagination-btn${p === currentPage ? ' active' : ''}" onclick="goToPage(${p})">${p + 1}</button>`;
  }

  if (end < totalPages - 1) {
    if (end < totalPages - 2) pagesHtml += `<span style="color:var(--text-muted);padding:0 4px;">…</span>`;
    pagesHtml += `<button class="pagination-btn" onclick="goToPage(${totalPages - 1})">${totalPages}</button>`;
  }

  pagination.innerHTML = `
    <span class="pagination-info">Showing ${from}–${to} of ${totalElements} customers</span>
    <div class="pagination-controls">
      <button class="pagination-btn" id="prevPageBtn" onclick="goToPage(${currentPage - 1})" ${currentPage === 0 ? 'disabled' : ''}>
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="15 18 9 12 15 6"></polyline></svg>
      </button>
      ${pagesHtml}
      <button class="pagination-btn" id="nextPageBtn" onclick="goToPage(${currentPage + 1})" ${currentPage >= totalPages - 1 ? 'disabled' : ''}>
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="9 18 15 12 9 6"></polyline></svg>
      </button>
    </div>
  `;
}

/* ── Labels ─────────────────────────────────────────────────────────── */

function updateLabels() {
  if (currentSearch) {
    countLabel.textContent    = `${totalElements} result${totalElements !== 1 ? 's' : ''} for "${currentSearch}"`;
    tableSubtitle.textContent = `Search results`;
  } else {
    countLabel.textContent    = `${totalElements} customer${totalElements !== 1 ? 's' : ''} registered`;
    tableSubtitle.textContent = 'All registered customers';
  }
}

/* ── Pagination nav ─────────────────────────────────────────────────── */

function goToPage(p) {
  if (p < 0 || p >= totalPages) return;
  currentPage = p;
  loadCustomers();
  window.scrollTo({ top: 0, behavior: 'smooth' });
}

/* ── Search ─────────────────────────────────────────────────────────── */

searchInput.addEventListener('input', () => {
  clearTimeout(searchTimer);
  searchTimer = setTimeout(() => {
    currentSearch = searchInput.value.trim();
    currentPage   = 0;
    loadCustomers();
  }, SEARCH_DEBOUNCE_MS);
});

/* ── View customer & order history modal ────────────────────────────── */

let _currentViewingCustomer = null;

async function viewCustomer(mobile, name) {
  _currentViewingCustomer = { mobile, name };
  const backdrop  = document.getElementById('viewCustomerOrdersModalBackdrop');
  const titleEl   = document.getElementById('viewCustomerNameTitle');
  const mobileEl  = document.getElementById('viewCustomerMobileSub');
  const avatarEl  = document.getElementById('viewCustomerAvatar');
  const summaryEl = document.getElementById('viewCustomerTotalOrdersSummary');
  const tbodyEl   = document.getElementById('viewCustomerOrdersTableBody');

  const cleanName = (name || 'Customer').trim();
  const initials  = cleanName.split(/\s+/).map(n => n[0]).join('').toUpperCase().slice(0, 2);

  avatarEl.textContent  = initials || 'CU';
  titleEl.textContent   = cleanName;
  mobileEl.textContent  = mobile;
  summaryEl.textContent = 'Loading orders…';

  tbodyEl.innerHTML = `
    <tr>
      <td colspan="5" style="padding:24px;text-align:center;color:var(--text-muted);font-size:12px;">
        Loading customer orders…
      </td>
    </tr>
  `;

  backdrop.classList.remove('hidden');

  try {
    const data = await Api.get(`${API.ORDERS}?search=${encodeURIComponent(mobile)}&size=50`);
    const orders = data?.content ?? [];
    summaryEl.textContent = `${orders.length} Order${orders.length !== 1 ? 's' : ''}`;

    if (!orders.length) {
      tbodyEl.innerHTML = `
        <tr>
          <td colspan="5" style="padding:32px;text-align:center;color:var(--text-muted);font-size:12px;">
            No orders found for this customer.
          </td>
        </tr>
      `;
      return;
    }

    const statusBadge = (s) => {
      if (typeof StageRegistry !== 'undefined') {
        const badge = StageRegistry.getBadgeHtml(s);
        if (badge) return badge;
      }
      const label = (s || '').replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, l => l.toUpperCase());
      return `<span style="padding:2px 8px;border-radius:10px;font-size:10px;font-weight:600;background:rgba(99,102,241,0.12);color:#818CF8;border:1px solid rgba(99,102,241,0.25);">${label}</span>`;
    };

    const payBadge = (p) => {
      const map = {
        PAID:    '<span style="padding:2px 8px;border-radius:10px;font-size:10px;font-weight:600;background:rgba(16,185,129,0.12);color:#10B981;border:1px solid rgba(16,185,129,0.25);">Paid</span>',
        PARTIAL: '<span style="padding:2px 8px;border-radius:10px;font-size:10px;font-weight:600;background:rgba(245,158,11,0.12);color:#F59E0B;border:1px solid rgba(245,158,11,0.25);">Partial</span>',
        PENDING: '<span style="padding:2px 8px;border-radius:10px;font-size:10px;font-weight:600;background:rgba(239,68,68,0.12);color:#EF4444;border:1px solid rgba(239,68,68,0.25);">Unpaid</span>',
      };
      return map[p] || `<span style="font-size:10px;">${p}</span>`;
    };

    const isOps = (Auth.getRole() === ROLES.OPERATIONS_MANAGER || Auth.getRole() === 'ROLE_OPERATIONS_MANAGER' || Auth.getRole() === 'OPERATIONS_MANAGER');
    const amtHead = document.getElementById('colCustOrderAmount');
    const payHead = document.getElementById('colCustOrderPayment');
    if (amtHead) amtHead.style.display = isOps ? 'none' : '';
    if (payHead) payHead.style.display = isOps ? 'none' : '';

    tbodyEl.innerHTML = orders.map(o => {
      const dateStr = o.deliveryDate ? Utils.formatDate(o.deliveryDate) : '—';
      const amt     = o.totalAmount != null ? `₹${Number(o.totalAmount).toLocaleString('en-IN')}` : '₹0';
      return `
        <tr style="border-bottom:1px solid var(--border-subtle, rgba(255,255,255,0.04));">
          <td style="padding:10px 12px;font-family:monospace;font-size:12px;color:var(--text-secondary);">${o.orderNumber || '#'+o.id}</td>
          <td style="padding:10px 12px;font-size:12px;color:var(--text-muted);">${dateStr}</td>
          ${!isOps ? `<td style="padding:10px 12px;font-size:12px;font-weight:600;text-align:right;color:var(--text-primary);">${amt}</td>` : ''}
          <td style="padding:10px 12px;text-align:center;">${statusBadge(o.status)}</td>
          ${!isOps ? `<td style="padding:10px 12px;text-align:center;">${payBadge(o.paymentStatus)}</td>` : ''}
        </tr>
      `;
    }).join('');

  } catch (err) {
    summaryEl.textContent = '0 Orders';
    tbodyEl.innerHTML = `
      <tr>
        <td colspan="5" style="padding:24px;text-align:center;color:#EF4444;font-size:12px;">
          Failed to load orders for this customer.
        </td>
      </tr>
    `;
  }
}

function closeViewCustomerOrdersModal() {
  document.getElementById('viewCustomerOrdersModalBackdrop')?.classList.add('hidden');
}

// Event listeners for closing & navigating
document.getElementById('closeViewCustomerOrdersModal')?.addEventListener('click', closeViewCustomerOrdersModal);
document.getElementById('closeViewCustomerOrdersModalFooter')?.addEventListener('click', closeViewCustomerOrdersModal);
document.getElementById('viewCustomerOrdersModalBackdrop')?.addEventListener('click', (e) => {
  if (e.target === document.getElementById('viewCustomerOrdersModalBackdrop')) closeViewCustomerOrdersModal();
});

document.getElementById('viewCustomerCreateOrderBtn')?.addEventListener('click', () => {
  if (_currentViewingCustomer) {
    window.location.href = `../order/order.html?action=new&mobile=${encodeURIComponent(_currentViewingCustomer.mobile)}`;
  }
});

document.getElementById('viewCustomerEditBtn')?.addEventListener('click', () => {
  if (!_currentViewingCustomer) return;
  const currentName = _currentViewingCustomer.name;
  const mobile = _currentViewingCustomer.mobile;

  Modal.open({
    title: 'Edit Customer Name',
    body: `
      <div>
        <label style="display:block;font-size:12px;font-weight:600;margin-bottom:6px;color:var(--text-secondary);">
          Customer Name <span style="color:#EF4444;">*</span>
        </label>
        <input type="text" id="editCustomerNameInput" value="${currentName.replace(/"/g, '&quot;')}" style="width:100%;height:38px;padding:8px 12px;font-size:13px;border-radius:6px;border:1px solid var(--border-default);background:var(--bg-surface-2);color:var(--text-primary);">
        <p style="font-size:11px;color:var(--text-muted);margin-top:6px;">Mobile number: <strong style="color:var(--text-primary);">${mobile}</strong> (Mobile cannot be changed)</p>
      </div>
    `,
    footer: `
      <button class="btn btn-secondary" id="cancelEditCustomerBtn">Cancel</button>
      <button class="btn btn-primary" id="saveEditCustomerBtn">Save Changes</button>
    `,
    size: 'sm'
  });

  const inputEl = document.getElementById('editCustomerNameInput');
  setTimeout(() => {
    inputEl?.focus();
    inputEl?.select();
  }, 100);

  document.getElementById('cancelEditCustomerBtn')?.addEventListener('click', () => Modal.close());

  document.getElementById('saveEditCustomerBtn')?.addEventListener('click', async () => {
    const newName = inputEl?.value.trim();
    if (!newName) {
      Toast.error('Customer name cannot be empty');
      return;
    }
    try {
      await Api.put(`${API.CUSTOMERS}/${encodeURIComponent(mobile)}`, { customerName: newName });
      Toast.success('Customer name updated successfully!');
      _currentViewingCustomer.name = newName;
      document.getElementById('viewCustomerNameTitle').textContent = newName;
      document.getElementById('viewCustomerAvatar').textContent = newName.split(/\s+/).map(n => n[0]).join('').toUpperCase().slice(0, 2);
      Modal.close();
      await loadCustomers();
    } catch (err) {
      Toast.error(err.message || 'Failed to update customer');
    }
  });
});

document.getElementById('viewCustomerAllOrdersBtn')?.addEventListener('click', () => {
  if (_currentViewingCustomer) {
    window.location.href = `../order/order.html?search=${encodeURIComponent(_currentViewingCustomer.mobile)}`;
  }
});

/* ── Modal: open / close ────────────────────────────────────────────── */

function openAddModal() {
  modalTitle.textContent   = 'New Customer';
  fieldMobile.value        = '';
  fieldName.value          = '';
  fieldMobile.disabled     = false;
  clearFieldError(fieldMobile, errorMobile);
  clearFieldError(fieldName,   errorName);
  modalBackdrop.classList.remove('hidden');
  setTimeout(() => fieldMobile.focus(), 50);
}

function closeModal() {
  modalBackdrop.classList.add('hidden');
}

document.getElementById('addCustomerBtn').addEventListener('click', openAddModal);
document.getElementById('closeCustomerModal').addEventListener('click', closeModal);
document.getElementById('cancelCustomerBtn').addEventListener('click', closeModal);
modalBackdrop.addEventListener('click', e => { if (e.target === modalBackdrop) closeModal(); });

/* ── Form validation ────────────────────────────────────────────────── */

function setFieldError(input, errorEl, msg) {
  input.classList.add('input-error');
  errorEl.textContent = msg;
  errorEl.classList.remove('hidden');
}

function clearFieldError(input, errorEl) {
  input.classList.remove('input-error');
  errorEl.textContent = '';
  errorEl.classList.add('hidden');
}

function validateForm() {
  let valid = true;
  const mobile = fieldMobile.value.trim();
  const name   = fieldName.value.trim();

  clearFieldError(fieldMobile, errorMobile);
  clearFieldError(fieldName,   errorName);

  if (!mobile) {
    setFieldError(fieldMobile, errorMobile, 'Mobile number is required.');
    valid = false;
  } else if (!/^[6-9]\d{9}$/.test(mobile)) {
    setFieldError(fieldMobile, errorMobile, 'Please enter a valid 10-digit Indian mobile number.');
    valid = false;
  }

  if (!name) {
    setFieldError(fieldName, errorName, 'Customer name is required.');
    valid = false;
  } else if (name.length < 2) {
    setFieldError(fieldName, errorName, 'Name must be at least 2 characters.');
    valid = false;
  }

  return valid;
}

/* ── Save customer ──────────────────────────────────────────────────── */

saveBtn.addEventListener('click', async () => {
  if (!validateForm()) return;

  const payload = {
    customerMobile: fieldMobile.value.trim(),
    customerName:   fieldName.value.trim(),
  };

  saveBtn.disabled    = true;
  saveBtn.textContent = 'Saving…';

  try {
    await Api.post(API.CUSTOMERS, payload);
    Toast.show('✅ Customer created successfully!', 'success');
    closeModal();
    currentPage   = 0;
    currentSearch = '';
    searchInput.value = '';
    loadCustomers();
  } catch (err) {
    Toast.show(err.message || 'Failed to create customer.', 'error');
  } finally {
    saveBtn.disabled  = false;
    saveBtn.innerHTML = `
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
        <polyline points="20 6 9 17 4 12"></polyline>
      </svg>
      Save Customer`;
  }
});

/* ── Allow Enter key in form ────────────────────────────────────────── */

document.getElementById('customerForm').addEventListener('keydown', e => {
  if (e.key === 'Enter') {
    e.preventDefault();
    saveBtn.click();
  }
});

/* ── Refresh button ─────────────────────────────────────────────────── */

document.getElementById('refreshBtn').addEventListener('click', () => {
  loadCustomers();
});

/* ── Customer Measurements Modal Logic ──────────────────────────────── */

let _activeCustMobile = null;
let _activeCustName   = null;

const msrModalBackdrop = document.getElementById('customerMeasurementsModalBackdrop');
const msrNameTitle     = document.getElementById('msrCustNameTitle');
const msrMobileSub     = document.getElementById('msrCustMobileSub');
const msrGarmentSelect = document.getElementById('custMsrGarmentType');
const msrNotesInput    = document.getElementById('custMsrNotes');
const msrBlousePanel   = document.getElementById('custMsrBlousePanel');
const msrChudiPanel    = document.getElementById('custMsrChudiPanel');
const saveCustMsrBtn   = document.getElementById('saveCustMsrBtn');
const closeCustMsrBtn  = document.getElementById('closeCustomerMeasurementsModal');
const closeCustMsrBtn2 = document.getElementById('closeCustMsrModalBtn');

function closeCustomerMeasurements() {
  msrModalBackdrop?.classList.add('hidden');
}

closeCustMsrBtn?.addEventListener('click', closeCustomerMeasurements);
closeCustMsrBtn2?.addEventListener('click', closeCustomerMeasurements);
msrModalBackdrop?.addEventListener('click', e => {
  if (e.target === msrModalBackdrop) closeCustomerMeasurements();
});

function toggleMsrPanels() {
  const isBlouse = msrGarmentSelect.value === 'BLOUSE';
  if (msrBlousePanel) msrBlousePanel.style.display = isBlouse ? 'block' : 'none';
  if (msrChudiPanel)  msrChudiPanel.style.display  = isBlouse ? 'none' : 'block';
}

msrGarmentSelect?.addEventListener('change', () => {
  toggleMsrPanels();
  if (_activeCustMobile) {
    loadSavedSpecsForGarment(_activeCustMobile, msrGarmentSelect.value);
  }
});

async function loadSavedSpecsForGarment(mobile, garmentType) {
  try {
    const res = await Api.get(`/api/customer-measurements/customer/${mobile}/garment/${garmentType}`);
    const record = res?.data ?? res;
    clearMsrInputs();
    if (record && record.measurements) {
      const cleanNote = (record.notes && !record.notes.includes('Order #') && !record.notes.includes('Auto-synced')) ? record.notes : '';
      if (msrNotesInput) msrNotesInput.value = cleanNote;
      const selector = garmentType === 'BLOUSE'
        ? '#custMsrBlousePanel input[data-garment="blouse"]'
        : '#custMsrChudiPanel input[data-garment="chudi"]';
      document.querySelectorAll(selector).forEach(inp => {
        const k = inp.getAttribute('data-key');
        inp.value = record.measurements[k] || '';
      });
    }
  } catch (_) {}
}

function clearMsrInputs() {
  document.querySelectorAll('#customerMeasurementsModalBackdrop input[data-key]').forEach(inp => inp.value = '');
}

window.openCustomerMeasurements = async function(mobile, name) {
  _activeCustMobile = mobile;
  _activeCustName   = (name || 'Customer').trim();

  if (msrNameTitle) msrNameTitle.textContent = `${_activeCustName} — Sizing Specs`;
  if (msrMobileSub) msrMobileSub.textContent = `📱 ${_activeCustMobile}`;

  clearMsrInputs();
  if (msrGarmentSelect) msrGarmentSelect.value = 'BLOUSE';
  if (msrNotesInput) msrNotesInput.value = '';
  toggleMsrPanels();

  msrModalBackdrop?.classList.remove('hidden');

  try {
    const res = await Api.get(`/api/customer-measurements/customer/${mobile}/latest`);
    const record = res?.data ?? res;
    if (record && record.garmentType && record.measurements) {
      if (msrGarmentSelect) msrGarmentSelect.value = record.garmentType;
      toggleMsrPanels();
      const cleanNote = (record.notes && !record.notes.includes('Order #') && !record.notes.includes('Auto-synced')) ? record.notes : '';
      if (msrNotesInput) msrNotesInput.value = cleanNote;
      const selector = record.garmentType === 'BLOUSE'
        ? '#custMsrBlousePanel input[data-garment="blouse"]'
        : '#custMsrChudiPanel input[data-garment="chudi"]';
      document.querySelectorAll(selector).forEach(inp => {
        const k = inp.getAttribute('data-key');
        inp.value = record.measurements[k] || '';
      });
    }
  } catch (_) {}
};

document.getElementById('viewCustomerMeasurementsModalBtn')?.addEventListener('click', () => {
  if (_currentViewingCustomer) {
    window.openCustomerMeasurements(_currentViewingCustomer.mobile, _currentViewingCustomer.name);
  }
});

saveCustMsrBtn?.addEventListener('click', async () => {
  if (!_activeCustMobile) return;

  const garment = msrGarmentSelect.value;
  const rawNotes = msrNotesInput.value.trim();
  const notes = (rawNotes && !rawNotes.includes('Order #') && !rawNotes.includes('Auto-synced')) ? rawNotes : null;

  const measurements = {};
  const selector = garment === 'BLOUSE'
    ? '#custMsrBlousePanel input[data-garment="blouse"]'
    : '#custMsrChudiPanel input[data-garment="chudi"]';

  document.querySelectorAll(selector).forEach(inp => {
    const v = inp.value.trim();
    const k = inp.getAttribute('data-key');
    if (v && k) measurements[k] = v;
  });

  if (Object.keys(measurements).length === 0) {
    Toast.error('Please fill at least 1 measurement field');
    return;
  }

  const payload = {
    customerMobile: _activeCustMobile,
    customerName: _activeCustName,
    garmentType: garment,
    measurements: measurements,
    notes: notes
  };

  Utils.setLoading(saveCustMsrBtn, true, 'Saving…');
  try {
    await Api.post(API.MEASUREMENTS, payload);
    Toast.success(`Measurements saved for ${_activeCustName}! 📐`);
    closeCustomerMeasurements();
  } catch (err) {
    Toast.error(err.message || 'Failed to save measurements');
  } finally {
    Utils.setLoading(saveCustMsrBtn, false);
  }
});

/* ── Init ───────────────────────────────────────────────────────────── */

document.addEventListener('DOMContentLoaded', async () => {

  // ── Auth Guard ──────────────────────────────────────────────────────
  if (!Router.protect([ROLES.ADMIN, ROLES.RECEPTION, ROLES.OPERATIONS_MANAGER])) return;

  // ── Init Components ─────────────────────────────────────────────────
  Sidebar.init({ activePage: 'customer' });
  Header.init({ title: 'Customers', subtitle: 'Customer Management' });

  if (typeof StageRegistry !== 'undefined') {
    try { await StageRegistry.init(); } catch (_) {}
  }

  // ── Load Data ───────────────────────────────────────────────────────
  await loadCustomers();

  // ── Auto-polling & Focus Auto-refresh ──────────────────────────────────────
  setInterval(loadCustomers, 10000);
  window.addEventListener('focus', loadCustomers);

  // Check URL params to auto-open modal or focus search if navigated from Quick Actions
  const params = new URLSearchParams(window.location.search);
  if (params.get('action') === 'new' || params.has('create') || params.has('new')) {
    openModal();
  } else if (params.get('focus') === 'search') {
    document.getElementById('customerSearch')?.focus();
  }
});

