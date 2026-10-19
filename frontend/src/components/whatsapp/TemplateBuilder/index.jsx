import { useMemo, useRef, useState } from 'react';
import { Plus, Trash2, Variable } from 'lucide-react';
import toast from 'react-hot-toast';
import {
  Alert, Button, Card, Checkbox, FileUpload, IconButton, InfoTip, Input, RadioCards, Select, Textarea, WhatsAppPreview,
} from '../../ui';
import { getErrorMessage } from '../../../api/axios';
import {
  CATEGORY_HELP, TEMPLATE_LANGUAGES, builderToComponents, extractVariables, nextVariable, validateBuilder,
} from './templateUtils';

export * from './templateUtils';

const EMPTY = {
  wabaDbId: '',
  name: '',
  language: 'en',
  category: 'UTILITY',
  parameterFormat: 'POSITIONAL',
  headerType: 'NONE',
  headerText: '',
  headerExample: '',
  headerHandle: '',
  headerFileUid: '',
  body: '',
  bodyExamples: {},
  footer: '',
  addSecurityRecommendation: true,
  codeExpirationMinutes: 10,
  buttons: [],
};

const BUTTON_TYPES = [
  { value: 'QUICK_REPLY', label: 'Quick reply' },
  { value: 'URL', label: 'Open website' },
  { value: 'PHONE_NUMBER', label: 'Call phone number' },
  { value: 'COPY_CODE', label: 'Copy coupon code' },
];

const MEDIA_ACCEPT = { IMAGE: 'image/jpeg,image/png', VIDEO: 'video/mp4', DOCUMENT: 'application/pdf' };
const MEDIA_MAX = { IMAGE: 5, VIDEO: 16, DOCUMENT: 16 };

/**
 * Reusable template builder with live WhatsApp preview (partner and admin).
 * Props:
 *  - initial: builder state (see componentsToBuilder) for editing
 *  - wabaOptions: [{ value: wabaDbId, label }] — the WhatsApp account the template belongs to
 *  - lockIdentity: disable name/language/WABA (editing an existing template)
 *  - uploadHeader(wabaDbId, file) => Promise<{ fileUid, headerHandle }>
 *  - onSubmit(body, { submit }) where body = TP2 request; submit=false saves a draft
 *  - submitting, submitLabel, allowDraft, businessName
 */
