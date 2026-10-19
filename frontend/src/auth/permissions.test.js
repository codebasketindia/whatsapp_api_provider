import { describe, expect, it } from 'vitest';
import { hasPermission } from './permissions';

describe('hasPermission', () => {
  it('grants everything to super admin', () => {
    expect(hasPermission({ userType: 'ADMIN', isSuperAdmin: true, permissions: [] }, 'STAFF_MANAGE')).toBe(true);
  });
  it('checks staff permissions (any of array)', () => {
    const u = { userType: 'ADMIN', permissions: ['PARTNER_VIEW'] };
    expect(hasPermission(u, 'PARTNER_VIEW')).toBe(true);
    expect(hasPermission(u, 'WALLET_ADJUST')).toBe(false);
    expect(hasPermission(u, ['WALLET_ADJUST', 'PARTNER_VIEW'])).toBe(true);
  });
  it('never grants admin permissions to partners', () => {
    expect(hasPermission({ userType: 'PARTNER', isSuperAdmin: true }, 'PARTNER_VIEW')).toBe(false);
  });
});
