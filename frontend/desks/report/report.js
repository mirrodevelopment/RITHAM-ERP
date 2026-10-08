/**
 * Ritham ERP — Reports & Analytics Controller (report.js)
 */

'use strict';

/* ── Constants ───────────────────────────────────────────────────────── */

/* ── Stage Meta loaded dynamically from StageRegistry ───────────────── */

const PAYMENT_STATUS_META = {
  PENDING: { label: 'Unpaid', cls: 'rpt-badge-amber' },
  PARTIAL: { label: 'Partial', cls: 'rpt-badge-blue' },
  PAID: { label: 'Paid', cls: 'rpt-badge-green' },
  REFUNDED: { label: 'Refunded', cls: 'rpt-badge-muted' },
};

const PAYMENT_MODE_META = {
  CASH: { label: 'Cash', icon: 'CASH', color: '#22C55E' },
  UPI: { label: 'UPI / QR', icon: 'UPI', color: '#3B82F6' },
  CARD: { label: 'Debit / Credit', icon: 'CARD', color: '#8B5CF6' },
  BANK_TRANSFER: { label: 'Bank Transfer', icon: 'BANK', color: '#14B8A6' },
  GOOGLE_PAY: { label: 'Google Pay', icon: 'GPay', color: '#3B82F6' },
};

/* ── State ────────────────────────────────────────────────────────────── */

let _allOrders = [];
let _filteredOrders = [];
let _filters = {
  preset: 'WEEK',
  startDate: '',
  endDate: '',
  stage: 'ALL',
  paymentStatus: 'ALL',
  paymentMode: 'ALL',
  searchQuery: ''
};

function isOperationsManager() {
  const role = Auth.getRole();
  return role === ROLES.OPERATIONS_MANAGER || role === 'ROLE_OPERATIONS_MANAGER' || role === 'OPERATIONS_MANAGER';
}

/* ── Init ─────────────────────────────────────────────────────────────── */

document.addEventListener('DOMContentLoaded', async () => {
  if (!Router.protect([ROLES.ADMIN, ROLES.OPERATIONS_MANAGER])) return;
  Sidebar.init({ activePage: 'report' });
  Header.init({ title: 'Reports & Analytics' });

  if (isOperationsManager()) {
    // Hide financial filters
    document.getElementById('paymentStatusFilter')?.closest('.rpt-filter-item')?.style.setProperty('display', 'none');
    document.getElementById('paymentModeFilter')?.closest('.rpt-filter-item')?.style.setProperty('display', 'none');

    // Hide financial stat cards (Total Revenue and Pending Balance)
    document.querySelector('.rpt-stat-green')?.style.setProperty('display', 'none');
    document.querySelector('.rpt-stat-amber')?.style.setProperty('display', 'none');

    // Hide dedicated income graph card
    document.getElementById('incomeChart')?.closest('.rpt-card')?.style.setProperty('display', 'none');

    // Hide revenue summary card
    document.getElementById('revenueSummary')?.closest('.rpt-card')?.style.setProperty('display', 'none');

    // Hide payment method breakdown card
    document.getElementById('paymentBreakdown')?.closest('.rpt-card')?.style.setProperty('display', 'none');

    // Hide table headers
    ['colRptTotal', 'colRptCollected', 'colRptBalance', 'colRptPayment'].forEach(id => {
      document.getElementById(id)?.style.setProperty('display', 'none');
    });
  }

  bindFilterEvents();
  await StageRegistry.init();
  const stageFilterEl = document.getElementById('stageFilter');
  if (stageFilterEl) {
    const activeStages = StageRegistry.getAll();
    if (activeStages && activeStages.length > 0) {
      stageFilterEl.innerHTML = `
        <option value="ALL">All Production Stages</option>
        ${activeStages.map(s => `<option value="${s.stageKey}">[${s.icon || s.title.charAt(0)}] ${s.title}</option>`).join('')}
        <option value="COMPLETED">[C] Completed</option>
        <option value="DELIVERED">[D] Delivered</option>
        <option value="CANCELLED">[X] Cancelled</option>
      `;
    }
  }
  await loadAll();

  window.addEventListener('themeChanged', () => {
    applyFilters();
  });
});


function bindFilterEvents() {
  document.getElementById('refreshBtn').addEventListener('click', loadAll);

  document.getElementById('exportReportExcelBtn')?.addEventListener('click', async () => {
    const listToExport = Array.isArray(_filteredOrders) && _filteredOrders.length > 0 ? _filteredOrders : _allOrders;
    if (!listToExport || !listToExport.length) {
      Toast.warning('No orders found in report to export.');
      return;
    }

    const isOps = isOperationsManager();
    const columns = [
      { key: 'sno', header: 'S.No' },
      { key: 'orderNumber', header: 'Order Number', transform: (v, o) => v || `#${o.id}` },
      { key: 'customerName', header: 'Customer Name', transform: v => v || '—' },
      { key: 'deliveryDate', header: 'Delivery Date', transform: v => ExcelExport.formatDate(v) },
      ...(!isOps ? [
        { key: 'totalAmount', header: 'Total (Rs)', transform: v => Number(v || 0) },
        { key: 'paidAmount', header: 'Collected (Rs)', transform: v => Number(v || 0) },
        { key: 'balanceAmount', header: 'Balance (Rs)', transform: (_, o) => Math.max(0, Number(o.totalAmount || 0) - Number(o.paidAmount || 0)) },
      ] : []),
      { key: 'status', header: 'Stage / Status', transform: v => StageRegistry.getStageTitle(v) || v || '—' },
      ...(!isOps ? [
        { key: 'paymentStatus', header: 'Payment Status', transform: v => PAYMENT_STATUS_META[v]?.label || v || '—' },
        { key: 'paymentMode', header: 'Payment Mode', transform: v => PAYMENT_MODE_META[v]?.label || v || '—' },
      ] : []),
    ];

    await ExcelExport.exportData({
      data: listToExport,
      fileName: `ritham-reports-${(_filters.preset || 'all').toLowerCase()}`,
      sheetName: 'Report Orders',
      columns,
    });
  });

  // Preset Buttons (Default: Last 7 Days)
  document.querySelectorAll('.rpt-pill').forEach(btn => {
    btn.addEventListener('click', (e) => {
      document.querySelectorAll('.rpt-pill').forEach(p => p.classList.remove('active'));
      e.target.classList.add('active');

      const preset = e.target.dataset.preset;
      _filters.preset = preset;

      const incSel = document.getElementById('incomeGraphFilter');
      if (incSel) incSel.value = preset;

      const ordSel = document.getElementById('ordersGraphFilter');
      if (ordSel) ordSel.value = preset;

      const dateRangeBox = document.getElementById('dateRangeBox');
      if (preset === 'CUSTOM') {
        dateRangeBox.style.display = 'flex';
      } else {
        dateRangeBox.style.display = 'none';
      }

      applyFilters();
    });
  });

  // Custom Date Range Pickers (Auto-filter on change and input)
  ['change', 'input'].forEach(evt => {
    document.getElementById('startDate')?.addEventListener(evt, (e) => {
      _filters.startDate = e.target.value;
      applyFilters();
    });
    document.getElementById('endDate')?.addEventListener(evt, (e) => {
      _filters.endDate = e.target.value;
      applyFilters();
    });

    // Dropdown Select Filters (Auto-filter on change and input)
    document.getElementById('stageFilter')?.addEventListener(evt, (e) => {
      _filters.stage = e.target.value;
      applyFilters();
    });
    document.getElementById('paymentStatusFilter')?.addEventListener(evt, (e) => {
      _filters.paymentStatus = e.target.value;
      applyFilters();
    });
    document.getElementById('paymentModeFilter')?.addEventListener(evt, (e) => {
      _filters.paymentMode = e.target.value;
      applyFilters();
    });
  });

  // Search Input (Auto-filter on input and keyup)
  ['input', 'keyup'].forEach(evt => {
    document.getElementById('rptSearch')?.addEventListener(evt, (e) => {
      _filters.searchQuery = e.target.value.trim().toLowerCase();
      applyFilters();
    });
  });

  // Dedicated Graph Timeframe Dropdowns (Sync with global preset and trigger auto-filter)
  document.getElementById('incomeGraphFilter')?.addEventListener('change', (e) => {
    const val = e.target.value;
    _filters.preset = val;
    document.querySelectorAll('.rpt-pill').forEach(p => {
      p.classList.toggle('active', p.dataset.preset === val);
    });
    const ordSel = document.getElementById('ordersGraphFilter');
    if (ordSel) ordSel.value = val;
    applyFilters();
  });
  document.getElementById('ordersGraphFilter')?.addEventListener('change', (e) => {
    const val = e.target.value;
    _filters.preset = val;
    document.querySelectorAll('.rpt-pill').forEach(p => {
      p.classList.toggle('active', p.dataset.preset === val);
    });
    const incSel = document.getElementById('incomeGraphFilter');
    if (incSel) incSel.value = val;
    applyFilters();
  });

  // Fullscreen Graph Buttons
  const incomeFsBtn = document.getElementById('incomeFullscreenBtn');
  if (incomeFsBtn) {
    incomeFsBtn.addEventListener('click', () => openGraphModal('INCOME'));
  }

  const ordersFsBtn = document.getElementById('ordersFullscreenBtn');
  if (ordersFsBtn) {
    ordersFsBtn.addEventListener('click', () => openGraphModal('ORDERS'));
  }

  // Modal Controls & Keydown
  document.getElementById('graphModalClose')?.addEventListener('click', closeGraphModal);
  document.getElementById('modalGraphFilter')?.addEventListener('change', async (e) => {
    const val = e.target.value;
    if (_filters.preset !== val) {
      _filters.preset = val;
      document.querySelectorAll('.rpt-pill').forEach(p => {
        p.classList.toggle('active', p.dataset.preset === val);
      });
      const incSel = document.getElementById('incomeGraphFilter');
      if (incSel) incSel.value = val;
      const ordSel = document.getElementById('ordersGraphFilter');
      if (ordSel) ordSel.value = val;
      await applyFilters();
    }
    renderModalGraph();
  });

  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') closeGraphModal();
  });

  // Reset Filters Button
  document.getElementById('resetFiltersBtn')?.addEventListener('click', resetFilters);
}

