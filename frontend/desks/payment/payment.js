/**
 * Ritham ERP — Payment Records Controller (payment.js)
 */

'use strict';

document.addEventListener('DOMContentLoaded', () => {
  // Page disabled for all users
  if (!Router.protect(['NONE'])) return;
  Sidebar.init({ activePage: 'payment' });
  Header.init({ title: 'Payment Records' });
});
