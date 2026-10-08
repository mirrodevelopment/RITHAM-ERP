/**
 * Ritham ERP — Order Management Controller (order.js)
 *
 * Features:
 *  - Live stats from GET /api/orders/stats
 *  - Paginated order list from GET /api/orders
 *  - Filter tabs (All / Pending / In Progress / Completed / Cancelled)
 *  - Debounced search by order number or mobile
 *  - New Order modal with customer mobile lookup
 *  - Status badges and payment badges
 */

'use strict';

/* ── Constants & Pagination Config ────────────────────────────────────── */
let PAGE_SIZE = 15;
const SEARCH_DEBOUNCE_MS = 400;

/* ── State ────────────────────────────────────────────────────────────── */
let currentPage = 0;
let currentSearch = '';
let currentFilter = '';     // '' = All
let totalPages = 0;
let totalElements = 0;
let searchTimer = null;

/* ── DOM refs ─────────────────────────────────────────────────────────── */
const tableBody = document.getElementById('orderTableBody');
const pagination = document.getElementById('pagination');
const searchInput = document.getElementById('orderSearch');
const countLabel = document.getElementById('orderCountLabel');
const tableSubtitle = document.getElementById('tableSubtitle');

/* ── Status helpers ───────────────────────────────────────────────────── */
const STATUS_MAP = {
  PENDING: { cls: 'badge-pending', label: 'Designing' },
  DESIGNING: { cls: 'badge-pending', label: 'Designing' },
  LINING: { cls: 'badge-inprogress', label: 'Lining' },
  HAND_MACHINE_WORK: { cls: 'badge-inprogress', label: 'Hand / Machine Work' },
  INITIAL_IRONING: { cls: 'badge-inprogress', label: 'Initial Ironing' },
  CUTTING: { cls: 'badge-inprogress', label: 'Cutting' },
  STRETCHING: { cls: 'badge-inprogress', label: 'Stretching' },
  STITCHING: { cls: 'badge-inprogress', label: 'Stitching' },
  HEMMING: { cls: 'badge-inprogress', label: 'Hemming' },
  FINAL_IRONING: { cls: 'badge-inprogress', label: 'Final Ironing' },
  QUALITY_CHECK: { cls: 'badge-inprogress', label: 'Quality Check (QC)' },
  READY_TO_DELIVERY: { cls: 'badge-completed', label: 'Ready to Delivery' },
  DELIVERY: { cls: 'badge-completed', label: 'Delivery' },
  DELIVERED: { cls: 'badge-completed', label: 'Delivered' },
  COMPLETED: { cls: 'badge-completed', label: 'Completed' },
  CANCELLED: { cls: 'badge-cancelled', label: 'Cancelled' },
  // Legacy mappings
  PATTERN_MAKING: { cls: 'badge-pending', label: 'Designing' },
  FABRIC_CUTTING: { cls: 'badge-inprogress', label: 'Cutting' },
  EMBROIDERY: { cls: 'badge-inprogress', label: 'Hand / Machine Work' },
  EMBROIDERY_STITCHING: { cls: 'badge-inprogress', label: 'Hand / Machine Work' },
  MAIN_SEWING: { cls: 'badge-inprogress', label: 'Stitching' },
  BUTTON_FITTING: { cls: 'badge-inprogress', label: 'Hemming' },
  IRONING_PRESS: { cls: 'badge-inprogress', label: 'Final Ironing' },
  FINAL_PACKAGING: { cls: 'badge-completed', label: 'Ready to Delivery' },
  IN_PROGRESS: { cls: 'badge-inprogress', label: 'In Progress' },
};

const PAYMENT_MAP = {
  PENDING: { cls: 'badge-pending', label: 'Unpaid' },
  ADVANCE: { cls: 'badge-inprogress', label: 'Partial' },
  PAID: { cls: 'badge-completed', label: 'Paid' },
  PARTIAL: { cls: 'badge-inprogress', label: 'Partial' },
  REFUNDED: { cls: 'badge-cancelled', label: 'Refunded' }
};

function isOperationsManager() {
  const role = Auth.getRole();
  return role === ROLES.OPERATIONS_MANAGER || role === 'ROLE_OPERATIONS_MANAGER' || role === 'OPERATIONS_MANAGER';
}

function statusBadge(status) {
  if (typeof StageRegistry !== 'undefined') {
    const stg = StageRegistry.getByKey(status);
    if (stg) {
      return StageRegistry.getBadgeHtml(status);
    }
  }
  const s = STATUS_MAP[status] || {
    cls: 'badge-inprogress',
    label: (status || '—').replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, l => l.toUpperCase())
  };
  return `<span class="order-badge ${s.cls}">${s.label}</span>`;
}

function paymentBadge(status) {
  const s = PAYMENT_MAP[status] || { cls: 'badge-pending', label: status || '—' };
  return `<span class="order-badge ${s.cls}">${s.label}</span>`;
}

/* ── Highlight helper ─────────────────────────────────────────────────── */
function highlight(text, query) {
  if (!query || !text) return text || '';
  const re = new RegExp(`(${query.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')})`, 'gi');
  return String(text).replace(re, '<mark style="background:rgba(99,102,241,0.3);color:inherit;border-radius:2px;padding:0 2px">$1</mark>');
}

/* ── Load Stats ───────────────────────────────────────────────────────── */
async function loadStats() {
  try {
    const stats = await Api.get(`${API.ORDERS}/stats`);
    document.getElementById('statTotal').textContent = stats.total ?? 0;
    document.getElementById('statPending').textContent = stats.pending ?? 0;
    document.getElementById('statInProgress').textContent = stats.inProgress ?? 0;
    document.getElementById('statCompleted').textContent = stats.completed ?? 0;
  } catch (_) {
    ['statTotal', 'statPending', 'statInProgress', 'statCompleted'].forEach(id => {
      document.getElementById(id).textContent = '0';
    });
  }
}

/* ── Load Orders ──────────────────────────────────────────────────────── */
async function loadOrders() {
  renderLoading();
  try {
    let url = `${API.ORDERS}?page=${currentPage}&size=${PAGE_SIZE}&sort=createdAt,desc`;
    if (currentFilter && currentFilter !== 'ALL') {
      url += `&status=${encodeURIComponent(currentFilter)}`;
    }
    if (currentSearch.trim()) {
      url += `&search=${encodeURIComponent(currentSearch.trim())}`;
    }

    const data = await Api.get(url);

    totalPages = data.totalPages ?? 0;
    totalElements = data.totalElements ?? 0;

    updateLabels();
    renderTable(data.content ?? []);
    renderPagination();
  } catch (err) {
    renderError('Could not load orders. Please refresh.');
  }
}

/* ── Render Table ─────────────────────────────────────────────────────── */
function renderLoading() {
  const isOps = isOperationsManager();
  const colSpan = isOps ? 6 : 8;
  tableBody.innerHTML = `
    <tr>
      <td colspan="${colSpan}" style="padding:40px;text-align:center;">
        <div style="display:inline-flex;align-items:center;gap:8px;color:var(--text-muted);font-size:13px;">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
               style="animation:spin 0.8s linear infinite;">
            <path d="M12 2v4M12 18v4M4.93 4.93l2.83 2.83M16.24 16.24l2.83 2.83M2 12h4M18 12h4M4.93 19.07l2.83-2.83M16.24 7.76l2.83-2.83"/>
          </svg>
          Loading orders…
        </div>
      </td>
    </tr>
  `;
}