function resetFilters() {
  _filters = {
    preset: 'WEEK',
    startDate: '',
    endDate: '',
    stage: 'ALL',
    paymentStatus: 'ALL',
    paymentMode: 'ALL',
    searchQuery: ''
  };

  document.querySelectorAll('.rpt-pill').forEach(p => {
    p.classList.toggle('active', p.dataset.preset === 'WEEK');
  });
  document.getElementById('dateRangeBox').style.display = 'none';
  document.getElementById('startDate').value = '';
  document.getElementById('endDate').value = '';
  document.getElementById('stageFilter').value = 'ALL';
  document.getElementById('paymentStatusFilter').value = 'ALL';
  document.getElementById('paymentModeFilter').value = 'ALL';
  document.getElementById('rptSearch').value = '';

  const incSel = document.getElementById('incomeGraphFilter');
  if (incSel) incSel.value = 'WEEK';

  const ordSel = document.getElementById('ordersGraphFilter');
  if (ordSel) ordSel.value = 'WEEK';

  applyFilters();
}

/* ── Data Loading & Filter Execution ─────────────────────────────────── */

let _lastAnalyticsData = null;

async function loadAll() {
  try {
    document.getElementById('reportSubtitle').textContent = 'Refreshing…';

    let orders = [], stats = {};
    try {
      const statsResp = await Api.get(`${API.ORDERS}/stats`);
      stats = (statsResp?.data || statsResp || {});
    } catch (e) { /* stats endpoint fallback */ }

    await applyFilters(stats);

  } catch (err) {
    document.getElementById('reportSubtitle').textContent = 'Failed to load data. Try refreshing.';
    Toast.error('Report data failed to load. Please refresh the page.');
  }
}

async function applyFilters(stats = {}) {
  const now = new Date();

  try {
    const params = new URLSearchParams();
    if (_filters.preset) params.append('preset', _filters.preset);
    if (_filters.startDate) params.append('startDate', _filters.startDate);
    if (_filters.endDate) params.append('endDate', _filters.endDate);
    if (_filters.stage && _filters.stage !== 'ALL') params.append('stage', _filters.stage);
    if (_filters.paymentStatus && _filters.paymentStatus !== 'ALL') params.append('paymentStatus', _filters.paymentStatus);
    if (_filters.paymentMode && _filters.paymentMode !== 'ALL') params.append('paymentMode', _filters.paymentMode);
    if (_filters.searchQuery) params.append('searchQuery', _filters.searchQuery);

    const analyticsResp = await Api.get(`${API.ORDERS_ANALYTICS}?${params.toString()}`);
    const data = analyticsResp?.data || analyticsResp;

    if (data && data.summary) {
      _lastAnalyticsData = data;
      renderFromBackendAnalytics(data);
      return;
    }
  } catch (err) {
    console.warn('Backend analytics endpoint call failed, falling back to local calculation:', err);
  }

  // Fallback to client-side filtering if API unavailable
  await runClientSideFilterFallback(stats);
}

function renderFromBackendAnalytics(data) {
  const isOps = isOperationsManager();
  const s = data.summary || {};
  const totalOrders = Number(s.totalOrders) || 0;
  const todayOrders = Number(s.todayOrders) || 0;
  const totalRevenue = Number(s.totalRevenue) || 0;
  const totalCollected = Number(s.totalCollected) || 0;
  const totalBalance = Number(s.totalBalance) || 0;
  const delivered = Number(s.deliveredCount) || 0;
  const paidFull = Number(s.paidCount) || 0;
  const partial = Number(s.partialCount) || 0;
  const unpaid = Number(s.unpaidCount) || 0;

  // 1. Stats cards
  setText('statTotalOrders', totalOrders);
  setText('statTodayOrders', `${todayOrders} new today`);

  setText('statTotalRevenue', fmtRupee(totalRevenue));
  setText('statCollected', `Collected: ${fmtRupee(totalCollected)}`);

  setText('statPendingBalance', fmtRupee(totalBalance));
  setText('statPendingCount', `${partial + unpaid} orders with balance`);

  setText('statDelivered', delivered);
  const pct = totalOrders > 0 ? Math.round((delivered / totalOrders) * 100) : 0;
  setText('statDeliveredPct', `${pct}% of total orders`);

  // 2. Charts and breakdowns
  if (!isOps) renderIncomeGraphFromAnalytics(data.dailyIncome || [], _filters.preset);
  renderOrdersGraphFromAnalytics(data.dailyOrders || [], _filters.preset);
  renderStatusBreakdownFromMap(data.statusBreakdown || {});
  if (!isOps) renderRevenueSummaryFromAggregates(totalRevenue, totalCollected, totalBalance, paidFull, partial, unpaid);
  renderStagePipelineFromMap(data.statusBreakdown || {});
  if (!isOps) renderPaymentBreakdownFromMap(data.paymentBreakdown || {});
  renderTopCustomersFromList(data.topCustomers || []);
  renderTable(data.recentOrders || []);

  const now = new Date();
  const timeStr = now.toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit' });
  document.getElementById('reportSubtitle').textContent =
    `Server-side analytics: ${totalOrders} total matching orders · Updated at ${timeStr}`;
}

