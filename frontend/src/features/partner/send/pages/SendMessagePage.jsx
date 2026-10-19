import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router-dom';
import { Clock, FileText, MessageSquareText, Send } from 'lucide-react';
import toast from 'react-hot-toast';
import * as messagesApi from '../../../../api/endpoints/messages';
import * as templatesApi from '../../../../api/endpoints/templates';
import { getErrorCode, getErrorMessage } from '../../../../api/axios';
import { useAuth } from '../../../../auth/useAuth';
import {
  Alert, Button, Card, ConfirmDialog, InfoTip, Input, PageHeader, PhoneInput, RadioCards, Select, Skeleton, WhatsAppPreview, isE164Digits,
} from '../../../../components/ui';
import {
  buildTemplatePayload, templateToPreview, templateValuesComplete,
} from '../../../../components/whatsapp/TemplateBuilder';
import { formatMoney, formatRelative, humanize } from '../../../../lib/format';
import SenderSelect from '../../common/SenderSelect';
import TemplateFill from '../../common/TemplateFill';
import MessageComposer from '../../common/MessageComposer';
import { EMPTY_COMPOSER, composerPreview, composerToRequest, composerValid } from '../../common/composer';
import { defaultSenderId, useMySenders } from '../../common/useSenders';
import { useWindowStatus } from '../../common/useWindowStatus';
import { useDebounce } from '../../../../hooks/useDebounce';

function bodyValuesForPreview(template, values) {
  if (!template) return undefined;
  if (String(template.parameterFormat).toUpperCase() === 'NAMED') return values.body || {};
  const vars = Object.keys(values.body || {});
  const arr = [];
  vars.forEach((k) => {
    arr[Number(k) - 1] = values.body[k];
  });
  return arr;
}

