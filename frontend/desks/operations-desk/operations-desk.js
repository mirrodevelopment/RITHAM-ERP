/**
 * Ritham ERP — Operations Monitoring Desk Controller (operations-desk.js)
 * View-Only Monitoring Dashboard for Operations Managers (OM)
 */

'use strict';

document.addEventListener('DOMContentLoaded', async () => {

  if (!Router.protect([ROLES.ADMIN, ROLES.OPERATIONS_MANAGER])) return;

  Sidebar.init({ activePage: 'dashboard3' });
  Header.init({ title: 'Operations Monitoring Desk (D3)', subtitle: 'Executive View-Only Dashboard' });

  await StageRegistry.init();

  let orders = [];
  let stats = null;

  async function loadOperationsData() {
    try {
      const [orderData, statsData] = await Promise.allSettled([
        Api.get(`${API.ORDERS}?size=100&sort=createdAt,desc`),
        Api.get(`${API.ORDERS}/stats`)
      ]);
      orders = orderData.status === 'fulfilled' ? (orderData.value?.content ?? []) : [];
      stats  = statsData.status === 'fulfilled' ? statsData.value : null;
    } catch (_) {
      orders = [];
      stats  = null;
    }
    renderMetrics();
    renderOrdersTable();
  }

  function renderMetrics() {
    const totalCount = stats?.total ?? orders.length;
    const inProdCount = stats?.inProgress ?? orders.filter(o => o.status !== 'COMPLETED' && o.status !== 'DELIVERY' && o.status !== 'DELIVERED' && o.status !== 'CANCELLED').length;
    const readyCount  = orders.filter(o => o.status === 'READY_TO_DELIVERY' || o.status === 'COMPLETED').length;
    const deliveredCount = stats?.completed ?? orders.filter(o => o.status === 'DELIVERY' || o.status === 'DELIVERED' || o.status === 'COMPLETED').length;

    document.getElementById('statTotalOrders').textContent = totalCount;
    document.getElementById('statInProduction').textContent = inProdCount;
    document.getElementById('statReadyDelivery').textContent = readyCount;
    const completedEl = document.getElementById('statCompletedOrders') || document.getElementById('statTotalValue');
    if (completedEl) {
      completedEl.textContent = deliveredCount;
    }
  }

  function renderOrdersTable() {
    const tbody = document.getElementById('opOrdersTableBody');
    if (!tbody) return;

    const query = (document.getElementById('opSearchInput')?.value || '').toLowerCase();

    const filtered = orders.filter(o => {
      if (!query) return true;
      return (o.orderNumber || '').toLowerCase().includes(query) ||
             (o.customerName || '').toLowerCase().includes(query) ||
             (o.customerMobile || '').includes(query);
    });

    if (!filtered.length) {
      tbody.innerHTML = `
        <tr>
          <td colspan="6" style="padding:40px;text-align:center;color:var(--text-muted);">
            No orders match the current filter.
          </td>
        </tr>
      `;
      return;
    }

    tbody.innerHTML = filtered.map(o => {
      const dateStr  = o.deliveryDate ? Utils.formatDate(o.deliveryDate) : '—';
      const isUrgent = o.deliveryDate && ((new Date(o.deliveryDate)).getTime() - Date.now() < 2 * 24 * 60 * 60 * 1000);
      const priority = isUrgent ? 'URGENT' : 'NORMAL';

      return `
        <tr>
          <td style="font-family:monospace;font-size:12px;font-weight:600;">
            ${o.orderNumber || '#'+o.id}
            ${o.garmentType ? `<div style="margin-top:4px;"><span class="badge" style="font-size:10px;padding:2px 6px;border-radius:4px;font-weight:600;background:${o.garmentType==='BLOUSE'?'rgba(236,72,153,0.15)':'rgba(59,130,246,0.15)'};color:${o.garmentType==='BLOUSE'?'#F472B6':'#60A5FA'};border:1px solid ${o.garmentType==='BLOUSE'?'rgba(236,72,153,0.3)':'rgba(59,130,246,0.3)'};">${o.garmentType==='BLOUSE'?'👗 Blouse':'👘 Chudi'}</span></div>` : ''}
          </td>
          <td>
            <div style="font-weight:600;font-size:13px;">${o.customerName || 'Customer'}</div>
            <div style="font-size:11px;color:var(--text-muted);font-family:monospace;">${o.customerMobile || ''}</div>
          </td>
          <td>
            ${StageRegistry.getBadgeHtml(o.status)}
          </td>
          <td style="font-size:12px;color:var(--text-muted);">${dateStr}</td>
          <td style="text-align:center;">
            <span class="badge ${priority === 'HIGH' ? 'badge-warning' : 'badge-neutral'}" style="font-size:10px;">${priority}</span>
          </td>
          <td style="text-align:right;">
            <span class="badge badge-neutral" style="opacity:0.75;font-size:10px;">👁️ Monitoring (View Only)</span>
          </td>
        </tr>
      `;
    }).join('');
  }

  // ── Event Handlers ────────────────────────────────────────────────────────
  document.getElementById('refreshBtn')?.addEventListener('click', async () => {
    Toast.info('Refreshing Operations metrics...');
    await loadOperationsData();
    Toast.success('Metrics refreshed');
  });

  document.getElementById('opSearchInput')?.addEventListener('input', () => {
    renderOrdersTable();
  });

  document.getElementById('exportOperationsExcelBtn')?.addEventListener('click', async () => {
    if (!orders.length) {
      Toast.warning('No operational orders found to export.');
      return;
    }

    const columns = [
      { key: 'sno', header: 'S.No' },
      { key: 'orderNumber', header: 'Order Number', transform: (v, o) => v || `#${o.id}` },
      { key: 'customerName', header: 'Customer Name', transform: v => v || '—' },
      { key: 'customerMobile', header: 'Mobile Number', transform: v => v || '—' },
      { key: 'garmentType', header: 'Garment Type', transform: v => v || '—' },
      { key: 'status', header: 'Current Stage', transform: v => StageRegistry.getStageTitle(v) || v || '—' },
      { key: 'assignedEmployeeName', header: 'Assigned Staff', transform: v => v || 'Unassigned' },
      { key: 'deliveryDate', header: 'Target Delivery', transform: v => ExcelExport.formatDate(v) },
      { key: 'totalAmount', header: 'Total (Rs)', transform: v => Number(v || 0) },
      { key: 'paidAmount', header: 'Paid (Rs)', transform: v => Number(v || 0) },
      { key: 'balanceDue', header: 'Balance Due (Rs)', transform: (_, o) => Math.max(0, Number(o.totalAmount || 0) - Number(o.paidAmount || 0)) },
    ];

    await ExcelExport.exportData({
      data: orders,
      fileName: 'ritham-operations-monitoring',
      sheetName: 'Operations Orders',
      columns,
    });
  });

  // ── Auto-polling & Focus Auto-refresh ──────────────────────────────────────
  setInterval(loadOperationsData, 10000);
  window.addEventListener('focus', loadOperationsData);

  await loadOperationsData();
});
