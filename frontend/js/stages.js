/**
 * Ritham ERP — Shared Stage Registry (stages.js)
 * Loads production stages dynamically from the DB API (/api/production-stages)
 * and exposes global helpers for stage badges, labels, colors, and lists.
 */

'use strict';

const StageRegistry = (function() {
  let _cachedStages = [];
  let _initialized = false;
  let _initPromise = null;

  /**
   * Fetch active production stages from the database API
   */
  async function init() {
    if (_initPromise) return _initPromise;

    _initPromise = (async () => {
      try {
        if (typeof Api !== 'undefined') {
          const res = await Api.get(`${API.PRODUCTION_STAGES}?activeOnly=true`);
          const list = Array.isArray(res) ? res : (res?.data || []);
          _cachedStages = Array.isArray(list) ? list.sort((a, b) => (a.seqOrder || a.displayOrder || 0) - (b.seqOrder || b.displayOrder || 0)) : [];
          _initialized = true;
        }
      } catch (err) {
        _cachedStages = [];
      }
      return _cachedStages;
    })();

    return _initPromise;
  }

  /**
   * Get all active stages sorted by display order
   */
  function getAll() {
    return _cachedStages;
  }

  /**
   * Get a specific stage by stageKey (e.g. 'PATTERN_MAKING')
   */
  function getByKey(key) {
    if (!key) return null;
    const kUpper = String(key).toUpperCase();
    return _cachedStages.find(s => (s.stageKey || '').toUpperCase() === kUpper || (s.title || '').toUpperCase() === kUpper);
  }

  /**
   * Returns styled HTML badge for a stage key
   */
  function getBadgeHtml(stageKey, titleOverride = null) {
    const stg = getByKey(stageKey);
    const title = titleOverride || (stg ? stg.title : stageKey || 'Unassigned');
    const color = stg ? stg.color || '#818CF8' : '#A1A1AA';
    const bg    = stg ? stg.bgColor || 'rgba(161, 161, 170, 0.15)' : 'rgba(161, 161, 170, 0.15)';
    const iconLetter = stg ? (stg.icon || (stg.title ? stg.title.charAt(0).toUpperCase() : '')) : '';
    const iconBadge = iconLetter
      ? `<span style="display:inline-flex;align-items:center;justify-content:center;width:16px;height:16px;border-radius:4px;background:${color}25;font-size:10px;font-weight:800;line-height:1;border:1px solid ${color}50;flex-shrink:0;">${iconLetter}</span>`
      : '';

    return `<span class="badge" style="background:${bg}; color:${color}; border:1px solid ${color}40; font-size:11px; font-weight:600; padding:4px 10px; border-radius:6px; display:inline-flex; align-items:center; gap:6px;">${iconBadge}<span>${title}</span></span>`;
  }

  /**
   * Returns metadata map of { STAGE_KEY: { label, color, bg } } for charts and filters
   */
  function getStageMeta() {
    const map = {
      'PENDING':   { label: 'Pending',   color: '#F59E0B', bg: 'rgba(245, 158, 11, 0.15)' },
      'COMPLETED': { label: 'Completed', color: '#22C55E', bg: 'rgba(34, 197, 94, 0.15)' },
      'DELIVERED': { label: 'Delivered', color: '#10B981', bg: 'rgba(16, 185, 129, 0.15)' },
      'CANCELLED': { label: 'Cancelled', color: '#EF4444', bg: 'rgba(239, 68, 68, 0.15)' }
    };

    _cachedStages.forEach(s => {
      map[s.stageKey] = {
        label: s.title,
        color: s.color || '#818CF8',
        bg: s.bgColor || 'rgba(99, 102, 241, 0.15)'
      };
    });

    return map;
  }

  /**
   * Returns human-readable stage title for a key
   */
  function getStageTitle(key) {
    if (!key) return '';
    const stg = getByKey(key);
    if (stg) return stg.title;
    return String(key).replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, l => l.toUpperCase());
  }

  // Auto-initialize on load if document ready
  if (typeof document !== 'undefined') {
    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', () => { init(); });
    } else {
      init();
    }
  }

  return {
    init,
    getAll,
    getByKey,
    getStageTitle,
    getBadgeHtml,
    getStageMeta
  };
})();

if (typeof window !== 'undefined') {
  window.StageRegistry = StageRegistry;
}
