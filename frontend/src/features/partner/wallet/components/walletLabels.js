import { toNumber } from '../../../../lib/format';

const REF_LABELS = {
  TOPUP_REQUEST: 'Bank / UPI transfer',
  PAYMENT_ORDER: 'Online payment',
  BONUS: 'Bonus credit',
  ADMIN_ADJUSTMENT: 'Adjustment by support',
  MESSAGE: 'Message',
  PLATFORM_FEE: 'Monthly number fee',
  CAMPAIGN: 'Campaign',
};

const TYPE_LABELS = {
  CREDIT: 'Money added',
  DEBIT: 'Charged',
  HOLD: 'On hold',
  RELEASE: 'Hold returned',
  REFUND: 'Refund',
};

/** Plain-language description for a passbook row. */
export function txnDescription(t) {
  const ref = REF_LABELS[t.referenceType] || 'Wallet';
  if (t.referenceType === 'MESSAGE') {
    if (t.type === 'HOLD') return 'Message price held until delivery';
    if (t.type === 'DEBIT') return 'Message charged';
    if (t.type === 'REFUND') return 'Refund for a failed message';
    if (t.type === 'RELEASE') return 'Message not sent — hold returned';
  }
  return `${TYPE_LABELS[t.type] || t.type} · ${ref}`;
}

/** Change in spendable balance (+ added, − removed, 0 when a hold is finalised). */
export function balanceDelta(t) {
  return toNumber(t.balanceAfter) - toNumber(t.balanceBefore);
}
