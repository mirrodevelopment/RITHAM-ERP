/**
 * Ritham ERP — Order Desk Controller (order-desk.js)
 * Desktop Order-Taking, Reception & Live Floor Intake Dashboard
 */

'use strict';

let _allOrders = [];
let _activeFilter = 'ALL';
let _activeSearchQuery = '';

document.addEventListener('DOMContentLoaded', async () => {

  // ── Auth Guard ─────────────────────────────────────────────────────────
  if (!Router.protect([ROLES.ADMIN, ROLES.RECEPTION, ROLES.OPERATIONS_MANAGER])) return;

  // ── Init Components ───────────────────────────────────────────────────
  Sidebar.init({ activePage: 'dashboard1' });
  Header.init({ title: 'Order Desk', subtitle: 'Desktop Order-Taking Dashboard' });

  // ── Init Stage Registry ───────────────────────────────────────────────
  await StageRegistry.init();

  // ── Initial Data Load ──────────────────────────────────────────────────
  await Promise.allSettled([loadStats(), loadRecentOrders()]);

  // ── Wire Actions ───────────────────────────────────────────────────────
  document.getElementById('newOrderBtn')?.addEventListener('click',
    () => window.location.href = '../order/order.html?action=new');

  // ── Refresh Button ────────────────────────────────────────────────────
  document.getElementById('refreshBtn')?.addEventListener('click', async () => {
    const btn = document.getElementById('refreshBtn');
    if (btn) btn.classList.add('loading');
    Toast.info('Refreshing order desk...');
    await Promise.allSettled([loadStats(), loadRecentOrders()]);
    if (btn) btn.classList.remove('loading');
    Toast.success('Order desk updated');
  });

  // ── Export Excel Button ───────────────────────────────────────────────
  document.getElementById('exportOrderDeskExcelBtn')?.addEventListener('click', async () => {
    const listToExport = Array.isArray(_filteredOrders) && _filteredOrders.length > 0 ? _filteredOrders : _allOrders;
    if (!listToExport || !listToExport.length) {
      Toast.warning('No orders available to export.');
      return;
    }
    const columns = [
      { key: 'sno', header: 'S.No' },
      { key: 'orderNumber', header: 'Order Number', transform: (v, o) => v || `#${o.id}` },
      { key: 'customerName', header: 'Customer Name', transform: v => v || '—' },
      { key: 'customerMobile', header: 'Mobile Number', transform: v => v || '—' },
      { key: 'garmentType', header: 'Garment Type', transform: v => v || '—' },
      { key: 'orderDate', header: 'Order Date', transform: (v, o) => ExcelExport.formatDate(v || o.createdAt) },
      { key: 'deliveryDate', header: 'Delivery Date', transform: v => ExcelExport.formatDate(v) },
      { key: 'totalAmount', header: 'Total (Rs)', transform: v => Number(v || 0) },
      { key: 'paidAmount', header: 'Paid (Rs)', transform: v => Number(v || 0) },
      { key: 'balanceDue', header: 'Balance Due (Rs)', transform: (_, o) => Math.max(0, Number(o.totalAmount || 0) - Number(o.paidAmount || 0)) },
      { key: 'status', header: 'Status / Stage', transform: v => v || '—' },
      { key: 'assignedEmployeeName', header: 'Assigned Staff', transform: v => v || 'Unassigned' }
    ];
    await ExcelExport.exportData({
      data: listToExport,
      fileName: 'ritham-orderdesk-recent',
      sheetName: 'Recent Orders',
      columns,
    });
  });

  // ── Search & Filter Controls ──────────────────────────────────────────
  const searchInput = document.getElementById('orderSearch');
  const clearBtn    = document.getElementById('searchClearBtn');

  searchInput?.addEventListener('input', Utils.debounce((e) => {
    _activeSearchQuery = (e.target.value || '').trim().toLowerCase();
    if (clearBtn) clearBtn.style.display = _activeSearchQuery ? 'block' : 'none';
    applyFilters();
  }, 250));

  clearBtn?.addEventListener('click', () => {
    if (searchInput) {
      searchInput.value = '';
      _activeSearchQuery = '';
      clearBtn.style.display = 'none';
      applyFilters();
      searchInput.focus();
    }
  });

  // Filter Tabs
  const filterTabs = document.querySelectorAll('.od-tab-btn');
  filterTabs.forEach(tab => {
    tab.addEventListener('click', () => {
      filterTabs.forEach(t => t.classList.remove('active'));
      tab.classList.add('active');
      _activeFilter = tab.dataset.filter || 'ALL';
      applyFilters();
    });
  });

  // ── Auto-polling & Window Focus ───────────────────────────────────────
  const refreshAll = () => Promise.allSettled([loadStats(), loadRecentOrders(), loadStages()]);
  const pollInterval = setInterval(refreshAll, 12000);
  window.addEventListener('focus', refreshAll);
  window.addEventListener('beforeunload', () => clearInterval(pollInterval));

});

