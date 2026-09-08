/**
 * Ritham ERP — Theme Manager & Switcher Module
 * 
 * Supports 3 Themes:
 *  1. Dark (Black & White Luxe - Default)
 *  2. Light (Clean High-Contrast)
 *  3. Midnight (Royal Navy & Indigo Glass)
 */

'use strict';

window.ThemeManager = window.ThemeManager || (function() {

  const THEMES = [
    { id: 'white-black', name: 'White & Black (Luxe)',  icon: '⚪' },
    { id: 'dark',        name: 'Black & White (Dark)',  icon: '🌙' },
    { id: 'rosegold',    name: 'Rose Gold & Cashmere',  icon: '🌸' },
    { id: 'midnight',    name: 'Midnight Royal',        icon: '🌌' }
  ];

  function getSavedTheme() {
    try {
      return localStorage.getItem('ritham_theme') || 'white-black';
    } catch (e) {
      return 'white-black';
    }
  }

  function applyTheme(themeId) {
    const validTheme = THEMES.find(t => t.id === themeId) ? themeId : 'white-black';
    document.documentElement.setAttribute('data-theme', validTheme);
    try {
      localStorage.setItem('ritham_theme', validTheme);
    } catch (e) {}

    // Update active state on any theme picker elements in header
    document.querySelectorAll('.theme-option-btn').forEach(btn => {
      const isActive = btn.dataset.themeId === validTheme;
      btn.classList.toggle('active', isActive);
      btn.style.backgroundColor = isActive ? 'var(--bg-hover, rgba(255,255,255,0.08))' : 'transparent';
    });

    document.querySelectorAll('.theme-check-icon').forEach(chk => {
      chk.style.opacity = (chk.dataset.themeCheck === validTheme) ? '1' : '0';
    });

    const themeLabelEl = document.getElementById('currentThemeLabel');
    if (themeLabelEl) {
      const cur = THEMES.find(t => t.id === validTheme) || THEMES[0];
      themeLabelEl.textContent = `${cur.icon} ${cur.name}`;
    }

    // Broadcast theme change event so components/charts can update colors if needed
    window.dispatchEvent(new CustomEvent('themeChanged', { detail: { theme: validTheme } }));
  }

  function init() {
    applyTheme(getSavedTheme());
  }

  // Execute immediately to prevent theme flash
  init();

  const instance = {
    THEMES,
    getSavedTheme,
    applyTheme,
    init
  };

  if (typeof window !== 'undefined') {
    window.ThemeManager = instance;
  }
  return instance;
})();

if (typeof window !== 'undefined') {
  window.ThemeManager = ThemeManager;
}
