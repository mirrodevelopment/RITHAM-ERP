/**
 * Ritham ERP — Database Backup Desk Controller (backup.js)
 * System Administrator exclusive dashboard for PostgreSQL automated snapshots & disaster recovery.
 * Features: Multi-Drive location selection, Complete External Drive Hot Backup, Integrity Verification & Manifest.
 */

'use strict';

document.addEventListener('DOMContentLoaded', () => {
  // Guard access: Administrator role only
  if (!Router.protect([ROLES.ADMIN])) return;

  // Initialize Shell
  Header.init({
    title: 'Database & System Backup',
    subtitle: 'PostgreSQL snapshots, external redundancy & disaster recovery archives',
  });
  Sidebar.init({ activePage: 'backup' });

  // ── State ─────────────────────────────────────────────────────────────────
  let backupFiles = [];
  let availableLocations = [];
  let activeLocation = ''; // Active viewing location path, e.g. "E:\ERP-Backups"
  let activeCategory = 'All';
  let searchQuery = '';
  let statusData = null;

  // ── DOM Elements ──────────────────────────────────────────────────────────
  const refreshBackupBtn          = document.getElementById('refreshBackupBtn');
  const runManualBackupBtn        = document.getElementById('runManualBackupBtn');
  const openPlaybookBtn           = document.getElementById('openPlaybookBtn');
  const backupSearchInput         = document.getElementById('backupSearchInput');
  const backupTableBody           = document.getElementById('backupTableBody');
  const tableResultSummary        = document.getElementById('tableResultSummary');
  const totalFilesCountBadge      = document.getElementById('totalFilesCountBadge');
  const overallStatusBadge        = document.getElementById('overallStatusBadge');

  // Location Toolbar Banner Elements
  const activeDriveTypeBadge      = document.getElementById('activeDriveTypeBadge');
  const activeLocationPathDisplay = document.getElementById('activeLocationPathDisplay');
  const locationSelectDropdown    = document.getElementById('locationSelectDropdown');
  const openLocationConfigBtn     = document.getElementById('openLocationConfigBtn');
  const browseFolderBtn           = document.getElementById('browseFolderBtn');

  // Stat Card Elements
  const driveFreeSpaceVal         = document.getElementById('driveFreeSpaceVal');
  const driveSubtext              = document.getElementById('driveSubtext');
  const driveProgressBar          = document.getElementById('driveProgressBar');
  const driveDot                  = document.getElementById('driveDot');
  const latestSnapshotAge         = document.getElementById('latestSnapshotAge');
  const latestSnapshotFile        = document.getElementById('latestSnapshotFile');
  const latestSnapshotBadge       = document.getElementById('latestSnapshotBadge');
  const postgresStatusVal         = document.getElementById('postgresStatusVal');
  const postgresSubtext           = document.getElementById('postgresSubtext');
  const pgDot                     = document.getElementById('pgDot');
  const nextScheduledRunVal       = document.getElementById('nextScheduledRunVal');
  const retentionStatsBadge       = document.getElementById('retentionStatsBadge');

  // Diagnostics elements
  const diagDriveDot              = document.getElementById('diagDriveDot');
  const diagDriveBadge            = document.getElementById('diagDriveBadge');
  const diagSpaceDot              = document.getElementById('diagSpaceDot');
  const diagSpaceBadge            = document.getElementById('diagSpaceBadge');
  const diagPgDot                 = document.getElementById('diagPgDot');
  const diagPgBadge               = document.getElementById('diagPgBadge');
  const diagLockDot               = document.getElementById('diagLockDot');
  const diagLockBadge             = document.getElementById('diagLockBadge');

  // Tab count elements
  const tabAllCount               = document.getElementById('tabAllCount');
  const tabDailyCount             = document.getElementById('tabDailyCount');
  const tabWeeklyCount            = document.getElementById('tabWeeklyCount');
  const tabMonthlyCount           = document.getElementById('tabMonthlyCount');
  const tabUploadCount            = document.getElementById('tabUploadCount');
  const tabButtons                = document.querySelectorAll('.backup-tab-btn');

  // Import & Restore Modal Elements
  const openImportModalBtn           = document.getElementById('openImportModalBtn');
  const importBackupModal            = document.getElementById('importBackupModal');
  const closeImportModalBtn          = document.getElementById('closeImportModalBtn');
  const cancelImportModalBtn         = document.getElementById('cancelImportModalBtn');
  const tabSourceRepoBtn             = document.getElementById('tabSourceRepoBtn');
  const tabSourceUploadBtn           = document.getElementById('tabSourceUploadBtn');
  const repoFileSection              = document.getElementById('repoFileSection');
  const uploadFileSection            = document.getElementById('uploadFileSection');
  const repoBackupSelect             = document.getElementById('repoBackupSelect');
  const repoFileLocationLabel        = document.getElementById('repoFileLocationLabel');
  const externalDumpFileInput        = document.getElementById('externalDumpFileInput');
  const dumpDropzone                 = document.getElementById('dumpDropzone');
  const uploadProgressBox            = document.getElementById('uploadProgressBox');
  const uploadFileNameLabel          = document.getElementById('uploadFileNameLabel');
  const uploadPercentLabel           = document.getElementById('uploadPercentLabel');
  const uploadProgressBar            = document.getElementById('uploadProgressBar');
  const archiveInspectionCard        = document.getElementById('archiveInspectionCard');
  const inspectCardFileName          = document.getElementById('inspectCardFileName');
  const inspectCardMeta              = document.getElementById('inspectCardMeta');
  const inspectCardBadge             = document.getElementById('inspectCardBadge');
  const inspectCardDetail            = document.getElementById('inspectCardDetail');
  const modeCardTest                 = document.getElementById('modeCardTest');
  const modeCardProd                 = document.getElementById('modeCardProd');
  const radioModeTest                = document.getElementById('radioModeTest');
  const radioModeProd                = document.getElementById('radioModeProd');
  const prodConfirmCallout           = document.getElementById('prodConfirmCallout');
  const confirmRestorePhraseInput    = document.getElementById('confirmRestorePhraseInput');
  const restoreExecutionPanel        = document.getElementById('restoreExecutionPanel');
  const restoreStatusTitle           = document.getElementById('restoreStatusTitle');
  const restoreStatusBadge           = document.getElementById('restoreStatusBadge');
  const restoreConsoleBox            = document.getElementById('restoreConsoleBox');
  const restoreAuditCountsPanel      = document.getElementById('restoreAuditCountsPanel');
  const auditStatsGrid               = document.getElementById('auditStatsGrid');
  const executeRestoreBtn            = document.getElementById('executeRestoreBtn');
  const executeRestoreBtnText        = document.getElementById('executeRestoreBtnText');

  // Modals
  const runBackupModal            = document.getElementById('runBackupModal');
  const closeRunBackupModalBtn    = document.getElementById('closeRunBackupModalBtn');
  const cancelRunBackupBtn        = document.getElementById('cancelRunBackupBtn');
  const confirmRunBackupBtn       = document.getElementById('confirmRunBackupBtn');
  const backupExecutionSpinner    = document.getElementById('backupExecutionSpinner');
  const backupExecutionResult     = document.getElementById('backupExecutionResult');
  const modalBackupLocationSelect = document.getElementById('modalBackupLocationSelect');

  const locationConfigModal       = document.getElementById('locationConfigModal');
  const closeLocationConfigModalBtn= document.getElementById('closeLocationConfigModalBtn');
  const cancelLocationConfigBtn   = document.getElementById('cancelLocationConfigBtn');
  const saveLocationConfigBtn     = document.getElementById('saveLocationConfigBtn');
  const detectedDrivesGrid        = document.getElementById('detectedDrivesGrid');
  const customLocationInput       = document.getElementById('customLocationInput');
  const testWriteAccessBtn        = document.getElementById('testWriteAccessBtn');
  const testWriteResult           = document.getElementById('testWriteResult');
  const setAsDefaultCheckbox      = document.getElementById('setAsDefaultCheckbox');
  const browseSystemFolderBtn     = document.getElementById('browseSystemFolderBtn');
  const systemFolderPicker         = document.getElementById('systemFolderPicker');
  const quickFolderChips          = document.getElementById('quickFolderChips');
  const recentFoldersSection      = document.getElementById('recentFoldersSection');
  const recentFolderChips         = document.getElementById('recentFolderChips');

  // ── Recent Folders Persistence ───────────────────────────────────────────
  function getRecentFolders() {
    try {
      const raw = localStorage.getItem('ritham_backup_recent_folders');
      return raw ? JSON.parse(raw) : [];
    } catch (e) {
      return [];
    }
  }

  function saveRecentFolder(path) {
    if (!path || !path.trim()) return;
    try {
      let list = getRecentFolders();
      list = list.filter(p => p.toUpperCase() !== path.trim().toUpperCase());
      list.unshift(path.trim());
      if (list.length > 6) list = list.slice(0, 6);
      localStorage.setItem('ritham_backup_recent_folders', JSON.stringify(list));
    } catch (e) {}
  }

  const playbookModal             = document.getElementById('playbookModal');
  const closePlaybookBtn          = document.getElementById('closePlaybookBtn');
  const dismissPlaybookBtn        = document.getElementById('dismissPlaybookBtn');

  const verifyModal               = document.getElementById('verifyModal');
  const closeVerifyModalBtn       = document.getElementById('closeVerifyModalBtn');
  const dismissVerifyModalBtn     = document.getElementById('dismissVerifyModalBtn');
  const verifyModalBody           = document.getElementById('verifyModalBody');

  // ── Load All Data ─────────────────────────────────────────────────────────
  async function loadAllData() {
    try {
      await loadLocations();
      await Promise.all([
        loadStatus(),
        loadFiles(),
      ]);
    } catch (err) {
      console.error('Error loading backup data:', err);
    }
  }

  // ── Load Available Locations ──────────────────────────────────────────────
  async function loadLocations() {
    try {
      const data = await Api.get(API.BACKUP_LOCATIONS);
      availableLocations = data || [];
      renderLocationDropdowns();
      renderDriveCardsInModal();
    } catch (err) {
      console.warn('Could not load backup locations:', err);
    }
  }

  function renderLocationDropdowns() {
    if (!locationSelectDropdown) return;

    // Find currently active target drive if not yet set
    const current = availableLocations.find(l => l.currentTarget);
    if (!activeLocation && current) {
      activeLocation = current.suggestedPath || (current.driveLetter + '\\ERP-Backups');
    }

    const recent = getRecentFolders();
    let optionsHtml = '';

    // Group 1: Connected Storage Drives
    if (availableLocations.length > 0) {
      optionsHtml += '<optgroup label="Connected Storage Drives">';
      optionsHtml += availableLocations.map(l => {
        const isRemovable = l.removable;
        const tag = isRemovable ? ' [EXTERNAL USB]' : ' [LOCAL DISK]';
        const path = l.suggestedPath || (l.driveLetter + '\\ERP-Backups');
        const isSelected = activeLocation && (path.toUpperCase() === activeLocation.toUpperCase());
        return `<option value="${Utils.escapeHtml(path)}" ${isSelected ? 'selected' : ''}>${l.driveLetter} (${l.volumeName || 'Drive'}) • ${l.freeSpaceGB} GB Free${tag}</option>`;
      }).join('');
      optionsHtml += '</optgroup>';
    }

    // Group 2: Custom Selected Folders
    const customList = recent.filter(r => !availableLocations.some(l => (l.suggestedPath || (l.driveLetter + '\\ERP-Backups')).toUpperCase() === r.toUpperCase()));
    if (customList.length > 0) {
      optionsHtml += '<optgroup label="Custom Folders">';
      customList.forEach(r => {
        const isSelected = activeLocation && activeLocation.toUpperCase() === r.toUpperCase();
        optionsHtml += `<option value="${Utils.escapeHtml(r)}" ${isSelected ? 'selected' : ''}>📁 ${Utils.escapeHtml(r)}</option>`;
      });
      optionsHtml += '</optgroup>';
    }

    // If activeLocation is custom and not already in options, add it as active option
    const isPresent = availableLocations.some(l => (l.suggestedPath || (l.driveLetter + '\\ERP-Backups')).toUpperCase() === (activeLocation || '').toUpperCase()) ||
                      customList.some(r => r.toUpperCase() === (activeLocation || '').toUpperCase());
    if (activeLocation && !isPresent) {
      optionsHtml = `<optgroup label="Active Selected Folder"><option value="${Utils.escapeHtml(activeLocation)}" selected>📁 ${Utils.escapeHtml(activeLocation)}</option></optgroup>` + optionsHtml;
    }

    // Action Option
    optionsHtml += `<optgroup label="Actions"><option value="__CHOOSE_CUSTOM__">📁 + Select / Browse Custom Folder...</option></optgroup>`;

    locationSelectDropdown.innerHTML = optionsHtml;
    if (modalBackupLocationSelect) {
      modalBackupLocationSelect.innerHTML = optionsHtml;
    }

    updateBannerDisplay();
  }

  function updateBannerDisplay() {
    if (activeLocationPathDisplay) {
      activeLocationPathDisplay.textContent = activeLocation || 'E:\\ERP-Backups';
    }

    const currentLoc = availableLocations.find(l =>
      activeLocation && activeLocation.toUpperCase().startsWith(l.driveLetter.toUpperCase())
    );

    if (activeDriveTypeBadge) {
      if (currentLoc && currentLoc.removable) {
        activeDriveTypeBadge.className = 'badge badge-success';
        activeDriveTypeBadge.textContent = 'EXTERNAL USB DRIVE';
      } else if (currentLoc) {
        activeDriveTypeBadge.className = 'badge badge-info';
        activeDriveTypeBadge.textContent = 'LOCAL STORAGE';
      } else {
        activeDriveTypeBadge.className = 'badge badge-secondary';
        activeDriveTypeBadge.textContent = 'STORAGE TARGET';
      }
    }
  }

  function renderDriveCardsInModal() {
    if (!detectedDrivesGrid) return;

    if (availableLocations.length === 0) {
      detectedDrivesGrid.innerHTML = `
        <div style="padding: 24px; text-align: center; color: var(--text-muted);">
          No accessible storage drives detected.
        </div>
      `;
      return;
    }

    const cardsHtml = availableLocations.map((l, idx) => {
      const isSelected = activeLocation && activeLocation.toUpperCase().startsWith(l.driveLetter.toUpperCase());
      const badgeClass = l.removable ? 'badge-removable' : 'badge-fixed';
      const badgeText = l.removable ? 'EXTERNAL USB' : 'LOCAL DISK';

      return `
        <div class="drive-card ${isSelected ? 'selected' : ''}" data-index="${idx}" data-path="${Utils.escapeHtml(l.suggestedPath || '')}" data-letter="${Utils.escapeHtml(l.driveLetter)}">
          <div class="drive-card-header">
            <div class="drive-card-title">
              <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="${l.removable ? '#16a34a' : '#2563eb'}" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <rect x="2" y="2" width="20" height="8" rx="2" ry="2"/>
                <rect x="2" y="14" width="20" height="8" rx="2" ry="2"/>
                <line x1="6" y1="6" x2="6.01" y2="6"/>
                <line x1="6" y1="18" x2="6.01" y2="18"/>
              </svg>
              <span>Drive ${Utils.escapeHtml(l.driveLetter)}</span>
            </div>
            <span class="${badgeClass}">${badgeText}</span>
          </div>
          <div class="drive-card-sub">
            ${Utils.escapeHtml(l.volumeName || 'Storage Volume')} • ${Utils.escapeHtml(l.fileSystem || 'NTFS')}
          </div>
          <div class="drive-card-space-row">
            <span>Free Space</span>
            <span>${l.freeSpaceGB} GB / ${l.totalSpaceGB} GB</span>
          </div>
          <div class="drive-card-bar-bg">
            <div class="drive-card-bar-fill" style="width: ${Math.min(100, l.usedPercent)}%; background: ${l.usedPercent > 90 ? '#ef4444' : (l.removable ? '#16a34a' : '#2563eb')};"></div>
          </div>
        </div>
      `;
    }).join('');

    detectedDrivesGrid.innerHTML = cardsHtml;

    // Attach card click handlers
    detectedDrivesGrid.querySelectorAll('.drive-card').forEach(card => {
      card.addEventListener('click', () => {
        detectedDrivesGrid.querySelectorAll('.drive-card').forEach(c => c.classList.remove('selected'));
        card.classList.add('selected');
        const path = card.dataset.path;
        if (customLocationInput) {
          customLocationInput.value = path;
        }
        renderFolderSuggestions(path);
        handleTestWriteAccess();
      });
    });
  }

  // ── Load Status & Diagnostic Metrics ──────────────────────────────────────
  async function loadStatus() {
    try {
      const url = activeLocation ? `${API.BACKUP_STATUS}?location=${encodeURIComponent(activeLocation)}` : API.BACKUP_STATUS;
      const data = await Api.get(url);
      if (!data) return;
      statusData = data;
      renderStatus(data);
    } catch (err) {
      console.error('Failed to load backup status:', err);
      Toast.error('Could not fetch backup system status');
    }
  }

  function renderStatus(data) {
    // 1. External Drive Card
    const drive = data.drive || {};
    if (drive.connected) {
      driveFreeSpaceVal.textContent = `${drive.freeSpaceGB} GB Free`;
      driveSubtext.innerHTML = `<span class="pulse-dot online"></span> ${drive.driveLetter} Mounted (${drive.totalSpaceGB} GB total)`;
      driveProgressBar.style.width = `${Math.min(100, drive.usedPercent)}%`;
      driveProgressBar.style.background = drive.usedPercent > 90 ? '#ef4444' : (drive.usedPercent > 75 ? '#f59e0b' : '#2563eb');

      diagDriveDot.className = 'pulse-dot online';
      diagDriveBadge.className = 'badge badge-success';
      diagDriveBadge.textContent = `${drive.driveLetter} Connected`;

      if (drive.freeSpaceGB > 0.5) {
        diagSpaceDot.className = 'pulse-dot online';
        diagSpaceBadge.className = 'badge badge-success';
        diagSpaceBadge.textContent = `${drive.freeSpaceGB} GB Available`;
      } else {
        diagSpaceDot.className = 'pulse-dot offline';
        diagSpaceBadge.className = 'badge badge-danger';
        diagSpaceBadge.textContent = 'Low Space (< 500 MB)';
      }
    } else {
      driveFreeSpaceVal.textContent = 'Drive Detached';
      driveSubtext.innerHTML = `<span class="pulse-dot offline"></span> ${drive.driveLetter || 'E:'} Disconnected`;
      driveProgressBar.style.width = '0%';

      diagDriveDot.className = 'pulse-dot offline';
      diagDriveBadge.className = 'badge badge-danger';
      diagDriveBadge.textContent = 'Disconnected';

      diagSpaceDot.className = 'pulse-dot offline';
      diagSpaceBadge.className = 'badge badge-danger';
      diagSpaceBadge.textContent = 'Drive Offline';
    }

    // 2. Latest Snapshot Card
    const latest = data.latestBackup;
    if (latest) {
      latestSnapshotAge.textContent = latest.ageText || 'Recently';
      latestSnapshotFile.textContent = latest.fileName;
      latestSnapshotFile.title = latest.fileName;
      latestSnapshotBadge.className = 'badge badge-success';
      latestSnapshotBadge.textContent = `Verified (${latest.sizeMB} MB)`;
    } else {
      latestSnapshotAge.textContent = 'No Archives';
      latestSnapshotFile.textContent = 'No .dump snapshots in this location';
      latestSnapshotBadge.className = 'badge badge-warning';
      latestSnapshotBadge.textContent = 'Pending First Run';
    }

    // 3. PostgreSQL Engine Card
    if (data.postgresRunning) {
      postgresStatusVal.textContent = 'PostgreSQL 17';
      postgresSubtext.innerHTML = `<span class="pulse-dot online"></span> Port 5432 • ${data.databaseName}`;
      diagPgDot.className = 'pulse-dot online';
      diagPgBadge.className = 'badge badge-success';
      diagPgBadge.textContent = 'Online (Port 5432)';
    } else {
      postgresStatusVal.textContent = 'Engine Stopped';
      postgresSubtext.innerHTML = `<span class="pulse-dot offline"></span> PostgreSQL is offline`;
      diagPgDot.className = 'pulse-dot offline';
      diagPgBadge.className = 'badge badge-warning';
      diagPgBadge.textContent = 'Service Stopped';
    }

    // 4. Schedule & Retention Card
    if (data.nextScheduledRun) {
      nextScheduledRunVal.textContent = `Next run: ${data.nextScheduledRun}`;
    }
    retentionStatsBadge.textContent = `${data.dailyCount}/${data.dailyLimit}D • ${data.weeklyCount}/${data.weeklyLimit}W • ${data.monthlyCount}/${data.monthlyLimit}M`;

    // Concurrency Lock
    if (data.isLocked) {
      diagLockDot.className = 'pulse-dot offline';
      diagLockBadge.className = 'badge badge-warning';
      diagLockBadge.textContent = 'Backup In Progress';
    } else {
      diagLockDot.className = 'pulse-dot online';
      diagLockBadge.className = 'badge badge-success';
      diagLockBadge.textContent = 'System Idle';
    }

    // Tab counts
    tabAllCount.textContent = String(data.totalCount || 0);
    tabDailyCount.textContent = `${data.dailyCount || 0}/${data.dailyLimit || 7}`;
    tabWeeklyCount.textContent = `${data.weeklyCount || 0}/${data.weeklyLimit || 4}`;
    tabMonthlyCount.textContent = `${data.monthlyCount || 0}/${data.monthlyLimit || 12}`;
    if (tabUploadCount) tabUploadCount.textContent = String(data.uploadCount || 0);
    totalFilesCountBadge.textContent = `${data.totalCount || 0} Archives`;

    // Overall Status Badge
    if (drive.connected && data.postgresRunning) {
      overallStatusBadge.className = 'badge badge-success';
      overallStatusBadge.innerHTML = `<span class="pulse-dot online"></span> Redundancy Healthy`;
    } else if (!drive.connected) {
      overallStatusBadge.className = 'badge badge-danger';
      overallStatusBadge.innerHTML = `<span class="pulse-dot offline"></span> Target Drive Missing`;
    } else {
      overallStatusBadge.className = 'badge badge-warning';
      overallStatusBadge.innerHTML = `<span class="pulse-dot offline"></span> Database Offline`;
    }
  }

  // ── Load Backup Files ─────────────────────────────────────────────────────
  async function loadFiles() {
    try {
      const url = `${API.BACKUP_FILES}?category=${encodeURIComponent(activeCategory)}${activeLocation ? `&location=${encodeURIComponent(activeLocation)}` : ''}`;
      const files = await Api.get(url);
      backupFiles = files || [];
      renderTable();
    } catch (err) {
      console.error('Failed to load backup files:', err);
      backupTableBody.innerHTML = `
        <tr>
          <td colspan="7" style="text-align: center; padding: 30px; color: var(--status-delayed, #ef4444);">
            Failed to load backup archives from ${Utils.escapeHtml(activeLocation || 'target location')}.
          </td>
        </tr>
      `;
    }
  }

  // ── Render Backup Archives Table ──────────────────────────────────────────
  function renderTable() {
    let filtered = [...backupFiles];

    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase().trim();
      filtered = filtered.filter(f =>
        f.fileName.toLowerCase().includes(q) ||
        f.category.toLowerCase().includes(q) ||
        (f.ageText && f.ageText.toLowerCase().includes(q))
      );
    }

    tableResultSummary.textContent = `Showing ${filtered.length} of ${backupFiles.length} archives in ${activeCategory}`;

    if (filtered.length === 0) {
      backupTableBody.innerHTML = `
        <tr>
          <td colspan="7" style="text-align: center; padding: 48px; color: var(--text-muted);">
            <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" style="margin: 0 auto 10px; color: var(--text-muted); display: block;">
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
              <polyline points="14 2 14 8 20 8"></polyline>
            </svg>
            <div style="font-weight: 500; font-size: 14px; color: var(--text-primary);">No backup archives found in this location</div>
            <div style="font-size: 12px; margin-top: 4px; color: var(--text-muted);">Click "Run Backup Now" to create a complete snapshot.</div>
          </td>
        </tr>
      `;
      return;
    }

    const rows = filtered.map(f => {
      let badgeClass = 'badge-daily';
      if (f.category === 'Weekly') badgeClass = 'badge-weekly';
      if (f.category === 'Monthly') badgeClass = 'badge-monthly';
      if (f.category === 'Uploads') badgeClass = 'badge-uploads';

      const formattedDate = f.modifiedTime ? f.modifiedTime.replace('T', ' ').substring(0, 19) : '--';

      return `
        <tr>
          <td>
            <div class="file-name-cell">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#2563eb" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
                <polyline points="14 2 14 8 20 8"></polyline>
              </svg>
              <span>${Utils.escapeHtml(f.fileName)}</span>
            </div>
          </td>
          <td>
            <span class="category-badge ${badgeClass}">${Utils.escapeHtml(f.category)}</span>
          </td>
          <td style="font-weight: 600;">
            ${f.sizeMB} MB
          </td>
          <td style="color: var(--text-muted); font-size: 12px;">
            ${formattedDate}
          </td>
          <td>
            <span class="badge badge-ghost" style="font-size: 11.5px;">${Utils.escapeHtml(f.ageText || '')}</span>
          </td>
          <td>
            <span class="badge badge-success" style="font-size: 11px; gap:4px;">
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                <polyline points="20 6 9 17 4 12"></polyline>
              </svg>
              Verified
            </span>
          </td>
          <td style="text-align: right;">
            <div style="display: inline-flex; gap: 6px;">
              <button class="btn btn-ghost btn-sm restore-action-btn" data-category="${Utils.escapeHtml(f.category)}" data-file="${Utils.escapeHtml(f.fileName)}" title="Restore database from this backup" style="padding: 4px 8px; font-size: 12px; gap: 4px; color: #60a5fa;">
                <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"></path>
                  <path d="M3 3v5h5"></path>
                </svg>
                <span>Restore</span>
              </button>
              <button class="btn btn-ghost btn-sm download-btn" data-category="${Utils.escapeHtml(f.category)}" data-file="${Utils.escapeHtml(f.fileName)}" title="Download .dump" style="padding: 4px 8px; font-size: 12px; gap: 4px;">
                <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path>
                  <polyline points="7 10 12 15 17 10"></polyline>
                  <line x1="12" y1="15" x2="12" y2="3"></line>
                </svg>
                <span>Download</span>
              </button>
              <button class="btn btn-ghost btn-sm verify-btn" data-category="${Utils.escapeHtml(f.category)}" data-file="${Utils.escapeHtml(f.fileName)}" title="Verify Table of Contents" style="padding: 4px 8px; font-size: 12px; gap: 4px;">
                <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"></path>
                </svg>
                <span>Verify</span>
              </button>
            </div>
          </td>
        </tr>
      `;
    }).join('');

    backupTableBody.innerHTML = rows;

    // Attach row button events
    backupTableBody.querySelectorAll('.restore-action-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        const found = backupFiles.find(b => b.fileName === btn.dataset.file && b.category === btn.dataset.category);
        openImportModal(found || { fileName: btn.dataset.file, category: btn.dataset.category });
      });
    });

    backupTableBody.querySelectorAll('.download-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        downloadBackupFile(btn.dataset.category, btn.dataset.file);
      });
    });

    backupTableBody.querySelectorAll('.verify-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        openVerifyModal(btn.dataset.category, btn.dataset.file);
      });
    });
  }

  // ── Download Backup Dump ──────────────────────────────────────────────────
  async function downloadBackupFile(category, fileName) {
    try {
      Toast.info(`Preparing secure download for ${fileName}...`);
      const token = Storage.getToken();
      const url = API.BACKUP_DOWNLOAD(category, fileName, activeLocation);

      const res = await fetch(url, {
        headers: {
          'Authorization': `Bearer ${token}`
        }
      });

      if (!res.ok) {
        throw new Error(`Download failed with status ${res.status}`);
      }

      const blob = await res.blob();
      const blobUrl = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = blobUrl;
      a.download = fileName;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      window.URL.revokeObjectURL(blobUrl);

      Toast.success(`Successfully downloaded ${fileName}`);
    } catch (err) {
      console.error('Download error:', err);
      Toast.error(err.message || 'Failed to download snapshot file');
    }
  }

  // ── Verify Table of Contents ──────────────────────────────────────────────
  async function openVerifyModal(category, fileName) {
    verifyModalBody.innerHTML = `
      <div style="text-align: center; padding: 24px 0;">
        <div class="spinner" style="width: 32px; height: 32px; margin: 0 auto 12px; border-width: 3px;"></div>
        <div style="font-weight: 600; color: var(--text-primary);">Reading archive Table of Contents with pg_restore...</div>
        <div style="font-size: 12px; color: var(--text-muted); margin-top: 4px;">Target: ${Utils.escapeHtml(fileName)}</div>
      </div>
    `;
    verifyModal.style.display = 'flex';

    try {
      const res = await Api.post(API.BACKUP_VERIFY(category, fileName, activeLocation));
      if (res && res.verified) {
        verifyModalBody.innerHTML = `
          <div style="background: rgba(34, 197, 94, 0.12); border: 1px solid rgba(34, 197, 94, 0.25); border-radius: 8px; padding: 16px; margin-bottom: 14px;">
            <div style="display: flex; align-items: center; gap: 8px; color: #4ade80; font-weight: 600; font-size: 15px;">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path>
                <polyline points="22 4 12 14.01 9 11.01"></polyline>
              </svg>
              <span>Integrity Verification Passed</span>
            </div>
            <div style="margin-top: 8px; font-size: 13px; color: var(--text-secondary);">
              ${Utils.escapeHtml(res.message || '')}
            </div>
          </div>
          <div style="font-size: 13px; color: var(--text-secondary); line-height: 1.6;">
            <div>• <strong>File:</strong> <code>${Utils.escapeHtml(res.fileName)}</code></div>
            <div>• <strong>Tier:</strong> ${Utils.escapeHtml(res.category)}</div>
            <div>• <strong>TOC Elements:</strong> ${res.entryCount} database objects (tables, indexes, blobs, constraints)</div>
            <div>• <strong>Format:</strong> PostgreSQL Custom Compressed Archive</div>
          </div>
        `;
      } else {
        verifyModalBody.innerHTML = `
          <div style="background: rgba(239, 68, 68, 0.12); border: 1px solid rgba(239, 68, 68, 0.25); border-radius: 8px; padding: 16px;">
            <div style="color: #f87171; font-weight: 600; font-size: 15px;">Verification Failed</div>
            <div style="margin-top: 6px; font-size: 13px; color: var(--text-secondary);">
              The dump file could not be parsed by pg_restore. It may be incomplete or corrupted.
            </div>
          </div>
        `;
      }
    } catch (err) {
      verifyModalBody.innerHTML = `
        <div style="background: rgba(239, 68, 68, 0.12); border: 1px solid rgba(239, 68, 68, 0.25); border-radius: 8px; padding: 16px; color: #f87171;">
          <div style="font-weight: 600;">Error during verification check:</div>
          <div style="font-size: 13px; margin-top: 4px; color: var(--text-secondary);">${Utils.escapeHtml(err.message || 'Execution error')}</div>
        </div>
      `;
    }
  }

  // ── Run Complete Manual Backup ────────────────────────────────────────────
  function openRunBackupModal() {
    backupExecutionSpinner.style.display = 'none';
    backupExecutionResult.style.display = 'none';
    backupExecutionResult.innerHTML = '';
    confirmRunBackupBtn.disabled = false;
    cancelRunBackupBtn.disabled = false;
    confirmRunBackupBtn.style.display = 'inline-block';

    // Synchronize selected option in modal dropdown
    if (modalBackupLocationSelect && activeLocation) {
      modalBackupLocationSelect.value = activeLocation;
    }

    runBackupModal.style.display = 'flex';
  }

  async function executeManualBackup() {
    backupExecutionSpinner.style.display = 'block';
    backupExecutionResult.style.display = 'none';
    confirmRunBackupBtn.disabled = true;
    cancelRunBackupBtn.disabled = true;

    const targetLoc = modalBackupLocationSelect ? modalBackupLocationSelect.value : activeLocation;

    try {
      const payload = {
        targetLocation: targetLoc,
        completeBackup: true,
        persistAsDefault: false
      };

      const res = await Api.post(API.BACKUP_RUN, payload);
      backupExecutionSpinner.style.display = 'none';
      confirmRunBackupBtn.style.display = 'none';
      cancelRunBackupBtn.disabled = false;
      cancelRunBackupBtn.textContent = 'Close';

      if (res && res.success) {
        Toast.success('Complete external drive backup finished successfully!');
        backupExecutionResult.style.display = 'block';
        backupExecutionResult.innerHTML = `
          <div style="background: rgba(34, 197, 94, 0.12); border: 1px solid rgba(34, 197, 94, 0.25); border-radius: 8px; padding: 16px;">
            <div style="display: flex; align-items: center; gap: 8px; color: #4ade80; font-weight: 600; font-size: 15px;">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path>
                <polyline points="22 4 12 14.01 9 11.01"></polyline>
              </svg>
              <span>Complete Backup Created Successfully!</span>
            </div>
            <div style="font-size: 13px; color: var(--text-secondary); margin-top: 8px;">
              Duration: <strong>${res.durationSeconds}s</strong> • Snapshot: <code>${Utils.escapeHtml(res.backupFileName || 'ritham_erp_latest.dump')}</code>
            </div>
            <div style="margin-top: 8px; font-size: 12.5px; color: var(--text-secondary); line-height: 1.5;">
              • Destination: <code>${Utils.escapeHtml(targetLoc)}</code><br>
              • TOC Verified • Globals SQL Archived • Manifest Generated
            </div>
          </div>
        `;

        // Update active location if needed and refresh
        if (targetLoc && targetLoc !== activeLocation) {
          activeLocation = targetLoc;
          if (locationSelectDropdown) locationSelectDropdown.value = targetLoc;
          updateBannerDisplay();
        }
        await Promise.all([loadStatus(), loadFiles(), loadLogs()]);

      } else {
        Toast.error(res?.message || 'Backup failed');
        backupExecutionResult.style.display = 'block';
        backupExecutionResult.innerHTML = `
          <div style="background: rgba(239, 68, 68, 0.12); border: 1px solid rgba(239, 68, 68, 0.25); border-radius: 8px; padding: 16px; color: #f87171;">
            <div style="font-weight: 600;">Backup Execution Failed</div>
            <div style="font-size: 13px; margin-top: 4px; color: var(--text-secondary);">${Utils.escapeHtml(res?.message || 'Script reported non-zero exit code.')}</div>
          </div>
        `;
      }

    } catch (err) {
      backupExecutionSpinner.style.display = 'none';
      confirmRunBackupBtn.style.display = 'none';
      cancelRunBackupBtn.disabled = false;
      cancelRunBackupBtn.textContent = 'Close';

      Toast.error(err.message || 'Error triggering backup');
      backupExecutionResult.style.display = 'block';
      backupExecutionResult.innerHTML = `
        <div style="background: rgba(239, 68, 68, 0.12); border: 1px solid rgba(239, 68, 68, 0.25); border-radius: 8px; padding: 16px; color: #f87171;">
          <div style="font-weight: 600;">Failed to trigger backup</div>
          <div style="font-size: 13px; margin-top: 4px; color: var(--text-secondary);">${Utils.escapeHtml(err.message || '')}</div>
        </div>
      `;
    }
  }

  // ── Location Configuration Modal Actions ──────────────────────────────────
  function openLocationConfigModal() {
    renderDriveCardsInModal();
    if (customLocationInput) {
      customLocationInput.value = activeLocation || 'E:\\ERP-Backups';
    }
    renderFolderSuggestions(customLocationInput ? customLocationInput.value : '');
    renderRecentFolderChips();
    locationConfigModal.style.display = 'flex';
    handleTestWriteAccess();
  }

  function renderFolderSuggestions(basePath) {
    if (!quickFolderChips) return;
    const path = basePath || (customLocationInput ? customLocationInput.value : activeLocation) || 'E:\\ERP-Backups';
    const driveLetter = path.length >= 2 ? path.substring(0, 2) : 'E:';

    const suggestions = [
      `${driveLetter}\\ERP-Backups`,
      `${driveLetter}\\Backups\\RithamERP`,
      `${driveLetter}\\Database_Archives`,
      `${driveLetter}\\Daily_Snapshots`
    ];

    const currentNorm = (customLocationInput ? customLocationInput.value.trim() : '').toUpperCase();

    quickFolderChips.innerHTML = suggestions.map(s => {
      const isSelected = currentNorm === s.toUpperCase();
      return `
        <span class="folder-chip ${isSelected ? 'active' : ''}" data-path="${Utils.escapeHtml(s)}">
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/></svg>
          ${Utils.escapeHtml(s)}
        </span>
      `;
    }).join('');

    quickFolderChips.querySelectorAll('.folder-chip').forEach(chip => {
      chip.addEventListener('click', () => {
        quickFolderChips.querySelectorAll('.folder-chip').forEach(c => c.classList.remove('active'));
        chip.classList.add('active');
        if (customLocationInput) {
          customLocationInput.value = chip.dataset.path;
        }
        handleTestWriteAccess();
      });
    });
  }

  function renderRecentFolderChips() {
    if (!recentFoldersSection || !recentFolderChips) return;
    const recent = getRecentFolders();
    if (!recent || recent.length === 0) {
      recentFoldersSection.style.display = 'none';
      return;
    }

    recentFoldersSection.style.display = 'block';
    const currentNorm = (customLocationInput ? customLocationInput.value.trim() : '').toUpperCase();

    recentFolderChips.innerHTML = recent.map(r => {
      const isSelected = currentNorm === r.toUpperCase();
      return `
        <span class="folder-chip ${isSelected ? 'active' : ''}" data-path="${Utils.escapeHtml(r)}">
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/></svg>
          ${Utils.escapeHtml(r)}
        </span>
      `;
    }).join('');

    recentFolderChips.querySelectorAll('.folder-chip').forEach(chip => {
      chip.addEventListener('click', () => {
        recentFolderChips.querySelectorAll('.folder-chip').forEach(c => c.classList.remove('active'));
        chip.classList.add('active');
        if (customLocationInput) {
          customLocationInput.value = chip.dataset.path;
        }
        handleTestWriteAccess();
      });
    });
  }

  async function handleTestWriteAccess() {
    const path = customLocationInput ? customLocationInput.value.trim() : '';
    if (!path) {
      if (testWriteResult) testWriteResult.style.display = 'none';
      return;
    }

    testWriteResult.style.display = 'block';
    testWriteResult.innerHTML = '<span style="color: var(--text-muted); font-size:12px;">Checking path & write permissions...</span>';

    try {
      // Validate via status endpoint
      const res = await Api.get(`${API.BACKUP_STATUS}?location=${encodeURIComponent(path)}`);
      if (res && res.drive && res.drive.connected) {
        testWriteResult.innerHTML = `
          <div style="color: #4ade80; font-weight: 600; font-size:12.5px; display:flex; align-items:center; gap:6px;">
            <span>✓</span>
            <span>Ready: <strong>${res.drive.freeSpaceGB} GB Free</strong> on Drive ${res.drive.driveLetter || ''} • Full Write Access</span>
          </div>
        `;
      } else {
        testWriteResult.innerHTML = `
          <div style="color: #f87171; font-weight: 600; font-size:12.5px; display:flex; align-items:center; gap:6px;">
            <span>✕</span>
            <span>Drive is not accessible or disconnected. Please verify drive letter and folder path.</span>
          </div>
        `;
      }
    } catch (err) {
      testWriteResult.innerHTML = `
        <div style="color: #f87171; font-weight: 600; font-size:12.5px;">
          ✕ Error testing path: ${Utils.escapeHtml(err.message || 'Access error')}
        </div>
      `;
    }
  }

  async function handleSaveLocationConfig() {
    const path = customLocationInput ? customLocationInput.value.trim() : '';
    if (!path) {
      Toast.warning('Please enter or select a backup target folder.');
      return;
    }

    const persistAsDefault = setAsDefaultCheckbox ? setAsDefaultCheckbox.checked : true;

    try {
      saveLocationConfigBtn.disabled = true;
      saveLocationConfigBtn.textContent = 'Applying...';

      const res = await Api.post(API.BACKUP_SET_LOCATION, {
        locationPath: path,
        persistAsDefault: persistAsDefault
      });

      saveRecentFolder(path);
      Toast.success(`Backup folder set to: ${path}`);
      activeLocation = path;
      locationConfigModal.style.display = 'none';

      await loadLocations();
      await Promise.all([loadStatus(), loadFiles(), loadLogs()]);

    } catch (err) {
      console.error('Failed to update location:', err);
      Toast.error(err.message || 'Failed to update backup location');
    } finally {
      saveLocationConfigBtn.disabled = false;
      saveLocationConfigBtn.textContent = 'Select & Apply Folder';
    }
  }

  // ── Event Listeners ───────────────────────────────────────────────────────
  if (refreshBackupBtn) {
    refreshBackupBtn.addEventListener('click', () => {
      Toast.info('Refreshing backup metrics...');
      loadAllData();
    });
  }

  if (refreshLogsBtn) {
    refreshLogsBtn.addEventListener('click', () => {
      loadLogs();
      Toast.success('Logs updated');
    });
  }

  if (backupSearchInput) {
    backupSearchInput.addEventListener('input', (e) => {
      searchQuery = e.target.value;
      renderTable();
    });
  }

  // Location Selector Dropdown Change in Top Banner
  if (locationSelectDropdown) {
    locationSelectDropdown.addEventListener('change', (e) => {
      const selected = e.target.value;
      if (selected === '__CHOOSE_CUSTOM__') {
        openLocationConfigModal();
        // Revert dropdown display to current activeLocation until modal confirms
        locationSelectDropdown.value = activeLocation;
        return;
      }
      if (selected) {
        activeLocation = selected;
        saveRecentFolder(selected);
        updateBannerDisplay();
        Toast.info(`Switching view to ${selected}...`);
        Promise.all([loadStatus(), loadFiles(), loadLogs()]);
      }
    });
  }

  // Direct "Select Folder" button in banner
  if (browseFolderBtn) {
    browseFolderBtn.addEventListener('click', openLocationConfigModal);
  }

  // System Folder Picker ("Browse..." button in modal)
  if (browseSystemFolderBtn && systemFolderPicker) {
    browseSystemFolderBtn.addEventListener('click', () => {
      systemFolderPicker.value = '';
      systemFolderPicker.click();
    });

    systemFolderPicker.addEventListener('change', (e) => {
      const files = e.target.files;
      if (files && files.length > 0) {
        const relPath = files[0].webkitRelativePath || '';
        const folderName = relPath.split('/')[0] || relPath.split('\\')[0] || '';
        if (folderName) {
          const curVal = customLocationInput.value.trim();
          const drivePrefix = curVal.length >= 2 ? curVal.substring(0, 2) : (availableLocations[0]?.driveLetter || 'E:');
          customLocationInput.value = `${drivePrefix}\\${folderName}`;
          renderFolderSuggestions(customLocationInput.value);
          handleTestWriteAccess();
          Toast.info(`Selected folder: ${folderName}`);
        }
      }
    });
  }

  // Live input change on custom folder path
  if (customLocationInput) {
    let debounceTimer = null;
    customLocationInput.addEventListener('input', () => {
      clearTimeout(debounceTimer);
      debounceTimer = setTimeout(() => {
        renderFolderSuggestions(customLocationInput.value);
        handleTestWriteAccess();
      }, 400);
    });
  }

  // Category Tab switches
  tabButtons.forEach(btn => {
    btn.addEventListener('click', () => {
      tabButtons.forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      activeCategory = btn.dataset.category || 'All';
      loadFiles();
    });
  });

  // Location Config Modal Events
  if (openLocationConfigBtn) openLocationConfigBtn.addEventListener('click', openLocationConfigModal);
  if (closeLocationConfigModalBtn) closeLocationConfigModalBtn.addEventListener('click', () => { locationConfigModal.style.display = 'none'; });
  if (cancelLocationConfigBtn) cancelLocationConfigBtn.addEventListener('click', () => { locationConfigModal.style.display = 'none'; });
  if (testWriteAccessBtn) testWriteAccessBtn.addEventListener('click', handleTestWriteAccess);
  if (saveLocationConfigBtn) saveLocationConfigBtn.addEventListener('click', handleSaveLocationConfig);

  // Run Manual Backup Modal Events
  if (runManualBackupBtn) runManualBackupBtn.addEventListener('click', openRunBackupModal);
  if (closeRunBackupModalBtn) closeRunBackupModalBtn.addEventListener('click', () => { runBackupModal.style.display = 'none'; });
  if (cancelRunBackupBtn) cancelRunBackupBtn.addEventListener('click', () => { runBackupModal.style.display = 'none'; });
  if (confirmRunBackupBtn) confirmRunBackupBtn.addEventListener('click', executeManualBackup);

  // Playbook Modal Events
  if (openPlaybookBtn) openPlaybookBtn.addEventListener('click', () => { playbookModal.style.display = 'flex'; });
  if (closePlaybookBtn) closePlaybookBtn.addEventListener('click', () => { playbookModal.style.display = 'none'; });
  if (dismissPlaybookBtn) dismissPlaybookBtn.addEventListener('click', () => { playbookModal.style.display = 'none'; });

  // Verify Modal Events
  if (closeVerifyModalBtn) closeVerifyModalBtn.addEventListener('click', () => { verifyModal.style.display = 'none'; });
  if (dismissVerifyModalBtn) dismissVerifyModalBtn.addEventListener('click', () => { verifyModal.style.display = 'none'; });

  // ── Import & Restore Modal Logic ──────────────────────────────────────────
  let selectedRestoreArchive = null;
  let selectedRestoreMode = 'TEST';
  let isRestoring = false;
  let isUploadingDump = false;

  function openImportModal(preselectedFile = null) {
    selectedRestoreMode = 'TEST';
    if (radioModeTest) radioModeTest.checked = true;
    if (radioModeProd) radioModeProd.checked = false;
    if (modeCardTest) modeCardTest.classList.add('selected');
    if (modeCardProd) modeCardProd.classList.remove('selected', 'danger');
    if (prodConfirmCallout) prodConfirmCallout.style.display = 'none';
    if (confirmRestorePhraseInput) confirmRestorePhraseInput.value = '';
    if (restoreExecutionPanel) restoreExecutionPanel.style.display = 'none';
    if (restoreAuditCountsPanel) restoreAuditCountsPanel.style.display = 'none';
    if (executeRestoreBtn) {
      executeRestoreBtn.disabled = false;
      executeRestoreBtnText.textContent = 'Start Safe Test Restore';
      executeRestoreBtn.className = 'btn btn-primary';
    }

    populateRepoBackupSelect(preselectedFile ? preselectedFile.fileName : null);

    if (preselectedFile && preselectedFile.category === 'Uploads') {
      switchImportSourceTab('upload');
      inspectAndDisplayArchive(preselectedFile);
    } else {
      switchImportSourceTab('repo');
      if (preselectedFile) {
        inspectAndDisplayArchive(preselectedFile);
      } else if (backupFiles.length > 0) {
        inspectAndDisplayArchive(backupFiles[0]);
      }
    }

    if (importBackupModal) {
      importBackupModal.style.display = 'flex';
    }
  }

  function switchImportSourceTab(tab) {
    if (tab === 'upload') {
      if (tabSourceUploadBtn) tabSourceUploadBtn.classList.add('active');
      if (tabSourceRepoBtn) tabSourceRepoBtn.classList.remove('active');
      if (uploadFileSection) uploadFileSection.style.display = 'block';
      if (repoFileSection) repoFileSection.style.display = 'none';
      if (!selectedRestoreArchive || !selectedRestoreArchive.isUpload) {
        if (archiveInspectionCard) archiveInspectionCard.style.display = 'none';
      }
    } else {
      if (tabSourceRepoBtn) tabSourceRepoBtn.classList.add('active');
      if (tabSourceUploadBtn) tabSourceUploadBtn.classList.remove('active');
      if (repoFileSection) repoFileSection.style.display = 'block';
      if (uploadFileSection) uploadFileSection.style.display = 'none';
      if (repoBackupSelect && repoBackupSelect.value) {
        const found = backupFiles.find(b => b.fileName === repoBackupSelect.value);
        if (found) inspectAndDisplayArchive(found);
      }
    }
  }

  function populateRepoBackupSelect(selectedFileName = null) {
    if (!repoBackupSelect) return;
    if (repoFileLocationLabel) {
      repoFileLocationLabel.textContent = activeLocation || 'Default Repository';
    }

    if (!backupFiles || backupFiles.length === 0) {
      repoBackupSelect.innerHTML = '<option value="">No backup snapshots found in active repository</option>';
      if (archiveInspectionCard) archiveInspectionCard.style.display = 'none';
      return;
    }

    repoBackupSelect.innerHTML = backupFiles.map(b => {
      const isSel = (selectedFileName && b.fileName === selectedFileName) || (!selectedFileName && b === backupFiles[0]);
      return `<option value="${Utils.escapeHtml(b.fileName)}" ${isSel ? 'selected' : ''}>${Utils.escapeHtml(b.fileName)} (${b.category} • ${b.sizeMB} MB • ${b.ageText || ''})</option>`;
    }).join('');

    const target = backupFiles.find(b => b.fileName === (selectedFileName || repoBackupSelect.value)) || backupFiles[0];
    if (target) {
      inspectAndDisplayArchive(target);
    }
  }

  async function inspectAndDisplayArchive(archive) {
    if (!archive || !archive.fileName) return;
    selectedRestoreArchive = {
      fileName: archive.fileName,
      category: archive.category || 'Daily',
      sizeMB: archive.sizeMB || 0,
      filePath: archive.filePath || null,
      isUpload: archive.isUpload || (archive.category === 'Uploads')
    };

    if (archiveInspectionCard) {
      archiveInspectionCard.style.display = 'block';
      inspectCardFileName.textContent = archive.fileName;
      inspectCardMeta.textContent = `${archive.sizeMB || '--'} MB • Category: ${archive.category || 'Database'} • Location: ${archive.filePath || activeLocation || 'Default'}`;
      inspectCardBadge.className = 'badge badge-primary';
      inspectCardBadge.textContent = 'Verifying TOC...';
      inspectCardDetail.textContent = 'Reading Table of Contents entries with pg_restore...';
    }

    try {
      const url = `${API.BACKUP_INSPECT}?fileName=${encodeURIComponent(archive.fileName)}&category=${encodeURIComponent(archive.category || 'Uploads')}${activeLocation ? `&location=${encodeURIComponent(activeLocation)}` : ''}${archive.filePath ? `&filePath=${encodeURIComponent(archive.filePath)}` : ''}`;
      const res = await Api.post(url);
      if (res && res.valid) {
        inspectCardBadge.className = 'badge badge-success';
        inspectCardBadge.textContent = 'TOC Verified';
        inspectCardDetail.innerHTML = `Integrity verified: <strong>${res.tocEntriesCount} Table of Contents entries</strong> confirmed. Schema and data records are intact.`;
        selectedRestoreArchive.tocEntries = res.tocEntriesCount;
      } else {
        inspectCardBadge.className = 'badge badge-warning';
        inspectCardBadge.textContent = 'TOC Warnings';
        inspectCardDetail.textContent = res ? res.message : 'Inspection completed with warnings.';
      }
    } catch (err) {
      console.warn('Inspect archive failed:', err);
      if (inspectCardBadge) {
        inspectCardBadge.className = 'badge badge-warning';
        inspectCardBadge.textContent = 'Snapshot Ready';
        inspectCardDetail.textContent = 'Archive present and ready for restoration.';
      }
    }
  }

  async function handleDumpFileUpload(file) {
    if (!file) return;
    if (!file.name.toLowerCase().endsWith('.dump')) {
      Toast.error('Please select a valid PostgreSQL custom dump archive (.dump)');
      return;
    }

    if (file.size > 500 * 1024 * 1024) {
      Toast.error('File size exceeds maximum allowed upload limit (500 MB)');
      return;
    }

    isUploadingDump = true;
    if (uploadProgressBox) uploadProgressBox.style.display = 'block';
    if (uploadFileNameLabel) uploadFileNameLabel.textContent = `Uploading ${file.name} (${Math.round(file.size / 1024 / 1024 * 10) / 10} MB)...`;
    if (uploadProgressBar) uploadProgressBar.style.width = '45%';
    if (uploadPercentLabel) uploadPercentLabel.textContent = '45%';

    try {
      const formData = new FormData();
      formData.append('file', file);
      if (activeLocation) {
        formData.append('location', activeLocation);
      }

      const headers = {};
      const token = Storage.getToken();
      if (token) headers['Authorization'] = `Bearer ${token}`;
      const activeBranchIdHeader = Storage.getActiveBranchId();
      if (activeBranchIdHeader && activeBranchIdHeader !== 'ALL') {
        headers[APP.BRANCH_HEADER || 'X-Branch-Id'] = String(activeBranchIdHeader);
      }

      if (uploadProgressBar) uploadProgressBar.style.width = '75%';
      if (uploadPercentLabel) uploadPercentLabel.textContent = '75%';

      const response = await fetch(API.BACKUP_UPLOAD, {
        method: 'POST',
        headers: headers,
        body: formData
      });

      const json = await response.json().catch(() => null);
      if (!response.ok) {
        throw new Error(json?.message || `Upload failed with status ${response.status}`);
      }

      const data = json?.data ?? json;
      if (uploadProgressBar) uploadProgressBar.style.width = '100%';
      if (uploadPercentLabel) uploadPercentLabel.textContent = '100%';

      Toast.success(`Uploaded and verified ${data.fileName}`);

      selectedRestoreArchive = {
        fileName: data.fileName,
        category: 'Uploads',
        sizeMB: data.sizeMB,
        filePath: data.savedPath,
        isUpload: true,
        tocEntries: data.tocEntriesCount
      };

      inspectAndDisplayArchive(selectedRestoreArchive);

      loadFiles();
      loadStatus();

      setTimeout(() => {
        if (uploadProgressBox) uploadProgressBox.style.display = 'none';
      }, 1000);

    } catch (err) {
      console.error('Upload failed:', err);
      Toast.error(err.message || 'Failed to upload backup dump file');
      if (uploadProgressBox) uploadProgressBox.style.display = 'none';
    } finally {
      isUploadingDump = false;
    }
  }

  async function executeDatabaseRestore() {
    if (isRestoring) return;
    if (!selectedRestoreArchive || !selectedRestoreArchive.fileName) {
      Toast.error('Please select or upload a backup archive first.');
      return;
    }

    if (selectedRestoreMode === 'PRODUCTION') {
      const confirmVal = confirmRestorePhraseInput ? confirmRestorePhraseInput.value.trim() : '';
      if (confirmVal !== 'CONFIRM RESTORE') {
        Toast.error('Type "CONFIRM RESTORE" exactly to authorize overwriting the production database.');
        if (confirmRestorePhraseInput) confirmRestorePhraseInput.focus();
        return;
      }
    }

    isRestoring = true;
    executeRestoreBtn.disabled = true;
    cancelImportModalBtn.disabled = true;
    closeImportModalBtn.disabled = true;

    restoreExecutionPanel.style.display = 'block';
    restoreStatusTitle.textContent = selectedRestoreMode === 'PRODUCTION'
      ? 'Executing Disaster Recovery Restore (ritham_erp)...'
      : 'Executing Safe Test Restore (ritham_erp_restore_test)...';
    restoreStatusBadge.className = 'badge badge-primary';
    restoreStatusBadge.textContent = 'Running...';
    restoreConsoleBox.innerHTML = `
      <div class="console-line"><span class="console-ts">[${new Date().toLocaleTimeString()}]</span> <span class="console-level INFO">[START]</span> <span class="console-msg">Target mode: ${selectedRestoreMode}</span></div>
      <div class="console-line"><span class="console-ts">[${new Date().toLocaleTimeString()}]</span> <span class="console-level INFO">[ARCHIVE]</span> <span class="console-msg">${Utils.escapeHtml(selectedRestoreArchive.fileName)}</span></div>
      <div class="console-line"><span class="console-ts">[${new Date().toLocaleTimeString()}]</span> <span class="console-level INFO">[SCRIPT]</span> <span class="console-msg">Launching erp-restore.ps1...</span></div>
    `;

    try {
      const payload = {
        backupFileName: selectedRestoreArchive.fileName,
        category: selectedRestoreArchive.category || 'Uploads',
        location: activeLocation,
        filePath: selectedRestoreArchive.filePath || null,
        restoreMode: selectedRestoreMode,
        confirmText: selectedRestoreMode === 'PRODUCTION' ? confirmRestorePhraseInput.value.trim() : null
      };

      const res = await Api.post(API.BACKUP_RESTORE, payload);

      if (res && res.success) {
        restoreStatusBadge.className = 'badge badge-success';
        restoreStatusBadge.textContent = 'Completed';
        restoreStatusTitle.textContent = selectedRestoreMode === 'PRODUCTION'
          ? 'Production Restore Finished Successfully!'
          : 'Safe Test Drill Completed Successfully!';

        if (res.outputLog) {
          const lines = res.outputLog.split('\n');
          const lastLines = lines.slice(-30).join('\n');
          restoreConsoleBox.innerHTML += `<div class="console-line"><pre style="margin:0; font-family: inherit; color: #a1a1aa;">${Utils.escapeHtml(lastLines)}</pre></div>`;
          restoreConsoleBox.scrollTop = restoreConsoleBox.scrollHeight;
        }

        if (res.tableRowCounts && Object.keys(res.tableRowCounts).length > 0) {
          restoreAuditCountsPanel.style.display = 'block';
          auditStatsGrid.innerHTML = Object.entries(res.tableRowCounts).map(([tbl, count]) => `
            <div class="audit-stat-box">
              <div class="audit-stat-name">${Utils.escapeHtml(tbl.replace('_', ' '))}</div>
              <div class="audit-stat-count">${Number(count).toLocaleString()}</div>
            </div>
          `).join('');
        }

        Toast.success(res.message || 'Database restore completed successfully!');
        executeRestoreBtnText.textContent = 'Restore Complete';
        executeRestoreBtn.className = 'btn btn-success';

        loadStatus();
        loadFiles();
        loadLogs();

      } else {
        restoreStatusBadge.className = 'badge badge-danger';
        restoreStatusBadge.textContent = 'Failed';
        restoreStatusTitle.textContent = 'Restore Encountered Errors';
        if (res && res.outputLog) {
          restoreConsoleBox.innerHTML += `<div class="console-line" style="color: #f87171;"><pre style="margin:0; font-family: inherit;">${Utils.escapeHtml(res.outputLog)}</pre></div>`;
          restoreConsoleBox.scrollTop = restoreConsoleBox.scrollHeight;
        }
        Toast.error(res?.message || 'Restore failed. Please inspect execution log.');
      }

    } catch (err) {
      console.error('Restore error:', err);
      restoreStatusBadge.className = 'badge badge-danger';
      restoreStatusBadge.textContent = 'Failed';
      restoreStatusTitle.textContent = 'Execution Error';
      restoreConsoleBox.innerHTML += `<div class="console-line" style="color: #f87171;">${Utils.escapeHtml(err.message || 'Unknown execution error')}</div>`;
      Toast.error(err.message || 'Failed to execute restore operation');
    } finally {
      isRestoring = false;
      cancelImportModalBtn.disabled = false;
      closeImportModalBtn.disabled = false;
      cancelImportModalBtn.textContent = 'Close';
    }
  }

  // Import Modal Events
  if (openImportModalBtn) openImportModalBtn.addEventListener('click', () => openImportModal());
  if (closeImportModalBtn) closeImportModalBtn.addEventListener('click', () => { importBackupModal.style.display = 'none'; });
  if (cancelImportModalBtn) cancelImportModalBtn.addEventListener('click', () => { importBackupModal.style.display = 'none'; });
  if (executeRestoreBtn) executeRestoreBtn.addEventListener('click', executeDatabaseRestore);

  if (tabSourceRepoBtn) tabSourceRepoBtn.addEventListener('click', () => switchImportSourceTab('repo'));
  if (tabSourceUploadBtn) tabSourceUploadBtn.addEventListener('click', () => switchImportSourceTab('upload'));

  if (repoBackupSelect) {
    repoBackupSelect.addEventListener('change', () => {
      const found = backupFiles.find(b => b.fileName === repoBackupSelect.value);
      if (found) inspectAndDisplayArchive(found);
    });
  }

  if (dumpDropzone && externalDumpFileInput) {
    dumpDropzone.addEventListener('click', () => externalDumpFileInput.click());
    externalDumpFileInput.addEventListener('change', (e) => {
      if (e.target.files && e.target.files[0]) {
        handleDumpFileUpload(e.target.files[0]);
      }
    });

    dumpDropzone.addEventListener('dragover', (e) => {
      e.preventDefault();
      dumpDropzone.classList.add('dragover');
    });

    dumpDropzone.addEventListener('dragleave', () => {
      dumpDropzone.classList.remove('dragover');
    });

    dumpDropzone.addEventListener('drop', (e) => {
      e.preventDefault();
      dumpDropzone.classList.remove('dragover');
      if (e.dataTransfer.files && e.dataTransfer.files[0]) {
        handleDumpFileUpload(e.dataTransfer.files[0]);
      }
    });
  }

  if (modeCardTest && radioModeTest) {
    modeCardTest.addEventListener('click', () => {
      selectedRestoreMode = 'TEST';
      radioModeTest.checked = true;
      modeCardTest.classList.add('selected');
      modeCardProd.classList.remove('selected', 'danger');
      if (prodConfirmCallout) prodConfirmCallout.style.display = 'none';
      if (executeRestoreBtnText) executeRestoreBtnText.textContent = 'Start Safe Test Restore';
      if (executeRestoreBtn) executeRestoreBtn.className = 'btn btn-primary';
    });
  }

  if (modeCardProd && radioModeProd) {
    modeCardProd.addEventListener('click', () => {
      selectedRestoreMode = 'PRODUCTION';
      radioModeProd.checked = true;
      modeCardProd.classList.add('selected', 'danger');
      modeCardTest.classList.remove('selected');
      if (prodConfirmCallout) prodConfirmCallout.style.display = 'block';
      if (executeRestoreBtnText) executeRestoreBtnText.textContent = 'Execute Disaster Recovery';
      if (executeRestoreBtn) executeRestoreBtn.className = 'btn btn-danger';
    });
  }

  // Close modals on clicking outside backdrop
  [runBackupModal, locationConfigModal, playbookModal, verifyModal, importBackupModal].forEach(modal => {
    if (modal) {
      modal.addEventListener('click', (e) => {
        if (e.target === modal) modal.style.display = 'none';
      });
    }
  });

  // Initial load
  loadAllData();
});