// ── Role Check ─────────────────────────────────────────────────────────────
function isOperationsManager() {
  const role = Auth.getRole();
  return role === ROLES.OPERATIONS_MANAGER || role === 'ROLE_OPERATIONS_MANAGER' || role === 'OPERATIONS_MANAGER';
}

// ── Load Stats ─────────────────────────────────────────────────────────────
async function loadStats() {
  try {
    const stats = await Api.get(`${API.ORDERS}/stats`);
    renderStats({
      todayOrders:      Number(stats.todayOrders  ?? 0),
      inProduction:     Number(stats.inProgress   ?? 0),
      readyForDelivery: Number(stats.completed    ?? 0),
      todayRevenue:     stats.todayRevenue != null ? Number(stats.todayRevenue) : 0,
    });
  } catch (_) {
    renderStats({
      todayOrders:      0,
      inProduction:     0,
      readyForDelivery: 0,
      todayRevenue:     0,
    });
  }
}

function renderStats(stats) {
  const statsRow = document.getElementById('statsRow');
  if (!statsRow) return;

  const isOps = isOperationsManager();

  statsRow.innerHTML = `
    <!-- 1. Today's Orders -->
    <a class="od-kpi-card" href="${ROUTES.ORDER}" title="View all orders">
      <div class="od-kpi-header">
        <span class="od-kpi-label">TODAY'S ORDERS</span>
        <div class="od-kpi-icon-box od-kpi-icon-orders">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M6 2L3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4z"></path>
            <line x1="3" y1="6" x2="21" y2="6"></line>
            <path d="M16 10a4 4 0 0 1-8 0"></path>
          </svg>
        </div>
      </div>
      <div class="od-kpi-value">${stats.todayOrders}</div>
      <div class="od-kpi-delta">
        <span class="od-kpi-pill">Intake</span>
        <span>New orders today</span>
      </div>
    </a>

    <!-- 2. In Production -->
    <a class="od-kpi-card" href="${ROUTES.PRODUCTION}" title="View production floor">
      <div class="od-kpi-header">
        <span class="od-kpi-label">IN PRODUCTION</span>
        <div class="od-kpi-icon-box od-kpi-icon-production">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="6" cy="6" r="3"></circle>
            <circle cx="6" cy="18" r="3"></circle>
            <line x1="20" y1="4" x2="8.12" y2="15.88"></line>
            <line x1="14.47" y1="14.48" x2="20" y2="20"></line>
            <line x1="8.12" y1="8.12" x2="12" y2="12"></line>
          </svg>
        </div>
      </div>
      <div class="od-kpi-value">${stats.inProduction}</div>
      <div class="od-kpi-delta">
        <span class="od-kpi-pill">Active</span>
        <span>Active production orders</span>
      </div>
    </a>

    <!-- 3. Ready for Delivery -->
    <a class="od-kpi-card" href="${ROUTES.DELIVERY}" title="View ready orders">
      <div class="od-kpi-header">
        <span class="od-kpi-label">READY FOR PICKUP</span>
        <div class="od-kpi-icon-box od-kpi-icon-ready">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"></path>
            <polyline points="3.27 6.96 12 12.01 20.73 6.96"></polyline>
            <line x1="12" y1="22.08" x2="12" y2="12"></line>
          </svg>
        </div>
      </div>
      <div class="od-kpi-value">${stats.readyForDelivery}</div>
      <div class="od-kpi-delta">
        <span class="od-kpi-pill">Ready</span>
        <span>Awaiting customer pickup</span>
      </div>
    </a>

    <!-- 4. Today's Revenue / Completed -->
    ${isOps ? `
      <a class="od-kpi-card" href="${ROUTES.PRODUCTION}" title="View production throughput">
        <div class="od-kpi-header">
          <span class="od-kpi-label">COMPLETED TODAY</span>
          <div class="od-kpi-icon-box od-kpi-icon-revenue">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path>
              <polyline points="22 4 12 14.01 9 11.01"></polyline>
            </svg>
          </div>
        </div>
        <div class="od-kpi-value">${stats.readyForDelivery}</div>
        <div class="od-kpi-delta">
          <span class="od-kpi-pill">Throughput</span>
          <span>Garments finished today</span>
        </div>
      </a>
    ` : `
      <a class="od-kpi-card" href="${ROUTES.REPORT}" title="View financial reports">
        <div class="od-kpi-header">
          <span class="od-kpi-label">TODAY'S REVENUE</span>
          <div class="od-kpi-icon-box od-kpi-icon-revenue">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <line x1="12" y1="1" x2="12" y2="23"></line>
              <path d="M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6"></path>
            </svg>
          </div>
        </div>
        <div class="od-kpi-value">${stats.todayRevenue !== null ? Utils.formatCurrency(stats.todayRevenue) : '₹0'}</div>
        <div class="od-kpi-delta">
          <span class="od-kpi-pill">Collected</span>
          <span>Cash & digital payments</span>
        </div>
      </a>
    `}
  `;
}