function renderTable(orders) {
  const isOps = isOperationsManager();
  const colSpan = isOps ? 6 : 8;

  // Toggle table headers for financial columns
  const amtCol = document.getElementById('colOrderAmount');
  const payCol = document.getElementById('colOrderPayment');
  if (amtCol) amtCol.style.display = isOps ? 'none' : '';
  if (payCol) payCol.style.display = isOps ? 'none' : '';

  if (!orders.length) {
    tableBody.innerHTML = `
      <tr>
        <td colspan="${colSpan}">
          <div class="order-empty">
            <svg class="order-empty-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
              <polyline points="14 2 14 8 20 8"/>
              <line x1="16" y1="13" x2="8" y2="13"/>
              <line x1="16" y1="17" x2="8" y2="17"/>
            </svg>
            <h3>${currentSearch || currentFilter ? 'No orders found' : 'No orders yet'}</h3>
            <p>${currentSearch
        ? `No results for "<strong>${currentSearch}</strong>". Try a different search.`
        : currentFilter
          ? `No orders with status "${currentFilter.replace(/_/g, ' ')}".`
          : 'Create your first order using the "New Order" button above.'
      }</p>
          </div>
        </td>
      </tr>
    `;
    return;
  }

  const startIndex = currentPage * PAGE_SIZE;

  tableBody.innerHTML = orders.map((o, i) => {
    const idx = startIndex + i + 1;
    const orderNum = o.orderNumber || `#${o.id}`;
    const custName = o.customerName || '—';
    const custMobile = o.customerMobile || '—';
    const deliveryDate = o.deliveryDate
      ? new Date(o.deliveryDate).toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' })
      : '—';
    const total = o.totalAmount != null ? `₹${Number(o.totalAmount).toLocaleString('en-IN')}` : '₹0';
    const balance = o.balanceAmount != null && Number(o.balanceAmount) > 0
      ? `<div class="order-balance">Balance: ₹${Number(o.balanceAmount).toLocaleString('en-IN')}</div>`
      : '';

    const hOrderNum = highlight(orderNum, currentSearch);
    const hMobile = highlight(custMobile, currentSearch);

    return `
      <tr class="order-table-row" id="order-row-${o.id}" onclick="window.toggleOrderRowSelect(this, event)">
        <td class="text-muted" style="font-size:12px;">${idx}</td>
        <td>
          <span class="order-num">${hOrderNum}</span>
          ${o.garmentType ? `<span class="garment-badge ${o.garmentType === 'BLOUSE' ? 'blouse-badge' : 'chudi-badge'}" style="margin-left:6px;font-size:10px;padding:2px 6px;">${o.garmentType === 'BLOUSE' ? 'Blouse' : 'Chudi'}</span>` : ''}
          ${o.lining ? `<span style="font-size:9px;color:var(--text-muted);margin-left:4px;">(${o.lining === 'WITH_LINING' ? 'Lining' : 'No Lin.'})</span>` : ''}
        </td>
        <td>
          <div class="order-customer-name">${custName}</div>
          <div class="order-customer-mobile">${hMobile}</div>
        </td>
        <td style="font-size:12px;color:var(--text-muted);">${deliveryDate}</td>
        ${!isOps ? `
          <td>
            <div class="order-amount">${total}</div>
            ${balance}
          </td>
        ` : ''}
        <td>${statusBadge(o.status)}</td>
        ${!isOps ? `<td>${paymentBadge(o.paymentStatus)}</td>` : ''}
        <td style="text-align:right;">
          <div style="display:flex;gap:6px;justify-content:flex-end;">
            <button class="btn btn-ghost btn-sm" onclick="viewOrder(${o.id})">View</button>
            ${o.status !== 'CANCELLED' && o.status !== 'COMPLETED'
        ? `<button class="btn btn-ghost btn-sm" style="color:#EF4444;" onclick="cancelOrder(${o.id}, '${orderNum.replace(/'/g, "\\'")}')">Cancel</button>`
        : ''}
          </div>
        </td>
      </tr>
    `;
  }).join('');
}

function renderError(msg) {
  tableBody.innerHTML = `
    <tr>
      <td colspan="8">
        <div class="order-empty">
          <svg class="order-empty-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/>
          </svg>
          <h3>Failed to load orders</h3>
          <p>${msg}</p>
        </div>
      </td>
    </tr>
  `;
}

/* ── Labels ───────────────────────────────────────────────────────────── */
function updateLabels() {
  if (currentSearch) {
    countLabel.textContent = `${totalElements} result${totalElements !== 1 ? 's' : ''} for "${currentSearch}"`;
    tableSubtitle.textContent = 'Search results';
  } else if (currentFilter) {
    countLabel.textContent = `${totalElements} ${currentFilter.replace(/_/g, ' ')} order${totalElements !== 1 ? 's' : ''}`;
    tableSubtitle.textContent = `Filtered by status`;
  } else {
    countLabel.textContent = `${totalElements} order${totalElements !== 1 ? 's' : ''} registered`;
    tableSubtitle.textContent = 'All registered orders';
  }
}

/* ── Pagination & Rows per Page ───────────────────────────────────────── */
function renderPagination() {
  if (totalElements === 0) { pagination.innerHTML = ''; return; }
  const from = currentPage * PAGE_SIZE + 1;
  const to = Math.min((currentPage + 1) * PAGE_SIZE, totalElements);

  let pagesHtml = '';
  const start = Math.max(0, currentPage - 2);
  const end = Math.min(totalPages - 1, currentPage + 2);

  if (start > 0) {
    pagesHtml += `<button class="order-page-btn" onclick="goToPage(0)">1</button>`;
    if (start > 1) pagesHtml += `<span class="order-pagination-ellipsis">…</span>`;
  }

  for (let p = start; p <= end; p++) {
    pagesHtml += `
      <button class="order-page-btn${p === currentPage ? ' active' : ''}"
              onclick="goToPage(${p})">${p + 1}</button>
    `;
  }

  if (end < totalPages - 1) {
    if (end < totalPages - 2) pagesHtml += `<span class="order-pagination-ellipsis">…</span>`;
    pagesHtml += `<button class="order-page-btn" onclick="goToPage(${totalPages - 1})">${totalPages}</button>`;
  }

  pagination.innerHTML = `
    <div class="order-pagination-left" style="display:flex; align-items:center; gap:16px; flex-wrap:wrap;">
      <span>Showing <strong>${from}–${to}</strong> of <strong>${totalElements}</strong> orders</span>
      <div style="display:flex; align-items:center; gap:6px; font-size:12px; color:var(--text-secondary);">
        <span>Rows:</span>
        <select id="orderPageSizeSelect" class="form-select text-xs" style="width:auto; padding:2px 8px; height:28px; border-radius:6px; background:rgba(255,255,255,0.06); border:1px solid var(--border-default); color:var(--text-primary); cursor:pointer; font-weight:600;" onchange="window.handleOrderPageSizeChange(this.value)">
          <option value="10" ${PAGE_SIZE === 10 ? 'selected' : ''}>10 / page</option>
          <option value="15" ${PAGE_SIZE === 15 ? 'selected' : ''}>15 / page</option>
          <option value="25" ${PAGE_SIZE === 25 ? 'selected' : ''}>25 / page</option>
          <option value="50" ${PAGE_SIZE === 50 ? 'selected' : ''}>50 / page</option>
          <option value="100" ${PAGE_SIZE === 100 ? 'selected' : ''}>100 / page</option>
        </select>
      </div>
    </div>
    ${totalPages > 1 ? `
      <div class="order-pagination-controls">
        <button class="order-page-btn" onclick="goToPage(${currentPage - 1})" ${currentPage === 0 ? 'disabled' : ''} title="Previous Page">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="15 18 9 12 15 6"/></svg>
        </button>
        ${pagesHtml}
        <button class="order-page-btn" onclick="goToPage(${currentPage + 1})" ${currentPage >= totalPages - 1 ? 'disabled' : ''} title="Next Page">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="9 18 15 12 9 6"/></svg>
        </button>
      </div>
    ` : ''}
  `;
}

function goToPage(p) {
  if (p < 0 || p >= totalPages) return;
  currentPage = p;
  loadOrders();
  window.scrollTo({ top: 0, behavior: 'smooth' });
}

window.handleOrderPageSizeChange = function (newSize) {
  PAGE_SIZE = parseInt(newSize, 10) || 15;
  currentPage = 0;
  loadOrders();
};

window.toggleOrderRowSelect = function (rowEl, event) {
  if (event.target.closest('button') || event.target.closest('a')) return;
  rowEl.classList.toggle('selected');
};

/* ── Navigation ───────────────────────────────────────────────────────── */
// viewOrder is defined below as window.viewOrder to open the detail modal


/* ── Cancel Order ─────────────────────────────────────────────────────── */
async function cancelOrder(id, orderNum) {
  Modal.confirm(
    `Cancel order <strong>${orderNum}</strong>? This cannot be undone.`,
    async () => {
      try {
        await Api.del(`${API.ORDER_CANCEL(id)}`);
        Toast.success(`Order ${orderNum} cancelled`);
        await Promise.all([loadStats(), loadOrders()]);
      } catch (err) {
        Toast.error(err?.message || 'Could not cancel this order');
      }
    },
    { title: 'Cancel Order', confirmText: 'Yes, Cancel', danger: true }
  );
}

/* ── Stat Cards Click ─────────────────────────────────────────────────── */
document.querySelectorAll('.order-stat-card').forEach(card => {
  card.addEventListener('click', () => {
    const filter = card.dataset.filter ?? '';
    currentFilter = filter;
    currentPage = 0;
    currentSearch = '';
    searchInput.value = '';
    document.querySelectorAll('.order-stat-card').forEach(c => c.classList.toggle('active', c === card));
    document.querySelectorAll('.filter-tab').forEach(b => {
      b.classList.toggle('active', (b.dataset.status || '') === filter);
    });
    loadOrders();
  });
});

/* ── Filter Tabs ──────────────────────────────────────────────────────── */
document.getElementById('filterTabs').addEventListener('click', (e) => {
  const btn = e.target.closest('.filter-tab');
  if (!btn) return;
  document.querySelectorAll('.filter-tab').forEach(b => b.classList.remove('active'));
  btn.classList.add('active');
  currentFilter = btn.dataset.status;
  currentPage = 0;
  currentSearch = '';
  searchInput.value = '';
  document.querySelectorAll('.order-stat-card').forEach(c => {
    c.classList.toggle('active', (c.dataset.filter || '') === currentFilter);
  });
  loadOrders();
});

/* ── Search ───────────────────────────────────────────────────────────── */
searchInput.addEventListener('input', () => {
  clearTimeout(searchTimer);
  searchTimer = setTimeout(() => {
    currentSearch = searchInput.value.trim();
    currentPage = 0;
    currentFilter = '';
    document.querySelectorAll('.filter-tab').forEach((b, i) => b.classList.toggle('active', i === 0));
    document.querySelectorAll('.order-stat-card').forEach((c, i) => c.classList.toggle('active', i === 0));
    loadOrders();
  }, SEARCH_DEBOUNCE_MS);
});

/* ── Refresh ──────────────────────────────────────────────────────────── */
document.getElementById('refreshBtn').addEventListener('click', async () => {
  Toast.info('Refreshing…');
  await Promise.all([loadStats(), loadOrders()]);
  Toast.success('Data refreshed');
});

/* ── Export to Excel ─────────────────────────────────────────────────── */
document.getElementById('exportOrdersExcelBtn')?.addEventListener('click', async () => {
  try {
    Toast.info('Preparing orders export…');
    let url = `${API.ORDERS}?page=0&size=2000&sort=createdAt,desc`;
    if (currentFilter && currentFilter !== 'ALL') {
      url += `&status=${encodeURIComponent(currentFilter)}`;
    }
    if (currentSearch.trim()) {
      url += `&search=${encodeURIComponent(currentSearch.trim())}`;
    }
    const res = await Api.get(url);
    const orders = res?.content ?? [];
    if (!orders.length) {
      Toast.warning('No orders found to export.');
      return;
    }

    const columns = [
      { key: 'sno', header: 'S.No' },
      { key: 'orderNumber', header: 'Order Number', transform: (v, o) => v || `#${o.id}` },
      { key: 'customerName', header: 'Customer Name', transform: v => v || '—' },
      { key: 'customerMobile', header: 'Mobile Number', transform: v => v || '—' },
      { key: 'garmentType', header: 'Garment Type', transform: v => v || '—' },
      { key: 'lining', header: 'Lining', transform: v => v === 'WITH_LINING' ? 'With Lining' : (v || 'Without Lining') },
      { key: 'orderDate', header: 'Order Date', transform: (v, o) => ExcelExport.formatDate(v || o.createdAt) },
      { key: 'deliveryDate', header: 'Target Delivery', transform: v => ExcelExport.formatDate(v) },
      { key: 'totalAmount', header: 'Total Amount (Rs)', transform: v => Number(v || 0) },
      { key: 'paidAmount', header: 'Paid Amount (Rs)', transform: v => Number(v || 0) },
      { key: 'balanceDue', header: 'Balance Due (Rs)', transform: (_, o) => Math.max(0, Number(o.totalAmount || 0) - Number(o.paidAmount || 0)) },
      { key: 'paymentStatus', header: 'Payment Status', transform: v => (PAYMENT_MAP[v]?.label || v || '—') },
      { key: 'status', header: 'Stage / Status', transform: v => (STATUS_MAP[v]?.label || v || '—') },
      { key: 'assignedEmployeeName', header: 'Assigned Staff', transform: v => v || 'Unassigned' },
      { key: 'branchName', header: 'Branch', transform: (v, o) => v || (o.branchId ? `Branch ${o.branchId}` : 'Main Branch') },
    ];

    await ExcelExport.exportData({
      data: orders,
      fileName: 'ritham-orders',
      sheetName: 'Orders Directory',
      columns,
    });
  } catch (err) {
    Toast.error('Failed to export orders: ' + (err.message || 'Error'));
  }
});

