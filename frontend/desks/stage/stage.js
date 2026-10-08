/**
 * Ritham ERP — Worker Categories & Production Stages (stage.js)
 * High-Performance Page Controller with Pipeline Cards, Interactive Stepper & Enterprise Table
 */

'use strict';

function getStageIconSvg(stageKeyOrIcon) {
  const k = (stageKeyOrIcon || '').toUpperCase();
  if (k.includes('PATTERN') || k.includes('DESIGN')) {
    return `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 20h9"/><path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z"/></svg>`;
  }
  if (k.includes('CUT')) {
    return `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="6" cy="6" r="3"/><circle cx="6" cy="18" r="3"/><line x1="20" y1="4" x2="8.12" y2="15.88"/><line x1="14.47" y1="14.48" x2="20" y2="20"/><line x1="8.12" y1="8.12" x2="12" y2="12"/></svg>`;
  }
  if (k.includes('EMBROID') || k.includes('HAND')) {
    return `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 2v20M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6"/></svg>`;
  }
  if (k.includes('SEW') || k.includes('STITCH')) {
    return `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 12A10 10 0 1 1 12 2v10z"/></svg>`;
  }
  if (k.includes('BUTTON') || k.includes('FIT')) {
    return `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="9"/><circle cx="9" cy="9" r="1"/><circle cx="15" cy="9" r="1"/><circle cx="9" cy="15" r="1"/><circle cx="15" cy="15" r="1"/></svg>`;
  }
  if (k.includes('CHECK') || k.includes('QUALITY') || k.includes('QC')) {
    return `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>`;
  }
  if (k.includes('IRON') || k.includes('PRESS')) {
    return `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 14.89V19h16v-4.11a5 5 0 0 0-1.84-3.85L12 6.5 5.84 11.04A5 5 0 0 0 4 14.89z"/></svg>`;
  }
  if (k.includes('PACK') || k.includes('PICKUP') || k.includes('DELIVERY')) {
    return `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/><polyline points="3.27 6.96 12 12.01 20.73 6.96"/><line x1="12" y1="22.08" x2="12" y2="12"/></svg>`;
  }
  if (k.includes('LINING')) {
    return `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="18" height="18" rx="2" ry="2"/><line x1="12" y1="3" x2="12" y2="21"/></svg>`;
  }
  return `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="18" height="18" rx="2" ry="2"/><line x1="9" y1="3" x2="9" y2="21"/></svg>`;
}

function hexToRgba(hex, alpha = 0.15) {
  if (!hex || !hex.startsWith('#')) return `rgba(99, 102, 241, ${alpha})`;
  let c = hex.substring(1);
  if (c.length === 3) c = c.split('').map(x => x + x).join('');
  const num = parseInt(c, 16);
  const r = (num >> 16) & 255;
  const g = (num >> 8) & 255;
  const b = num & 255;
  return `rgba(${r}, ${g}, ${b}, ${alpha})`;
}

