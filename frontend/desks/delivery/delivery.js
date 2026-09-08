/**
 * Ritham ERP — Delivery Management Controller (delivery.js)
 *
 * Two-tab page:
 *  - "Ready to Deliver" — orders with status FINAL_PACKAGING or COMPLETED
 *  - "Completed Orders" — orders with status DELIVERED
 *
 * API calls:
 *  - GET  /api/orders?search={STATUS}&size=500
 *  - PUT  /api/orders/{id}/status  { status: "DELIVERED" }
 */

'use strict';

/* ── State ──────────────────────────────────────────────────────────────── */
let activeTab      = 'ready';   // 'ready' | 'delivered'
let allOrders      = [];
let filteredOrders = [];
let searchQuery    = '';
let _searchTimer   = null;

/* ── DOM refs ───────────────────────────────────────────────────────────── */
const tableBody    = document.getElementById('dlvTableBody');
const tableHead    = document.getElementById('tableHead');
const tableTitle   = document.getElementById('tableTitle');
const tableSubtitle= document.getElementById('tableSubtitle');
const countLabel   = document.getElementById('deliveryCountLabel');
const searchInput  = document.getElementById('dlvSearch');

/* ── Status helpers ─────────────────────────────────────────────────────── */
const STAGE_LABELS = {
  READY_TO_DELIVERY: 'Ready to Delivery',
  FINAL_PACKAGING:   'Ready to Delivery',
  COMPLETED:         'Ready',
  DELIVERY:          'Delivered',
  DELIVERED:         'Delivered',
  FINAL_IRONING:     'Final Ironing',
  QUALITY_CHECK:     'Quality Check',
  DESIGNING:         'Designing',
  LINING:            'Lining',
  HAND_MACHINE_WORK: 'Hand / Machine',
  INITIAL_IRONING:   'Initial Ironing',
  CUTTING:           'Cutting',
  STRETCHING:        'Stretching',
  STITCHING:         'Stitching',
  HEMMING:           'Hemming',
};

const STAGE_CLS = {
  READY_TO_DELIVERY: 'badge-dlv-indigo',
  FINAL_PACKAGING:   'badge-dlv-indigo',
  COMPLETED:         'badge-dlv-amber',
  DELIVERY:          'badge-dlv-green',
  DELIVERED:         'badge-dlv-green',
};

const PAY_LABELS = { PAID: 'Paid', ADVANCE: 'Partial', PARTIAL: 'Partial', PENDING: 'Unpaid', REFUNDED: 'Refunded' };
const PAY_CLS    = { PAID: 'badge-dlv-green', ADVANCE: 'badge-dlv-amber', PARTIAL: 'badge-dlv-amber', PENDING: 'badge-dlv-red', REFUNDED: 'badge-dlv-indigo' };

const MODE_LABELS = { CASH: '💵 Cash', UPI: '📱 UPI', CARD: '💳 Card', BANK_TRANSFER: '🏦 Transfer' };

function stageLabel(s) { return STAGE_LABELS[s] || (s || '—').replace(/_/g, ' '); }
function stageCls(s)   { return STAGE_CLS[s]   || 'badge-dlv-amber'; }
function payLabel(s)   { return PAY_LABELS[s]   || s || '—'; }
function payCls(s)     { return PAY_CLS[s]      || 'badge-dlv-amber'; }
function modeLabel(m)  { return MODE_LABELS[m]  || m || '💵 Cash'; }

/* ── Load Stats ─────────────────────────────────────────────────────────── */
async function loadStats() {
  try {
    const [rReady, rDeliv] = await Promise.allSettled([
      Api.get(`${API.ORDERS}?search=READY_TO_DELIVERY&size=500`),
      Api.get(`${API.ORDERS}?search=DELIVERED&size=500`),
    ]);

    // Ready to deliver count (strictly non-delivered, non-cancelled)
    const readyOrders = rReady.status === 'fulfilled' ? (rReady.value?.content ?? []) : [];
    const seenReady = new Set();
    const readyCount = readyOrders.filter(o => {
      if (!o || seenReady.has(o.id)) return false;
      if (o.status === 'DELIVERED' || o.status === 'DELIVERY' || o.status === 'CANCELLED') return false;
      seenReady.add(o.id);
      return true;
    }).length;

    // Delivered count
    const delivOrders = rDeliv.status === 'fulfilled' ? (rDeliv.value?.content ?? []) : [];
    const seenDeliv = new Set();
    const delivCount = delivOrders.filter(o => {
      if (!o || seenDeliv.has(o.id)) return false;
      if (o.status !== 'DELIVERED' && o.status !== 'DELIVERY') return false;
      seenDeliv.add(o.id);
      return true;
    }).length;

    document.getElementById('statReady').textContent = readyCount;
    document.getElementById('tabReadyBadge').textContent = readyCount;
    document.getElementById('statDelivered').textContent = delivCount;
    document.getElementById('tabDeliveredBadge').textContent = delivCount;
  } catch (_) { /* silent — UI already shows — */ }
}

