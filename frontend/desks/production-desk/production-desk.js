/**
 * Ritham ERP — Production Floor Desk (D2) Controller
 * Real-time Production Board & Stage Progression (Database Bound)
 */

'use strict';

document.addEventListener('DOMContentLoaded', async () => {

  // ── Auth Guard ─────────────────────────────────────────────────────────────
  if (!Router.protect([ROLES.ADMIN, ROLES.PRODUCTION_EMPLOYEE, ROLES.OPERATIONS_MANAGER])) return;

  // ── Init Components ───────────────────────────────────────────────────────
  Sidebar.init({ activePage: 'dashboard2' });
  Header.init({ title: 'Production Floor Desk (D2)', subtitle: 'Work Floor Stage Progression' });

  let jobs = [];

  let STAGES = StageRegistry.getAll().map(s => ({ key: s.stageKey, title: s.title }));

  let currentStageFilter = 'ALL';
  let PAGE_SIZE = 25;
  let currentPage = 0;

  // Check URL params for initial stage
  const urlParams = new URLSearchParams(window.location.search);
  if (urlParams.has('stage')) {
    currentStageFilter = urlParams.get('stage');
  }

  let allEmployees = [];

  // ── Fetch Real Data from Database API ──────────────────────────────────────
  async function loadProductionJobs() {
    try {
      await StageRegistry.init();
      const stagesList = StageRegistry.getAll();
      if (stagesList && stagesList.length > 0) {
        STAGES = stagesList.map(s => ({ key: s.stageKey, title: s.title }));
      }

      const [ordersData, empData] = await Promise.all([
        Api.get(`${API.ORDERS}?size=1000&sort=createdAt,desc`),
        Api.get(`${API.EMPLOYEES}?size=100`).catch(() => ({ content: [] }))
      ]);

      const orders = Array.isArray(ordersData) ? ordersData : (ordersData?.content ?? []);
      allEmployees = Array.isArray(empData) ? empData : (empData?.content ?? []);

      jobs = orders.map(o => {
        let stage = (o.status || 'DESIGNING').toUpperCase();
        if (stage === 'PENDING' || stage === 'NEW' || stage === 'CONFIRMED') {
          stage = 'DESIGNING';
        } else if (stage === 'IN_PROGRESS' || stage === 'IN_PRODUCTION') {
          stage = 'CUTTING';
        }

        const dateStr = o.deliveryDate ? Utils.formatDate(o.deliveryDate) : 'Today';
        const totalAmt = Number(o.totalAmount || 0);

        return {
          id: o.orderNumber || ('#' + o.id),
          dbId: o.id,
          customer: o.customerName || o.customerMobile || 'Customer',
          garment: o.garmentType ? `${o.garmentType === 'BLOUSE' ? 'Blouse' : 'Chudi'}` : `Order #${o.orderNumber || o.id}`,
          garmentType: o.garmentType || null,
          stage: stage,
          date: dateStr,
          status: o.status,
          totalAmount: totalAmt,
          assignedEmployeeId: o.assignedEmployeeId || null,
          assignedEmployeeName: o.assignedEmployeeName || null
        };
      }).filter(j => j.status !== 'CANCELLED' && j.status !== 'DELIVERED');

      renderBoard(
        document.getElementById('jobSearch')?.value.toLowerCase() || '',
        currentStageFilter
      );
    } catch (err) {
      Toast.error('Failed to load production jobs');
      jobs = [];
      renderBoard('', currentStageFilter);
    }
  }

  function getStageBadge(stageKey) {
    return StageRegistry.getBadgeHtml(stageKey);
  }

  function renderStageTabs(filterStage) {
    const tabsWrapper = document.getElementById('stageTabsWrapper');
    const selectEl = document.getElementById('stageFilter');
    if (!tabsWrapper) return;

    const allCount = jobs.length;
    let tabsHtml = `
      <button class="stage-tab ${filterStage === 'ALL' ? 'active' : ''}" data-stage="ALL">
        <span>All Stages (Kanban)</span>
        <span class="stage-tab-badge">${allCount}</span>
      </button>
    `;

    STAGES.forEach(stg => {
      const cnt = jobs.filter(j => j.stage === stg.key).length;
      tabsHtml += `
        <button class="stage-tab ${filterStage === stg.key ? 'active' : ''}" data-stage="${stg.key}">
          <span>${stg.title}</span>
          <span class="stage-tab-badge" id="badge-${stg.key}">${cnt}</span>
        </button>
      `;
    });

    tabsWrapper.innerHTML = tabsHtml;

    tabsWrapper.querySelectorAll('.stage-tab').forEach(tab => {
      tab.addEventListener('click', () => {
        const stg = tab.getAttribute('data-stage');
        currentPage = 0;
        Utils.setQueryParam('stage', stg);
        renderBoard(document.getElementById('jobSearch')?.value.toLowerCase() || '', stg);
      });
    });

    if (selectEl) {
      selectEl.innerHTML = `
        <option value="ALL" ${filterStage === 'ALL' ? 'selected' : ''}>All Production Stages (${allCount})</option>
        ${STAGES.map(s => {
          const cnt = jobs.filter(j => j.stage === s.key).length;
          return `<option value="${s.key}" ${s.key === filterStage ? 'selected' : ''}>${s.title} Stage (${cnt})</option>`;
        }).join('')}
      `;
    }
  }

  // ── Render Board & Stage Workstations (Table Format) ──────────────────────
  function renderBoard(filterQuery = '', filterStage = currentStageFilter) {
    currentStageFilter = filterStage;
    const boardEl = document.getElementById('productionBoard');
    if (!boardEl) return;

    let filteredJobs = jobs.filter(j => {
      const matchSearch = j.id.toLowerCase().includes(filterQuery) ||
                          j.garment.toLowerCase().includes(filterQuery) ||
                          j.customer.toLowerCase().includes(filterQuery) ||
                          (j.assignedEmployeeName && j.assignedEmployeeName.toLowerCase().includes(filterQuery));
      const matchStage  = filterStage === 'ALL' || j.stage === filterStage;
      return matchSearch && matchStage;
    });

    // Update Stats dynamically from database orders across the 12 modern stages
    const preStitchingStages = ['DESIGNING', 'LINING', 'HAND_MACHINE_WORK', 'INITIAL_IRONING', 'CUTTING', 'STRETCHING'];
    const stitchingAssemblyStages = ['STITCHING', 'HEMMING', 'FINAL_IRONING'];
    const qcDispatchStages = ['QUALITY_CHECK', 'READY_TO_DELIVERY', 'DELIVERY'];

    document.getElementById('statActiveJobs').textContent = jobs.length;
    document.getElementById('statCutting').textContent = jobs.filter(j => preStitchingStages.includes(j.stage)).length;
    document.getElementById('statSewing').textContent  = jobs.filter(j => stitchingAssemblyStages.includes(j.stage)).length;
    document.getElementById('statQC').textContent      = jobs.filter(j => qcDispatchStages.includes(j.stage)).length;

    // Render / Update dynamic tabs and select options
    renderStageTabs(filterStage);

    const stageTitle = filterStage === 'ALL' 
      ? 'All Production Floor Orders' 
      : ((STAGES.find(s => s.key === filterStage)?.title || filterStage) + ' Stage Table');

    // ── 25 Rows per page pagination calculation ──
    const totalItems = filteredJobs.length;
    const totalPages = Math.ceil(totalItems / PAGE_SIZE) || 1;
    if (currentPage >= totalPages) currentPage = Math.max(0, totalPages - 1);
    if (currentPage < 0) currentPage = 0;

    const startIndex = currentPage * PAGE_SIZE;
    const endIndex   = Math.min(startIndex + PAGE_SIZE, totalItems);
    const pageJobs   = filteredJobs.slice(startIndex, endIndex);

    const tableRowsHtml = filteredJobs.length === 0
      ? `
        <tr>
          <td colspan="6" style="padding:40px;text-align:center;color:var(--color-text-muted);">
            No active production orders found ${filterStage !== 'ALL' ? 'in this stage' : ''}.
          </td>
        </tr>
      `
      : pageJobs.map(j => {
          const nextStageObj = getNextStage(j.stage);

          const empBadgeHtml = j.assignedEmployeeName ? `
            <span style="font-size:12px;font-weight:600;color:var(--text-primary);display:inline-flex;align-items:center;gap:6px;">
              <span style="background:rgba(99,102,241,0.15);color:#818CF8;padding:2px 6px;border-radius:4px;font-size:10px;font-weight:700;">STAFF</span>
              ${j.assignedEmployeeName}
            </span>
          ` : `<span style="font-size:11px;color:var(--text-muted);font-style:italic;">Unassigned</span>`;

          return `
            <tr>
              <td style="font-family:var(--font-mono);font-size:12px;font-weight:600;color:var(--text-primary);">
                ${j.id}
                ${j.garmentType ? `<div style="margin-top:4px;"><span class="badge" style="font-size:10px;padding:2px 6px;border-radius:4px;font-weight:600;background:${j.garmentType==='BLOUSE'?'rgba(236,72,153,0.15)':'rgba(59,130,246,0.15)'};color:${j.garmentType==='BLOUSE'?'#F472B6':'#60A5FA'};border:1px solid ${j.garmentType==='BLOUSE'?'rgba(236,72,153,0.3)':'rgba(59,130,246,0.3)'};">${j.garmentType==='BLOUSE'?'👗 Blouse':'👘 Chudi'}</span></div>` : ''}
              </td>
              <td>
                <div style="font-weight:600;font-size:13px;color:var(--text-primary);">${j.customer}</div>
              </td>
              <td>
                ${getStageBadge(j.stage)}
              </td>
              <td>
                ${empBadgeHtml}
              </td>
              <td style="font-size:12px;color:var(--text-secondary);">${j.date}</td>
              <td style="text-align:right;">
                ${!Utils.canAdvanceProductionStage() ? `
                  <span class="badge badge-neutral" style="opacity:0.85;font-size:11px;padding:3px 8px;">View Only Mode</span>
                ` : nextStageObj ? `
                  <button class="btn btn-primary btn-sm advance-btn" data-id="${j.id}" data-dbid="${j.dbId}">
                    Advance to ${nextStageObj.title} ➔
                  </button>
                ` : `
                  <span class="badge badge-success">Ready for Delivery</span>
                `}
              </td>
            </tr>
          `;
        }).join('');

    // ── Generate numbered page buttons (e.g., 1, 2, 3, 4) ──
    let pagesHtml = '';
    const startPage = Math.max(0, currentPage - 2);
    const endPage   = Math.min(totalPages - 1, currentPage + 2);

    if (startPage > 0) {
      pagesHtml += `<button class="pagination-btn" onclick="window.goToProductionPage(0)" title="Page 1">1</button>`;
      if (startPage > 1) pagesHtml += `<span class="pagination-ellipsis">…</span>`;
    }

    for (let p = startPage; p <= endPage; p++) {
      pagesHtml += `<button class="pagination-btn ${p === currentPage ? 'active' : ''}" onclick="window.goToProductionPage(${p})" title="Page ${p + 1}">${p + 1}</button>`;
    }

    if (endPage < totalPages - 1) {
      if (endPage < totalPages - 2) pagesHtml += `<span class="pagination-ellipsis">…</span>`;
      pagesHtml += `<button class="pagination-btn" onclick="window.goToProductionPage(${totalPages - 1})" title="Page ${totalPages}">${totalPages}</button>`;
    }

    const paginationHtml = totalItems > 0 ? `
      <div class="card-footer" style="display:flex; align-items:center; justify-content:space-between; padding:14px 20px; border-top:1px solid var(--border-color); background:rgba(0,0,0,0.2); border-radius:0 0 12px 12px; flex-wrap:wrap; gap:12px;">
        <div style="display:flex; align-items:center; gap:16px; flex-wrap:wrap;">
          <div class="pagination-info" style="font-size:12px; color:var(--text-muted); margin:0;">
            Showing <strong style="color:var(--text-primary);">${startIndex + 1}–${endIndex}</strong> of <strong style="color:var(--text-primary);">${totalItems}</strong> orders (Page <strong style="color:var(--text-primary);">${currentPage + 1}</strong> of <strong style="color:var(--text-primary);">${totalPages}</strong>)
          </div>
          <div style="display:flex; align-items:center; gap:6px; font-size:12px; color:var(--text-secondary);">
            <span>Rows:</span>
            <select id="pageSizeSelect" class="form-select text-xs" style="width:auto; padding:3px 10px; height:28px; border-radius:6px; background:rgba(255,255,255,0.06); border:1px solid var(--border-color); color:var(--text-primary); cursor:pointer; font-weight:600;" onchange="window.handlePageSizeChange(this.value)">
              <option value="10" ${PAGE_SIZE === 10 ? 'selected' : ''}>10 / page</option>
              <option value="25" ${PAGE_SIZE === 25 ? 'selected' : ''}>25 / page</option>
              <option value="50" ${PAGE_SIZE === 50 ? 'selected' : ''}>50 / page</option>
              <option value="100" ${PAGE_SIZE === 100 ? 'selected' : ''}>100 / page</option>
            </select>
          </div>
        </div>
        ${totalPages > 1 ? `
          <div class="pagination" style="display:flex; align-items:center; gap:6px;">
            <button class="pagination-btn" onclick="window.goToProductionPage(${currentPage - 1})" ${currentPage === 0 ? 'disabled style="opacity:0.35;cursor:not-allowed;"' : ''} title="Previous Page">
              &laquo;
            </button>
            ${pagesHtml}
            <button class="pagination-btn" onclick="window.goToProductionPage(${currentPage + 1})" ${currentPage >= totalPages - 1 ? 'disabled style="opacity:0.35;cursor:not-allowed;"' : ''} title="Next Page">
              &raquo;
            </button>
          </div>
        ` : ''}
      </div>
    ` : '';

    boardEl.innerHTML = `
      <div class="card" style="width:100%;grid-column:1/-1;">
        <div class="card-header flex items-center justify-between" style="padding:16px 20px;">
          <div>
            <div class="card-title" style="font-size:16px;font-weight:700;">
              ${stageTitle} ${!Utils.canAdvanceProductionStage() ? '<span class="badge badge-warning" style="font-size:10px;margin-left:8px;">View Only Account</span>' : ''}
            </div>
            <div class="card-subtitle" style="font-size:12px;color:var(--text-muted);margin-top:2px;">
              ${totalItems > 0 ? `Showing ${startIndex + 1}–${endIndex} of ${totalItems} Active Production Orders (${totalPages} Pages • 25 rows/page)` : '0 Active Production Orders Listed'}
            </div>
          </div>
        </div>
        <div class="table-wrapper">
          <table class="table" style="width:100%;">
            <thead>
              <tr>
                <th>Order #</th>
                <th>Customer</th>
                <th>Current Production Stage</th>
                <th>Assigned Staff Employee</th>
                <th>Delivery Date</th>
                <th style="text-align:right">Action</th>
              </tr>
            </thead>
            <tbody>
              ${tableRowsHtml}
            </tbody>
          </table>
        </div>
        ${paginationHtml}
      </div>
    `;

    // Attach click listeners for stage advance
    boardEl.querySelectorAll('.advance-btn').forEach(btn => {
      btn.addEventListener('click', (e) => {
        const jobId = e.currentTarget.getAttribute('data-id');
        const dbId  = e.currentTarget.getAttribute('data-dbid');
        advanceJobStage(jobId, dbId);
      });
    });
  }

  function getNextStage(currentStageKey) {
    const idx = STAGES.findIndex(s => s.key === currentStageKey);
    if (idx >= 0 && idx < STAGES.length - 1) {
      return STAGES[idx + 1];
    }
    return null;
  }

  // ── Stage Advance & Employee Assignment Modal ──────────────────────────────
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

  async function advanceJobStage(jobId, dbId) {
    if (!Utils.canAdvanceProductionStage()) {
      Toast.warning('System Administrator account is View-Only. Changes restricted to floor staff.');
      return;
    }
    const job = jobs.find(j => j.id === jobId);
    if (!job) return;

    const nextStage = getNextStage(job.stage);
    if (!nextStage) return;

    const currentStageTitle = STAGES.find(s => s.key === job.stage)?.title || job.stage;

    // Filter physical staff employees strictly for target nextStage
    let stageStaff = allEmployees.filter(e => e.stage === nextStage.key && e.isActive !== false);

    // Fallback if allEmployees not preloaded
    if (allEmployees.length === 0) {
      try {
        const empData = await Api.get(`${API.EMPLOYEES}?size=100`);
        allEmployees = empData?.content ?? [];
        stageStaff = allEmployees.filter(e => e.stage === nextStage.key && e.isActive !== false);
      } catch (_) {}
    }

    // Populate modal select
    document.getElementById('advanceOrderId').value = dbId;
    document.getElementById('advanceTargetStage').value = nextStage.key;
    document.getElementById('advanceOrderInfo').textContent = `${job.id} — ${job.customer}`;
    document.getElementById('advanceStageTransition').textContent = `${currentStageTitle} ➔ ${nextStage.title}`;

    if (stageStaff.length > 0) {
      stageEmployeeSelect.innerHTML = `
        <option value="">-- Select ${nextStage.title} Staff Employee --</option>
        ${stageStaff.map(e => `
          <option value="${e.id}" data-name="${e.fullName}">${e.fullName} (${e.employeeCode})</option>
        `).join('')}
      `;
    } else {
      // Fallback if no specific stage staff found
      stageEmployeeSelect.innerHTML = `
        <option value="">-- Select Employee --</option>
        ${allEmployees.filter(e => e.isActive !== false && !['EMP-001','EMP-002','EMP-003','EMP-004'].includes(e.employeeCode)).map(e => `
          <option value="${e.id}" data-name="${e.fullName}">${e.fullName} (${e.employeeCode}) — ${e.stage || 'Staff'}</option>
        `).join('')}
      `;
    }

    if (advanceModalBackdrop) advanceModalBackdrop.classList.remove('hidden');
  }

  if (advanceStageForm) {
    advanceStageForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      const dbId = document.getElementById('advanceOrderId').value;
      const targetStage = document.getElementById('advanceTargetStage').value;
      const selectedOpt = stageEmployeeSelect.options[stageEmployeeSelect.selectedIndex];

      if (!selectedOpt || !selectedOpt.value) {
        Toast.error('Please select an employee for this production stage');
        return;
      }

      const empId = Number(selectedOpt.value);
      const empName = selectedOpt.getAttribute('data-name');

      const nextStageObj = STAGES.find(s => s.key === targetStage);
      let newStatus = targetStage;
      if (targetStage === 'DELIVERY' || targetStage === 'COMPLETED') {
        newStatus = 'DELIVERY';
      }

      const confirmBtn = document.getElementById('confirmAdvanceBtn');
      Utils.setLoading(confirmBtn, true, 'Saving...');

      try {
        await Api.put(`${API.ORDERS}/${dbId}/status`, {
          status: newStatus,
          assignedEmployeeId: empId,
          assignedEmployeeName: empName
        });

        Toast.success(`Order advanced to ${nextStageObj?.title || targetStage} & assigned to ${empName}`);
        closeAdvanceModal();
        await loadProductionJobs();
      } catch (err) {
        Toast.error(err.message || 'Failed to advance stage');
      } finally {
        Utils.setLoading(confirmBtn, false);
      }
    });
  }

  // ── Event Handlers ────────────────────────────────────────────────────────
  document.getElementById('refreshBtn')?.addEventListener('click', async () => {
    Toast.info('Refreshing Production Board...');
    await loadProductionJobs();
    Toast.success('Production Board refreshed');
  });

  // ── Pagination & Page Size Navigation Handlers ────────────────────────────
  window.goToProductionPage = function(pageIndex) {
    currentPage = pageIndex;
    renderBoard(document.getElementById('jobSearch')?.value.toLowerCase() || '', currentStageFilter);
    const boardEl = document.getElementById('productionBoard');
    if (boardEl) {
      boardEl.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  };

  window.handlePageSizeChange = function(newSize) {
    PAGE_SIZE = parseInt(newSize, 10) || 25;
    currentPage = 0;
    renderBoard(document.getElementById('jobSearch')?.value.toLowerCase() || '', currentStageFilter);
  };

  document.getElementById('jobSearch')?.addEventListener('input', (e) => {
    currentPage = 0;
    renderBoard(e.target.value.toLowerCase(), currentStageFilter);
  });

  document.getElementById('stageFilter')?.addEventListener('change', (e) => {
    const val = e.target.value;
    currentPage = 0;
    Utils.setQueryParam('stage', val);
    renderBoard(document.getElementById('jobSearch')?.value.toLowerCase() || '', val);
  });

  // ── Auto-polling & Focus Auto-refresh ──────────────────────────────────────
  setInterval(loadProductionJobs, 10000);
  window.addEventListener('focus', loadProductionJobs);

  // Initial load from real database API
  await loadProductionJobs();
});