// ── Load Recent Orders ─────────────────────────────────────────────────────
async function loadRecentOrders() {
  const tbody = document.getElementById('ordersBody');
  if (!tbody) return;

  try {
    const data = await Api.get(`${API.ORDERS}?size=100&sort=createdAt,desc`);
    _allOrders = data?.content ?? data ?? [];

    const badge = document.getElementById('ordersCountBadge');
    if (badge) badge.textContent = `${_allOrders.length} Orders`;

    const subtitle = document.getElementById('ordersSubtitle');
    if (subtitle) subtitle.textContent = `Showing ${_allOrders.length} recent orders from active branch`;

    applyFilters();
  } catch (_) {
    tbody.innerHTML = `
      <tr>
        <td colspan="7">
          <div class="od-empty-state">
            <div class="od-empty-icon">
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10"></circle>
                <line x1="12" y1="8" x2="12" y2="12"></line>
                <line x1="12" y1="16" x2="12.01" y2="16"></line>
              </svg>
            </div>
            <div class="od-empty-title">Could not load orders</div>
            <div class="od-empty-sub">Please verify your server connection and retry.</div>
          </div>
        </td>
      </tr>
    `;
  }
}

// ── Filter & Search Logic ──────────────────────────────────────────────────
function applyFilters() {
  let list = [..._allOrders];

  // Tab Status Filtering
  if (_activeFilter === 'PENDING') {
    list = list.filter(o => {
      const s = (o.status || '').toUpperCase();
      return s === 'PENDING' || s === 'NEW' || s === 'CONFIRMED' || s === 'PATTERN_MAKING' || s === 'DESIGNING';
    });
  } else if (_activeFilter === 'IN_PROGRESS') {
    list = list.filter(o => {
      const s = (o.status || '').toUpperCase();
      return s !== 'PENDING' && s !== 'NEW' && s !== 'CONFIRMED' && s !== 'COMPLETED' && s !== 'DELIVERED' && s !== 'CANCELLED';
    });
  } else if (_activeFilter === 'COMPLETED') {
    list = list.filter(o => {
      const s = (o.status || '').toUpperCase();
      return s === 'COMPLETED' || s === 'DELIVERED';
    });
  }

  // Search Query
  if (_activeSearchQuery) {
    list = list.filter(o => {
      const num    = (o.orderNumber || '').toLowerCase();
      const cust   = (o.customerName || '').toLowerCase();
      const mob    = (o.customerMobile || '').toLowerCase();
      const garm   = (o.garmentType || '').toLowerCase();
      const stg    = (o.status || '').toLowerCase();
      return num.includes(_activeSearchQuery) ||
             cust.includes(_activeSearchQuery) ||
             mob.includes(_activeSearchQuery) ||
             garm.includes(_activeSearchQuery) ||
             stg.includes(_activeSearchQuery);
    });
  }

  renderOrdersTable(list);
}