export default function SendMessagePage() {
  const [params] = useSearchParams();
  const qc = useQueryClient();
  const { user } = useAuth();
  const senders = useMySenders();
  const [senderId, setSenderId] = useState('');
  const [to, setTo] = useState(params.get('to') || '');
  const [mode, setMode] = useState('template');
  const [templateId, setTemplateId] = useState(params.get('templateId') || '');
  const [values, setValues] = useState({});
  const [composer, setComposer] = useState(EMPTY_COMPOSER);
  const [clientReference, setClientReference] = useState('');
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [sent, setSent] = useState(null);

  useEffect(() => {
    if (!senderId && senders.data) setSenderId(defaultSenderId(senders.data));
  }, [senders.data, senderId]);
  const sender = (senders.data || []).find((s) => s.id === senderId);

  const templates = useQuery({
    queryKey: ['partner', 'templates', 'approved', sender?.waba?.id],
    queryFn: () => templatesApi.listMyTemplates({ status: 'APPROVED', wabaId: sender?.waba?.id, size: 100 }),
    enabled: Boolean(sender),
  });
  const templateList = templates.data?.content || [];
  const template = templateList.find((t) => String(t.id) === String(templateId));
  useEffect(() => setValues({}), [templateId]);

  const validTo = isE164Digits(to);
  const win = useWindowStatus(senderId, validTo ? to : '');
  const freeForm = mode === 'free';

  const ready =
    Boolean(senderId) && validTo && (freeForm ? composerValid(composer) && win.open : Boolean(template) && templateValuesComplete(template, values));

  const body = useMemo(() => {
    if (!ready) return null;
    const base = { senderId, to, clientReference: clientReference || undefined };
    if (freeForm) return { ...base, ...composerToRequest(composer) };
    return { ...base, type: 'template', template: buildTemplatePayload(template, values) };
  }, [ready, senderId, to, clientReference, freeForm, composer, template, values]);

  const estimateKey = useDebounce(JSON.stringify({ senderId, to, mode, templateId, ok: validTo && Boolean(senderId) }), 400);
  const estimate = useQuery({
    queryKey: ['partner', 'estimate', estimateKey],
    queryFn: () =>
      messagesApi.estimateCost({
        senderId,
        recipients: [to],
        type: freeForm ? composerToRequest(composer)?.type || 'text' : 'template',
        templateId: freeForm ? undefined : template?.id,
      }),
    enabled: Boolean(senderId) && validTo && (freeForm || Boolean(template)),
    retry: false,
  });

  const send = useMutation({
    mutationFn: () => messagesApi.sendMessage(body),
    onSuccess: (m) => {
      setConfirmOpen(false);
      setSent(m);
      toast.success('Message queued for sending');
      qc.invalidateQueries({ queryKey: ['partner', 'wallet'] });
      qc.invalidateQueries({ queryKey: ['partner', 'dashboard'] });
    },
    onError: (e) => {
      setConfirmOpen(false);
      toast.error(getErrorMessage(e));
    },
  });

  const preview = freeForm
    ? composerPreview(composer)
    : template
      ? { ...templateToPreview(template), values: bodyValuesForPreview(template, values), headerValues: values.header?.text ? [values.header.text] : undefined }
      : { body: '' };

  const est = estimate.data;
  const insufficient = est && est.sufficient === false;
  const sendError = send.error && getErrorCode(send.error);

  return (
    <>
      <PageHeader title="Send message" subtitle="Send one WhatsApp message. For many people use a campaign." />
      {sent && (
        <Alert variant="success" className="mb-4" title="Message queued" onClose={() => setSent(null)}>
          It will be delivered in a few seconds. <Link className="font-medium underline" to={`/partner/reports?search=${sent.uid}`}>Track this message</Link>
        </Alert>
      )}
      <div className="grid gap-5 lg:grid-cols-[minmax(0,1fr)_360px]">
        <div className="space-y-5">
          <Card title="1. From and to">
            <div className="grid gap-4 sm:grid-cols-2">
              <SenderSelect value={senderId} onChange={setSenderId} />
              <PhoneInput label="Customer WhatsApp number" value={to} onChange={setTo} error={to && !validTo ? 'Enter a valid number' : undefined} required />
            </div>
            {validTo && senderId && !win.loading && (
              <p className="mt-3 flex items-center gap-1.5 text-sm text-muted">
                <Clock className="h-4 w-4" aria-hidden />
                {win.open
                  ? `Chat window is open (closes ${formatRelative(win.expiresAt)}). You can send any message.`
                  : 'Chat window is closed. Only approved templates can be sent.'}
                <InfoTip term="SERVICE" />
              </p>
            )}
          </Card>

          <Card title="2. Message">
            <RadioCards
              name="mode"
              columns={2}
              value={mode}
              onChange={setMode}
              options={[
                { value: 'template', label: 'Template', description: 'Works any time', icon: FileText },
                { value: 'free', label: 'Free message', description: 'Only within 24 hours after the customer messaged you', icon: MessageSquareText },
              ]}
            />
            <div className="mt-5">
              {mode === 'template' ? (
                !sender ? (
                  <p className="text-sm text-muted">Choose a number first.</p>
                ) : templates.isLoading ? (
                  <Skeleton className="h-11 w-full" />
                ) : templateList.length === 0 ? (
                  <Alert variant="info" title="No approved templates for this number" action={<Button size="sm" to="/partner/templates/new">Create template</Button>}>
                    Templates need Meta approval before you can send them.
                  </Alert>
                ) : (
                  <div className="space-y-4">
                    <Select
                      label="Template"
                      value={templateId}
                      onChange={(e) => setTemplateId(e.target.value)}
                      options={templateList.map((t) => ({ value: t.id, label: `${t.name} (${t.language}) · ${humanize(t.category)}` }))}
                      placeholder="Choose a template"
                    />
                    {template && <TemplateFill template={template} senderId={senderId} values={values} onChange={setValues} />}
                  </div>
                )
              ) : (
                <>
                  {validTo && senderId && !win.loading && !win.open && (
                    <Alert variant="warning" className="mb-4" title="Chat window is closed">
                      This customer has not messaged you in the last 24 hours. Use a template instead.
                    </Alert>
                  )}
                  <MessageComposer senderId={senderId} value={composer} onChange={setComposer} />
                </>
              )}
            </div>
            <Input className="mt-4" label="Your reference (optional)" maxLength={100} value={clientReference} onChange={(e) => setClientReference(e.target.value)} hint="For example an order or invoice number. Shown in reports." />
          </Card>

          <Card title="3. Cost and send">
            {!senderId || !validTo || (!freeForm && !template) ? (
              <p className="text-sm text-muted">The price appears after you choose the number, customer and message.</p>
            ) : estimate.isLoading ? (
              <Skeleton className="h-10 w-48" />
            ) : estimate.isError ? (
              <Alert variant="error">{getErrorMessage(estimate.error)}</Alert>
            ) : est ? (
              <div className="flex flex-wrap items-end justify-between gap-3">
                <div>
                  <p className="text-sm text-muted">This message costs</p>
                  <p className="text-2xl font-bold text-ink">{formatMoney(est.total, { precise: true })}</p>
                  <p className="text-xs text-muted">
                    {humanize(est.category)} · Balance after sending {formatMoney(est.balanceAfter)}
                  </p>
                </div>
                {insufficient && <Button variant="outline" to="/partner/wallet/add">Add money</Button>}
              </div>
            ) : null}
            {sendError === 'WALLET_INSUFFICIENT_BALANCE' && <Alert variant="error" className="mt-3">Not enough balance. Add money and try again.</Alert>}
            <Button className="mt-4" fullWidth size="lg" leftIcon={Send} disabled={!ready || insufficient} onClick={() => setConfirmOpen(true)}>
              Send message
            </Button>
          </Card>
        </div>

        <div className="lg:sticky lg:top-4 lg:self-start">
          <p className="mb-2 text-sm font-medium text-muted">Preview</p>
          <WhatsAppPreview {...preview} businessName={sender?.verifiedName || user?.partner?.businessName || 'Your business'} />
        </div>
      </div>

      <ConfirmDialog
        open={confirmOpen}
        onClose={() => setConfirmOpen(false)}
        onConfirm={() => send.mutate()}
        loading={send.isPending}
        title="Send this message?"
        confirmLabel="Send now"
        message={
          <>
            To <strong>+{to}</strong> from <strong>{sender?.displayPhoneNumber}</strong>.
            {est && <> Cost <strong>{formatMoney(est.total, { precise: true })}</strong> will be held from your wallet and refunded if the message fails.</>}
          </>
        }
      />
    </>
  );
}
