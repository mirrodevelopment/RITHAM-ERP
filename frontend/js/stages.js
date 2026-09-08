/**
 * Ritham ERP — Shared Stage Registry (stages.js)
 * Loads production stages dynamically from the DB API (/api/production-stages)
 * and exposes global helpers for stage badges, labels, colors, and lists.
 */

'use strict';

const StageRegistry = (function() {
  // Default fallback stage list (used if network fails before API response)
  const DEFAULT_STAGES = [
    { id: 1,  stageKey: 'DESIGNING',           title: 'Designing',                icon: null, displayOrder: 1,  color: '#818CF8', bgColor: 'rgba(99, 102, 241, 0.15)' },
    { id: 2,  stageKey: 'LINING',              title: 'Lining',                   icon: null, displayOrder: 2,  color: '#A78BFA', bgColor: 'rgba(167, 139, 250, 0.15)' },
    { id: 3,  stageKey: 'HAND_MACHINE_WORK',   title: 'Hand Work / Machine Work', icon: null, displayOrder: 3,  color: '#EC4899', bgColor: 'rgba(236, 72, 153, 0.15)' },
    { id: 4,  stageKey: 'INITIAL_IRONING',     title: 'Initial Ironing',          icon: null, displayOrder: 4,  color: '#F59E0B', bgColor: 'rgba(245, 158, 11, 0.15)' },
    { id: 5,  stageKey: 'CUTTING',             title: 'Cutting',                  icon: null, displayOrder: 5,  color: '#EF4444', bgColor: 'rgba(239, 68, 68, 0.15)' },
    { id: 6,  stageKey: 'STRETCHING',          title: 'Stretching',               icon: null, displayOrder: 6,  color: '#14B8A6', bgColor: 'rgba(20, 184, 166, 0.15)' },
    { id: 7,  stageKey: 'STITCHING',           title: 'Stitching',                icon: null, displayOrder: 7,  color: '#3B82F6', bgColor: 'rgba(59, 130, 246, 0.15)' },
    { id: 8,  stageKey: 'HEMMING',             title: 'Hemming',                  icon: null, displayOrder: 8,  color: '#6366F1', bgColor: 'rgba(99, 102, 241, 0.15)' },
    { id: 9,  stageKey: 'FINAL_IRONING',       title: 'Final Ironing',            icon: null, displayOrder: 9,  color: '#FB923C', bgColor: 'rgba(251, 146, 60, 0.15)' },
    { id: 10, stageKey: 'QUALITY_CHECK',        title: 'Quality Check (QC)',       icon: null, displayOrder: 10, color: '#10B981', bgColor: 'rgba(16, 185, 129, 0.15)' },
    { id: 11, stageKey: 'READY_TO_DELIVERY',   title: 'Ready to Delivery',        icon: null, displayOrder: 11, color: '#06B6D4', bgColor: 'rgba(6, 182, 212, 0.15)' },
    { id: 12, stageKey: 'DELIVERY',            title: 'Delivery',                 icon: null, displayOrder: 12, color: '#22C55E', bgColor: 'rgba(34, 197, 94, 0.15)' }
  ];

  let _cachedStages = [...DEFAULT_STAGES];
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
          if (list && list.length > 0) {
            _cachedStages = list.sort((a, b) => (a.displayOrder || 0) - (b.displayOrder || 0));
            _initialized = true;
          }
        }
      } catch (err) {
        // Silently fallback to default stage list if offline or API unready
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

    return `<span class="badge" style="background:${bg}; color:${color}; border:1px solid ${color}40; font-size:11px; font-weight:600; padding:4px 10px; border-radius:6px; display:inline-flex; align-items:center; gap:6px;">${title}</span>`;
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
    getBadgeHtml,
    getStageMeta
  };
})();

if (typeof window !== 'undefined') {
  window.StageRegistry = StageRegistry;
}