/* ── New Order Modal ──────────────────────────────────────────────────── */
const modalBackdrop = document.getElementById('orderModalBackdrop');
const fieldMobile = document.getElementById('fieldCustomerMobile');
const fieldDate = document.getElementById('fieldDeliveryDate');
const fieldTotal = document.getElementById('fieldTotalAmount');
const fieldAdvance = document.getElementById('fieldAdvanceAmount');
const orderCreateFeedback = document.getElementById('orderCreateFeedback');
const lookupResult = document.getElementById('customerLookupResult');
const saveBtn = document.getElementById('orderSaveBtn');
const newCustBanner = document.getElementById('newCustomerBanner');
const fieldNewName = document.getElementById('fieldNewCustomerName');
const fieldGarmentType = document.getElementById('fieldGarmentType');
const fieldLining = document.getElementById('fieldLining');
const blousePanel = document.getElementById('blouseMeasurementsPanel');
const chudiPanel = document.getElementById('chudiMeasurementsPanel');
const clearBlouseBtn = document.getElementById('clearBlouseBtn');
const clearChudiBtn = document.getElementById('clearChudiBtn');
const editBlouseBtn = document.getElementById('editBlouseBtn');
const saveEditBlouseBtn = document.getElementById('saveEditBlouseBtn');
const closeEditBlouseBtn = document.getElementById('closeEditBlouseBtn');
const editChudiBtn = document.getElementById('editChudiBtn');
const saveEditChudiBtn = document.getElementById('saveEditChudiBtn');
const closeEditChudiBtn = document.getElementById('closeEditChudiBtn');
const blouseLoadedBadge = document.getElementById('blouseLoadedBadge');
const chudiLoadedBadge = document.getElementById('chudiLoadedBadge');
const customerDueAlert = document.getElementById('customerDueAlert');
const customerDueTotal = document.getElementById('customerDueTotal');
const customerDueDetails = document.getElementById('customerDueDetails');
const btnToggleDueOrders = document.getElementById('btnToggleDueOrders');
const dueOrdersList = document.getElementById('dueOrdersList');
const orderModalEl = document.querySelector('#orderModalBackdrop .order-modal');
let _customerSavedSpecsMap = {};
let _isEditingSpecs = { BLOUSE: false, CHUDI: false };
let _customerOldDue = 0;

let _customerValid = false;
let _lookupTimer = null;
let _selectedCustomer = null;   // { customerMobile, customerName }
let _isNewCustomer = false;  // true when mobile not found → will register on save

async function checkCustomerDue(mobile) {
  if (!mobile || mobile.length !== 10) {
    hideCustomerDue();
    return;
  }

  try {
    let dueData = null;
    try {
      const res = await Api.get(`${API.ORDERS}/customer/${mobile}/due`);
      dueData = res?.data ?? res;
    } catch (_) {
      // Fallback: search orders directly
      const ordRes = await Api.get(`${API.ORDERS}?search=${encodeURIComponent(mobile)}&size=100`);
      const orders = ordRes?.content ?? [];
      let totalDue = 0;
      let dueOrders = [];
      orders.forEach(o => {
        if (o.status === 'CANCELLED') return;
        const bal = parseFloat(o.balanceAmount || 0) || 0;
        if (bal > 0) {
          totalDue += bal;
          dueOrders.push({
            orderNumber: o.orderNumber,
            status: o.status,
            garmentType: o.garmentType || 'Standard',
            orderDate: o.orderDate ? new Date(o.orderDate).toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' }) : (o.createdAt ? new Date(o.createdAt).toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' }) : ''),
            balanceAmount: bal,
            totalAmount: o.totalAmount
          });
        }
      });
      dueData = {
        hasDue: totalDue > 0,
        totalDue: totalDue,
        dueOrderCount: dueOrders.length,
        dueOrders: dueOrders
      };
    }

    if (dueData && dueData.hasDue && parseFloat(dueData.totalDue) > 0) {
      showCustomerDue(dueData);
    } else {
      hideCustomerDue(true);
    }
  } catch (err) {
    console.warn('Could not check customer due:', err);
    hideCustomerDue();
  }
}

function showCustomerDue(dueData) {
  if (!customerDueAlert) return;
  const dueAmount = parseFloat(dueData.totalDue || 0);
  const formattedDue = `₹${dueAmount.toLocaleString('en-IN', { minimumFractionDigits: 2 })}`;

  _customerOldDue = dueAmount;

  if (customerDueTotal) customerDueTotal.textContent = formattedDue;
  if (customerDueDetails) {
    const count = dueData.dueOrderCount || dueData.dueOrders?.length || 1;
    customerDueDetails.textContent = `${count} previous order${count > 1 ? 's have' : ' has'} pending balance of ${formattedDue}`;
  }

  if (dueOrdersList && dueData.dueOrders && dueData.dueOrders.length > 0) {
    dueOrdersList.innerHTML = dueData.dueOrders.map(o => {
      const gType = (o.garmentType || 'Standard').toUpperCase();
      let icon = '🧵';
      let label = o.garmentType || 'Standard';
      if (gType === 'BLOUSE') { icon = '👗'; label = 'Blouse'; }
      else if (gType === 'CHUDI') { icon = '👘'; label = 'Chudi'; }

      let dateDisplay = o.orderDate || '';
      if (!dateDisplay && o.createdAt) {
        try {
          dateDisplay = new Date(o.createdAt).toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
        } catch (_) { }
      }

      return `
        <div style="display:flex;justify-content:space-between;align-items:center;padding:5px 0;border-bottom:1px dashed rgba(239,68,68,0.2);">
          <div style="display:flex;align-items:center;gap:8px;flex-wrap:wrap;">
            <span style="font-weight:700;color:#FCA5A5;">${o.orderNumber}</span>
            <span style="display:inline-flex;align-items:center;gap:4px;background:rgba(255,255,255,0.06);padding:2px 7px;border-radius:4px;font-size:11px;color:var(--text-primary);border:1px solid rgba(255,255,255,0.08);">
              <span>${icon}</span>
              <strong>${label}</strong>
              ${dateDisplay ? `<span style="color:var(--text-muted);font-size:10px;margin-left:4px;">• ${dateDisplay}</span>` : ''}
            </span>
          </div>
          <span style="font-weight:700;color:#EF4444;font-size:12px;">₹${parseFloat(o.balanceAmount || 0).toLocaleString('en-IN')} Due</span>
        </div>
      `;
    }).join('');
  }

  customerDueAlert.style.display = 'block';

  // Update customer lookup banner with due badge
  if (lookupResult && lookupResult.classList.contains('visible')) {
    const existingDueBadge = lookupResult.querySelector('.due-badge');
    if (existingDueBadge) existingDueBadge.remove();
    const existingClearBadge = lookupResult.querySelector('.clear-badge');
    if (existingClearBadge) existingClearBadge.remove();

    lookupResult.insertAdjacentHTML('beforeend', ` <span class="due-badge" style="color:#EF4444;font-weight:700;margin-left:8px;background:rgba(239,68,68,0.15);padding:2px 8px;border-radius:4px;font-size:11px;">⚠️ ${formattedDue} Due</span>`);
  }

  updateOrderCreateCalculation();
}

function hideCustomerDue(allClear = false) {
  _customerOldDue = 0;
  if (customerDueAlert) customerDueAlert.style.display = 'none';
  if (dueOrdersList) dueOrdersList.style.display = 'none';
  if (btnToggleDueOrders) btnToggleDueOrders.textContent = 'View Dues ▼';

  if (lookupResult && lookupResult.classList.contains('visible')) {
    const existingDueBadge = lookupResult.querySelector('.due-badge');
    if (existingDueBadge) existingDueBadge.remove();
    const existingClearBadge = lookupResult.querySelector('.clear-badge');
    if (existingClearBadge) existingClearBadge.remove();

    if (allClear && _customerValid) {
      lookupResult.insertAdjacentHTML('beforeend', ` <span class="clear-badge" style="color:#10B981;font-weight:600;margin-left:8px;background:rgba(16,185,129,0.12);padding:2px 8px;border-radius:4px;font-size:11px;">✓ No Dues (All Clear)</span>`);
    }
  }

  updateOrderCreateCalculation();
}

btnToggleDueOrders?.addEventListener('click', () => {
  if (!dueOrdersList) return;
  const isOpen = dueOrdersList.style.display === 'block';
  dueOrdersList.style.display = isOpen ? 'none' : 'block';
  btnToggleDueOrders.textContent = isOpen ? 'View Dues ▼' : 'Hide Dues ▲';
});

async function checkPreviousMeasurements(mobile) {
  _customerSavedSpecsMap = {};
  if (!mobile || mobile.length !== 10) return;

  try {
    // 1. Check customer measurement profiles
    const cmRes = await Api.get(`${API.MEASUREMENTS}/customer/${mobile}`);
    const cmList = cmRes?.data ?? (Array.isArray(cmRes) ? cmRes : []);
    if (Array.isArray(cmList) && cmList.length > 0) {
      cmList.forEach(item => {
        if (item.garmentType && item.measurements && Object.keys(item.measurements).length > 0) {
          _customerSavedSpecsMap[item.garmentType.toUpperCase()] = item;
        }
      });
    }

    // 2. Check /latest endpoint fallback
    if (Object.keys(_customerSavedSpecsMap).length === 0) {
      const cmLatest = await Api.get(`${API.MEASUREMENTS}/customer/${mobile}/latest`);
      const single = cmLatest?.data ?? cmLatest;
      if (single && single.garmentType && single.measurements && Object.keys(single.measurements).length > 0) {
        _customerSavedSpecsMap[single.garmentType.toUpperCase()] = single;
      }
    }

    // 3. Check past order latest measurements fallback
    if (Object.keys(_customerSavedSpecsMap).length === 0) {
      const pastRes = await Api.get(`${API.ORDERS}/customer/${mobile}/latest-measurements`);
      const past = pastRes?.data ?? pastRes;
      if (past && past.garmentType && past.measurements && Object.keys(past.measurements).length > 0) {
        _customerSavedSpecsMap[past.garmentType.toUpperCase()] = past;
      }
    }

    // Auto-load if garment type is already selected OR if customer only has 1 garment type
    const currentGType = fieldGarmentType?.value;
    if (currentGType && _customerSavedSpecsMap[currentGType]) {
      loadSavedMeasurementsForGarment(currentGType);
    } else {
      const availableTypes = Object.keys(_customerSavedSpecsMap);
      if (availableTypes.length === 1 && fieldGarmentType) {
        fieldGarmentType.value = availableTypes[0];
        handleGarmentTypeChange();
      }
    }
  } catch (err) {
    console.warn('Could not check previous measurements:', err);
  }
}

function loadSavedMeasurementsForGarment(gType) {
  if (!gType) return false;
  const saved = _customerSavedSpecsMap[gType.toUpperCase()];
  const panel = gType === 'BLOUSE' ? blousePanel : (gType === 'CHUDI' ? chudiPanel : null);
  const editBtn = gType === 'BLOUSE' ? editBlouseBtn : editChudiBtn;
  const badge = gType === 'BLOUSE' ? blouseLoadedBadge : chudiLoadedBadge;

  if (!panel) return false;

  if (!saved || !saved.measurements || Object.keys(saved.measurements).length === 0) {
    // No saved specs for this garment type: fields remain freely editable
    panel.classList.remove('specs-locked', 'specs-editing');
    panel.querySelectorAll('input').forEach(inp => inp.readOnly = false);
    if (editBtn) editBtn.style.display = 'none';
    if (badge) badge.style.display = 'none';
    _isEditingSpecs[gType] = true;
    return false;
  }

  let loadedCount = 0;
  Object.entries(saved.measurements).forEach(([k, v]) => {
    const inp = panel.querySelector(`input[data-key="${k}"]`);
    if (inp) {
      inp.value = v;
      inp.readOnly = true; // Lock until user clicks edit
      loadedCount++;
    }
  });

  if (saved.lining && fieldLining && !fieldLining.value) {
    fieldLining.value = saved.lining;
  }

  panel.classList.add('specs-locked');
  panel.classList.remove('specs-editing');
  _isEditingSpecs[gType] = false;

  const saveBtn = gType === 'BLOUSE' ? saveEditBlouseBtn : saveEditChudiBtn;
  const closeBtn = gType === 'BLOUSE' ? closeEditBlouseBtn : closeEditChudiBtn;
  if (saveBtn) saveBtn.style.display = 'none';
  if (closeBtn) closeBtn.style.display = 'none';

  if (editBtn) {
    editBtn.style.display = 'inline-flex';
    editBtn.textContent = '✏️ Edit Specs';
    editBtn.classList.remove('btn-primary');
    editBtn.classList.add('btn-outline');
  }
  if (badge) {
    badge.style.display = 'inline-block';
    badge.className = 'badge badge-success';
    badge.textContent = '✓ Saved Specs';
  }

  if (loadedCount > 0) {
    Toast.success(`Auto-loaded saved ${gType} specs for ${_selectedCustomer?.customerName || 'customer'} 📐`);
    return true;
  }
  return false;
}

