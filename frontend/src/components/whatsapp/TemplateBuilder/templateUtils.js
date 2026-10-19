// Pure helpers for WhatsApp templates (shared by the builder, Send Message, campaigns and inbox).

const VAR_RE = /\{\{\s*([\w.]+)\s*\}\}/g;

/** Common Meta template language codes (subset, most used in India first). */
export const TEMPLATE_LANGUAGES = [
  { value: 'en', label: 'English' },
  { value: 'en_US', label: 'English (US)' },
  { value: 'en_GB', label: 'English (UK)' },
  { value: 'hi', label: 'Hindi' },
  { value: 'bn', label: 'Bengali' },
  { value: 'gu', label: 'Gujarati' },
  { value: 'kn', label: 'Kannada' },
  { value: 'ml', label: 'Malayalam' },
  { value: 'mr', label: 'Marathi' },
  { value: 'pa', label: 'Punjabi' },
  { value: 'ta', label: 'Tamil' },
  { value: 'te', label: 'Telugu' },
  { value: 'ur', label: 'Urdu' },
  { value: 'ar', label: 'Arabic' },
  { value: 'es', label: 'Spanish' },
  { value: 'fr', label: 'French' },
  { value: 'de', label: 'German' },
  { value: 'id', label: 'Indonesian' },
  { value: 'pt_BR', label: 'Portuguese (Brazil)' },
  { value: 'ru', label: 'Russian' },
  { value: 'zh_CN', label: 'Chinese (Simplified)' },
];

export const CATEGORY_HELP = {
  MARKETING: 'Offers, promotions, announcements and newsletters.',
  UTILITY: 'Updates about something the customer already started: orders, bookings, payments, accounts.',
  AUTHENTICATION: 'One-time passwords (OTP) to log in or verify. Meta writes the text for you.',
};

/** Variable names in order of first appearance: ["1","2"] or ["name","order_id"]. */
export function extractVariables(text) {
  if (!text) return [];
  const out = [];
  VAR_RE.lastIndex = 0;
  let m;
  while ((m = VAR_RE.exec(text)) !== null) {
    if (!out.includes(m[1])) out.push(m[1]);
  }
  return out;
}

/** Validates variable naming: positional must be {{1}}..{{n}} in sequence, named must be lowercase_with_underscores. */
export function validateVariables(text, parameterFormat = 'POSITIONAL') {
  const vars = extractVariables(text);
  if (!vars.length) return null;
  if (parameterFormat === 'NAMED') {
    const bad = vars.find((v) => !/^[a-z][a-z0-9_]*$/.test(v));
    return bad ? `Variable {{${bad}}} must use lowercase letters, numbers and _ (for example {{first_name}})` : null;
  }
  const nums = vars.map(Number);
  if (nums.some((n) => !Number.isInteger(n) || n < 1)) return 'Use numbered variables like {{1}}, {{2}}';
  const sorted = [...nums].sort((a, b) => a - b);
  for (let i = 0; i < sorted.length; i += 1) {
    if (sorted[i] !== i + 1) return `Variables must be numbered in order: {{1}}, {{2}}, … (missing {{${i + 1}}})`;
  }
  return null;
}

/** Next variable token for the "Add variable" button. */
export function nextVariable(text, parameterFormat = 'POSITIONAL') {
  const vars = extractVariables(text);
  if (parameterFormat === 'NAMED') {
    let i = vars.length + 1;
    while (vars.includes(`var_${i}`)) i += 1;
    return `{{var_${i}}}`;
  }
  return `{{${vars.length + 1}}}`;
}

/** Replaces variables with values (array = positional, object = named); unknown stay as {{x}}. */
export function fillVariables(text, values) {
  if (!text) return '';
  return text.replace(VAR_RE, (whole, key) => {
    let v;
    if (Array.isArray(values)) v = values[Number(key) - 1];
    else if (values && typeof values === 'object') v = values[key];
    return v !== undefined && v !== null && v !== '' ? String(v) : whole;
  });
}

export const getComponent = (template, type) => (template?.components || []).find((c) => String(c.type).toUpperCase() === type);

