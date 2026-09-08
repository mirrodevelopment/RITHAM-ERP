/**
 * Ritham ERP — Material Procurement Controller (material.js)
 */

'use strict';

document.addEventListener('DOMContentLoaded', () => {
  if (!Router.protect()) return;
  Sidebar.init({ activePage: 'material' });
  Header.init({ title: 'Material Procurement' });
});