function enableSpecsEditing(gType) {
  const panel = gType === 'BLOUSE' ? blousePanel : (gType === 'CHUDI' ? chudiPanel : null);
  const editBtn = gType === 'BLOUSE' ? editBlouseBtn : editChudiBtn;
  const saveBtn = gType === 'BLOUSE' ? saveEditBlouseBtn : saveEditChudiBtn;
  const closeBtn = gType === 'BLOUSE' ? closeEditBlouseBtn : closeEditChudiBtn;
  const badge = gType === 'BLOUSE' ? blouseLoadedBadge : chudiLoadedBadge;
  if (!panel) return;

  _isEditingSpecs[gType] = true;
  panel.classList.remove('specs-locked');
  panel.classList.add('specs-editing');
  panel.querySelectorAll('input').forEach(inp => inp.readOnly = false);

  // Hide the Edit button (no "Unlocked to Edit" text)
  if (editBtn) {
    editBtn.style.display = 'none';
  }
  // Show the two options: Save and Close without save
  if (saveBtn) {
    saveBtn.style.display = 'inline-flex';
  }
  if (closeBtn) {
    closeBtn.style.display = 'inline-flex';
  }
  if (badge) {
    badge.className = 'badge badge-warning';
    badge.textContent = '✏️ Editing Mode';
  }

  const firstInp = panel.querySelector('input');
  if (firstInp) firstInp.focus();

  Toast.info(`Editing measurements. Click 'Save' to update or 'Close' to discard.`);
}

function cancelSpecsEditing(gType) {
  const panel = gType === 'BLOUSE' ? blousePanel : (gType === 'CHUDI' ? chudiPanel : null);
  const editBtn = gType === 'BLOUSE' ? editBlouseBtn : editChudiBtn;
  const saveBtn = gType === 'BLOUSE' ? saveEditBlouseBtn : saveEditChudiBtn;
  const closeBtn = gType === 'BLOUSE' ? closeEditBlouseBtn : closeEditChudiBtn;
  const badge = gType === 'BLOUSE' ? blouseLoadedBadge : chudiLoadedBadge;
  if (!panel) return;

  // Revert all inputs to original saved measurements
  const saved = _customerSavedSpecsMap[gType.toUpperCase()];
  const savedMeasurements = (saved && saved.measurements) || {};
  panel.querySelectorAll('input[data-key]').forEach(inp => {
    inp.value = savedMeasurements[inp.dataset.key] || '';
  });

  // Lock inputs
  panel.classList.add('specs-locked');
  panel.classList.remove('specs-editing');
  panel.querySelectorAll('input').forEach(inp => inp.readOnly = true);

  _isEditingSpecs[gType] = false;

  // Restore buttons
  if (editBtn) editBtn.style.display = 'inline-flex';
  if (saveBtn) saveBtn.style.display = 'none';
  if (closeBtn) closeBtn.style.display = 'none';

  if (badge) {
    badge.className = 'badge badge-success';
    badge.textContent = '✓ Saved Specs';
  }

  Toast.info(`Closed without saving. Original ${gType} specs restored.`);
}

async function saveSpecsEditing(gType) {
  const panel = gType === 'BLOUSE' ? blousePanel : (gType === 'CHUDI' ? chudiPanel : null);
  const editBtn = gType === 'BLOUSE' ? editBlouseBtn : editChudiBtn;
  const saveBtn = gType === 'BLOUSE' ? saveEditBlouseBtn : saveEditChudiBtn;
  const closeBtn = gType === 'BLOUSE' ? closeEditBlouseBtn : closeEditChudiBtn;
  const badge = gType === 'BLOUSE' ? blouseLoadedBadge : chudiLoadedBadge;
  if (!panel) return;

  // Collect updated measurements from the panel inputs
  const measurements = {};
  panel.querySelectorAll('input[data-key]').forEach(inp => {
    const val = inp.value.trim();
    if (val) measurements[inp.dataset.key] = val;
  });

  // Update in-memory saved specs map
  if (!_customerSavedSpecsMap[gType.toUpperCase()]) {
    _customerSavedSpecsMap[gType.toUpperCase()] = { garmentType: gType, measurements: {} };
  }
  _customerSavedSpecsMap[gType.toUpperCase()].measurements = measurements;

  // Lock inputs
  panel.classList.add('specs-locked');
  panel.classList.remove('specs-editing');
  panel.querySelectorAll('input').forEach(inp => inp.readOnly = true);

  _isEditingSpecs[gType] = false;

  // Restore buttons
  if (editBtn) editBtn.style.display = 'inline-flex';
  if (saveBtn) saveBtn.style.display = 'none';
  if (closeBtn) closeBtn.style.display = 'none';

  if (badge) {
    badge.className = 'badge badge-success';
    badge.textContent = '✓ Saved Specs';
  }

  // Also sync to customer measurements profile API if customer mobile is selected
  const mobile = fieldMobile?.value?.trim();
  if (mobile && Object.keys(measurements).length > 0) {
    try {
      const customerName = _selectedCustomer?.customerName || document.getElementById('fieldNewCustomerName')?.value?.trim() || 'Customer';
      const lining = fieldLining?.value || null;
      await Api.post(API.MEASUREMENTS, {
        customerMobile: mobile,
        customerName: customerName,
        garmentType: gType,
        lining: lining,
        measurements: measurements,
        notes: 'Updated via Order Specs Editor'
      });
      Toast.success(`Saved and updated ${gType} measurements profile! ✓`);
    } catch (e) {
      console.warn('Could not immediately sync to customer measurements:', e);
      Toast.success(`Measurements updated for this order! ✓`);
    }
  } else {
    Toast.success(`Measurements updated! ✓`);
  }
}

editBlouseBtn?.addEventListener('click', () => enableSpecsEditing('BLOUSE'));
saveEditBlouseBtn?.addEventListener('click', () => saveSpecsEditing('BLOUSE'));
closeEditBlouseBtn?.addEventListener('click', () => cancelSpecsEditing('BLOUSE'));

editChudiBtn?.addEventListener('click', () => enableSpecsEditing('CHUDI'));
saveEditChudiBtn?.addEventListener('click', () => saveSpecsEditing('CHUDI'));
closeEditChudiBtn?.addEventListener('click', () => cancelSpecsEditing('CHUDI'));

function handleGarmentTypeChange() {
  const gType = fieldGarmentType?.value;
  if (gType === 'BLOUSE') {
    if (blousePanel) blousePanel.style.display = 'block';
    if (chudiPanel) chudiPanel.style.display = 'none';
    orderModalEl?.classList.add('has-measurements');
    loadSavedMeasurementsForGarment('BLOUSE');
  } else if (gType === 'CHUDI') {
    if (blousePanel) blousePanel.style.display = 'none';
    if (chudiPanel) chudiPanel.style.display = 'block';
    orderModalEl?.classList.add('has-measurements');
    loadSavedMeasurementsForGarment('CHUDI');
  } else {
    if (blousePanel) blousePanel.style.display = 'none';
    if (chudiPanel) chudiPanel.style.display = 'none';
    orderModalEl?.classList.remove('has-measurements');
  }
}

fieldGarmentType?.addEventListener('change', handleGarmentTypeChange);

clearBlouseBtn?.addEventListener('click', () => {
  blousePanel?.querySelectorAll('input').forEach(inp => {
    inp.value = '';
    inp.readOnly = false;
  });
  blousePanel?.classList.remove('specs-locked', 'specs-editing');
  if (editBlouseBtn) editBlouseBtn.style.display = 'none';
  if (saveEditBlouseBtn) saveEditBlouseBtn.style.display = 'none';
  if (closeEditBlouseBtn) closeEditBlouseBtn.style.display = 'none';
  if (blouseLoadedBadge) blouseLoadedBadge.style.display = 'none';
  _isEditingSpecs.BLOUSE = true;
});

clearChudiBtn?.addEventListener('click', () => {
  chudiPanel?.querySelectorAll('input').forEach(inp => {
    inp.value = '';
    inp.readOnly = false;
  });
  chudiPanel?.classList.remove('specs-locked', 'specs-editing');
  if (editChudiBtn) editChudiBtn.style.display = 'none';
  if (saveEditChudiBtn) saveEditChudiBtn.style.display = 'none';
  if (closeEditChudiBtn) closeEditChudiBtn.style.display = 'none';
  if (chudiLoadedBadge) chudiLoadedBadge.style.display = 'none';
  _isEditingSpecs.CHUDI = true;
});

function showNewCustomerBanner() {
  _isNewCustomer = true;
  newCustBanner.style.display = 'flex';
  document.getElementById('errCustomerMobile').classList.remove('visible');
  lookupResult.classList.remove('visible');
  if (fieldMobile.value.trim().length === 10) {
    setTimeout(() => fieldNewName.focus(), 50);
  } else {
    setTimeout(() => fieldMobile.focus(), 50);
  }
}

function hideNewCustomerBanner() {
  _isNewCustomer = false;
  newCustBanner.style.display = 'none';
  fieldNewName.value = '';
  document.getElementById('errNewCustomerName').classList.remove('visible');
}

/* ── Build autocomplete dropdown DOM (once) ──────────────────────────── */
const mobileWrap = fieldMobile.parentElement;
mobileWrap.style.position = 'relative';

const autocompleteList = document.createElement('div');
autocompleteList.id = 'customerAutocomplete';
autocompleteList.style.cssText = `
  display:none;
  position:absolute;
  top:calc(100% + 4px);
  left:0;
  right:0;
  background:var(--bg-surface-2);
  border:1px solid var(--border-strong);
  border-radius:var(--radius-md);
  z-index:9999;
  max-height:200px;
  overflow-y:auto;
  box-shadow:0 8px 24px rgba(0,0,0,0.35);
`;
mobileWrap.appendChild(autocompleteList);