/* ── Load Orders ────────────────────────────────────────────────────────── */
async function loadOrders() {
  renderLoading();
  try {
    if (activeTab === 'ready') {
      const r = await Api.get(`${API.ORDERS}?status=READY_TO_DELIVERY&size=500&sort=deliveryDate,asc`);
      const seen = new Set();
      allOrders = (r?.content ?? []).filter(o => {
        if (!o || seen.has(o.id)) return false;
        // Strictly exclude orders that have been marked DELIVERED or CANCELLED
        if (o.status === 'DELIVERED' || o.status === 'DELIVERY' || o.status === 'CANCELLED') return false;
        seen.add(o.id);
        return true;
      }).sort((a, b) => new Date(a.deliveryDate || 0) - new Date(b.deliveryDate || 0));
    } else {
      const r = await Api.get(`${API.ORDERS}?status=DELIVERED&size=500&sortBy=updatedAt&sortDir=desc`);
      const seen = new Set();
      allOrders = (r?.content ?? [])
        .filter(o => {
          if (!o || seen.has(o.id)) return false;
          // Strictly include delivered orders
          if (o.status !== 'DELIVERED' && o.status !== 'DELIVERY') return false;
          seen.add(o.id);
          return true;
        })
        // Arrange completed orders by last delivered first
        .sort((a, b) => {
          const timeA = new Date(a.updatedAt || a.deliveryDate || a.createdAt || 0).getTime();
          const timeB = new Date(b.updatedAt || b.deliveryDate || b.createdAt || 0).getTime();
          return timeB - timeA;
        });
    }
    applySearch();
    updateLabels();
  } catch (err) {
    renderError('Could not load orders. Please refresh.');
  }
}

/* ── Search ─────────────────────────────────────────────────────────────── */
function applySearch() {
  const q = searchQuery.toLowerCase();
  filteredOrders = q
    ? allOrders.filter(o =>
        (o.orderNumber    || '').toLowerCase().includes(q) ||
        (o.customerName   || '').toLowerCase().includes(q) ||
        (o.customerMobile || '').includes(q)
      )
    : [...allOrders];
  renderTable(filteredOrders);
}

searchInput.addEventListener('input', () => {
  clearTimeout(_searchTimer);
  _searchTimer = setTimeout(() => {
    searchQuery = searchInput.value.trim();
    applySearch();
    updateLabels();
  }, 300);
});

/* ── Labels ─────────────────────────────────────────────────────────────── */
function updateLabels() {
  const count = filteredOrders.length;
  const archiveActions = document.getElementById('dlvArchiveActions');
  const btnDeleteOld   = document.getElementById('btnDeleteOld');
  const user           = Storage.getUser();
  const isAdmin        = user && (user.role === ROLES.ADMIN || user.role === 'ROLE_ADMIN');

  if (activeTab === 'ready') {
    countLabel.textContent   = `${count} order${count !== 1 ? 's' : ''} ready for delivery`;
    tableTitle.textContent   = 'Ready to Deliver';
    tableSubtitle.textContent= 'Orders packaged and awaiting customer dispatch';
    if (archiveActions) archiveActions.style.display = 'none';
  } else {
    countLabel.textContent   = `${count} order${count !== 1 ? 's' : ''} delivered`;
    tableTitle.textContent   = 'Completed Orders';
    tableSubtitle.textContent= 'Orders successfully handed to customers';
    if (archiveActions) archiveActions.style.display = 'flex';
    if (btnDeleteOld) btnDeleteOld.style.display = isAdmin ? 'inline-flex' : 'none';
  }
}


/* ── Render helpers ─────────────────────────────────────────────────────── */
function renderLoading() {
  tableBody.innerHTML = `
    <tr>
      <td colspan="8" style="padding:48px;text-align:center;">
        <div style="display:inline-flex;align-items:center;gap:8px;color:var(--text-muted);font-size:13px;">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
               style="animation:dlvSpin 0.7s linear infinite;">
            <path d="M12 2v4M12 18v4M4.93 4.93l2.83 2.83M16.24 16.24l2.83 2.83M2 12h4M18 12h4M4.93 19.07l2.83-2.83M16.24 7.76l2.83-2.83"/>
          </svg>
          Loading orders…
        </div>
      </td>
    </tr>
  `;
}

function isOperationsManager() {
  const role = Auth.getRole();
  return role === ROLES.OPERATIONS_MANAGER || role === 'ROLE_OPERATIONS_MANAGER' || role === 'OPERATIONS_MANAGER';
}

function renderError(msg) {
  tableBody.innerHTML = `
    <tr><td colspan="8">
      <div class="dlv-empty">
        <svg class="dlv-empty-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
          <circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/>
        </svg>
        <h3>Failed to load</h3>
        <p>${msg}</p>
      </div>
    </td></tr>
  `;
}

/* ── Set Table Headers ──────────────────────────────────────────────────── */
function setTableHead() {
  const isOps = isOperationsManager();
  if (activeTab === 'ready') {
    tableHead.innerHTML = `
      <th>#</th>
      <th>Order No.</th>
      <th>Customer</th>
      <th>Delivery Date</th>
      ${!isOps ? '<th>Amount</th>' : ''}
      <th>Stage</th>
      ${!isOps ? '<th>Payment</th>' : ''}
      <th style="text-align:right;">Action</th>
    `;
  } else {
    tableHead.innerHTML = `
      <th>#</th>
      <th>Order No.</th>
      <th>Customer</th>
      <th>Delivered On</th>
      ${!isOps ? '<th>Amount</th>' : ''}
      ${!isOps ? '<th>Payment</th>' : ''}
      <th style="text-align:right;">Details</th>
    `;
  }
}

