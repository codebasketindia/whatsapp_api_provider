import { describe, expect, it } from 'vitest';
import { EMPTY_COMPOSER, composerToRequest, composerValid } from './composer';

describe('free-form composer', () => {
  it('builds a text message', () => {
    const s = { ...EMPTY_COMPOSER, text: 'Hello' };
    expect(composerValid(s)).toBe(true);
    expect(composerToRequest(s)).toEqual({ type: 'text', text: { body: 'Hello', previewUrl: true } });
  });

  it('requires a file or https link for media', () => {
    expect(composerValid({ ...EMPTY_COMPOSER, kind: 'image' })).toBe(false);
    expect(composerValid({ ...EMPTY_COMPOSER, kind: 'image', link: 'http://x.in/a.jpg' })).toBe(false);
    const s = { ...EMPTY_COMPOSER, kind: 'document', fileUid: 'F1', fileName: 'bill.pdf', caption: 'Your bill' };
    expect(composerToRequest(s)).toEqual({ type: 'document', media: { fileUid: 'F1', link: undefined, caption: 'Your bill', filename: 'bill.pdf' } });
  });

  it('builds reply buttons', () => {
    const s = { ...EMPTY_COMPOSER, kind: 'buttons', text: 'Pick one', buttons: ['Yes', '', 'No'] };
    const r = composerToRequest(s);
    expect(r.interactive.action.buttons.map((b) => b.reply.title)).toEqual(['Yes', 'No']);
  });
});
