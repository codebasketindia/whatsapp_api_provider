import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { CalendarClock, FileText, MessageSquareText } from 'lucide-react';
import toast from 'react-hot-toast';
import * as scheduledApi from '../../../../api/endpoints/scheduled';
import * as templatesApi from '../../../../api/endpoints/templates';
import { getErrorMessage } from '../../../../api/axios';
import { useAuth } from '../../../../auth/useAuth';
import { Alert, Button, Input, Modal, PhoneInput, RadioCards, Select, Skeleton, WhatsAppPreview, isE164Digits } from '../../../../components/ui';
import { buildTemplatePayload, templateToPreview, templateValuesComplete } from '../../../../components/whatsapp/TemplateBuilder/templateUtils';
import { formatDateTime, formatRelative, humanize } from '../../../../lib/format';
import SenderSelect from '../../common/SenderSelect';
import TemplateFill from '../../common/TemplateFill';
import MessageComposer from '../../common/MessageComposer';
import { EMPTY_COMPOSER, composerPreview, composerToRequest, composerValid } from '../../common/composer';
import { defaultSenderId, useMySenders } from '../../common/useSenders';
import { useWindowStatus } from '../../common/useWindowStatus';
import { istLocalToIso, nowIstLocal } from '../../campaigns/components/schedule';
import { isoToIstLocal, scheduleTimeSchema, windowAtTime } from './scheduleTime';
import { requestToComposer, requestToTemplateValues } from './requestForm';

const schema = z.object({
  senderId: z.coerce.number({ invalid_type_error: 'Choose a number' }).int().positive('Choose a number'),
  to: z.string().refine((v) => isE164Digits(v), 'Enter a valid WhatsApp number'),
  at: scheduleTimeSchema,
});

function previewValues(template, values) {
  if (!template) return undefined;
  if (String(template.parameterFormat).toUpperCase() === 'NAMED') return values.body || {};
  const arr = [];
  Object.keys(values.body || {}).forEach((k) => {
    arr[Number(k) - 1] = values.body[k];
  });
  return arr;
}

/** Free-form messages need an open 24-hour window at send time; show what we know. */
export function WindowNotice({ win, iso, freeForm }) {
  if (!freeForm || !iso || win.loading || !win.known) return null;
  const state = windowAtTime(win, iso);
  if (state === 'open') {
    return <p className="text-xs text-green-700 dark:text-green-400">The customer’s 24-hour chat window will still be open at this time.</p>;
  }
  return (
    <Alert variant="warning" title="Chat window may be closed at this time">
      {win.open
        ? `The window closes ${formatDateTime(win.expiresAt)}. If the customer does not message you again before then, this message will fail (no charge). Use a template to be safe.`
        : 'The customer has not messaged you in the last 24 hours. Unless they message you before this time, it will fail (no charge). Use a template to be safe.'}
    </Alert>
  );
}

/**
 * Create (SC2) or edit (SC4) a scheduled message.
 * item: ScheduledMessageDto when editing. initial: { senderId, to } for new ones.
 */