// ── Render Orders Table ────────────────────────────────────────────────────
function renderOrdersTable(orders) {
  const tbody = document.getElementById('ordersBody');
  if (!tbody) return;

  const isOps = isOperationsManager();
  const amtCol = document.getElementById('colOrderDeskAmount');
  if (amtCol) amtCol.style.display = isOps ? 'none' : '';

  const colSpan = isOps ? 6 : 7;

  if (!orders || orders.length === 0) {
    tbody.innerHTML = `
      <tr>
        <td colspan="${colSpan}">
          <div class="od-empty-state">
            <div class="od-empty-icon">
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="11" cy="11" r="8"></circle>
                <line x1="21" y1="21" x2="16.65" y2="16.65"></line>
              </svg>
            </div>
            <div class="od-empty-title">No orders found</div>
            <div class="od-empty-sub">No matching orders found. Try adjusting your search or tab filters.</div>
          </div>
        </td>
      </tr>
    `;
    return;
  }

  tbody.innerHTML = orders.map(order => {
    // Garment styling
    const gType = (order.garmentType || '').toUpperCase();
    let garmentHtml = '';
    if (gType === 'BLOUSE') {
      garmentHtml = `<span class="od-garment-pill od-garment-blouse">👗 Blouse</span>`;
    } else if (gType === 'CHUDI') {
      garmentHtml = `<span class="od-garment-pill od-garment-chudi">👘 Chudi</span>`;
    } else if (gType === 'SAREE' || gType === 'SAREE_ROLLING') {
      garmentHtml = `<span class="od-garment-pill od-garment-generic">🥻 Saree</span>`;
    } else if (gType === 'ANARKALI') {
      garmentHtml = `<span class="od-garment-pill od-garment-generic">🧵 Anarkali</span>`;
    } else if (order.garmentType) {
      garmentHtml = `<span class="od-garment-pill od-garment-generic">${order.garmentType}</span>`;
    } else {
      garmentHtml = `<span style="font-size:12px; color:var(--text-muted);">${order.itemCount ?? 1} item${(order.itemCount ?? 1) !== 1 ? 's' : ''}</span>`;
    }

    // Due Date Urgency
    const dueFormatted = Utils.formatDate(order.deliveryDate);
    const isToday = isDateToday(order.deliveryDate);

    // Balance
    const balance = Number(order.balanceAmount ?? 0);

    return `
      <tr onclick="onOrderRowClick(event, '${order.id}')">
        <td>
          <span class="od-order-no-pill">${order.orderNumber || '#' + order.id}</span>
        </td>
        <td>
          <div class="od-customer-cell">
            <span class="od-customer-name">${order.customerName || 'Walk-in Customer'}</span>
            <div class="od-customer-sub">
              <span>${order.customerMobile || '—'}</span>
              ${order.customerDue ? `<span class="od-due-badge">⚠️ ₹${order.customerDue} Due</span>` : ''}
            </div>
          </div>
        </td>
        <td>${garmentHtml}</td>
        <td>
          <div class="od-date-cell">
            <span class="od-date-main">${dueFormatted}</span>
            ${isToday ? `<span class="od-date-urgent">Due Today</span>` : ''}
          </div>
        </td>
        ${!isOps ? `
          <td>
            <div class="od-amount-cell">
              <span class="od-amount-main">${Utils.formatCurrency(order.totalAmount)}</span>
              ${balance > 0 ? `<span class="od-amount-balance">Bal: ${Utils.formatCurrency(balance)}</span>` : ''}
            </div>
          </td>
        ` : ''}
        <td>${_statusBadge(order.status)}</td>
        <td style="text-align: right;">
          <button class="btn od-view-btn" onclick="event.stopPropagation(); viewOrder('${order.id}')">
            View
          </button>
        </td>
      </tr>
    `;
  }).join('');
}

