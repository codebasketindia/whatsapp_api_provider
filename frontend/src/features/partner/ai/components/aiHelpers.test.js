import { describe, expect, it } from 'vitest';
import { aiActiveFor, assistantToForm, buildAiHistory, formToAssistant, knowledgeSnippet } from './aiHelpers';

describe('buildAiHistory', () => {
  it('keeps customer/business turns in order and drops failed, pending and empty ones', () => {
    const chat = [
      { role: 'customer', text: 'Hi' },
      { role: 'business', text: 'Hello! How can I help?' },
      { role: 'customer', text: 'price?', error: true },
      { role: 'business', text: '', attachments: [{ id: 1 }] },
      { role: 'system', text: 'Handed over' },
      { role: 'customer', text: '  Timing?  ' },
      { role: 'business', text: '...', pending: true },
    ];
    expect(buildAiHistory(chat)).toEqual([
      { role: 'customer', text: 'Hi' },
      { role: 'business', text: 'Hello! How can I help?' },
      { role: 'customer', text: 'Timing?' },
    ]);
  });
  it('keeps only the last N turns', () => {
    const chat = Array.from({ length: 15 }, (_, i) => ({ role: i % 2 ? 'business' : 'customer', text: `m${i}` }));
    const h = buildAiHistory(chat, 4);
    expect(h).toHaveLength(4);
    expect(h[0].text).toBe('m11');
  });
});

describe('assistant form mapping', () => {
  it('maps null senderIds to "all numbers" and back', () => {
    const f = assistantToForm({ enabled: true, senderIds: null, maxRepliesPerChatPerDay: 5 });
    expect(f.allNumbers).toBe(true);
    expect(formToAssistant({ ...f, fallbackMessage: ' Wait ' })).toMatchObject({ senderIds: null, fallbackMessage: 'Wait', maxRepliesPerChatPerDay: 5 });
    expect(formToAssistant({ ...f, allNumbers: false, senderIds: ['3'] }).senderIds).toEqual([3]);
  });
});

describe('aiActiveFor', () => {
  const a = { enabled: true, platformEnabled: true, senderIds: [7] };
  it('is true only for covered, unpaused chats', () => {
    expect(aiActiveFor(a, 7, {})).toBe(true);
    expect(aiActiveFor(a, 8, {})).toBe(false);
    expect(aiActiveFor(a, 7, { aiPaused: true })).toBe(false);
    expect(aiActiveFor({ ...a, senderIds: null }, 8, {})).toBe(true);
    expect(aiActiveFor({ ...a, platformEnabled: false }, 7, {})).toBe(false);
  });
});

it('knowledgeSnippet trims long content', () => {
  expect(knowledgeSnippet('a\n b', 10)).toBe('a b');
  expect(knowledgeSnippet('x'.repeat(20), 10)).toHaveLength(10);
});