export default function ScheduledMessageForm({ open, onClose, item, initial }) {
  const qc = useQueryClient();
  const { user } = useAuth();
  const senders = useMySenders();
  const editing = Boolean(item);
  const req = item?.request || {};
  const { control, register, handleSubmit, reset, watch, setValue, formState } = useForm({
    resolver: zodResolver(schema),
    defaultValues: { senderId: '', to: '', at: '' },
  });
  const [mode, setMode] = useState('template');
  const [templateId, setTemplateId] = useState('');
  const [values, setValues] = useState({});
  const [composer, setComposer] = useState(EMPTY_COMPOSER);

  useEffect(() => {
    if (!open) return;
    const isTemplate = item ? String(req.type).toLowerCase() === 'template' : true;
    reset({
      senderId: item?.sender?.id || initial?.senderId || defaultSenderId(senders.data || []) || '',
      to: item?.to || initial?.to || '',
      at: item ? isoToIstLocal(item.scheduledAt) : nowIstLocal(60),
    });
    setMode(isTemplate ? 'template' : 'free');
    setTemplateId(isTemplate && req.template?.templateId ? String(req.template.templateId) : '');
    setValues({});
    setComposer(item && !isTemplate ? requestToComposer(req) : EMPTY_COMPOSER);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, item?.uid]);

  const senderId = Number(watch('senderId')) || '';
  const to = watch('to');
  const iso = istLocalToIso(watch('at'));
  const sender = (senders.data || []).find((s) => s.id === senderId);
  useEffect(() => {
    if (open && !senderId && senders.data?.length) setValue('senderId', defaultSenderId(senders.data));
  }, [open, senderId, senders.data, setValue]);

  const templates = useQuery({
    queryKey: ['partner', 'templates', 'approved', sender?.waba?.id],
    queryFn: () => templatesApi.listMyTemplates({ status: 'APPROVED', wabaId: sender?.waba?.id, size: 100 }),
    enabled: open && Boolean(sender),
  });
  const templateList = templates.data?.content || [];
  const template = templateList.find((t) => String(t.id) === String(templateId));
  // Prefill template values once when editing (inverse of buildTemplatePayload).
  useEffect(() => {
    if (editing && template && String(req.template?.templateId) === String(template.id)) setValues(requestToTemplateValues(template, req.template));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [template?.id]);

  const freeForm = mode === 'free';
  const win = useWindowStatus(senderId, isE164Digits(to) ? to : '');
  const messageOk = freeForm ? composerValid(composer) : Boolean(template) && templateValuesComplete(template, values);

  const save = useMutation({
    mutationFn: (v) => {
      const message = {
        senderId: v.senderId,
        to: v.to,
        ...(freeForm ? composerToRequest(composer) : { type: 'template', template: buildTemplatePayload(template, values) }),
      };
      const body = { scheduledAt: istLocalToIso(v.at), message };
      return editing ? scheduledApi.updateScheduled(item.uid, body) : scheduledApi.createScheduled(body);
    },
    onSuccess: (res) => {
      toast.success(editing ? 'Scheduled message updated' : `Message scheduled for ${formatDateTime(res?.scheduledAt || iso)}`);
      if (res && res.windowOpenAtSchedule === false && freeForm) toast('The chat window may be closed at that time — it will fail unless the customer messages you.', { icon: '⚠️' });
      qc.invalidateQueries({ queryKey: ['partner', 'scheduled'] });
      onClose();
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });

  const preview = useMemo(() => {
    if (freeForm) return composerPreview(composer);
    if (!template) return { body: '' };
    return { ...templateToPreview(template), values: previewValues(template, values), headerValues: values.header?.text ? [values.header.text] : undefined };
  }, [freeForm, composer, template, values]);

  return (
    <Modal
      open={open}
      onClose={onClose}
      size="xl"
      title={editing ? 'Edit scheduled message' : 'New scheduled message'}
      description="It is sent automatically at the time you choose (India time). Money is taken only when it is sent."
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button leftIcon={CalendarClock} loading={save.isPending} disabled={!messageOk} onClick={handleSubmit((v) => save.mutate(v))}>
            {editing ? 'Save changes' : 'Schedule'}
          </Button>
        </>
      }
    >
      <div className="grid grid-cols-1 gap-5 lg:grid-cols-[minmax(0,1fr)_320px]">
        <div className="space-y-4">
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <Controller
              name="senderId"
              control={control}
              render={({ field }) => <SenderSelect value={field.value} onChange={field.onChange} error={formState.errors.senderId?.message} />}
            />
            <Controller
              name="to"
              control={control}
              render={({ field }) => <PhoneInput label="Customer WhatsApp number" value={field.value} onChange={field.onChange} error={formState.errors.to?.message} required />}
            />
          </div>
          <Input
            type="datetime-local"
            label="Send on (India time)"
            min={nowIstLocal(1)}
            max={nowIstLocal(90 * 24 * 60)}
            error={formState.errors.at?.message}
            hint={iso ? `${formatDateTime(iso)} · ${formatRelative(iso)}` : 'Between 1 minute and 90 days from now.'}
            {...register('at')}
          />
          <RadioCards
            name="sched-mode"
            columns={2}
            value={mode}
            onChange={setMode}
            options={[
              { value: 'template', label: 'Template', description: 'Always delivered', icon: FileText },
              { value: 'free', label: 'Text / photo / video', description: 'Only if the customer messaged you in the 24 hours before', icon: MessageSquareText },
            ]}
          />
          {freeForm ? (
            <>
              <WindowNotice win={win} iso={iso} freeForm />
              <MessageComposer senderId={senderId} value={composer} onChange={setComposer} />
            </>
          ) : !sender ? (
            <p className="text-sm text-muted">Choose a number first.</p>
          ) : templates.isLoading ? (
            <Skeleton className="h-11 w-full" />
          ) : !templateList.length ? (
            <Alert variant="info" title="No approved templates for this number" action={<Button size="sm" to="/partner/templates/new">Create template</Button>}>
              Templates need Meta approval before you can use them.
            </Alert>
          ) : (
            <div className="space-y-4">
              <Select
                label="Template"
                value={templateId}
                onChange={(e) => {
                  setTemplateId(e.target.value);
                  setValues({});
                }}
                options={templateList.map((t) => ({ value: t.id, label: `${t.name} (${t.language}) · ${humanize(t.category)}` }))}
                placeholder="Choose a template"
              />
              {template && <TemplateFill template={template} senderId={senderId} values={values} onChange={setValues} />}
            </div>
          )}
        </div>
        <div>
          <p className="mb-2 text-sm font-medium text-muted">Preview</p>
          <WhatsAppPreview {...preview} businessName={sender?.verifiedName || user?.partner?.businessName || 'Your business'} />
        </div>
      </div>
    </Modal>
  );
}