async function runClientSideFilterFallback(stats = {}) {
  const now = new Date();
  const todayStr = getLocalDateStr(now);

  if (!_allOrders || !_allOrders.length) {
    try {
      const ordersResp = await Api.get(`${API.ORDERS}?size=200&sort=id,desc`);
      _allOrders = (ordersResp?.content || ordersResp?.data?.content || ordersResp?.data || []);
    } catch (e) { /* ignore fallback errors */ }
  }

  const yesterday = new Date(now);
  yesterday.setDate(now.getDate() - 1);
  const yesterdayStr = getLocalDateStr(yesterday);

  const weekAgo = new Date(now);
  weekAgo.setDate(now.getDate() - 6);

  const fortnightAgo = new Date(now);
  fortnightAgo.setDate(now.getDate() - 13);

  const monthAgo = new Date(now);
  monthAgo.setDate(now.getDate() - 29);

  const quarterAgo = new Date(now);
  quarterAgo.setDate(now.getDate() - 89);

  _filteredOrders = _allOrders.filter(o => {
    const rawDate = o.orderDate || o.createdAt;
    const orderDateStr = getLocalDateStr(rawDate);

    if (_filters.preset === 'TODAY') {
      if (orderDateStr !== todayStr) return false;
    } else if (_filters.preset === 'YESTERDAY') {
      if (orderDateStr !== yesterdayStr) return false;
    } else if (_filters.preset === 'WEEK') {
      if (!orderDateStr || orderDateStr < getLocalDateStr(weekAgo)) return false;
    } else if (_filters.preset === '14DAYS') {
      if (!orderDateStr || orderDateStr < getLocalDateStr(fortnightAgo)) return false;
    } else if (_filters.preset === 'MONTH') {
      if (!orderDateStr || orderDateStr < getLocalDateStr(monthAgo)) return false;
    } else if (_filters.preset === '90DAYS') {
      if (!orderDateStr || orderDateStr < getLocalDateStr(quarterAgo)) return false;
    } else if (_filters.preset === 'CUSTOM') {
      if (_filters.startDate && orderDateStr < _filters.startDate) return false;
      if (_filters.endDate && orderDateStr > _filters.endDate) return false;
    }

    if (_filters.stage !== 'ALL' && o.status !== _filters.stage) return false;
    if (_filters.paymentStatus !== 'ALL' && o.paymentStatus !== _filters.paymentStatus) return false;
    if (_filters.paymentMode !== 'ALL') {
      const mode = o.paymentMode || o.paymentMethod || 'CASH';
      if (mode !== _filters.paymentMode) return false;
    }

    if (_filters.searchQuery) {
      const q = _filters.searchQuery;
      const matchNo = (o.orderNumber || '').toLowerCase().includes(q);
      const matchName = (o.customerName || '').toLowerCase().includes(q);
      const matchMob = (o.customerMobile || '').toLowerCase().includes(q);
      if (!matchNo && !matchName && !matchMob) return false;
    }

    return true;
  });

  const isOps = isOperationsManager();
  renderStats(_filteredOrders, stats);
  if (!isOps) renderIncomeGraph(_filteredOrders);
  renderOrdersGraph(_filteredOrders);
  renderStatusBreakdown(_filteredOrders);
  if (!isOps) renderRevenueSummary(_filteredOrders);
  renderStagePipeline(_filteredOrders);
  if (!isOps) renderPaymentBreakdown(_filteredOrders);
  renderTopCustomers(_filteredOrders);
  renderTable(_filteredOrders);

  const isFiltered = _filteredOrders.length !== _allOrders.length;
  const timeStr = now.toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit' });
  if (isFiltered) {
    document.getElementById('reportSubtitle').textContent =
      `Filtered view: Showing ${_filteredOrders.length} of ${_allOrders.length} total orders · Updated at ${timeStr}`;
  } else {
    document.getElementById('reportSubtitle').textContent =
      `All time view: ${_allOrders.length} total orders · Updated at ${timeStr}`;
  }
}

/* ── Separate Dedicated Graphs: Income & Orders ─────────────────────── */

let _incomeChart = null;
let _ordersChart = null;

function generateDateRangeLabels(preset, datesOrOrders) {
  const now = new Date();

  if (preset === 'TODAY') {
    return [getLocalDateStr(now)];
  }

  if (preset === 'YESTERDAY') {
    const y = new Date(now);
    y.setDate(now.getDate() - 1);
    return [getLocalDateStr(y)];
  }

  if (preset === 'ALL') {
    const dateSet = new Set();
    if (Array.isArray(datesOrOrders)) {
      datesOrOrders.forEach(item => {
        if (!item) return;
        if (typeof item === 'string') {
          dateSet.add(item);
        } else if (item.date) {
          dateSet.add(item.date);
        } else {
          const rawDate = item.orderDate || item.createdAt;
          if (rawDate) dateSet.add(getLocalDateStr(rawDate));
        }
      });
    }
    const sorted = Array.from(dateSet).sort();
    return sorted.length ? sorted : [getLocalDateStr(now)];
  }

  if (preset === 'CUSTOM' && _filters.startDate && _filters.endDate) {
    const start = new Date(_filters.startDate);
    const end = new Date(_filters.endDate);
    const dateArr = [];
    let cur = new Date(start);
    while (cur <= end) {
      dateArr.push(getLocalDateStr(cur));
      cur.setDate(cur.getDate() + 1);
    }
    return dateArr.length ? dateArr : [getLocalDateStr(now)];
  }

  let numDays = 7; // Default: Last 7 Days
  if (preset === '14DAYS') numDays = 14;
  else if (preset === 'MONTH') numDays = 30;
  else if (preset === '90DAYS') numDays = 90;

  const dateArr = [];
  for (let i = numDays - 1; i >= 0; i--) {
    const d = new Date(now);
    d.setDate(now.getDate() - i);
    dateArr.push(getLocalDateStr(d));
  }
  return dateArr;
}

function renderIncomeGraph(orders) {
  const canvas = document.getElementById('incomeChart');
  if (!canvas || typeof Chart === 'undefined') return;

  const graphPreset = document.getElementById('incomeGraphFilter')?.value || 'WEEK';

  const dateMap = {};
  orders.forEach(o => {
    if (o.payments && Array.isArray(o.payments) && o.payments.length > 0) {
      o.payments.forEach(p => {
        const rawDate = p.paymentDate;
        const dateStr = rawDate ? getLocalDateStr(rawDate) : 'Unknown';
        if (!dateMap[dateStr]) dateMap[dateStr] = 0;
        dateMap[dateStr] += Number(p.amount) || 0;
      });
    } else {
      const rawDate = o.orderDate || o.createdAt;
      const dateStr = rawDate ? getLocalDateStr(rawDate) : 'Unknown';
      if (!dateMap[dateStr]) dateMap[dateStr] = 0;
      dateMap[dateStr] += Number(o.paidAmount) || 0;
    }
  });

  const sortedDates = generateDateRangeLabels(graphPreset, orders);
  const labels = sortedDates.map(d => {
    if (d === 'Unknown') return d;
    const parts = d.split('-');
    const dt = new Date(parts[0], parts[1] - 1, parts[2]);
    return dt.toLocaleDateString('en-IN', { day: '2-digit', month: 'short' });
  });

  const collectedData = sortedDates.map(d => dateMap[d] || 0);

  // Compute Average Daily Income
  const totalIncomeVal = collectedData.reduce((sum, val) => sum + val, 0);
  const daysCount = collectedData.length || 1;
  const avgIncome = totalIncomeVal / daysCount;
  const maxIncomeVal = Math.max(...collectedData, 1000);

  const avgFormatted = avgIncome >= 100000 ? (avgIncome / 100000).toFixed(1) + 'L'
    : avgIncome >= 1000 ? (avgIncome / 1000).toFixed(1) + 'K'
      : Math.round(avgIncome).toLocaleString('en-IN');

  // Update card subtitle with average income metrics
  const subEl = document.getElementById('incomeGraphSub');
  if (subEl) {
    subEl.textContent = `Daily collection (Avg: ₹${avgFormatted}/day)`;
  }

  const yMaxBound = Math.max(maxIncomeVal * 1.15, Math.ceil(avgIncome * 2));

  if (_incomeChart) {
    _incomeChart.destroy();
    _incomeChart = null;
  }

  const ctx = canvas.getContext('2d');
  const gradientCollected = ctx.createLinearGradient(0, 0, 0, 240);
  gradientCollected.addColorStop(0, 'rgba(74, 222, 128, 0.4)');
  gradientCollected.addColorStop(1, 'rgba(74, 222, 128, 0.0)');

  _incomeChart = new Chart(ctx, {
    type: 'line',
    data: {
      labels: labels.length ? labels : ['No Data'],
      datasets: [
        {
          label: 'Collected Income (₹)',
          data: collectedData.length ? collectedData : [0],
          borderColor: '#4ADE80',
          backgroundColor: gradientCollected,
          borderWidth: 3,
          fill: true,
          tension: 0.35,
          pointRadius: 4,
          pointBackgroundColor: '#4ADE80'
        }
      ]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: { display: false },
        tooltip: {
          backgroundColor: '#1E293B',
          titleColor: '#F8FAFC',
          bodyColor: '#CBD5E1',
          borderColor: '#334155',
          borderWidth: 1,
          padding: 10,
          callbacks: {
            label: (ctx) => ` 💰 Collected Income: ₹${Number(ctx.raw).toLocaleString('en-IN')} (Avg: ₹${avgFormatted}/day)`
          }
        }
      },
      scales: {
        x: {
          grid: { color: 'rgba(255, 255, 255, 0.05)' },
          ticks: { color: '#94A3B8', font: { size: 11 } }
        },
        y: {
          suggestedMin: 0,
          suggestedMax: yMaxBound,
          grid: { color: 'rgba(255, 255, 255, 0.05)' },
          ticks: {
            color: '#94A3B8',
            font: { size: 11 },
            callback: (v) => {
              if (v >= 100000) return '₹' + (v / 100000).toFixed(1) + 'L';
              if (v >= 1000) return '₹' + (v / 1000).toFixed(0) + 'K';
              return '₹' + v;
            }
          }
        }
      }
    }
  });
}

