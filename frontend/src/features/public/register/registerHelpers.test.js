import { describe, expect, it } from 'vitest';
import { passStatusText } from './registerHelpers';

describe('passStatusText', () => {
  it('explains every pass state', () => {
    expect(passStatusText('SENT')).toMatch(/sent to your WhatsApp/);
    expect(passStatusText('WAITING_FOR_CHAT')).toMatch(/green button/);
    expect(passStatusText('PENDING')).toMatch(/being sent/);
    expect(passStatusText('FAILED')).toMatch(/download/);
    expect(passStatusText('DISABLED')).toBe('');
  });
});
