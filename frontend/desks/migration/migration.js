/**
 * Ritham ERP — Historical Data Migration & OCR Desk Controller
 */

'use strict';

document.addEventListener('DOMContentLoaded', () => {
  // Guard access: Admin or Operations Manager
  if (!Router.protect([ROLES.ADMIN, ROLES.OPERATIONS_MANAGER])) return;

  // Initialize Shell
  Header.init({
    title: 'Historical Data Migration & OCR',
    subtitle: 'Digitize legacy paper order slips into active ERP records',
  });
  Sidebar.init({ activePage: 'migration' });

  // Safe HTML escaper
  function escapeHtml(str) {
    if (!str && str !== 0) return '';
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }

  // ── State ─────────────────────────────────────────────────────────────────
  let batches = [];
  let activeBatchId = null;
  let branches = [];
  let currentDocuments = [];
  let currentFilterStatus = '';
  let searchQuery = '';
  let currentPage = 0;
  const pageSize = 50;
  let totalElements = 0;
  let totalPages = 0;
  const collapsedGroupKeys = new Set();
  let allGroupsCollapsed = false;

  // Review modal state
  let currentDocInReview = null;
  let currentBlobUrl = null;
  let zoomLevel = 1.0;
  let matchedCustomerMobile = null;
  const currentlyScanningDocIds = new Set();

  // ── Garment Measurement Templates ─────────────────────────────────────────
  // Mirrors the canonical key lists in ExtractionService and measurement.html.
    const MEASUREMENT_TEMPLATES = {
    BLOUSE: [
      { key: 'LTH',    label: 'LTH (Length)' },
      { key: 'SHO',    label: 'SHO (Shoulder)' },
      { key: 'HS',     label: 'H.S. (Half Shldr)' },
      { key: 'HL',     label: 'H.L. (Hand Lth)' },
      { key: 'HLO',    label: 'H.LO (Hand Loose)' },
      { key: 'AK',     label: 'AK (Armhole)' },
      { key: 'AM',     label: 'AM (Arm)' },
      { key: 'BN',     label: 'BN (Back Neck)' },
      { key: 'FN',     label: 'FN (Front Neck)' },
      { key: 'DP1_1',  label: 'DP-1 (Dart Pt 1)' },
      { key: 'DP1_2',  label: 'DP-1 (Dart Pt 2)' },
      { key: 'B1',     label: 'B-1 (Upper Bust)' },
      { key: 'B2',     label: 'B-2 (Full Bust)' },
      { key: 'B3',     label: 'B-3 (Under Bust)' },
      { key: 'F_HOOK', label: 'F.HOOK (Front Hook)' },
      { key: 'B_HOOK', label: 'B.HOOK (Back Hook)' },
      { key: 'LINING', label: 'LINING (Lining)' },
      { key: 'AV',     label: 'AV. (Aari / Work)' },
      { key: 'SARI',   label: 'SARI (Saree Notes)' },
    ],
    // CHUDI template: plain canonical keys matching ExtractionService.CHUDI_KEYS
    // SL_1/SL_2 preserve the two separate sleeve-length rows on the printed form.
    // L_1/L_2 preserve the two separate length rows.
    CHUDI: [
      { key: 'FN',    label: 'F.N. (Front Neck)' },
      { key: 'BN',    label: 'B.N. (Back Neck)' },
      { key: 'HB',    label: 'H.B. (High Bust)' },
      { key: 'L_1',   label: 'L (Top Length)' },
      { key: 'SS',    label: 'SS (Side Slit)' },
      { key: 'SL_1',  label: 'SL 1 (Sleeve Lth)' },
      { key: 'SL_2',  label: 'SL 2 (Sleeve Loose)' },
      { key: 'AM',    label: 'AM (Arm)' },
      { key: 'B',     label: 'B (Bust)' },
      { key: 'H',     label: 'H (Hip)' },
      { key: 'TS',    label: 'T.S. (Top Slit)' },
      { key: 'PL',    label: 'PL. (Pant Length)' },
      { key: 'S',     label: 'S. (Seat)' },
      { key: 'L_2',   label: 'L. (Leg Loose)' },
      { key: 'SCUT',  label: 'S.CUT (Side Cut)' },
      { key: 'LNG',   label: 'LNG (Lining)' },
      { key: 'SHALL', label: 'SHALL (Shawl)' },
      { key: 'BD',    label: 'B.D. (Bottom Design)' },
    ],
  };
  // Alias CHURIDAR → CHUDI template
  MEASUREMENT_TEMPLATES.CHURIDAR = MEASUREMENT_TEMPLATES.CHUDI;

  // ── DOM Element Cache ─────────────────────────────────────────────────────
  // Header / KPIs
  const refreshMigrationBtn  = document.getElementById('refreshMigrationBtn');
  const btnHeaderUpload      = document.getElementById('btnHeaderUpload');
  const kpiTotalDocs         = document.getElementById('kpiTotalDocs');
  const kpiBatchesCount      = document.getElementById('kpiBatchesCount');
  const kpiOcrCompleted      = document.getElementById('kpiOcrCompleted');
  const kpiNeedsReview       = document.getElementById('kpiNeedsReview');
  const kpiVerified          = document.getElementById('kpiVerified');
  const kpiImported          = document.getElementById('kpiImported');
  const kpiProgressFill      = document.getElementById('kpiProgressFill');

  // Actions Bar & Details
  const batchDetailsPill     = document.getElementById('batchDetailsPill');
  const pillTotalDocs        = document.getElementById('pillTotalDocs');
  const pillProgress         = document.getElementById('pillProgress');
  const btnRunBatchOcr       = document.getElementById('btnRunBatchOcr');
  const btnExtractBatch      = document.getElementById('btnExtractBatch');
  const btnBulkImport        = document.getElementById('btnBulkImport');
  const btnClearAllMigration = document.getElementById('btnClearAllMigration');

  // Upload Zone
  const uploadZone           = document.getElementById('uploadZone');
  const fileInput            = document.getElementById('fileInput');
  const browseFilesBtn       = document.getElementById('browseFilesBtn');
  const uploadProgressContainer = document.getElementById('uploadProgressContainer');
  const uploadStatusText     = document.getElementById('uploadStatusText');
  const uploadPercentText    = document.getElementById('uploadPercentText');
  const uploadProgressBar    = document.getElementById('uploadProgressBar');

  // Camera Scanner Elements & State
  const btnCameraScan             = document.getElementById('btnCameraScan');
  const cameraScanModal           = document.getElementById('cameraScanModal');
  const closeCameraModalBtn       = document.getElementById('closeCameraModalBtn');
  const cameraVideo               = document.getElementById('cameraVideo');
  const cameraDeviceSelect        = document.getElementById('cameraDeviceSelect');
  const cameraCaptureCanvas       = document.getElementById('cameraCaptureCanvas');
  const cameraShutterFlash        = document.getElementById('cameraShutterFlash');
  const cameraResBadge            = document.getElementById('cameraResBadge');
  const cameraQueueHUD            = document.getElementById('cameraQueueHUD');
  const hudQueuedCount            = document.getElementById('hudQueuedCount');
  const hudProcessingCount        = document.getElementById('hudProcessingCount');
  const hudWaitingCount           = document.getElementById('hudWaitingCount');
  const cameraProcessingOverlay   = document.getElementById('cameraProcessingOverlay');
  const cameraProcessingMsg       = document.getElementById('cameraProcessingMsg');
  const btnScanAndProcessNow      = document.getElementById('btnScanAndProcessNow');
  const btnQueueBackgroundScan    = document.getElementById('btnQueueBackgroundScan');
  const btnDoneSnapping           = document.getElementById('btnDoneSnapping');
  const btnToggleTorch            = document.getElementById('btnToggleTorch');

  let activeCameraStream          = null;
  let isTorchOn                   = false;
  let cameraBackgroundQueue       = [];
  let isQueueWorkerRunning        = false;
  let queueStats                  = { queued: 0, processing: 0, waitingForApproval: 0 };

  // Pipeline Filters & Search
  const statusFilterPills    = document.getElementById('statusFilterPills');
  const docSearchInput       = document.getElementById('docSearchInput');
  const documentsTableBody   = document.getElementById('documentsTableBody');
  const paginationBar        = document.getElementById('paginationBar');
  const paginationInfo       = document.getElementById('paginationInfo');
  const prevPageBtn          = document.getElementById('prevPageBtn');
  const nextPageBtn          = document.getElementById('nextPageBtn');
  const currentPageNum       = document.getElementById('currentPageNum');

  // Review Split-Screen Modal
  const reviewModal          = document.getElementById('reviewModal');
  const reviewModalTitle     = document.getElementById('reviewModalTitle');
  const reviewDocStatusBadge = document.getElementById('reviewDocStatusBadge');
  const reviewDocOcrConf     = document.getElementById('reviewDocOcrConf');
  const reviewDocOcrConf2    = document.getElementById('reviewDocOcrConf2');
  const reviewGarmentBadge   = document.getElementById('reviewGarmentBadge');
  const reviewMeasGarmentLabel = document.getElementById('reviewMeasGarmentLabel');
  const closeReviewModalBtn  = document.getElementById('closeReviewModalBtn');
  const zoomInBtn            = document.getElementById('zoomInBtn');
  const zoomOutBtn           = document.getElementById('zoomOutBtn');
  const resetZoomBtn         = document.getElementById('resetZoomBtn');
  const toggleRawOcrBtn      = document.getElementById('toggleRawOcrBtn');
  const reviewDocImage       = document.getElementById('reviewDocImage');
  const reviewDocPdf         = document.getElementById('reviewDocPdf');
  const reviewRawOcrBox      = document.getElementById('reviewRawOcrBox');
  const reviewRawOcrText     = document.getElementById('reviewRawOcrText');
  const viewerLoading        = document.getElementById('viewerLoading');
  const btnCopyJson          = document.getElementById('btnCopyJson');

  // Review Form Fields
  const revCustomerMobile    = document.getElementById('revCustomerMobile');
  const revCustomerName      = document.getElementById('revCustomerName');
  const matchStatusBadge     = document.getElementById('matchStatusBadge');
  const matchCandidatesContainer = document.getElementById('matchCandidatesContainer');
  const matchCandidatesList  = document.getElementById('matchCandidatesList');
  const revOrderDate         = document.getElementById('revOrderDate');
  const revDeliveryDate      = document.getElementById('revDeliveryDate');
  const revErode             = document.getElementById('revErode');
  const revCloth             = document.getElementById('revCloth');
  const revGarmentType       = document.getElementById('revGarmentType');
  const revGarmentTypeBadge  = document.getElementById('revGarmentTypeBadge');
  const toggleProcessedImgBtn = document.getElementById('toggleProcessedImgBtn');
  const toggleProcessedImgText = document.getElementById('toggleProcessedImgText');
  let isViewingProcessedImage = false;
  const revLining            = document.getElementById('revLining');
  const measurementsEditGrid = document.getElementById('measurementsEditGrid');
  const btnAddMeasurementRow = document.getElementById('btnAddMeasurementRow');
  const revTotalAmount       = document.getElementById('revTotalAmount');
  const revAdvanceAmount     = document.getElementById('revAdvanceAmount');
  const revBalanceAmount     = document.getElementById('revBalanceAmount');
  const revUpdateProfileCheck= document.getElementById('revUpdateProfileCheck');
  const revRemarks           = document.getElementById('revRemarks');
  const revJsonPreview       = document.getElementById('revJsonPreview');
  const btnRejectDocument    = document.getElementById('btnRejectDocument');
  const btnReworkDocument    = document.getElementById('btnReworkDocument');
  const btnCancelReview      = document.getElementById('btnCancelReview');
  const btnApproveReview     = document.getElementById('btnApproveReview');
  const btnApproveAndImport  = document.getElementById('btnApproveAndImport');
  const toggleAnnotationsBtn = document.getElementById('toggleAnnotationsBtn');
  const annotationHost       = document.getElementById('annotationHost');

  // Document & Multi-Page PDF Review Navigation
  const reviewPageNavBar        = document.getElementById('reviewPageNavBar');
  const pageNavLabel            = document.getElementById('pageNavLabel');
  const reviewPrevPageBtn       = document.getElementById('reviewPrevPageBtn');
  const reviewNextPageBtn       = document.getElementById('reviewNextPageBtn');
  const reviewPageNavCurrent    = document.getElementById('reviewPageNavCurrent');
  const reviewPageNavTotal      = document.getElementById('reviewPageNavTotal');
  const reviewPagePillsContainer= document.getElementById('reviewPagePillsContainer');
  const reviewDownloadSourcePdf = document.getElementById('reviewDownloadSourcePdf');
  const btnApproveAllPages      = document.getElementById('btnApproveAllPages');
  const btnApproveAndNextPage   = document.getElementById('btnApproveAndNextPage');

  // Review Modal Footer Navigation
  const reviewFooterNav         = document.getElementById('reviewFooterNav');
  const btnPrevDocFooter        = document.getElementById('btnPrevDocFooter');
  const btnNextDocFooter        = document.getElementById('btnNextDocFooter');
  const reviewFooterNavText     = document.getElementById('reviewFooterNavText');

  let siblingPagesInReview = [];

  // ═══════════════════════════════════════════════════════════════════════════
  // ANNOTATION OVERLAY — Image-Adaptive Dynamic Region Engine
  //
  // Rather than using static/common coordinates for every image, this engine:
  // 1. Scans pixel luminance on an offscreen canvas to detect the actual paper slip
  //    boundary (excluding dark wooden tables, desk margins, or camera framing).
  // 2. Analyzes OCR text structure to identify the document layout (e.g. linear
  //    order slip like test_order_slip vs two-column printed tailoring slip).
  // 3. Dynamically projects and fits all region boxes to the detected paper boundaries.
  // ═══════════════════════════════════════════════════════════════════════════

  const SVG_NS = 'http://www.w3.org/2000/svg';

  const AnnotationOverlay = {
    svgEl:          null,
    garmentType:    'CHUDI',
    visible:        true,
    activeRegion:   null,
    currentRegions: null,

    /**
     * Dynamically computes image-specific annotation regions.
     * Analyzes image pixel luminance on an offscreen canvas to detect paper boundaries,
     * and inspects OCR text to select either linear or tailoring-slip layout.
     */
    detectRegions(img, doc, garmentType) {
      const type = garmentType || (revGarmentType ? revGarmentType.value : 'CHUDI');
      const isBlouse = (type || '').toUpperCase() === 'BLOUSE';

      // 0. Check if extractedDataJson has explicit custom region coordinates
      let ext = null;
      if (doc && doc.extractedDataJson) {
        try {
          ext = typeof doc.extractedDataJson === 'string' ? JSON.parse(doc.extractedDataJson) : doc.extractedDataJson;
        } catch (e) {}
      }

      // 1. Analyze image dimensions and orientation
      const nw = (img && img.naturalWidth) || 1000;
      const nh = (img && img.naturalHeight) || 1000;

      // Auto-detect if raw OCR text indicates sideways scan
      const rawText = ((doc && doc.rawOcrText) || (ext && ext.rawOcrText) || '').toUpperCase();
      const textLines = rawText.split('\n').map(l => l.trim()).filter(l => l.length > 0);
      const avgLineLen = textLines.length > 5 ? textLines.reduce((acc, l) => acc + l.length, 0) / textLines.length : 25;
      const isSidewaysText = textLines.length >= 6 && avgLineLen < 8;

      let isLandscape = (currentDocInReview && currentDocInReview._userOrientation)
        ? (currentDocInReview._userOrientation === 'LANDSCAPE')
        : (nw > nh || (ext && ext.orientation === 'LANDSCAPE') || isSidewaysText);

      this.currentIsLandscape = isLandscape;

      // Update orientation badge and Flip to Portrait button in viewer toolbar
      const orientBadge = document.getElementById('docOrientationBadge');
      const btnFlip = document.getElementById('btnFlipPortrait');
      if (orientBadge) {
        orientBadge.style.display = 'inline-flex';
        orientBadge.style.cursor = 'pointer';
        orientBadge.title = 'Click to switch between Portrait and Landscape slip mode';
        orientBadge.textContent = isLandscape ? '• Landscape Slip' : '• Portrait Slip';
        orientBadge.style.background = isLandscape ? 'rgba(245, 158, 11, 0.16)' : 'rgba(99, 102, 241, 0.14)';
        orientBadge.style.color = isLandscape ? '#fbbf24' : '#818cf8';
        orientBadge.style.borderColor = isLandscape ? 'rgba(245, 158, 11, 0.35)' : 'rgba(99, 102, 241, 0.35)';
      }
      if (btnFlip) {
        btnFlip.style.display = 'inline-flex';
        btnFlip.textContent = isLandscape ? '↺ Flip to Portrait' : '↷ Flip to Landscape';
        btnFlip.title = isLandscape ? 'Rotate 90° clockwise and set Portrait' : 'Rotate 90° counter-clockwise and set Landscape';
      }

      // 1. If backend returned dynamic text-anchored regions, use them directly!
      if (ext && ext.regions && Object.keys(ext.regions).length >= 2) {
        return ext.regions;
      }

      // 2. Default paper boundaries: normalized 0-1
      let px = 0, py = 0, pw = 1, ph = 1;

      if (img && img.naturalWidth && img.naturalHeight) {
        try {
          const sampleW = Math.min(300, nw);
          const sampleH = Math.round(nh * (sampleW / nw));
          const canvas = document.createElement('canvas');
          canvas.width = sampleW;
          canvas.height = sampleH;
          const ctx = canvas.getContext('2d', { willReadFrequently: true });
          ctx.drawImage(img, 0, 0, sampleW, sampleH);

          const imgData = ctx.getImageData(0, 0, sampleW, sampleH);
          const data = imgData.data;

          // Row-wise brightness histogram: count rows with > 30% bright pixels (r, g, b > 115)
          const rowBright = new Float32Array(sampleH);
          for (let y = 0; y < sampleH; y++) {
            let count = 0;
            for (let x = 0; x < sampleW; x += 2) {
              const idx = (y * sampleW + x) * 4;
              if (data[idx] > 115 && data[idx + 1] > 115 && data[idx + 2] > 115) count++;
            }
            rowBright[y] = count / (sampleW / 2);
          }

          let minY = -1, maxY = -1;
          for (let y = 0; y < sampleH; y++) {
            if (rowBright[y] > 0.30) {
              if (minY === -1) minY = y;
              maxY = y;
            }
          }

          if (minY !== -1 && maxY > minY) {
            // Col-wise brightness histogram within the detected paper rows
            let minX = -1, maxX = -1;
            const totalY = Math.max(1, Math.floor((maxY - minY) / 2));
            for (let x = 0; x < sampleW; x++) {
              let count = 0;
              for (let y = minY; y <= maxY; y += 2) {
                const idx = (y * sampleW + x) * 4;
                if (data[idx] > 115 && data[idx + 1] > 115 && data[idx + 2] > 115) count++;
              }
              if (count / totalY > 0.30) {
                if (minX === -1) minX = x;
                maxX = x;
              }
            }

            if (minX !== -1 && maxX > minX) {
              const detectedW = maxX - minX;
              const detectedH = maxY - minY;
              if (detectedW > sampleW * 0.25 && detectedH > sampleH * 0.25) {
                px = minX / sampleW;
                py = minY / sampleH;
                pw = detectedW / sampleW;
                ph = detectedH / sampleH;
              }
            }
          }
        } catch (canvasErr) {
          console.debug('Canvas paper boundary detection fallback:', canvasErr);
        }
      }

      const COLOR_BLUE = '#2563eb';
      const COLOR_GREEN = '#16a34a';
      const COLOR_RED = '#dc2626';
      const COLOR_ORANGE = '#ea580c';

      // 3. Standard Tailoring Slip (adaptive to detected paper slip boundaries px, py, pw, ph)
      if (isLandscape) {
        return {
          dateMobileRegion: {
            x: Math.max(0, px + pw * 0.015),
            y: Math.max(0, py + ph * 0.020),
            w: Math.min(1 - px, pw * 0.140),
            h: Math.min(1 - py, ph * 0.350),
            color: COLOR_RED,
            label: 'Phone & Dates',
            fields: ['revOrderDate', 'revDeliveryDate', 'revCustomerMobile']
          },
          garmentRegion: {
            x: Math.max(0, px + pw * 0.015),
            y: Math.max(0, py + ph * 0.380),
            w: Math.min(1 - px, pw * 0.140),
            h: Math.min(1 - py, ph * 0.170),
            color: COLOR_GREEN,
            label: 'Garment Type & Cloth',
            fields: ['revGarmentType', 'revCloth']
          },
          customerRegion: {
            x: Math.max(0, px + pw * 0.015),
            y: Math.max(0, py + ph * 0.560),
            w: Math.min(1 - px, pw * 0.140),
            h: Math.min(1 - py, ph * 0.400),
            color: COLOR_BLUE,
            label: 'Customer Info (Name / Erode)',
            fields: ['revCustomerName', 'revErode']
          },
          measurementRegion: {
            x: Math.max(0, px + pw * 0.165),
            y: Math.max(0, py + ph * 0.020),
            w: Math.min(1 - px, pw * 0.815),
            h: Math.min(1 - py, ph * 0.940),
            color: COLOR_ORANGE,
            label: 'Measurements',
            fields: []
          }
        };
      }

      return {
        customerRegion: {
          x: Math.max(0, px + pw * 0.03),
          y: Math.max(0, py + ph * 0.070),
          w: Math.min(1 - px, pw * 0.42),
          h: Math.min(1 - py, ph * 0.130),
          color: COLOR_BLUE,
          label: 'Customer Info (Name / Erode)',
          fields: ['revCustomerName', 'revErode']
        },
        garmentRegion: {
          x: Math.max(0, px + pw * 0.35),
          y: Math.max(0, py + ph * 0.015),
          w: Math.min(1 - px, pw * 0.30),
          h: Math.min(1 - py, ph * 0.065),
          color: COLOR_GREEN,
          label: 'Garment Type & Cloth',
          fields: ['revGarmentType', 'revCloth']
        },
        dateMobileRegion: {
          x: Math.max(0, px + pw * 0.52),
          y: Math.max(0, py + ph * 0.070),
          w: Math.min(1 - px, pw * 0.44),
          h: Math.min(1 - py, ph * 0.130),
          color: COLOR_RED,
          label: 'Phone & Dates',
          fields: ['revOrderDate', 'revDeliveryDate', 'revCustomerMobile']
        },
        measurementRegion: {
          x: Math.max(0, px + pw * 0.02),
          y: Math.max(0, py + ph * (isBlouse ? 0.210 : 0.215)),
          w: Math.min(1 - px, pw * 0.96),
          h: Math.min(1 - py, ph * 0.760),
          color: COLOR_ORANGE,
          label: 'Measurements',
          fields: []
        }
      };
    },

    /** Create SVG overlay and draw image-adaptive regions. */
    init(garmentType, doc) {
      this.clear();
      if (!annotationHost || !reviewDocImage) return;
      this.garmentType = garmentType || (revGarmentType ? revGarmentType.value : 'CHUDI');
      this.visible     = true;

      const svg = document.createElementNS(SVG_NS, 'svg');
      svg.id = 'annotationSvg';
      svg.setAttribute('class', 'annot-overlay');
      // viewBox 0-1000 gives whole-number pixel-equivalent coordinates
      svg.setAttribute('viewBox', '0 0 1000 1000');
      svg.setAttribute('preserveAspectRatio', 'none');
      annotationHost.appendChild(svg);
      this.svgEl = svg;

      this._buildDefs();
      this.draw(this.garmentType, doc || currentDocInReview);

      // Sync toggle button state
      if (toggleAnnotationsBtn) {
        toggleAnnotationsBtn.classList.remove('annot-off');
        toggleAnnotationsBtn.title = 'Hide region annotations';
      }
    },

    /** Injects SVG filter defs (glow effect for active region). */
    _buildDefs() {
      if (!this.svgEl) return;
      const defs = document.createElementNS(SVG_NS, 'defs');
      const filter = document.createElementNS(SVG_NS, 'filter');
      filter.id = 'annotGlow';
      filter.setAttribute('x', '-20%'); filter.setAttribute('y', '-20%');
      filter.setAttribute('width', '140%'); filter.setAttribute('height', '140%');
      const blur = document.createElementNS(SVG_NS, 'feGaussianBlur');
      blur.setAttribute('in', 'SourceGraphic'); blur.setAttribute('stdDeviation', '5');
      blur.setAttribute('result', 'blur');
      const merge = document.createElementNS(SVG_NS, 'feMerge');
      ['blur','SourceGraphic'].forEach(n => {
        const mn = document.createElementNS(SVG_NS, 'feMergeNode');
        mn.setAttribute('in', n);
        merge.appendChild(mn);
      });
      filter.appendChild(blur); filter.appendChild(merge);
      defs.appendChild(filter);
      this.svgEl.appendChild(defs);
    },

    /** Redraw all region boxes for the current image & garment type. */
    draw(garmentType, doc) {
      if (!this.svgEl) return;
      this.garmentType = garmentType || (revGarmentType ? revGarmentType.value : 'CHUDI');
      const targetDoc = doc || currentDocInReview;
      this.currentRegions = this.detectRegions(reviewDocImage, targetDoc, this.garmentType);

      // Remove existing region groups
      this.svgEl.querySelectorAll('g[data-region]').forEach(g => g.remove());
      for (const [key, region] of Object.entries(this.currentRegions)) {
        this._drawRegion(key, region, false);
      }
    },

    /** Draw one region box with label pill and corner ticks. */
    _drawRegion(key, region, isActive) {
      if (!this.svgEl) return;
      const S = 1000;
      const rx = Math.round(region.x * S), ry = Math.round(region.y * S);
      const rw = Math.round(region.w * S), rh = Math.round(region.h * S);
      const col = region.color;

      const g = document.createElementNS(SVG_NS, 'g');
      g.setAttribute('data-region', key);
      g.setAttribute('class', 'annot-region-group');
      g.style.cursor = 'pointer';

      const title = document.createElementNS(SVG_NS, 'title');
      title.textContent = `Click on image to select/edit ${region.label || key}`;
      g.appendChild(title);

      g.addEventListener('click', (e) => {
        e.stopPropagation();
        handleAnnotationRegionClick(key);
      });

      // ── Main semi-transparent filled rect ─────────────────────────────────
      const rect = document.createElementNS(SVG_NS, 'rect');
      rect.setAttribute('class', 'annot-main-rect');
      rect.setAttribute('x', rx); rect.setAttribute('y', ry);
      rect.setAttribute('width', rw); rect.setAttribute('height', rh);
      rect.setAttribute('rx', 7); rect.setAttribute('ry', 7);
      rect.setAttribute('fill', col);
      rect.setAttribute('fill-opacity', isActive ? '0.28' : '0.11');
      rect.setAttribute('stroke', col);
      rect.setAttribute('stroke-width', isActive ? '4' : '2.5');
      rect.setAttribute('stroke-opacity', isActive ? '1' : '0.75');
      if (isActive) rect.setAttribute('filter', 'url(#annotGlow)');
      g.appendChild(rect);

      // ── Corner tick marks ─────────────────────────────────────────────────
      const T = 14;
      [[rx,ry],[rx+rw-T,ry],[rx,ry+rh-T],[rx+rw-T,ry+rh-T]].forEach(([tx,ty]) => {
        const tk = document.createElementNS(SVG_NS, 'rect');
        tk.setAttribute('x',tx); tk.setAttribute('y',ty);
        tk.setAttribute('width',T); tk.setAttribute('height',T);
        tk.setAttribute('rx', 2);
        tk.setAttribute('fill', col);
        tk.setAttribute('fill-opacity', isActive ? '1' : '0.88');
        g.appendChild(tk);
      });

      // ── Label pill (above the box, or inside top-left if no room) ─────────
      const txt  = region.label;
      const fs   = 20;          // font-size in SVG units (≈ 20/1000 = 2.0% of image)
      const padX = 10, padY = 5;
      const lw   = Math.round(txt.length * fs * 0.54 + padX * 2);
      const lh   = fs + padY * 2;
      // place above rect; if too close to top, place just inside
      let ly   = (ry > lh + 6) ? ry - lh - 5 : ry + 6;
      let lx   = rx;
      // Clamp within SVG boundaries
      lx = Math.max(6, Math.min(lx, 1000 - lw - 6));
      ly = Math.max(6, Math.min(ly, 1000 - lh - 6));

      const lr = document.createElementNS(SVG_NS, 'rect');
      lr.setAttribute('x', lx); lr.setAttribute('y', ly);
      lr.setAttribute('width', lw); lr.setAttribute('height', lh);
      lr.setAttribute('rx', 4);
      lr.setAttribute('fill', col);
      lr.setAttribute('fill-opacity', isActive ? '1' : '0.90');
      g.appendChild(lr);

      const lt = document.createElementNS(SVG_NS, 'text');
      lt.setAttribute('x', lx + padX);
      lt.setAttribute('y', ly + padY + fs * 0.78);
      lt.setAttribute('fill', '#ffffff');
      lt.setAttribute('font-size', fs);
      lt.setAttribute('font-weight', '700');
      lt.setAttribute('font-family', 'Inter,system-ui,sans-serif');
      lt.setAttribute('letter-spacing', '0.04em');
      lt.textContent = txt;
      g.appendChild(lt);

      this.svgEl.appendChild(g);
    },

    /** Highlight one region (bring to top, increase opacity, add glow). */
    highlight(regionKey) {
      if (!this.svgEl || !this.visible || !this.currentRegions) return;

      // Reset all to default state
      for (const key of Object.keys(this.currentRegions)) {
        const g = this.svgEl.querySelector(`g[data-region="${key}"]`);
        if (!g) continue;
        const rect = g.querySelector('.annot-main-rect');
        if (rect) {
          rect.setAttribute('fill-opacity', '0.11');
          rect.setAttribute('stroke-width', '2.5');
          rect.setAttribute('stroke-opacity', '0.75');
          rect.removeAttribute('filter');
        }
        g.querySelectorAll('rect:not(.annot-main-rect)').forEach(t =>
          t.setAttribute('fill-opacity', '0.88'));
        const lr = g.querySelector('rect + rect + rect + rect + rect, rect:nth-child(6)');
        if (lr) lr.setAttribute('fill-opacity', '0.90');
      }

      this.activeRegion = regionKey || null;
      if (!regionKey) return;

      const g = this.svgEl.querySelector(`g[data-region="${regionKey}"]`);
      if (!g) return;

      const rect = g.querySelector('.annot-main-rect');
      if (rect) {
        rect.setAttribute('fill-opacity', '0.28');
        rect.setAttribute('stroke-width', '4');
        rect.setAttribute('stroke-opacity', '1');
        rect.setAttribute('filter', 'url(#annotGlow)');
      }
      g.querySelectorAll('rect:not(.annot-main-rect)').forEach(t =>
        t.setAttribute('fill-opacity', '1'));
      // Render this group on top
      this.svgEl.appendChild(g);
    },

    /** Find which annotation region a form field ID maps to. */
    getRegionForField(fieldId) {
      if (!this.currentRegions) return null;
      for (const [key, r] of Object.entries(this.currentRegions)) {
        if (r.fields && r.fields.includes(fieldId)) return key;
      }
      return null;
    },

    /** Show or hide the SVG overlay. */
    toggle() {
      if (!this.svgEl) return;
      this.visible = !this.visible;
      this.svgEl.style.display = this.visible ? '' : 'none';
      if (toggleAnnotationsBtn) {
        toggleAnnotationsBtn.classList.toggle('annot-off', !this.visible);
        toggleAnnotationsBtn.title = this.visible ? 'Hide region annotations' : 'Show region annotations';
      }
    },

    /** Remove SVG from DOM, reset state. */
    clear() {
      const old = document.getElementById('annotationSvg');
      if (old) old.remove();
      this.svgEl = null;
      this.activeRegion = null;
      this.currentRegions = null;
      this.visible = true;
    }
  };

  /**
   * Handles user clicking an annotation region on the document scan.
   * Directly allows selecting / editing Garment Type, Customer, Date, Measurements, or Billing.
   */
  function handleAnnotationRegionClick(regionKey) {
    AnnotationOverlay.highlight(regionKey);

    if (regionKey === 'garmentRegion') {
      if (revGarmentType) {
        revGarmentType.focus();
        revGarmentType.classList.add('field-highlight-pulse');
        setTimeout(() => revGarmentType.classList.remove('field-highlight-pulse'), 1200);
        if (!revGarmentType.disabled && !revGarmentType.value) {
          Toast.info('Garment type could not be determined. Please choose BLOUSE or CHUDI.');
        } else {
          Toast.info(`Garment Type: ${revGarmentType.value || 'None'} ${revGarmentType.disabled ? '(auto-selected from image)' : '(user selected)'}`);
        }
      }
    } else if (regionKey === 'customerRegion') {
      if (revCustomerName) {
        revCustomerName.focus();
        revCustomerName.classList.add('field-highlight-pulse');
        setTimeout(() => revCustomerName.classList.remove('field-highlight-pulse'), 1200);
        revCustomerName.scrollIntoView({ behavior: 'smooth', block: 'center' });
      }
    } else if (regionKey === 'dateMobileRegion') {
      if (revCustomerMobile) {
        revCustomerMobile.focus();
        revCustomerMobile.classList.add('field-highlight-pulse');
        setTimeout(() => revCustomerMobile.classList.remove('field-highlight-pulse'), 1200);
        revCustomerMobile.scrollIntoView({ behavior: 'smooth', block: 'center' });
      }
    } else if (regionKey === 'measurementRegion') {
      const firstInput = measurementsEditGrid?.querySelector('.meas-val-input');
      if (firstInput) {
        firstInput.focus();
        firstInput.scrollIntoView({ behavior: 'smooth', block: 'center' });
      }
    } else if (regionKey === 'billingRegion') {
      if (revTotalAmount) {
        revTotalAmount.focus();
        revTotalAmount.classList.add('field-highlight-pulse');
        setTimeout(() => revTotalAmount.classList.remove('field-highlight-pulse'), 1200);
        revTotalAmount.scrollIntoView({ behavior: 'smooth', block: 'center' });
      }
    }
  }

  function setGarmentType(newType) {
    if (!revGarmentType) return;
    const raw = (newType || '').toUpperCase().trim();
    // Strictly only allow BLOUSE or CHUDI
    const cleanType = raw.includes('BLOUSE') ? 'BLOUSE' : 'CHUDI';
    revGarmentType.value = cleanType;
    revGarmentType.dispatchEvent(new Event('change'));
  }

  function bindFieldHighlight(inputEl, primaryRegionKey, fallbackRegionKey) {
    if (!inputEl) return;
    const doHighlight = () => {
      if (AnnotationOverlay.currentRegions && AnnotationOverlay.currentRegions[primaryRegionKey]) {
        AnnotationOverlay.highlight(primaryRegionKey);
      } else if (fallbackRegionKey) {
        AnnotationOverlay.highlight(fallbackRegionKey);
      }
    };
    inputEl.addEventListener('focus', doHighlight);
    inputEl.addEventListener('click', doHighlight);
  }

  // ── Init Desk ─────────────────────────────────────────────────────────────
  init();

  async function init() {
    setupEventListeners();
    await loadBranches();
    await loadBatches();
    await loadOverallStatistics();
  }

  // ── Event Handlers ────────────────────────────────────────────────────────
  function setupEventListeners() {
    if (refreshMigrationBtn) {
      refreshMigrationBtn.addEventListener('click', async () => {
        await loadBatches();
        await loadOverallStatistics();
        Toast.info('Refreshed migration data');
      });
    }

    if (btnHeaderUpload) {
      btnHeaderUpload.addEventListener('click', () => {
        fileInput.click();
      });
    }

    // Pipeline Action Buttons
    if (btnRunBatchOcr) btnRunBatchOcr.addEventListener('click', handleRunBatchOcr);
    if (btnExtractBatch) btnExtractBatch.addEventListener('click', handleExtractBatch);
    if (btnBulkImport) btnBulkImport.addEventListener('click', handleBulkImportVerified);
    if (btnClearAllMigration) btnClearAllMigration.addEventListener('click', handleClearAllMigrationData);

    // Drag & Drop Upload
    if (uploadZone) {
      uploadZone.addEventListener('click', (e) => {
        if (e.target === fileInput) return;
        // Only trigger if clicking inside uploadZone and not on browse or camera buttons
        if (browseFilesBtn && (e.target === browseFilesBtn || browseFilesBtn.contains(e.target))) return;
        if (btnCameraScan && (e.target === btnCameraScan || btnCameraScan.contains(e.target))) return;
        fileInput.click();
      });

      if (browseFilesBtn) {
        browseFilesBtn.addEventListener('click', (e) => {
          e.stopPropagation();
          fileInput.click();
        });
      }

      if (btnCameraScan) {
        btnCameraScan.addEventListener('click', (e) => {
          e.stopPropagation();
          openCameraModal();
        });
      }

      // Camera Modal Controls
      if (closeCameraModalBtn) closeCameraModalBtn.addEventListener('click', closeCameraModal);
      if (btnDoneSnapping) btnDoneSnapping.addEventListener('click', closeCameraModal);
      if (cameraScanModal) {
        cameraScanModal.addEventListener('click', (e) => {
          if (e.target === cameraScanModal) closeCameraModal();
        });
      }
      if (cameraDeviceSelect) {
        cameraDeviceSelect.addEventListener('change', () => {
          startCameraStream(cameraDeviceSelect.value);
        });
      }
      if (btnToggleTorch) btnToggleTorch.addEventListener('click', toggleCameraTorch);
      if (btnScanAndProcessNow) btnScanAndProcessNow.addEventListener('click', handleScanAndProcessNow);
      if (btnQueueBackgroundScan) btnQueueBackgroundScan.addEventListener('click', handleQueueBackgroundScan);

      ['dragenter', 'dragover'].forEach(eventName => {
        uploadZone.addEventListener(eventName, (e) => {
          e.preventDefault();
          e.stopPropagation();
          uploadZone.classList.add('drag-active');
        }, false);
      });

      uploadZone.addEventListener('dragleave', (e) => {
        e.preventDefault();
        e.stopPropagation();
        uploadZone.classList.remove('drag-active');
      }, false);

      uploadZone.addEventListener('drop', (e) => {
        e.preventDefault();
        e.stopPropagation();
        uploadZone.classList.remove('drag-active');
        const dt = e.dataTransfer;
        const files = dt ? dt.files : null;
        if (files && files.length > 0) {
          handleFilesUpload(files);
        }
      }, false);
    }

    // Global dragover/drop preventer to prevent browser navigating away on stray drops
    window.addEventListener('dragover', (e) => e.preventDefault(), false);
    window.addEventListener('drop', (e) => e.preventDefault(), false);

    if (fileInput) {
      fileInput.addEventListener('change', () => {
        if (fileInput.files && fileInput.files.length > 0) {
          const files = Array.from(fileInput.files);
          fileInput.value = ''; // Reset immediately so re-selecting the same file works
          handleFilesUpload(files);
        }
      });
    }

    // Status Filter Pills
    if (statusFilterPills) {
      statusFilterPills.addEventListener('click', (e) => {
        const pill = e.target.closest('.filter-pill');
        if (!pill) return;
        statusFilterPills.querySelectorAll('.filter-pill').forEach(p => p.classList.remove('active'));
        pill.classList.add('active');
        currentFilterStatus = pill.dataset.status || '';
        currentPage = 0;
        loadBatchDocuments();
      });
    }

    // Search Input
    if (docSearchInput) {
      let debounceTimer;
      docSearchInput.addEventListener('input', (e) => {
        clearTimeout(debounceTimer);
        debounceTimer = setTimeout(() => {
          searchQuery = e.target.value.trim().toLowerCase();
          renderDocumentsTable();
        }, 250);
      });
    }

    // Pagination
    if (prevPageBtn) {
      prevPageBtn.addEventListener('click', () => {
        if (currentPage > 0) {
          currentPage--;
          loadBatchDocuments();
        }
      });
    }
    if (nextPageBtn) {
      nextPageBtn.addEventListener('click', () => {
        if (currentPage < totalPages - 1) {
          currentPage++;
          loadBatchDocuments();
        }
      });
    }

    // Review Modal Controls
    if (closeReviewModalBtn) closeReviewModalBtn.addEventListener('click', closeReviewModal);
    if (btnCancelReview) btnCancelReview.addEventListener('click', closeReviewModal);
    if (reviewModal) {
      reviewModal.addEventListener('click', (e) => {
        if (e.target === reviewModal) closeReviewModal();
      });
    }

    // Zoom Controls
    if (zoomInBtn) zoomInBtn.addEventListener('click', () => adjustZoom(0.25));
    if (zoomOutBtn) zoomOutBtn.addEventListener('click', () => adjustZoom(-0.25));
    if (resetZoomBtn) resetZoomBtn.addEventListener('click', () => resetZoom());

    // Toggle Raw OCR Text
    if (toggleRawOcrBtn) {
      toggleRawOcrBtn.addEventListener('click', () => {
        const isShown = reviewRawOcrBox.style.display !== 'none';
        reviewRawOcrBox.style.display = isShown ? 'none' : 'block';
        toggleRawOcrBtn.textContent = isShown ? 'Toggle Raw OCR Text' : 'Hide Raw OCR Text';
      });
    }

    // Add Custom Measurement Row (user-added, editable key)
    if (btnAddMeasurementRow) {
      btnAddMeasurementRow.addEventListener('click', () => {
        addMeasurementRow('', '', '', false);
        // Focus the newly added key input
        const rows = measurementsEditGrid.querySelectorAll('.measurement-row');
        const lastRow = rows[rows.length - 1];
        lastRow?.querySelector('.meas-key-input')?.focus();
      });
    }

    // Garment type change — re-render measurement template and update badges
    if (revGarmentType) {
      revGarmentType.addEventListener('change', () => {
        if (!currentDocInReview) return;
        const newType = revGarmentType.value;

        if (!newType || newType === 'NEEDS_REVIEW') {
          if (reviewGarmentBadge) reviewGarmentBadge.textContent = 'SELECT REQUIRED';
          if (reviewMeasGarmentLabel) reviewMeasGarmentLabel.textContent = 'SELECT REQUIRED';
          if (revGarmentTypeBadge) {
            revGarmentTypeBadge.innerHTML = '<i class="ri-alert-line"></i> Please Select';
            revGarmentTypeBadge.style.color = '#fbbf24';
            revGarmentTypeBadge.style.background = 'rgba(245,158,11,0.15)';
            revGarmentTypeBadge.style.borderColor = 'rgba(245,158,11,0.4)';
          }
          revGarmentType.style.borderColor = '#f59e0b';
          revGarmentType.style.color = '#fbbf24';
          return;
        }

        // When user manually selects, update styling and badge to "Selected"
        if (!revGarmentType.disabled && revGarmentTypeBadge) {
          revGarmentTypeBadge.innerHTML = '<i class="ri-check-line"></i> Selected';
          revGarmentTypeBadge.style.color = '#34d399';
          revGarmentTypeBadge.style.background = 'rgba(52,211,153,0.12)';
          revGarmentTypeBadge.style.borderColor = 'rgba(52,211,153,0.3)';
          revGarmentType.style.borderColor = '#34d399';
          revGarmentType.style.color = '#e0e7ff';
          revGarmentType.style.boxShadow = 'none';
        }

        // Update the garment badges in left panel header and meas section
        if (reviewGarmentBadge) reviewGarmentBadge.textContent = newType;
        if (reviewMeasGarmentLabel) reviewMeasGarmentLabel.textContent = newType;

        // Capture current values before wiping the grid
        const currentVals = {};
        measurementsEditGrid.querySelectorAll('.measurement-row').forEach(row => {
          const k = row.querySelector('.meas-key-input')?.value?.trim();
          const v = row.querySelector('.meas-val-input')?.value?.trim();
          if (k) currentVals[k] = v || '';
        });
        // Re-render template for the resolved garment
        renderMeasurementTemplate(newType, currentVals);
        // Redraw annotation regions for the new garment type
        AnnotationOverlay.draw(newType, currentDocInReview);
      });
    }

    // Billing Calculation
    if (revTotalAmount) revTotalAmount.addEventListener('input', updateBalanceCalc);
    if (revAdvanceAmount) revAdvanceAmount.addEventListener('input', updateBalanceCalc);

    // JSON Preview: refresh on any customer / order field change
    [revCustomerName, revCustomerMobile, revOrderDate, revDeliveryDate, revErode, revCloth].forEach(el => {
      if (el) el.addEventListener('input', refreshJsonPreview);
    });
    if (revGarmentType) revGarmentType.addEventListener('change', refreshJsonPreview);

    // Click-to-Highlight: Wire field focus / click to highlight its bounding box on image scan
    bindFieldHighlight(revCustomerName, 'field_revCustomerName', 'customerRegion');
    bindFieldHighlight(revErode, 'field_revErode', 'customerRegion');
    bindFieldHighlight(revCustomerMobile, 'field_revCustomerMobile', 'dateMobileRegion');
    bindFieldHighlight(revOrderDate, 'field_revOrderDate', 'dateMobileRegion');
    bindFieldHighlight(revDeliveryDate, 'field_revDeliveryDate', 'dateMobileRegion');
    bindFieldHighlight(revGarmentType, 'field_revGarmentType', 'garmentRegion');
    bindFieldHighlight(revCloth, 'field_revCloth', 'garmentRegion');

    // Copy JSON to Clipboard
    if (btnCopyJson) {
      btnCopyJson.addEventListener('click', () => {
        const text = revJsonPreview ? revJsonPreview.textContent : '{}';
        navigator.clipboard?.writeText(text).then(() => {
          btnCopyJson.textContent = 'Copied!';
          setTimeout(() => { btnCopyJson.innerHTML = `<svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg> Copy JSON`; }, 1800);
        }).catch(() => Toast.info('Copy not supported in this browser'));
      });
    }

    // Annotation overlay toggle
    if (toggleAnnotationsBtn) {
      toggleAnnotationsBtn.addEventListener('click', () => AnnotationOverlay.toggle());
    }

    // Toggle between Original upload and OCR Processed (contrast-enhanced) image
    if (toggleProcessedImgBtn) {
      toggleProcessedImgBtn.addEventListener('click', async () => {
        if (!currentDocInReview) return;
        isViewingProcessedImage = !isViewingProcessedImage;
        const docId = currentDocInReview.id;
        const targetUrl = isViewingProcessedImage
          ? `${API.MIGRATION_DOCUMENT_PROCESSED_IMAGE(docId)}?t=${Date.now()}`
          : `${API.MIGRATION_DOCUMENT_FILE(docId)}?t=${Date.now()}`;

        try {
          const token = Storage.getToken();
          const headers = {};
          if (token) headers['Authorization'] = `Bearer ${token}`;

          const res = await fetch(targetUrl, { headers });
          if (!res.ok) throw new Error(`HTTP ${res.status}`);

          const blob = await res.blob();
          if (currentBlobUrl) URL.revokeObjectURL(currentBlobUrl);
          currentBlobUrl = URL.createObjectURL(blob);

          reviewDocImage.src = currentBlobUrl;
          if (toggleProcessedImgText) {
            toggleProcessedImgText.textContent = isViewingProcessedImage ? 'Original' : 'Processed';
          }
          toggleProcessedImgBtn.classList.toggle('vtb-btn-primary', isViewingProcessedImage);
          Toast.info(isViewingProcessedImage ? 'Viewing OCR Processed (enhanced) view' : 'Viewing Original scan view');
        } catch (err) {
          Toast.error('Could not load image: ' + err.message);
          isViewingProcessedImage = !isViewingProcessedImage;
        }
      });
    }

    // ── Field → Region highlight (focusin delegation) ───────────────────────
    // When the user focuses any right-panel field, highlight the corresponding
    // source region on the left image.
    if (reviewModal) {
      let _highlightClearTimer = null;

      reviewModal.addEventListener('focusin', (e) => {
        const target = e.target;
        if (!target) return;

        let regionKey = null;
        // Measurement value or key inputs → always measurementRegion
        if (target.classList.contains('meas-val-input') ||
            target.classList.contains('meas-key-input')) {
          regionKey = 'measurementRegion';
        } else {
          // data-region attr takes precedence (explicit markup)
          const dataRegion = target.dataset?.region;
          if (dataRegion) {
            regionKey = dataRegion;
          } else if (target.id) {
            regionKey = AnnotationOverlay.getRegionForField(target.id);
          }
        }
        if (regionKey) {
          if (_highlightClearTimer) { clearTimeout(_highlightClearTimer); _highlightClearTimer = null; }
          AnnotationOverlay.highlight(regionKey);
        }
      }, true);

      reviewModal.addEventListener('focusout', () => {
        // Delay slightly to avoid flicker when tabbing between fields in same region
        _highlightClearTimer = setTimeout(() => {
          const active = document.activeElement;
          // If focus stayed inside the review modal, keep the highlight
          if (active && active.closest && active.closest('#reviewModal')) return;
          AnnotationOverlay.highlight(null);
        }, 200);
      }, true);
    }

    // Review Decisions
    if (btnRejectDocument) btnRejectDocument.addEventListener('click', () => submitReview('REJECTED'));
    if (btnReworkDocument) btnReworkDocument.addEventListener('click', () => submitReview('NEEDS_REWORK'));
    if (btnApproveReview) btnApproveReview.addEventListener('click', () => submitReview('APPROVED'));
    if (btnApproveAndImport) btnApproveAndImport.addEventListener('click', () => submitReviewAndImport());

    // Document & Multi-Page Review Navigation Controls
    const handlePrevNav = async () => {
      const nav = getReviewNavigationContext();
      if (nav.prevDocId) {
        await switchReviewPage(nav.prevDocId);
      }
    };
    if (reviewPrevPageBtn) reviewPrevPageBtn.addEventListener('click', handlePrevNav);
    if (btnPrevDocFooter)  btnPrevDocFooter.addEventListener('click', handlePrevNav);

    const handleNextNav = async () => {
      const nav = getReviewNavigationContext();
      if (nav.nextDocId) {
        await switchReviewPage(nav.nextDocId);
      }
    };
    if (reviewNextPageBtn) reviewNextPageBtn.addEventListener('click', handleNextNav);
    if (btnNextDocFooter)  btnNextDocFooter.addEventListener('click', handleNextNav);

    if (reviewPagePillsContainer) {
      reviewPagePillsContainer.addEventListener('click', async (e) => {
        const btn = e.target.closest('.page-pill');
        if (!btn) return;
        const targetId = Number(btn.getAttribute('data-id'));
        if (targetId) {
          await switchReviewPage(targetId);
        }
      });
    }

    if (btnApproveAndNextPage) {
      btnApproveAndNextPage.addEventListener('click', () => submitReviewAndNextPage());
    }

    if (btnApproveAllPages) {
      btnApproveAllPages.addEventListener('click', () => submitApproveAllPages());
    }

    // Keyboard Shortcuts: Alt+Left (Prev), Alt+Right (Next), Space (Camera Snap & Queue), Esc (Close Camera)
    document.addEventListener('keydown', (e) => {
      // Camera Modal Shortcuts
      if (cameraScanModal && cameraScanModal.style.display !== 'none') {
        if (e.key === ' ' || e.code === 'Space') {
          const tag = e.target?.tagName;
          if (tag !== 'INPUT' && tag !== 'SELECT' && tag !== 'BUTTON') {
            e.preventDefault();
            handleQueueBackgroundScan();
          }
        } else if (e.key === 'Escape') {
          e.preventDefault();
          closeCameraModal();
        }
        return;
      }

      // Review Modal Shortcuts
      if (!reviewModal || reviewModal.style.display === 'none') return;
      if (e.altKey && e.key === 'ArrowLeft') {
        e.preventDefault();
        handlePrevNav();
      } else if (e.altKey && e.key === 'ArrowRight') {
        e.preventDefault();
        handleNextNav();
      }
    });

    // Toggle Expand/Collapse All PDF Groups
    const btnToggleExpandAll = document.getElementById('btnToggleExpandAll');
    const textExpandAll = document.getElementById('textExpandAll');
    const iconExpandAll = document.getElementById('iconExpandAll');

    if (btnToggleExpandAll) {
      btnToggleExpandAll.addEventListener('click', () => {
        allGroupsCollapsed = !allGroupsCollapsed;
        const childRows = documentsTableBody.querySelectorAll('.pdf-child-row');
        const groupRows = documentsTableBody.querySelectorAll('.pdf-group-row');

        if (allGroupsCollapsed) {
          childRows.forEach(r => r.classList.add('is-collapsed'));
          groupRows.forEach(r => {
            r.classList.remove('is-expanded');
            const key = r.dataset.groupKey;
            if (key) collapsedGroupKeys.add(key);
          });
          if (textExpandAll) textExpandAll.textContent = 'Expand All';
          if (iconExpandAll) {
            iconExpandAll.innerHTML = '<polyline points="7 6 12 11 17 6"/><polyline points="7 13 12 18 17 13"/>';
          }
        } else {
          childRows.forEach(r => r.classList.remove('is-collapsed'));
          groupRows.forEach(r => {
            r.classList.add('is-expanded');
            const key = r.dataset.groupKey;
            if (key) collapsedGroupKeys.delete(key);
          });
          if (textExpandAll) textExpandAll.textContent = 'Collapse All';
          if (iconExpandAll) {
            iconExpandAll.innerHTML = '<polyline points="7 13 12 18 17 13"/><polyline points="7 6 12 11 17 6"/>';
          }
        }
      });
    }
  }

  // ── Load Branches ─────────────────────────────────────────────────────────
  async function loadBranches() {
    try {
      const res = await Api.get(API.BRANCHES);
      branches = res.data || res || [];
    } catch (err) {
      console.error('Failed to load branches:', err);
    }
  }

  // ── Auto-Batch & Load Batches ─────────────────────────────────────────────
  async function ensureActiveBatch() {
    if (activeBatchId) return activeBatchId;
    try {
      const res = await Api.get(`${API.MIGRATION_BATCHES}?size=1`);
      const pageData = res.data || res || {};
      const list = pageData.content || [];
      if (list.length > 0) {
        activeBatchId = list[0].id;
        batches = list;
        updateBatchDisplay(list[0]);
        return activeBatchId;
      }
      // Silently create default batch in background
      const payload = {
        description: 'Order Slips Archive'
      };
      if (branches && branches.length > 0) {
        payload.branchId = branches[0].id;
      }
      const createRes = await Api.post(API.MIGRATION_BATCHES, payload);
      const newBatch = createRes.data || createRes;
      activeBatchId = newBatch.id;
      batches = [newBatch];
      updateBatchDisplay(newBatch);
      return activeBatchId;
    } catch (err) {
      console.error('Failed to ensure active batch:', err);
      return null;
    }
  }

  async function loadBatches(preferredBatchId = null) {
    try {
      const res = await Api.get(`${API.MIGRATION_BATCHES}?size=50`);
      const pageData = res.data || res || {};
      batches = pageData.content || [];

      if (batches.length === 0) {
        await ensureActiveBatch();
      } else {
        let targetId = preferredBatchId;
        if (!targetId || !batches.some(b => b.id === targetId)) {
          targetId = batches[0].id;
        }
        activeBatchId = targetId;
        const currentBatch = batches.find(b => b.id === activeBatchId) || batches[0];
        updateBatchDisplay(currentBatch);
      }

      if (activeBatchId) {
        currentPage = 0;
        await loadBatchDocuments();
      } else {
        renderEmptyDocumentsTable();
      }
    } catch (err) {
      console.error('Failed to load migration batches:', err);
      renderEmptyDocumentsTable();
    }
  }

  function updateBatchDisplay(batch) {
    if (!batch) {
      if (batchDetailsPill) batchDetailsPill.style.display = 'none';
      return;
    }

    if (batchDetailsPill) {
      batchDetailsPill.style.display = 'inline-flex';
      const total = batch.totalDocuments || 0;
      const imported = batch.importedDocuments || 0;
      if (pillTotalDocs) pillTotalDocs.textContent = total;
      const pct = total > 0 ? Math.round((imported / total) * 100) : 0;
      if (pillProgress) pillProgress.textContent = `${pct}% (${imported}/${total})`;
    }
  }

  let latestStats = null;

  // ── Load Overall Statistics ───────────────────────────────────────────────
  async function loadOverallStatistics() {
    try {
      const res = await Api.get(API.MIGRATION_STATS);
      const stats = res.data || res || {};
      latestStats = stats;

      const totalDocs = stats.totalDocuments || 0;
      const activeBatches = stats.activeBatches || (stats.batchStats ? stats.batchStats.length : 0);
      const needsReview = stats.reviewRequired || 0;
      const verified = stats.verified || 0;
      const imported = stats.imported || 0;

      if (kpiTotalDocs) kpiTotalDocs.textContent = totalDocs.toLocaleString();
      if (kpiBatchesCount) kpiBatchesCount.textContent = `${activeBatches} active batch${activeBatches === 1 ? '' : 'es'}`;
      if (kpiNeedsReview) kpiNeedsReview.textContent = needsReview.toLocaleString();
      if (kpiVerified) kpiVerified.textContent = verified.toLocaleString();
      if (kpiImported) kpiImported.textContent = imported.toLocaleString();

      const pct = totalDocs > 0 ? Math.min(100, Math.round((imported / totalDocs) * 100)) : 0;
      if (kpiProgressFill) kpiProgressFill.style.width = `${pct}%`;

      updateStatusBadges();
    } catch (err) {
      console.warn('Could not load statistics:', err);
    }
  }

  // ── Load Batch Documents ──────────────────────────────────────────────────
  async function loadBatchDocuments() {
    if (!activeBatchId) {
      renderEmptyDocumentsTable();
      return;
    }

    try {
      let url = `${API.MIGRATION_BATCH_DOCUMENTS(activeBatchId)}?page=${currentPage}&size=${pageSize}`;
      if (currentFilterStatus) {
        url += `&reviewStatus=${currentFilterStatus}`;
      }

      documentsTableBody.innerHTML = `
        <tr>
          <td colspan="7" class="text-center py-8 text-muted">
            <div style="display:inline-flex; align-items:center; gap:8px;">
              <span class="spinner" style="width:16px; height:16px; border-width:2px;"></span>
              <span>Loading batch documents...</span>
            </div>
          </td>
        </tr>
      `;

      const res = await Api.get(url);
      const pageData = res.data || res || {};
      currentDocuments = pageData.content || [];
      totalElements = pageData.totalElements || 0;
      totalPages = pageData.totalPages || 1;

      updateStatusBadges();
      renderDocumentsTable();
      updatePaginationControls();
    } catch (err) {
      Toast.error(err.message || 'Failed to load documents');
      documentsTableBody.innerHTML = `
        <tr>
          <td colspan="7" class="text-center py-8 text-danger">
            Failed to load documents for this batch.
          </td>
        </tr>
      `;
    }
  }

  function updateStatusBadges() {
    const badgeAll = document.getElementById('badgeCountAll');
    const badgeReview = document.getElementById('badgeCountReview');
    const badgeVerified = document.getElementById('badgeCountVerified');
    const badgeImported = document.getElementById('badgeCountImported');

    let total = 0, review = 0, verified = 0, imported = 0;
    if (activeBatchId && latestStats && latestStats.batchStats) {
      const bStat = latestStats.batchStats.find(b => b.batchId === activeBatchId);
      if (bStat) {
        total = bStat.totalDocuments || 0;
        review = bStat.needsReviewCount !== undefined ? bStat.needsReviewCount : 0;
        verified = bStat.verifiedCount !== undefined ? bStat.verifiedCount : 0;
        imported = bStat.importedCount !== undefined ? bStat.importedCount : 0;
      }
    } else if (latestStats) {
      total = latestStats.totalDocuments || 0;
      review = latestStats.reviewRequired || 0;
      verified = latestStats.verified || 0;
      imported = latestStats.imported || 0;
    }

    if (badgeAll) badgeAll.textContent = total;
    if (badgeReview) badgeReview.textContent = review;
    if (badgeVerified) badgeVerified.textContent = verified;
    if (badgeImported) badgeImported.textContent = imported;
  }

  function renderEmptyDocumentsTable() {
    if (documentsTableBody) {
      documentsTableBody.innerHTML = `
        <tr>
          <td colspan="7" class="text-center py-8 text-muted">
            <div style="display:flex; flex-direction:column; align-items:center; gap:8px; padding:24px 0;">
              <svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" style="color:var(--text-muted); opacity:0.6;">
                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/>
                <line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/>
              </svg>
              <div style="font-size:14px; font-weight:600; color:var(--text-primary);">No scanned slips uploaded yet</div>
              <div style="font-size:12.5px; color:var(--text-muted); max-width:440px; text-align:center;">
                Drag & drop scanned order slips into the upload zone above or click "Upload Order Slips" to begin processing.
              </div>
            </div>
          </td>
        </tr>
      `;
    }
    if (paginationBar) paginationBar.style.display = 'none';
  }

  function getDocExtractedSummary(doc) {
    if (!doc) return null;
    let data = null;
    const jsonStr = doc.correctedDataJson || doc.extractedDataJson;
    if (jsonStr) {
      try {
        data = typeof jsonStr === 'string' ? JSON.parse(jsonStr) : jsonStr;
      } catch (e) {
        console.warn('Failed to parse extracted JSON for doc', doc.id, e);
      }
    }
    if (!data) return null;

    const customer = data.customer || {};
    const order = data.order || {};
    const measurements = order.measurements || {};
    const measCount = Object.keys(measurements).length;

    return {
      customerName: customer.customerName || '',
      customerMobile: customer.customerMobile || '',
      garmentType: order.garmentType || '',
      totalAmount: order.totalAmount != null ? order.totalAmount : '',
      measurementsCount: measCount,
      measurements: measurements,
      orderDate: order.orderDate || '',
      overallConfidence: data.overallConfidence != null ? data.overallConfidence : null
    };
  }

  // ── Document Grouping & Table Rendering ───────────────────────────────────

  function groupDocumentsBySource(documents) {
    if (!documents || documents.length === 0) return [];
    const groups = [];
    const groupMap = new Map();

    documents.forEach(doc => {
      const isMulti = (doc.totalPages && doc.totalPages > 1) ||
                      (doc.sourceFileName && doc.sourceFileName.toLowerCase().endsWith('.pdf'));
      const key = isMulti
        ? (doc.sourceFilePath || doc.sourceFileName || `pdf_group_${doc.id}`)
        : `single_doc_${doc.id}`;

      if (!groupMap.has(key)) {
        const grp = {
          key,
          isGroup: isMulti,
          sourceFileName: doc.sourceFileName || doc.fileName,
          sourceFilePath: doc.sourceFilePath || doc.filePath,
          totalPages: doc.totalPages || 1,
          items: []
        };
        groupMap.set(key, grp);
        groups.push(grp);
      }
      groupMap.get(key).items.push(doc);
    });

    // Ensure items are ordered by pageNumber ASC
    groups.forEach(g => {
      if (g.isGroup) {
        g.items.sort((a, b) => (a.pageNumber || 1) - (b.pageNumber || 1));
        if (g.items.length > 1) {
          g.totalPages = Math.max(g.totalPages, g.items.length);
        }
      }
    });

    return groups;
  }

  function computeDocFieldCompletion(doc, summary) {
    if (!summary) {
      summary = getDocExtractedSummary(doc);
    }
    if (!summary) {
      return { filled: 0, total: 22, pct: 0, label: '0/22 fields filled (0%)' };
    }

    // 1. Core Fields: Customer Mobile, Name, Garment Type, Order Date
    let filled = 0;
    let total = 4;

    if (summary.customerMobile && String(summary.customerMobile).trim().length >= 5) filled++;
    if (summary.customerName && String(summary.customerName).trim().length > 0) filled++;
    if (summary.orderDate && String(summary.orderDate).trim().length > 0) filled++;
    if (summary.garmentType && summary.garmentType !== 'OTHER' && String(summary.garmentType).trim().length > 0) filled++;

    // Optional fields: total amount
    if (summary.totalAmount !== undefined && summary.totalAmount !== null && summary.totalAmount !== '' && String(summary.totalAmount) !== '0') {
      filled++;
      total++;
    }

    // 2. Tailoring Measurements
    const gType = (summary.garmentType || '').toUpperCase();
    const template = MEASUREMENT_TEMPLATES[gType] || MEASUREMENT_TEMPLATES.CHUDI || [];
    const expectedMeas = template.length > 0 ? template.length : 18;
    total += expectedMeas;

    let filledMeas = 0;
    const measMap = summary.measurements || {};
    Object.keys(measMap).forEach(k => {
      const v = String(measMap[k] || '').trim();
      if (v && v !== '—' && v !== '-' && v !== '--') {
        filledMeas++;
      }
    });
    filled += filledMeas;

    const pct = total > 0 ? Math.round((filled / total) * 100) : 0;
    const boundedPct = Math.max(0, Math.min(100, pct));

    return {
      filled,
      total,
      pct: boundedPct,
      label: `${filled}/${total} fields filled (${boundedPct}%)`
    };
  }

  function computeDocAccuracyPct(doc, summary) {
    return computeDocFieldCompletion(doc, summary).pct;
  }

  function renderDocRowHtml(doc, isChildOfGroup = false, groupKey = '', isGroupCollapsed = false) {
    const isScanning = currentlyScanningDocIds.has(Number(doc.id)) ||
                       currentlyScanningDocIds.has(doc.id) ||
                       doc.ocrStatus === 'OCR_PROCESSING' ||
                       doc.extractionStatus === 'EXTRACTION_PROCESSING';
    const rawStatus = doc.reviewStatus || 'UPLOADED';
    const importStatus = doc.importStatus || '';
    const summary = getDocExtractedSummary(doc);

    // Determine classification
    let stageKey = 'NEEDS_REVIEW';
    let stageLabel = 'Needs Review';
    let badgeClass = 'status-badge-REVIEW_REQUIRED';

    if (isScanning) {
      stageKey = 'SCANNING';
      stageLabel = 'Scanning Slip...';
      badgeClass = 'status-badge-SCANNING';
    } else if (importStatus === 'IMPORTED' || rawStatus === 'IMPORTED') {
      stageKey = 'IMPORTED';
      stageLabel = 'Imported';
      badgeClass = 'status-badge-IMPORTED';
    } else if (rawStatus === 'VERIFIED') {
      stageKey = 'VERIFIED';
      stageLabel = 'Verified';
      badgeClass = 'status-badge-VERIFIED';
    } else {
      stageKey = 'NEEDS_REVIEW';
      stageLabel = 'Needs Review';
      badgeClass = 'status-badge-REVIEW_REQUIRED';
    }

    const comp = computeDocFieldCompletion(doc, summary);
    const accuracyPct = comp.pct;

    // OCR Accuracy / Scanning Loading Bar
    let confChip = '';
    if (isScanning) {
      confChip = `
        <div class="accuracy-bar-container scanning" title="Optical Character Recognition in progress...">
          <div class="accuracy-bar-header">
            <span class="accuracy-bar-value scanning">
              <span class="scanner-spinner"></span>
              <span>Scanning...</span>
            </span>
          </div>
          <div class="accuracy-bar-track">
            <div class="row-scanning-fill"></div>
          </div>
        </div>
      `;
    } else {
      let confClass = 'conf-low';
      if (accuracyPct >= 80) confClass = 'conf-high';
      else if (accuracyPct >= 50) confClass = 'conf-med';

      confChip = `
        <div class="accuracy-bar-container" title="${comp.label}">
          <div class="accuracy-bar-header">
            <span class="accuracy-bar-value ${confClass}">
              <span class="accuracy-bar-dot ${confClass}"></span>
              ${accuracyPct}%
            </span>
            <span style="font-size:11px; color:var(--text-muted); margin-left:auto; font-variant-numeric:tabular-nums;">${comp.filled}/${comp.total}</span>
          </div>
          <div class="accuracy-bar-track">
            <div class="accuracy-bar-fill ${confClass}" style="width: ${accuracyPct}%;"></div>
          </div>
        </div>
      `;
    }

    // Extracted Summary Display
    let summaryHtml = '<span class="text-muted" style="font-size:12px;">Not extracted yet</span>';
    if (isScanning) {
      summaryHtml = `
        <div class="extracting-placeholder" title="Reading handwritten tailoring measurements...">
          <div class="shimmer-line shimmer-line-w80"></div>
          <div class="shimmer-line shimmer-line-w50"></div>
          <span class="text-muted" style="font-size:11px; display:inline-flex; align-items:center; gap:5px; margin-top:2px;">
            <span class="pulse-dot"></span> Reading customer & measurements...
          </span>
        </div>
      `;
    } else if (summary && (summary.customerName || summary.customerMobile || summary.garmentType || summary.measurementsCount > 0)) {
      const cName = summary.customerName || 'Customer Not Named';
      const cMobile = summary.customerMobile ? `• ${summary.customerMobile}` : '';
      const gType = summary.garmentType || 'TAILORING';
      const total = summary.totalAmount ? `₹${summary.totalAmount}` : '';
      const measCount = summary.measurementsCount ? `${summary.measurementsCount} meas` : '';

      summaryHtml = `
        <div class="summary-cell-title">${escapeHtml(cName)} <span style="font-weight:400; font-size:12px; color:var(--text-muted);">${escapeHtml(cMobile)}</span></div>
        <div class="summary-cell-sub">
          <span class="summary-tag">${escapeHtml(gType)}</span>
          ${total ? `<span class="summary-tag" style="color:#34d399;">${escapeHtml(total)}</span>` : ''}
          ${measCount ? `<span class="summary-tag">${escapeHtml(measCount)}</span>` : ''}
        </div>
      `;
    }

    // Actions buttons
    let actionButtons = '';
    if (isScanning) {
      actionButtons = `
        <button class="btn btn-secondary btn-xs" disabled style="opacity:0.75; cursor:wait; display:inline-flex; align-items:center; gap:5px;">
          <span class="scanner-spinner"></span>
          <span>Scanning...</span>
        </button>
      `;
    } else if (stageKey === 'NEEDS_REVIEW') {
      if (doc.measurementExists) {
        actionButtons = `
          <div class="meas-exists-container">
            <span class="meas-exists-badge" title="Customer already has saved measurements in ERP for this garment type">
              <i class="ri-alert-line"></i> Measurement already exists
            </span>
            <div class="meas-exists-actions">
              <button class="btn btn-secondary btn-xs review-btn" data-id="${doc.id}" title="Inspect paper scan & edit measurements">Edit</button>
              <button class="btn btn-warning btn-xs btn-override override-btn" data-id="${doc.id}" title="Override existing ERP measurement with this slip's data">Override</button>
            </div>
          </div>
        `;
      } else {
        actionButtons = `
          <button class="btn btn-warning btn-xs review-btn" data-id="${doc.id}" title="Inspect paper scan & verify tailoring measurements" style="background:#d97706; color:#fff; border-color:#d97706;">
            Review & Verify
          </button>
        `;
      }
    } else if (stageKey === 'VERIFIED') {
      const canImport = comp.pct >= 50;
      const importBtnHtml = canImport
        ? `<button class="btn btn-success btn-xs import-btn" data-id="${doc.id}" title="Import directly to ERP tables">Import</button>`
        : `<button class="btn btn-secondary btn-xs" disabled style="opacity:0.4; cursor:not-allowed;" title="Cannot process: only ${comp.pct}% fields filled (minimum 50% required)">Import</button>`;

      actionButtons = `
        <button class="btn btn-primary btn-xs review-btn" data-id="${doc.id}" title="Inspect record">
          Review
        </button>
        ${importBtnHtml}
      `;
    } else if (stageKey === 'IMPORTED') {
      actionButtons = `
        <button class="btn btn-ghost btn-xs review-btn" data-id="${doc.id}" title="View imported record">
          View
        </button>
      `;
    }

    actionButtons += `
      <button class="btn btn-danger-ghost btn-xs delete-doc-btn" data-id="${doc.id}" data-code="${escapeHtml(doc.documentCode)}" title="Delete document">
        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/><line x1="10" y1="11" x2="10" y2="17"/><line x1="14" y1="11" x2="14" y2="17"/></svg>
      </button>
    `;

    const createdTime = doc.createdAt ? Utils.formatDate(doc.createdAt) : '—';
    const fileExt = (doc.fileName || '').split('.').pop().toUpperCase();

    const rowClass = isChildOfGroup ? `pdf-child-row${isGroupCollapsed ? ' is-collapsed' : ''}` : '';
    const groupKeyAttr = isChildOfGroup ? ` data-group-key="${escapeHtml(groupKey)}"` : '';

    return `
      <tr class="${rowClass}" data-doc-id="${doc.id}"${groupKeyAttr}>
        <td style="padding-left:${isChildOfGroup ? '36px' : '20px'};">
          ${isChildOfGroup ? `<span class="pdf-tree-indicator">├──</span><span class="pdf-child-page-tag">Page ${doc.pageNumber}/${doc.totalPages}</span>` : ''}
          <span class="doc-code-badge">${doc.documentCode}</span>
        </td>
        <td>
          <div style="font-size:12px; font-weight:500; color:var(--text-primary); max-width:180px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap;" title="${escapeHtml(doc.fileName)}">
            ${escapeHtml(doc.fileName)}
          </div>
          <div style="display:flex; align-items:center; gap:4px; margin-top:2px; flex-wrap:wrap;">
            <span class="summary-tag" style="font-size:10px;">${fileExt}</span>
            ${!isChildOfGroup && doc.totalPages > 1 ? `<span class="doc-page-badge" title="Page ${doc.pageNumber} of ${doc.totalPages}">Page ${doc.pageNumber}/${doc.totalPages}</span>` : ''}
          </div>
          ${!isChildOfGroup && doc.sourceFileName && doc.sourceFileName !== doc.fileName ? `<span class="doc-source-file-subtext" title="Source PDF: ${escapeHtml(doc.sourceFileName)}">📄 ${escapeHtml(doc.sourceFileName)}</span>` : ''}
        </td>
        <td>
          <span class="status-badge-doc ${badgeClass}">
            ${isScanning ? '<span class="scanner-spinner"></span> ' : ''}${stageLabel}
          </span>
        </td>
        <td>
          ${confChip}
        </td>
        <td>
          ${summaryHtml}
        </td>
        <td style="font-size:12px; color:var(--text-muted);">
          ${createdTime}
        </td>
        <td style="text-align:right; padding-right:20px;">
          <div style="display:inline-flex; gap:6px; align-items:center; justify-content:flex-end;">
            ${actionButtons}
          </div>
        </td>
      </tr>
    `;
  }

  function renderPdfGroupHeaderHtml(group, isExpanded) {
    const totalInGroup = group.items.length;
    const verifiedCount = group.items.filter(d => d.reviewStatus === 'VERIFIED').length;
    const importedCount = group.items.filter(d => d.importStatus === 'IMPORTED' || d.reviewStatus === 'IMPORTED').length;
    const scanningCount = group.items.filter(d => currentlyScanningDocIds.has(Number(d.id)) || d.ocrStatus === 'OCR_PROCESSING' || d.extractionStatus === 'EXTRACTION_PROCESSING').length;
    const needsReviewCount = Math.max(0, totalInGroup - verifiedCount - importedCount - scanningCount);

    // Compute average OCR accuracy across all pages in the PDF
    let sumAccuracy = 0;
    group.items.forEach(doc => {
      const summary = getDocExtractedSummary(doc);
      sumAccuracy += computeDocAccuracyPct(doc, summary);
    });
    const avgAccuracy = Math.round(sumAccuracy / (totalInGroup || 1));

    let confClass = 'conf-low';
    if (avgAccuracy >= 80) confClass = 'conf-high';
    else if (avgAccuracy >= 50) confClass = 'conf-med';

    const confChip = `
      <div class="accuracy-bar-container" title="Average PDF Accuracy: ${avgAccuracy}%">
        <div class="accuracy-bar-header">
          <span class="accuracy-bar-value ${confClass}">
            <span class="accuracy-bar-dot ${confClass}"></span>
            Avg ${avgAccuracy}%
          </span>
        </div>
        <div class="accuracy-bar-track">
          <div class="accuracy-bar-fill ${confClass}" style="width: ${avgAccuracy}%;"></div>
        </div>
      </div>
    `;

    // Group Status Badge
    let statusBadgeHtml = '';
    if (scanningCount > 0) {
      statusBadgeHtml = `<span class="status-badge-doc status-badge-SCANNING"><span class="scanner-spinner"></span> Scanning (${scanningCount}/${totalInGroup})...</span>`;
    } else if (importedCount === totalInGroup) {
      statusBadgeHtml = `<span class="status-badge-doc status-badge-IMPORTED">All ${totalInGroup} Imported</span>`;
    } else if (verifiedCount === totalInGroup) {
      statusBadgeHtml = `<span class="status-badge-doc status-badge-VERIFIED">All ${totalInGroup} Verified</span>`;
    } else if (importedCount > 0) {
      statusBadgeHtml = `<span class="status-badge-doc status-badge-VERIFIED">${importedCount} Imp • ${verifiedCount} Ver • ${needsReviewCount} Rev</span>`;
    } else {
      statusBadgeHtml = `<span class="status-badge-doc status-badge-REVIEW_REQUIRED">${verifiedCount}/${totalInGroup} Verified</span>`;
    }

    // Customer names preview
    const customerNames = group.items
      .map(d => getDocExtractedSummary(d)?.customerName)
      .filter(Boolean);
    const uniqueNames = Array.from(new Set(customerNames));
    const customerPreview = uniqueNames.length > 0
      ? uniqueNames.slice(0, 3).join(', ') + (uniqueNames.length > 3 ? '...' : '')
      : 'Tailoring measurement slips';

    const firstDoc = group.items[0];
    const createdTime = firstDoc.createdAt ? Utils.formatDate(firstDoc.createdAt) : '—';

    // Group Actions
    let groupActionsHtml = '<div class="pdf-group-actions-container">';
    if (verifiedCount < totalInGroup && importedCount < totalInGroup) {
      groupActionsHtml += `
        <button class="btn btn-warning btn-xs btn-group-approve-all" data-id="${firstDoc.id}" data-total="${totalInGroup}" data-name="${escapeHtml(group.sourceFileName)}" title="Approve all ${totalInGroup} pages in this PDF at once" style="background:#d97706; color:#fff; border-color:#d97706;">
          Approve All Pages
        </button>
      `;
    }
    if (verifiedCount > 0) {
      groupActionsHtml += `
        <button class="btn btn-success btn-xs btn-group-import-all" data-id="${firstDoc.id}" data-verified="${verifiedCount}" data-name="${escapeHtml(group.sourceFileName)}" title="Import all ${verifiedCount} verified pages from this PDF directly to ERP">
          Import Verified (${verifiedCount})
        </button>
      `;
    }
    groupActionsHtml += `
      <button class="btn btn-secondary btn-xs btn-group-download-pdf" data-id="${firstDoc.id}" data-name="${escapeHtml(group.sourceFileName)}" title="View or download original PDF source file">
        Original PDF
      </button>
    </div>`;

    return `
      <tr class="pdf-group-row${isExpanded ? ' is-expanded' : ''}" data-group-key="${escapeHtml(group.key)}">
        <td style="padding-left:20px;">
          <button class="pdf-group-toggle-btn" type="button" title="${isExpanded ? 'Collapse' : 'Expand'} pages of ${escapeHtml(group.sourceFileName)}">
            <svg class="pdf-group-chevron" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
              <polyline points="9 18 15 12 9 6"></polyline>
            </svg>
          </button>
          <span class="pdf-group-code-pill">PDF GROUP</span>
        </td>
        <td>
          <div class="pdf-group-title">
            <span class="pdf-group-icon">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
                <polyline points="14 2 14 8 20 8"></polyline>
              </svg>
            </span>
            <span style="max-width:160px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap;" title="${escapeHtml(group.sourceFileName)}">
              ${escapeHtml(group.sourceFileName)}
            </span>
          </div>
          <div style="display:flex; align-items:center; gap:6px; margin-top:2px;">
            <span class="pdf-page-count-badge">${group.totalPages} Pages</span>
          </div>
        </td>
        <td>
          ${statusBadgeHtml}
        </td>
        <td>
          ${confChip}
        </td>
        <td>
          <div class="summary-cell-title" style="font-weight:600; font-size:12.5px;">${totalInGroup} Measurement Slips</div>
          <div class="summary-cell-sub" style="font-size:11.5px; color:var(--text-muted); max-width:260px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap;" title="${escapeHtml(customerPreview)}">
            ${escapeHtml(customerPreview)}
          </div>
        </td>
        <td style="font-size:12px; color:var(--text-muted);">
          ${createdTime}
        </td>
        <td style="text-align:right; padding-right:20px;">
          ${groupActionsHtml}
        </td>
      </tr>
    `;
  }

  function renderDocumentsTable() {
    if (!currentDocuments || currentDocuments.length === 0) {
      renderEmptyDocumentsTable();
      return;
    }

    // Filter by search query if present
    let filtered = currentDocuments;
    if (searchQuery) {
      filtered = filtered.filter(doc => {
        const code = (doc.documentCode || '').toLowerCase();
        const fname = (doc.fileName || '').toLowerCase();
        const srcName = (doc.sourceFileName || '').toLowerCase();
        const sum = getDocExtractedSummary(doc);
        const cName = sum ? (sum.customerName || '').toLowerCase() : '';
        const cMobile = sum ? (sum.customerMobile || '').toLowerCase() : '';
        const gType = sum ? (sum.garmentType || '').toLowerCase() : '';

        return code.includes(searchQuery) ||
               fname.includes(searchQuery) ||
               srcName.includes(searchQuery) ||
               cName.includes(searchQuery) ||
               cMobile.includes(searchQuery) ||
               gType.includes(searchQuery);
      });
    }

    if (filtered.length === 0) {
      documentsTableBody.innerHTML = `
        <tr>
          <td colspan="7" class="text-center py-8 text-muted">
            No documents match search "${escapeHtml(searchQuery)}"
          </td>
        </tr>
      `;
      return;
    }

    // Group documents by source PDF
    const groups = groupDocumentsBySource(filtered);
    let tableHtml = '';

    groups.forEach(group => {
      if (group.isGroup && (group.items.length > 1 || group.totalPages > 1)) {
        // Multi-page PDF group
        const isCollapsed = collapsedGroupKeys.has(group.key);
        const isExpanded = !isCollapsed;

        // If actively searching and this group matches, auto-expand
        const effectiveExpanded = searchQuery ? true : isExpanded;

        tableHtml += renderPdfGroupHeaderHtml(group, effectiveExpanded);
        group.items.forEach(doc => {
          tableHtml += renderDocRowHtml(doc, true, group.key, !effectiveExpanded);
        });
      } else {
        // Standalone single image or single-page document
        group.items.forEach(doc => {
          tableHtml += renderDocRowHtml(doc, false);
        });
      }
    });

    documentsTableBody.innerHTML = tableHtml;

    // ── 1. Group toggle expand/collapse click ──
    documentsTableBody.querySelectorAll('.pdf-group-toggle-btn, .pdf-group-row').forEach(el => {
      el.addEventListener('click', (e) => {
        if (e.target.closest('button:not(.pdf-group-toggle-btn)') || e.target.closest('a')) return;
        const row = el.closest('.pdf-group-row');
        if (!row) return;
        const key = row.dataset.groupKey;
        const isCurrentlyExpanded = row.classList.contains('is-expanded');

        if (isCurrentlyExpanded) {
          row.classList.remove('is-expanded');
          collapsedGroupKeys.add(key);
          documentsTableBody.querySelectorAll(`.pdf-child-row[data-group-key="${CSS.escape(key)}"]`)
            .forEach(child => child.classList.add('is-collapsed'));
        } else {
          row.classList.add('is-expanded');
          collapsedGroupKeys.delete(key);
          documentsTableBody.querySelectorAll(`.pdf-child-row[data-group-key="${CSS.escape(key)}"]`)
            .forEach(child => child.classList.remove('is-collapsed'));
        }
      });
    });

    // ── 2. Group Approve All Pages ──
    documentsTableBody.querySelectorAll('.btn-group-approve-all').forEach(btn => {
      btn.addEventListener('click', async (e) => {
        e.stopPropagation();
        const id = btn.dataset.id;
        const name = btn.dataset.name || 'this PDF';
        const total = btn.dataset.total || 'all';
        if (!confirm(`Approve all ${total} pages in "${name}" as VERIFIED?`)) return;

        try {
          btn.disabled = true;
          btn.textContent = 'Approving...';
          const res = await Api.post(API.MIGRATION_DOCUMENT_APPROVE_ALL_PAGES(id));
          const count = (res.data && res.data.length) ? res.data.length : total;
          Toast.success(`Approved all ${count} pages in "${name}"!`);
          await loadBatchDocuments();
          await loadOverallStatistics();
        } catch (err) {
          Toast.error(err.message || 'Failed to approve pages');
          btn.disabled = false;
          btn.textContent = 'Approve All Pages';
        }
      });
    });

    // ── 3. Group Import All Verified Pages ──
    documentsTableBody.querySelectorAll('.btn-group-import-all').forEach(btn => {
      btn.addEventListener('click', async (e) => {
        e.stopPropagation();
        const id = btn.dataset.id;
        const name = btn.dataset.name || 'this PDF';
        const verified = btn.dataset.verified || '';
        if (!confirm(`Import all ${verified} verified pages from "${name}" into ERP tables?`)) return;

        try {
          btn.disabled = true;
          btn.textContent = 'Importing...';
          const res = await Api.post(API.MIGRATION_DOCUMENT_IMPORT_ALL_PAGES(id));
          const list = res.data || res || [];
          Toast.success(`Successfully imported ${list.length} orders from "${name}"!`);
          await loadBatchDocuments();
          await loadBatches(activeBatchId);
          await loadOverallStatistics();
        } catch (err) {
          Toast.error(err.message || 'Failed to import verified pages');
          btn.disabled = false;
          btn.textContent = `Import Verified (${verified})`;
        }
      });
    });

    // ── 4. Group Download Original PDF ──
    documentsTableBody.querySelectorAll('.btn-group-download-pdf').forEach(btn => {
      btn.addEventListener('click', (e) => {
        e.stopPropagation();
        const id = btn.dataset.id;
        const name = btn.dataset.name || 'original_document.pdf';
        downloadOrOpenSourcePdf(id, name);
      });
    });

    // Attach row button handlers
    documentsTableBody.querySelectorAll('.run-ocr-btn').forEach(btn => {
      btn.addEventListener('click', (e) => {
        const id = e.currentTarget.dataset.id;
        handleRunSingleOcr(id, e.currentTarget);
      });
    });

    documentsTableBody.querySelectorAll('.extract-btn').forEach(btn => {
      btn.addEventListener('click', (e) => {
        const id = e.currentTarget.dataset.id;
        handleRunSingleExtract(id, e.currentTarget);
      });
    });

    documentsTableBody.querySelectorAll('.review-btn').forEach(btn => {
      btn.addEventListener('click', (e) => {
        const id = e.currentTarget.dataset.id;
        openReviewModal(id);
      });
    });

    documentsTableBody.querySelectorAll('.override-btn').forEach(btn => {
      btn.addEventListener('click', (e) => {
        const id = e.currentTarget.dataset.id;
        handleRowOverride(id, e.currentTarget);
      });
    });

    documentsTableBody.querySelectorAll('.import-btn').forEach(btn => {
      btn.addEventListener('click', (e) => {
        const id = e.currentTarget.dataset.id;
        handleImportSingleDoc(id, e.currentTarget);
      });
    });

    documentsTableBody.querySelectorAll('.delete-doc-btn').forEach(btn => {
      btn.addEventListener('click', (e) => {
        const id = e.currentTarget.dataset.id;
        const code = e.currentTarget.dataset.code || 'this document';
        handleDeleteSingleDoc(id, code);
      });
    });
  }

  async function downloadOrOpenSourcePdf(docId, fileName) {
    try {
      Toast.info('Opening original PDF...');
      const token = Storage.getToken();
      const headers = {};
      if (token) headers['Authorization'] = `Bearer ${token}`;

      let url = API.MIGRATION_DOCUMENT_SOURCE_FILE(docId);
      if (token) {
        url += (url.includes('?') ? '&' : '?') + 'token=' + encodeURIComponent(token);
      }

      const res = await fetch(url, { headers });
      if (!res.ok) {
        throw new Error(`HTTP ${res.status}: Could not load original file`);
      }
      const blob = await res.blob();
      const pdfBlob = new Blob([blob], { type: 'application/pdf' });
      const blobUrl = URL.createObjectURL(pdfBlob);

      const win = window.open(blobUrl, '_blank');
      if (!win) {
        const a = document.createElement('a');
        a.href = blobUrl;
        a.download = fileName || 'original_document.pdf';
        document.body.appendChild(a);
        a.click();
        a.remove();
      }
    } catch (err) {
      console.warn('Blob fetch failed, falling back to direct navigation:', err);
      const token = Storage.getToken();
      let url = API.MIGRATION_DOCUMENT_SOURCE_FILE(docId);
      if (token) url += (url.includes('?') ? '&' : '?') + 'token=' + encodeURIComponent(token);
      window.open(url, '_blank');
    }
  }


  function updatePaginationControls() {
    if (!paginationBar) return;
    if (totalElements <= pageSize) {
      paginationBar.style.display = 'none';
      return;
    }

    paginationBar.style.display = 'flex';
    const start = currentPage * pageSize + 1;
    const end = Math.min((currentPage + 1) * pageSize, totalElements);
    if (paginationInfo) {
      paginationInfo.textContent = `Showing ${start} to ${end} of ${totalElements} documents`;
    }

    if (currentPageNum) currentPageNum.textContent = String(currentPage + 1);
    if (prevPageBtn) prevPageBtn.disabled = currentPage === 0;
    if (nextPageBtn) nextPageBtn.disabled = currentPage >= totalPages - 1;
  }

  // ── Drag & Drop / File Upload ─────────────────────────────────────────────
  async function handleFilesUpload(fileList) {
    if (!activeBatchId) {
      await ensureActiveBatch();
    }
    if (!activeBatchId) {
      Toast.error('Could not initialize upload session. Please refresh and try again.');
      return;
    }

    const files = Array.from(fileList);
    if (files.length === 0) return;

    uploadProgressContainer.style.display = 'block';
    uploadProgressBar.style.width = '0%';
    uploadStatusText.textContent = `Uploading 0 of ${files.length} files...`;
    uploadPercentText.textContent = '0%';

    let successCount = 0;
    let failCount = 0;
    const uploadedDocs = [];

    // Phase 1: Rapid file upload (takes ~200ms per file)
    for (let i = 0; i < files.length; i++) {
      const file = files[i];
      const percent = Math.round(((i + 0.5) / files.length) * 50);
      uploadProgressBar.style.width = `${percent}%`;
      uploadStatusText.textContent = `Uploading "${file.name}" (${i + 1}/${files.length})...`;
      uploadPercentText.textContent = `${percent}%`;

      try {
        const uploaded = await uploadSingleFile(activeBatchId, file);
        const docs = Array.isArray(uploaded) ? uploaded : (uploaded ? [uploaded] : []);
        for (const d of docs) {
          if (d && d.id) {
            uploadedDocs.push(d);
            successCount++;
          }
        }
      } catch (err) {
        console.error(`Failed to upload ${file.name}:`, err);
        failCount++;
      }
    }

    // Immediately display newly uploaded documents in table!
    if (successCount > 0) {
      await loadBatchDocuments();
      await loadOverallStatistics();
    }

    // Phase 2: Run OCR & field extraction progressively on newly uploaded docs
    if (uploadProgressBar) {
      uploadProgressBar.classList.add('scanning-active');
    }

    for (let i = 0; i < uploadedDocs.length; i++) {
      const doc = uploadedDocs[i];
      const docId = Number(doc.id);
      const percent = 50 + Math.round(((i + 1) / uploadedDocs.length) * 50);
      uploadProgressBar.style.width = `${percent}%`;
      uploadStatusText.textContent = `Scanning & reading "${doc.fileName || doc.documentCode}" (${i + 1}/${uploadedDocs.length})...`;
      uploadPercentText.textContent = `${percent}%`;

      // Activate animated loading bar on this slip's row immediately
      currentlyScanningDocIds.add(docId);
      renderDocumentsTable();

      try {
        await Api.post(API.MIGRATION_DOCUMENT_OCR(doc.id));
        await Api.post(API.MIGRATION_DOCUMENT_EXTRACT(doc.id));
      } catch (ocrErr) {
        console.warn(`OCR/extract warning on doc ${doc.id}:`, ocrErr);
      } finally {
        currentlyScanningDocIds.delete(docId);
      }
      // Update table live after each document finishes OCR!
      await loadBatchDocuments();
      await loadOverallStatistics();
    }

    if (uploadProgressBar) {
      uploadProgressBar.classList.remove('scanning-active');
    }

    uploadProgressBar.style.width = '100%';
    uploadPercentText.textContent = '100%';
    uploadStatusText.textContent = `Complete: ${successCount} uploaded & processed${failCount > 0 ? `, ${failCount} failed` : ''}`;

    setTimeout(() => {
      uploadProgressContainer.style.display = 'none';
    }, 2500);

    if (successCount > 0) {
      Toast.success(`Successfully uploaded and scanned ${successCount} document${successCount === 1 ? '' : 's'}`);
      await loadBatches(activeBatchId);
      await loadOverallStatistics();
    }
    if (failCount > 0) {
      Toast.error(`${failCount} files failed to upload.`);
    }
  }

  async function uploadSingleFile(batchId, file) {
    const formData = new FormData();
    formData.append('file', file);

    const headers = {};
    const token = Storage.getToken();
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }
    const activeBranchIdHeader = Storage.getActiveBranchId();
    if (activeBranchIdHeader && activeBranchIdHeader !== 'ALL') {
      headers[APP.BRANCH_HEADER || 'X-Branch-Id'] = String(activeBranchIdHeader);
    }

    const response = await fetch(API.MIGRATION_BATCH_DOCUMENTS(batchId), {
      method: 'POST',
      headers: headers,
      body: formData
    });

    const json = await response.json().catch(() => null);
    if (!response.ok) {
      throw new Error(json?.message || `Upload failed (${response.status})`);
    }
    return json?.data ?? json;
  }

  // ── Live Camera Scanner & Continuous Capture Queue ───────────────────────
  async function openCameraModal() {
    if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
      Toast.error('Live camera access is not supported by this browser environment or requires HTTPS/localhost.');
      return;
    }

    if (!activeBatchId) {
      await ensureActiveBatch();
    }
    if (!activeBatchId) {
      Toast.error('Could not initialize batch session for camera capture.');
      return;
    }

    // Reset HUD and stats
    queueStats = { queued: 0, processing: 0, waitingForApproval: 0 };
    updateCameraHUD();
    cameraQueueHUD.style.display = 'none';
    cameraProcessingOverlay.style.display = 'none';

    cameraScanModal.style.display = 'flex';
    await populateCameraDevices();
    await startCameraStream();
  }

  async function populateCameraDevices() {
    if (!cameraDeviceSelect) return;
    try {
      const devices = await navigator.mediaDevices.enumerateDevices();
      const videoInputs = devices.filter(d => d.kind === 'videoinput');
      cameraDeviceSelect.innerHTML = '';

      if (videoInputs.length === 0) {
        cameraDeviceSelect.innerHTML = '<option value="">Default Camera</option>';
        return;
      }

      videoInputs.forEach((device, idx) => {
        const opt = document.createElement('option');
        opt.value = device.deviceId;
        opt.textContent = device.label || `Camera ${idx + 1}`;
        cameraDeviceSelect.appendChild(opt);
      });
    } catch (err) {
      console.warn('Could not enumerate camera devices:', err);
    }
  }

  async function startCameraStream(preferredDeviceId = null) {
    if (activeCameraStream) {
      activeCameraStream.getTracks().forEach(t => t.stop());
      activeCameraStream = null;
    }

    const videoConstraints = {
      width: { ideal: 1920, min: 1280 },
      height: { ideal: 1080, min: 720 }
    };

    if (preferredDeviceId) {
      videoConstraints.deviceId = { exact: preferredDeviceId };
    } else {
      videoConstraints.facingMode = { ideal: 'environment' };
    }

    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: videoConstraints,
        audio: false
      });
      activeCameraStream = stream;
      cameraVideo.srcObject = stream;
      await cameraVideo.play().catch(() => {});

      const track = stream.getVideoTracks()[0];
      if (track) {
        const settings = track.getSettings?.() || {};
        if (settings.width && settings.height) {
          cameraResBadge.textContent = `${settings.width}×${settings.height}`;
        } else {
          cameraResBadge.textContent = 'HD Ready';
        }

        // Torch capability
        isTorchOn = false;
        const caps = track.getCapabilities ? track.getCapabilities() : {};
        if (caps.torch) {
          btnToggleTorch.style.display = 'inline-flex';
          btnToggleTorch.innerHTML = '<i class="ri-flashlight-line"></i> Torch';
        } else {
          btnToggleTorch.style.display = 'none';
        }
      }
    } catch (err) {
      console.error('Camera stream error:', err);
      Toast.error(`Could not start camera: ${err.message || 'Check camera device/permission'}`);
    }
  }

  async function toggleCameraTorch() {
    if (!activeCameraStream) return;
    const track = activeCameraStream.getVideoTracks()[0];
    if (!track) return;
    try {
      isTorchOn = !isTorchOn;
      await track.applyConstraints({
        advanced: [{ torch: isTorchOn }]
      });
      btnToggleTorch.innerHTML = isTorchOn
        ? '<i class="ri-flashlight-fill" style="color:#fbbf24;"></i> Torch On'
        : '<i class="ri-flashlight-line"></i> Torch';
    } catch (err) {
      console.warn('Torch toggle failed:', err);
    }
  }

  function closeCameraModal() {
    if (activeCameraStream) {
      activeCameraStream.getTracks().forEach(t => t.stop());
      activeCameraStream = null;
    }
    if (cameraVideo) cameraVideo.srcObject = null;
    cameraScanModal.style.display = 'none';
    cameraProcessingOverlay.style.display = 'none';

    // If documents were queued and processed in the background, alert the user and switch to Needs Review tab
    if (queueStats.waitingForApproval > 0) {
      Toast.success(`Captured ${queueStats.waitingForApproval} slip(s)! All processed and waiting for approval in Needs Review.`);
      // Activate Needs Review filter tab
      if (statusFilterPills) {
        const reviewPill = statusFilterPills.querySelector('[data-status="REVIEW_REQUIRED"]');
        if (reviewPill) {
          statusFilterPills.querySelectorAll('.filter-pill').forEach(p => p.classList.remove('active'));
          reviewPill.classList.add('active');
          currentFilterStatus = 'REVIEW_REQUIRED';
          currentPage = 0;
        }
      }
      loadBatchDocuments();
      loadOverallStatistics();
    }
  }

  function updateCameraHUD() {
    if (hudQueuedCount) hudQueuedCount.textContent = queueStats.queued;
    if (hudProcessingCount) hudProcessingCount.textContent = queueStats.processing;
    if (hudWaitingCount) hudWaitingCount.textContent = queueStats.waitingForApproval;
  }

  function captureSnapshotAsFile(prefix = 'scan_slip') {
    if (!cameraVideo || !cameraVideo.videoWidth || !cameraVideo.videoHeight) {
      throw new Error('Camera feed is not ready. Please wait a moment.');
    }

    cameraCaptureCanvas.width = cameraVideo.videoWidth;
    cameraCaptureCanvas.height = cameraVideo.videoHeight;
    const ctx = cameraCaptureCanvas.getContext('2d');
    ctx.drawImage(cameraVideo, 0, 0, cameraVideo.videoWidth, cameraVideo.videoHeight);

    // Trigger visual shutter flash
    if (cameraShutterFlash) {
      cameraShutterFlash.classList.add('flash');
      setTimeout(() => cameraShutterFlash.classList.remove('flash'), 100);
    }

    return new Promise((resolve, reject) => {
      cameraCaptureCanvas.toBlob((blob) => {
        if (!blob) {
          reject(new Error('Failed to capture picture from camera.'));
          return;
        }
        const timestamp = new Date().toISOString().replace(/[-:T]/g, '').slice(0, 14);
        const fileName = `${prefix}_${timestamp}.jpg`;
        const file = new File([blob], fileName, { type: 'image/jpeg' });
        resolve(file);
      }, 'image/jpeg', 0.92);
    });
  }

  // Option 1: Scan & Process Now (Snaps, runs OCR, closes camera, opens Review modal)
  async function handleScanAndProcessNow() {
    if (btnScanAndProcessNow) btnScanAndProcessNow.disabled = true;
    try {
      if (!activeBatchId) await ensureActiveBatch();
      if (!activeBatchId) throw new Error('No active batch session found.');

      cameraProcessingOverlay.style.display = 'flex';
      cameraProcessingMsg.textContent = 'Snapping & uploading slip...';

      const file = await captureSnapshotAsFile('instant_scan');
      const uploaded = await uploadSingleFile(activeBatchId, file);
      const docs = Array.isArray(uploaded) ? uploaded : (uploaded ? [uploaded] : []);
      const doc = docs[0];

      if (!doc || !doc.id) {
        throw new Error('Upload completed but document ID was not returned.');
      }

      cameraProcessingMsg.textContent = 'Running OCR text recognition...';
      await Api.post(API.MIGRATION_DOCUMENT_OCR(doc.id));

      cameraProcessingMsg.textContent = 'Extracting measurements & tailoring data...';
      await Api.post(API.MIGRATION_DOCUMENT_EXTRACT(doc.id));

      closeCameraModal();
      await loadBatchDocuments();
      await loadOverallStatistics();
      Toast.success('Slip captured & OCR complete! Opening review.');
      await openReviewModal(doc.id);
    } catch (err) {
      console.error('Scan and process error:', err);
      Toast.error(err.message || 'Failed to scan and process document.');
    } finally {
      cameraProcessingOverlay.style.display = 'none';
      if (btnScanAndProcessNow) btnScanAndProcessNow.disabled = false;
    }
  }

  // Option 2: Add to Queue & Next Slip (Keeps camera live, processes in background, waits for approval)
  async function handleQueueBackgroundScan() {
    try {
      if (!activeBatchId) await ensureActiveBatch();
      if (!activeBatchId) throw new Error('No active batch session found.');

      const file = await captureSnapshotAsFile('queue_slip');
      queueStats.queued++;
      updateCameraHUD();
      cameraQueueHUD.style.display = 'flex';

      cameraBackgroundQueue.push(file);

      // Flash quick indicator
      Toast.info(`Slip #${queueStats.queued + queueStats.processing + queueStats.waitingForApproval} added to queue. Camera ready for next!`);

      if (!isQueueWorkerRunning) {
        processBackgroundQueue();
      }
    } catch (err) {
      console.error('Queue snapshot error:', err);
      Toast.error(err.message || 'Could not queue camera snapshot.');
    }
  }

  async function processBackgroundQueue() {
    if (isQueueWorkerRunning) return;
    isQueueWorkerRunning = true;

    while (cameraBackgroundQueue.length > 0) {
      const file = cameraBackgroundQueue.shift();
      queueStats.queued = Math.max(0, queueStats.queued - 1);
      queueStats.processing++;
      updateCameraHUD();

      try {
        if (!activeBatchId) await ensureActiveBatch();
        const uploaded = await uploadSingleFile(activeBatchId, file);
        const docs = Array.isArray(uploaded) ? uploaded : (uploaded ? [uploaded] : []);
        const doc = docs[0];

        if (doc && doc.id) {
          const docId = Number(doc.id);
          currentlyScanningDocIds.add(docId);
          renderDocumentsTable();

          await Api.post(API.MIGRATION_DOCUMENT_OCR(doc.id));
          await Api.post(API.MIGRATION_DOCUMENT_EXTRACT(doc.id));

          currentlyScanningDocIds.delete(docId);
          queueStats.waitingForApproval++;
          await loadBatchDocuments();
          await loadOverallStatistics();
        }
      } catch (err) {
        console.error('Background processing failed on queued slip:', err);
      } finally {
        queueStats.processing = Math.max(0, queueStats.processing - 1);
        updateCameraHUD();
      }
    }

    isQueueWorkerRunning = false;
  }

  // ── Row Pipeline Actions ──────────────────────────────────────────────────
  async function handleRunSingleOcr(docId, btnElement) {
    const numId = Number(docId);
    try {
      currentlyScanningDocIds.add(numId);
      renderDocumentsTable();
      if (btnElement) {
        btnElement.disabled = true;
        btnElement.textContent = 'Scanning...';
      }
      Toast.info('Processing OCR text recognition...');
      const res = await withTimeout(Api.post(API.MIGRATION_DOCUMENT_OCR(docId)), 60000, 'OCR scanning timed out after 1 minute.');
      Toast.success('OCR completed successfully');
    } catch (err) {
      Toast.error(`OCR failed: ${err.message}. The process was cancelled. Please re-upload.`);
      showReviewTimeoutDialog(docId, `OCR processing timed out after 1 minute or failed: ${err.message}. The process was cancelled. Please re-upload this document.`);
    } finally {
      currentlyScanningDocIds.delete(numId);
      await loadBatchDocuments();
      await loadOverallStatistics();
    }
  }

  async function handleRunSingleExtract(docId, btnElement) {
    const numId = Number(docId);
    try {
      currentlyScanningDocIds.add(numId);
      renderDocumentsTable();
      if (btnElement) {
        btnElement.disabled = true;
        btnElement.textContent = 'Extracting...';
      }
      const res = await withTimeout(Api.post(API.MIGRATION_DOCUMENT_EXTRACT(docId)), 60000, 'Field extraction timed out after 1 minute.');
      Toast.success('Extracted tailoring details');
    } catch (err) {
      Toast.error(`Field extraction failed: ${err.message}. The process was cancelled. Please re-upload.`);
      showReviewTimeoutDialog(docId, `Field extraction timed out after 1 minute or failed: ${err.message}. The process was cancelled. Please re-upload this document.`);
    } finally {
      currentlyScanningDocIds.delete(numId);
      await loadBatchDocuments();
      await loadOverallStatistics();
    }
  }

  async function handleRowOverride(docId, btnElement) {
    const doc = currentDocuments.find(d => String(d.id) === String(docId));
    if (!doc) return;

    const summary = getDocExtractedSummary(doc);
    const comp = computeDocFieldCompletion(doc, summary);
    if (comp.pct < 50) {
      Toast.error(`Cannot override: only ${comp.pct}% of fields are filled (${comp.filled}/${comp.total}). Minimum 50% required. Opening review to complete details.`);
      openReviewModal(docId);
      return;
    }

    const cName = summary.customerName || 'this customer';
    const cGarment = summary.garmentType || 'garment';

    if (!confirm(`Measurement already exists in ERP for ${cName} (${cGarment}).\n\nDo you want to OVERRIDE the existing ERP measurement profile with this slip's data and mark it verified?`)) {
      return;
    }

    try {
      if (btnElement) {
        btnElement.disabled = true;
        btnElement.textContent = 'Overriding...';
      }

      let correctedJson = doc.correctedDataJson;
      if (!correctedJson && doc.extractedDataJson) {
        correctedJson = doc.extractedDataJson;
      }

      const payload = {
        action: 'APPROVED',
        correctedDataJson: correctedJson,
        remarks: 'Overridden from migration table',
        updateCustomerProfile: true
      };

      await Api.post(API.MIGRATION_DOCUMENT_REVIEW(docId), payload);
      Toast.success(`Measurement profile overridden & document verified for ${cName}!`);
      await loadBatchDocuments();
      await loadOverallStatistics();
    } catch (err) {
      Toast.error(err.message || 'Failed to override measurement');
      if (btnElement) {
        btnElement.disabled = false;
        btnElement.textContent = 'Override';
      }
    }
  }

  async function handleImportSingleDoc(docId, btnElement) {
    const doc = currentDocuments.find(d => d.id === docId);
    if (doc) {
      const summary = getDocExtractedSummary(doc);
      const comp = computeDocFieldCompletion(doc, summary);
      if (comp.pct < 50) {
        Toast.error(`Cannot process document: only ${comp.pct}% of fields are filled (${comp.filled}/${comp.total}). Minimum 50% required.`);
        return;
      }
    }
    if (!confirm('Import this historical record into live ERP tables now?')) return;

    try {
      if (btnElement) {
        btnElement.disabled = true;
        btnElement.textContent = 'Importing...';
      }
      const res = await Api.post(API.MIGRATION_DOCUMENT_IMPORT(docId));
      const summary = res.data || res;
      Toast.success(`Imported as Order #${summary.orderNumber} for customer ${summary.customerName}`);
      await loadBatchDocuments();
      await loadBatches(activeBatchId);
      await loadOverallStatistics();
    } catch (err) {
      Toast.error(err.message || 'Failed to import document to ERP');
      if (btnElement) {
        btnElement.disabled = false;
        btnElement.textContent = 'Import';
      }
    }
  }

  // ── Batch Actions ─────────────────────────────────────────────────────────
  async function handleRunBatchOcr() {
    if (!activeBatchId) return;
    const uploadedDocs = currentDocuments.filter(d => d.reviewStatus === 'UPLOADED');
    if (uploadedDocs.length === 0) {
      Toast.info('No documents in UPLOADED status found in current view.');
      return;
    }

    if (!confirm(`Run OCR text recognition sequentially on ${uploadedDocs.length} documents?`)) return;

    btnRunBatchOcr.disabled = true;
    let completed = 0;
    for (const doc of uploadedDocs) {
      const numId = Number(doc.id);
      try {
        currentlyScanningDocIds.add(numId);
        renderDocumentsTable();
        await Api.post(API.MIGRATION_DOCUMENT_OCR(doc.id));
        completed++;
      } catch (err) {
        console.error(`OCR failed on ${doc.documentCode}:`, err);
      } finally {
        currentlyScanningDocIds.delete(numId);
        await loadBatchDocuments();
      }
    }

    Toast.success(`OCR complete for ${completed}/${uploadedDocs.length} documents.`);
    btnRunBatchOcr.disabled = false;
    await loadBatchDocuments();
    await loadOverallStatistics();
  }

  async function handleExtractBatch() {
    if (!activeBatchId) return;
    const ocrDocs = currentDocuments.filter(d => d.reviewStatus === 'OCR_COMPLETED');
    if (ocrDocs.length === 0) {
      Toast.info('No documents in OCR_COMPLETED status found in current view.');
      return;
    }

    btnExtractBatch.disabled = true;
    let completed = 0;
    for (const doc of ocrDocs) {
      const numId = Number(doc.id);
      try {
        currentlyScanningDocIds.add(numId);
        renderDocumentsTable();
        await Api.post(API.MIGRATION_DOCUMENT_EXTRACT(doc.id));
        completed++;
      } catch (err) {
        console.error(`Extraction failed on ${doc.documentCode}:`, err);
      } finally {
        currentlyScanningDocIds.delete(numId);
        await loadBatchDocuments();
      }
    }

    Toast.success(`Extracted fields for ${completed}/${ocrDocs.length} documents.`);
    btnExtractBatch.disabled = false;
    await loadBatchDocuments();
    await loadOverallStatistics();
  }

  async function handleBulkImportVerified() {
    if (!activeBatchId) return;
    const verifiedDocs = currentDocuments.filter(d => {
      if (d.reviewStatus !== 'VERIFIED') return false;
      const comp = computeDocFieldCompletion(d, getDocExtractedSummary(d));
      return comp.pct >= 50;
    });
    if (verifiedDocs.length === 0) {
      Toast.info('No verified documents with ≥ 50% field completion found to import.');
      return;
    }

    if (!confirm(`Import all ${verifiedDocs.length} VERIFIED documents into live ERP order & customer tables?`)) return;

    btnBulkImport.disabled = true;
    let imported = 0;
    for (const doc of verifiedDocs) {
      try {
        await Api.post(API.MIGRATION_DOCUMENT_IMPORT(doc.id));
        imported++;
      } catch (err) {
        console.error(`Import failed on ${doc.documentCode}:`, err);
      }
    }

    Toast.success(`Successfully imported ${imported}/${verifiedDocs.length} documents.`);
    btnBulkImport.disabled = false;
    await loadBatchDocuments();
    await loadBatches(activeBatchId);
    await loadOverallStatistics();
  }

  async function handleDeleteSingleDoc(docId, docCode) {
    if (!confirm(`Are you sure you want to permanently delete document "${docCode}"?`)) return;
    try {
      await Api.delete(API.MIGRATION_DOCUMENT(docId));
      Toast.success(`Deleted ${docCode}`);
      await loadBatchDocuments();
      await loadBatches(activeBatchId);
      await loadOverallStatistics();
    } catch (err) {
      Toast.error(err.message || 'Failed to delete document');
    }
  }

  async function handleClearAllMigrationData() {
    const promptMsg = '⚠️ WARNING: You are about to permanently remove ALL old OCR data!\n\n' +
      '• All migration batches will be deleted\n' +
      '• All scanned documents & OCR text will be deleted\n' +
      '• All review records will be cleared\n' +
      '• Uploaded disk files will be erased\n\n' +
      'Type OK to confirm removal of all old OCR data:';

    const userInput = prompt(promptMsg);
    if (!userInput || userInput.trim().toUpperCase() !== 'OK') {
      Toast.info('Clear operation cancelled');
      return;
    }

    try {
      if (btnClearAllMigration) {
        btnClearAllMigration.disabled = true;
        btnClearAllMigration.textContent = 'Clearing...';
      }
      await Api.post(API.MIGRATION_CLEAR_ALL);
      Toast.success('All old OCR data and batches successfully removed!');
      activeBatchId = null;
      currentDocuments = [];
      await loadBatches();
      await loadOverallStatistics();
      renderEmptyDocumentsTable();
    } catch (err) {
      Toast.error(err.message || 'Failed to clear migration data');
    } finally {
      if (btnClearAllMigration) {
        btnClearAllMigration.disabled = false;
        btnClearAllMigration.innerHTML = `
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/><line x1="10" y1="11" x2="10" y2="17"/><line x1="14" y1="11" x2="14" y2="17"/></svg>
          <span>Clear All OCR Data</span>
        `;
      }
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // SPLIT-SCREEN REVIEW & CORRECTION DESK
  // ═══════════════════════════════════════════════════════════════════════════

  // Helper to bound async operations
  function withTimeout(promise, ms = 60000, errorMsg = 'Process timed out after 1 minute.') {
    let timer;
    const timeoutPromise = new Promise((_, reject) => {
      timer = setTimeout(() => {
        reject(new Error(errorMsg));
      }, ms);
    });
    return Promise.race([
      promise.then(res => {
        clearTimeout(timer);
        return res;
      }).catch(err => {
        clearTimeout(timer);
        throw err;
      }),
      timeoutPromise
    ]);
  }

  // 1-Minute Review Timeout Watchdog State & Dialog
  let reviewWatchdogTimer = null;
  let reviewWatchdogInterval = null;

  const reviewTimeoutModal = document.getElementById('reviewTimeoutModal');
  const reviewTimeoutMsg = document.getElementById('reviewTimeoutMsg');
  const btnDismissTimeoutModal = document.getElementById('btnDismissTimeoutModal');
  const btnReuploadFromTimeout = document.getElementById('btnReuploadFromTimeout');

  function showReviewTimeoutDialog(docId, customMessage) {
    if (reviewTimeoutMsg && customMessage) {
      reviewTimeoutMsg.textContent = customMessage;
    }
    if (reviewTimeoutModal) {
      reviewTimeoutModal.style.display = 'flex';
    }
  }

  function hideReviewTimeoutDialog() {
    if (reviewTimeoutModal) {
      reviewTimeoutModal.style.display = 'none';
    }
  }

  btnDismissTimeoutModal?.addEventListener('click', hideReviewTimeoutDialog);

  btnReuploadFromTimeout?.addEventListener('click', () => {
    hideReviewTimeoutDialog();
    const uploadZone = document.getElementById('uploadZone');
    if (uploadZone) {
      uploadZone.scrollIntoView({ behavior: 'smooth', block: 'center' });
      uploadZone.classList.add('highlight-pulse');
      setTimeout(() => uploadZone.classList.remove('highlight-pulse'), 3500);
    }
    const fileInput = document.getElementById('fileInput');
    if (fileInput) {
      fileInput.click();
    }
  });

  function clearReviewWatchdog() {
    if (reviewWatchdogTimer) {
      clearTimeout(reviewWatchdogTimer);
      reviewWatchdogTimer = null;
    }
    if (reviewWatchdogInterval) {
      clearInterval(reviewWatchdogInterval);
      reviewWatchdogInterval = null;
    }
  }

  function cancelReviewProcessAndPromptReupload(docId, reason) {
    clearReviewWatchdog();
    if (docId) {
      currentlyScanningDocIds.delete(Number(docId));
    }
    closeReviewModal();
    renderDocumentsTable();

    const msg = reason || 'Failed to review document within 1 minute. The process was cancelled. Please re-upload the document.';
    Toast.error(msg, 7000);
    showReviewTimeoutDialog(docId, msg);
  }

  document.getElementById('btnCancelViewerReview')?.addEventListener('click', () => {
    cancelReviewProcessAndPromptReupload(currentDocInReview?.id, 'Review preparation cancelled. Please re-upload the document.');
  });

  async function openReviewModal(docId) {
    try {
      resetZoom();
      reviewModal.style.display = 'flex';
      viewerLoading.style.display = 'flex';

      // Start 1-Minute Watchdog (Cancels process and instructs user to re-upload if > 60s)
      clearReviewWatchdog();
      let secondsLeft = 60;
      const viewerSecondsLeft = document.getElementById('viewerSecondsLeft');
      const viewerLoadingText = document.getElementById('viewerLoadingText');
      if (viewerSecondsLeft) viewerSecondsLeft.textContent = '60s';
      if (viewerLoadingText) viewerLoadingText.textContent = 'Preparing review & loading document...';

      reviewWatchdogInterval = setInterval(() => {
        secondsLeft--;
        if (viewerSecondsLeft) viewerSecondsLeft.textContent = `${Math.max(0, secondsLeft)}s`;
        if (secondsLeft <= 0) {
          cancelReviewProcessAndPromptReupload(docId, 'Failed to review document within 1 minute. The process was cancelled. Please re-upload the document.');
        }
      }, 1000);

      reviewWatchdogTimer = setTimeout(() => {
        cancelReviewProcessAndPromptReupload(docId, 'Failed to review document within 1 minute. The process was cancelled. Please re-upload the document.');
      }, 60000);

      // Hide annotation host (wraps img) and clear old overlay
      if (annotationHost) annotationHost.style.display = 'none';
      AnnotationOverlay.clear();
      reviewDocPdf.style.display = 'none';
      reviewRawOcrBox.style.display = 'none';
      toggleRawOcrBtn.textContent = 'Toggle Raw OCR Text';

      // Load Document Details
      const res = await withTimeout(Api.get(API.MIGRATION_DOCUMENT(docId)), 60000, 'Loading document timed out after 1 minute.');
      currentDocInReview = res.data || res;

      // If document has not been OCR-processed or extracted yet, auto-process now
      if (!currentDocInReview.rawOcrText || currentDocInReview.reviewStatus === 'UPLOADED') {
        try {
          currentlyScanningDocIds.add(Number(docId));
          updateProcessingSteps(currentDocInReview);
          if (viewerLoadingText) viewerLoadingText.textContent = 'Running OCR recognition...';
          await withTimeout(Api.post(API.MIGRATION_DOCUMENT_OCR(docId)), 55000, 'OCR text recognition timed out.');
          if (viewerLoadingText) viewerLoadingText.textContent = 'Extracting fields & measurements...';
          const extRes = await withTimeout(Api.post(API.MIGRATION_DOCUMENT_EXTRACT(docId)), 55000, 'Field extraction timed out.');
          currentDocInReview = extRes.data || extRes || currentDocInReview;
        } catch (autoErr) {
          console.warn('Auto OCR/extract on review open:', autoErr);
        } finally {
          currentlyScanningDocIds.delete(Number(docId));
          updateProcessingSteps(currentDocInReview);
        }
      }

      // Update Header Badges
      if (reviewModalTitle) {
        reviewModalTitle.textContent = `${currentDocInReview.documentCode} — ${currentDocInReview.fileName}`;
      }
      if (reviewDocStatusBadge) {
        let sLabel = 'Needs Review';
        let sClass = 'status-badge-REVIEW_REQUIRED';
        if (currentDocInReview.importStatus === 'IMPORTED' || currentDocInReview.reviewStatus === 'IMPORTED') {
          sLabel = 'Imported'; sClass = 'status-badge-IMPORTED';
        } else if (currentDocInReview.reviewStatus === 'VERIFIED') {
          sLabel = 'Verified'; sClass = 'status-badge-VERIFIED';
        }
        reviewDocStatusBadge.className = `badge status-badge-doc ${sClass}`;
        reviewDocStatusBadge.textContent = sLabel;
      }
      const docSumm = getDocExtractedSummary(currentDocInReview);
      const comp = computeDocFieldCompletion(currentDocInReview, docSumm);
      if (reviewDocOcrConf)  reviewDocOcrConf.textContent  = `Filled: ${comp.pct}% (${comp.filled}/${comp.total})`;
      if (reviewDocOcrConf2) reviewDocOcrConf2.textContent = `Filled: ${comp.pct}% (${comp.filled}/${comp.total})`;

      // Processing Steps — mark steps 1-4 done (step 5 only after import)
      updateProcessingSteps(currentDocInReview);

      isViewingProcessedImage = false;
      if (toggleProcessedImgBtn) {
        if (currentDocInReview.ocrProcessedImagePath) {
          toggleProcessedImgBtn.style.display = 'inline-flex';
          toggleProcessedImgBtn.classList.remove('vtb-btn-primary');
          if (toggleProcessedImgText) toggleProcessedImgText.textContent = 'Processed';
          toggleProcessedImgBtn.title = 'Switch to OCR preprocessed (contrast-enhanced) image';
        } else {
          toggleProcessedImgBtn.style.display = 'none';
        }
      }

      // Load File Preview via Object URL with Authorization
      loadFilePreview(docId, currentDocInReview.fileName);

      // Raw OCR Text
      if (reviewRawOcrText) {
        reviewRawOcrText.textContent = currentDocInReview.rawOcrText || 'No raw OCR text captured for this document.';
      }

      // Populate Form Fields
      populateReviewForm(currentDocInReview);

      // Load ERP Customer Matches
      loadCustomerMatches(docId);

      // Multi-Page PDF Navigation
      if (currentDocInReview.totalPages && currentDocInReview.totalPages > 1) {
        try {
          const pRes = await Api.get(API.MIGRATION_DOCUMENT_PAGES(docId));
          siblingPagesInReview = pRes.data || pRes || [currentDocInReview];
        } catch (pErr) {
          console.warn('Could not fetch sibling pages:', pErr);
          siblingPagesInReview = [currentDocInReview];
        }
      } else {
        siblingPagesInReview = [currentDocInReview];
      }
      renderPageNavigation(currentDocInReview, siblingPagesInReview);

      // Successfully ready for review within 1 minute! Clear the timeout watchdog
      clearReviewWatchdog();

    } catch (err) {
      cancelReviewProcessAndPromptReupload(docId, `Failed to review document: ${err.message}. The process was cancelled. Please re-upload.`);
    }
  }

  function getReviewNavigationContext() {
    let queue = currentDocuments || [];
    if (searchQuery) {
      queue = queue.filter(doc => {
        const code = (doc.documentCode || '').toLowerCase();
        const fname = (doc.fileName || '').toLowerCase();
        const srcName = (doc.sourceFileName || '').toLowerCase();
        const sum = getDocExtractedSummary(doc);
        const cName = sum ? (sum.customerName || '').toLowerCase() : '';
        const cMobile = sum ? (sum.customerMobile || '').toLowerCase() : '';
        const gType = sum ? (sum.garmentType || '').toLowerCase() : '';
        return code.includes(searchQuery) ||
               fname.includes(searchQuery) ||
               srcName.includes(searchQuery) ||
               cName.includes(searchQuery) ||
               cMobile.includes(searchQuery) ||
               gType.includes(searchQuery);
      });
    }

    if (!currentDocInReview) {
      return { prevDocId: null, nextDocId: null, currentNum: 1, totalNum: 1, navLabel: 'Doc', isMultiPage: false, queue };
    }

    const currId = currentDocInReview.id;
    const isMultiPage = Boolean(currentDocInReview.totalPages && currentDocInReview.totalPages > 1 && siblingPagesInReview && siblingPagesInReview.length > 1);

    let prevDocId = null;
    let nextDocId = null;
    let currentNum = 1;
    let totalNum = 1;
    let navLabel = 'Doc';

    if (isMultiPage) {
      const sibIdx = siblingPagesInReview.findIndex(d => d.id === currId);
      currentNum = (sibIdx >= 0) ? (siblingPagesInReview[sibIdx].pageNumber || sibIdx + 1) : (currentDocInReview.pageNumber || 1);
      totalNum = currentDocInReview.totalPages || siblingPagesInReview.length;
      navLabel = 'Page';

      if (sibIdx > 0) {
        prevDocId = siblingPagesInReview[sibIdx - 1].id;
      }
      if (sibIdx >= 0 && sibIdx < siblingPagesInReview.length - 1) {
        nextDocId = siblingPagesInReview[sibIdx + 1].id;
      }

      // If at boundary of multi-page PDF, allow stepping to previous/next item in the overall batch queue
      if (!prevDocId) {
        const qIdx = queue.findIndex(d => d.id === currId);
        if (qIdx > 0) prevDocId = queue[qIdx - 1].id;
      }
      if (!nextDocId) {
        const qIdx = queue.findIndex(d => d.id === currId);
        if (qIdx >= 0 && qIdx < queue.length - 1) nextDocId = queue[qIdx + 1].id;
      }
    } else {
      const qIdx = queue.findIndex(d => d.id === currId);
      totalNum = Math.max(1, queue.length);
      currentNum = qIdx >= 0 ? qIdx + 1 : 1;
      navLabel = 'Doc';

      if (qIdx > 0) {
        prevDocId = queue[qIdx - 1].id;
      }
      if (qIdx >= 0 && qIdx < queue.length - 1) {
        nextDocId = queue[qIdx + 1].id;
      }
    }

    return {
      prevDocId,
      nextDocId,
      currentNum,
      totalNum,
      navLabel,
      isMultiPage,
      queue
    };
  }

  function renderPageNavigation(doc, siblings) {
    const nav = getReviewNavigationContext();

    const hasMultiple = (nav.totalNum > 1) || (nav.queue && nav.queue.length > 1) || Boolean(nav.prevDocId || nav.nextDocId);
    if (reviewPageNavBar) {
      reviewPageNavBar.style.display = hasMultiple ? 'flex' : 'none';
    }
    if (reviewFooterNav) {
      reviewFooterNav.style.display = hasMultiple ? 'inline-flex' : 'none';
    }

    if (pageNavLabel) pageNavLabel.textContent = nav.navLabel;
    if (reviewPageNavCurrent) reviewPageNavCurrent.textContent = String(nav.currentNum);
    if (reviewPageNavTotal) reviewPageNavTotal.textContent = String(nav.totalNum);

    const counterText = `${nav.navLabel} ${nav.currentNum} of ${nav.totalNum}`;
    if (reviewFooterNavText) reviewFooterNavText.textContent = counterText;

    const canPrev = Boolean(nav.prevDocId);
    const canNext = Boolean(nav.nextDocId);

    if (reviewPrevPageBtn) reviewPrevPageBtn.disabled = !canPrev;
    if (reviewNextPageBtn) reviewNextPageBtn.disabled = !canNext;
    if (btnPrevDocFooter)  btnPrevDocFooter.disabled  = !canPrev;
    if (btnNextDocFooter)  btnNextDocFooter.disabled  = !canNext;

    // Approve & Next button
    if (btnApproveAndNextPage) {
      btnApproveAndNextPage.style.display = canNext ? 'inline-block' : 'none';
      btnApproveAndNextPage.textContent = nav.isMultiPage ? 'Approve & Next Page ➔' : 'Approve & Next ➔';
    }

    // Approve All Pages button (only for multi-page PDF)
    if (btnApproveAllPages) {
      btnApproveAllPages.style.display = (nav.isMultiPage && doc.totalPages > 1) ? 'inline-block' : 'none';
    }

    // Original PDF button
    if (reviewDownloadSourcePdf) {
      const isPdfSource = (doc.sourceFileName && doc.sourceFileName.toLowerCase().endsWith('.pdf')) ||
                          (doc.fileName && doc.fileName.toLowerCase().endsWith('.pdf')) ||
                          (doc.totalPages && doc.totalPages > 1);
      if (isPdfSource) {
        reviewDownloadSourcePdf.style.display = 'inline-flex';
        reviewDownloadSourcePdf.onclick = (e) => {
          e.preventDefault();
          downloadOrOpenSourcePdf(doc.id, doc.sourceFileName || doc.fileName || 'original_document.pdf');
        };
      } else {
        reviewDownloadSourcePdf.style.display = 'none';
      }
    }

    // Multi-page pills container
    if (reviewPagePillsContainer) {
      if (nav.isMultiPage && siblings && siblings.length > 1) {
        reviewPagePillsContainer.style.display = 'flex';
        reviewPagePillsContainer.innerHTML = siblings.map(s => {
          const pNum = s.pageNumber || 1;
          const isActive = s.id === doc.id;
          const isVerified = s.reviewStatus === 'VERIFIED' || s.importStatus === 'IMPORTED';
          const icon = isVerified ? '✓' : '';
          return `
            <button type="button" class="page-pill ${isActive ? 'active' : ''} ${isVerified ? 'verified' : ''}"
                    data-id="${s.id}" data-page="${pNum}"
                    title="Switch to Page ${pNum} (${s.reviewStatus || 'UPLOADED'})">
              P${pNum}${icon ? ' ' + icon : ''}
            </button>
          `;
        }).join('');
      } else {
        reviewPagePillsContainer.style.display = 'none';
        reviewPagePillsContainer.innerHTML = '';
      }
    }
  }

  async function switchReviewPage(targetDocId) {
    if (!targetDocId || (currentDocInReview && currentDocInReview.id === targetDocId)) return;
    await openReviewModal(targetDocId);
  }

  async function submitReviewAndNextPage() {
    if (!currentDocInReview) return;
    try {
      if (btnApproveAndNextPage) {
        btnApproveAndNextPage.disabled = true;
        btnApproveAndNextPage.textContent = 'Saving...';
      }
      const payload = buildReviewPayload('APPROVED');
      await Api.post(API.MIGRATION_DOCUMENT_REVIEW(currentDocInReview.id), payload);
      currentDocInReview.reviewStatus = 'VERIFIED';
      updateProcessingSteps(currentDocInReview);
      const isPage = Boolean(currentDocInReview.totalPages && currentDocInReview.totalPages > 1);
      Toast.success(isPage ? `Page ${currentDocInReview.pageNumber || 1} approved!` : `${currentDocInReview.documentCode || 'Document'} approved!`);
      await loadBatchDocuments();
      await loadOverallStatistics();

      // Find next document or page
      const nav = getReviewNavigationContext();
      if (nav.nextDocId) {
        await switchReviewPage(nav.nextDocId);
      } else {
        closeReviewModal();
      }
    } catch (err) {
      Toast.error(err.message || 'Failed to save review');
    } finally {
      if (btnApproveAndNextPage) {
        btnApproveAndNextPage.disabled = false;
        btnApproveAndNextPage.textContent = (currentDocInReview && currentDocInReview.totalPages > 1) ? 'Approve & Next Page ➔' : 'Approve & Next ➔';
      }
    }
  }

  async function submitApproveAllPages() {
    if (!currentDocInReview) return;
    const name = currentDocInReview.sourceFileName || currentDocInReview.fileName;
    if (!confirm(`Are you sure you want to approve all ${currentDocInReview.totalPages} pages of "${name}"?`)) {
      return;
    }
    try {
      if (btnApproveAllPages) {
        btnApproveAllPages.disabled = true;
        btnApproveAllPages.textContent = 'Approving All...';
      }
      const res = await Api.post(API.MIGRATION_DOCUMENT_APPROVE_ALL_PAGES(currentDocInReview.id));
      currentDocInReview.reviewStatus = 'VERIFIED';
      updateProcessingSteps(currentDocInReview);
      const count = (res.data && res.data.length) ? res.data.length : currentDocInReview.totalPages;
      Toast.success(`All ${count} pages marked as VERIFIED!`);
      await loadBatchDocuments();
      await loadOverallStatistics();
      // Re-open current doc to refresh status badges
      await openReviewModal(currentDocInReview.id);
    } catch (err) {
      Toast.error(err.message || 'Failed to approve all pages');
    } finally {
      if (btnApproveAllPages) {
        btnApproveAllPages.disabled = false;
        btnApproveAllPages.textContent = 'Approve All Pages';
      }
    }
  }

  async function loadFilePreview(docId, fileName) {
    try {
      const token = Storage.getToken();
      const headers = {};
      if (token) headers['Authorization'] = `Bearer ${token}`;

      const fileUrl = `${API.MIGRATION_DOCUMENT_FILE(docId)}?t=${Date.now()}`;
      const res = await fetch(fileUrl, { headers });
      if (!res.ok) throw new Error(`HTTP ${res.status} file load failed`);

      const blob = await res.blob();
      if (currentBlobUrl) URL.revokeObjectURL(currentBlobUrl);
      currentBlobUrl = URL.createObjectURL(blob);

      viewerLoading.style.display = 'none';
      const isPdf = blob.type === 'application/pdf' || ((fileName || '').toLowerCase().endsWith('.pdf') && !fileName.includes('(Page '));

      if (isPdf) {
        reviewDocPdf.src = currentBlobUrl;
        reviewDocPdf.style.display = 'block';
        if (annotationHost) annotationHost.style.display = 'none';
        AnnotationOverlay.clear();
      } else {
        reviewDocImage.src = currentBlobUrl;
        reviewDocPdf.style.display = 'none';
        // Show host and init annotation AFTER image finishes loading
        // (onload fires once dimensions are known, ensuring perfect SVG alignment)
        reviewDocImage.onload = () => {
          if (annotationHost) annotationHost.style.display = 'inline-block';
          const garmentType = revGarmentType ? revGarmentType.value : 'CHUDI';
          AnnotationOverlay.init(garmentType, currentDocInReview);
        };
      }
    } catch (err) {
      viewerLoading.innerHTML = `<span style="color:#ef4444;">Failed to load scan preview (${err.message})</span>`;
    }
  }

  function populateReviewForm(doc) {
    matchedCustomerMobile = null;
    let ext = null;
    if (doc.extractedDataJson) {
      try {
        ext = typeof doc.extractedDataJson === 'string' ? JSON.parse(doc.extractedDataJson) : doc.extractedDataJson;
      } catch (e) {
        console.warn('Could not parse extractedDataJson:', e);
      }
    }

    const customer = ext?.customer || {};
    const order = ext?.order || {};

    const rawOcrBox = document.getElementById('reviewRawOcrBox');
    const rawOcrPre = document.getElementById('reviewRawOcrText');
    if (rawOcrPre) rawOcrPre.textContent = doc.rawOcrText || 'No OCR text available.';
    if (rawOcrBox) rawOcrBox.style.display = 'none';

    // Customer
    revCustomerMobile.value = customer.customerMobile || '';
    revCustomerName.value = customer.customerName || '';

    // Order
    revOrderDate.value = order.orderDate || new Date().toISOString().split('T')[0];
    if (revDeliveryDate) {
      revDeliveryDate.value = order.deliveryDate || ext?.order?.deliveryDate || '';
    }
    if (revErode) {
      revErode.value = order.erode || ext?.order?.erode || '';
    }
    if (revCloth) {
      revCloth.value = order.cloth || ext?.order?.cloth || '';
    }

    // Garment type determination:
    // Strictly detected from top-center / header area.
    // Spec: "Do not infer garment type from the measurement names.
    // If the top-center garment type cannot be confidently detected, mark it as LOW CONFIDENCE and require review."
    const gTypeRaw = (order.garmentType || '').toUpperCase().trim();

    let resolvedType = null;
    if (gTypeRaw === 'BLOUSE' || (gTypeRaw !== 'NEEDS_REVIEW' && gTypeRaw.includes('BLOUSE'))) {
      resolvedType = 'BLOUSE';
    } else if (gTypeRaw === 'CHUDI' || (gTypeRaw !== 'NEEDS_REVIEW' && (gTypeRaw.includes('CHUDI') || gTypeRaw.includes('CHURIDAR')))) {
      resolvedType = 'CHUDI';
    }

    if (resolvedType) {
      // Confidently detected from top-center -> locked / disabled
      revGarmentType.value = resolvedType;
      revGarmentType.disabled = true;
      revGarmentType.style.cursor = 'not-allowed';
      revGarmentType.style.background = '#0e111d';
      revGarmentType.style.color = '#a5b4fc';
      revGarmentType.style.borderColor = 'rgba(99,102,241,0.35)';
      revGarmentType.style.boxShadow = 'none';

      if (revGarmentTypeBadge) {
        revGarmentTypeBadge.innerHTML = '<i class="ri-scan-2-line"></i> From Top-Center';
        revGarmentTypeBadge.style.color = '#a5b4fc';
        revGarmentTypeBadge.style.background = 'rgba(99,102,241,0.12)';
        revGarmentTypeBadge.style.borderColor = 'rgba(99,102,241,0.3)';
        revGarmentTypeBadge.title = 'Garment type auto-detected from top-center header';
      }
    } else {
      // Cannot be determined from top-center -> mark as NEEDS REVIEW and allow user to select
      revGarmentType.value = gTypeRaw === 'NEEDS_REVIEW' ? 'NEEDS_REVIEW' : '';
      revGarmentType.disabled = false;
      revGarmentType.style.cursor = 'pointer';
      revGarmentType.style.background = '#131728';
      revGarmentType.style.color = '#fbbf24';
      revGarmentType.style.borderColor = '#f59e0b';
      revGarmentType.style.boxShadow = '0 0 8px rgba(245, 158, 11, 0.25)';

      if (revGarmentTypeBadge) {
        revGarmentTypeBadge.innerHTML = '<i class="ri-alert-line"></i> NEEDS REVIEW';
        revGarmentTypeBadge.style.color = '#fbbf24';
        revGarmentTypeBadge.style.background = 'rgba(245,158,11,0.15)';
        revGarmentTypeBadge.style.borderColor = 'rgba(245,158,11,0.4)';
        revGarmentTypeBadge.title = 'Garment type could not be confidently determined from top-center. Please choose BLOUSE or CHUDI.';
      }
    }

    revLining.value = order.lining === 'WITH_LINING' ? 'WITH_LINING' : 'WITHOUT_LINING';

    // Update the garment badges
    const garmentType = revGarmentType.value;
    if (reviewGarmentBadge) reviewGarmentBadge.textContent = garmentType || 'SELECT REQUIRED';
    if (reviewMeasGarmentLabel) reviewMeasGarmentLabel.textContent = garmentType || 'SELECT REQUIRED';

    // Redraw annotation regions for the resolved garment type (or default CHUDI) and document
    AnnotationOverlay.draw(garmentType || 'CHUDI', doc);

    // Extract confidence map from extracted data (per-field or overall)
    const confidences = {};
    if (ext?.confidences) {
      Object.assign(confidences, ext.confidences);
    } else if (ext?.measurementConfidences) {
      Object.assign(confidences, ext.measurementConfidences);
    }

    // Measurements — garment-aware template with confidence badges
    const measurements = order.measurements || {};
    renderMeasurementTemplate(garmentType || 'CHUDI', measurements, confidences);

    // Billing
    revTotalAmount.value = order.totalAmount !== undefined && order.totalAmount !== null ? order.totalAmount : '0';
    revAdvanceAmount.value = order.advanceAmount !== undefined && order.advanceAmount !== null ? order.advanceAmount : '0';
    updateBalanceCalc();

    revRemarks.value = '';
    revUpdateProfileCheck.checked = true;

    // Refresh the JSON preview once all fields are populated
    refreshJsonPreview();
  }

  /**
   * Renders the measurement grid from a garment-specific template.
   * @param {string} garmentType  - e.g. 'BLOUSE', 'CHUDI', 'CHURIDAR'
   * @param {Object} values       - existing key→value map to pre-fill
   * @param {Object} confidences  - per-field OCR confidence map (0–1)
   */
  function renderMeasurementTemplate(garmentType, values = {}, confidences = {}) {
    measurementsEditGrid.innerHTML = '';
    const template = MEASUREMENT_TEMPLATES[garmentType] || [];

    // Update garment label inside the measurements card header
    if (reviewMeasGarmentLabel) reviewMeasGarmentLabel.textContent = garmentType;

    if (template.length > 0) {
      // Render canonical template fields (fixed label, hidden key)
      template.forEach(({ key, label }) => {
        addMeasurementRow(key, values[key] || '', label, true, confidences[key]);
      });
      // Append any extra extracted fields that are NOT in the template
      Object.entries(values).forEach(([k, v]) => {
        if (!template.some(t => t.key === k)) {
          addMeasurementRow(k, v, '', false, confidences[k]);
        }
      });
    } else {
      // Unknown garment: render extracted values as editable rows
      const entries = Object.entries(values);
      if (entries.length > 0) {
        entries.forEach(([k, v]) => addMeasurementRow(k, v, '', false, confidences[k]));
      } else {
        // Minimum: one blank row so reviewer can type
        addMeasurementRow('', '', '', false, null);
      }
    }
  }

  /**
   * Adds a single measurement row to the grid.
   * Template rows show a fixed label and store the key in a hidden input.
   * Custom rows show an editable text input for the key.
   * @param {string}  key        - canonical measurement key (e.g. 'F.N.', 'LTH')
   * @param {string}  val        - current value
   * @param {string}  label      - human-readable label (only for template rows)
   * @param {boolean} isTemplate - if true, key is fixed; if false, key is editable
   * @param {number|null} confidence - OCR confidence 0-1, or null if unknown
   */
  function addMeasurementRow(key = '', val = '', label = '', isTemplate = false, confidence = null) {
    const row = document.createElement('div');
    row.className = 'measurement-row';

    // Build confidence badge HTML
    let confClass = 'conf-none';
    let confLabel = '—';
    if (confidence !== null && confidence !== undefined) {
      const confPct = Math.round(confidence * 100);
      confLabel = `${confPct}%`;
      if (confPct >= 80) confClass = 'conf-high';
      else if (confPct >= 50) confClass = 'conf-med';
      else confClass = 'conf-low';
    }
    const confBadgeHtml = `<span class="meas-conf-badge ${confClass}" title="OCR Confidence">${confLabel}</span>`;

    if (isTemplate && label) {
      // Parse "SHORT_KEY (description)" from label. E.g. "LTH (Length)" → shortKey="LTH", hint="Length"
      const parenIdx = label.indexOf('(');
      const shortKey = parenIdx > 0 ? label.slice(0, parenIdx).trim() : (key || label);
      const hintText = parenIdx > 0 ? label.slice(parenIdx + 1).replace(')', '').trim() : '';

      row.innerHTML = `
        <label class="meas-label-fixed" title="${escapeHtml(label)}">
          <span>${escapeHtml(shortKey)}</span>
          ${hintText ? `<span class="meas-label-hint">${escapeHtml(hintText)}</span>` : ''}
        </label>
        <input type="hidden" class="meas-key-input" value="${escapeHtml(key)}">
        <input type="text" class="meas-val-input" value="${escapeHtml(val)}" placeholder="—">
        ${confBadgeHtml}
        <button type="button" class="meas-remove-btn" title="Remove field">&times;</button>
      `;
    } else {
      row.innerHTML = `
        <input type="text" class="meas-key-input" value="${escapeHtml(key)}" placeholder="FIELD" style="text-transform:uppercase;">
        <input type="text" class="meas-val-input" value="${escapeHtml(val)}" placeholder="0">
        ${confBadgeHtml}
        <button type="button" class="meas-remove-btn" title="Remove field">&times;</button>
      `;
    }

    row.querySelector('.meas-remove-btn').addEventListener('click', () => row.remove());
    // Refresh JSON preview on value change
    row.querySelector('.meas-val-input')?.addEventListener('input', refreshJsonPreview);

    // Click-to-Highlight: highlight exact measurement box or measurement region on focus/click
    const valInput = row.querySelector('.meas-val-input');
    const keyInput = row.querySelector('.meas-key-input');
    const highlightMeasBox = () => {
      const currentKey = (keyInput ? keyInput.value : key || '').trim();
      if (currentKey && AnnotationOverlay.currentRegions && AnnotationOverlay.currentRegions['meas_' + currentKey]) {
        AnnotationOverlay.highlight('meas_' + currentKey);
      } else {
        AnnotationOverlay.highlight('measurementRegion');
      }
    };
    if (valInput) {
      valInput.addEventListener('focus', highlightMeasBox);
      valInput.addEventListener('click', highlightMeasBox);
    }
    if (keyInput && keyInput.type !== 'hidden') {
      keyInput.addEventListener('focus', highlightMeasBox);
      keyInput.addEventListener('click', highlightMeasBox);
    }

    measurementsEditGrid.appendChild(row);
  }

  /**
   * Updates the visual processing timeline at the bottom of the left panel.
   * Real-time 5 stages:
   * 1: Upload (Image / PDF Page registered)
   * 2: Scanning (OCR text recognition in progress)
   * 3: Scanned (OCR text captured & fields extracted)
   * 4: Wait to Review (Ready / open in review modal)
   * 5: Approved (Document approved / imported to ERP)
   */
  function updateProcessingSteps(doc) {
    if (!doc) return;

    const docId = Number(doc.id);
    const isScanning = currentlyScanningDocIds.has(docId) ||
                       doc.ocrStatus === 'IN_PROGRESS' ||
                       doc.ocrStatus === 'PROCESSING';

    const hasOcr = !!(doc.rawOcrText && doc.rawOcrText.trim().length > 0) ||
                   doc.ocrStatus === 'OCR_COMPLETED' ||
                   doc.ocrStatus === 'COMPLETED';

    const hasExtract = !!(doc.extractedDataJson) ||
                       doc.extractionStatus === 'COMPLETED';

    const isVerified = doc.reviewStatus === 'VERIFIED';
    const isImported = doc.reviewStatus === 'IMPORTED' || doc.importStatus === 'IMPORTED';
    const isApproved = isVerified || isImported;

    const checkmarkSvg = `<svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"/></svg>`;
    const scannerSpinnerHtml = `<span class="scanner-spinner"></span>`;

    const setStep = (id, lineId, state, stepNumber, activeHtml = null) => {
      const el = document.getElementById(id);
      const ln = lineId ? document.getElementById(lineId) : null;
      if (el) {
        el.className = `ps-item${state === 'done' ? ' ps-done' : state === 'active' ? ' ps-active' : state === 'complete' ? ' ps-complete' : ''}`;
        const bubble = el.querySelector('.ps-bubble');
        if (bubble) {
          if (state === 'done' || state === 'complete') {
            bubble.innerHTML = checkmarkSvg;
          } else if (state === 'active' && activeHtml) {
            bubble.innerHTML = activeHtml;
          } else {
            bubble.textContent = String(stepNumber);
          }
        }
      }
      if (ln) {
        ln.className = `ps-line${(state === 'done' || state === 'complete') ? ' ps-line-done' : ''}`;
      }
    };

    // 1. Upload - Always done for documents in migration batch
    setStep('psStep1', 'psLine1', 'done', 1);

    // 2. Scanning - Active if currently scanning, done if OCR completed or extracted or approved
    if (isScanning) {
      setStep('psStep2', 'psLine2', 'active', 2, scannerSpinnerHtml);
    } else if (hasOcr || hasExtract || isApproved) {
      setStep('psStep2', 'psLine2', 'done', 2);
    } else {
      setStep('psStep2', 'psLine2', 'pending', 2);
    }

    // 3. Scanned - Done if extraction or OCR finished and not actively scanning
    if (isScanning) {
      setStep('psStep3', 'psLine3', 'pending', 3);
    } else if (hasExtract || (hasOcr && !isScanning)) {
      setStep('psStep3', 'psLine3', 'done', 3);
    } else {
      setStep('psStep3', 'psLine3', 'pending', 3);
    }

    // 4. Wait to Review - Done if already approved, active if ready/being reviewed in this modal
    if (isApproved) {
      setStep('psStep4', 'psLine4', 'done', 4);
    } else if (hasExtract || hasOcr) {
      setStep('psStep4', 'psLine4', 'active', 4);
    } else {
      setStep('psStep4', 'psLine4', 'pending', 4);
    }

    // 5. Approved - Complete/Done if approved/verified/imported, else pending
    if (isApproved) {
      setStep('psStep5', null, 'complete', 5);
    } else {
      setStep('psStep5', null, 'pending', 5);
    }
  }

  function updateBalanceCalc() {
    const total = parseFloat(revTotalAmount.value) || 0;
    const adv = parseFloat(revAdvanceAmount.value) || 0;
    revBalanceAmount.value = String(Math.max(0, total - adv));
    refreshJsonPreview();
  }

  /**
   * Reads all review form inputs and renders a live JSON preview.
   * Called whenever any form field changes and on initial form load.
   */
  function refreshJsonPreview() {
    if (!revJsonPreview) return;
    const measurements = {};
    measurementsEditGrid.querySelectorAll('.measurement-row').forEach(row => {
      const k = row.querySelector('.meas-key-input')?.value?.trim();
      const v = row.querySelector('.meas-val-input')?.value?.trim();
      if (k && v) measurements[k] = v;
    });

    const preview = {
      customerName: revCustomerName?.value || null,
      mobileNo:     revCustomerMobile?.value || null,
      date:         revOrderDate?.value || null,
      deliveryDate: revDeliveryDate?.value || null,
      erode:        revErode?.value || null,
      cloth:        revCloth?.value || null,
      garmentType:  revGarmentType?.value || null,
      measurements
    };
    revJsonPreview.textContent = JSON.stringify(preview, null, 2);
    updateReviewCompletionBar();
  }

  function updateReviewCompletionBar() {
    if (!currentDocInReview) return;
    const measurements = {};
    measurementsEditGrid.querySelectorAll('.measurement-row').forEach(row => {
      const k = row.querySelector('.meas-key-input')?.value?.trim();
      const v = row.querySelector('.meas-val-input')?.value?.trim();
      if (k && v && v !== '—' && v !== '-' && v !== '--') {
        measurements[k] = v;
      }
    });

    const formSummary = {
      customerName: revCustomerName?.value || '',
      customerMobile: revCustomerMobile?.value || '',
      garmentType: revGarmentType?.value || '',
      orderDate: revOrderDate?.value || '',
      totalAmount: revTotalAmount?.value || '',
      measurements: measurements
    };

    const comp = computeDocFieldCompletion(currentDocInReview, formSummary);

    if (reviewDocOcrConf)  reviewDocOcrConf.textContent  = `Filled: ${comp.pct}% (${comp.filled}/${comp.total})`;
    if (reviewDocOcrConf2) reviewDocOcrConf2.textContent = `Filled: ${comp.pct}% (${comp.filled}/${comp.total})`;

    const warningBar = document.getElementById('reviewWarningBar');
    const warningText = document.getElementById('reviewWarningText');

    if (comp.pct < 50) {
      if (warningBar) {
        warningBar.style.background = 'rgba(239, 68, 68, 0.15)';
        warningBar.style.borderColor = 'rgba(239, 68, 68, 0.4)';
        warningBar.style.color = '#f87171';
      }
      if (warningText) {
        warningText.innerHTML = `<strong>⚠️ Incomplete Document (${comp.pct}%):</strong> Only ${comp.filled} of ${comp.total} fields are filled. Less than 50% cannot be processed. Please fill in missing customer and measurement fields before approving.`;
      }
      if (btnApproveReview) {
        btnApproveReview.disabled = true;
        btnApproveReview.title = `Cannot process: only ${comp.pct}% fields filled (minimum 50% required)`;
        btnApproveReview.style.opacity = '0.5';
        btnApproveReview.style.cursor = 'not-allowed';
      }
      if (btnApproveAndImport) {
        btnApproveAndImport.disabled = true;
        btnApproveAndImport.title = `Cannot process: only ${comp.pct}% fields filled (minimum 50% required)`;
        btnApproveAndImport.style.opacity = '0.5';
        btnApproveAndImport.style.cursor = 'not-allowed';
      }
      if (btnApproveAndNextPage) {
        btnApproveAndNextPage.disabled = true;
        btnApproveAndNextPage.title = `Cannot process: only ${comp.pct}% fields filled (minimum 50% required)`;
        btnApproveAndNextPage.style.opacity = '0.5';
        btnApproveAndNextPage.style.cursor = 'not-allowed';
      }
    } else {
      if (warningBar) {
        warningBar.style.background = '';
        warningBar.style.borderColor = '';
        warningBar.style.color = '';
      }
      if (warningText) {
        warningText.innerHTML = `Ready to process: ${comp.filled}/${comp.total} fields filled (${comp.pct}%). Verify data and make corrections if needed before importing.`;
      }
      if (btnApproveReview) {
        btnApproveReview.disabled = false;
        btnApproveReview.title = 'Approve and mark document as VERIFIED';
        btnApproveReview.style.opacity = '';
        btnApproveReview.style.cursor = '';
      }
      if (btnApproveAndImport) {
        btnApproveAndImport.disabled = false;
        btnApproveAndImport.title = 'Approve and import directly into ERP tables';
        btnApproveAndImport.style.opacity = '';
        btnApproveAndImport.style.cursor = '';
      }
      if (btnApproveAndNextPage) {
        btnApproveAndNextPage.disabled = false;
        btnApproveAndNextPage.title = 'Approve this document/page and move to next';
        btnApproveAndNextPage.style.opacity = '';
        btnApproveAndNextPage.style.cursor = '';
      }
    }
  }


  async function loadCustomerMatches(docId) {
    matchStatusBadge.textContent = 'Checking matches...';
    matchStatusBadge.className = 'match-badge';
    matchCandidatesContainer.style.display = 'none';
    matchCandidatesList.innerHTML = '';

    try {
      const res = await Api.get(API.MIGRATION_DOCUMENT_MATCHES(docId));
      const matchResult = res.data || res || {};

      if (matchResult.matchType === 'EXACT_MOBILE' && matchResult.candidates && matchResult.candidates.length > 0) {
        const candidate = matchResult.candidates[0];
        matchedCustomerMobile = candidate.mobile;
        matchCandidatesContainer.style.display = 'block';

        if (matchResult.measurementExists || candidate.measurementExists || currentDocInReview?.measurementExists) {
          matchStatusBadge.textContent = 'Existing Measurement';
          matchStatusBadge.className = 'match-badge';
          matchStatusBadge.style.background = 'rgba(245,158,11,0.15)';
          matchStatusBadge.style.color = '#fbbf24';
          matchStatusBadge.style.borderColor = 'rgba(245,158,11,0.35)';

          const gType = matchResult.existingGarmentType || revGarmentType?.value || 'garment';
          matchCandidatesList.innerHTML = `
            <div class="match-candidate-item">
              <div>
                <strong>${escapeHtml(candidate.name)}</strong> (${candidate.mobile})
                <div style="font-size:11px; color:var(--text-muted);">${candidate.totalOrders} previous order(s) on record</div>
              </div>
              <span class="badge" style="font-size:10px; background:rgba(245,158,11,0.2); color:#fbbf24; border:1px solid rgba(245,158,11,0.4);">MEASUREMENT EXISTS</span>
            </div>
            <div class="existing-meas-modal-banner">
              <i class="ri-alert-fill"></i>
              <div>
                <strong style="color:#f59e0b;">Measurement Already Exists in ERP:</strong>
                <div>An active measurement profile for <strong>${escapeHtml(gType)}</strong> is already saved in ERP for this customer. Approving will override the existing measurements.</div>
              </div>
            </div>
          `;

          if (btnApproveReview) {
            btnApproveReview.innerHTML = `<i class="ri-refresh-line"></i> Approve & Override`;
            btnApproveReview.title = 'Approve corrections and override existing ERP measurement profile';
          }
          if (btnApproveAndImport) {
            btnApproveAndImport.innerHTML = `<i class="ri-check-double-line"></i> Override & Import Now`;
            btnApproveAndImport.title = 'Approve, override existing measurement, and import to ERP';
          }
          if (revUpdateProfileCheck) {
            revUpdateProfileCheck.checked = true;
          }
        } else {
          matchStatusBadge.textContent = 'Existing ERP Match';
          matchStatusBadge.className = 'match-badge matched';
          matchStatusBadge.style.background = '';
          matchStatusBadge.style.color = '';
          matchStatusBadge.style.borderColor = '';

          matchCandidatesList.innerHTML = `
            <div class="match-candidate-item">
              <div>
                <strong>${escapeHtml(candidate.name)}</strong> (${candidate.mobile})
                <div style="font-size:11px; color:var(--text-muted);">${candidate.totalOrders} previous order(s) on record</div>
              </div>
              <span class="badge badge-success" style="font-size:10px;">EXACT MOBILE</span>
            </div>
          `;

          if (btnApproveReview) {
            btnApproveReview.innerHTML = 'Approve & Mark Verified';
            btnApproveReview.title = '';
          }
          if (btnApproveAndImport) {
            btnApproveAndImport.innerHTML = 'Approve & Import Now';
            btnApproveAndImport.title = '';
          }
        }
      } else {
        matchStatusBadge.textContent = 'New Customer';
        matchStatusBadge.className = 'match-badge new-customer';
        matchStatusBadge.style.background = '';
        matchStatusBadge.style.color = '';
        matchStatusBadge.style.borderColor = '';
        matchCandidatesContainer.style.display = 'none';
        matchedCustomerMobile = null;

        if (btnApproveReview) {
          btnApproveReview.innerHTML = 'Approve & Mark Verified';
          btnApproveReview.title = '';
        }
        if (btnApproveAndImport) {
          btnApproveAndImport.innerHTML = 'Approve & Import Now';
          btnApproveAndImport.title = '';
        }
      }
    } catch (err) {
      matchStatusBadge.textContent = 'No Match';
      matchStatusBadge.className = 'match-badge';
      matchStatusBadge.style.background = '';
      matchStatusBadge.style.color = '';
      matchStatusBadge.style.borderColor = '';
    }
  }

  // Zoom & Rotation helpers
  let rotationAngle = 0;

  function updateTransform() {
    // Apply to annotationHost so the SVG overlay transforms together with the image
    if (!annotationHost || annotationHost.style.display === 'none') {
      if (reviewDocImage) {
        reviewDocImage.style.transform = `rotate(${rotationAngle}deg) scale(${zoomLevel})`;
      }
      return;
    }

    // When rotated 90° or 270°, fit rotated image within viewport so it is not clipped
    const isRotated90 = Math.abs(rotationAngle % 180) === 90;
    let fitScale = 1.0;
    if (isRotated90 && viewerContainer && reviewDocImage) {
      const vW = viewerContainer.clientWidth - 32;
      const vH = viewerContainer.clientHeight - 32;
      const iW = reviewDocImage.offsetWidth || 500;
      const iH = reviewDocImage.offsetHeight || 300;
      if (iW > 0 && iH > 0 && vW > 0 && vH > 0) {
        const scaleW = vW / iH;
        const scaleH = vH / iW;
        fitScale = Math.min(1.0, scaleW, scaleH);
      }
    }

    const totalScale = (zoomLevel * fitScale).toFixed(3);
    annotationHost.style.transform = `rotate(${rotationAngle}deg) scale(${totalScale})`;
  }

  function adjustZoom(delta) {
    zoomLevel = Math.max(0.5, Math.min(3.0, zoomLevel + delta));
    updateTransform();
  }

  function rotateImage(delta) {
    rotationAngle = (rotationAngle + delta) % 360;
    updateTransform();
  }

  function resetZoom() {
    zoomLevel = 1.0;
    rotationAngle = 0;
    updateTransform();
  }

  document.getElementById('zoomInBtn')?.addEventListener('click', () => adjustZoom(0.2));
  document.getElementById('zoomOutBtn')?.addEventListener('click', () => adjustZoom(-0.2));
  document.getElementById('rotateLeftBtn')?.addEventListener('click', () => rotateImage(-90));
  document.getElementById('rotateRightBtn')?.addEventListener('click', () => rotateImage(90));
  document.getElementById('resetZoomBtn')?.addEventListener('click', () => resetZoom());
  document.getElementById('toggleRawOcrBtn')?.addEventListener('click', () => {
    const box = document.getElementById('reviewRawOcrBox');
    if (box) {
      box.style.display = (box.style.display === 'none' || !box.style.display) ? 'block' : 'none';
    }
  });
  document.getElementById('btnFlipPortrait')?.addEventListener('click', () => {
    // Rotating by 270 or running rescan auto-flips landscape to portrait and re-extracts
    rotationAngle = 270;
    handleRescanDocument();
  });

  async function handleRescanDocument() {
    if (!currentDocInReview || !currentDocInReview.id) return;

    const docId = currentDocInReview.id;
    const btnRescanDoc = document.getElementById('btnRescanDoc');
    const btnRescanFooter = document.getElementById('btnRescanDocFooter');

    const origToolbarHtml = btnRescanDoc ? btnRescanDoc.innerHTML : '';
    const origFooterHtml = btnRescanFooter ? btnRescanFooter.innerHTML : '';

    try {
      currentlyScanningDocIds.add(Number(docId));
      renderDocumentsTable();
      updateProcessingSteps(currentDocInReview);
      if (btnRescanDoc) {
        btnRescanDoc.disabled = true;
        btnRescanDoc.innerHTML = `<span class="spinner" style="width:11px;height:11px;border-width:1.5px;display:inline-block;vertical-align:middle;margin-right:4px;"></span> Re-scanning...`;
      }
      if (btnRescanFooter) {
        btnRescanFooter.disabled = true;
        btnRescanFooter.innerHTML = `<span class="spinner" style="width:12px;height:12px;border-width:1.5px;display:inline-block;vertical-align:middle;margin-right:4px;"></span> Re-scanning...`;
      }

      if (viewerLoading) {
        viewerLoading.style.display = 'flex';
        const span = viewerLoading.querySelector('span');
        if (span) span.textContent = 'Re-running OCR recognition & extracting fields...';
      }

      // If user rotated image in viewer, pass that rotation angle
      const normRotate = ((rotationAngle % 360) + 360) % 360;

      // Call re-scan API (1-minute timeout limit)
      const rescanUrl = `${API.MIGRATION_DOCUMENT_RESCAN(docId)}?rotate=${normRotate}`;
      const res = await withTimeout(Api.post(rescanUrl), 60000, 'Re-scan timed out after 1 minute.');
      currentDocInReview = res.data || res;

      // Reset local rotation transform and reload image preview to reflect portrait orientation on disk
      rotationAngle = 0;
      zoomLevel = 1.0;
      updateTransform();
      await loadFilePreview(docId, currentDocInReview.fileName);

      const docSumm2 = getDocExtractedSummary(currentDocInReview);
      const comp2 = computeDocFieldCompletion(currentDocInReview, docSumm2);
      if (reviewDocOcrConf)  reviewDocOcrConf.textContent  = `Filled: ${comp2.pct}% (${comp2.filled}/${comp2.total})`;
      if (reviewDocOcrConf2) reviewDocOcrConf2.textContent = `Filled: ${comp2.pct}% (${comp2.filled}/${comp2.total})`;

      // Update processing steps timeline
      updateProcessingSteps(currentDocInReview);

      // Update Raw OCR text
      if (reviewRawOcrText) {
        reviewRawOcrText.textContent = currentDocInReview.rawOcrText || 'No raw OCR text captured for this document.';
      }

      // Re-populate form with newly extracted data
      populateReviewForm(currentDocInReview);

      // Reload ERP customer matches
      loadCustomerMatches(docId);

      // Refresh documents list & stats in background
      await loadBatchDocuments();
      await loadOverallStatistics();

      Toast.success('Document re-scanned and fields updated successfully!');
    } catch (err) {
      console.error('Re-scan failed:', err);
      cancelReviewProcessAndPromptReupload(docId, `Re-scan failed or timed out: ${err.message}. The process was cancelled. Please re-upload this document.`);
    } finally {
      currentlyScanningDocIds.delete(Number(docId));
      updateProcessingSteps(currentDocInReview);
      if (btnRescanDoc) {
        btnRescanDoc.disabled = false;
        btnRescanDoc.innerHTML = origToolbarHtml;
      }
      if (btnRescanFooter) {
        btnRescanFooter.disabled = false;
        btnRescanFooter.innerHTML = origFooterHtml;
      }
      if (viewerLoading) {
        viewerLoading.style.display = 'none';
        const span = viewerLoading.querySelector('span');
        if (span) span.textContent = 'Loading scan image...';
      }
    }
  }

  document.getElementById('btnRescanDoc')?.addEventListener('click', handleRescanDocument);
  document.getElementById('btnRescanDocFooter')?.addEventListener('click', handleRescanDocument);

  function closeReviewModal() {
    clearReviewWatchdog();
    if (currentBlobUrl) {
      URL.revokeObjectURL(currentBlobUrl);
      currentBlobUrl = null;
    }
    AnnotationOverlay.clear();
    if (annotationHost) annotationHost.style.display = 'none';
    reviewModal.style.display = 'none';
    currentDocInReview = null;
    siblingPagesInReview = [];
    if (reviewPageNavBar) reviewPageNavBar.style.display = 'none';
    if (reviewFooterNav) reviewFooterNav.style.display = 'none';
    if (btnApproveAllPages) btnApproveAllPages.style.display = 'none';
    if (btnApproveAndNextPage) btnApproveAndNextPage.style.display = 'none';
    resetZoom();
  }

  // Gather Review Corrections Payload
  function buildReviewPayload(action) {
    const customerMobile = (revCustomerMobile.value || '').trim();
    const customerName = (revCustomerName.value || '').trim();
    const orderDate = revOrderDate.value || '';
    const garmentType = revGarmentType.value;
    const lining = revLining.value;
    const total = parseFloat(revTotalAmount.value) || 0;
    const advance = parseFloat(revAdvanceAmount.value) || 0;

    if (action === 'APPROVED') {
      let hasError = false;
      let firstErrorElement = null;

      function flagError(inputEl, rowEl) {
        if (!inputEl) return;
        inputEl.classList.add('field-error');
        if (rowEl) rowEl.classList.add('row-error');
        if (!firstErrorElement) firstErrorElement = inputEl;

        const clearError = () => {
          inputEl.classList.remove('field-error');
          if (rowEl) rowEl.classList.remove('row-error');
        };
        inputEl.addEventListener('input', clearError, { once: true });
        inputEl.addEventListener('change', clearError, { once: true });
      }

      // 1. Validate Customer Details
      if (!customerName) {
        flagError(revCustomerName);
        hasError = true;
      }
      if (!customerMobile) {
        flagError(revCustomerMobile);
        hasError = true;
      }

      // 2. Validate Order Date & Garment Type
      if (!orderDate) {
        flagError(revOrderDate);
        hasError = true;
      }
      if (!garmentType || garmentType === 'NEEDS_REVIEW') {
        flagError(revGarmentType);
        hasError = true;
      }

      // 3. Validate ALL Tailoring Measurements: empty data MUST be filled before saving!
      const rows = measurementsEditGrid.querySelectorAll('.measurement-row');
      let emptyMeasCount = 0;
      const measurements = {};

      rows.forEach(row => {
        const kInput = row.querySelector('.meas-key-input');
        const vInput = row.querySelector('.meas-val-input');
        const k = kInput?.value?.trim();
        const v = vInput?.value?.trim();

        if (!k) {
          flagError(kInput, row);
          hasError = true;
          emptyMeasCount++;
          return;
        }

        // Must have non-empty numeric or valid measurement value
        if (!v || v === '—' || v === '-' || v === '--') {
          flagError(vInput, row);
          hasError = true;
          emptyMeasCount++;
        } else {
          const isCanonical = /[.\-]/.test(k);
          measurements[isCanonical ? k : k.toUpperCase()] = v;
        }
      });

      if (rows.length === 0) {
        throw new Error('Please add tailoring measurements before saving.');
      }

      if (hasError) {
        if (firstErrorElement) {
          firstErrorElement.scrollIntoView({ behavior: 'smooth', block: 'center' });
          setTimeout(() => firstErrorElement.focus(), 150);
        }

        if (!customerName || !customerMobile) {
          throw new Error('Please fill in Customer Name and Mobile number before saving.');
        }
        if (!garmentType || garmentType === 'NEEDS_REVIEW') {
          throw new Error('Please select Garment Type (BLOUSE or CHUDI) before saving.');
        }
        if (emptyMeasCount > 0) {
          throw new Error(`All data must be filled before saving! Please fill in ${emptyMeasCount} empty measurement field${emptyMeasCount > 1 ? 's' : ''} (or remove unused with ✕).`);
        }
        throw new Error('Please fill all empty data before saving.');
      }

      // 4. Enforce minimum 50% fields filled requirement
      const currentReviewSummary = {
        customerMobile: customerMobile,
        customerName: customerName,
        garmentType: garmentType,
        orderDate: orderDate,
        totalAmount: total,
        measurements: measurements
      };
      const comp = computeDocFieldCompletion(currentDocInReview, currentReviewSummary);
      if (comp.pct < 50) {
        throw new Error(`Cannot process document: only ${comp.pct}% of fields are filled (${comp.filled}/${comp.total}). Minimum 50% fields must be filled before processing.`);
      }
    }

    // For non-approval actions (REJECTED / NEEDS_REWORK), gather whatever is present
    const measurements = {};
    measurementsEditGrid.querySelectorAll('.measurement-row').forEach(row => {
      const k = row.querySelector('.meas-key-input')?.value?.trim();
      const v = row.querySelector('.meas-val-input')?.value?.trim();
      if (k && v && v !== '—' && v !== '-') {
        const isCanonical = /[.\-]/.test(k);
        measurements[isCanonical ? k : k.toUpperCase()] = v;
      }
    });

    const correctedData = {
      customer: {
        customerName: customerName,
        nameConfidence: 1.0,
        customerMobile: customerMobile,
        mobileConfidence: 1.0,
        needsReview: false
      },
      order: {
        orderDate: orderDate || new Date().toISOString().split('T')[0],
        orderDateConfidence: 1.0,
        deliveryDate: revDeliveryDate?.value || null,
        deliveryDateConfidence: revDeliveryDate?.value ? 1.0 : null,
        garmentType: garmentType,
        garmentTypeConfidence: 1.0,
        lining: lining,
        measurements: measurements,
        measurementConfidences: {},
        totalAmount: total,
        totalAmountConfidence: 1.0,
        advanceAmount: advance,
        advanceAmountConfidence: 1.0,
        paymentDateKnown: true,
        needsReview: false,
        erode: (revErode?.value || '').trim() || null,
        cloth: (revCloth?.value || '').trim() || null
      },
      overallConfidence: 1.0
    };

    return {
      action: action,
      correctedDataJson: JSON.stringify(correctedData),
      remarks: (revRemarks.value || '').trim(),
      matchedCustomerMobile: matchedCustomerMobile || null,
      updateCustomerProfile: revUpdateProfileCheck.checked
    };
  }

  async function submitReview(action) {
    if (!currentDocInReview) return;

    try {
      const payload = buildReviewPayload(action);
      btnApproveReview.disabled = true;
      btnRejectDocument.disabled = true;
      btnReworkDocument.disabled = true;

      const res = await Api.post(API.MIGRATION_DOCUMENT_REVIEW(currentDocInReview.id), payload);
      if (action === 'APPROVED') {
        currentDocInReview.reviewStatus = 'VERIFIED';
        updateProcessingSteps(currentDocInReview);
      }
      Toast.success(`Document marked as ${action}`);
      closeReviewModal();
      await loadBatchDocuments();
      await loadOverallStatistics();
    } catch (err) {
      Toast.error(err.message || 'Failed to save review decision');
    } finally {
      btnApproveReview.disabled = false;
      btnRejectDocument.disabled = false;
      btnReworkDocument.disabled = false;
    }
  }

  async function submitReviewAndImport() {
    if (!currentDocInReview) return;

    try {
      const payload = buildReviewPayload('APPROVED');
      btnApproveAndImport.disabled = true;
      btnApproveAndImport.textContent = 'Importing...';

      const res = await Api.post(API.MIGRATION_DOCUMENT_IMPORT(currentDocInReview.id), payload);
      currentDocInReview.reviewStatus = 'IMPORTED';
      currentDocInReview.importStatus = 'IMPORTED';
      updateProcessingSteps(currentDocInReview);
      const summary = res.data || res;
      Toast.success(`Approved & Imported! Created Order #${summary.orderNumber} for ${summary.customerName}`);
      closeReviewModal();
      await loadBatchDocuments();
      await loadBatches(activeBatchId);
      await loadOverallStatistics();
    } catch (err) {
      Toast.error(err.message || 'Import failed');
    } finally {
      btnApproveAndImport.disabled = false;
      btnApproveAndImport.textContent = 'Approve & Import Now';
    }
  }

});
