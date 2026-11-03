import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { FileText, MessageSquareText } from 'lucide-react';
import * as templatesApi from '../../../../api/endpoints/templates';
import { Alert, Button, RadioCards, Select, Skeleton } from '../../../../components/ui';
import { buildTemplatePayload, templateValuesComplete } from '../../../../components/whatsapp/TemplateBuilder/templateUtils';
import { humanize } from '../../../../lib/format';
import TemplateFill from '../../common/TemplateFill';
import MessageComposer from '../../common/MessageComposer';
import { EMPTY_COMPOSER, composerToRequest, composerValid } from '../../common/composer';
import { useMySenders } from '../../common/useSenders';
import { requestToComposer, requestToTemplateValues } from '../../scheduled/components/requestForm';

/** Approved templates of the sender's WhatsApp account. */
export function useApprovedTemplates(senderId) {
  const senders = useMySenders();
  const sender = (senders.data || []).find((s) => s.id === Number(senderId));
  const q = useQuery({
    queryKey: ['partner', 'templates', 'approved', sender?.waba?.id],
    queryFn: () => templatesApi.listMyTemplates({ status: 'APPROVED', wabaId: sender?.waba?.id, size: 100 }),
    enabled: Boolean(sender),
  });
  return { sender, query: q, list: q.data?.content || [] };
}

/**
 * Template or free-form message editor that reports a SendMessageRequest body (no senderId/to) via
 * onChange(request | null). {name}, {mobile} and {passNo} typed anywhere are filled in per person.
 */
export default function MessageBuilder({ senderId, value, onChange }) {
  const { sender, query, list } = useApprovedTemplates(senderId);
  const initialTemplate = String(value?.type || 'template').toLowerCase() === 'template';
  const [mode, setMode] = useState(initialTemplate ? 'template' : 'free');
  const [templateId, setTemplateId] = useState(initialTemplate && value?.template?.templateId ? String(value.template.templateId) : '');
  const [values, setValues] = useState({});
  const [composer, setComposer] = useState(!initialTemplate && value ? requestToComposer(value) : EMPTY_COMPOSER);
  const [prefilled, setPrefilled] = useState(false);
  const template = list.find((t) => String(t.id) === String(templateId));

  useEffect(() => {
    if (!prefilled && template && value?.template && String(value.template.templateId) === String(template.id)) {
      setValues(requestToTemplateValues(template, value.template));
      setPrefilled(true);
    }
  }, [template, value, prefilled]);

  useEffect(() => {
    if (mode === 'free') {
      onChange(composerValid(composer) ? composerToRequest(composer) : null);
    } else {
      onChange(template && templateValuesComplete(template, values) ? { type: 'template', template: buildTemplatePayload(template, values) } : null);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mode, composer, template, values]);

  return (
    <div className="space-y-4">
      <RadioCards
        name={`mb-mode-${senderId}`}
        columns={2}
        value={mode}
        onChange={setMode}
        options={[
          { value: 'template', label: 'Template', description: 'Always delivered', icon: FileText },
          { value: 'free', label: 'Text / photo / video', description: 'Only if they messaged you in the last 24 hours', icon: MessageSquareText },
        ]}
      />
      <p className="text-xs text-muted">Type <code>{'{name}'}</code>, <code>{'{mobile}'}</code> or <code>{'{passNo}'}</code> to fill in each person’s details.</p>
      {mode === 'free' ? (
        <MessageComposer senderId={senderId} value={composer} onChange={setComposer} compact />
      ) : !sender ? (
        <p className="text-sm text-muted">Choose the WhatsApp number first.</p>
      ) : query.isLoading ? (
        <Skeleton className="h-11 w-full" />
      ) : !list.length ? (
        <Alert variant="info" title="No approved templates for this number" action={<Button size="sm" to="/partner/templates/new">Create template</Button>}>
          Templates need Meta approval before you can use them.
        </Alert>
      ) : (
        <>
          <Select
            label="Template"
            value={templateId}
            onChange={(e) => {
              setTemplateId(e.target.value);
              setValues({});
            }}
            options={list.map((t) => ({ value: t.id, label: `${t.name} (${t.language}) · ${humanize(t.category)}` }))}
            placeholder="Choose a template"
          />
          {template && <TemplateFill template={template} senderId={senderId} values={values} onChange={setValues} />}
        </>
      )}
    </div>
  );
}
