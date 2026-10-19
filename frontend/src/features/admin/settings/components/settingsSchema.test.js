import { describe, expect, it } from 'vitest';
import { SETTINGS_FIELDS, buildSchema, toBody, toFormValues } from './settingsSchema';

describe('settings form helpers', () => {
  const fields = SETTINGS_FIELDS.smtp;
  it('maps API values and hides secrets', () => {
    const v = toFormValues(fields, { host: 'smtp.x.com', port: 587, passwordSet: true, starttls: true });
    expect(v.host).toBe('smtp.x.com');
    expect(v.port).toBe('587');
    expect(v.password).toBe('');
    expect(v.starttls).toBe(true);
  });
  it('sends only changed fields and secrets when typed', () => {
    const original = { host: 'a', port: 587, starttls: true };
    const values = { ...toFormValues(fields, original), host: 'b', password: '' };
    expect(toBody(fields, values, original)).toEqual({ host: 'b' });
    expect(toBody(fields, { ...values, password: 'pw' }, original)).toEqual({ host: 'b', password: 'pw' });
  });
  it('validates numbers and emails', () => {
    const schema = buildSchema(fields);
    const ok = schema.safeParse({ ...toFormValues(fields, {}), port: '587', fromEmail: 'x@y.in' });
    expect(ok.success).toBe(true);
    const bad = schema.safeParse({ ...toFormValues(fields, {}), port: 'abc', fromEmail: 'nope' });
    expect(bad.success).toBe(false);
  });
});
