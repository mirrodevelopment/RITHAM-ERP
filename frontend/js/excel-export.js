/**
 * Ritham ERP — Universal Excel (.xlsx) Export Module
 *
 * Lightweight, zero-dependency client-side exporter leveraging SheetJS.
 * Features interactive Column Selection Modal on every export, auto-column
 * sizing, persistent column preferences, date-stamped filenames, and toast alerts.
 */

'use strict';

const ExcelExport = (() => {

  const CDN_URL = 'https://cdn.jsdelivr.net/npm/xlsx@0.18.5/dist/xlsx.full.min.js';
  let loadPromise = null;

  /**
   * Ensure SheetJS library is loaded in the browser.
   */
  async function ensureLibrary() {
    if (typeof XLSX !== 'undefined') {
      return window.XLSX;
    }

    if (!loadPromise) {
      loadPromise = new Promise((resolve, reject) => {
        const existing = document.querySelector(`script[src*="xlsx"]`);
        if (existing) {
          existing.addEventListener('load', () => resolve(window.XLSX));
          existing.addEventListener('error', () => reject(new Error('Failed to load SheetJS library')));
          return;
        }

        const script = document.createElement('script');
        script.src = CDN_URL;
        script.async = true;
        script.onload = () => resolve(window.XLSX);
        script.onerror = () => reject(new Error('Unable to connect to Excel export engine'));
        document.head.appendChild(script);
      });
    }

    return loadPromise;
  }

  /**
   * Format a date string or timestamp into readable local format.
   */
  function formatDate(val) {
    if (!val) return '—';
    try {
      const d = new Date(val);
      if (isNaN(d.getTime())) return String(val);
      return d.toLocaleDateString('en-IN', {
        day: '2-digit',
        month: 'short',
        year: 'numeric'
      });
    } catch (_) {
      return String(val);
    }
  }

  /**
   * Auto-fit column widths for SheetJS worksheet.
   */
  function autoFitColumns(data, headerKeys) {
    return headerKeys.map(key => {
      let maxLen = String(key).length;
      for (const row of data) {
        const val = row[key];
        if (val !== null && val !== undefined) {
          const strLen = String(val).length;
          if (strLen > maxLen) maxLen = strLen;
        }
      }
      return { wch: Math.min(Math.max(maxLen + 3, 10), 50) };
    });
  }

  /**
   * Display interactive Column Selection Modal before downloading spreadsheet.
   *
   * @param {Object} options
   * @param {Array<string>} options.columns - All available column names
   * @param {number} options.totalRecords - Number of rows to export
   * @param {string} options.sheetName - Sheet name (for title and storage key)
   * @param {string} options.fileName - Destination filename
   * @returns {Promise<Array<string>|null>} Selected columns or null if canceled
   */
  function showColumnSelectModal({ columns, totalRecords, sheetName, fileName }) {
    return new Promise(resolve => {
      // Clean up any lingering backdrop
      const existing = document.getElementById('excelColumnSelectBackdrop');
      if (existing) existing.remove();

      // Retrieve previously saved column choices if available
      const storageKey = `ritham_excel_cols_${(sheetName || 'default').toLowerCase().replace(/\s+/g, '_')}`;
      let savedCols = null;
      try {
        const raw = localStorage.getItem(storageKey);
        if (raw) savedCols = JSON.parse(raw);
      } catch (_) {}

      // Initial selection: prefer saved columns if present, else all checked
      const selectedSet = new Set(
        Array.isArray(savedCols) && savedCols.length > 0
          ? savedCols.filter(c => columns.includes(c))
          : columns
      );
      if (selectedSet.size === 0) {
        columns.forEach(c => selectedSet.add(c));
      }

      const backdrop = document.createElement('div');
      backdrop.id = 'excelColumnSelectBackdrop';
      backdrop.className = 'modal-backdrop';
      backdrop.style.cssText = 'position:fixed; inset:0; background:rgba(0,0,0,0.72); backdrop-filter:blur(5px); z-index:99999; display:flex; align-items:center; justify-content:center; padding:16px; animation:fadeIn 0.15s ease;';

      backdrop.innerHTML = `
        <div class="modal modal-lg" style="max-width:680px; width:100%; max-height:calc(100vh - 40px); background:var(--bg-surface, #111827); border:1px solid rgba(16, 185, 129, 0.35); border-radius:14px; box-shadow:0 25px 50px -12px rgba(0,0,0,0.85), 0 0 32px rgba(16, 185, 129, 0.18); display:flex; flex-direction:column; overflow:hidden;">
          
          <!-- Modal Header -->
          <div class="modal-header" style="display:flex; align-items:center; justify-content:space-between; padding:18px 22px; border-bottom:1px solid var(--border-default, rgba(255,255,255,0.08));">
            <div style="display:flex; align-items:center; gap:12px;">
              <div style="width:38px; height:38px; border-radius:10px; background:rgba(16, 185, 129, 0.12); border:1px solid rgba(16, 185, 129, 0.3); display:flex; align-items:center; justify-content:center; color:#10B981; flex-shrink:0;">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
                  <polyline points="14 2 14 8 20 8"></polyline>
                  <line x1="8" y1="13" x2="16" y2="13"></line>
                  <line x1="8" y1="17" x2="16" y2="17"></line>
                  <polyline points="10 9 9 9 8 9"></polyline>
                </svg>
              </div>
              <div>
                <h3 style="margin:0; font-size:16px; font-weight:700; color:var(--text-primary, #f9fafb); line-height:1.3;">
                  Export to Excel — Select Columns
                </h3>
                <p style="margin:2px 0 0 0; font-size:12px; color:var(--text-muted, #9ca3af);">
                  Choose the columns to include in your spreadsheet • <strong style="color:var(--text-primary, #fff);">${totalRecords} records</strong> ready
                </p>
              </div>
            </div>
            <button type="button" class="btn btn-ghost btn-icon" id="excelColCloseXBtn" style="color:var(--text-muted); padding:6px; border-radius:6px; cursor:pointer;" title="Close">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <line x1="18" y1="6" x2="6" y2="18"></line>
                <line x1="6" y1="6" x2="18" y2="18"></line>
              </svg>
            </button>
          </div>

          <!-- Toolbar: Search & Select All/None -->
          <div style="padding:12px 22px; background:var(--bg-surface-2, rgba(255,255,255,0.02)); border-bottom:1px solid var(--border-default, rgba(255,255,255,0.08)); display:flex; align-items:center; justify-content:space-between; gap:12px; flex-wrap:wrap;">
            <div style="flex:1; min-width:180px;">
              <input type="text" id="excelColSearchInput" placeholder="Filter columns..." style="width:100%; max-width:260px; padding:7px 12px; font-size:12px; border-radius:6px; background:var(--bg-surface-1, #1f2937); border:1px solid var(--border-default, rgba(255,255,255,0.12)); color:var(--text-primary, #fff); outline:none;">
            </div>
            <div style="display:flex; align-items:center; gap:8px;">
              <button type="button" class="btn btn-secondary btn-sm" id="excelColSelectAllBtn" style="font-size:11px; padding:5px 10px;">Select All</button>
              <button type="button" class="btn btn-secondary btn-sm" id="excelColDeselectAllBtn" style="font-size:11px; padding:5px 10px;">Deselect All</button>
              <span id="excelColCountBadge" class="badge" style="font-size:11px; padding:5px 10px; background:rgba(16, 185, 129, 0.15); color:#10B981; border:1px solid rgba(16, 185, 129, 0.3); font-weight:600; border-radius:6px;">
                ${selectedSet.size} / ${columns.length} Selected
              </span>
            </div>
          </div>

          <!-- Modal Body: Columns Grid -->
          <div class="modal-body" style="padding:18px 22px; max-height:360px; overflow-y:auto; flex:1;">
            <div id="excelColsGrid" style="display:grid; grid-template-columns:repeat(auto-fill, minmax(180px, 1fr)); gap:10px;">
              ${columns.map((col) => {
                const isChecked = selectedSet.has(col);
                return `
                  <label class="excel-col-item ${isChecked ? 'selected' : ''}" data-col="${col}">
                    <input type="checkbox" value="${col}" ${isChecked ? 'checked' : ''}>
                    <span title="${col}">${col}</span>
                  </label>
                `;
              }).join('')}
            </div>
          </div>

          <!-- Modal Footer -->
          <div class="modal-footer" style="padding:14px 22px; display:flex; align-items:center; justify-content:space-between; border-top:1px solid var(--border-default, rgba(255,255,255,0.08)); background:var(--bg-surface-2, rgba(255,255,255,0.02));">
            <div style="font-size:12px; color:var(--text-muted, #9ca3af);" id="excelColFooterInfo">
              💡 Column selections are remembered for this desk
            </div>
            <div style="display:flex; align-items:center; gap:10px;">
              <button type="button" class="btn btn-secondary" id="excelColCancelBtn" style="padding:8px 16px;">Cancel</button>
              <button type="button" class="btn btn-excel" id="excelColDownloadBtn" style="padding:8px 18px; gap:8px; font-weight:600;">
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
                  <polyline points="14 2 14 8 20 8"></polyline>
                  <line x1="8" y1="13" x2="16" y2="13"></line>
                  <line x1="8" y1="17" x2="16" y2="17"></line>
                </svg>
                <span id="excelColDownloadBtnText">Download (${selectedSet.size} Columns)</span>
              </button>
            </div>
          </div>

        </div>
      `;

      document.body.appendChild(backdrop);

      const closeBtn = backdrop.querySelector('#excelColCloseXBtn');
      const cancelBtn = backdrop.querySelector('#excelColCancelBtn');
      const downloadBtn = backdrop.querySelector('#excelColDownloadBtn');
      const downloadBtnText = backdrop.querySelector('#excelColDownloadBtnText');
      const searchInput = backdrop.querySelector('#excelColSearchInput');
      const selectAllBtn = backdrop.querySelector('#excelColSelectAllBtn');
      const deselectAllBtn = backdrop.querySelector('#excelColDeselectAllBtn');
      const countBadge = backdrop.querySelector('#excelColCountBadge');
      const grid = backdrop.querySelector('#excelColsGrid');

      function updateUI() {
        const checkedCount = selectedSet.size;
        countBadge.textContent = `${checkedCount} / ${columns.length} Selected`;
        downloadBtnText.textContent = `Download (${checkedCount} Columns)`;
        downloadBtn.disabled = checkedCount === 0;

        grid.querySelectorAll('.excel-col-item').forEach(item => {
          const colName = item.getAttribute('data-col');
          const isSel = selectedSet.has(colName);
          const chk = item.querySelector('input[type="checkbox"]');
          if (chk) chk.checked = isSel;
          if (isSel) {
            item.classList.add('selected');
          } else {
            item.classList.remove('selected');
          }
        });
      }

      function closeModal(result) {
        document.removeEventListener('keydown', handleKey);
        backdrop.style.opacity = '0';
        backdrop.style.transition = 'opacity 0.15s ease';
        setTimeout(() => {
          backdrop.remove();
          resolve(result);
        }, 150);
      }

      function handleKey(e) {
        if (e.key === 'Escape') closeModal(null);
      }
      document.addEventListener('keydown', handleKey);

      grid.addEventListener('change', e => {
        const chk = e.target;
        if (chk && chk.type === 'checkbox') {
          const col = chk.value;
          if (chk.checked) {
            selectedSet.add(col);
          } else {
            selectedSet.delete(col);
          }
          updateUI();
        }
      });

      searchInput?.addEventListener('input', () => {
        const q = searchInput.value.trim().toLowerCase();
        grid.querySelectorAll('.excel-col-item').forEach(item => {
          const colName = (item.getAttribute('data-col') || '').toLowerCase();
          item.style.display = colName.includes(q) ? 'flex' : 'none';
        });
      });

      selectAllBtn?.addEventListener('click', () => {
        grid.querySelectorAll('.excel-col-item').forEach(item => {
          if (item.style.display !== 'none') {
            const col = item.getAttribute('data-col');
            selectedSet.add(col);
          }
        });
        updateUI();
      });

      deselectAllBtn?.addEventListener('click', () => {
        grid.querySelectorAll('.excel-col-item').forEach(item => {
          if (item.style.display !== 'none') {
            const col = item.getAttribute('data-col');
            selectedSet.delete(col);
          }
        });
        updateUI();
      });

      closeBtn?.addEventListener('click', () => closeModal(null));
      cancelBtn?.addEventListener('click', () => closeModal(null));
      backdrop.addEventListener('click', e => {
        if (e.target === backdrop) closeModal(null);
      });

      downloadBtn?.addEventListener('click', () => {
        if (selectedSet.size === 0) return;
        const chosen = columns.filter(c => selectedSet.has(c));
        try {
          localStorage.setItem(storageKey, JSON.stringify(chosen));
        } catch (_) {}
        closeModal(chosen);
      });

      if (searchInput) searchInput.focus();
      updateUI();
    });
  }

  /**
   * Export an array of data objects to an Excel (.xlsx) file.
   * Prompts user with interactive column selector modal.
   *
   * @param {Object|Array} arg1 - Array of records OR options object { data, fileName, sheetName, columns, skipColumnSelect }
   * @param {Object} [arg2] - Options object when arg1 is array
   */
  async function exportData(arg1, arg2) {
    let rawData = [];
    let opts = {};

    if (Array.isArray(arg1)) {
      rawData = arg1;
      opts = arg2 || {};
    } else if (arg1 && typeof arg1 === 'object') {
      rawData = arg1.data || [];
      opts = arg1;
    }

    const fileName = opts.fileName || opts.filename || 'export';
    const sheetName = opts.sheetName || opts.sheetname || 'Records';
    const columnsConfig = opts.columns || null;
    const skipColumnSelect = opts.skipColumnSelect === true;

    if (!Array.isArray(rawData) || rawData.length === 0) {
      if (typeof Toast !== 'undefined') {
        Toast.warning('No records available to export.');
      } else {
        alert('No records available to export.');
      }
      return;
    }

    try {
      // 1. Transform rows based on column configuration if provided
      let allRows = [];
      if (Array.isArray(columnsConfig) && columnsConfig.length > 0) {
        allRows = rawData.map((item, idx) => {
          const row = {};
          columnsConfig.forEach(col => {
            const header = col.header || col.key;
            if (col.key === 'sno' || col.key === 'index') {
              row[header] = idx + 1;
            } else if (typeof col.transform === 'function') {
              row[header] = col.transform(item[col.key], item, idx);
            } else {
              row[header] = item[col.key] !== undefined && item[col.key] !== null ? item[col.key] : '—';
            }
          });
          return row;
        });
      } else {
        allRows = rawData;
      }

      if (allRows.length === 0) {
        if (typeof Toast !== 'undefined') Toast.warning('No data rows available to export.');
        return;
      }

      // 2. Discover available column headers
      const availableColumns = Object.keys(allRows[0] || {});
      if (availableColumns.length === 0) {
        if (typeof Toast !== 'undefined') Toast.warning('No data columns found to export.');
        return;
      }

      // 3. Prompt user for column selection unless explicitly skipped or only 1 column
      let selectedColumns = availableColumns;
      if (!skipColumnSelect && availableColumns.length > 1) {
        selectedColumns = await showColumnSelectModal({
          columns: availableColumns,
          totalRecords: allRows.length,
          sheetName,
          fileName
        });

        // User dismissed the modal or canceled
        if (!selectedColumns || selectedColumns.length === 0) {
          return;
        }
      }

      // 4. Ensure XLSX library is loaded
      const XLSX = await ensureLibrary();

      // 5. Filter rows to contain only selected columns (preserving user choice order)
      const finalRows = allRows.map((row, idx) => {
        const filtered = {};
        selectedColumns.forEach(k => {
          if ((k === 'S.No' || k === 'S No' || k === '#' || k === 'Sequence') && typeof row[k] === 'number') {
            filtered[k] = idx + 1;
          } else {
            filtered[k] = row[k] !== undefined && row[k] !== null ? row[k] : '';
          }
        });
        return filtered;
      });

      // 6. Build worksheet and auto-fit column widths
      const ws = XLSX.utils.json_to_sheet(finalRows);
      ws['!cols'] = autoFitColumns(finalRows, selectedColumns);

      const wb = XLSX.utils.book_new();
      XLSX.utils.book_append_sheet(wb, ws, sheetName.slice(0, 31));

      const dateStr = new Date().toISOString().slice(0, 10);
      const cleanFileName = fileName.endsWith('.xlsx') ? fileName : `${fileName}-${dateStr}.xlsx`;

      XLSX.writeFile(wb, cleanFileName);

      if (typeof Toast !== 'undefined') {
        Toast.success(`Exported ${finalRows.length} record${finalRows.length === 1 ? '' : 's'} (${selectedColumns.length} columns) to ${cleanFileName} 📊`);
      }
    } catch (err) {
      console.error('Excel export failed:', err);
      if (typeof Toast !== 'undefined') {
        Toast.error(`Export failed: ${err.message || 'Error creating spreadsheet'}`);
      }
    }
  }

  /**
   * Export an HTML Table element directly to an Excel file with optional column selection.
   *
   * @param {Object} options
   * @param {HTMLTableElement|string} options.table - Table DOM element or CSS selector
   * @param {string} options.fileName - File name without or with extension
   * @param {string} [options.sheetName='Table'] - Worksheet title
   * @param {Array<string>} [options.excludeSelectors] - Elements to strip (e.g. action buttons)
   * @param {boolean} [options.skipColumnSelect=false] - Whether to bypass column selector
   */
  async function exportTable({ table, fileName = 'table-export', sheetName = 'Table', excludeSelectors = ['.btn', '.action-col', '.actions', 'button'], skipColumnSelect = false }) {
    const tableEl = typeof table === 'string' ? document.querySelector(table) : table;
    if (!tableEl) {
      if (typeof Toast !== 'undefined') Toast.error('Table not found for export');
      return;
    }

    try {
      // Extract headers from the table element
      const headerCells = Array.from(tableEl.querySelectorAll('thead th, tr:first-child th'))
        .filter(th => !excludeSelectors.some(sel => th.matches(sel) || th.closest(sel)));
      const headers = headerCells.map(th => th.textContent.trim()).filter(Boolean);

      if (!skipColumnSelect && headers.length > 1) {
        const rows = [];
        const trs = Array.from(tableEl.querySelectorAll('tbody tr'));
        trs.forEach((tr, rIdx) => {
          const cells = Array.from(tr.querySelectorAll('td'))
            .filter(td => !excludeSelectors.some(sel => td.matches(sel) || td.closest(sel)));
          if (cells.length > 0) {
            const rowObj = {};
            headers.forEach((h, cIdx) => {
              rowObj[h] = cells[cIdx] ? cells[cIdx].textContent.trim() : '';
            });
            rows.push(rowObj);
          }
        });

        if (rows.length > 0) {
          return exportData({
            data: rows,
            fileName,
            sheetName
          });
        }
      }

      // Fallback: direct table DOM export
      const XLSX = await ensureLibrary();
      const clone = tableEl.cloneNode(true);
      if (excludeSelectors && excludeSelectors.length > 0) {
        excludeSelectors.forEach(sel => {
          clone.querySelectorAll(sel).forEach(el => el.remove());
        });
      }

      const ws = XLSX.utils.table_to_sheet(clone);
      const wb = XLSX.utils.book_new();
      XLSX.utils.book_append_sheet(wb, ws, sheetName.slice(0, 31));

      const dateStr = new Date().toISOString().slice(0, 10);
      const cleanFileName = fileName.endsWith('.xlsx') ? fileName : `${fileName}-${dateStr}.xlsx`;

      XLSX.writeFile(wb, cleanFileName);

      if (typeof Toast !== 'undefined') {
        Toast.success(`Table exported to ${cleanFileName} 📊`);
      }
    } catch (err) {
      console.error('Table export failed:', err);
      if (typeof Toast !== 'undefined') Toast.error('Failed to export table to Excel');
    }
  }

  /**
   * Return an SVG string for the Excel export button icon.
   */
  function getExcelIconSvg(size = 14) {
    return `<svg width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
      <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
      <polyline points="14 2 14 8 20 8"></polyline>
      <line x1="8" y1="13" x2="16" y2="13"></line>
      <line x1="8" y1="17" x2="16" y2="17"></line>
      <polyline points="10 9 9 9 8 9"></polyline>
    </svg>`;
  }

  return {
    ensureLibrary,
    exportData,
    exportTable,
    formatDate,
    getExcelIconSvg,
    showColumnSelectModal
  };

})();

if (typeof window !== 'undefined') {
  window.ExcelExport = ExcelExport;
}
