// Free-form (non-template) message state → SendMessageRequest fields + preview props.

export const EMPTY_COMPOSER = {
  kind: 'text', // text | image | video | audio | document | buttons | link
  text: '',
  previewUrl: true,
  fileUid: null,
  fileName: '',
  link: '',
  caption: '',
  buttons: ['', '', ''],
  ctaText: '',
  ctaUrl: '',
};

export function composerValid(s) {
  switch (s.kind) {
    case 'text':
      return s.text.trim().length > 0 && s.text.length <= 4096;
    case 'image':
    case 'video':
    case 'audio':
    case 'document':
      return Boolean(s.fileUid || /^https:\/\/\S+$/.test(s.link || ''));
    case 'buttons':
      return s.text.trim().length > 0 && s.text.length <= 1024 && s.buttons.some((b) => b.trim()) && s.buttons.every((b) => b.length <= 20);
    case 'link':
      return s.text.trim().length > 0 && s.ctaText.trim().length > 0 && s.ctaText.length <= 20 && /^https?:\/\/\S+$/.test(s.ctaUrl);
    default:
      return false;
  }
}

export function composerToRequest(s) {
  switch (s.kind) {
    case 'text':
      return { type: 'text', text: { body: s.text, previewUrl: Boolean(s.previewUrl) } };
    case 'image':
    case 'video':
    case 'audio':
    case 'document':
      return {
        type: s.kind,
        media: {
          fileUid: s.fileUid || undefined,
          link: s.fileUid ? undefined : s.link || undefined,
          caption: s.kind !== 'audio' && s.caption ? s.caption : undefined,
          filename: s.kind === 'document' ? s.fileName || undefined : undefined,
        },
      };
    case 'buttons':
      return {
        type: 'interactive',
        interactive: {
          type: 'button',
          body: { text: s.text },
          action: {
            buttons: s.buttons
              .map((b) => b.trim())
              .filter(Boolean)
              .map((title, i) => ({ type: 'reply', reply: { id: `btn_${i + 1}`, title } })),
          },
        },
      };
    case 'link':
      return {
        type: 'interactive',
        interactive: {
          type: 'cta_url',
          body: { text: s.text },
          action: { name: 'cta_url', parameters: { display_text: s.ctaText, url: s.ctaUrl } },
        },
      };
    default:
      return null;
  }
}

export function composerPreview(s) {
  const header = ['image', 'video', 'document'].includes(s.kind) ? { format: s.kind.toUpperCase(), filename: s.fileName } : null;
  if (s.kind === 'buttons') return { body: s.text, buttons: s.buttons.filter((b) => b.trim()).map((text) => ({ type: 'QUICK_REPLY', text })) };
  if (s.kind === 'link') return { body: s.text, buttons: s.ctaText ? [{ type: 'URL', text: s.ctaText }] : [] };
  if (s.kind === 'audio') return { body: '🎤 Audio message' };
  if (header) return { header, body: s.caption };
  return { body: s.text };
}
