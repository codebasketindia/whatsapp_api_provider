import { EMPTY_COMPOSER } from '../../common/composer';
import { templateInputs } from '../../../../components/whatsapp/TemplateBuilder/templateUtils';

/** SendMessageRequest (free-form) → MessageComposer value, for editing a scheduled item. */
export function requestToComposer(req = {}) {
  const type = String(req.type || '').toLowerCase();
  if (type === 'text') return { ...EMPTY_COMPOSER, kind: 'text', text: req.text?.body || '', previewUrl: req.text?.previewUrl !== false };
  if (['image', 'video', 'audio', 'document'].includes(type)) {
    const m = req.media || req[type] || {};
    return { ...EMPTY_COMPOSER, kind: type, fileUid: m.fileUid || null, link: m.link || '', caption: m.caption || '', fileName: m.filename || '' };
  }
  if (type === 'interactive') {
    const i = req.interactive || {};
    if (i.type === 'cta_url') {
      const p = i.action?.parameters || {};
      return { ...EMPTY_COMPOSER, kind: 'link', text: i.body?.text || '', ctaText: p.display_text || '', ctaUrl: p.url || '' };
    }
    const titles = (i.action?.buttons || []).map((b) => b.reply?.title || '');
    return { ...EMPTY_COMPOSER, kind: 'buttons', text: i.body?.text || '', buttons: [0, 1, 2].map((n) => titles[n] || '') };
  }
  return { ...EMPTY_COMPOSER };
}

/** Template part of a SendMessageRequest → TemplateFill values (inverse of buildTemplatePayload). */
export function requestToTemplateValues(template, payload = {}) {
  if (!template || !payload) return {};
  const inputs = templateInputs(template);
  const vars = payload.variables || {};
  const body = {};
  if (inputs.named) {
    Object.assign(body, payload.namedVariables || {});
  } else {
    inputs.bodyVars.forEach((v, i) => {
      if (vars.body?.[i] !== undefined) body[v] = vars.body[i];
    });
  }
  const values = { body };
  const h = vars.header;
  if (h) {
    values.header = h.type === 'text' ? { text: h.text } : { ...h };
    delete values.header.type;
  }
  if (vars.buttons?.length) values.buttons = Object.fromEntries(vars.buttons.map((b) => [b.index, b.value]));
  return values;
}
