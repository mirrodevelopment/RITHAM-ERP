/**
 * Ritham ERP — Production Department Workstations Controller (production.js)
 * Dedicated stage workstation pages and order stage progression
 */

'use strict';

document.addEventListener('DOMContentLoaded', async () => {

  if (!Router.protect([ROLES.ADMIN, ROLES.OPERATIONS_MANAGER, ROLES.PRODUCTION_EMPLOYEE])) return;

  Sidebar.init({ activePage: 'production' });
  Header.init({ title: 'Production Workflow', subtitle: 'Department Stage Workstations' });

  const DEPARTMENTS = [
    { key: 'DESIGNING',           icon: '🎨', title: 'Designing',                desc: 'Design consultation, styling & pattern drafting' },
    { key: 'LINING',              icon: '🥻', title: 'Lining',                   desc: 'Lining fabric selection, pre-wash & matching' },
    { key: 'HAND_MACHINE_WORK',   icon: '🪡', title: 'Hand Work / Machine Work', desc: 'Aari, zardosi, embroidery & machine stitching' },
    { key: 'INITIAL_IRONING',     icon: '🧺', title: 'Initial Ironing',          desc: 'Pre-cutting fabric steam pressing & smoothing' },
    { key: 'CUTTING',             icon: '✂️', title: 'Cutting',                  desc: 'Master fabric cutting & darts sectioning' },
    { key: 'STRETCHING',          icon: '📐', title: 'Stretching',               desc: 'Fabric tensioning & body contour fitting' },
    { key: 'STITCHING',           icon: '🧵', title: 'Stitching',                desc: 'Main garment assembly & seams joining' },
    { key: 'HEMMING',             icon: '🪢', title: 'Hemming',                  desc: 'Edge hemming, piping, hooks & eye finish' },
    { key: 'FINAL_IRONING',       icon: '♨️', title: 'Final Ironing',            desc: 'Final steam press & silhouette alignment' },
    { key: 'QUALITY_CHECK',        icon: '🔍', title: 'Quality Check (QC)',       desc: 'Measurement verification & quality audit' },
    { key: 'READY_TO_DELIVERY',   icon: '📦', title: 'Ready to Delivery',        desc: 'Garment tagging, hanger packing & pickup ready' },
    { key: 'DELIVERY',            icon: '🚚', title: 'Delivery',                 desc: 'Order dispatched & handed over to customer' },
  ];

  let currentStage = 'DESIGNING';
  let allOrders    = [];
  let allEmployees = [];

  // ── Load Orders & Employees ─────────────────────────────────────────────
  async function loadOrders() {
    try {
      const [ordersData, empData] = await Promise.all([
        Api.get(`${API.ORDERS}?size=500&sort=createdAt,desc`),
        Api.get(`${API.EMPLOYEES}?size=100`).catch(() => ({ content: [] }))
      ]);
      allOrders = ordersData?.content ?? [];
      allEmployees = empData?.content ?? [];
    } catch (_) {
      allOrders = [];
    }
    renderDepartmentCards();
    renderWorkstationTable();
  }

  function normalizeStage(status) {
    if (!status) return 'DESIGNING';
    const s = status.toUpperCase();
    if (s === 'PENDING' || s === 'PATTERN_MAKING') return 'DESIGNING';
    if (s === 'FABRIC_CUTTING') return 'CUTTING';
    if (s === 'EMBROIDERY_STITCHING') return 'HAND_MACHINE_WORK';
    if (s === 'MAIN_SEWING' || s === 'IN_PROGRESS') return 'STITCHING';
    if (s === 'BUTTON_FITTING') return 'HEMMING';
    if (s === 'IRONING_PRESS') return 'FINAL_IRONING';
    if (s === 'FINAL_PACKAGING' || s === 'COMPLETED') return 'READY_TO_DELIVERY';
    if (s === 'DELIVERED') return 'DELIVERY';
    return s;
  }

  // ── Render Department Cards Grid ────────────────────────────────────────
  function renderDepartmentCards() {
    const gridEl = document.getElementById('stageCardsGrid');
    if (!gridEl) return;

    gridEl.innerHTML = DEPARTMENTS.map(dept => {
      const count = allOrders.filter(o => normalizeStage(o.status) === dept.key).length;
      const isActive = dept.key === currentStage;

      return `
        <div class="stage-dept-card ${isActive ? 'active' : ''}" data-stage="${dept.key}">
          <div class="stage-dept-header">
            <span class="stage-dept-icon">${dept.icon}</span>
            <span class="stage-dept-count">${count} Orders</span>
          </div>
          <div>
            <div class="stage-dept-title">${dept.title}</div>
            <div style="font-size:11px;color:var(--text-muted);margin-top:2px;">${dept.desc}</div>
          </div>
        </div>
      `;
    }).join('');

    gridEl.querySelectorAll('.stage-dept-card').forEach(card => {
      card.addEventListener('click', () => {
        currentStage = card.getAttribute('data-stage');
        renderDepartmentCards();
        renderWorkstationTable();
      });
    });
  }

  // ── Render Current Workstation Table ─────────────────────────────────────
  function renderWorkstationTable() {
    const titleEl    = document.getElementById('currentStageTitle');
    const subtitleEl = document.getElementById('currentStageSubtitle');
    const tbodyEl    = document.getElementById('stageOrdersBody');
    const searchVal  = (document.getElementById('stageOrderSearch')?.value || '').toLowerCase();

    const dept = DEPARTMENTS.find(d => d.key === currentStage) || DEPARTMENTS[0];

    const canAdvance = Utils.canAdvanceProductionStage();
    if (titleEl) {
      titleEl.innerHTML = `${dept.icon} ${dept.title} Workstation ${!canAdvance ? '<span class="badge badge-neutral" style="font-size:11px;margin-left:8px;font-weight:600;opacity:0.85;">View Only Mode</span>' : ''}`;
    }

    const stageOrders = allOrders.filter(o => normalizeStage(o.status) === currentStage).filter(o => {
      if (!searchVal) return true;
      return (o.orderNumber || '').toLowerCase().includes(searchVal) ||
             (o.customerName || '').toLowerCase().includes(searchVal) ||
             (o.customerMobile || '').includes(searchVal) ||
             (o.assignedEmployeeName || '').toLowerCase().includes(searchVal);
    });

    if (subtitleEl) {
      subtitleEl.textContent = `Active Department Workstation — ${stageOrders.length} Order${stageOrders.length !== 1 ? 's' : ''} processing`;
    }

    if (!stageOrders.length) {
      tbodyEl.innerHTML = `
        <tr>
          <td colspan="6" style="padding:40px;text-align:center;color:var(--text-muted);">
            No active orders currently in ${dept.title}.
          </td>
        </tr>
      `;
      return;
    }

    const nextDept = getNextDepartment(currentStage);

    tbodyEl.innerHTML = stageOrders.map(o => {
      const dateStr  = o.deliveryDate ? Utils.formatDate(o.deliveryDate) : '—';

      const empBadgeHtml = o.assignedEmployeeName ? `
        <span style="font-size:12px;font-weight:600;color:var(--text-primary);display:inline-flex;align-items:center;gap:6px;">
          <span style="background:rgba(99,102,241,0.15);color:#818CF8;padding:2px 6px;border-radius:4px;font-size:10px;font-weight:700;">STAFF</span>
          ${o.assignedEmployeeName}
        </span>
      ` : `<span style="font-size:11px;color:var(--text-muted);font-style:italic;">Unassigned</span>`;

      const garmentBadgeHtml = o.garmentType ? `
        <div style="display:flex;align-items:center;gap:6px;">
          <span class="badge" style="background:${o.garmentType === 'BLOUSE' ? 'rgba(236,72,153,0.15)' : 'rgba(59,130,246,0.15)'};color:${o.garmentType === 'BLOUSE' ? '#F472B6' : '#60A5FA'};border:1px solid ${o.garmentType === 'BLOUSE' ? 'rgba(236,72,153,0.3)' : 'rgba(59,130,246,0.3)'};font-size:10px;font-weight:600;padding:3px 8px;border-radius:4px;">
            ${o.garmentType === 'BLOUSE' ? '👗 Blouse' : '👘 Chudi'}
          </span>
          <button class="btn btn-ghost btn-sm view-spec-btn" data-id="${o.id}" style="padding:2px 6px;font-size:10px;color:#818CF8;" title="View Customer Measurements">
            📐 Specs
          </button>
        </div>
      ` : `<span style="font-size:11px;color:var(--text-muted);font-style:italic;">Standard</span>`;

      return `
        <tr>
          <td style="font-family:monospace;font-size:12px;font-weight:600;">${o.orderNumber || '#'+o.id}</td>
          <td>
            <div style="font-weight:600;font-size:13px;">${o.customerName || 'Customer'}</div>
            <div style="font-size:11px;color:var(--text-muted);font-family:monospace;">${o.customerMobile || ''}</div>
          </td>
          <td>${garmentBadgeHtml}</td>
          <td>${empBadgeHtml}</td>
          <td style="font-size:12px;color:var(--text-muted);">${dateStr}</td>
          <td style="text-align:right;">
            ${!canAdvance ? `
              <span class="badge badge-neutral" style="opacity:0.85;font-size:11px;padding:3px 8px;">View Only Mode</span>
            ` : nextDept ? `
              <button class="btn btn-primary btn-sm advance-dept-btn" data-id="${o.id}">
                Advance to ${nextDept.title} ➔
              </button>
            ` : `
              <span class="badge badge-success">Completed &amp; Ready</span>
            `}
          </td>
        </tr>
      `;
    }).join('');

    tbodyEl.querySelectorAll('.view-spec-btn').forEach(btn => {
      btn.addEventListener('click', (e) => {
        const orderId = e.currentTarget.getAttribute('data-id');
        openWorkstationMeasurements(orderId);
      });
    });

    tbodyEl.querySelectorAll('.advance-dept-btn').forEach(btn => {
      btn.addEventListener('click', async (e) => {
        const orderId = e.currentTarget.getAttribute('data-id');
        advanceOrderStage(orderId);
      });
    });
  }

  function getNextDepartment(currentKey) {
    const idx = DEPARTMENTS.findIndex(d => d.key === currentKey);
    if (idx >= 0 && idx < DEPARTMENTS.length - 1) {
      return DEPARTMENTS[idx + 1];
    }
    return null;
  }

  // Modal Controls
  const advanceModalBackdrop = document.getElementById('advanceStageModalBackdrop');
  const advanceModalCloseBtn = document.getElementById('advanceModalCloseBtn');
  const advanceModalCancelBtn = document.getElementById('advanceModalCancelBtn');
  const advanceStageForm = document.getElementById('advanceStageForm');
  const stageEmployeeSelect = document.getElementById('stageEmployeeSelect');

  if (advanceModalCloseBtn) advanceModalCloseBtn.addEventListener('click', closeAdvanceModal);
  if (advanceModalCancelBtn) advanceModalCancelBtn.addEventListener('click', closeAdvanceModal);

  function closeAdvanceModal() {
    if (advanceModalBackdrop) advanceModalBackdrop.classList.add('hidden');
    if (advanceStageForm) advanceStageForm.reset();
  }

  const advanceTargetStageSelect = document.getElementById('advanceTargetStageSelect');

  async function advanceOrderStage(orderId) {
    if (!Utils.canAdvanceProductionStage()) {
      Toast.warning('System Administrator account is View-Only. Advancing production stages is reserved for floor staff.');
      return;
    }
    const nextDept = getNextDepartment(currentStage) || DEPARTMENTS.find(d => d.key === currentStage) || DEPARTMENTS[0];
    const currentDeptObj = DEPARTMENTS.find(d => d.key === currentStage);
    const orderObj = allOrders.find(o => String(o.id) === String(orderId));

    if (allEmployees.length === 0) {
      try {
        const empData = await Api.get(`${API.EMPLOYEES}?size=100`);
        allEmployees = empData?.content ?? [];
      } catch (_) {}
    }

    document.getElementById('advanceOrderId').value = orderId;
    document.getElementById('advanceTargetStage').value = nextDept.key;
    document.getElementById('advanceOrderInfo').textContent = `${orderObj?.orderNumber || ('#' + orderId)} — ${orderObj?.customerName || 'Customer'}`;
    document.getElementById('advanceStageTransition').textContent = `${currentDeptObj?.title || currentStage} ➔ ${nextDept.title}`;

    if (advanceTargetStageSelect) {
      advanceTargetStageSelect.innerHTML = DEPARTMENTS.map(d => `
        <option value="${d.key}" ${d.key === nextDept.key ? 'selected' : ''}>${d.title}</option>
      `).join('');
    }

    function populateStaffForTarget(targetKey) {
      const targetDept = DEPARTMENTS.find(d => d.key === targetKey);
      document.getElementById('advanceTargetStage').value = targetKey;
      document.getElementById('advanceStageTransition').textContent = `${currentDeptObj?.title || currentStage} ➔ ${targetDept?.title || targetKey}`;

      const stageStaff = allEmployees.filter(e => e.stage === targetKey && e.isActive !== false);
      if (stageStaff.length > 0) {
        stageEmployeeSelect.innerHTML = `
          <option value="">-- Select ${targetDept?.title || targetKey} Staff Employee --</option>
          ${stageStaff.map(e => `
            <option value="${e.id}" data-name="${e.fullName}">${e.fullName} (${e.employeeCode})</option>
          `).join('')}
        `;
      } else {
        stageEmployeeSelect.innerHTML = `
          <option value="">-- Select Employee --</option>
          ${allEmployees.filter(e => e.isActive !== false && !['EMP-001','EMP-002','EMP-003','EMP-004'].includes(e.employeeCode)).map(e => `
            <option value="${e.id}" data-name="${e.fullName}">${e.fullName} (${e.employeeCode}) — ${e.stage || 'Staff'}</option>
          `).join('')}
        `;
      }
    }

    populateStaffForTarget(nextDept.key);
    if (advanceTargetStageSelect) {
      advanceTargetStageSelect.onchange = () => populateStaffForTarget(advanceTargetStageSelect.value);
    }

    if (advanceModalBackdrop) advanceModalBackdrop.classList.remove('hidden');
  }

  if (advanceStageForm) {
    advanceStageForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      const orderId = document.getElementById('advanceOrderId').value;
      const targetStage = advanceTargetStageSelect?.value || document.getElementById('advanceTargetStage').value;
      const selectedOpt = stageEmployeeSelect.options[stageEmployeeSelect.selectedIndex];

      if (!selectedOpt || !selectedOpt.value) {
        Toast.error('Please select an employee for this production stage');
        return;
      }

      const empId = Number(selectedOpt.value);
      const empName = selectedOpt.getAttribute('data-name');
      const nextDeptObj = DEPARTMENTS.find(d => d.key === targetStage);

      let newStatus = targetStage;
      if (targetStage === 'DELIVERY' || targetStage === 'COMPLETED') {
        newStatus = 'DELIVERED';
      }

      const confirmBtn = document.getElementById('confirmAdvanceBtn');
      Utils.setLoading(confirmBtn, true, 'Saving...');

      try {
        await Api.put(`${API.ORDERS}/${orderId}/status`, {
          status: newStatus,
          assignedEmployeeId: empId,
          assignedEmployeeName: empName
        });
        Toast.success(`Order advanced to ${nextDeptObj?.title || targetStage} & assigned to ${empName}`);
        closeAdvanceModal();
        await loadOrders();
      } catch (err) {
        Toast.error(err?.message || 'Failed to advance order stage');
      } finally {
        Utils.setLoading(confirmBtn, false);
      }
    });
  }

  // ── Workstation Measurements Modal ──────────────────────────────────────
  const wsMsrBackdrop = document.getElementById('workstationMeasurementModalBackdrop');
  const wsMsrBody     = document.getElementById('wsMsrModalBody');
  const wsCloseBtn    = document.getElementById('closeWsMsrModalBtn');
  const wsFooterBtn   = document.getElementById('closeWsMsrFooterBtn');

  function closeWsMeasurements() {
    wsMsrBackdrop?.classList.add('hidden');
  }

  wsCloseBtn?.addEventListener('click', closeWsMeasurements);
  wsFooterBtn?.addEventListener('click', closeWsMeasurements);
  wsMsrBackdrop?.addEventListener('click', e => {
    if (e.target === wsMsrBackdrop) closeWsMeasurements();
  });

  async function openWorkstationMeasurements(orderId) {
    if (!wsMsrBody) return;
    wsMsrBody.innerHTML = `
      <div style="padding:30px;text-align:center;color:var(--text-muted);">
        <div class="spinner" style="margin:0 auto 10px;width:24px;height:24px;border:2px solid rgba(255,255,255,0.1);border-top-color:#818CF8;border-radius:50%;animation:spin 0.7s linear infinite;"></div>
        Fetching garment measurements…
      </div>
    `;
    wsMsrBackdrop.classList.remove('hidden');

    try {
      const order = await Api.get(`${API.ORDERS}/${orderId}`);
      const isBlouse = order.garmentType === 'BLOUSE';
      const badgeStyle = isBlouse 
        ? 'background:rgba(236,72,153,0.15);color:#F472B6;border:1px solid rgba(236,72,153,0.3);' 
        : 'background:rgba(59,130,246,0.15);color:#60A5FA;border:1px solid rgba(59,130,246,0.3);';
      const badgeLabel = isBlouse ? '👗 Blouse (19 Measurements)' : '👘 Chudi (18 Measurements)';

      document.getElementById('wsMsrModalTitle').textContent = `${order.customerName || 'Customer'} — Measurements`;
      document.getElementById('wsMsrModalSub').textContent   = `Order ${order.orderNumber || '#'+order.id} • Target Delivery: ${Utils.formatDate(order.deliveryDate)}`;

      const measurements = order.measurements || {};
      const entries = Object.entries(measurements);

      if (entries.length === 0) {
        wsMsrBody.innerHTML = `
          <div style="padding:32px;text-align:center;color:var(--text-muted);background:rgba(255,255,255,0.02);border-radius:8px;">
            <div style="font-size:20px;margin-bottom:6px;">📏</div>
            <div>No specific tailoring measurements found for this order.</div>
          </div>
        `;
        return;
      }

      wsMsrBody.innerHTML = `
        <div style="display:flex;flex-direction:column;gap:14px;">
          <div style="display:flex;align-items:center;justify-content:space-between;padding:10px 14px;background:rgba(255,255,255,0.03);border-radius:8px;border:1px solid rgba(255,255,255,0.08);">
            <div style="font-size:12px;color:var(--text-secondary);">
              Mobile: <strong style="color:var(--text-primary);font-family:monospace;">${order.customerMobile || '—'}</strong>
            </div>
            <div style="display:flex;align-items:center;gap:6px;">
              <span class="badge" style="${badgeStyle}font-size:11px;font-weight:700;padding:4px 10px;border-radius:5px;">
                ${badgeLabel}
              </span>
              ${order.lining ? `
                <span class="badge" style="font-size:11px;font-weight:600;padding:4px 8px;border-radius:5px;background:var(--bg-surface-3);color:var(--text-primary);border:1px solid var(--border-default);">
                  ${order.lining === 'WITH_LINING' ? '✨ With Lining' : 'Without Lining'}
                </span>
              ` : ''}
            </div>
          </div>

          <div style="font-size:11px;font-weight:700;color:var(--text-muted);text-transform:uppercase;letter-spacing:0.5px;">
            Workshop Cutting &amp; Stitching Specs (${entries.length} Parameters)
          </div>

          <div style="display:grid;grid-template-columns:repeat(auto-fill, minmax(130px, 1fr));gap:10px;">
            ${entries.map(([k, v]) => `
              <div style="background:var(--bg-surface-2);border:1px solid rgba(255,255,255,0.06);border-radius:8px;padding:8px 12px;display:flex;flex-direction:column;">
                <span style="font-size:10px;font-weight:700;color:var(--text-muted);text-transform:uppercase;">${k}</span>
                <span style="font-size:15px;font-weight:700;color:var(--status-completed);margin-top:2px;">${v}</span>
              </div>
            `).join('')}
          </div>
        </div>
      `;
    } catch (err) {
      wsMsrBody.innerHTML = `
        <div style="padding:24px;text-align:center;color:#EF4444;">
          Failed to load measurements: ${err.message || 'Unknown error'}
        </div>
      `;
    }
  }

  // ── Handlers ────────────────────────────────────────────────────────────
  document.getElementById('refreshBtn')?.addEventListener('click', async () => {
    Toast.info('Refreshing department stages…');
    await loadOrders();
    Toast.success('Stages refreshed');
  });

  document.getElementById('stageOrderSearch')?.addEventListener('input', () => {
    renderWorkstationTable();
  });

  // URL stage query parameter
  const urlParams = new URLSearchParams(window.location.search);
  if (urlParams.has('stage')) {
    currentStage = urlParams.get('stage');
  }

  await loadOrders();

  // ── Auto-polling & Focus Auto-refresh ──────────────────────────────────────
  setInterval(loadOrders, 10000);
  window.addEventListener('focus', loadOrders);
});