/** Converts a TemplateDto into WhatsAppPreview props. */
export function templateToPreview(template) {
  const header = getComponent(template, 'HEADER');
  const body = getComponent(template, 'BODY');
  const footer = getComponent(template, 'FOOTER');
  const buttons = getComponent(template, 'BUTTONS')?.buttons || [];
  const auth = String(template?.category).toUpperCase() === 'AUTHENTICATION';
  let bodyText = body?.text;
  if (auth && !bodyText) {
    bodyText = `{{1}} is your verification code.${body?.addSecurityRecommendation ? ' For your security, do not share this code.' : ''}`;
  }
  let footerText = footer?.text;
  if (auth && !footerText && footer?.codeExpirationMinutes) footerText = `This code expires in ${footer.codeExpirationMinutes} minutes.`;
  return {
    header: header ? { format: header.format || 'TEXT', text: header.text } : null,
    body: bodyText,
    footer: footerText,
    buttons: buttons.map((b) => ({ type: b.type, text: b.text || (b.type === 'OTP' || b.type === 'COPY_CODE' ? 'Copy code' : '') })),
  };
}

/** What a sender must fill for this template: body vars, header var / media, dynamic URL buttons, copy code. */
export function templateInputs(template) {
  const header = getComponent(template, 'HEADER');
  const body = getComponent(template, 'BODY');
  const buttons = getComponent(template, 'BUTTONS')?.buttons || [];
  const auth = String(template?.category).toUpperCase() === 'AUTHENTICATION';
  const bodyVars = auth ? ['1'] : extractVariables(body?.text);
  const headerFormat = header ? String(header.format || 'TEXT').toUpperCase() : null;
  const headerVars = headerFormat === 'TEXT' ? extractVariables(header.text) : [];
  const buttonInputs = [];
  buttons.forEach((b, index) => {
    const t = String(b.type).toUpperCase();
    if (t === 'URL' && /\{\{/.test(b.url || '')) buttonInputs.push({ index, subType: 'url', label: `Link ending for "${b.text}"` });
    if (t === 'COPY_CODE') buttonInputs.push({ index, subType: 'copy_code', label: 'Coupon code' });
    if (t === 'OTP') buttonInputs.push({ index, subType: 'url', label: 'One-time code (same as the code in the message)', otp: true });
  });
  return {
    named: String(template?.parameterFormat).toUpperCase() === 'NAMED',
    bodyVars,
    headerFormat,
    headerVars,
    needsHeaderMedia: ['IMAGE', 'VIDEO', 'DOCUMENT'].includes(headerFormat),
    needsLocation: headerFormat === 'LOCATION',
    buttonInputs,
    auth,
  };
}

/**
 * Builds SendMessageRequest.template from form values.
 * values: { body: {var: value}, header: {text|fileUid|link|filename|latitude...}, buttons: {index: value} }
 */
export function buildTemplatePayload(template, values = {}) {
  const inputs = templateInputs(template);
  const bodyVals = values.body || {};
  const payload = { templateId: template.id, name: template.name, language: template.language };
  if (inputs.named) {
    payload.namedVariables = Object.fromEntries(inputs.bodyVars.map((v) => [v, bodyVals[v] ?? '']));
  }
  const variables = { body: inputs.named ? [] : inputs.bodyVars.map((v) => bodyVals[v] ?? '') };
  if (inputs.headerFormat === 'TEXT' && inputs.headerVars.length) {
    variables.header = { type: 'text', text: values.header?.text ?? '' };
  } else if (inputs.needsHeaderMedia) {
    const h = values.header || {};
    variables.header = { type: inputs.headerFormat.toLowerCase(), fileUid: h.fileUid, link: h.link, filename: h.filename };
  } else if (inputs.needsLocation) {
    const h = values.header || {};
    variables.header = { type: 'location', latitude: Number(h.latitude), longitude: Number(h.longitude), name: h.name, address: h.address };
  }
  const btnVals = values.buttons || {};
  const buttons = inputs.buttonInputs.map((b) => ({
    index: b.index,
    subType: b.subType,
    value: b.otp ? bodyVals['1'] ?? btnVals[b.index] ?? '' : btnVals[b.index] ?? '',
  }));
  if (buttons.length) variables.buttons = buttons;
  payload.variables = variables;
  return payload;
}

/** True when every required value is filled. */
export function templateValuesComplete(template, values = {}) {
  const inputs = templateInputs(template);
  const body = values.body || {};
  if (inputs.bodyVars.some((v) => !String(body[v] ?? '').trim())) return false;
  if (inputs.headerVars.length && !String(values.header?.text ?? '').trim()) return false;
  if (inputs.needsHeaderMedia && !values.header?.fileUid && !values.header?.link) return false;
  if (inputs.needsLocation && (values.header?.latitude === undefined || values.header?.longitude === undefined)) return false;
  if (inputs.buttonInputs.some((b) => !b.otp && !String(values.buttons?.[b.index] ?? '').trim())) return false;
  return true;
}

/**
 * Builds the TP2 components array from builder state.
 * state: { headerType, headerText, headerExample, headerHandle, headerFileUid, body, bodyExamples{var:val},
 *          footer, buttons[], addSecurityRecommendation, codeExpirationMinutes, parameterFormat, category }
 */
export function builderToComponents(state) {
  const named = state.parameterFormat === 'NAMED';
  const auth = state.category === 'AUTHENTICATION';
  const components = [];
  if (!auth && state.headerType && state.headerType !== 'NONE') {
    const h = { type: 'HEADER', format: state.headerType };
    if (state.headerType === 'TEXT') {
      h.text = state.headerText;
      const hv = extractVariables(state.headerText);
      if (hv.length) {
        h.example = named
          ? { header_text_named_params: hv.map((v) => ({ param_name: v, example: state.headerExample || '' })) }
          : { header_text: [state.headerExample || ''] };
      }
    } else if (state.headerType !== 'LOCATION') {
      if (state.headerHandle) h.example = { header_handle: [state.headerHandle] };
      if (state.headerFileUid) h.headerMediaFileUid = state.headerFileUid;
    }
    components.push(h);
  }
  if (auth) {
    components.push({ type: 'BODY', addSecurityRecommendation: Boolean(state.addSecurityRecommendation) });
    if (state.codeExpirationMinutes) components.push({ type: 'FOOTER', codeExpirationMinutes: Number(state.codeExpirationMinutes) });
  } else {
    const vars = extractVariables(state.body);
    const b = { type: 'BODY', text: state.body };
    if (vars.length) {
      b.example = named
        ? { body_text_named_params: vars.map((v) => ({ param_name: v, example: state.bodyExamples?.[v] || '' })) }
        : { body_text: [vars.map((v) => state.bodyExamples?.[v] || '')] };
    }
    components.push(b);
    if (state.footer?.trim()) components.push({ type: 'FOOTER', text: state.footer.trim() });
  }
  const buttons = (state.buttons || []).map((btn) => {
    const out = { type: btn.type, text: btn.text };
    if (btn.type === 'URL') {
      out.url = btn.url;
      if (/\{\{/.test(btn.url || '')) out.example = [btn.example || ''];
    }
    if (btn.type === 'PHONE_NUMBER') out.phoneNumber = btn.phoneNumber;
    if (btn.type === 'COPY_CODE') out.example = [btn.example || ''];
    if (btn.type === 'OTP') {
      out.otpType = btn.otpType || 'COPY_CODE';
      out.text = btn.text || 'Copy code';
      if (btn.otpType === 'ONE_TAP' || btn.otpType === 'ZERO_TAP') {
        out.packageName = btn.packageName;
        out.signatureHash = btn.signatureHash;
      }
    }
    return out;
  });
  if (buttons.length) components.push({ type: 'BUTTONS', buttons });
  return components;
}

/** Inverse of builderToComponents for editing an existing template. */
export function componentsToBuilder(template) {
  const header = getComponent(template, 'HEADER');
  const body = getComponent(template, 'BODY');
  const footer = getComponent(template, 'FOOTER');
  const buttons = getComponent(template, 'BUTTONS')?.buttons || [];
  const named = String(template.parameterFormat).toUpperCase() === 'NAMED';
  const bodyVars = extractVariables(body?.text);
  const bodyExamples = {};
  if (named) (body?.example?.body_text_named_params || []).forEach((p) => (bodyExamples[p.param_name] = p.example));
  else (body?.example?.body_text?.[0] || []).forEach((v, i) => bodyVars[i] && (bodyExamples[bodyVars[i]] = v));
  return {
    name: template.name,
    language: template.language,
    category: template.category,
    parameterFormat: named ? 'NAMED' : 'POSITIONAL',
    headerType: header ? String(header.format || 'TEXT').toUpperCase() : 'NONE',
    headerText: header?.text || '',
    headerExample: header?.example?.header_text?.[0] || header?.example?.header_text_named_params?.[0]?.example || '',
    headerHandle: header?.example?.header_handle?.[0] || '',
    headerFileUid: header?.headerMediaFileUid || '',
    body: body?.text || '',
    bodyExamples,
    footer: footer?.text || '',
    addSecurityRecommendation: Boolean(body?.addSecurityRecommendation),
    codeExpirationMinutes: footer?.codeExpirationMinutes || '',
    buttons: buttons.map((b) => ({
      type: b.type,
      text: b.text || '',
      url: b.url || '',
      phoneNumber: b.phoneNumber || '',
      example: Array.isArray(b.example) ? b.example[0] : b.example || '',
      otpType: b.otpType || 'COPY_CODE',
      packageName: b.packageName || '',
      signatureHash: b.signatureHash || '',
    })),
  };
}

/** Client-side checks mirroring Meta rules. Returns { field: message }. */
export function validateBuilder(state) {
  const errors = {};
  if (!/^[a-z0-9_]{1,512}$/.test(state.name || '')) errors.name = 'Use lowercase letters, numbers and _ only (e.g. order_update)';
  if (!state.language) errors.language = 'Choose a language';
  if (!state.category) errors.category = 'Choose a category';
  const auth = state.category === 'AUTHENTICATION';
  if (!auth) {
    if (!state.body?.trim()) errors.body = 'Message text is required';
    else if (state.body.length > 1024) errors.body = 'Message text can be at most 1024 characters';
    else {
      const ve = validateVariables(state.body, state.parameterFormat);
      if (ve) errors.body = ve;
      else if (extractVariables(state.body).some((v) => !String(state.bodyExamples?.[v] ?? '').trim())) {
        errors.bodyExamples = 'Give a sample value for every variable (Meta needs it to review)';
      }
    }
    if (state.headerType === 'TEXT') {
      if (!state.headerText?.trim()) errors.headerText = 'Header text is required';
      else if (state.headerText.length > 60) errors.headerText = 'Header can be at most 60 characters';
      else if (extractVariables(state.headerText).length > 1) errors.headerText = 'Header can have only one variable';
      else if (extractVariables(state.headerText).length === 1 && !state.headerExample?.trim()) errors.headerExample = 'Sample value is required';
    }
    if (['IMAGE', 'VIDEO', 'DOCUMENT'].includes(state.headerType) && !state.headerHandle) {
      errors.headerHandle = 'Upload a sample file for Meta review';
    }
    if ((state.footer || '').length > 60) errors.footer = 'Footer can be at most 60 characters';
  }
  const buttons = state.buttons || [];
  if (buttons.length > 10) errors.buttons = 'At most 10 buttons';
  if (buttons.filter((b) => b.type === 'URL').length > 2) errors.buttons = 'At most 2 website buttons';
  if (buttons.filter((b) => b.type === 'PHONE_NUMBER').length > 1) errors.buttons = 'Only 1 call button';
  if (buttons.filter((b) => b.type === 'COPY_CODE').length > 1) errors.buttons = 'Only 1 copy code button';
  buttons.forEach((b, i) => {
    if (b.type !== 'OTP' && b.type !== 'COPY_CODE' && !b.text?.trim()) errors[`buttons.${i}.text`] = 'Button text is required';
    if ((b.text || '').length > 25) errors[`buttons.${i}.text`] = 'At most 25 characters';
    if (b.type === 'URL') {
      if (!/^https:\/\/\S+$/.test(b.url || '')) errors[`buttons.${i}.url`] = 'Enter a link starting with https://';
      else if (/\{\{/.test(b.url) && !/\{\{1\}\}$/.test(b.url)) errors[`buttons.${i}.url`] = 'A variable is allowed only at the end as {{1}}';
      else if (/\{\{/.test(b.url) && !b.example?.trim()) errors[`buttons.${i}.example`] = 'Sample link ending is required';
    }
    if (b.type === 'PHONE_NUMBER' && !/^\+?[1-9]\d{7,14}$/.test(b.phoneNumber || '')) errors[`buttons.${i}.phoneNumber`] = 'Enter a phone number with country code';
    if (b.type === 'OTP' && (b.otpType === 'ONE_TAP' || b.otpType === 'ZERO_TAP')) {
      if (!b.packageName?.trim()) errors[`buttons.${i}.packageName`] = 'Android package name is required';
      if (!b.signatureHash?.trim()) errors[`buttons.${i}.signatureHash`] = 'App signature hash is required';
    }
  });
  if (auth && !buttons.some((b) => b.type === 'OTP')) errors.buttons = 'Authentication templates need a code button';
  return errors;
}