function renderIncomeGraphFromAnalytics(dailyIncome, preset) {
  const canvas = document.getElementById('incomeChart');
  if (!canvas || typeof Chart === 'undefined') return;

  const graphPreset = preset || document.getElementById('incomeGraphFilter')?.value || 'WEEK';

  const dateMap = {};
  (dailyIncome || []).forEach(d => {
    if (d && d.date) {
      dateMap[d.date] = Number(d.amount) || 0;
    }
  });

  const sortedDates = generateDateRangeLabels(graphPreset, dailyIncome);
  const labels = sortedDates.map(d => {
    if (d === 'Unknown') return d;
    const parts = d.split('-');
    const dt = new Date(parts[0], parts[1] - 1, parts[2]);
    return dt.toLocaleDateString('en-IN', { day: '2-digit', month: 'short' });
  });

  const collectedData = sortedDates.map(d => dateMap[d] || 0);

  // Compute Average Daily Income
  const totalIncomeVal = collectedData.reduce((sum, val) => sum + val, 0);
  const daysCount = collectedData.length || 1;
  const avgIncome = totalIncomeVal / daysCount;
  const maxIncomeVal = Math.max(...collectedData, 1000);

  const avgFormatted = avgIncome >= 100000 ? (avgIncome / 100000).toFixed(1) + 'L'
    : avgIncome >= 1000 ? (avgIncome / 1000).toFixed(1) + 'K'
      : Math.round(avgIncome).toLocaleString('en-IN');

  // Update card subtitle with average income metrics
  const subEl = document.getElementById('incomeGraphSub');
  if (subEl) {
    subEl.textContent = `Daily collection (Avg: ₹${avgFormatted}/day)`;
  }

  const yMaxBound = Math.max(maxIncomeVal * 1.15, Math.ceil(avgIncome * 2));

  if (_incomeChart) {
    _incomeChart.destroy();
    _incomeChart = null;
  }

  const ctx = canvas.getContext('2d');
  const gradientCollected = ctx.createLinearGradient(0, 0, 0, 240);
  gradientCollected.addColorStop(0, 'rgba(74, 222, 128, 0.4)');
  gradientCollected.addColorStop(1, 'rgba(74, 222, 128, 0.0)');

  _incomeChart = new Chart(ctx, {
    type: 'line',
    data: {
      labels: labels.length ? labels : ['No Data'],
      datasets: [
        {
          label: 'Collected Income (₹)',
          data: collectedData.length ? collectedData : [0],
          borderColor: '#4ADE80',
          backgroundColor: gradientCollected,
          borderWidth: 3,
          fill: true,
          tension: 0.35,
          pointRadius: 4,
          pointBackgroundColor: '#4ADE80'
        }
      ]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: { display: false },
        tooltip: {
          backgroundColor: '#1E293B',
          titleColor: '#F8FAFC',
          bodyColor: '#CBD5E1',
          borderColor: '#334155',
          borderWidth: 1,
          padding: 10,
          callbacks: {
            label: (ctx) => ` 💰 Collected Income: ₹${Number(ctx.raw).toLocaleString('en-IN')} (Avg: ₹${avgFormatted}/day)`
          }
        }
      },
      scales: {
        x: {
          grid: { color: 'rgba(255, 255, 255, 0.05)' },
          ticks: { color: '#94A3B8', font: { size: 11 } }
        },
        y: {
          suggestedMin: 0,
          suggestedMax: yMaxBound,
          grid: { color: 'rgba(255, 255, 255, 0.05)' },
          ticks: {
            color: '#94A3B8',
            font: { size: 11 },
            callback: (v) => {
              if (v >= 100000) return '₹' + (v / 100000).toFixed(1) + 'L';
              if (v >= 1000) return '₹' + (v / 1000).toFixed(0) + 'K';
              return '₹' + v;
            }
          }
        }
      }
    }
  });
}


function renderOrdersGraph(orders) {
  const canvas = document.getElementById('ordersChart');
  if (!canvas || typeof Chart === 'undefined') return;

  const graphPreset = document.getElementById('ordersGraphFilter')?.value || 'WEEK';

  const dateMap = {};
  orders.forEach(o => {
    const rawDate = o.orderDate || o.createdAt;
    const dateStr = rawDate ? getLocalDateStr(rawDate) : 'Unknown';
    if (!dateMap[dateStr]) dateMap[dateStr] = 0;
    dateMap[dateStr] += 1;
  });

  const sortedDates = generateDateRangeLabels(graphPreset, orders);
  const labels = sortedDates.map(d => {
    if (d === 'Unknown') return d;
    const parts = d.split('-');
    const dt = new Date(parts[0], parts[1] - 1, parts[2]);
    return dt.toLocaleDateString('en-IN', { day: '2-digit', month: 'short' });
  });

  const countData = sortedDates.map(d => dateMap[d] || 0);

  // Compute Average Orders Count per day
  const totalOrdersCount = countData.reduce((sum, val) => sum + val, 0);
  const daysCount = countData.length || 1;
  const avgOrdersCount = totalOrdersCount / daysCount;
  const roundedAvg = Math.round(avgOrdersCount * 10) / 10;
  const maxOrderVal = Math.max(...countData, 1);

  // Update card subtitle with average order metrics
  const subEl = document.getElementById('ordersGraphSub');
  if (subEl) {
    subEl.textContent = `Daily volume (Avg: ${roundedAvg} orders/day)`;
  }

  // Calculate dynamic step size based on average daily order volume
  let yStepSize = 1;
  if (avgOrdersCount >= 20) yStepSize = 5;
  else if (avgOrdersCount >= 10) yStepSize = 2;
  else yStepSize = 1;

  const yMaxBound = Math.max(maxOrderVal + 1, Math.ceil(avgOrdersCount * 2));

  if (_ordersChart) {
    _ordersChart.destroy();
    _ordersChart = null;
  }

  const ctx = canvas.getContext('2d');

  _ordersChart = new Chart(ctx, {
    type: 'bar',
    data: {
      labels: labels.length ? labels : ['No Data'],
      datasets: [
        {
          label: 'Order Count',
          data: countData.length ? countData : [0],
          backgroundColor: 'rgba(129, 140, 248, 0.4)',
          borderColor: '#818CF8',
          borderWidth: 1,
          borderRadius: 4,
          barPercentage: 0.55
        }
      ]
    },
    plugins: [{
      id: 'barTopLabels',
      afterDatasetsDraw(chart) {
        const { ctx } = chart;
        chart.data.datasets.forEach((dataset, i) => {
          const meta = chart.getDatasetMeta(i);
          if (!meta || meta.type !== 'bar') return;
          meta.data.forEach((bar, index) => {
            const val = dataset.data[index];
            if (val !== undefined && val !== null && val > 0) {
              ctx.save();
              ctx.fillStyle = '#A5B4FC';
              ctx.font = 'bold 11px sans-serif';
              ctx.textAlign = 'center';
              ctx.textBaseline = 'bottom';
              ctx.fillText(val, bar.x, bar.y - 4);
              ctx.restore();
            }
          });
        });
      }
    }],
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: { display: false },
        tooltip: {
          backgroundColor: '#1E293B',
          titleColor: '#F8FAFC',
          bodyColor: '#CBD5E1',
          borderColor: '#334155',
          borderWidth: 1,
          padding: 10,
          callbacks: {
            label: (ctx) => ` 📦 Order Count: ${ctx.raw} order(s) (Avg: ${roundedAvg}/day)`
          }
        }
      },
      scales: {
        x: {
          grid: { color: 'rgba(255, 255, 255, 0.05)' },
          ticks: { color: '#94A3B8', font: { size: 11 } }
        },
        y: {
          suggestedMin: 0,
          suggestedMax: yMaxBound,
          grid: { color: 'rgba(255, 255, 255, 0.05)' },
          ticks: {
            color: '#818CF8',
            font: { size: 11 },
            stepSize: yStepSize,
            precision: 0,
            callback: (v) => v + ' orders'
          }
        }
      }
    }
  });
}