function showAutocomplete(customers, query) {
  let html = '';

  if (customers && customers.length > 0) {
    html = customers.map(c => `
      <div class="ac-item" data-mobile="${c.customerMobile}" data-name="${c.customerName}"
           style="padding:10px 14px;cursor:pointer;display:flex;align-items:center;
                  justify-content:space-between;border-bottom:1px solid var(--border-default);
                  transition:background 0.15s;font-size:13px;">
        <div>
          <span style="font-weight:600;color:var(--text-primary);">${c.customerName}</span>
        </div>
        <span style="font-family:monospace;font-size:12px;color:var(--text-muted);">${c.customerMobile}</span>
      </div>
    `).join('');
  } else {
    html = `
      <div style="padding:12px 14px;font-size:12px;color:var(--text-muted);text-align:center;">
        No customer found matching "${query}"
      </div>
    `;
  }

  // Add "+ Create New Customer" option at the bottom
  html += `
    <div class="ac-new-btn"
         style="padding:10px 14px;cursor:pointer;display:flex;align-items:center;gap:8px;
                background:rgba(99,102,241,0.12);color:#818CF8;font-weight:600;font-size:13px;
                border-top:1px solid var(--border-default);transition:background 0.15s;">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
        <line x1="12" y1="5" x2="12" y2="19"></line><line x1="5" y1="12" x2="19" y2="12"></line>
      </svg>
      + Create New Customer
    </div>
  `;

  autocompleteList.innerHTML = html;

  // Hover & click handlers for matching items
  autocompleteList.querySelectorAll('.ac-item').forEach(item => {
    item.addEventListener('mouseenter', () => item.style.background = 'var(--bg-surface-3)');
    item.addEventListener('mouseleave', () => item.style.background = '');
    item.addEventListener('mousedown', (e) => {
      e.preventDefault();
      selectCustomer(item.dataset.mobile, item.dataset.name);
    });
  });

  // Handler for "+ Create New Customer" option
  const newBtn = autocompleteList.querySelector('.ac-new-btn');
  if (newBtn) {
    newBtn.addEventListener('mouseenter', () => newBtn.style.background = 'rgba(99,102,241,0.22)');
    newBtn.addEventListener('mouseleave', () => newBtn.style.background = 'rgba(99,102,241,0.12)');
    newBtn.addEventListener('mousedown', (e) => {
      e.preventDefault();
      hideAutocomplete();
      showNewCustomerBanner();
    });
  }

  autocompleteList.style.display = 'block';
}

function hideAutocomplete() {
  autocompleteList.style.display = 'none';
}

function selectCustomer(mobile, name) {
  fieldMobile.value = mobile;
  _selectedCustomer = { customerMobile: mobile, customerName: name };
  _customerValid = true;
  _isNewCustomer = false;
  lookupResult.innerHTML = `<strong>✓ ${name}</strong> — ${mobile}`;
  lookupResult.classList.add('visible');
  document.getElementById('errCustomerMobile').classList.remove('visible');
  hideAutocomplete();
  hideNewCustomerBanner();
  checkPreviousMeasurements(mobile);
  checkCustomerDue(mobile);
  fieldDate.focus();
}

fieldMobile.addEventListener('blur', () => {
  // Small delay so mousedown on ac-item fires first
  setTimeout(hideAutocomplete, 150);
});

function updateOrderCreateCalculation() {
  const total = parseFloat(fieldTotal?.value || 0) || 0;
  const advance = parseFloat(fieldAdvance?.value || 0) || 0;
  const oldDue = _customerOldDue || 0;
  const grandTotal = total + oldDue;

  const combinedRow = document.getElementById('combinedTotalRow');
  const oldDueBadge = document.getElementById('oldDueHintBadge');

  if (oldDue > 0) {
    if (combinedRow) {
      combinedRow.style.display = 'block';
      const sumThisOrder = document.getElementById('summaryThisOrder');
      const sumOldDue = document.getElementById('summaryOldDue');
      const sumTotalPayable = document.getElementById('summaryTotalPayable');
      if (sumThisOrder) sumThisOrder.textContent = `₹${total.toLocaleString('en-IN')}`;
      if (sumOldDue) sumOldDue.textContent = `₹${oldDue.toLocaleString('en-IN')}`;
      if (sumTotalPayable) sumTotalPayable.textContent = `₹${grandTotal.toLocaleString('en-IN')}`;
    }
    if (oldDueBadge) {
      oldDueBadge.style.display = 'inline-block';
      oldDueBadge.textContent = `+ ₹${oldDue.toLocaleString('en-IN')} Old Due`;
    }
  } else {
    if (combinedRow) combinedRow.style.display = 'none';
    if (oldDueBadge) oldDueBadge.style.display = 'none';
  }

  if (total <= 0 && oldDue <= 0) {
    if (orderCreateFeedback) orderCreateFeedback.style.display = 'none';
    return;
  }

  const remainingBalance = Math.max(0, grandTotal - advance);
  const extraPaid = Math.max(0, advance - grandTotal);

  if (orderCreateFeedback) {
    orderCreateFeedback.style.display = 'block';
    if (oldDue > 0) {
      if (extraPaid > 0) {
        orderCreateFeedback.style.background = 'rgba(99,102,241,0.12)';
        orderCreateFeedback.style.color = '#818CF8';
        orderCreateFeedback.style.border = '1px solid rgba(99,102,241,0.3)';
        orderCreateFeedback.innerHTML = `Total Payable: <strong>₹${grandTotal.toLocaleString('en-IN')}</strong> (Order ₹${total.toLocaleString('en-IN')} + Old Due ₹${oldDue.toLocaleString('en-IN')}) &bull; 🌟 <strong>Fully Cleared (+₹${extraPaid.toLocaleString('en-IN')} Extra)</strong>`;
      } else if (remainingBalance === 0) {
        orderCreateFeedback.style.background = 'rgba(16,185,129,0.12)';
        orderCreateFeedback.style.color = '#4ADE80';
        orderCreateFeedback.style.border = '1px solid rgba(16,185,129,0.3)';
        orderCreateFeedback.innerHTML = `Total Payable: <strong>₹${grandTotal.toLocaleString('en-IN')}</strong> (Order ₹${total.toLocaleString('en-IN')} + Old Due ₹${oldDue.toLocaleString('en-IN')}) &bull; ✓ <strong>All Dues &amp; Order Fully Paid (₹0 Due)</strong>`;
      } else {
        orderCreateFeedback.style.background = 'rgba(239,68,68,0.12)';
        orderCreateFeedback.style.color = '#F87171';
        orderCreateFeedback.style.border = '1px solid rgba(239,68,68,0.3)';
        orderCreateFeedback.innerHTML = `Total Payable: <strong>₹${grandTotal.toLocaleString('en-IN')}</strong> (Order ₹${total.toLocaleString('en-IN')} + Old Due ₹${oldDue.toLocaleString('en-IN')}) &bull; ⚠️ Total Balance Due: <strong>₹${remainingBalance.toLocaleString('en-IN')}</strong>`;
      }
    } else {
      if (extraPaid > 0) {
        orderCreateFeedback.style.background = 'rgba(99,102,241,0.12)';
        orderCreateFeedback.style.color = '#818CF8';
        orderCreateFeedback.style.border = '1px solid rgba(99,102,241,0.3)';
        orderCreateFeedback.innerHTML = `Total Amount: <strong>₹${total.toLocaleString('en-IN')}</strong> &bull; 🌟 <strong>Fully Paid (+₹${extraPaid.toLocaleString('en-IN')} Extra)</strong>`;
      } else if (remainingBalance === 0) {
        orderCreateFeedback.style.background = 'rgba(16,185,129,0.12)';
        orderCreateFeedback.style.color = '#4ADE80';
        orderCreateFeedback.style.border = '1px solid rgba(16,185,129,0.3)';
        orderCreateFeedback.innerHTML = `Total Amount: <strong>₹${total.toLocaleString('en-IN')}</strong> &bull; ✓ <strong>Fully Settled &amp; Paid (₹0 Due)</strong>`;
      } else {
        orderCreateFeedback.style.background = 'rgba(245,158,11,0.12)';
        orderCreateFeedback.style.color = '#FBBF24';
        orderCreateFeedback.style.border = '1px solid rgba(245,158,11,0.3)';
        orderCreateFeedback.innerHTML = `Total Amount: <strong>₹${total.toLocaleString('en-IN')}</strong> &bull; ⚠️ Balance Due: <strong>₹${remainingBalance.toLocaleString('en-IN')}</strong>`;
      }
    }
  }
}

fieldTotal?.addEventListener('input', updateOrderCreateCalculation);
fieldAdvance?.addEventListener('input', updateOrderCreateCalculation);

function openModal() {
  // Reset
  fieldMobile.value = '';
  fieldDate.value = '';
  fieldTotal.value = '';
  fieldAdvance.value = '';
  if (orderCreateFeedback) orderCreateFeedback.style.display = 'none';
  lookupResult.textContent = '';
  lookupResult.classList.remove('visible');
  _customerValid = false;
  _selectedCustomer = null;
  _isNewCustomer = false;
  hideAutocomplete();
  hideNewCustomerBanner();
  ['errCustomerMobile', 'errDeliveryDate', 'errTotalAmount', 'errNewCustomerName'].forEach(id => {
    const el = document.getElementById(id);
    if (el) el.classList.remove('visible');
  });
  // Reset garment type & measurements
  if (fieldGarmentType) fieldGarmentType.value = '';
  if (fieldLining) fieldLining.value = '';
  if (editBlouseBtn) editBlouseBtn.style.display = 'none';
  if (saveEditBlouseBtn) saveEditBlouseBtn.style.display = 'none';
  if (closeEditBlouseBtn) closeEditBlouseBtn.style.display = 'none';
  if (editChudiBtn) editChudiBtn.style.display = 'none';
  if (saveEditChudiBtn) saveEditChudiBtn.style.display = 'none';
  if (closeEditChudiBtn) closeEditChudiBtn.style.display = 'none';
  if (blouseLoadedBadge) blouseLoadedBadge.style.display = 'none';
  if (chudiLoadedBadge) chudiLoadedBadge.style.display = 'none';
  _customerSavedSpecsMap = {};
  _isEditingSpecs = { BLOUSE: false, CHUDI: false };
  if (blousePanel) {
    blousePanel.style.display = 'none';
    blousePanel.classList.remove('specs-locked', 'specs-editing');
    blousePanel.querySelectorAll('input').forEach(inp => { inp.readOnly = false; inp.value = ''; });
  }
  if (chudiPanel) {
    chudiPanel.style.display = 'none';
    chudiPanel.classList.remove('specs-locked', 'specs-editing');
    chudiPanel.querySelectorAll('input').forEach(inp => { inp.readOnly = false; inp.value = ''; });
  }
  orderModalEl?.classList.remove('has-measurements');
  hideCustomerDue();

  saveBtn.disabled = false;
  saveBtn.innerHTML = `
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"/></svg>
    Create Order`;
  // Set min date to today
  const today = new Date().toISOString().split('T')[0];
  fieldDate.min = today;

  modalBackdrop.classList.remove('hidden');
  fieldMobile.focus();
}