function onOrderRowClick(event, id) {
  // Prevent redirect if clicking on link or button
  if (event.target.closest('button') || event.target.closest('a')) return;
  viewOrder(id);
}

function isDateToday(dateString) {
  if (!dateString) return false;
  const target = new Date(dateString);
  const today = new Date();
  return target.toDateString() === today.toDateString();
}

function viewOrder(id) {
  window.location.href = `../order/order.html?id=${id}`;
}

function _statusBadge(status) {
  if (typeof StageRegistry !== 'undefined') {
    const stg = StageRegistry.getByKey(status);
    if (stg) return StageRegistry.getBadgeHtml(status);
  }
  const STAGE_CONFIG = {
    COMPLETED: { cls: 'badge-completed', label: 'Completed' },
    DELIVERED: { cls: 'badge-completed', label: 'Delivered' },
    CANCELLED: { cls: 'badge-neutral',   label: 'Cancelled' },
  };
  const cfg = STAGE_CONFIG[status] || {
    cls: 'badge-inprogress',
    label: (status || 'IN PRODUCTION').replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, l => l.toUpperCase())
  };
  return `<span class="badge ${cfg.cls}">${cfg.label}</span>`;
}

// ── Production Stages Pipeline ─────────────────────────────────────────────
async function loadStages() {
  const panel = document.getElementById('stagesPanel');
  if (!panel) return;

  try {
    const stages = StageRegistry.getAll();
    const data = await Api.get(`${API.ORDERS}?size=100&sort=createdAt,desc`);
    const orders = data?.content ?? data ?? [];

    const counts = {};
    stages.forEach(s => counts[s.stageKey] = 0);

    let totalInProduction = 0;
    orders.forEach(o => {
      let stg = (o.status || '').toUpperCase();
      if (stg === 'PENDING' || stg === 'NEW' || stg === 'CONFIRMED' || stg === 'PATTERN_MAKING') stg = 'DESIGNING';
      else if (stg === 'IN_PROGRESS' || stg === 'FABRIC_CUTTING') stg = 'CUTTING';

      if (counts[stg] !== undefined) {
        counts[stg]++;
        totalInProduction++;
      }
    });

    panel.innerHTML = stages.map(s => {
      const count = counts[s.stageKey] ?? 0;
      const pct = totalInProduction > 0 ? Math.round((count / totalInProduction) * 100) : 0;

      return `
        <div class="od-pipeline-row" onclick="viewProductionStage('${s.stageKey}')" title="Filter ${s.title} in Production">
          <div class="od-pipeline-meta">
            <span class="od-pipeline-name">${s.title}</span>
            <span class="od-pipeline-count-pill">${count}</span>
          </div>
          <div class="od-pipeline-bar-track">
            <div class="od-pipeline-bar-fill" style="width: ${Math.max(pct, count > 0 ? 8 : 0)}%;"></div>
          </div>
        </div>
      `;
    }).join('');
  } catch (_) {
    panel.innerHTML = '<div style="color:var(--text-muted); font-size:12px; padding:8px 0;">Stage data unavailable</div>';
  }
}

function viewProductionStage(stageKey) {
  window.location.href = `../stage/stage.html?stage=${stageKey}`;
}
