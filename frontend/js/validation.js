/**
 * Ritham ERP — Form Validation (validation.js)
 */

'use strict';

const Validation = (() => {

  // ── Validator functions (return error string or null) ─────────────────────

  function required(value, label = 'This field') {
    const v = String(value ?? '').trim();
    return v === '' ? `${label} is required` : null;
  }

  function minLength(value, min, label = 'This field') {
    return value.trim().length < min
      ? `${label} must be at least ${min} characters`
      : null;
  }

  function maxLength(value, max, label = 'This field') {
    return value.trim().length > max
      ? `${label} must not exceed ${max} characters`
      : null;
  }

  function mobile(value) {
    const clean = value.replace(/\s/g, '');
    return /^[6-9]\d{9}$/.test(clean) ? null : 'Enter a valid 10-digit mobile number';
  }

  function email(value) {
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value.trim())
      ? null
      : 'Enter a valid email address';
  }

  function numeric(value, label = 'This field') {
    return isNaN(Number(value)) || String(value).trim() === ''
      ? `${label} must be a number`
      : null;
  }

  function positiveNumber(value, label = 'This field') {
    const num = Number(value);
    return isNaN(num) || num <= 0
      ? `${label} must be a positive number`
      : null;
  }

  function minValue(value, min, label = 'This field') {
    return Number(value) < min ? `${label} must be at least ${min}` : null;
  }

  function maxValue(value, max, label = 'This field') {
    return Number(value) > max ? `${label} must not exceed ${max}` : null;
  }

  function futureDate(value) {
    const date = new Date(value);
    return date <= new Date()
      ? 'Date must be in the future'
      : null;
  }

  function password(value) {
    if (value.length < 8) return 'Password must be at least 8 characters';
    return null;
  }

  // ── Field-level validation with DOM feedback ──────────────────────────────

  /**
   * Validate a single input field and show/clear error message.
   *
   * @param {HTMLInputElement} input
   * @param {...Function} validators - validator functions that take the value
   * @returns {boolean} true if valid
   */
  function validateField(input, ...validators) {
    const value      = input.value;
    let   error      = null;

    for (const validator of validators) {
      error = validator(value);
      if (error) break;
    }

    setFieldError(input, error);
    return error === null;
  }

  function setFieldError(input, message) {
    input.classList.toggle('error', !!message);

    // Look for adjacent .form-error element
    const parent    = input.closest('.form-group');
    const errorEl   = parent?.querySelector('.form-error');
    if (errorEl) {
      errorEl.textContent = message || '';
      errorEl.style.display = message ? 'flex' : 'none';
    }
  }

  function clearFieldError(input) {
    setFieldError(input, null);
  }

  // ── Form-level validation ─────────────────────────────────────────────────

  /**
   * Validate multiple fields at once.
   *
   * @param {Array<{input: HTMLElement, validators: Function[]}>} fields
   * @returns {boolean} true if ALL fields are valid
   */
  function validateForm(fields) {
    let allValid = true;
    fields.forEach(({ input, validators }) => {
      const valid = validateField(input, ...validators);
      if (!valid) allValid = false;
    });
    return allValid;
  }

  /**
   * Clear errors on all inputs within a form element.
   */
  function clearForm(formEl) {
    formEl.querySelectorAll('.form-control.error').forEach(el => {
      clearFieldError(el);
    });
  }

  // ── Live validation helper ────────────────────────────────────────────────

  /**
   * Attach real-time validation to an input (validates on blur, clears on focus).
   */
  function attachLiveValidation(input, ...validators) {
    input.addEventListener('blur', () => validateField(input, ...validators));
    input.addEventListener('focus', () => clearFieldError(input));
  }

  return {
    // Validators
    required, minLength, maxLength,
    mobile, email, numeric, positiveNumber,
    minValue, maxValue, futureDate, password,
    // DOM helpers
    validateField, validateForm, clearForm,
    setFieldError, clearFieldError,
    attachLiveValidation,
  };

})();
