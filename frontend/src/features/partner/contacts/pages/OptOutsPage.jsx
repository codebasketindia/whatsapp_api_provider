import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ShieldOff } from 'lucide-react';
import toast from 'react-hot-toast';
import * as contactsApi from '../../../../api/endpoints/contacts';
import { getErrorMessage } from '../../../../api/axios';
import {
  Button, Card, DataTable, DateTime, EmptyState, ErrorState, FilterBar, InfoTip, PageHeader, SearchInput, Select, Skeleton, StatusBadge, Textarea,
} from '../../../../components/ui';
import { useUrlState } from '../../../../hooks/useUrlState';
import { formatPhone, humanize } from '../../../../lib/format';
import ContactsSubnav from '../components/ContactsSubnav';
import TagInput from '../../common/TagInput';

const SOURCES = ['KEYWORD', 'MANUAL', 'API', 'IMPORT', 'META_131050'];
const SOURCE_LABEL = { KEYWORD: 'Replied with a stop word', MANUAL: 'Added by you', API: 'Via API', IMPORT: 'From import', META_131050: 'Stopped by WhatsApp' };

function KeywordSettings() {
  const qc = useQueryClient();
  const q = useQuery({ queryKey: ['partner', 'opt-out-settings'], queryFn: contactsApi.getOptOutSettings });
  const [form, setForm] = useState(null);
  useEffect(() => {
    if (q.data) setForm({ optOutKeywords: q.data.optOutKeywords || [], optInKeywords: q.data.optInKeywords || [], autoReply: q.data.autoReply || '' });
  }, [q.data]);
  const m = useMutation({
    mutationFn: () => contactsApi.updateOptOutSettings(form),
    onSuccess: (d) => {
      qc.setQueryData(['partner', 'opt-out-settings'], d);
      toast.success('Saved');
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });
  if (q.isError) return <ErrorState error={q.error} onRetry={q.refetch} />;
  if (!form) return <Skeleton className="h-48 w-full" />;
  const upper = (s) => s.toUpperCase().slice(0, 32);
  return (
    <Card title={<span className="inline-flex items-center gap-1">Stop words <InfoTip term="OPT_OUT" /></span>} subtitle="When a customer sends exactly one of these words, they stop getting marketing messages">
      <div className="space-y-4">
        <TagInput label="Stop words" value={form.optOutKeywords} onChange={(v) => setForm({ ...form, optOutKeywords: v })} transform={upper} hint="Example: STOP, UNSUBSCRIBE, BAND KARO" />
        <TagInput label="Start again words" value={form.optInKeywords} onChange={(v) => setForm({ ...form, optInKeywords: v })} transform={upper} hint="Example: START" />
        <Textarea
          label="Automatic reply after a stop word (optional)"
          rows={2}
          maxLength={1024}
          value={form.autoReply}
          onChange={(e) => setForm({ ...form, autoReply: e.target.value })}
          hint="Example: You will not get offers from us anymore. Reply START to subscribe again."
        />
        <div className="flex justify-end">
          <Button onClick={() => m.mutate()} loading={m.isPending} disabled={!form.optOutKeywords.length}>Save</Button>
        </div>
      </div>
    </Card>
  );
}

export default function OptOutsPage() {
  const [f, update] = useUrlState({ page: 0, search: '', source: '' });
  const params = { page: f.page, size: 20, search: f.search, source: f.source };
  const q = useQuery({ queryKey: ['partner', 'opt-outs', params], queryFn: () => contactsApi.listOptOuts(params), placeholderData: (p) => p });
  const columns = [
    { key: 'phone', header: 'Number', primary: true, render: (r) => formatPhone(r.phone) },
    { key: 'source', header: 'How', render: (r) => <StatusBadge status="OPTED_OUT" label={SOURCE_LABEL[r.source] || humanize(r.source)} /> },
    { key: 'keyword', header: 'Word', render: (r) => r.keyword || '—' },
    { key: 'createdAt', header: 'Date', render: (r) => <DateTime value={r.createdAt} /> },
  ];
  return (
    <>
      <PageHeader title="Opt-outs" subtitle="People who asked not to receive marketing messages" />
      <ContactsSubnav />
      <div className="grid grid-cols-1 gap-5 xl:grid-cols-[minmax(0,1fr)_420px]">
        <div>
          <FilterBar>
            <SearchInput value={f.search} onSearch={(v) => update({ search: v })} placeholder="Search number" />
            <Select aria-label="How" value={f.source} onChange={(e) => update({ source: e.target.value })} options={SOURCES.map((s) => ({ value: s, label: SOURCE_LABEL[s] }))} placeholder="All" />
          </FilterBar>
          <DataTable
            columns={columns}
            page={q.data}
            loading={q.isLoading}
            error={q.error}
            onRetry={q.refetch}
            onPageChange={(p) => update({ page: p })}
            rowKey={(r) => r.phone}
            empty={<EmptyState compact icon={ShieldOff} title="Nobody has opted out" />}
          />
        </div>
        <KeywordSettings />
      </div>
    </>
  );
}
