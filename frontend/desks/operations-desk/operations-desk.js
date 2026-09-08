/**
 * Ritham ERP — Operations Monitoring Desk Controller (operations-desk.js)
 * View-Only Monitoring Dashboard for Operations Managers (OM)
 */

'use strict';

document.addEventListener('DOMContentLoaded', async () => {

  if (!Router.protect([ROLES.ADMIN, ROLES.OPERATIONS_MANAGER])) return;

  Sidebar.init({ activePage: 'dashboard3' });
  Header.init({ title: 'Operations Monitoring Desk (D3)', subtitle: 'Executive View-Only Dashboard' });

  let orders = [];

  const STAGE_CONFIG = {
    'DESIGNING':           { title: 'Designing',                icon: '🎨', bg: 'rgba(99, 102, 241, 0.15)', color: '#818CF8' },
    'LINING':              { title: 'Lining',                   icon: '🥻', bg: 'rgba(167, 139, 250, 0.15)', color: '#A78BFA' },
    'HAND_MACHINE_WORK':   { title: 'Hand / Machine Work',      icon: '🪡', bg: 'rgba(236, 72, 153, 0.15)', color: '#EC4899' },
    'INITIAL_IRONING':     { title: 'Initial Ironing',          icon: '🧺', bg: 'rgba(245, 158, 11, 0.15)', color: '#F59E0B' },
    'CUTTING':             { title: 'Cutting',                  icon: '✂️', bg: 'rgba(239, 68, 68, 0.15)',  color: '#EF4444' },
    'STRETCHING':          { title: 'Stretching',               icon: '📐', bg: 'rgba(20, 184, 166, 0.15)', color: '#14B8A6' },
    'STITCHING':           { title: 'Stitching',                icon: '🧵', bg: 'rgba(59, 130, 246, 0.15)', color: '#3B82F6' },
    'HEMMING':             { title: 'Hemming',                  icon: '🪢', bg: 'rgba(99, 102, 241, 0.15)', color: '#6366F1' },
    'FINAL_IRONING':       { title: 'Final Ironing',            icon: '♨️', bg: 'rgba(251, 146, 60, 0.15)', color: '#FB923C' },
    'QUALITY_CHECK':        { title: 'Quality Check (QC)',       icon: '🔍', bg: 'rgba(16, 185, 129, 0.15)', color: '#10B981' },
    'READY_TO_DELIVERY':   { title: 'Ready to Delivery',        icon: '📦', bg: 'rgba(6, 182, 212, 0.15)',  color: '#06B6D4' },
    'DELIVERY':            { title: 'Delivery',                 icon: '🚚', bg: 'rgba(34, 197, 94, 0.15)',  color: '#22C55E' },
    'COMPLETED':            { title: 'Completed',                icon: '✅', bg: 'rgba(34, 197, 94, 0.2)',   color: '#22C55E' },
    'DELIVERED':            { title: 'Delivered',                icon: '🚚', bg: 'rgba(34, 197, 94, 0.15)',  color: '#22C55E' }
  };

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
      let stgKey = o.status || 'DESIGNING';
      if (stgKey === 'PENDING') stgKey = 'DESIGNING';
      if (stgKey === 'IN_PROGRESS') stgKey = 'CUTTING';

      const stgInfo  = STAGE_CONFIG[stgKey] || { title: stgKey, icon: '⚙️', bg: 'rgba(255,255,255,0.1)', color: '#FFF' };
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
            <span class="badge" style="background:${stgInfo.bg};color:${stgInfo.color};border:1px solid ${stgInfo.color}40;font-size:11px;font-weight:600;padding:4px 10px;border-radius:6px;display:inline-flex;align-items:center;gap:5px;">
              <span>${stgInfo.icon}</span>
              <span>${stgInfo.title}</span>
            </span>
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

  // ── Auto-polling & Focus Auto-refresh ──────────────────────────────────────
  setInterval(loadOperationsData, 10000);
  window.addEventListener('focus', loadOperationsData);

  await loadOperationsData();
});