function renderOrdersGraphFromAnalytics(dailyOrders, preset) {
  const canvas = document.getElementById('ordersChart');
  if (!canvas || typeof Chart === 'undefined') return;

  const graphPreset = preset || document.getElementById('ordersGraphFilter')?.value || 'WEEK';

  const dateMap = {};
  (dailyOrders || []).forEach(d => {
    if (d && d.date) {
      dateMap[d.date] = Number(d.count) || 0;
    }
  });

  const sortedDates = generateDateRangeLabels(graphPreset, dailyOrders);
  const labels = sortedDates.map(d => {
    if (d === 'Unknown') return d;
    const parts = d.split('-');
    const dt = new Date(parts[0], parts[1] - 1, parts[2]);
    return dt.toLocaleDateString('en-IN', { day: '2-digit', month: 'short' });
  });

  const countData = sortedDates.map(d => dateMap[d] || 0);

  // Compute Average Orders Count per day
  const totalOrdersCount = countData.reduce((sum, val) => sum + val, 0);
  const daysCount = countData.length || 1;
  const avgOrdersCount = totalOrdersCount / daysCount;
  const roundedAvg = Math.round(avgOrdersCount * 10) / 10;
  const maxOrderVal = Math.max(...countData, 1);

  // Update card subtitle with average order metrics
  const subEl = document.getElementById('ordersGraphSub');
  if (subEl) {
    subEl.textContent = `Daily volume (Avg: ${roundedAvg} orders/day)`;
  }

  // Calculate dynamic step size based on average daily order volume
  let yStepSize = 1;
  if (avgOrdersCount >= 20) yStepSize = 5;
  else if (avgOrdersCount >= 10) yStepSize = 2;
  else yStepSize = 1;

  const yMaxBound = Math.max(maxOrderVal + 1, Math.ceil(avgOrdersCount * 2));

  if (_ordersChart) {
    _ordersChart.destroy();
    _ordersChart = null;
  }

  const ctx = canvas.getContext('2d');

  _ordersChart = new Chart(ctx, {
    type: 'bar',
    data: {
      labels: labels.length ? labels : ['No Data'],
      datasets: [
        {
          label: 'Order Count',
          data: countData.length ? countData : [0],
          backgroundColor: 'rgba(129, 140, 248, 0.4)',
          borderColor: '#818CF8',
          borderWidth: 1,
          borderRadius: 4,
          barPercentage: 0.55
        }
      ]
    },
    plugins: [{
      id: 'barTopLabels',
      afterDatasetsDraw(chart) {
        const { ctx } = chart;
        chart.data.datasets.forEach((dataset, i) => {
          const meta = chart.getDatasetMeta(i);
          if (!meta || meta.type !== 'bar') return;
          meta.data.forEach((bar, index) => {
            const val = dataset.data[index];
            if (val !== undefined && val !== null && val > 0) {
              ctx.save();
              ctx.fillStyle = '#A5B4FC';
              ctx.font = 'bold 11px sans-serif';
              ctx.textAlign = 'center';
              ctx.textBaseline = 'bottom';
              ctx.fillText(val, bar.x, bar.y - 4);
              ctx.restore();
            }
          });
        });
      }
    }],
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: { display: false },
        tooltip: {
          backgroundColor: '#1E293B',
          titleColor: '#F8FAFC',
          bodyColor: '#CBD5E1',
          borderColor: '#334155',
          borderWidth: 1,
          padding: 10,
          callbacks: {
            label: (ctx) => ` 📦 Order Count: ${ctx.raw} order(s) (Avg: ${roundedAvg}/day)`
          }
        }
      },
      scales: {
        x: {
          grid: { color: 'rgba(255, 255, 255, 0.05)' },
          ticks: { color: '#94A3B8', font: { size: 11 } }
        },
        y: {
          suggestedMin: 0,
          suggestedMax: yMaxBound,
          grid: { color: 'rgba(255, 255, 255, 0.05)' },
          ticks: {
            color: '#818CF8',
            font: { size: 11 },
            stepSize: yStepSize,
            precision: 0,
            callback: (v) => v + ' orders'
          }
        }
      }
    }
  });
}

/* ── Fullscreen Graph Modal Renderer ─────────────────────────────────── */

let _currentModalGraphType = null;
let _modalChart = null;

function openGraphModal(type) {
  _currentModalGraphType = type;
  const overlay = document.getElementById('graphModalOverlay');
  if (!overlay) return;

  const currentPreset = (type === 'INCOME')
    ? (document.getElementById('incomeGraphFilter')?.value || 'WEEK')
    : (document.getElementById('ordersGraphFilter')?.value || 'WEEK');

  const modalSel = document.getElementById('modalGraphFilter');
  if (modalSel) modalSel.value = currentPreset;

  const titleEl = document.getElementById('graphModalTitle');
  const subEl = document.getElementById('graphModalSub');

  if (type === 'INCOME') {
    if (titleEl) titleEl.textContent = '💰 Revenue Income Trend (Full Screen)';
    if (subEl) subEl.textContent = 'High-resolution daily collection analytics & trend line';
  } else {
    if (titleEl) titleEl.textContent = '📦 Order Volume Trend (Full Screen)';
    if (subEl) subEl.textContent = 'High-resolution order volume distribution & daily counts';
  }

  overlay.style.display = 'flex';
  document.body.style.overflow = 'hidden';

  setTimeout(() => renderModalGraph(), 50);
}

function closeGraphModal() {
  const overlay = document.getElementById('graphModalOverlay');
  if (overlay) overlay.style.display = 'none';
  document.body.style.overflow = '';

  if (_modalChart) {
    _modalChart.destroy();
    _modalChart = null;
  }
}