document.addEventListener('DOMContentLoaded', async () => {

  // Auth Guard
  if (!Router.protect([ROLES.ADMIN, ROLES.OPERATIONS_MANAGER])) return;

  // Initialize Layout
  Sidebar.init({ activePage: 'stage' });
  Header.init({ title: 'Production Stages', subtitle: 'Garment manufacturing stage workflows & staff distribution' });

  // State
  let stagesList = [];
  let allOrders = [];
  let allEmployees = [];
  let tempReorderList = [];
  let editingStageId = null;

  // DOM Elements
  const refreshBtn = document.getElementById('refreshStagesBtn');
  const addStageBtn = document.getElementById('addStageBtn');
  const overallEditBtn = document.getElementById('overallEditBtn');
  const stageSearchInput = document.getElementById('stageSearchInput');
  const stageFilterStaff = document.getElementById('stageFilterStaff');
  const stageFilterStatus = document.getElementById('stageFilterStatus');
  const stageTableCard = document.getElementById('stageTableCard');
  const stageTableBody = document.getElementById('stageTableBody');
  const workflowRibbon = document.getElementById('workflowRibbon');
  const scrollWorkflowRightBtn = document.getElementById('scrollWorkflowRightBtn');

  // Create/Edit Stage Modal Elements
  const stageModalBackdrop = document.getElementById('stageModalBackdrop');
  const stageModalTitle = document.getElementById('stageModalTitle');
  const stageModalCloseBtn = document.getElementById('stageModalCloseBtn');
  const stageCancelBtn = document.getElementById('stageCancelBtn');
  const stageForm = document.getElementById('stageForm');
  const editStageId = document.getElementById('editStageId');
  const stageTitleInput = document.getElementById('stageTitleInput');
  const stageDescInput = document.getElementById('stageDescInput');
  const stageColorInput = document.getElementById('stageColorInput');
  const stageActiveCheckbox = document.getElementById('stageActiveCheckbox');
  const deleteStageBtn = document.getElementById('deleteStageBtn');
  const duplicateStageModalBtn = document.getElementById('duplicateStageModalBtn');
  const saveStageBtn = document.getElementById('saveStageBtn');
  const saveStageBtnText = document.getElementById('saveStageBtnText');

  // Stage Workers Modal Elements
  const stageWorkersModalBackdrop = document.getElementById('stageWorkersModalBackdrop');
  const stageWorkersModalTitle = document.getElementById('stageWorkersModalTitle');
  const stageWorkersModalSubtitle = document.getElementById('stageWorkersModalSubtitle');
  const stageWorkersModalBody = document.getElementById('stageWorkersModalBody');
  const stageWorkersModalCloseBtn = document.getElementById('stageWorkersModalCloseBtn');
  const stageWorkersModalCloseFooterBtn = document.getElementById('stageWorkersModalCloseFooterBtn');

  // Reorder Modal Elements
  const reorderModalBackdrop = document.getElementById('reorderModalBackdrop');
  const reorderModalCloseBtn = document.getElementById('reorderModalCloseBtn');
  const reorderCancelBtn = document.getElementById('reorderCancelBtn');
  const reorderStageList = document.getElementById('reorderStageList');
  const saveReorderBtn = document.getElementById('saveReorderBtn');
  const modalAddNewStageBtn = document.getElementById('modalAddNewStageBtn');

  // Initial Data Load
  await loadData();

  // ── Event Handlers ────────────────────────────────────────────────────────

  if (refreshBtn) {
    refreshBtn.addEventListener('click', async () => {
      Toast.info('Refreshing production stages and workforce...');
      await loadData();
      Toast.success('Production stages updated');
    });
  }

  if (addStageBtn) addStageBtn.addEventListener('click', openCreateStageModal);
  if (overallEditBtn) overallEditBtn.addEventListener('click', openReorderModal);

  const exportStagesExcelBtn = document.getElementById('exportStagesExcelBtn');
  if (exportStagesExcelBtn) {
    exportStagesExcelBtn.addEventListener('click', () => {
      const stagesToExport = getFilteredStages();
      if (!stagesToExport || stagesToExport.length === 0) {
        Toast.warning('No production stages found to export');
        return;
      }
      const data = stagesToExport.map((stg, idx) => {
        const stageKey = (stg.stageKey || '').toUpperCase();
        const stageWorkers = (allEmployees || []).filter(e => (e.stage || '').toUpperCase() === stageKey);
        return {
          'Sequence': stg.seqOrder || (idx + 1),
          'Stage Key': stg.stageKey || '—',
          'Stage Name': stg.title || '—',
          'Description': stg.description || '—',
          'Color Hex': stg.color || '#6366f1',
          'Status': stg.isActive !== false ? 'Active' : 'Inactive',
          'Assigned Workers': stageWorkers.length,
          'Staff List': stageWorkers.map(w => `${w.fullName} (${w.employeeCode})`).join(', ') || 'None'
        };
      });
      ExcelExport.exportData(data, {
        filename: 'Ritham_Production_Stages',
        sheetName: 'Stages',
        columnWidths: [10, 16, 24, 35, 12, 12, 18, 45]
      });
    });
  }

  // Search & Filter Listeners
  if (stageSearchInput) stageSearchInput.addEventListener('input', renderViews);
  if (stageFilterStaff) stageFilterStaff.addEventListener('change', renderViews);
  if (stageFilterStatus) stageFilterStatus.addEventListener('change', renderViews);

  // Scroll ribbon helper
  if (scrollWorkflowRightBtn && workflowRibbon) {
    scrollWorkflowRightBtn.addEventListener('click', () => {
      workflowRibbon.scrollBy({ left: 240, behavior: 'smooth' });
    });
  }

  // Modal Closures
  if (stageModalCloseBtn) stageModalCloseBtn.addEventListener('click', closeStageModal);
  if (stageCancelBtn) stageCancelBtn.addEventListener('click', closeStageModal);
  if (stageModalBackdrop) {
    stageModalBackdrop.addEventListener('click', (e) => {
      if (e.target === stageModalBackdrop) closeStageModal();
    });
  }

  if (stageForm) {
    stageForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      await handleStageFormSubmit();
    });
  }

  // Color Swatches
  document.querySelectorAll('.color-swatch').forEach(swatch => {
    swatch.addEventListener('click', () => {
      document.querySelectorAll('.color-swatch').forEach(s => s.classList.remove('active'));
      swatch.classList.add('active');
      const color = swatch.getAttribute('data-color') || '#6366f1';
      if (stageColorInput) stageColorInput.value = color;
    });
  });

  // Stage Workers Modal
  if (stageWorkersModalCloseBtn) stageWorkersModalCloseBtn.addEventListener('click', closeStageWorkersModal);
  if (stageWorkersModalCloseFooterBtn) stageWorkersModalCloseFooterBtn.addEventListener('click', closeStageWorkersModal);
  if (stageWorkersModalBackdrop) {
    stageWorkersModalBackdrop.addEventListener('click', (e) => {
      if (e.target === stageWorkersModalBackdrop) closeStageWorkersModal();
    });
  }

  // Reorder Modal Handlers
  if (reorderModalCloseBtn) reorderModalCloseBtn.addEventListener('click', closeReorderModal);
  if (reorderCancelBtn) reorderCancelBtn.addEventListener('click', closeReorderModal);
  if (reorderModalBackdrop) {
    reorderModalBackdrop.addEventListener('click', (e) => {
      if (e.target === reorderModalBackdrop) closeReorderModal();
    });
  }
  if (saveReorderBtn) saveReorderBtn.addEventListener('click', handleSaveReorder);
  if (modalAddNewStageBtn) {
    modalAddNewStageBtn.addEventListener('click', () => {
      closeReorderModal();
      openCreateStageModal();
    });
  }

  // ── Core Data Loading ─────────────────────────────────────────────────────

  async function loadData() {
    try {
      const [stagesRes, ordersRes, empsRes] = await Promise.all([
        Api.get(`${API.PRODUCTION_STAGES}?activeOnly=false`).catch(() => []),
        Api.get(`${API.ORDERS}?page=0&size=1000`).catch(() => ({ content: [] })),
        Api.get(`${API.EMPLOYEES}?page=0&size=500`).catch(() => ({ content: [] }))
      ]);

      stagesList = stagesRes.data || stagesRes || [];
      allOrders = ordersRes.content || ordersRes.data?.content || ordersRes || [];
      const rawEmployees = empsRes.content || empsRes.data?.content || empsRes || [];

      // Filter out the 4 system dashboard accounts (EMP-001 through EMP-004)
      allEmployees = (rawEmployees || []).filter(e =>
        !['EMP-001', 'EMP-002', 'EMP-003', 'EMP-004'].includes(e.employeeCode) &&
        !['admin', 'manager', 'reception', 'production'].includes((e.username || '').toLowerCase())
      );

      // Sort by seqOrder ascending
      stagesList.sort((a, b) => (a.seqOrder || 0) - (b.seqOrder || 0));

      renderWorkflowRibbon();
      renderViews();

    } catch (err) {
      Toast.error('Failed to load production stage data');
    }
  }

  // ── Sequential Workflow Ribbon ────────────────────────────────────────────

  function renderWorkflowRibbon() {
    if (!workflowRibbon) return;

    if (!stagesList || stagesList.length === 0) {
      workflowRibbon.innerHTML = '<div style="font-size:12px; color:var(--text-muted); padding:8px;">No production stages configured yet.</div>';
      return;
    }

    const activeStages = stagesList.filter(s => s.isActive !== false);

    workflowRibbon.innerHTML = activeStages.map((stg, idx) => {
      const stageKey = (stg.stageKey || '').toUpperCase();
      const stageWorkers = (allEmployees || []).filter(e => (e.stage || '').toUpperCase() === stageKey);
      const isLast = idx === activeStages.length - 1;

      return `
        <div class="workflow-step-node" id="ribbon-step-${stg.id}" onclick="window.scrollToStage(${stg.id})" title="Jump to ${stg.title}">
          <span class="step-seq-pill">#${String(idx + 1).padStart(2, '0')}</span>
          <span style="color:${stg.color || '#818cf8'}; display:flex; align-items:center;">
            ${getStageIconSvg(stg.stageKey || stg.title)}
          </span>
          <span class="step-title-text">${stg.title}</span>
          <span class="step-worker-chip" title="${stageWorkers.length} assigned workers">${stageWorkers.length}w</span>
        </div>
        ${!isLast ? '<span class="workflow-separator">→</span>' : ''}
      `;
    }).join('');
  }

  window.scrollToStage = (id) => {
    // Highlight in ribbon
    document.querySelectorAll('.workflow-step-node').forEach(n => n.classList.remove('active-highlight'));
    const ribbonNode = document.getElementById(`ribbon-step-${id}`);
    if (ribbonNode) ribbonNode.classList.add('active-highlight');

    // Scroll table row into view
    const rowEl = document.getElementById(`stage-row-${id}`);
    if (rowEl) {
      rowEl.scrollIntoView({ behavior: 'smooth', block: 'center' });
      rowEl.style.transition = 'background 0.3s ease';
      const origBg = rowEl.style.backgroundColor;
      rowEl.style.backgroundColor = 'rgba(99, 102, 241, 0.18)';
      setTimeout(() => {
        rowEl.style.backgroundColor = origBg;
      }, 2000);
    }
  };

  // ── Filter & Search Logic ─────────────────────────────────────────────────

  function getFilteredStages() {
    const q = (stageSearchInput?.value || '').trim().toLowerCase();
    const staffFilter = stageFilterStaff?.value || 'ALL';
    const statusFilter = stageFilterStatus?.value || 'ALL';

    return stagesList.filter(stg => {
      const stageKey = (stg.stageKey || '').toUpperCase();
      const stageWorkers = (allEmployees || []).filter(e => (e.stage || '').toUpperCase() === stageKey);

      // Status filter
      if (statusFilter === 'ACTIVE' && stg.isActive === false) return false;
      if (statusFilter === 'INACTIVE' && stg.isActive !== false) return false;

      // Staffing filter
      if (staffFilter === 'STAFFED' && stageWorkers.length === 0) return false;
      if (staffFilter === 'UNSTAFFED' && stageWorkers.length > 0) return false;

      // Text query
      if (q) {
        const matchesTitle = (stg.title || '').toLowerCase().includes(q);
        const matchesDesc = (stg.description || '').toLowerCase().includes(q);
        const matchesKey = (stg.stageKey || '').toLowerCase().includes(q);
        const matchesWorker = stageWorkers.some(w =>
          (w.fullName || '').toLowerCase().includes(q) ||
          (w.employeeCode || '').toLowerCase().includes(q) ||
          (w.mobileNumber || '').includes(q)
        );
        if (!matchesTitle && !matchesDesc && !matchesKey && !matchesWorker) {
          return false;
        }
      }

      return true;
    });
  }

  function renderViews() {
    const filtered = getFilteredStages();
    renderStageTable(filtered);
  }

  // ── Table View ────────────────────────────────────────────────────────────

  function renderStageTable(stages) {
    if (!stageTableBody) return;

    if (!stages || stages.length === 0) {
      stageTableBody.innerHTML = `
        <tr>
          <td colspan="6" style="padding: 32px; text-align: center;">
            <div style="font-size: 14px; font-weight: 600; color: var(--text-primary); margin-bottom: 4px;">No stages found</div>
            <div style="font-size: 12px; color: var(--text-muted);">No production stages match your current filter.</div>
          </td>
        </tr>
      `;
      return;
    }

    stageTableBody.innerHTML = stages.map((stg, idx) => {
      const stageKey = (stg.stageKey || '').toUpperCase();
      const stageWorkers = (allEmployees || []).filter(e => (e.stage || '').toUpperCase() === stageKey);

      // Visible worker avatar bubbles
      const visibleWorkers = stageWorkers.slice(0, 3);
      const remainingCount = stageWorkers.length - visibleWorkers.length;

      const avatarsHtml = stageWorkers.length > 0 ? `
        <div style="display:flex; align-items:center; gap:8px;">
          <div class="worker-avatars-stack">
            ${visibleWorkers.map((w, wIdx) => {
        const initials = (w.fullName || 'W').split(' ').map(p => p[0]).join('').substring(0, 2).toUpperCase();
        const colors = ['#6366f1', '#10b981', '#f59e0b', '#ec4899', '#3b82f6'];
        const bg = colors[wIdx % colors.length];
        return `<div class="worker-bubble" style="background:${bg}; width:24px; height:24px; font-size:10px;" title="${w.fullName} (${w.employeeCode})">${initials}</div>`;
      }).join('')}
            ${remainingCount > 0 ? `<div class="worker-bubble worker-bubble-more" style="width:24px; height:24px; font-size:9px;">+${remainingCount}</div>` : ''}
          </div>
          <span class="badge" style="background:rgba(99,102,241,0.12); color:#818cf8; font-size:11px; font-weight:700;">
            ${stageWorkers.length} Staff
          </span>
          <span style="font-size:12px; color:var(--text-secondary);">
            ${stageWorkers.slice(0, 2).map(w => w.fullName).join(', ')}${stageWorkers.length > 2 ? ` +${stageWorkers.length - 2}` : ''}
          </span>
        </div>
      ` : `
        <span style="font-size:12px; color:var(--text-muted); font-style:italic;">No staff assigned</span>
      `;

      return `
        <tr id="stage-row-${stg.id}">
          <td>
            <span class="stage-table-seq-badge">#${String(stg.seqOrder || (idx + 1)).padStart(2, '0')}</span>
          </td>

          <td>
            <div style="display:flex; align-items:center; gap:10px;">
              <div style="width:34px; height:34px; border-radius:8px; display:flex; align-items:center; justify-content:center; background:${stg.bgColor || hexToRgba(stg.color, 0.15)}; color:${stg.color || '#818cf8'}; border: 1px solid ${stg.color || '#818cf8'}33; flex-shrink:0;">
                ${getStageIconSvg(stg.stageKey || stg.title)}
              </div>
              <div>
                <div style="font-weight:600; font-size:14px; color:var(--text-primary); cursor:pointer;" onclick="window.openEditStageModal(${stg.id})" title="Click to edit stage">
                  ${stg.title}
                </div>
                <div style="font-size:11px; color:var(--text-muted);">${stg.description || 'Production floor manufacturing step'}</div>
              </div>
            </div>
          </td>

          <td>
            <div class="stage-table-worker-chip" onclick="window.openStageWorkersModal(${stg.id})" title="Click to view assigned factory staff">
              ${avatarsHtml}
            </div>
          </td>

          <td>
            <span class="badge badge-neutral" style="font-size:11px;">
              Active Flow
            </span>
          </td>

          <td>
            <span class="badge ${stg.isActive !== false ? 'badge-completed' : 'badge-neutral'}">
              ${stg.isActive !== false ? 'Active' : 'Inactive'}
            </span>
          </td>

          <td style="text-align:right;">
            <div style="display:inline-flex; gap:6px;">
              <button class="btn btn-secondary btn-sm" onclick="window.openEditStageModal(${stg.id})" title="Edit Stage">
                Edit
              </button>
              <button class="btn btn-danger btn-sm" onclick="window.deleteStageDirectly(${stg.id})" title="Delete Stage">
                Delete
              </button>
            </div>
          </td>
        </tr>
      `;
    }).join('');
  }

  window.triggerSearch = () => {
    renderViews();
  };

  // ── Stage Workers Details Modal ───────────────────────────────────────────

  window.openStageWorkersModal = (stageId) => {
    const stg = stagesList.find(s => s.id === stageId);
    if (!stg) return;

    const stageKey = (stg.stageKey || '').toUpperCase();
    const stageWorkers = (allEmployees || []).filter(e => (e.stage || '').toUpperCase() === stageKey);

    stageWorkersModalTitle.textContent = `${stg.title} — Floor Workers`;
    stageWorkersModalSubtitle.textContent = `${stageWorkers.length} factory worker(s) assigned to this production category`;

    if (stageWorkers.length === 0) {
      stageWorkersModalBody.innerHTML = `
        <div style="padding:40px 20px; text-align:center;">
          <div style="width:48px; height:48px; border-radius:50%; background:rgba(99,102,241,0.1); color:#818cf8; display:flex; align-items:center; justify-content:center; margin:0 auto 12px auto;">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><line x1="19" y1="8" x2="19" y2="14"/><line x1="22" y1="11" x2="16" y2="11"/></svg>
          </div>
          <div style="font-size:15px; font-weight:600; color:var(--text-primary); margin-bottom:4px;">No Workers Assigned Yet</div>
          <p style="font-size:13px; color:var(--text-muted); max-width:340px; margin:0 auto 16px auto;">
            Factory staff can be assigned to the "${stg.title}" stage in the Employee Directory desk.
          </p>
          <a href="../employee/employee.html" class="btn btn-primary btn-sm">
            Open Employee Desk to Assign
          </a>
        </div>
      `;
    } else {
      stageWorkersModalBody.innerHTML = `
        <div style="display:flex; flex-direction:column; gap:8px;">
          ${stageWorkers.map(w => {
        const initials = (w.fullName || 'W').split(' ').map(p => p[0]).join('').substring(0, 2).toUpperCase();
        return `
              <div class="worker-list-row">
                <div style="display:flex; align-items:center; gap:12px;">
                  <div class="worker-bubble" style="background:#6366f1; width:36px; height:36px; font-size:13px; border:none; margin:0;">
                    ${initials}
                  </div>
                  <div>
                    <div style="font-weight:600; font-size:14px; color:var(--text-primary);">${w.fullName}</div>
                    <div style="font-size:12px; color:var(--text-muted); display:flex; align-items:center; gap:8px;">
                      <span style="font-family:monospace;">${w.employeeCode}</span>
                      <span>•</span>
                      <span>📱 ${w.mobileNumber || 'No mobile'}</span>
                    </div>
                  </div>
                </div>

                <div style="display:flex; align-items:center; gap:16px;">
                  ${w.advance ? `<div style="text-align:right;"><div style="font-size:10px; color:var(--text-muted); text-transform:uppercase;">Advance</div><div style="font-weight:700; font-size:13px; color:#fb923c;">₹${w.advance.toLocaleString('en-IN')}</div></div>` : ''}
                  ${w.joiningDate ? `<div style="text-align:right;"><div style="font-size:10px; color:var(--text-muted); text-transform:uppercase;">Joined</div><div style="font-size:12px; color:var(--text-secondary);">${w.joiningDate}</div></div>` : ''}
                </div>
              </div>
            `;
      }).join('')}
        </div>
      `;
    }

    stageWorkersModalBackdrop.classList.remove('hidden');
  };

  function closeStageWorkersModal() {
    stageWorkersModalBackdrop.classList.add('hidden');
  }

  // ── Create & Edit Stage Modal Handlers ─────────────────────────────────────

  function openCreateStageModal() {
    editingStageId = null;
    editStageId.value = '';
    stageModalTitle.textContent = 'Create Custom Production Stage';
    saveStageBtnText.textContent = 'Create Stage';
    stageForm.reset();
    stageColorInput.value = '#6366f1';
    document.querySelectorAll('.color-swatch').forEach(s => {
      s.classList.toggle('active', s.getAttribute('data-color') === '#6366f1');
    });
    if (stageActiveCheckbox) stageActiveCheckbox.checked = true;
    if (deleteStageBtn) deleteStageBtn.classList.add('hidden');
    if (duplicateStageModalBtn) duplicateStageModalBtn.classList.add('hidden');

    stageModalBackdrop.classList.remove('hidden');
    setTimeout(() => stageTitleInput.focus(), 50);
  }

  window.openEditStageModal = (id) => {
    const stg = stagesList.find(s => s.id === id);
    if (!stg) return;

    editingStageId = stg.id;
    editStageId.value = stg.id;
    stageModalTitle.textContent = `Edit Stage — ${stg.title}`;
    saveStageBtnText.textContent = 'Save Changes';

    stageTitleInput.value = stg.title;
    stageDescInput.value = stg.description || '';
    const color = stg.color || '#6366f1';
    stageColorInput.value = color;

    document.querySelectorAll('.color-swatch').forEach(s => {
      s.classList.toggle('active', s.getAttribute('data-color')?.toLowerCase() === color.toLowerCase());
    });

    if (stageActiveCheckbox) stageActiveCheckbox.checked = stg.isActive !== false;
    if (deleteStageBtn) deleteStageBtn.classList.remove('hidden');
    if (duplicateStageModalBtn) duplicateStageModalBtn.classList.remove('hidden');

    stageModalBackdrop.classList.remove('hidden');
    setTimeout(() => {
      stageTitleInput.focus();
      stageTitleInput.select();
    }, 50);
  };

  function closeStageModal() {
    stageModalBackdrop.classList.add('hidden');
    stageForm.reset();
    editingStageId = null;
  }

  if (deleteStageBtn) {
    deleteStageBtn.addEventListener('click', async () => {
      if (!editingStageId) return;
      if (!confirm('Are you sure you want to deactivate/delete this production stage?')) return;

      Utils.setLoading(deleteStageBtn, true, 'Deleting...');
      try {
        await Api.del(`/api/production-stages/${editingStageId}`);
        Toast.success('Production stage deactivated successfully');
        closeStageModal();
        await loadData();
      } catch (err) {
        Toast.error(err.message || 'Failed to delete stage');
      } finally {
        Utils.setLoading(deleteStageBtn, false);
      }
    });
  }

  if (duplicateStageModalBtn) {
    duplicateStageModalBtn.addEventListener('click', () => {
      const currentTitle = stageTitleInput.value.trim() || 'Stage';
      editingStageId = null;
      editStageId.value = '';
      stageModalTitle.textContent = `Duplicate Stage — Copy of ${currentTitle}`;
      saveStageBtnText.textContent = 'Save New Duplicate Stage';
      if (!stageTitleInput.value.includes('(Copy)')) {
        stageTitleInput.value = `${currentTitle} (Copy)`;
      }
      if (deleteStageBtn) deleteStageBtn.classList.add('hidden');
      if (duplicateStageModalBtn) duplicateStageModalBtn.classList.add('hidden');
      stageTitleInput.focus();
      stageTitleInput.select();
      Toast.info('Switched to Duplicate Stage creation mode');
    });
  }

  async function handleStageFormSubmit() {
    const title = stageTitleInput.value.trim();
    if (!title) {
      Toast.error('Please enter a stage title');
      return;
    }

    const selectedColor = stageColorInput.value || '#6366f1';
    const payload = {
      title: title,
      description: stageDescInput.value.trim() || null,
      icon: title.charAt(0).toUpperCase() || 'P',
      color: selectedColor,
      bgColor: hexToRgba(selectedColor, 0.15),
      isActive: stageActiveCheckbox ? stageActiveCheckbox.checked : true
    };

    Utils.setLoading(saveStageBtn, true, editingStageId ? 'Saving...' : 'Creating...');

    try {
      if (editingStageId) {
        await Api.put(`${API.PRODUCTION_STAGES}/${editingStageId}`, payload);
        Toast.success('Production stage updated successfully');
      } else {
        await Api.post(API.PRODUCTION_STAGES, payload);
        Toast.success('Custom production stage created successfully');
      }
      closeStageModal();
      await loadData();
    } catch (err) {
      Toast.error(err.message || 'Failed to save stage');
    } finally {
      Utils.setLoading(saveStageBtn, false);
    }
  }

  window.deleteStageDirectly = async (id) => {
    const stg = stagesList.find(s => s.id === id);
    if (!stg) return;

    if (!confirm(`Are you sure you want to deactivate/delete stage "${stg.title}"?`)) return;

    try {
      await Api.del(`/api/production-stages/${id}`);
      Toast.success(`Stage "${stg.title}" deactivated successfully`);
      await loadData();
    } catch (err) {
      Toast.error(err.message || 'Failed to delete stage');
    }
  };

  // ── Reorder Sequence Modal ────────────────────────────────────────────────

  function openReorderModal() {
    tempReorderList = [...stagesList];
    renderReorderList();
    reorderModalBackdrop.classList.remove('hidden');
  }

  function closeReorderModal() {
    reorderModalBackdrop.classList.add('hidden');
  }

  function renderReorderList() {
    if (!reorderStageList) return;

    reorderStageList.innerHTML = tempReorderList.map((stg, idx) => `
      <div class="reorder-item" draggable="true" 
           ondragstart="window.handleReorderDragStart(event, ${idx})"
           ondragover="window.handleReorderDragOver(event)"
           ondragleave="window.handleReorderDragLeave(event)"
           ondrop="window.handleReorderDrop(event, ${idx})"
           ondragend="window.handleReorderDragEnd(event)"
           style="cursor:grab;">

        <div style="display:flex; align-items:center; gap:12px; flex:1; min-width:0;">
          <span style="font-size:16px; color:var(--text-muted); cursor:grab;" title="Drag to reorder sequence">⣿</span>
          <span class="reorder-item-seq">#${String(idx + 1).padStart(2, '0')}</span>
          <div style="width:32px; height:32px; border-radius:8px; display:flex; align-items:center; justify-content:center; background:${stg.bgColor || hexToRgba(stg.color, 0.15)}; color:${stg.color || '#818cf8'}; flex-shrink:0;">
            ${getStageIconSvg(stg.stageKey || stg.title)}
          </div>
          <div style="flex:1; min-width:0;">
            <div class="reorder-item-title">${stg.title}</div>
            <div style="font-size:11px; color:var(--text-muted); font-family:monospace;">${stg.stageKey}</div>
          </div>
        </div>

        <div class="reorder-item-actions">
          <button type="button" class="btn btn-ghost btn-xs" ${idx === 0 ? 'disabled style="opacity:0.3;"' : ''} onclick="window.moveStageOrder(${idx}, -1)" title="Move Stage Up">
            ▲ Up
          </button>
          <button type="button" class="btn btn-ghost btn-xs" ${idx === tempReorderList.length - 1 ? 'disabled style="opacity:0.3;"' : ''} onclick="window.moveStageOrder(${idx}, 1)" title="Move Stage Down">
            ▼ Down
          </button>
        </div>
      </div>
    `).join('');
  }

  window.moveStageOrder = (index, direction) => {
    const targetIndex = index + direction;
    if (targetIndex < 0 || targetIndex >= tempReorderList.length) return;

    const item = tempReorderList.splice(index, 1)[0];
    tempReorderList.splice(targetIndex, 0, item);
    renderReorderList();
  };

  let dragSrcIdx = null;

  window.handleReorderDragStart = (e, idx) => {
    dragSrcIdx = idx;
    e.target.classList.add('dragging');
    e.dataTransfer.effectAllowed = 'move';
  };

  window.handleReorderDragOver = (e) => {
    e.preventDefault();
    e.dataTransfer.dropEffect = 'move';
    const row = e.target.closest('.reorder-item');
    if (row) row.classList.add('drag-over');
  };

  window.handleReorderDragLeave = (e) => {
    const row = e.target.closest('.reorder-item');
    if (row) row.classList.remove('drag-over');
  };

  window.handleReorderDrop = (e, targetIdx) => {
    e.preventDefault();
    document.querySelectorAll('.reorder-item').forEach(r => r.classList.remove('drag-over'));
    if (dragSrcIdx === null || dragSrcIdx === targetIdx) return;

    const moved = tempReorderList.splice(dragSrcIdx, 1)[0];
    tempReorderList.splice(targetIdx, 0, moved);
    renderReorderList();
  };

  window.handleReorderDragEnd = (e) => {
    e.target.classList.remove('dragging');
    document.querySelectorAll('.reorder-item').forEach(r => r.classList.remove('drag-over'));
  };

  async function handleSaveReorder() {
    const orderedIds = tempReorderList.map(s => s.id);
    Utils.setLoading(saveReorderBtn, true, 'Saving Sequence...');

    try {
      await Api.put(`${API.PRODUCTION_STAGES}/reorder`, { stageIds: orderedIds });
      Toast.success('Production stage sequence updated successfully');
      closeReorderModal();
      await loadData();
    } catch (err) {
      Toast.error(err.message || 'Failed to reorder stages');
    } finally {
      Utils.setLoading(saveReorderBtn, false);
    }
  }

});
