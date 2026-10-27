import { describe, expect, it } from 'vitest';
import { composerToRequest } from '../../common/composer';
import { requestToComposer } from './requestForm';

describe('requestToComposer', () => {
  it('round-trips a text message', () => {
    const c = requestToComposer({ type: 'text', text: { body: 'Hello', previewUrl: true } });
    expect(c.kind).toBe('text');
    expect(composerToRequest(c)).toEqual({ type: 'text', text: { body: 'Hello', previewUrl: true } });
  });
  it('round-trips a photo with caption', () => {
    const req = { type: 'image', media: { fileUid: 'f1', caption: 'Offer' } };
    expect(composerToRequest(requestToComposer(req))).toEqual({ type: 'image', media: { fileUid: 'f1', link: undefined, caption: 'Offer', filename: undefined } });
  });
  it('reads reply buttons back', () => {
    const c = requestToComposer({
      type: 'interactive',
      interactive: { type: 'button', body: { text: 'Pick' }, action: { buttons: [{ type: 'reply', reply: { id: 'btn_1', title: 'Yes' } }] } },
    });
    expect(c.kind).toBe('buttons');
    expect(c.buttons).toEqual(['Yes', '', '']);
  });
});
