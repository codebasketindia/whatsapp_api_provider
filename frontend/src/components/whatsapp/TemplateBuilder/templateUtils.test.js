import { describe, expect, it } from 'vitest';
import {
  buildTemplatePayload, builderToComponents, componentsToBuilder, extractVariables, fillVariables, nextVariable, templateInputs,
  templateValuesComplete, validateBuilder, validateVariables,
} from './templateUtils';

const positional = {
  id: 7,
  name: 'order_update',
  language: 'en',
  category: 'UTILITY',
  parameterFormat: 'POSITIONAL',
  components: [
    { type: 'HEADER', format: 'IMAGE' },
    { type: 'BODY', text: 'Hi {{1}}, order {{2}} is on the way. Thanks {{1}}!' },
    { type: 'BUTTONS', buttons: [{ type: 'URL', text: 'Track', url: 'https://x.in/t/{{1}}' }, { type: 'QUICK_REPLY', text: 'Stop' }] },
  ],
};

describe('template variables', () => {
  it('extracts unique variables in order', () => {
    expect(extractVariables('Hi {{1}}, {{ 2 }} and {{1}} again')).toEqual(['1', '2']);
    expect(extractVariables('Hi {{first_name}}')).toEqual(['first_name']);
    expect(extractVariables('')).toEqual([]);
  });

  it('validates positional sequence and named format', () => {
    expect(validateVariables('{{1}} {{2}}')).toBeNull();
    expect(validateVariables('{{1}} {{3}}')).toMatch(/missing \{\{2\}\}/);
    expect(validateVariables('{{Name}}', 'NAMED')).toMatch(/lowercase/);
    expect(validateVariables('{{first_name}}', 'NAMED')).toBeNull();
  });

  it('suggests the next variable', () => {
    expect(nextVariable('Hi {{1}}')).toBe('{{2}}');
    expect(nextVariable('', 'NAMED')).toBe('{{var_1}}');
  });

  it('fills values', () => {
    expect(fillVariables('Hi {{1}} {{2}}', ['Ravi'])).toBe('Hi Ravi {{2}}');
    expect(fillVariables('Hi {{name}}', { name: 'Asha' })).toBe('Hi Asha');
  });
});

describe('sending a template', () => {
  it('lists the inputs a sender must fill', () => {
    const i = templateInputs(positional);
    expect(i.bodyVars).toEqual(['1', '2']);
    expect(i.needsHeaderMedia).toBe(true);
    expect(i.buttonInputs).toEqual([{ index: 0, subType: 'url', label: 'Link ending for "Track"' }]);
  });

  it('builds the SendMessageRequest.template payload', () => {
    const values = { body: { 1: 'Ravi', 2: 'A100' }, header: { fileUid: 'F1' }, buttons: { 0: 'A100' } };
    expect(templateValuesComplete(positional, values)).toBe(true);
    expect(templateValuesComplete(positional, { body: { 1: 'Ravi' } })).toBe(false);
    const p = buildTemplatePayload(positional, values);
    expect(p).toMatchObject({ templateId: 7, name: 'order_update', language: 'en' });
    expect(p.variables.body).toEqual(['Ravi', 'A100']);
    expect(p.variables.header).toMatchObject({ type: 'image', fileUid: 'F1' });
    expect(p.variables.buttons).toEqual([{ index: 0, subType: 'url', value: 'A100' }]);
  });

  it('uses namedVariables for NAMED templates', () => {
    const t = { ...positional, parameterFormat: 'NAMED', components: [{ type: 'BODY', text: 'Hi {{first_name}}' }] };
    const p = buildTemplatePayload(t, { body: { first_name: 'Asha' } });
    expect(p.namedVariables).toEqual({ first_name: 'Asha' });
    expect(p.variables.body).toEqual([]);
  });

  it('fills the OTP button with the code from the body', () => {
    const t = { id: 1, name: 'otp', language: 'en', category: 'AUTHENTICATION', components: [{ type: 'BODY' }, { type: 'BUTTONS', buttons: [{ type: 'OTP', otpType: 'COPY_CODE' }] }] };
    const p = buildTemplatePayload(t, { body: { 1: '123456' } });
    expect(p.variables.body).toEqual(['123456']);
    expect(p.variables.buttons).toEqual([{ index: 0, subType: 'url', value: '123456' }]);
  });
});

describe('builder', () => {
  const state = {
    name: 'diwali_offer',
    language: 'en',
    category: 'MARKETING',
    parameterFormat: 'POSITIONAL',
    headerType: 'TEXT',
    headerText: 'Hello {{1}}',
    headerExample: 'Ravi',
    body: 'Get {{1}}% off till {{2}}',
    bodyExamples: { 1: '20', 2: 'Sunday' },
    footer: 'Reply STOP to opt out',
    buttons: [{ type: 'URL', text: 'Shop', url: 'https://shop.in/{{1}}', example: 'diwali' }],
  };

  it('converts to Meta components and back', () => {
    const comps = builderToComponents(state);
    expect(comps[0]).toMatchObject({ type: 'HEADER', format: 'TEXT', example: { header_text: ['Ravi'] } });
    expect(comps[1]).toMatchObject({ type: 'BODY', example: { body_text: [['20', 'Sunday']] } });
    expect(comps[3].buttons[0]).toMatchObject({ type: 'URL', url: 'https://shop.in/{{1}}', example: ['diwali'] });
    const back = componentsToBuilder({ ...state, components: comps });
    expect(back.bodyExamples).toEqual({ 1: '20', 2: 'Sunday' });
    expect(back.headerType).toBe('TEXT');
  });

  it('validates Meta rules', () => {
    expect(validateBuilder(state)).toEqual({});
    expect(validateBuilder({ ...state, name: 'Bad Name' }).name).toBeTruthy();
    expect(validateBuilder({ ...state, bodyExamples: {} }).bodyExamples).toBeTruthy();
    expect(validateBuilder({ ...state, buttons: [{ type: 'URL', text: 'x', url: 'http://a.in' }] })['buttons.0.url']).toBeTruthy();
    expect(validateBuilder({ ...state, category: 'AUTHENTICATION', buttons: [] }).buttons).toBeTruthy();
  });
});
