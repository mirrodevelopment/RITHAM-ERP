/**
 * Ritham ERP — Design Details Controller (design.js)
 */

'use strict';

document.addEventListener('DOMContentLoaded', () => {
  if (!Router.protect()) return;
  Sidebar.init({ activePage: 'design' });
  Header.init({ title: 'Design Details' });
});