function closeModal() {
  modalBackdrop.classList.add('hidden');
  hideCustomerDue();
  if (orderCreateFeedback) orderCreateFeedback.style.display = 'none';
  if (fieldGarmentType) fieldGarmentType.value = '';
  if (fieldLining) fieldLining.value = '';
  if (editBlouseBtn) editBlouseBtn.style.display = 'none';
  if (saveEditBlouseBtn) saveEditBlouseBtn.style.display = 'none';
  if (closeEditBlouseBtn) closeEditBlouseBtn.style.display = 'none';
  if (editChudiBtn) editChudiBtn.style.display = 'none';
  if (saveEditChudiBtn) saveEditChudiBtn.style.display = 'none';
  if (closeEditChudiBtn) closeEditChudiBtn.style.display = 'none';
  if (blouseLoadedBadge) blouseLoadedBadge.style.display = 'none';
  if (chudiLoadedBadge) chudiLoadedBadge.style.display = 'none';
  _customerSavedSpecsMap = {};
  _isEditingSpecs = { BLOUSE: false, CHUDI: false };
  if (blousePanel) {
    blousePanel.style.display = 'none';
    blousePanel.classList.remove('specs-locked', 'specs-editing');
    blousePanel.querySelectorAll('input').forEach(inp => { inp.readOnly = false; inp.value = ''; });
  }
  if (chudiPanel) {
    chudiPanel.style.display = 'none';
    chudiPanel.classList.remove('specs-locked', 'specs-editing');
    chudiPanel.querySelectorAll('input').forEach(inp => { inp.readOnly = false; inp.value = ''; });
  }
  orderModalEl?.classList.remove('has-measurements');
}

document.getElementById('newOrderBtn').addEventListener('click', openModal);
document.getElementById('orderModalCloseBtn').addEventListener('click', closeModal);
document.getElementById('orderCancelBtn').addEventListener('click', closeModal);
modalBackdrop.addEventListener('click', (e) => { if (e.target === modalBackdrop) closeModal(); });

/* ── Customer Mobile Autocomplete (live search as user types) ─────────── */
fieldMobile.addEventListener('keypress', (e) => {
  // Allow only digits, backspace, delete, arrow keys, tab
  if (!/[0-9]/.test(e.key) && !['Backspace', 'Delete', 'ArrowLeft', 'ArrowRight', 'Tab'].includes(e.key)) {
    e.preventDefault();
  }
});

fieldMobile.addEventListener('input', () => {
  // Strip any non-digit characters (e.g. paste)
  const cleaned = fieldMobile.value.replace(/\D/g, '').slice(0, 10);
  if (fieldMobile.value !== cleaned) fieldMobile.value = cleaned;

  clearTimeout(_lookupTimer);
  _customerValid = false;
  _selectedCustomer = null;
  lookupResult.classList.remove('visible');
  hideAutocomplete();
  hideCustomerDue();
  if (!_isNewCustomer) hideNewCustomerBanner();
  document.getElementById('errCustomerMobile').classList.remove('visible');

  const query = fieldMobile.value.trim();
  if (!query) {
    hideNewCustomerBanner();
    return;
  }

  _lookupTimer = setTimeout(async () => {
    try {
      const data = await Api.get(`${API.CUSTOMERS}?search=${encodeURIComponent(query)}&size=8`);
      const customers = data?.content ?? [];

      if (customers.length === 1 && customers[0].customerMobile === query) {
        // Exact single match → auto-select
        selectCustomer(customers[0].customerMobile, customers[0].customerName);
      } else if (customers.length > 0) {
        showAutocomplete(customers, query);
      } else {
        // No match found
        if (query.length === 10) {
          if (/^[6-9]\d{9}$/.test(query)) {
            // Exactly 10 valid digits typed & no match → show new customer name field directly
            hideAutocomplete();
            showNewCustomerBanner();
          } else {
            hideAutocomplete();
            hideNewCustomerBanner();
            document.getElementById('errCustomerMobile').textContent = 'Please enter a valid 10-digit Indian mobile number';
            document.getElementById('errCustomerMobile').classList.add('visible');
          }
        } else {
          // Fewer than 10 digits typed → show dropdown with "+ Create New Customer" option
          showAutocomplete([], query);
        }
      }
    } catch (_) {
      hideAutocomplete();
    }
  }, 250);
});

/* ── Save Order ───────────────────────────────────────────────────────── */
document.getElementById('orderSaveBtn').addEventListener('click', async () => {
  let valid = true;

  const mobile = fieldMobile.value.trim();

  if (!mobile || !/^[6-9]\d{9}$/.test(mobile)) {
    document.getElementById('errCustomerMobile').textContent = 'Please enter a valid 10-digit Indian mobile number';
    document.getElementById('errCustomerMobile').classList.add('visible');
    fieldMobile.focus();
    valid = false;
  } else if (_isNewCustomer) {
    const newName = fieldNewName.value.trim();
    if (!newName) {
      document.getElementById('errNewCustomerName').classList.add('visible');
      fieldNewName.focus();
      valid = false;
    } else {
      document.getElementById('errNewCustomerName').classList.remove('visible');
    }
  } else {
    // Case B: existing customer — must have been selected from dropdown
    if (!_customerValid) {
      document.getElementById('errCustomerMobile').textContent = 'Please select a valid customer from the dropdown';
      document.getElementById('errCustomerMobile').classList.add('visible');
      fieldMobile.focus();
      valid = false;
    } else {
      document.getElementById('errCustomerMobile').classList.remove('visible');
    }
  }

  // Validate delivery date
  const deliveryDate = fieldDate.value;
  if (!deliveryDate) {
    document.getElementById('errDeliveryDate').classList.add('visible');
    valid = false;
  } else {
    document.getElementById('errDeliveryDate').classList.remove('visible');
  }

  // Validate total
  const total = parseFloat(fieldTotal.value);
  if (isNaN(total) || total <= 0) {
    document.getElementById('errTotalAmount').classList.add('visible');
    valid = false;
  } else {
    document.getElementById('errTotalAmount').classList.remove('visible');
  }

  if (!valid) return;

  const advance = parseFloat(fieldAdvance.value) || 0;

  saveBtn.disabled = true;
  saveBtn.innerHTML = `<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
      style="animation:spin 0.7s linear infinite;">
      <path d="M12 2v4M12 18v4M4.93 4.93l2.83 2.83M16.24 16.24l2.83 2.83M2 12h4M18 12h4M4.93 19.07l2.83-2.83M16.24 7.76l2.83-2.83"/>
    </svg> Saving…`;

  try {
    // If new customer → register them first
    if (_isNewCustomer) {
      const newName = fieldNewName.value.trim();
      await Api.post(API.CUSTOMERS, {
        customerMobile: mobile,
        customerName: newName,
      });
      Toast.info(`Customer "${newName}" registered`);
    }

    // Collect measurements if garment type selected
    const garmentType = fieldGarmentType?.value || null;
    const measurements = {};
    if (garmentType === 'BLOUSE' && blousePanel) {
      blousePanel.querySelectorAll('input[data-key]').forEach(inp => {
        const val = inp.value.trim();
        if (val) measurements[inp.dataset.key] = val;
      });
    } else if (garmentType === 'CHUDI' && chudiPanel) {
      chudiPanel.querySelectorAll('input[data-key]').forEach(inp => {
        const val = inp.value.trim();
        if (val) measurements[inp.dataset.key] = val;
      });
    }

    const lining = fieldLining?.value || null;

    const payload = {
      customerMobile: mobile,
      deliveryDate: new Date(deliveryDate + 'T18:00:00').toISOString(),
      totalAmount: total,
      advanceAmount: advance,
    };
    if (garmentType) payload.garmentType = garmentType;
    if (lining) payload.lining = lining;
    if (Object.keys(measurements).length > 0) payload.measurements = measurements;

    const order = await Api.post(API.ORDERS, payload);

    // After edit and save, update the record of saved measurements in customer profile
    if (garmentType && Object.keys(measurements).length > 0 && mobile) {
      try {
        const customerName = _selectedCustomer?.customerName || document.getElementById('fieldNewCustomerName')?.value.trim() || 'Customer';
        await Api.post(API.MEASUREMENTS, {
          customerMobile: mobile,
          customerName: customerName,
          garmentType: garmentType,
          lining: lining,
          measurements: measurements,
          notes: `Updated from Order ${order?.orderNumber || ''}`
        });
        console.log(`Updated saved measurement profile for ${mobile} (${garmentType})`);
      } catch (cmErr) {
        console.warn('Could not auto-sync customer measurement profile:', cmErr);
      }
    }

    closeModal();
    Toast.success(`Order ${order.orderNumber} created & measurements saved!`);
    await Promise.all([loadStats(), loadOrders()]);
    // Auto-open the details modal for the just-created order
    if (order.id) window.viewOrder(order.id);
  } catch (err) {
    Toast.error(err?.message || 'Failed to create order');
    saveBtn.disabled = false;
    saveBtn.innerHTML = `
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"/></svg>
      Create Order`;
  }
});

/* ── Enter key in modal ───────────────────────────────────────────────── */
document.querySelector('.order-modal').addEventListener('keydown', e => {
  if (e.key === 'Enter') { e.preventDefault(); saveBtn.click(); }
});

/* ── Add spin keyframe if not already present ─────────────────────────── */
(function () {
  if (!document.getElementById('_orderSpinStyle')) {
    const s = document.createElement('style');
    s.id = '_orderSpinStyle';
    s.textContent = '@keyframes spin { to { transform: rotate(360deg); } }';
    document.head.appendChild(s);
  }
})();

/* ── Init ─────────────────────────────────────────────────────────────── */
document.addEventListener('DOMContentLoaded', async () => {
  if (!Router.protect([ROLES.ADMIN, ROLES.RECEPTION, ROLES.OPERATIONS_MANAGER, ROLES.PRODUCTION_EMPLOYEE])) return;

  Sidebar.init({ activePage: 'order' });
  Header.init({ title: 'Orders', subtitle: 'Order Management' });

  if (Utils.isReadOnlyUser()) {
    const newBtn = document.getElementById('openModalBtn') || document.querySelector('.page-header-actions .btn-primary');
    if (newBtn) newBtn.style.display = 'none';
  }

  await Promise.all([loadStats(), loadOrders()]);

  // ── Auto-polling & Focus Auto-refresh ──────────────────────────────────────
  const refreshAllOrders = () => Promise.all([loadStats(), loadOrders()]);
  setInterval(refreshAllOrders, 10000);
  window.addEventListener('focus', refreshAllOrders);

  // Check URL params to auto-open modal or filter search
  const params = new URLSearchParams(window.location.search);
  if (params.get('action') === 'new' || params.has('create') || params.has('new')) {
    openModal();
    const mob = params.get('mobile');
    if (mob) {
      fieldMobile.value = mob;
      fieldMobile.dispatchEvent(new Event('input'));
    }
  } else if (params.has('id')) {
    window.viewOrder(params.get('id'));
  } else if (params.has('search')) {
    currentSearch = params.get('search');
    if (searchInput) searchInput.value = currentSearch;
    await loadOrders();
  }
});

/* ── View Order Details Modal ─────────────────────────────────────────── */

function closeViewModal() {
  document.getElementById('viewOrderModalBackdrop')?.classList.add('hidden');
}

document.getElementById('closeViewOrderModalBtn')?.addEventListener('click', closeViewModal);
document.getElementById('closeViewOrderModalFooterBtn')?.addEventListener('click', closeViewModal);
document.getElementById('viewOrderModalBackdrop')?.addEventListener('click', e => {
  if (e.target.id === 'viewOrderModalBackdrop') closeViewModal();
});

