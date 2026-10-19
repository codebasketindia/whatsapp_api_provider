export const APP_NAME = import.meta.env.VITE_APP_NAME || 'CB WhatsApp Gateway';

export const PAYMENT_MODES = [
  { value: 'UPI', label: 'UPI' },
  { value: 'NEFT', label: 'NEFT' },
  { value: 'IMPS', label: 'IMPS' },
  { value: 'CASH', label: 'Cash' },
  { value: 'OTHER', label: 'Other' },
];

export const TEMPLATE_CATEGORIES = ['MARKETING', 'UTILITY', 'AUTHENTICATION'];
export const PRICE_CATEGORIES = ['MARKETING', 'UTILITY', 'AUTHENTICATION', 'AUTHENTICATION_INTERNATIONAL', 'SERVICE'];
export const WALLET_TXN_TYPES = ['CREDIT', 'DEBIT', 'HOLD', 'RELEASE', 'REFUND'];
export const MESSAGE_STATUSES = ['QUEUED', 'ACCEPTED', 'SENT', 'DELIVERED', 'READ', 'FAILED', 'RECEIVED'];
export const PAGE_SIZE = 20;

export const ADMIN_PERMISSIONS = [
  'DASHBOARD_VIEW', 'PARTNER_VIEW', 'PARTNER_MANAGE', 'LOGIN_AS_PARTNER', 'WALLET_VIEW', 'WALLET_ADJUST',
  'TOPUP_VIEW', 'TOPUP_APPROVE', 'PAYMENT_VIEW', 'PAYMENT_RECONCILE', 'PRICING_MANAGE', 'WHATSAPP_VIEW',
  'WHATSAPP_MANAGE', 'TEMPLATE_VIEW', 'TEMPLATE_REVIEW', 'MESSAGE_LOG_VIEW', 'CAMPAIGN_VIEW', 'CAMPAIGN_MANAGE',
  'REPORT_VIEW', 'SETTINGS_MANAGE', 'STAFF_MANAGE', 'AUDIT_VIEW',
];

/** Strong password rule shared with backend (min 8, upper, lower, digit). */
export const PASSWORD_RULE = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,}$/;
export const PASSWORD_HINT = 'At least 8 characters with an uppercase letter, a lowercase letter and a number.';