function renderModalGraph() {
  const canvas = document.getElementById('modalChart');
  if (!canvas || typeof Chart === 'undefined' || !_currentModalGraphType) return;

  const preset = document.getElementById('modalGraphFilter')?.value || 'WEEK';
  const dateMap = {};
  let sourceForLabels = [];

  if (_lastAnalyticsData && _lastAnalyticsData.dailyIncome && _lastAnalyticsData.dailyOrders) {
    if (_currentModalGraphType === 'INCOME') {
      _lastAnalyticsData.dailyIncome.forEach(d => {
        if (d && d.date) dateMap[d.date] = Number(d.amount) || 0;
      });
      sourceForLabels = _lastAnalyticsData.dailyIncome;
    } else {
      _lastAnalyticsData.dailyOrders.forEach(d => {
        if (d && d.date) dateMap[d.date] = Number(d.count) || 0;
      });
      sourceForLabels = _lastAnalyticsData.dailyOrders;
    }
  } else {
    const orders = _filteredOrders;
    sourceForLabels = orders;
    orders.forEach(o => {
      if (_currentModalGraphType === 'INCOME') {
        if (o.payments && Array.isArray(o.payments) && o.payments.length > 0) {
          o.payments.forEach(p => {
            const rawDate = p.paymentDate;
            const dateStr = rawDate ? getLocalDateStr(rawDate) : 'Unknown';
            if (!dateMap[dateStr]) dateMap[dateStr] = 0;
            dateMap[dateStr] += Number(p.amount) || 0;
          });
        } else {
          const rawDate = o.orderDate || o.createdAt;
          const dateStr = rawDate ? getLocalDateStr(rawDate) : 'Unknown';
          if (!dateMap[dateStr]) dateMap[dateStr] = 0;
          dateMap[dateStr] += Number(o.paidAmount) || 0;
        }
      } else {
        const rawDate = o.orderDate || o.createdAt;
        const dateStr = rawDate ? getLocalDateStr(rawDate) : 'Unknown';
        if (!dateMap[dateStr]) dateMap[dateStr] = 0;
        dateMap[dateStr] += 1;
      }
    });
  }

  const sortedDates = generateDateRangeLabels(preset, sourceForLabels);
  const labels = sortedDates.map(d => {
    if (d === 'Unknown') return d;
    const parts = d.split('-');
    const dt = new Date(parts[0], parts[1] - 1, parts[2]);
    return dt.toLocaleDateString('en-IN', { day: '2-digit', month: 'short' });
  });

  const chartData = sortedDates.map(d => dateMap[d] || 0);

  if (_modalChart) {
    _modalChart.destroy();
    _modalChart = null;
  }

  const ctx = canvas.getContext('2d');

  if (_currentModalGraphType === 'INCOME') {
    const totalVal = chartData.reduce((a, b) => a + b, 0);
    const avgIncome = totalVal / (chartData.length || 1);
    const maxVal = Math.max(...chartData, 1000);
    const avgFormatted = avgIncome >= 100000 ? (avgIncome / 100000).toFixed(1) + 'L'
      : avgIncome >= 1000 ? (avgIncome / 1000).toFixed(1) + 'K'
        : Math.round(avgIncome).toLocaleString('en-IN');

    const subEl = document.getElementById('graphModalSub');
    if (subEl) subEl.textContent = `High-resolution daily collection (Avg: ₹${avgFormatted}/day)`;

    const yMaxBound = Math.max(maxVal * 1.15, Math.ceil(avgIncome * 2));

    const gradient = ctx.createLinearGradient(0, 0, 0, 450);
    gradient.addColorStop(0, 'rgba(74, 222, 128, 0.45)');
    gradient.addColorStop(1, 'rgba(74, 222, 128, 0.0)');

    _modalChart = new Chart(ctx, {
      type: 'line',
      data: {
        labels: labels,
        datasets: [{
          label: 'Collected Income (₹)',
          data: chartData,
          borderColor: '#4ADE80',
          backgroundColor: gradient,
          borderWidth: 4,
          fill: true,
          tension: 0.35,
          pointRadius: 6,
          pointBackgroundColor: '#4ADE80'
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { display: false },
          tooltip: {
            backgroundColor: '#1E293B',
            titleColor: '#F8FAFC',
            bodyColor: '#CBD5E1',
            padding: 12,
            callbacks: {
              label: (ctx) => ` 💰 Collected Income: ₹${Number(ctx.raw).toLocaleString('en-IN')} (Avg: ₹${avgFormatted}/day)`
            }
          }
        },
        scales: {
          x: { grid: { color: 'rgba(255, 255, 255, 0.05)' }, ticks: { color: '#94A3B8', font: { size: 12 } } },
          y: {
            suggestedMin: 0,
            suggestedMax: yMaxBound,
            grid: { color: 'rgba(255, 255, 255, 0.05)' },
            ticks: {
              color: '#94A3B8',
              font: { size: 12 },
              callback: (v) => {
                if (v >= 100000) return '₹' + (v / 100000).toFixed(1) + 'L';
                if (v >= 1000) return '₹' + (v / 1000).toFixed(0) + 'K';
                return '₹' + v;
              }
            }
          }
        }
      }
    });
  } else {
    const totalCount = chartData.reduce((a, b) => a + b, 0);
    const avgOrdersCount = totalCount / (chartData.length || 1);
    const roundedAvg = Math.round(avgOrdersCount * 10) / 10;
    const maxVal = Math.max(...chartData, 1);

    const subEl = document.getElementById('graphModalSub');
    if (subEl) subEl.textContent = `High-resolution order volume (Avg: ${roundedAvg} orders/day)`;

    let yStepSize = 1;
    if (avgOrdersCount >= 20) yStepSize = 5;
    else if (avgOrdersCount >= 10) yStepSize = 2;
    else yStepSize = 1;

    const yMaxBound = Math.max(maxVal + 1, Math.ceil(avgOrdersCount * 2));

    _modalChart = new Chart(ctx, {
      type: 'bar',
      data: {
        labels: labels,
        datasets: [{
          label: 'Order Count',
          data: chartData,
          backgroundColor: 'rgba(129, 140, 248, 0.45)',
          borderColor: '#818CF8',
          borderWidth: 2,
          borderRadius: 6,
          barPercentage: 0.6
        }]
      },
      plugins: [{
        id: 'modalBarTopLabels',
        afterDatasetsDraw(chart) {
          const { ctx } = chart;
          chart.data.datasets.forEach((dataset, i) => {
            const meta = chart.getDatasetMeta(i);
            if (!meta || meta.type !== 'bar') return;
            meta.data.forEach((bar, index) => {
              const val = dataset.data[index];
              if (val !== undefined && val !== null && val > 0) {
                ctx.save();
                ctx.fillStyle = '#A5B4FC';
                ctx.font = 'bold 12px sans-serif';
                ctx.textAlign = 'center';
                ctx.textBaseline = 'bottom';
                ctx.fillText(val, bar.x, bar.y - 6);
                ctx.restore();
              }
            });
          });
        }
      }],
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { display: false },
          tooltip: {
            backgroundColor: '#1E293B',
            titleColor: '#F8FAFC',
            bodyColor: '#CBD5E1',
            padding: 12,
            callbacks: {
              label: (ctx) => ` 📦 Order Count: ${ctx.raw} order(s) (Avg: ${roundedAvg}/day)`
            }
          }
        },
        scales: {
          x: { grid: { color: 'rgba(255, 255, 255, 0.05)' }, ticks: { color: '#94A3B8', font: { size: 12 } } },
          y: {
            suggestedMin: 0,
            suggestedMax: yMaxBound,
            grid: { color: 'rgba(255, 255, 255, 0.05)' },
            ticks: {
              color: '#818CF8',
              font: { size: 12 },
              stepSize: yStepSize,
              precision: 0,
              callback: (v) => v + ' orders'
            }
          }
        }
      }
    });
  }
}


function toJsDate(rawDate) {
  if (!rawDate) return null;
  if (rawDate instanceof Date) return isNaN(rawDate.getTime()) ? null : rawDate;
  if (Array.isArray(rawDate)) {
    return new Date(rawDate[0], (rawDate[1] || 1) - 1, rawDate[2] || 1, rawDate[3] || 0, rawDate[4] || 0);
  }
  if (typeof rawDate === 'string') {
    const match = rawDate.match(/^(\d{4})-(\d{2})-(\d{2})/);
    if (match) {
      return new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]));
    }
  }
  const dt = new Date(rawDate);
  return isNaN(dt.getTime()) ? null : dt;
}