window.viewOrder = async function (id) {
  const modal = document.getElementById('viewOrderModalBackdrop');
  const body = document.getElementById('viewOrderModalBody');
  const title = document.getElementById('viewOrderNumber');
  const sub = document.getElementById('viewOrderSub');

  if (!modal || !body) return;

  body.innerHTML = `
    <div style="padding:40px;text-align:center;color:var(--text-muted);">
      <div class="spinner" style="margin:0 auto 12px;width:24px;height:24px;border:2px solid rgba(255,255,255,0.1);border-top-color:#888;border-radius:50%;animation:spin 0.7s linear infinite;"></div>
      Loading order details…
    </div>
  `;
  modal.classList.remove('hidden');

  try {
    const order = await Api.get(`${API.ORDERS}/${id}`);

    if (title) title.textContent = order.orderNumber || `#${order.id}`;
    if (sub) sub.textContent = `Registered on ${Utils.formatDate(order.orderDate || order.createdAt)}`;

    const stgBadge = statusBadge(order.status);
    const payBadge = paymentBadge(order.paymentStatus);
    const totalAmt = Number(order.totalAmount || 0);
    const discAmt = Number(order.discountAmount || 0);
    const advAmt = Number(order.advanceAmount || 0);
    const paidAmt = Number(order.paidAmount || 0);
    const balAmt = order.balanceAmount != null ? Number(order.balanceAmount) : Math.max(0, totalAmt - discAmt - paidAmt);

    body.innerHTML = `
      <div style="display:flex;flex-direction:column;gap:16px;">
        <!-- Status Row -->
        <div style="display:flex;align-items:center;justify-content:space-between;background:rgba(255,255,255,0.03);padding:12px 16px;border-radius:8px;border:1px solid rgba(255,255,255,0.08);">
          <div>
            <div style="font-size:11px;color:var(--text-muted);text-transform:uppercase;letter-spacing:0.5px;">Current Stage</div>
            <div style="margin-top:4px;">${stgBadge}</div>
          </div>
          <div style="text-align:right;">
            <div style="font-size:11px;color:var(--text-muted);text-transform:uppercase;letter-spacing:0.5px;">Payment Status</div>
            <div style="margin-top:4px;">${payBadge}</div>
          </div>
        </div>

        <!-- Production Stage Quick Changer (Floor Staff Only) -->
        ${Utils.canAdvanceProductionStage() ? `
        <div style="background:rgba(99,102,241,0.06);padding:14px 16px;border-radius:8px;border:1px solid rgba(99,102,241,0.2);">
          <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:8px;">
            <div style="font-size:11px;font-weight:700;color:var(--text-secondary);text-transform:uppercase;letter-spacing:0.5px;">Change Production Stage</div>
            <span style="font-size:11px;color:var(--text-muted);">Assigned: <strong style="color:var(--text-primary);">${order.assignedEmployeeName || 'Unassigned'}</strong></span>
          </div>
          <div style="display:flex;gap:8px;flex-wrap:wrap;align-items:center;">
            <select id="quickStageSelect" class="order-form-input" style="flex:1;min-width:160px;height:38px;padding:6px 10px;font-size:13px;background:var(--bg-surface-2);color:var(--text-primary);border-radius:6px;border:1px solid var(--border-default);">
              ${(typeof StageRegistry !== 'undefined' ? StageRegistry.getAll() : []).map(s => `
                <option value="${s.stageKey}" ${s.stageKey === (order.status || '').toUpperCase() ? 'selected' : ''}>${s.title}</option>
              `).join('')}
            </select>
            <select id="quickWorkerSelect" class="order-form-input" style="flex:1;min-width:160px;height:38px;padding:6px 10px;font-size:13px;background:var(--bg-surface-2);color:var(--text-primary);border-radius:6px;border:1px solid var(--border-default);">
              <option value="">-- Auto-assign Stage Worker --</option>
            </select>
            <button id="btnApplyStageChange" class="btn btn-primary" style="padding:8px 18px;height:38px;font-size:13px;white-space:nowrap;font-weight:600;cursor:pointer;">
              Update Stage
            </button>
          </div>
        </div>
        ` : ''}

        <!-- Customer Info -->
        <div style="background:rgba(255,255,255,0.03);padding:14px 16px;border-radius:8px;border:1px solid rgba(255,255,255,0.08);">
          <div style="font-size:11px;font-weight:600;color:var(--text-secondary);text-transform:uppercase;letter-spacing:0.5px;margin-bottom:8px;">Customer Information</div>
          <div style="display:grid;grid-template-columns:1fr 1fr;gap:12px;">
            <div>
              <div style="font-size:11px;color:var(--text-muted);">Name</div>
              <div style="font-weight:600;font-size:14px;color:var(--text-primary);margin-top:2px;">${order.customerName || 'Customer'}</div>
            </div>
            <div>
              <div style="font-size:11px;color:var(--text-muted);">Mobile Number</div>
              <div style="font-family:monospace;font-size:13px;color:var(--text-primary);margin-top:2px;">${order.customerMobile || '—'}</div>
            </div>
          </div>
        </div>

        ${order.garmentType ? `
        <!-- Garment & Measurements -->
        <div class="view-measurements-card">
          <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:8px;">
            <div style="font-size:11px;font-weight:600;color:var(--text-secondary);text-transform:uppercase;letter-spacing:0.5px;">Garment & Measurements</div>
            <div style="display:flex;align-items:center;gap:6px;">
              <span class="garment-badge ${order.garmentType === 'BLOUSE' ? 'blouse-badge' : 'chudi-badge'}">
                ${order.garmentType === 'BLOUSE' ? '👗 Blouse' : '👘 Chudi'}
              </span>
              ${order.lining ? `
                <span class="badge" style="background:var(--bg-surface-3);color:var(--text-primary);padding:3px 8px;border-radius:4px;font-size:11px;font-weight:600;border:1px solid var(--border-default);">
                  ${order.lining === 'WITH_LINING' ? '✨ With Lining' : 'Without Lining'}
                </span>
              ` : ''}
            </div>
          </div>
          ${order.measurements && Object.keys(order.measurements).length > 0 ? `
            <div class="view-measurements-table">
              ${Object.entries(order.measurements).map(([k, v]) => `
                <div class="view-m-item">
                  <span class="view-m-key">${k}</span>
                  <span class="view-m-val">${v}</span>
                </div>
              `).join('')}
            </div>
          ` : `
            <div style="font-size:12px;color:var(--text-muted);font-style:italic;margin-top:6px;">No specific measurements recorded</div>
          `}
        </div>
        ` : ''}

        <!-- Dates -->
        <div style="display:grid;grid-template-columns:1fr 1fr;gap:12px;">
          <div style="background:rgba(255,255,255,0.03);padding:12px 16px;border-radius:8px;border:1px solid rgba(255,255,255,0.08);">
            <div style="font-size:11px;color:var(--text-muted);">Order Date</div>
            <div style="font-weight:600;font-size:13px;color:var(--text-primary);margin-top:2px;">${Utils.formatDate(order.orderDate || order.createdAt)}</div>
          </div>
          <div style="background:rgba(255,255,255,0.03);padding:12px 16px;border-radius:8px;border:1px solid rgba(255,255,255,0.08);">
            <div style="font-size:11px;color:var(--text-muted);">Target Delivery Date</div>
            <div style="font-weight:600;font-size:13px;color:var(--status-completed);margin-top:2px;">${Utils.formatDate(order.deliveryDate)}</div>
          </div>
        </div>

        <!-- Financial Summary (hidden for Operations Managers) -->
        ${!isOperationsManager() ? `
        <div style="background:rgba(99,102,241,0.08);padding:16px;border-radius:8px;border:1px solid rgba(99,102,241,0.2);">
          <div style="font-size:11px;font-weight:600;color:var(--text-secondary);text-transform:uppercase;letter-spacing:0.5px;margin-bottom:10px;">Payment Breakdown</div>
          <div style="display:grid;grid-template-columns:${discAmt > 0 ? 'repeat(4, 1fr)' : 'repeat(3, 1fr)'};gap:8px;text-align:center;">
            <div>
              <div style="font-size:10px;color:var(--text-muted);">Total Order</div>
              <div style="font-weight:700;font-size:15px;color:var(--text-primary);margin-top:2px;">₹${totalAmt.toLocaleString('en-IN')}</div>
            </div>
            ${discAmt > 0 ? `
            <div>
              <div style="font-size:10px;color:var(--text-muted);">Discount</div>
              <div style="font-weight:700;font-size:15px;color:#F59E0B;margin-top:2px;">-₹${discAmt.toLocaleString('en-IN')}</div>
            </div>
            ` : ''}
            <div>
              <div style="font-size:10px;color:var(--text-muted);">${paidAmt > advAmt ? 'Total Paid' : 'Advance / Paid'}</div>
              <div style="font-weight:700;font-size:15px;color:var(--status-completed);margin-top:2px;">₹${paidAmt.toLocaleString('en-IN')}</div>
            </div>
            <div>
              <div style="font-size:10px;color:var(--text-muted);">Balance Due</div>
              <div style="font-weight:700;font-size:15px;color:${balAmt > 0 ? 'var(--status-delayed)' : 'var(--status-completed)'};margin-top:2px;">₹${balAmt.toLocaleString('en-IN')}</div>
            </div>
          </div>
        </div>
        ` : ''}

        ${order.status === 'READY_TO_DELIVERY' ? `
        <!-- Mark as Delivered Button -->
        <div style="margin-top:4px;">
          <button
            id="markDeliveredBtn"
            onclick="markAsDelivered(${order.id}, '${(order.orderNumber || '').replace(/'/g, "\\'")}')"
            style="
              width:100%;
              padding:13px 20px;
              background:linear-gradient(135deg,#16a34a,#15803d);
              color:#fff;
              border:none;
              border-radius:8px;
              font-size:14px;
              font-weight:600;
              font-family:inherit;
              cursor:pointer;
              display:flex;
              align-items:center;
              justify-content:center;
              gap:8px;
              letter-spacing:0.3px;
              transition:opacity 0.15s ease, transform 0.1s ease;
              box-shadow:0 2px 12px rgba(22,163,74,0.35);
            "
            onmouseover="this.style.opacity='0.88'"
            onmouseout="this.style.opacity='1'"
            onmousedown="this.style.transform='scale(0.98)'"
            onmouseup="this.style.transform=''"
          >
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
              <path d="M5 12l5 5L20 7"/>
            </svg>
            Mark as Delivered
          </button>
        </div>
        ` : order.status === 'DELIVERED' ? `
        <!-- Already Delivered Banner -->
        <div style="
          display:flex;flex-direction:column;align-items:center;justify-content:center;gap:4px;
          padding:12px 16px;
          background:var(--status-completed-bg);
          border:1px solid var(--status-completed-border);
          border-radius:8px;
          color:var(--status-completed);
          font-size:13px;
          font-weight:600;
        ">
          <div style="display:flex;align-items:center;gap:8px;">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
              <path d="M5 12l5 5L20 7"/>
            </svg>
            Order Delivered Successfully
          </div>
          ${order.receiverName ? `
            <div style="font-size:12px;color:var(--text-secondary);font-weight:400;margin-top:2px;">
              Received by: <strong style="color:var(--text-primary);">${order.receiverName}</strong>
            </div>
          ` : ''}
        </div>
        ` : ''}
      </div>
    `;

    // ── Wire up Stage Changer ───────────────────────────────────────────────
    const quickStageSelect = document.getElementById('quickStageSelect');
    const quickWorkerSelect = document.getElementById('quickWorkerSelect');
    const btnApplyStageChange = document.getElementById('btnApplyStageChange');

    if (quickStageSelect && quickWorkerSelect && btnApplyStageChange) {
      let emps = [];
      try {
        const res = await Api.get(`${API.EMPLOYEES}?size=100`);
        emps = res?.content ?? res?.data?.content ?? [];
      } catch (_) { }

      function updateWorkerDropdown(selectedKey) {
        const stageStaff = emps.filter(e => (e.stage || '').toUpperCase() === (selectedKey || '').toUpperCase() && e.isActive !== false);
        if (stageStaff.length > 0) {
          quickWorkerSelect.innerHTML = stageStaff.map(e => `
            <option value="${e.id}" data-name="${e.fullName}" ${e.id === order.assignedEmployeeId ? 'selected' : ''}>
              ${e.fullName} (${e.employeeCode})
            </option>
          `).join('');
        } else {
          quickWorkerSelect.innerHTML = `<option value="">-- No specific staff for stage --</option>` +
            emps.filter(e => e.isActive !== false && !['EMP-001', 'EMP-002', 'EMP-003', 'EMP-004'].includes(e.employeeCode)).map(e => `
              <option value="${e.id}" data-name="${e.fullName}">${e.fullName} (${e.employeeCode})</option>
            `).join('');
        }
      }

      updateWorkerDropdown(quickStageSelect.value);
      quickStageSelect.addEventListener('change', () => updateWorkerDropdown(quickStageSelect.value));

      btnApplyStageChange.addEventListener('click', async () => {
        const targetStage = quickStageSelect.value;
        const selectedWorkerOpt = quickWorkerSelect.options[quickWorkerSelect.selectedIndex];
        const assignedId = selectedWorkerOpt?.value ? Number(selectedWorkerOpt.value) : null;
        const assignedName = selectedWorkerOpt?.getAttribute('data-name') || null;

        Utils.setLoading(btnApplyStageChange, true, 'Updating...');
        try {
          await Api.put(`${API.ORDERS}/${order.id}/status`, {
            status: targetStage,
            assignedEmployeeId: assignedId,
            assignedEmployeeName: assignedName
          });
          Toast.success(`Production stage updated to ${targetStage}`);
          await Promise.all([loadStats(), loadOrders()]);
          window.viewOrder(order.id);
        } catch (err) {
          Toast.error(err?.message || 'Failed to update stage');
        } finally {
          Utils.setLoading(btnApplyStageChange, false);
        }
      });
    }

  } catch (err) {
    body.innerHTML = `
      <div style="padding:30px;text-align:center;color:#F87171;">
        Could not load order details. ${err.message || ''}
      </div>
    `;
  }
};

