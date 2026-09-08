/**
 * Ritham ERP — Customer Measurement Desk (measurement.js)
 * Sizing Specifications & Profiles Linked Directly to Customer Mobile Number & Name
 */

'use strict';

document.addEventListener('DOMContentLoaded', async () => {

  if (!Router.protect()) return;

  Sidebar.init({ activePage: 'measurement' });
  Header.init({ title: 'Customer Measurements', subtitle: 'Garment Profiles by Mobile Number' });

  let allRecords = [];
  let currentFilter = 'ALL';
  let searchTimer = null;
  let lookupTimer = null;

  const gridEl       = document.getElementById('msrCardsGrid');
  const searchInput  = document.getElementById('msrSearchInput');
  const refreshBtn   = document.getElementById('refreshBtn');
  const newBtn       = document.getElementById('newMeasurementBtn');

  // Sheet View Modal
  const sheetModalBackdrop = document.getElementById('printSheetModalBackdrop');
  const sheetModalBody     = document.getElementById('sheetModalBody');
  const closeSheetBtn      = document.getElementById('closeSheetModalBtn');
  const closeSheetFooter   = document.getElementById('closeSheetFooterBtn');
  const printBtn           = document.getElementById('triggerPrintBtn');

  // Record / Edit Modal
  const recordModalBackdrop = document.getElementById('recordMeasurementModalBackdrop');
  const recordModalTitle    = document.getElementById('recordModalTitle');
  const closeRecordModalBtn = document.getElementById('closeRecordModalBtn');
  const cancelRecordModalBtn= document.getElementById('cancelRecordModalBtn');
  const saveMeasurementBtn  = document.getElementById('saveMeasurementBtn');

  const modalMobile      = document.getElementById('modalCustomerMobile');
  const modalName        = document.getElementById('modalCustomerName');
  const modalGarmentType = document.getElementById('modalGarmentType');
  const modalNotes       = document.getElementById('modalNotes');
  const lookupStatus     = document.getElementById('custLookupStatus');
  const blousePanel      = document.getElementById('modalBlousePanel');
  const chudiPanel       = document.getElementById('modalChudiPanel');
  const clearBlouseBtn   = document.getElementById('clearModalBlouseBtn');
  const clearChudiBtn    = document.getElementById('clearModalChudiBtn');

  function closeSheetModal() {
    sheetModalBackdrop?.classList.add('hidden');
  }

  function closeRecordModal() {
    recordModalBackdrop?.classList.add('hidden');
  }

  closeSheetBtn?.addEventListener('click', closeSheetModal);
  closeSheetFooter?.addEventListener('click', closeSheetModal);
  sheetModalBackdrop?.addEventListener('click', e => {
    if (e.target === sheetModalBackdrop) closeSheetModal();
  });

  closeRecordModalBtn?.addEventListener('click', closeRecordModal);
  cancelRecordModalBtn?.addEventListener('click', closeRecordModal);
  recordModalBackdrop?.addEventListener('click', e => {
    if (e.target === recordModalBackdrop) closeRecordModal();
  });

  printBtn?.addEventListener('click', () => {
    window.print();
  });

  // Toggle Blouse / Chudi panels in modal
  function updateModalGarmentPanels() {
    const isBlouse = modalGarmentType.value === 'BLOUSE';
    if (blousePanel) blousePanel.style.display = isBlouse ? 'block' : 'none';
    if (chudiPanel)  chudiPanel.style.display  = isBlouse ? 'none' : 'block';
  }
  modalGarmentType?.addEventListener('change', () => {
    updateModalGarmentPanels();
    if (modalMobile.value.trim().length === 10) {
      checkExistingCustomerSpecs(modalMobile.value.trim(), modalGarmentType.value);
    }
  });

  clearBlouseBtn?.addEventListener('click', () => {
    document.querySelectorAll('#modalBlousePanel input[data-garment="blouse"]').forEach(inp => inp.value = '');
  });

  clearChudiBtn?.addEventListener('click', () => {
    document.querySelectorAll('#modalChudiPanel input[data-garment="chudi"]').forEach(inp => inp.value = '');
  });

  // Mobile number lookup in Record modal
  modalMobile?.addEventListener('input', () => {
    clearTimeout(lookupTimer);
    const mobile = modalMobile.value.trim();
    if (!/^\d+$/.test(mobile)) {
      modalMobile.value = mobile.replace(/\D/g, '');
    }

    if (modalMobile.value.length === 10) {
      if (/^[6-9]\d{9}$/.test(modalMobile.value)) {
        lookupTimer = setTimeout(() => performCustomerLookup(modalMobile.value), 250);
      } else {
        if (lookupStatus) lookupStatus.innerHTML = '<span style="color:#EF4444;">⚠️ Please enter a valid Indian mobile number (starts with 6-9)</span>';
      }
    } else {
      if (lookupStatus) lookupStatus.innerHTML = '';
    }
  });

  async function performCustomerLookup(mobile) {
    if (!lookupStatus) return;
    lookupStatus.innerHTML = '<span style="color:#818CF8;">Checking customer directory…</span>';

    try {
      const res = await Api.get(`/api/customers/${mobile}`);
      const cust = res?.data ?? res;
      if (cust && cust.customerName) {
        if (!modalName.value.trim() || modalName.value === 'Customer') {
          modalName.value = cust.customerName;
        }
        lookupStatus.innerHTML = `<span style="color:#10B981;">✅ Registered: <strong>${cust.customerName}</strong></span>`;
      } else {
        lookupStatus.innerHTML = '<span style="color:#F59E0B;">✨ New customer — will be saved to directory</span>';
      }
    } catch (_) {
      lookupStatus.innerHTML = '<span style="color:#F59E0B;">✨ New customer — will be saved to directory</span>';
    }

    // Check if specs already exist for this mobile & garment
    checkExistingCustomerSpecs(mobile, modalGarmentType.value);
  }

  async function checkExistingCustomerSpecs(mobile, garmentType) {
    try {
      const res = await Api.get(`/api/customer-measurements/customer/${mobile}/garment/${garmentType}`);
      const record = res?.data ?? res;
      if (record && record.measurements && Object.keys(record.measurements).length > 0) {
        populateModalMeasurements(record.garmentType, record.measurements, record.notes);
        Toast.info(`Loaded existing saved ${record.garmentType} specs for editing`);
      }
    } catch (_) {}
  }

  function populateModalMeasurements(garmentType, measurements = {}, notes = '') {
    if (modalGarmentType) modalGarmentType.value = garmentType;
    if (modalNotes) modalNotes.value = (notes && !notes.includes('Order #') && !notes.includes('Auto-synced')) ? notes : '';
    updateModalGarmentPanels();

    const selector = garmentType === 'BLOUSE'
      ? '#modalBlousePanel input[data-garment="blouse"]'
      : '#modalChudiPanel input[data-garment="chudi"]';

    document.querySelectorAll(selector).forEach(input => {
      const key = input.getAttribute('data-key');
      input.value = measurements[key] || '';
    });
  }

  function collectModalMeasurements(garmentType) {
    const measurements = {};
    const selector = garmentType === 'BLOUSE'
      ? '#modalBlousePanel input[data-garment="blouse"]'
      : '#modalChudiPanel input[data-garment="chudi"]';

    document.querySelectorAll(selector).forEach(input => {
      const val = input.value.trim();
      const key = input.getAttribute('data-key');
      if (val && key) {
        measurements[key] = val;
      }
    });
    return measurements;
  }

  // Open Record Modal (New)
  newBtn?.addEventListener('click', () => {
    recordModalTitle.textContent = 'Record Customer Measurements';
    modalMobile.value = '';
    modalMobile.readOnly = false;
    modalName.value = '';
    modalGarmentType.value = 'BLOUSE';
    modalNotes.value = '';
    if (lookupStatus) lookupStatus.innerHTML = '';
    document.querySelectorAll('#recordMeasurementModalBackdrop input[data-key]').forEach(inp => inp.value = '');
    updateModalGarmentPanels();
    recordModalBackdrop.classList.remove('hidden');
    modalMobile.focus();
  });

  // Open Edit Modal for a specific saved profile
  window.editCustomerMeasurement = function(recordId) {
    const r = allRecords.find(item => String(item.id) === String(recordId));
    if (!r) return;

    recordModalTitle.textContent = `Edit Measurements — ${r.customerName}`;
    modalMobile.value = r.customerMobile;
    modalMobile.readOnly = false;
    modalName.value = r.customerName;
    if (lookupStatus) lookupStatus.innerHTML = `<span style="color:#10B981;">📱 Mobile: ${r.customerMobile}</span>`;

    populateModalMeasurements(r.garmentType, r.measurements, r.notes);
    recordModalBackdrop.classList.remove('hidden');
  };

  // Save Customer Measurement Action
  saveMeasurementBtn?.addEventListener('click', async () => {
    const mobile = modalMobile.value.trim();
    const name   = modalName.value.trim();
    const garment= modalGarmentType.value;
    const rawNotes = modalNotes.value.trim();
    const notes = (rawNotes && !rawNotes.includes('Order #') && !rawNotes.includes('Auto-synced')) ? rawNotes : null;

    if (!mobile || !/^[6-9]\d{9}$/.test(mobile)) {
      Toast.error('Please enter a valid 10-digit Indian mobile number');
      modalMobile.focus();
      return;
    }

    if (!name || name.length < 2) {
      Toast.error('Please enter customer name');
      modalName.focus();
      return;
    }

    const measurements = collectModalMeasurements(garment);
    if (Object.keys(measurements).length === 0) {
      Toast.error(`Please enter at least 1 measurement for ${garment}`);
      return;
    }

    const payload = {
      customerMobile: mobile,
      customerName: name,
      garmentType: garment,
      measurements: measurements,
      notes: notes
    };

    Utils.setLoading(saveMeasurementBtn, true, 'Saving Specs…');

    try {
      await Api.post(API.MEASUREMENTS, payload);
      Toast.success(`Customer measurements saved for ${name} (${mobile})! 🎉`);
      closeRecordModal();
      await loadRecords();
    } catch (err) {
      Toast.error(err.message || 'Failed to save customer measurements');
    } finally {
      Utils.setLoading(saveMeasurementBtn, false);
    }
  });

  // ── Load Measurement Records Directly Linked by Mobile & Name ──────────
  async function loadRecords() {
    gridEl.innerHTML = `
      <div style="grid-column:1/-1;padding:48px;text-align:center;color:var(--text-muted);">
        <div class="spinner" style="margin:0 auto 12px;width:26px;height:26px;border:2px solid rgba(255,255,255,0.1);border-top-color:#818CF8;border-radius:50%;animation:spin 0.7s linear infinite;"></div>
        Loading customer tailoring measurement profiles…
      </div>
    `;

    try {
      const data = await Api.get(`${API.MEASUREMENTS}?size=250&sortBy=updatedAt&sortDir=desc`);
      const records = data?.content ?? data ?? [];

      allRecords = records.filter(r => r && r.measurements && Object.keys(r.measurements).length > 0);

      updateStats();
      renderCards();
    } catch (err) {
      gridEl.innerHTML = `
        <div style="grid-column:1/-1;padding:48px;text-align:center;color:var(--text-muted);">
          <div style="font-size:24px;margin-bottom:8px;">⚠️</div>
          <div style="font-weight:600;color:var(--text-primary);margin-bottom:4px;">Failed to load measurement records</div>
          <div style="font-size:12px;">${err.message || 'Please verify connection and retry'}</div>
        </div>
      `;
    }
  }

  // ── Stats update ──────────────────────────────────────────────────────
  function updateStats() {
    const total  = allRecords.length;
    const blouse = allRecords.filter(r => r.garmentType === 'BLOUSE').length;
    const chudi  = allRecords.filter(r => r.garmentType === 'CHUDI').length;

    document.getElementById('statTotalProfiles').textContent  = total;
    document.getElementById('statBlouseProfiles').textContent = blouse;
    document.getElementById('statChudiProfiles').textContent  = chudi;

    document.getElementById('countAll').textContent    = total;
    document.getElementById('countBlouse').textContent = blouse;
    document.getElementById('countChudi').textContent  = chudi;
  }

  // ── Render Cards (Customer Profile Based, No Order ID, No Lining) ─────
  function renderCards() {
    const query = (searchInput?.value || '').toLowerCase().trim();

    let filtered = allRecords.filter(r => {
      if (currentFilter === 'BLOUSE' && r.garmentType !== 'BLOUSE') return false;
      if (currentFilter === 'CHUDI'  && r.garmentType !== 'CHUDI')  return false;

      if (!query) return true;
      return (r.customerName   || '').toLowerCase().includes(query) ||
             (r.customerMobile || '').includes(query) ||
             (r.notes          || '').toLowerCase().includes(query);
    });

    if (filtered.length === 0) {
      gridEl.innerHTML = `
        <div style="grid-column:1/-1;padding:48px;text-align:center;color:var(--text-muted);background:var(--bg-surface-1);border-radius:12px;border:1px solid var(--border-default);">
          <div style="font-size:28px;margin-bottom:8px;">📐</div>
          <div style="font-weight:600;color:var(--text-primary);margin-bottom:4px;">No measurement records found</div>
          <div style="font-size:12px;">${query ? `No customer match for "${query}"` : 'Record customer measurements using the button above.'}</div>
        </div>
      `;
      return;
    }

    gridEl.innerHTML = filtered.map(r => {
      const isBlouse = r.garmentType === 'BLOUSE';
      const badgeCls = isBlouse ? 'msr-badge-blouse' : 'msr-badge-chudi';
      const badgeTxt = isBlouse ? '👗 Blouse (19 Specs)' : '👘 Chudi (18 Specs)';
      const measurements = r.measurements || {};
      const entries = Object.entries(measurements);
      
      const cleanNote = (r.notes && !r.notes.includes('Order #') && !r.notes.includes('Auto-synced')) ? r.notes : null;

      const specGridHtml = entries.length > 0 ? `
        <div class="msr-specs-grid">
          ${entries.slice(0, 12).map(([k, v]) => `
            <div class="msr-spec-cell">
              <span class="msr-spec-k">${k}</span>
              <span class="msr-spec-v" title="${v}">${v}</span>
            </div>
          `).join('')}
          ${entries.length > 12 ? `
            <div class="msr-spec-cell" style="background:rgba(129,140,248,0.1);border-color:rgba(129,140,248,0.2);align-items:center;justify-content:center;">
              <span style="font-size:10px;font-weight:700;color:#818CF8;">+${entries.length - 12} MORE</span>
            </div>
          ` : ''}
        </div>
      ` : `<div style="font-size:12px;color:var(--text-muted);font-style:italic;">No specs recorded</div>`;

      return `
        <div class="msr-card">
          <div class="msr-card-head">
            <div>
              <div class="msr-card-customer" style="font-size:15px;font-weight:700;color:var(--text-primary);">${r.customerName || 'Customer'}</div>
              <div class="msr-card-meta" style="margin-top:4px;">
                <span style="font-family:monospace;font-weight:600;color:#818CF8;">📱 ${r.customerMobile}</span>
                ${cleanNote ? `<span>•</span><span style="font-size:11px;color:var(--text-muted);">${cleanNote}</span>` : ''}
              </div>
            </div>
            <div style="display:flex;flex-direction:column;align-items:flex-end;gap:4px;">
              <span class="msr-badge ${badgeCls}">${badgeTxt}</span>
            </div>
          </div>

          ${specGridHtml}

          <div class="msr-card-footer">
            <span style="font-size:11px;color:var(--text-muted);">
              📅 Updated: ${Utils.formatDate(r.updatedAt || r.createdAt)}
            </span>
            <div style="display:flex;gap:6px;">
              <button class="msr-btn" onclick="window.copySpecs('${r.id}')" title="Copy specs to clipboard">
                📋 Copy
              </button>
              <button class="msr-btn" onclick="window.editCustomerMeasurement('${r.id}')" title="Edit customer measurements">
                ✏️ Edit
              </button>
              <button class="msr-btn" style="color:#818CF8;border-color:rgba(129,140,248,0.3);" onclick="window.openSpecSheet('${r.id}')">
                🔍 Spec Sheet
              </button>
            </div>
          </div>
        </div>
      `;
    }).join('');
  }

  // ── Open Spec Sheet Modal (Linked to Customer Mobile) ───────────────────
  window.openSpecSheet = function(recordId) {
    const record = allRecords.find(r => String(r.id) === String(recordId));
    if (!record) return;

    const isBlouse = record.garmentType === 'BLOUSE';
    const badgeCls = isBlouse ? 'msr-badge-blouse' : 'msr-badge-chudi';
    const badgeTxt = isBlouse ? '👗 Blouse Tailoring Specification' : '👘 Chudi Tailoring Specification';
    const entries  = Object.entries(record.measurements || {});
    const cleanNote = (record.notes && !record.notes.includes('Order #') && !record.notes.includes('Auto-synced')) ? record.notes : null;

    document.getElementById('sheetModalTitle').textContent = `${record.customerName} — Specification Profile`;
    document.getElementById('sheetModalSub').textContent   = `Customer Mobile: ${record.customerMobile} • Updated ${Utils.formatDate(record.updatedAt || record.createdAt)}`;

    sheetModalBody.innerHTML = `
      <div style="display:flex;flex-direction:column;gap:16px;">
        <!-- Header Info -->
        <div style="display:flex;align-items:center;justify-content:space-between;background:rgba(255,255,255,0.03);padding:14px 18px;border-radius:10px;border:1px solid rgba(255,255,255,0.08);">
          <div>
            <div style="font-size:11px;color:var(--text-muted);text-transform:uppercase;letter-spacing:0.5px;">Customer Profile</div>
            <div style="font-size:18px;font-weight:700;color:var(--text-primary);margin-top:2px;">
              ${record.customerName} 
              <span style="font-size:13px;color:#818CF8;font-family:monospace;margin-left:8px;">(📱 ${record.customerMobile})</span>
            </div>
            ${cleanNote ? `<div style="font-size:12px;color:var(--text-muted);margin-top:4px;">📝 ${cleanNote}</div>` : ''}
          </div>
          <div style="display:flex;align-items:center;gap:8px;">
            <span class="msr-badge ${badgeCls}" style="font-size:12px;padding:6px 12px;">${badgeTxt}</span>
          </div>
        </div>

        <!-- Full Measurements Table -->
        <div style="background:var(--bg-surface-2);border-radius:10px;border:1px solid var(--border-default);padding:16px;">
          <div style="font-size:11px;font-weight:700;color:var(--text-secondary);text-transform:uppercase;letter-spacing:0.5px;margin-bottom:12px;">
            Recorded Dimensions (${entries.length} Fields)
          </div>
          <div style="display:grid;grid-template-columns:repeat(auto-fill, minmax(130px, 1fr));gap:10px;">
            ${entries.map(([k, v]) => `
              <div style="background:var(--bg-surface-1);border:1px solid rgba(255,255,255,0.05);border-radius:8px;padding:8px 12px;display:flex;flex-direction:column;">
                <span style="font-size:10px;font-weight:700;color:var(--text-muted);text-transform:uppercase;">${k}</span>
                <span style="font-size:15px;font-weight:700;color:var(--status-completed);margin-top:3px;">${v}</span>
              </div>
            `).join('')}
          </div>
        </div>

        <div style="display:flex;align-items:center;justify-content:space-between;font-size:12px;color:var(--text-muted);padding:8px 4px;">
          <div>Customer Profile Key: <strong style="color:var(--text-primary);">${record.customerMobile}</strong></div>
          <div>Last Sizing Update: <strong style="color:var(--text-primary);">${Utils.formatDate(record.updatedAt || record.createdAt)}</strong></div>
        </div>
      </div>
    `;

    sheetModalBackdrop.classList.remove('hidden');
  };

  // ── Copy Specs to Clipboard ───────────────────────────────────────────
  window.copySpecs = function(recordId) {
    const record = allRecords.find(r => String(r.id) === String(recordId));
    if (!record) return;

    const cleanNote = (record.notes && !record.notes.includes('Order #') && !record.notes.includes('Auto-synced')) ? record.notes : null;

    const lines = [
      `=== RITHAM ERP TAILORING SPECIFICATION ===`,
      `Customer Name: ${record.customerName}`,
      `Mobile Number: ${record.customerMobile}`,
      `Garment: ${record.garmentType}`,
      `Updated: ${Utils.formatDate(record.updatedAt || record.createdAt)}`,
      ...(cleanNote ? [`Notes: ${cleanNote}`] : []),
      `------------------------------------------`,
      ...Object.entries(record.measurements || {}).map(([k, v]) => `${k.padEnd(8)} : ${v}`),
      `==========================================`
    ];

    navigator.clipboard.writeText(lines.join('\n')).then(() => {
      Toast.success('Customer measurements copied to clipboard! 📋');
    }).catch(() => {
      Toast.info('Unable to copy automatically');
    });
  };

  // ── Tabs ──────────────────────────────────────────────────────────────
  document.querySelectorAll('.msr-tab').forEach(tab => {
    tab.addEventListener('click', () => {
      document.querySelectorAll('.msr-tab').forEach(t => t.classList.remove('active'));
      tab.classList.add('active');
      currentFilter = tab.getAttribute('data-filter');
      renderCards();
    });
  });

  // ── Search input ──────────────────────────────────────────────────────
  searchInput?.addEventListener('input', () => {
    clearTimeout(searchTimer);
    searchTimer = setTimeout(renderCards, 250);
  });

  // ── Refresh ───────────────────────────────────────────────────────────
  refreshBtn?.addEventListener('click', async () => {
    Toast.info('Refreshing measurement records...');
    await loadRecords();
    Toast.success('Records refreshed');
  });

  // Auto-refresh on focus
  window.addEventListener('focus', loadRecords);

  await loadRecords();
});