export default function TemplateBuilder({
  initial,
  wabaOptions = [],
  lockIdentity = false,
  uploadHeader,
  onSubmit,
  submitting = false,
  submitLabel = 'Submit for approval',
  allowDraft = true,
  businessName,
}) {
  const [state, setState] = useState(() => ({ ...EMPTY, wabaDbId: wabaOptions[0]?.value ?? '', ...(initial || {}) }));
  const [errors, setErrors] = useState({});
  const [headerFile, setHeaderFile] = useState(null);
  const [uploading, setUploading] = useState(false);
  const bodyRef = useRef(null);

  const set = (patch) => setState((s) => ({ ...s, ...patch }));
  const auth = state.category === 'AUTHENTICATION';
  const bodyVars = useMemo(() => extractVariables(state.body), [state.body]);
  const headerVars = useMemo(() => extractVariables(state.headerText), [state.headerText]);

  const insertVariable = () => {
    const token = nextVariable(state.body, state.parameterFormat);
    const el = bodyRef.current;
    if (el && typeof el.selectionStart === 'number') {
      const start = el.selectionStart;
      const end = el.selectionEnd;
      const next = state.body.slice(0, start) + token + state.body.slice(end);
      set({ body: next });
      requestAnimationFrame(() => {
        el.focus();
        el.setSelectionRange(start + token.length, start + token.length);
      });
    } else set({ body: `${state.body}${token}` });
  };

  const setButton = (i, patch) => set({ buttons: state.buttons.map((b, idx) => (idx === i ? { ...b, ...patch } : b)) });
  const addButton = (type) => {
    if (state.buttons.length >= 10) return;
    set({ buttons: [...state.buttons, { type, text: '', url: '', phoneNumber: '', example: '', otpType: 'COPY_CODE' }] });
  };
  const removeButton = (i) => set({ buttons: state.buttons.filter((_, idx) => idx !== i) });

  const onCategory = (category) => {
    if (category === 'AUTHENTICATION') {
      set({ category, headerType: 'NONE', buttons: [{ type: 'OTP', otpType: 'COPY_CODE', text: 'Copy code' }] });
    } else {
      set({ category, buttons: state.buttons.filter((b) => b.type !== 'OTP') });
    }
  };

  const onHeaderFile = async (file) => {
    setHeaderFile(file);
    if (!file) {
      set({ headerHandle: '', headerFileUid: '' });
      return;
    }
    if (!state.wabaDbId) {
      toast.error('Choose the WhatsApp account first');
      return;
    }
    setUploading(true);
    try {
      const res = await uploadHeader(state.wabaDbId, file);
      set({ headerHandle: res.headerHandle, headerFileUid: res.fileUid });
      toast.success('Sample uploaded');
    } catch (err) {
      toast.error(getErrorMessage(err));
      setHeaderFile(null);
    } finally {
      setUploading(false);
    }
  };

  const submit = (doSubmit) => {
    const errs = validateBuilder(state);
    if (!state.wabaDbId) errs.wabaDbId = 'Choose a WhatsApp account';
    setErrors(errs);
    if (Object.keys(errs).length) {
      toast.error('Please fix the highlighted fields');
      return;
    }
    onSubmit(
      {
        wabaDbId: Number(state.wabaDbId),
        name: state.name,
        language: state.language,
        category: state.category,
        parameterFormat: state.parameterFormat,
        components: builderToComponents(state),
        allowCategoryChange: true,
        submit: doSubmit,
      },
      { submit: doSubmit },
    );
  };

  const headerFileUrl = useMemo(
    () => (headerFile && state.headerType !== 'DOCUMENT' ? URL.createObjectURL(headerFile) : undefined),
    [headerFile, state.headerType],
  );
  const previewHeader =
    state.headerType === 'NONE' || auth
      ? null
      : {
          format: state.headerType,
          text: state.headerText,
          url: headerFileUrl,
          filename: headerFile?.name,
        };
  const previewBody = auth
    ? `{{1}} is your verification code.${state.addSecurityRecommendation ? ' For your security, do not share this code.' : ''}`
    : state.body;
  const previewFooter = auth ? (state.codeExpirationMinutes ? `This code expires in ${state.codeExpirationMinutes} minutes.` : '') : state.footer;
  const previewValues = auth
    ? ['123456']
    : state.parameterFormat === 'NAMED'
      ? state.bodyExamples
      : bodyVars.map((v) => state.bodyExamples?.[v]);

  return (
    <div className="grid grid-cols-1 gap-5 lg:grid-cols-[minmax(0,1fr)_360px]">
      <div className="space-y-5">
        <Card title="Basics">
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            {wabaOptions.length > 1 || !lockIdentity ? (
              <Select
                label="WhatsApp account"
                info="WABA"
                value={state.wabaDbId}
                onChange={(e) => set({ wabaDbId: e.target.value })}
                options={wabaOptions}
                placeholder={wabaOptions.length ? undefined : 'No WhatsApp account available'}
                disabled={lockIdentity}
                error={errors.wabaDbId}
                className="sm:col-span-2"
              />
            ) : null}
            <Input
              label="Template name"
              hint="Lowercase letters, numbers and _ only. Example: order_shipped"
              value={state.name}
              disabled={lockIdentity}
              onChange={(e) => set({ name: e.target.value.toLowerCase().replace(/[^a-z0-9_]/g, '_') })}
              error={errors.name}
              maxLength={512}
              required
            />
            <Select
              label="Language"
              value={state.language}
              disabled={lockIdentity}
              onChange={(e) => set({ language: e.target.value })}
              options={TEMPLATE_LANGUAGES}
              error={errors.language}
              required
            />
          </div>
          <RadioCards
            className="mt-4"
            label="Category"
            name="category"
            value={state.category}
            onChange={onCategory}
            columns={3}
            options={['MARKETING', 'UTILITY', 'AUTHENTICATION'].map((c) => ({
              value: c,
              label: c.charAt(0) + c.slice(1).toLowerCase(),
              description: CATEGORY_HELP[c],
            }))}
          />
          {!auth && (
            <RadioCards
              className="mt-4"
              label="Variable style"
              name="parameterFormat"
              value={state.parameterFormat}
              onChange={(v) => set({ parameterFormat: v, bodyExamples: {} })}
              columns={2}
              options={[
                { value: 'POSITIONAL', label: 'Numbered', description: 'Use {{1}}, {{2}} …' },
                { value: 'NAMED', label: 'Named', description: 'Use {{first_name}}, {{order_id}} …' },
              ]}
            />
          )}
        </Card>

        {!auth && (
          <Card title="Header (optional)" subtitle="A title, picture, video, document or location shown on top">
            <Select
              label="Header type"
              value={state.headerType}
              onChange={(e) => set({ headerType: e.target.value, headerHandle: '', headerFileUid: '' })}
              options={[
                { value: 'NONE', label: 'No header' },
                { value: 'TEXT', label: 'Text' },
                { value: 'IMAGE', label: 'Image' },
                { value: 'VIDEO', label: 'Video' },
                { value: 'DOCUMENT', label: 'Document (PDF)' },
                { value: 'LOCATION', label: 'Location' },
              ]}
            />
            {state.headerType === 'TEXT' && (
              <div className="mt-4 grid gap-4 sm:grid-cols-2">
                <Input
                  label="Header text"
                  value={state.headerText}
                  maxLength={60}
                  onChange={(e) => set({ headerText: e.target.value })}
                  hint={`${state.headerText.length}/60 · one variable allowed`}
                  error={errors.headerText}
                />
                {headerVars.length === 1 && (
                  <Input
                    label={`Sample for {{${headerVars[0]}}}`}
                    value={state.headerExample}
                    onChange={(e) => set({ headerExample: e.target.value })}
                    error={errors.headerExample}
                  />
                )}
              </div>
            )}
            {['IMAGE', 'VIDEO', 'DOCUMENT'].includes(state.headerType) && (
              <div className="mt-4">
                <FileUpload
                  label="Sample file for Meta review"
                  value={headerFile}
                  onChange={onHeaderFile}
                  accept={MEDIA_ACCEPT[state.headerType]}
                  maxSizeMB={MEDIA_MAX[state.headerType]}
                  disabled={uploading}
                  error={errors.headerHandle}
                  hint={state.headerHandle && !headerFile ? 'A sample is already uploaded. Choose a file to replace it.' : undefined}
                />
                {uploading && <p className="mt-2 text-sm text-muted">Uploading sample…</p>}
                <p className="mt-2 text-xs text-muted">When sending, you can use a different file each time.</p>
              </div>
            )}
            {state.headerType === 'LOCATION' && (
              <p className="mt-3 text-sm text-muted">You will choose the location each time you send this template.</p>
            )}
          </Card>
        )}

        <Card title="Message">
          {auth ? (
            <div className="space-y-4">
              <Alert variant="info">Meta writes the text of authentication messages. You only choose the options below.</Alert>
              <Checkbox
                label="Add security advice"
                description='Adds "For your security, do not share this code."'
                checked={state.addSecurityRecommendation}
                onChange={(e) => set({ addSecurityRecommendation: e.target.checked })}
              />
              <Input
                label="Code expires after (minutes)"
                type="number"
                min={1}
                max={90}
                value={state.codeExpirationMinutes}
                onChange={(e) => set({ codeExpirationMinutes: e.target.value })}
                hint="Leave empty to hide the expiry line"
              />
            </div>
          ) : (
            <div className="space-y-4">
              <div>
                <div className="mb-1 flex items-center justify-between gap-2">
                  <span className="label-base mb-0">Message text</span>
                  <Button size="sm" variant="secondary" leftIcon={Variable} onClick={insertVariable}>
                    Add variable
                  </Button>
                </div>
                <Textarea
                  ref={bodyRef}
                  rows={6}
                  maxLength={1024}
                  showCount
                  value={state.body}
                  onChange={(e) => set({ body: e.target.value })}
                  error={errors.body}
                  hint="Use *bold*, _italic_ and ~strike~. Variables are filled in when you send."
                />
              </div>
              {bodyVars.length > 0 && (
                <div>
                  <p className="label-base">Sample values (Meta uses these to review)</p>
                  <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                    {bodyVars.map((v) => (
                      <Input
                        key={v}
                        label={`{{${v}}}`}
                        value={state.bodyExamples?.[v] || ''}
                        onChange={(e) => set({ bodyExamples: { ...state.bodyExamples, [v]: e.target.value } })}
                      />
                    ))}
                  </div>
                  {errors.bodyExamples && <p className="mt-1 text-xs text-red-600">{errors.bodyExamples}</p>}
                </div>
              )}
              <Input
                label="Footer (optional)"
                value={state.footer}
                maxLength={60}
                onChange={(e) => set({ footer: e.target.value })}
                hint={`${state.footer.length}/60 · small grey text, no variables`}
                error={errors.footer}
              />
            </div>
          )}
        </Card>

        <Card
          title="Buttons (optional)"
          subtitle={auth ? 'The code button lets customers copy or auto-fill the code' : 'Up to 10 buttons'}
          actions={
            !auth && (
              <Select
                aria-label="Add a button"
                value=""
                onChange={(e) => e.target.value && addButton(e.target.value)}
                options={BUTTON_TYPES}
                placeholder="+ Add button"
                selectClassName="min-h-[36px]"
                className="w-48"
              />
            )
          }
        >
          {errors.buttons && <p className="mb-3 text-sm text-red-600">{errors.buttons}</p>}
          {state.buttons.length === 0 ? (
            <p className="text-sm text-muted">No buttons. Buttons help customers reply or act with one tap.</p>
          ) : (
            <ul className="space-y-4">
              {state.buttons.map((b, i) => (
                <li key={i} className="rounded-xl border border-line p-3">
                  <div className="mb-3 flex items-center justify-between">
                    <span className="text-sm font-semibold text-ink">
                      {b.type === 'OTP' ? 'Code button' : BUTTON_TYPES.find((t) => t.value === b.type)?.label}
                    </span>
                    {!auth && <IconButton icon={Trash2} label="Remove button" variant="danger" size="sm" onClick={() => removeButton(i)} />}
                  </div>
                  <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                    {b.type === 'OTP' ? (
                      <>
                        <Select
                          label="Code button type"
                          value={b.otpType}
                          onChange={(e) => setButton(i, { otpType: e.target.value })}
                          options={[
                            { value: 'COPY_CODE', label: 'Copy code' },
                            { value: 'ONE_TAP', label: 'One-tap autofill (Android)' },
                            { value: 'ZERO_TAP', label: 'Zero-tap (Android)' },
                          ]}
                        />
                        <Input label="Button text" value={b.text} maxLength={25} onChange={(e) => setButton(i, { text: e.target.value })} />
                        {(b.otpType === 'ONE_TAP' || b.otpType === 'ZERO_TAP') && (
                          <>
                            <Input
                              label="Android package name"
                              placeholder="com.example.app"
                              value={b.packageName}
                              onChange={(e) => setButton(i, { packageName: e.target.value })}
                              error={errors[`buttons.${i}.packageName`]}
                            />
                            <Input
                              label="App signature hash"
                              value={b.signatureHash}
                              onChange={(e) => setButton(i, { signatureHash: e.target.value })}
                              error={errors[`buttons.${i}.signatureHash`]}
                            />
                          </>
                        )}
                      </>
                    ) : (
                      <>
                        {b.type !== 'COPY_CODE' && (
                          <Input
                            label="Button text"
                            value={b.text}
                            maxLength={25}
                            onChange={(e) => setButton(i, { text: e.target.value })}
                            error={errors[`buttons.${i}.text`]}
                          />
                        )}
                        {b.type === 'URL' && (
                          <>
                            <Input
                              label="Website link"
                              placeholder="https://example.com/track/{{1}}"
                              value={b.url}
                              onChange={(e) => setButton(i, { url: e.target.value })}
                              hint="Add {{1}} at the end to change the link for each message"
                              error={errors[`buttons.${i}.url`]}
                            />
                            {/\{\{/.test(b.url || '') && (
                              <Input
                                label="Sample link ending"
                                placeholder="ORDER123"
                                value={b.example}
                                onChange={(e) => setButton(i, { example: e.target.value })}
                                error={errors[`buttons.${i}.example`]}
                              />
                            )}
                          </>
                        )}
                        {b.type === 'PHONE_NUMBER' && (
                          <Input
                            label="Phone number"
                            placeholder="+919876543210"
                            value={b.phoneNumber}
                            onChange={(e) => setButton(i, { phoneNumber: e.target.value })}
                            error={errors[`buttons.${i}.phoneNumber`]}
                          />
                        )}
                        {b.type === 'COPY_CODE' && (
                          <Input label="Sample coupon code" value={b.example} maxLength={15} onChange={(e) => setButton(i, { example: e.target.value })} />
                        )}
                      </>
                    )}
                  </div>
                </li>
              ))}
            </ul>
          )}
          {!auth && state.buttons.length < 10 && (
            <div className="mt-3 flex flex-wrap gap-2 lg:hidden">
              {BUTTON_TYPES.map((t) => (
                <Button key={t.value} size="sm" variant="secondary" leftIcon={Plus} onClick={() => addButton(t.value)}>
                  {t.label}
                </Button>
              ))}
            </div>
          )}
        </Card>

        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          {allowDraft && (
            <Button variant="secondary" onClick={() => submit(false)} loading={submitting} disabled={uploading}>
              Save as draft
            </Button>
          )}
          <Button onClick={() => submit(true)} loading={submitting} disabled={uploading}>
            {submitLabel}
          </Button>
        </div>
      </div>

      <div className="lg:sticky lg:top-4 lg:self-start">
        <p className="mb-2 flex items-center gap-1 text-sm font-medium text-muted">
          Live preview <InfoTip term="TEMPLATE" />
        </p>
        <WhatsAppPreview
          businessName={businessName || 'Your business'}
          header={previewHeader}
          headerValues={
            state.headerExample
              ? state.parameterFormat === 'NAMED' && headerVars[0]
                ? { [headerVars[0]]: state.headerExample }
                : [state.headerExample]
              : undefined
          }
          body={previewBody}
          footer={previewFooter}
          buttons={state.buttons.map((b) => ({ type: b.type, text: b.text || (b.type === 'COPY_CODE' ? 'Copy code' : '') }))}
          values={previewValues}
        />
      </div>
    </div>
  );
}