/* ── Mark as Delivered Modal Logic ────────────────────────────────────── */
let _activeDeliveryOrder = null;

const dlvModalBackdrop = document.getElementById('deliveryConfirmModalBackdrop');
const dlvCloseBtn = document.getElementById('closeDeliveryConfirmModalBtn');
const dlvCancelBtn = document.getElementById('cancelDeliveryConfirmBtn');
const dlvSubmitBtn = document.getElementById('submitDeliveryConfirmBtn');

function closeDeliveryConfirmModal() {
  if (dlvModalBackdrop) dlvModalBackdrop.classList.add('hidden');
  const discInput = document.getElementById('dlvOrderDiscount');
  if (discInput) discInput.value = '';
  const feedbackEl = document.getElementById('dlvOrderLiveFeedback');
  if (feedbackEl) feedbackEl.style.display = 'none';
  _activeDeliveryOrder = null;
}

function updateOrderDeliveryCalculation() {
  if (!_activeDeliveryOrder) return;
  const total = Number(_activeDeliveryOrder.totalAmount || 0);
  const alreadyPaid = Number(_activeDeliveryOrder.paidAmount || 0);

  const discountVal = parseFloat(document.getElementById('dlvOrderDiscount')?.value || 0) || 0;
  const amountRecVal = parseFloat(document.getElementById('dlvAmountPaid')?.value || 0) || 0;

  const netTotal = Math.max(0, total - discountVal);
  const totalPaid = alreadyPaid + amountRecVal;
  const remainingBalance = Math.max(0, netTotal - totalPaid);
  const extraPaid = Math.max(0, totalPaid - netTotal);

  const balEl = document.getElementById('dlvBalanceSummary');
  if (balEl) {
    balEl.textContent = `₹${remainingBalance.toLocaleString('en-IN')}`;
    balEl.style.color = remainingBalance > 0 ? '#F87171' : '#4ADE80';
  }

  const feedbackEl = document.getElementById('dlvOrderLiveFeedback');
  if (feedbackEl) {
    if (extraPaid > 0) {
      feedbackEl.style.display = 'block';
      feedbackEl.style.background = 'rgba(99,102,241,0.12)';
      feedbackEl.style.color = '#818CF8';
      feedbackEl.style.border = '1px solid rgba(99,102,241,0.3)';
      feedbackEl.innerHTML = `🌟 Fully Paid with <strong>+₹${extraPaid.toLocaleString('en-IN')} Extra</strong> credited to order!`;
    } else if (remainingBalance === 0) {
      feedbackEl.style.display = 'block';
      feedbackEl.style.background = 'rgba(16,185,129,0.12)';
      feedbackEl.style.color = '#4ADE80';
      feedbackEl.style.border = '1px solid rgba(16,185,129,0.3)';
      feedbackEl.innerHTML = `✓ Fully Settled &amp; Paid (₹0 Due)`;
    } else {
      feedbackEl.style.display = 'block';
      feedbackEl.style.background = 'rgba(245,158,11,0.12)';
      feedbackEl.style.color = '#FBBF24';
      feedbackEl.style.border = '1px solid rgba(245,158,11,0.3)';
      feedbackEl.innerHTML = `⚠️ Partial Payment: <strong>₹${remainingBalance.toLocaleString('en-IN')}</strong> will remain in balance.`;
    }
  }

  const payModeContainer = document.getElementById('dlvPaymentModeContainer');
  if (payModeContainer) {
    payModeContainer.style.display = amountRecVal > 0 ? 'block' : 'none';
  }
}

document.getElementById('dlvOrderDiscount')?.addEventListener('input', updateOrderDeliveryCalculation);
document.getElementById('dlvAmountPaid')?.addEventListener('input', updateOrderDeliveryCalculation);

dlvCloseBtn?.addEventListener('click', closeDeliveryConfirmModal);
dlvCancelBtn?.addEventListener('click', closeDeliveryConfirmModal);
dlvModalBackdrop?.addEventListener('click', (e) => {
  if (e.target === dlvModalBackdrop) closeDeliveryConfirmModal();
});

dlvSubmitBtn?.addEventListener('click', async () => {
  if (!_activeDeliveryOrder) return;

  const receiverInput = document.getElementById('dlvReceiverName');
  const errReceiver = document.getElementById('errDlvReceiverName');
  const amountInput = document.getElementById('dlvAmountPaid');
  const discountInput = document.getElementById('dlvOrderDiscount');
  const modeSelect = document.getElementById('dlvPaymentMode');

  const receiverName = receiverInput?.value.trim() || '';
  if (!receiverName) {
    if (errReceiver) errReceiver.style.display = 'block';
    receiverInput?.focus();
    return;
  }
  if (errReceiver) errReceiver.style.display = 'none';

  const amountPaidVal = parseFloat(amountInput?.value || 0) || 0;
  const discountAmt = parseFloat(discountInput?.value || 0) || 0;
  const paymentMode = modeSelect?.value || 'CASH';

  Utils.setLoading(dlvSubmitBtn, true, 'Confirming Delivery...');
  try {
    const payload = {
      status: 'DELIVERED',
      receiverName: receiverName
    };
    if (discountAmt > 0) {
      payload.discountAmount = String(discountAmt);
    }
    if (amountPaidVal > 0) {
      payload.finalPayment = String(amountPaidVal);
      payload.paymentMode = paymentMode;
    }

    await Api.put(`${API.ORDERS}/${_activeDeliveryOrder.id}/status`, payload);
    Toast.success(`Order ${_activeDeliveryOrder.orderNumber || ''} marked as Delivered to ${receiverName}! 🎉`);
    closeDeliveryConfirmModal();

    await Promise.all([
      window.viewOrder(_activeDeliveryOrder.id),
      loadStats(),
      loadOrders()
    ]);
  } catch (err) {
    Toast.error(err?.message || 'Could not complete delivery');
  } finally {
    Utils.setLoading(dlvSubmitBtn, false);
  }
});

window.markAsDelivered = async function (id, orderNum) {
  try {
    const order = await Api.get(`${API.ORDERS}/${id}`);
    _activeDeliveryOrder = order;

    const totalAmt = Number(order.totalAmount || 0);
    const paidAmt = Number(order.paidAmount || 0);
    const balAmt = Math.max(0, totalAmt - paidAmt);

    const subEl = document.getElementById('dlvConfirmSubtitle');
    if (subEl) subEl.textContent = `${order.orderNumber || `#${order.id}`}`;

    const custEl = document.getElementById('dlvCustomerSummary');
    if (custEl) custEl.textContent = `${order.customerName || '—'} (${order.customerMobile || '—'})`;

    const totEl = document.getElementById('dlvTotalSummary');
    if (totEl) totEl.textContent = `₹${totalAmt.toLocaleString('en-IN')}`;

    const pdEl = document.getElementById('dlvPaidSummary');
    if (pdEl) pdEl.textContent = `₹${paidAmt.toLocaleString('en-IN')}`;

    const balEl = document.getElementById('dlvBalanceSummary');
    if (balEl) balEl.textContent = `₹${balAmt.toLocaleString('en-IN')}`;

    const receiverInput = document.getElementById('dlvReceiverName');
    if (receiverInput) {
      receiverInput.value = order.customerName || '';
    }
    const errReceiver = document.getElementById('errDlvReceiverName');
    if (errReceiver) errReceiver.style.display = 'none';

    const discountInput = document.getElementById('dlvOrderDiscount');
    if (discountInput) discountInput.value = '';

    const amountInput = document.getElementById('dlvAmountPaid');
    const amountHelp = document.getElementById('dlvAmountHelp');
    if (amountInput) {
      amountInput.value = balAmt > 0 ? balAmt : '';
    }
    if (amountHelp) {
      amountHelp.textContent = balAmt > 0 ? `Balance Due: ₹${balAmt.toLocaleString('en-IN')}` : 'Fully Paid (₹0 Due)';
    }

    const payModeContainer = document.getElementById('dlvPaymentModeContainer');
    if (payModeContainer) {
      payModeContainer.style.display = balAmt > 0 ? 'block' : 'none';
    }

    updateOrderDeliveryCalculation();

    if (dlvModalBackdrop) {
      dlvModalBackdrop.classList.remove('hidden');
      setTimeout(() => receiverInput?.focus(), 150);
    }
  } catch (err) {
    Toast.error('Could not load order details for delivery');
  }
};
