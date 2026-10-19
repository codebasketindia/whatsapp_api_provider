import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { BookOpen } from 'lucide-react';
import toast from 'react-hot-toast';
import * as templatesApi from '../../../../api/endpoints/templates';
import { getErrorMessage } from '../../../../api/axios';
import { useAuth } from '../../../../auth/useAuth';
import {
  Badge, Button, EmptyState, ErrorState, FilterBar, Input, Modal, PageHeader, SearchInput, Select, Skeleton, WhatsAppPreview,
} from '../../../../components/ui';
import { TEMPLATE_LANGUAGES } from '../../../../components/whatsapp/TemplateBuilder';
import { humanize } from '../../../../lib/format';
import { useWabaOptions } from '../components/useWabaOptions';

const TOPICS = ['ACCOUNT_UPDATE', 'CUSTOMER_FEEDBACK', 'EVENT_REMINDER', 'ORDER_MANAGEMENT', 'PAYMENTS', 'APPOINTMENTS'];

function libraryPreview(t) {
  const buttons = (t.buttons || []).map((b) => ({ type: b.type, text: b.text }));
  const header = t.header ? (typeof t.header === 'string' ? { format: 'TEXT', text: t.header } : t.header) : null;
  return { header, body: t.body, footer: t.footer, buttons };
}

function UseModal({ template, onClose }) {
  const navigate = useNavigate();
  const qc = useQueryClient();
  const wabas = useWabaOptions();
  const [wabaDbId, setWaba] = useState('');
  const [name, setName] = useState(template.name);
  const [language, setLanguage] = useState(template.language || 'en_US');
  const urlButtons = (template.buttons || []).map((b, i) => ({ ...b, i })).filter((b) => b.type === 'URL' || b.type === 'PHONE_NUMBER');
  const [inputs, setInputs] = useState({});
  const waba = wabaDbId || wabas.options[0]?.value || '';
  const m = useMutation({
    mutationFn: () =>
      templatesApi.applyLibraryTemplate({
        wabaDbId: Number(waba),
        libraryTemplateName: template.name,
        name,
        language,
        buttonInputs: urlButtons.map((b) => (b.type === 'URL' ? { type: 'URL', url: { baseUrl: inputs[b.i] || '' } } : { type: 'PHONE_NUMBER', phoneNumber: inputs[b.i] || '' })),
      }),
    onSuccess: (t) => {
      toast.success('Template created and sent for approval');
      qc.invalidateQueries({ queryKey: ['partner', 'templates'] });
      navigate(`/partner/templates/${t.id}`);
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });
  const nameOk = /^[a-z0-9_]{1,512}$/.test(name);
  const inputsOk = urlButtons.every((b) => (b.type === 'URL' ? /^https:\/\/\S+$/.test(inputs[b.i] || '') : /^\+?[1-9]\d{7,14}$/.test(inputs[b.i] || '')));
  return (
    <Modal
      open
      onClose={onClose}
      title="Use this template"
      size="lg"
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button onClick={() => m.mutate()} loading={m.isPending} disabled={!waba || !nameOk || !inputsOk}>Create template</Button>
        </>
      }
    >
      <div className="grid gap-4 sm:grid-cols-2">
        <Select className="sm:col-span-2" label="WhatsApp account" value={waba} onChange={(e) => setWaba(e.target.value)} options={wabas.options} />
        <Input label="Template name" value={name} onChange={(e) => setName(e.target.value.toLowerCase().replace(/[^a-z0-9_]/g, '_'))} error={nameOk ? undefined : 'Lowercase letters, numbers and _ only'} />
        <Select label="Language" value={language} onChange={(e) => setLanguage(e.target.value)} options={TEMPLATE_LANGUAGES} />
        {urlButtons.map((b) => (
          <Input
            key={b.i}
            className="sm:col-span-2"
            label={b.type === 'URL' ? `Website for "${b.text}"` : `Phone number for "${b.text}"`}
            placeholder={b.type === 'URL' ? 'https://yourwebsite.com' : '+919876543210'}
            value={inputs[b.i] || ''}
            onChange={(e) => setInputs({ ...inputs, [b.i]: e.target.value })}
          />
        ))}
      </div>
      <p className="mt-3 text-xs text-muted">Library templates are pre-written by Meta, so they are usually approved instantly.</p>
    </Modal>
  );
}

export default function TemplateLibraryPage() {
  const { user } = useAuth();
  const [filters, setFilters] = useState({ search: '', topic: '', language: '' });
  const [selected, setSelected] = useState(null);
  const q = useQuery({
    queryKey: ['partner', 'template-library', filters],
    queryFn: () => templatesApi.listTemplateLibrary(filters),
    placeholderData: (p) => p,
  });
  const list = Array.isArray(q.data) ? q.data : q.data?.content || [];
  return (
    <>
      <PageHeader title="Template library" subtitle="Ready-made utility templates from Meta. Pick one, adjust and submit." backTo="/partner/templates" backLabel="Templates" />
      <FilterBar>
        <SearchInput value={filters.search} onSearch={(v) => setFilters((f) => ({ ...f, search: v }))} placeholder="Search e.g. delivery, payment" />
        <Select aria-label="Topic" value={filters.topic} onChange={(e) => setFilters((f) => ({ ...f, topic: e.target.value }))} options={TOPICS.map((t) => ({ value: t, label: humanize(t) }))} placeholder="All topics" />
        <Select aria-label="Language" value={filters.language} onChange={(e) => setFilters((f) => ({ ...f, language: e.target.value }))} options={TEMPLATE_LANGUAGES} placeholder="Any language" />
      </FilterBar>
      {q.isError ? (
        <ErrorState error={q.error} onRetry={q.refetch} />
      ) : q.isLoading ? (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">{[1, 2, 3].map((i) => <Skeleton key={i} className="h-72 w-full" />)}</div>
      ) : !list.length ? (
        <div className="card"><EmptyState icon={BookOpen} title="No templates found" text="Try another search word." /></div>
      ) : (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {list.map((t) => (
            <div key={`${t.name}-${t.language}`} className="card flex flex-col p-4">
              <div className="mb-3 flex flex-wrap items-center gap-2">
                <p className="font-semibold text-ink">{humanize(t.name)}</p>
                <Badge tone="blue">{humanize(t.category)}</Badge>
                {t.topic && <Badge tone="gray">{humanize(t.topic)}</Badge>}
              </div>
              <WhatsAppPreview {...libraryPreview(t)} showHeader={false} businessName={user?.partner?.businessName} className="flex-1" />
              <Button className="mt-3" onClick={() => setSelected(t)}>Use template</Button>
            </div>
          ))}
        </div>
      )}
      {selected && <UseModal template={selected} onClose={() => setSelected(null)} />}
    </>
  );
}