/* ── Render Table ───────────────────────────────────────────────────────── */
function renderTable(orders) {
  setTableHead();
  const isOps = isOperationsManager();
  const colSpan = isOps ? 6 : 8;

  if (!orders.length) {
    const emptyMsg = activeTab === 'ready'
      ? 'No orders are ready for delivery yet. Orders appear here when they reach Ready to Delivery stage.'
      : 'No delivered orders yet. Mark orders as delivered from the "Ready to Deliver" tab.';

    const emptyIcon = activeTab === 'ready'
      ? `<rect x="1" y="3" width="15" height="13"/><polygon points="16 8 20 8 23 11 23 16 16 16 16 8"/>
         <circle cx="5.5" cy="18.5" r="2.5"/><circle cx="18.5" cy="18.5" r="2.5"/>`
      : `<path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/>`;

    tableBody.innerHTML = `
      <tr><td colspan="${colSpan}">
        <div class="dlv-empty">
          <svg class="dlv-empty-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
            ${emptyIcon}
          </svg>
          <h3>${searchQuery ? `No results for "${searchQuery}"` : (activeTab === 'ready' ? 'No orders ready' : 'No delivered orders')}</h3>
          <p>${searchQuery ? 'Try a different search term.' : emptyMsg}</p>
        </div>
      </td></tr>
    `;
    return;
  }

  const today = new Date();
  today.setHours(0, 0, 0, 0);

  tableBody.innerHTML = orders.map((o, i) => {
    const orderNum    = o.orderNumber || `#${o.id}`;
    const custName    = o.customerName || '—';
    const custMobile  = o.customerMobile || '—';
    const totalAmt    = Number(o.totalAmount || 0);
    const paidAmt     = Number(o.paidAmount || 0);
    const balAmt      = Math.max(0, totalAmt - paidAmt);
    const safeOrderNum = orderNum.replace(/'/g, "\\'");

    if (activeTab === 'ready') {
      const delivDate = o.deliveryDate ? new Date(o.deliveryDate) : null;
      let dateStr = '—', dateCls = '';
      if (delivDate) {
        const diff = Math.floor((delivDate - today) / 86400000);
        dateStr = delivDate.toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
        if (diff < 0) {
          dateCls = 'dlv-date-overdue';
          dateStr += ` (${Math.abs(diff)}d late)`;
        } else if (diff === 0) {
          dateCls = 'dlv-date-today';
          dateStr += ' (Today)';
        } else if (diff <= 2) {
          dateCls = 'dlv-date-soon';
        }
      }

      return `
        <tr>
          <td style="font-size:12px;color:var(--text-muted);">${i + 1}</td>
          <td>
            <span class="dlv-order-num">${orderNum}</span>
            ${o.garmentType ? `<div style="margin-top:3px;"><span class="badge" style="font-size:10px;font-weight:600;padding:2px 6px;border-radius:4px;background:${o.garmentType==='BLOUSE'?'rgba(236,72,153,0.15)':'rgba(59,130,246,0.15)'};color:${o.garmentType==='BLOUSE'?'#F472B6':'#60A5FA'};border:1px solid ${o.garmentType==='BLOUSE'?'rgba(236,72,153,0.3)':'rgba(59,130,246,0.3)'};">${o.garmentType==='BLOUSE'?'👗 Blouse':'👘 Chudi'}</span></div>` : ''}
          </td>
          <td>
            <div class="dlv-cust-name">${custName}</div>
            <div class="dlv-cust-mobile">${custMobile}</div>
          </td>
          <td><span class="${dateCls}">${dateStr}</span></td>
          ${!isOps ? `
            <td>
              <div class="dlv-amount">₹${totalAmt.toLocaleString('en-IN')}</div>
              ${balAmt > 0 ? `<div class="dlv-balance">Bal: ₹${balAmt.toLocaleString('en-IN')}</div>` : ''}
            </td>
          ` : ''}
          <td><span class="dlv-badge ${stageCls(o.status)}">${stageLabel(o.status)}</span></td>
          ${!isOps ? `
            <td>
              <span class="dlv-badge ${payCls(o.paymentStatus)}">${payLabel(o.paymentStatus)}</span>
              <div style="font-size:11px;color:var(--text-muted);margin-top:3px;">${modeLabel(o.paymentMode)}</div>
            </td>
          ` : ''}
          <td style="text-align:right;">
            <div style="display:flex;gap:6px;justify-content:flex-end;align-items:center;">
              <button class="btn btn-ghost btn-sm" onclick="viewDeliveryOrder(${o.id})">View</button>
              <button class="dlv-deliver-btn" id="dlv-btn-${o.id}"
                      onclick="markAsDelivered(${o.id}, '${safeOrderNum}')">
                <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M5 12l5 5L20 7"/>
                </svg>
                Mark Delivered
              </button>
            </div>
          </td>
        </tr>
      `;
    } else {
      const delivDate = o.updatedAt ? new Date(o.updatedAt) : (o.deliveryDate ? new Date(o.deliveryDate) : null);
      let delivStr = '—';
      if (delivDate) {
        const isToday = delivDate.toDateString() === today.toDateString();
        const timePart = delivDate.toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit' });
        delivStr = isToday
          ? `Today, ${timePart}`
          : delivDate.toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
      }

      return `
        <tr>
          <td style="font-size:12px;color:var(--text-muted);">${i + 1}</td>
          <td>
            <span class="dlv-order-num">${orderNum}</span>
            ${o.garmentType ? `<div style="margin-top:3px;"><span class="badge" style="font-size:10px;font-weight:600;padding:2px 6px;border-radius:4px;background:${o.garmentType==='BLOUSE'?'rgba(236,72,153,0.15)':'rgba(59,130,246,0.15)'};color:${o.garmentType==='BLOUSE'?'#F472B6':'#60A5FA'};border:1px solid ${o.garmentType==='BLOUSE'?'rgba(236,72,153,0.3)' : 'rgba(59,130,246,0.3)'};">${o.garmentType==='BLOUSE'?'👗 Blouse':'👘 Chudi'}</span></div>` : ''}
          </td>
          <td>
            <div class="dlv-cust-name">${custName}</div>
            <div class="dlv-cust-mobile">${custMobile}</div>
            ${o.receiverName ? `<div style="font-size:11px;color:var(--text-secondary);margin-top:2px;">Received by: <strong style="color:var(--text-primary);">${o.receiverName}</strong></div>` : ''}
          </td>
          <td>
            <div class="dlv-stamp">
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                <path d="M5 12l5 5L20 7"/>
              </svg>
              ${delivStr}
            </div>
          </td>
          ${!isOps ? `<td><div class="dlv-amount">₹${totalAmt.toLocaleString('en-IN')}</div></td>` : ''}
          ${!isOps ? `
            <td>
              <span class="dlv-badge ${payCls(o.paymentStatus)}">${payLabel(o.paymentStatus)}</span>
              <div style="font-size:11px;color:var(--text-muted);margin-top:3px;">${modeLabel(o.paymentMode)}</div>
            </td>
          ` : ''}
          <td style="text-align:right;">
            <button class="btn btn-ghost btn-sm" onclick="viewDeliveryOrder(${o.id})">View</button>
          </td>
        </tr>
      `;
    }
  }).join('');
}

/* ── Tab Switching ──────────────────────────────────────────────────────── */
document.getElementById('dlvTabs').addEventListener('click', e => {
  const btn = e.target.closest('.dlv-tab');
  if (!btn) return;
  const tab = btn.dataset.tab;
  if (tab === activeTab) return;

  activeTab = tab;
  document.querySelectorAll('.dlv-tab').forEach(b => b.classList.remove('active'));
  btn.classList.add('active');

  // Reset search
  searchQuery = '';
  searchInput.value = '';

  loadOrders();
});

/* ── Delivery Payment Collection Modal ──────────────────────────────────── */

// State for current delivery action
let _deliveryState = { id: null, orderNum: '', total: 0, paid: 0, customerName: '' };

const payModalBackdrop = document.getElementById('deliveryPayModalBackdrop');

function closePayModal() {
  payModalBackdrop?.classList.add('hidden');
  const amtInput = document.getElementById('dlvPayAmount');
  if (amtInput) amtInput.value = '';
  const discInput = document.getElementById('dlvDiscount');
  if (discInput) discInput.value = '';
  const errEl = document.getElementById('dlvPayError');
  if (errEl) errEl.style.display = 'none';
  const recInput = document.getElementById('dlvModalReceiverName');
  if (recInput) recInput.value = '';
  const recErr = document.getElementById('dlvReceiverError');
  if (recErr) recErr.style.display = 'none';
  const feedbackEl = document.getElementById('dlvLiveFeedback');
  if (feedbackEl) feedbackEl.style.display = 'none';
}

function updateDeliveryCalculation() {
  const total = _deliveryState.total;
  const alreadyPaid = _deliveryState.paid;

  const discountVal = parseFloat(document.getElementById('dlvDiscount')?.value || 0) || 0;
  const amountRecVal = parseFloat(document.getElementById('dlvPayAmount')?.value || 0) || 0;

  const netTotal = Math.max(0, total - discountVal);
  const totalPaid = alreadyPaid + amountRecVal;
  const remainingBalance = Math.max(0, netTotal - totalPaid);
  const extraPaid = Math.max(0, totalPaid - netTotal);

  // Update Balance Due display
  const balEl = document.getElementById('dlvPayBalance');
  if (balEl) {
    balEl.textContent = `₹${remainingBalance.toLocaleString('en-IN')}`;
    balEl.style.color = remainingBalance > 0 ? '#F87171' : '#4ADE80';
  }

  // Update live feedback banner
  const feedbackEl = document.getElementById('dlvLiveFeedback');
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

  // Payment mode toggle: show if amountRecVal > 0
  const payModeContainer = document.getElementById('dlvPaymentModeContainer');
  if (payModeContainer) {
    payModeContainer.style.display = amountRecVal > 0 ? 'block' : 'none';
  }
}

document.getElementById('dlvDiscount')?.addEventListener('input', updateDeliveryCalculation);
document.getElementById('dlvPayAmount')?.addEventListener('input', updateDeliveryCalculation);

document.getElementById('closePayModalBtn')?.addEventListener('click', closePayModal);
payModalBackdrop?.addEventListener('click', e => {
  if (e.target === payModalBackdrop) closePayModal();
});

document.getElementById('dlvPayCancelBtn')?.addEventListener('click', closePayModal);

document.getElementById('dlvPayConfirmBtn')?.addEventListener('click', async () => {
  const receiverInput = document.getElementById('dlvModalReceiverName');
  const recErrEl      = document.getElementById('dlvReceiverError');
  const amountInput   = document.getElementById('dlvPayAmount');
  const discountInput = document.getElementById('dlvDiscount');
  const errEl         = document.getElementById('dlvPayError');
  const confirmBtn    = document.getElementById('dlvPayConfirmBtn');

  const receiverName = receiverInput?.value.trim() || '';
  if (!receiverName) {
    if (recErrEl) recErrEl.style.display = 'block';
    receiverInput?.focus();
    return;
  }
  if (recErrEl) recErrEl.style.display = 'none';

  const rawAmt = amountInput?.value.trim();
  const amt = parseFloat(rawAmt || 0) || 0;
  const discountAmt = parseFloat(discountInput?.value.trim() || 0) || 0;

  if (amt < 0) {
    errEl.textContent = 'Please enter a valid amount (0 or more).';
    errEl.style.display = 'block';
    amountInput?.focus();
    return;
  }
  errEl.style.display = 'none';

  // Lock UI
  confirmBtn.disabled = true;
  confirmBtn.textContent = 'Processing…';

  try {
    const payload = {
      status: 'DELIVERED',
      receiverName: receiverName
    };
    if (discountAmt > 0) {
      payload.discountAmount = String(discountAmt);
    }
    if (amt > 0) {
      payload.finalPayment = String(amt);
      payload.paymentMode = document.getElementById('dlvPayMode')?.value || 'CASH';
    }

    await Api.put(`${API.ORDERS}/${_deliveryState.id}/status`, payload);

    Toast.success(`Order ${_deliveryState.orderNum} delivered to ${receiverName}! 🎉`);
    closePayModal();

    // Immediately remove from current list so it moves off the table instantly!
    allOrders = allOrders.filter(o => o.id !== _deliveryState.id);
    applySearch();
    updateLabels();

    await refresh();
  } catch (err) {
    Toast.error(err?.message || 'Could not update delivery status');
    confirmBtn.disabled = false;
    confirmBtn.textContent = '✓ Confirm Delivery';
  }
});

/* ── Mark as Delivered — opens payment modal ────────────────────────────── */
window.markAsDelivered = async function(id, orderNum, totalAmt, paidAmt) {
  let total = Number(totalAmt || 0);
  let paid  = Number(paidAmt  || 0);
  let custName = '';

  try {
    const order = await Api.get(`${API.ORDERS}/${id}`);
    total = Number(order.totalAmount || 0);
    paid  = Number(order.paidAmount  || 0);
    custName = order.customerName || '';
  } catch (_) { /* use provided or fallback */ }

  const balance = Math.max(0, total - paid);

  _deliveryState = { id, orderNum, total, paid, customerName: custName };

  // Populate modal
  document.getElementById('dlvPayOrderNum').textContent  = orderNum;
  document.getElementById('dlvPayTotal').textContent     = `₹${total.toLocaleString('en-IN')}`;
  document.getElementById('dlvPayAlreadyPaid').textContent = `₹${paid.toLocaleString('en-IN')}`;
  document.getElementById('dlvPayBalance').textContent   = `₹${balance.toLocaleString('en-IN')}`;
  
  const receiverInput = document.getElementById('dlvModalReceiverName');
  if (receiverInput) {
    receiverInput.value = custName || '';
  }
  const recErr = document.getElementById('dlvReceiverError');
  if (recErr) recErr.style.display = 'none';

  const discountInput = document.getElementById('dlvDiscount');
  if (discountInput) discountInput.value = '';

  const amountInput = document.getElementById('dlvPayAmount');
  if (amountInput) {
    amountInput.value = balance > 0 ? balance : '';
  }

  const payErr = document.getElementById('dlvPayError');
  if (payErr) payErr.style.display = 'none';

  document.getElementById('dlvPayConfirmBtn').disabled   = false;
  document.getElementById('dlvPayConfirmBtn').textContent = '✓ Confirm Delivery';

  // Informative banner if already fully paid
  const nothingDue = document.getElementById('dlvPayNothingDue');
  if (nothingDue) {
    nothingDue.style.display = balance <= 0 ? 'flex' : 'none';
  }

  // Ensure amount row is always visible!
  const amountRow = document.getElementById('dlvPayAmountRow');
  if (amountRow) amountRow.style.display = 'block';

  updateDeliveryCalculation();

  payModalBackdrop?.classList.remove('hidden');
  setTimeout(() => receiverInput?.focus(), 100);
};


/* ── View Order Detail Modal ────────────────────────────────────────────── */
const viewModalBackdrop = document.getElementById('viewModalBackdrop');
const viewModalBody     = document.getElementById('viewModalBody');
const viewModalTitle    = document.getElementById('viewModalOrderNum');
const viewModalSub      = document.getElementById('viewModalSub');

function closeViewModal() {
  viewModalBackdrop?.classList.add('hidden');
}

document.getElementById('closeViewModalBtn')?.addEventListener('click', closeViewModal);
document.getElementById('closeViewModalFooterBtn')?.addEventListener('click', closeViewModal);
viewModalBackdrop?.addEventListener('click', e => {
  if (e.target === viewModalBackdrop) closeViewModal();
});

window.viewDeliveryOrder = async function(id) {
  if (!viewModalBackdrop || !viewModalBody) return;

  viewModalBody.innerHTML = `
    <div style="padding:40px;text-align:center;color:var(--text-muted);">
      <div style="margin:0 auto 12px;width:24px;height:24px;border:2px solid rgba(255,255,255,0.1);border-top-color:#888;border-radius:50%;animation:dlvSpin 0.7s linear infinite;"></div>
      Loading order details…
    </div>
  `;
  viewModalBackdrop.classList.remove('hidden');

  try {
    const order   = await Api.get(`${API.ORDERS}/${id}`);
    const totalAmt = Number(order.totalAmount || 0);
    const paidAmt  = Number(order.paidAmount || 0);
    const balAmt   = Math.max(0, totalAmt - paidAmt);
    const isDelivered = order.status === 'DELIVERED';

    if (viewModalTitle) viewModalTitle.textContent = order.orderNumber || `#${order.id}`;
    if (viewModalSub)   viewModalSub.textContent   = `Registered on ${Utils.formatDate(order.orderDate || order.createdAt)}`;

    const safeOrderNum = (order.orderNumber || '').replace(/'/g, "\\'");

    viewModalBody.innerHTML = `
      <div style="display:flex;flex-direction:column;gap:14px;">

        <!-- Stage + Payment Row -->
        <div style="display:flex;align-items:center;justify-content:space-between;
                    background:rgba(255,255,255,0.03);padding:12px 16px;border-radius:8px;
                    border:1px solid rgba(255,255,255,0.08);">
          <div>
            <div style="font-size:10px;color:var(--text-muted);text-transform:uppercase;letter-spacing:0.5px;margin-bottom:4px;">Stage</div>
            <span class="dlv-badge ${stageCls(order.status)}">${stageLabel(order.status)}</span>
          </div>
          <div style="text-align:right;">
            <div style="font-size:10px;color:var(--text-muted);text-transform:uppercase;letter-spacing:0.5px;margin-bottom:4px;">Payment</div>
            <span class="dlv-badge ${payCls(order.paymentStatus)}">${payLabel(order.paymentStatus)}</span>
          </div>
        </div>

        <!-- Customer -->
        <div style="background:rgba(255,255,255,0.03);padding:14px 16px;border-radius:8px;border:1px solid rgba(255,255,255,0.08);">
          <div style="font-size:10px;font-weight:700;color:var(--text-secondary);text-transform:uppercase;letter-spacing:0.5px;margin-bottom:10px;">Customer</div>
          <div style="display:grid;grid-template-columns:1fr 1fr;gap:12px;">
            <div>
              <div style="font-size:11px;color:var(--text-muted);">Name</div>
              <div style="font-weight:600;font-size:14px;color:var(--text-primary);margin-top:2px;">${order.customerName || 'Customer'}</div>
            </div>
            <div>
              <div style="font-size:11px;color:var(--text-muted);">Mobile</div>
              <div style="font-family:monospace;font-size:13px;color:var(--text-primary);margin-top:2px;">${order.customerMobile || '—'}</div>
            </div>
          </div>
        </div>

        ${ order.garmentType ? `
        <!-- Garment & Measurements -->
        <div style="background:rgba(255,255,255,0.03);padding:14px 16px;border-radius:8px;border:1px solid rgba(255,255,255,0.08);">
          <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:8px;">
            <div style="font-size:10px;font-weight:700;color:var(--text-secondary);text-transform:uppercase;letter-spacing:0.5px;">Garment & Specs</div>
            <span class="badge" style="font-size:11px;font-weight:700;padding:3px 8px;border-radius:4px;background:${order.garmentType==='BLOUSE'?'rgba(236,72,153,0.15)':'rgba(59,130,246,0.15)'};color:${order.garmentType==='BLOUSE'?'#F472B6':'#60A5FA'};border:1px solid ${order.garmentType==='BLOUSE'?'rgba(236,72,153,0.3)':'rgba(59,130,246,0.3)'};">
              ${order.garmentType === 'BLOUSE' ? '👗 Blouse' : '👘 Chudi'}
            </span>
          </div>
          ${ order.measurements && Object.keys(order.measurements).length > 0 ? `
            <div style="display:grid;grid-template-columns:repeat(auto-fill, minmax(80px, 1fr));gap:6px;margin-top:8px;">
              ${Object.entries(order.measurements).map(([k, v]) => `
                <div style="background:rgba(255,255,255,0.04);border:1px solid rgba(255,255,255,0.06);border-radius:5px;padding:4px 6px;text-align:center;">
                  <div style="font-size:9px;font-weight:700;color:var(--text-muted);text-transform:uppercase;">${k}</div>
                  <div style="font-size:12px;font-weight:700;color:var(--text-primary);margin-top:2px;">${v}</div>
                </div>
              `).join('')}
            </div>
          ` : `
            <div style="font-size:12px;color:var(--text-muted);font-style:italic;">No recorded measurements</div>
          `}
        </div>
        ` : '' }

        <!-- Dates -->
        <div style="display:grid;grid-template-columns:1fr 1fr;gap:12px;">
          <div style="background:rgba(255,255,255,0.03);padding:12px 16px;border-radius:8px;border:1px solid rgba(255,255,255,0.08);">
            <div style="font-size:11px;color:var(--text-muted);">Order Date</div>
            <div style="font-weight:600;font-size:13px;color:var(--text-primary);margin-top:2px;">${Utils.formatDate(order.orderDate || order.createdAt)}</div>
          </div>
          <div style="background:rgba(255,255,255,0.03);padding:12px 16px;border-radius:8px;border:1px solid rgba(255,255,255,0.08);">
            <div style="font-size:11px;color:var(--text-muted);">Target Delivery</div>
            <div style="font-weight:600;font-size:13px;color:var(--status-completed);margin-top:2px;">${Utils.formatDate(order.deliveryDate)}</div>
          </div>
        </div>

        <!-- Payment Breakdown (hidden for Operations Manager) -->
        ${!isOperationsManager() ? `
        <div style="background:rgba(16,185,129,0.07);padding:16px;border-radius:8px;border:1px solid rgba(16,185,129,0.2);">
          <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:10px;">
            <div style="font-size:10px;font-weight:700;color:#10B981;text-transform:uppercase;letter-spacing:0.5px;">Payment Breakdown</div>
            <div style="font-size:11px;font-weight:600;color:var(--text-primary);background:var(--bg-surface-3);padding:3px 8px;border-radius:4px;">
              ${modeLabel(order.paymentMode)}
            </div>
          </div>
          <div style="display:grid;grid-template-columns:repeat(3,1fr);gap:8px;text-align:center;">
            <div>
              <div style="font-size:10px;color:var(--text-muted);">Total</div>
              <div style="font-weight:700;font-size:15px;color:var(--text-primary);margin-top:2px;">₹${totalAmt.toLocaleString('en-IN')}</div>
            </div>
            <div>
              <div style="font-size:10px;color:var(--text-muted);">Paid</div>
              <div style="font-weight:700;font-size:15px;color:var(--status-completed);margin-top:2px;">₹${paidAmt.toLocaleString('en-IN')}</div>
            </div>
            <div>
              <div style="font-size:10px;color:var(--text-muted);">Balance</div>
              <div style="font-weight:700;font-size:15px;color:${balAmt > 0 ? 'var(--status-delayed)' : 'var(--status-completed)'};margin-top:2px;">₹${balAmt.toLocaleString('en-IN')}</div>
            </div>
          </div>
        </div>
        ` : ''}

        <!-- Action / Status Banner -->
        ${!isDelivered ? `
          <button
            onclick="markAsDelivered(${order.id}, '${safeOrderNum}'); closeViewModal();"
            style="
              width:100%;padding:13px 20px;
              background:linear-gradient(135deg,#16a34a,#15803d);
              color:#fff;border:none;border-radius:8px;
              font-size:14px;font-weight:600;font-family:inherit;
              cursor:pointer;display:flex;align-items:center;
              justify-content:center;gap:8px;
              box-shadow:0 2px 12px rgba(22,163,74,0.35);
              transition:opacity 0.15s;
            "
            onmouseover="this.style.opacity='0.88'"
            onmouseout="this.style.opacity='1'"
          >
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
              <path d="M5 12l5 5L20 7"/>
            </svg>
            Mark as Delivered
          </button>
        ` : `
          <div style="
            display:flex;align-items:center;justify-content:center;gap:8px;
            padding:12px 16px;
            background:var(--status-completed-bg);border:1px solid var(--status-completed-border);
            border-radius:8px;color:var(--status-completed);font-size:13px;font-weight:600;">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
              <path d="M5 12l5 5L20 7"/>
            </svg>
            Order Successfully Delivered
          </div>
        `}
      </div>
    `;
  } catch (err) {
    viewModalBody.innerHTML = `
      <div style="padding:30px;text-align:center;color:#F87171;">
        Could not load order details. ${err.message || ''}
      </div>
    `;
  }
};

/* ── Export & Archive Operations ───────────────────────────────────────── */

function exportExcel() {
  if (!allOrders.length) {
    Toast.error('No delivered orders available to export.');
    return;
  }

  if (typeof XLSX === 'undefined') {
    Toast.error('Excel library is loading, please try again in a moment.');
    return;
  }

  const data = allOrders.map((o, idx) => ({
    'S.No': idx + 1,
    'Order Number': o.orderNumber || `#${o.id}`,
    'Customer Name': o.customerName || '—',
    'Mobile Number': o.customerMobile || '—',
    'Order Date': Utils.formatDate(o.orderDate || o.createdAt),
    'Delivered Date': Utils.formatDate(o.updatedAt || o.deliveryDate),
    'Total Amount (Rs)': Number(o.totalAmount || 0),
    'Paid Amount (Rs)': Number(o.paidAmount || 0),
    'Balance Due (Rs)': Math.max(0, Number(o.totalAmount || 0) - Number(o.paidAmount || 0)),
    'Payment Status': payLabel(o.paymentStatus),
    'Payment Type': modeLabel(o.paymentMode),
  }));

  const ws = XLSX.utils.json_to_sheet(data);
  const wb = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(wb, ws, 'Delivered Orders');
  const todayStr = new Date().toISOString().slice(0, 10);
  XLSX.writeFile(wb, `ritham-delivered-orders-${todayStr}.xlsx`);
  Toast.success('Excel file exported successfully! 📊');
}

function exportPDF() {
  if (!allOrders.length) {
    Toast.error('No delivered orders available to export.');
    return;
  }

  if (!window.jspdf || !window.jspdf.jsPDF) {
    Toast.error('PDF library is loading, please try again in a moment.');
    return;
  }

  const { jsPDF } = window.jspdf;
  const doc = new jsPDF('p', 'pt', 'a4');

  // Title & Header
  doc.setFontSize(16);
  doc.setTextColor(22, 163, 74);
  doc.text('Ritham ERP — Delivered Orders Report', 40, 42);

  doc.setFontSize(9);
  doc.setTextColor(120);
  const todayStr = new Date().toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
  doc.text(`Report Date: ${todayStr}  |  Total Delivered Orders: ${allOrders.length}`, 40, 58);

  const tableRows = allOrders.map((o, idx) => [
    idx + 1,
    o.orderNumber || `#${o.id}`,
    o.customerName || '—',
    o.customerMobile || '—',
    Utils.formatDate(o.updatedAt || o.deliveryDate),
    `Rs ${Number(o.totalAmount || 0).toLocaleString('en-IN')}`,
    payLabel(o.paymentStatus),
    modeLabel(o.paymentMode)
  ]);

  doc.autoTable({
    startY: 72,
    head: [['#', 'Order No.', 'Customer', 'Mobile', 'Delivered Date', 'Amount', 'Status', 'Mode']],
    body: tableRows,
    theme: 'grid',
    headStyles: { fillColor: [22, 163, 74], textColor: 255, fontStyle: 'bold' },
    styles: { fontSize: 8, cellPadding: 5 },
  });

  const fileDate = new Date().toISOString().slice(0, 10);
  doc.save(`ritham-delivered-orders-${fileDate}.pdf`);
  Toast.success('PDF report downloaded successfully! 📄');
}

document.getElementById('btnExportExcel')?.addEventListener('click', exportExcel);
document.getElementById('btnExportPdf')?.addEventListener('click', exportPDF);

/* ── Archive Delete Modal (Admin Only) ─────────────────────────────────── */

const archiveModalBackdrop = document.getElementById('deleteArchiveModalBackdrop');
const archivePasswordInput = document.getElementById('archiveAdminPassword');
const archiveConfirmBtn    = document.getElementById('archiveConfirmBtn');
const archiveErrorEl       = document.getElementById('archiveError');

function closeArchiveModal() {
  archiveModalBackdrop?.classList.add('hidden');
  if (archivePasswordInput) archivePasswordInput.value = '';
  if (archiveErrorEl) archiveErrorEl.style.display = 'none';
  if (archiveConfirmBtn) {
    archiveConfirmBtn.disabled = true;
    archiveConfirmBtn.textContent = '🗑 Delete Old Records';
  }
}

document.getElementById('closeArchiveModalBtn')?.addEventListener('click', closeArchiveModal);
document.getElementById('archiveCancelBtn')?.addEventListener('click', closeArchiveModal);
archiveModalBackdrop?.addEventListener('click', e => {
  if (e.target === archiveModalBackdrop) closeArchiveModal();
});

archivePasswordInput?.addEventListener('input', () => {
  const pwd = archivePasswordInput.value.trim();
  archiveConfirmBtn.disabled = pwd.length === 0;
  if (archiveErrorEl) archiveErrorEl.style.display = 'none';
});

document.getElementById('btnDeleteOld')?.addEventListener('click', async () => {
  try {
    const res = await Api.get(`${API_BASE_URL}/orders/delivered/archive-stats?keepDays=30`);
    document.getElementById('archiveToDeleteCount').textContent = res?.toDelete ?? 0;
    document.getElementById('archiveToKeepCount').textContent   = res?.toKeep   ?? 0;
    document.getElementById('archiveCutoffDate').textContent   = res?.cutoffDate ?? '—';

    archivePasswordInput.value = '';
    archiveConfirmBtn.disabled = true;
    archiveConfirmBtn.textContent = '🗑 Delete Old Records';
    if (archiveErrorEl) archiveErrorEl.style.display = 'none';

    archiveModalBackdrop?.classList.remove('hidden');
    setTimeout(() => archivePasswordInput?.focus(), 100);
  } catch (err) {
    Toast.error('Could not fetch archive stats: ' + (err.message || 'Server error'));
  }
});

archiveConfirmBtn?.addEventListener('click', async () => {
  const user = Storage.getUser();
  const password = archivePasswordInput.value.trim();

  if (!password) {
    archiveErrorEl.textContent = 'Admin password is required.';
    archiveErrorEl.style.display = 'block';
    archivePasswordInput.focus();
    return;
  }

  archiveConfirmBtn.disabled = true;
  archiveConfirmBtn.textContent = 'Deleting…';

  try {
    const res = await Api.post(`${API_BASE_URL}/orders/delivered/archive`, {
      adminUsername: user?.username || 'admin',
      password: password,
      keepDays: '30'
    });

    const deletedCount = res?.deletedCount ?? 0;
    Toast.success(`Archive cleanup complete! Purged ${deletedCount} record(s) older than 30 days. 🗑️`);
    closeArchiveModal();
    await refresh();
  } catch (err) {
    archiveErrorEl.textContent = err?.message || 'Invalid admin password. Access denied.';
    archiveErrorEl.style.display = 'block';
    archiveConfirmBtn.disabled = false;
    archiveConfirmBtn.textContent = '🗑 Delete Old Records';
    archivePasswordInput.focus();
  }
});

/* ── Refresh all ────────────────────────────────────────────────────────── */

async function refresh() {
  await Promise.all([loadStats(), loadOrders()]);
}

document.getElementById('refreshBtn').addEventListener('click', async () => {
  Toast.info('Refreshing…');
  await refresh();
  Toast.success('Data refreshed');
});

/* ── Init ───────────────────────────────────────────────────────────────── */
document.addEventListener('DOMContentLoaded', async () => {
  if (!Router.protect([ROLES.ADMIN, ROLES.RECEPTION, ROLES.OPERATIONS_MANAGER])) return;

  Sidebar.init({ activePage: 'delivery' });
  Header.init({ title: 'Deliveries', subtitle: 'Delivery Management' });

  await refresh();

  // Auto-refresh every 15s + focus refresh
  setInterval(refresh, 15000);
  window.addEventListener('focus', refresh);
});
