import { describe, expect, it } from 'vitest';
import {
  buildCampaignMapping, buildContactImportMapping, campaignMappingComplete, decodeVariableSource, encodeVariableSource, guessColumn, toAttributeKey,
} from './columnMapping';

describe('column mapping', () => {
  const cols = ['Customer Name', 'Mobile No.', 'E-mail', 'City'];

  it('guesses columns by common names', () => {
    expect(guessColumn(cols, 'phone')).toBe('Mobile No.');
    expect(guessColumn(cols, 'name')).toBe('Customer Name');
    expect(guessColumn(cols, 'email')).toBe('E-mail');
    expect(guessColumn(cols, 'tags')).toBe('');
  });

  it('turns column names into attribute keys', () => {
    expect(toAttributeKey('Order ID')).toBe('order_id');
    expect(toAttributeKey('  City / Town ')).toBe('city_town');
  });

  it('builds the contact import mapping', () => {
    expect(
      buildContactImportMapping({ phone: 'Mobile No.', name: 'Customer Name', email: '', tags: '', extraColumns: [{ column: 'City', key: 'city', include: true }, { column: 'X', key: 'x', include: false }] }),
    ).toEqual({ phone: 'Mobile No.', name: 'Customer Name', attributes: { City: 'city' } });
  });

  it('encodes and decodes variable sources', () => {
    expect(encodeVariableSource({ kind: 'column', value: 'City' })).toBe('City');
    expect(encodeVariableSource({ kind: 'attribute', value: 'city' })).toBe('{{city}}');
    expect(encodeVariableSource({ kind: 'static', value: 'Hi' })).toBe('=Hi');
    expect(decodeVariableSource('{{city}}')).toEqual({ kind: 'attribute', value: 'city' });
    expect(decodeVariableSource('=Hi')).toEqual({ kind: 'static', value: 'Hi' });
  });

  it('builds the campaign mapping and checks completeness', () => {
    const variables = { 1: { kind: 'column', value: 'Customer Name' }, 2: { kind: 'static', value: 'Sunday' } };
    expect(buildCampaignMapping({ phoneColumn: 'Mobile No.', variables })).toEqual({ phone: 'Mobile No.', variables: { 1: 'Customer Name', 2: '=Sunday' } });
    expect(campaignMappingComplete({ audienceType: 'FILE', phoneColumn: 'Mobile No.', variables, required: ['1', '2'] })).toBe(true);
    expect(campaignMappingComplete({ audienceType: 'FILE', phoneColumn: '', variables, required: ['1'] })).toBe(false);
    expect(campaignMappingComplete({ audienceType: 'GROUPS', variables: { 1: { kind: 'static', value: ' ' } }, required: ['1'] })).toBe(false);
  });
});