function getLocalDateStr(raw) {
  if (!raw) return '';
  if (typeof raw === 'string') {
    const match = raw.match(/^(\d{4}-\d{2}-\d{2})/);
    if (match) return match[1];
  }
  if (Array.isArray(raw)) {
    const y = raw[0];
    const m = String(raw[1]).padStart(2, '0');
    const d = String(raw[2]).padStart(2, '0');
    return `${y}-${m}-${d}`;
  }
  const dt = (raw instanceof Date) ? raw : toJsDate(raw);
  if (!dt || isNaN(dt.getTime())) return '';
  const y = dt.getFullYear();
  const m = String(dt.getMonth() + 1).padStart(2, '0');
  const d = String(dt.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}



/* ── Stats Cards ─────────────────────────────────────────────────────── */

function renderStats(orders, stats) {
  const totalOrders = orders.length;
  const todayOrders = stats.todayOrders ?? countToday(orders);

  const totalRevenue = orders.reduce((s, o) => s + (Number(o.totalAmount) || 0), 0);
  const totalCollected = orders.reduce((s, o) => s + (Number(o.paidAmount) || 0), 0);
  const totalBalance = orders.reduce((s, o) => s + (Number(o.balanceAmount || (o.totalAmount - o.paidAmount)) || 0), 0);

  const delivered = orders.filter(o => o.status === 'DELIVERED').length;
  const completed = orders.filter(o => ['DELIVERED', 'COMPLETED'].includes(o.status)).length;
  const paidFull = orders.filter(o => o.paymentStatus === 'PAID').length;
  const unpaidCount = orders.filter(o => o.paymentStatus === 'PENDING' || o.paymentStatus === 'PARTIAL').length;

  setText('statTotalOrders', totalOrders);
  setText('statTodayOrders', `${todayOrders} new today`);

  setText('statTotalRevenue', fmtRupee(totalRevenue));
  setText('statCollected', `Collected: ${fmtRupee(totalCollected)}`);

  setText('statPendingBalance', fmtRupee(totalBalance));
  setText('statPendingCount', `${unpaidCount} orders with balance`);

  setText('statDelivered', delivered);
  const pct = totalOrders > 0 ? Math.round((delivered / totalOrders) * 100) : 0;
  setText('statDeliveredPct', `${pct}% of total orders`);
}

/* ── Order Status Breakdown ──────────────────────────────────────────── */

function renderStatusBreakdown(orders) {
  const el = document.getElementById('statusBreakdown');
  if (!orders.length) { el.innerHTML = '<div class="rpt-empty">No orders yet.</div>'; return; }

  const counts = {};
  orders.forEach(o => { counts[o.status] = (counts[o.status] || 0) + 1; });
  const max = Math.max(...Object.values(counts));

  const rows = Object.entries(counts)
    .sort((a, b) => b[1] - a[1])
    .map(([status, cnt]) => {
      const stageMetaMap = StageRegistry.getStageMeta();
      const meta = stageMetaMap[status] || { label: status, color: '#555' };
      const pct = max > 0 ? Math.round((cnt / max) * 100) : 0;
      return `
        <div class="rpt-status-item">
          <div class="rpt-status-row">
            <span class="rpt-status-name">
              <span class="rpt-status-dot" style="background:${meta.color}"></span>
              ${meta.label}
            </span>
            <span class="rpt-status-count">${cnt}</span>
          </div>
          <div class="rpt-status-bar-track">
            <div class="rpt-status-bar-fill" style="width:${pct}%;background:${meta.color}"></div>
          </div>
        </div>`;
    }).join('');

  el.innerHTML = rows || '<div class="rpt-empty">No data.</div>';
}

function renderStatusBreakdownFromMap(statusBreakdown) {
  const el = document.getElementById('statusBreakdown');
  if (!el) return;

  const counts = statusBreakdown || {};
  const entries = Object.entries(counts);
  if (!entries.length) {
    el.innerHTML = '<div class="rpt-empty">No orders yet.</div>';
    return;
  }

  const max = Math.max(...Object.values(counts));

  const rows = entries
    .sort((a, b) => b[1] - a[1])
    .map(([status, cnt]) => {
      const stageMetaMap = StageRegistry.getStageMeta();
      const meta = stageMetaMap[status] || { label: status, color: '#555' };
      const pct = max > 0 ? Math.round((cnt / max) * 100) : 0;
      return `
        <div class="rpt-status-item">
          <div class="rpt-status-row">
            <span class="rpt-status-name">
              <span class="rpt-status-dot" style="background:${meta.color}"></span>
              ${meta.label}
            </span>
            <span class="rpt-status-count">${cnt}</span>
          </div>
          <div class="rpt-status-bar-track">
            <div class="rpt-status-bar-fill" style="width:${pct}%;background:${meta.color}"></div>
          </div>
        </div>`;
    }).join('');

  el.innerHTML = rows || '<div class="rpt-empty">No data.</div>';
}

/* ── Revenue Summary ─────────────────────────────────────────────────── */

function renderRevenueSummary(orders) {
  const el = document.getElementById('revenueSummary');
  if (!orders.length) { el.innerHTML = '<div class="rpt-empty">No revenue data.</div>'; return; }

  const total = orders.reduce((s, o) => s + (Number(o.totalAmount) || 0), 0);
  const collected = orders.reduce((s, o) => s + (Number(o.paidAmount) || 0), 0);
  const balance = Math.max(0, total - collected);

  const pctCollected = total > 0 ? Math.round((collected / total) * 100) : 0;
  const pctBalance = total > 0 ? Math.round((balance / total) * 100) : 0;
  const pctPaid = orders.filter(o => o.paymentStatus === 'PAID').length;
  const pctPartial = orders.filter(o => o.paymentStatus === 'PARTIAL').length;
  const pctUnpaid = orders.filter(o => o.paymentStatus === 'PENDING').length;

  el.innerHTML = `
    <div class="rpt-rev-item">
      <div class="rpt-rev-label-row">
        <span class="rpt-rev-label">💰 Total Order Value</span>
        <span class="rpt-rev-amount">${fmtRupee(total)}</span>
      </div>
      <div class="rpt-rev-bar"><div class="rpt-rev-bar-fill" style="width:100%;background:#6366F1"></div></div>
    </div>
    <div class="rpt-rev-item">
      <div class="rpt-rev-label-row">
        <span class="rpt-rev-label">✅ Collected</span>
        <span class="rpt-rev-amount" style="color:var(--status-completed)">${fmtRupee(collected)} <small style="font-size:10px;font-weight:400;color:var(--text-muted)">(${pctCollected}%)</small></span>
      </div>
      <div class="rpt-rev-bar"><div class="rpt-rev-bar-fill" style="width:${pctCollected}%;background:#22C55E"></div></div>
    </div>
    <div class="rpt-rev-item">
      <div class="rpt-rev-label-row">
        <span class="rpt-rev-label">⏳ Outstanding Balance</span>
        <span class="rpt-rev-amount" style="color:#FBBF24">${fmtRupee(balance)} <small style="font-size:10px;font-weight:400;color:var(--text-muted)">(${pctBalance}%)</small></span>
      </div>
      <div class="rpt-rev-bar"><div class="rpt-rev-bar-fill" style="width:${pctBalance}%;background:#F59E0B"></div></div>
    </div>
    <div style="border-top:1px solid var(--border-subtle);padding-top:var(--space-4);display:flex;gap:var(--space-4);">
      <div style="flex:1;text-align:center;">
        <div style="font-size:18px;font-weight:700;color:var(--status-completed)">${pctPaid}</div>
        <div style="font-size:10px;color:var(--text-muted);margin-top:2px;">Fully Paid</div>
      </div>
      <div style="flex:1;text-align:center;">
        <div style="font-size:18px;font-weight:700;color:#60A5FA">${pctPartial}</div>
        <div style="font-size:10px;color:var(--text-muted);margin-top:2px;">Partial</div>
      </div>
      <div style="flex:1;text-align:center;">
        <div style="font-size:18px;font-weight:700;color:#FBBF24">${pctUnpaid}</div>
        <div style="font-size:10px;color:var(--text-muted);margin-top:2px;">Unpaid</div>
      </div>
    </div>
  `;
}

function renderRevenueSummaryFromAggregates(total, collected, balance, paidFull, partial, unpaid) {
  const el = document.getElementById('revenueSummary');
  if (!el) return;

  const totVal = Number(total) || 0;
  const colVal = Number(collected) || 0;
  const balVal = Math.max(0, Number(balance) || 0);

  const pctCollected = totVal > 0 ? Math.round((colVal / totVal) * 100) : 0;
  const pctBalance = totVal > 0 ? Math.round((balVal / totVal) * 100) : 0;

  el.innerHTML = `
    <div class="rpt-rev-item">
      <div class="rpt-rev-label-row">
        <span class="rpt-rev-label">💰 Total Order Value</span>
        <span class="rpt-rev-amount">${fmtRupee(totVal)}</span>
      </div>
      <div class="rpt-rev-bar"><div class="rpt-rev-bar-fill" style="width:100%;background:#6366F1"></div></div>
    </div>
    <div class="rpt-rev-item">
      <div class="rpt-rev-label-row">
        <span class="rpt-rev-label">✅ Collected</span>
        <span class="rpt-rev-amount" style="color:var(--status-completed)">${fmtRupee(colVal)} <small style="font-size:10px;font-weight:400;color:var(--text-muted)">(${pctCollected}%)</small></span>
      </div>
      <div class="rpt-rev-bar"><div class="rpt-rev-bar-fill" style="width:${pctCollected}%;background:#22C55E"></div></div>
    </div>
    <div class="rpt-rev-item">
      <div class="rpt-rev-label-row">
        <span class="rpt-rev-label">⏳ Outstanding Balance</span>
        <span class="rpt-rev-amount" style="color:#FBBF24">${fmtRupee(balVal)} <small style="font-size:10px;font-weight:400;color:var(--text-muted)">(${pctBalance}%)</small></span>
      </div>
      <div class="rpt-rev-bar"><div class="rpt-rev-bar-fill" style="width:${pctBalance}%;background:#F59E0B"></div></div>
    </div>
    <div style="border-top:1px solid var(--border-subtle);padding-top:var(--space-4);display:flex;gap:var(--space-4);">
      <div style="flex:1;text-align:center;">
        <div style="font-size:18px;font-weight:700;color:var(--status-completed)">${paidFull}</div>
        <div style="font-size:10px;color:var(--text-muted);margin-top:2px;">Fully Paid</div>
      </div>
      <div style="flex:1;text-align:center;">
        <div style="font-size:18px;font-weight:700;color:#60A5FA">${partial}</div>
        <div style="font-size:10px;color:var(--text-muted);margin-top:2px;">Partial</div>
      </div>
      <div style="flex:1;text-align:center;">
        <div style="font-size:18px;font-weight:700;color:#FBBF24">${unpaid}</div>
        <div style="font-size:10px;color:var(--text-muted);margin-top:2px;">Unpaid</div>
      </div>
    </div>
  `;
}

/* ── Stage Pipeline ──────────────────────────────────────────────────── */

function renderStagePipeline(orders) {
  const el = document.getElementById('stagePipeline');
  if (!el) return;

  const stageMetaMap = StageRegistry.getStageMeta();
  const stagesList = StageRegistry.getAll();

  const counts = {};
  orders.forEach(o => {
    if (o.status) {
      counts[o.status] = (counts[o.status] || 0) + 1;
    }
  });

  const max = Math.max(1, ...Object.values(counts));

  const rows = stagesList.map(s => {
    const meta = stageMetaMap[s.stageKey] || { label: s.title, color: s.color || '#555' };
    const cnt = counts[s.stageKey] || 0;
    const pct = Math.round((cnt / max) * 100);
    return `
      <div class="rpt-stage-item">
        <div class="rpt-stage-label">
          ${meta.label}
        </div>
        <div class="rpt-stage-track">
          <div class="rpt-stage-fill" style="width:${pct}%;background:${meta.color}"></div>
        </div>
        <div class="rpt-stage-count">${cnt}</div>
      </div>`;
  }).join('');

  el.innerHTML = rows || '<div class="rpt-empty">No pipeline data.</div>';
}

function renderStagePipelineFromMap(statusBreakdown) {
  const el = document.getElementById('stagePipeline');
  if (!el) return;

  const stageMetaMap = StageRegistry.getStageMeta();
  const stagesList = StageRegistry.getAll();
  const counts = statusBreakdown || {};

  const max = Math.max(1, ...Object.values(counts));

  const rows = stagesList.map(s => {
    const meta = stageMetaMap[s.stageKey] || { label: s.title, color: s.color || '#555' };
    const cnt = counts[s.stageKey] || 0;
    const pct = Math.round((cnt / max) * 100);
    return `
      <div class="rpt-stage-item">
        <div class="rpt-stage-label">
          ${meta.label}
        </div>
        <div class="rpt-stage-track">
          <div class="rpt-stage-fill" style="width:${pct}%;background:${meta.color}"></div>
        </div>
        <div class="rpt-stage-count">${cnt}</div>
      </div>`;
  }).join('');

  el.innerHTML = rows || '<div class="rpt-empty">No pipeline data.</div>';
}

/* ── Payment Mode Breakdown ──────────────────────────────────────────── */

function renderPaymentBreakdown(orders) {
  const el = document.getElementById('paymentBreakdown');

  const paid = orders.filter(o => o.paymentStatus === 'PAID' || o.paymentStatus === 'PARTIAL');
  const counts = {};
  paid.forEach(o => {
    const mode = o.paymentMode || o.paymentMethod || 'CASH';
    counts[mode] = (counts[mode] || 0) + 1;
  });

  if (!Object.keys(counts).length) {
    el.innerHTML = '<div class="rpt-empty">No payment data yet.</div>';
    return;
  }

  const total = paid.length;
  const max = Math.max(1, ...Object.values(counts));

  const rows = Object.entries(counts)
    .sort((a, b) => b[1] - a[1])
    .map(([mode, cnt]) => {
      const meta = PAYMENT_MODE_META[mode] || { label: mode, icon: '💳', color: '#888' };
      const pct = total > 0 ? Math.round((cnt / total) * 100) : 0;
      return `
        <div class="rpt-pay-item">
          <div class="rpt-pay-icon">${meta.icon}</div>
          <div class="rpt-pay-info">
            <div class="rpt-pay-name">${meta.label}</div>
            <div class="rpt-pay-bar-wrap">
              <div class="rpt-pay-bar">
                <div class="rpt-pay-fill" style="width:${pct}%;background:${meta.color}"></div>
              </div>
              <span class="rpt-pay-pct">${pct}%</span>
            </div>
          </div>
          <div class="rpt-pay-count">${cnt}</div>
        </div>`;
    }).join('');

  el.innerHTML = rows;
}

function renderPaymentBreakdownFromMap(paymentBreakdown) {
  const el = document.getElementById('paymentBreakdown');
  if (!el) return;

  const map = paymentBreakdown || {};
  const entries = Object.entries(map);

  if (!entries.length) {
    el.innerHTML = '<div class="rpt-empty">No payment data yet.</div>';
    return;
  }

  const total = entries.reduce((s, [, details]) => s + (Number(details?.count !== undefined ? details.count : details) || 0), 0);

  const rows = entries
    .sort((a, b) => (Number(b[1]?.count !== undefined ? b[1].count : b[1]) || 0) - (Number(a[1]?.count !== undefined ? a[1].count : a[1]) || 0))
    .map(([mode, details]) => {
      const cnt = Number(details?.count !== undefined ? details.count : details) || 0;
      const meta = PAYMENT_MODE_META[mode] || { label: mode, icon: '💳', color: '#888' };
      const pct = total > 0 ? Math.round((cnt / total) * 100) : 0;
      return `
        <div class="rpt-pay-item">
          <div class="rpt-pay-icon">${meta.icon}</div>
          <div class="rpt-pay-info">
            <div class="rpt-pay-name">${meta.label}</div>
            <div class="rpt-pay-bar-wrap">
              <div class="rpt-pay-bar">
                <div class="rpt-pay-fill" style="width:${pct}%;background:${meta.color}"></div>
              </div>
              <span class="rpt-pay-pct">${pct}%</span>
            </div>
          </div>
          <div class="rpt-pay-count">${cnt}</div>
        </div>`;
    }).join('');

  el.innerHTML = rows;
}

/* ── Top Customers ───────────────────────────────────────────────────── */

function renderTopCustomers(orders) {
  const el = document.getElementById('topCustomers');
  if (!orders.length) { el.innerHTML = '<div class="rpt-empty">No customer data.</div>'; return; }

  const map = {};
  orders.forEach(o => {
    const key = o.customerMobile || o.customerName || 'Unknown';
    if (!map[key]) map[key] = { name: o.customerName || key, count: 0 };
    map[key].count++;
  });

  const top = Object.values(map)
    .sort((a, b) => b.count - a.count)
    .slice(0, 8);

  el.innerHTML = top.map((c, i) => `
    <div class="rpt-top-item">
      <div class="rpt-top-rank">${i + 1}</div>
      <div class="rpt-top-avatar">${initials(c.name)}</div>
      <div class="rpt-top-name">${c.name}</div>
      <div class="rpt-top-badge">${c.count} order${c.count !== 1 ? 's' : ''}</div>
    </div>
  `).join('');
}

function renderTopCustomersFromList(topCustomers) {
  const el = document.getElementById('topCustomers');
  if (!el) return;

  const list = topCustomers || [];
  if (!list.length) {
    el.innerHTML = '<div class="rpt-empty">No customer data.</div>';
    return;
  }

  el.innerHTML = list.slice(0, 8).map((c, i) => `
    <div class="rpt-top-item">
      <div class="rpt-top-rank">${i + 1}</div>
      <div class="rpt-top-avatar">${initials(c.name || c.mobile || 'Customer')}</div>
      <div class="rpt-top-name">${c.name || c.mobile}</div>
      <div class="rpt-top-badge">${c.count} order${c.count !== 1 ? 's' : ''}</div>
    </div>
  `).join('');
}

/* ── Recent Orders Table ─────────────────────────────────────────────── */

function renderTable(orders) {
  const tbody = document.getElementById('rptTableBody');
  const list = orders.slice(0, 20);
  const isOps = isOperationsManager();
  const colSpan = isOps ? 5 : 9;

  if (!list.length) {
    tbody.innerHTML = `<tr><td colspan="${colSpan}" class="rpt-empty">No orders found.</td></tr>`;
    return;
  }

  tbody.innerHTML = list.map((o, i) => {
    const payMeta = PAYMENT_STATUS_META[o.paymentStatus] || { label: o.paymentStatus, cls: 'rpt-badge-muted' };
    const balance = Number(o.balanceAmount) || Math.max(0, Number(o.totalAmount) - Number(o.paidAmount));

    return `<tr>
      <td style="color:var(--text-muted)">${i + 1}</td>
      <td style="color:var(--text-primary);font-weight:600;font-family:monospace;font-size:11px">${o.orderNumber || '—'}</td>
      <td style="color:var(--text-primary)">${o.customerName || '—'}</td>
      <td>${o.deliveryDate ? fmtDate(o.deliveryDate) : '—'}</td>
      ${!isOps ? `
        <td style="font-weight:600;color:var(--text-primary)">${fmtRupee(o.totalAmount)}</td>
        <td style="color:var(--status-completed)">${fmtRupee(o.paidAmount)}</td>
        <td style="color:${balance > 0 ? '#FBBF24' : 'var(--text-muted)'}">${fmtRupee(balance)}</td>
      ` : ''}
      <td>${StageRegistry.getBadgeHtml(o.status)}</td>
      ${!isOps ? `<td><span class="rpt-badge ${payMeta.cls}">${payMeta.label}</span></td>` : ''}
    </tr>`;
  }).join('');
}

/* ── Helpers ─────────────────────────────────────────────────────────── */

function fmtRupee(v) {
  const n = Number(v) || 0;
  if (n >= 100000) return '₹' + (n / 100000).toFixed(1) + 'L';
  if (n >= 1000) return '₹' + (n / 1000).toFixed(1) + 'K';
  return '₹' + n.toLocaleString('en-IN');
}

function fmtDate(d) {
  if (!d) return '—';
  const dt = typeof d === 'string' ? new Date(d) : d;
  return dt.toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: '2-digit' });
}

function setText(id, val) {
  const el = document.getElementById(id);
  if (el) el.textContent = val;
}

function initials(name) {
  if (!name) return '?';
  return name.trim().split(/\s+/).slice(0, 2).map(w => w[0]?.toUpperCase()).join('');
}

function countToday(orders) {
  const today = new Date().toISOString().slice(0, 10);
  return orders.filter(o => (o.createdAt || o.orderDate || '').startsWith(today)).length;
}
