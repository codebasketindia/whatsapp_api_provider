import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { CalendarClock, FileText, Film, Image, MessageSquareText, MoreVertical, Music, Plus, File } from 'lucide-react';
import toast from 'react-hot-toast';
import * as scheduledApi from '../../../../api/endpoints/scheduled';
import { getErrorMessage } from '../../../../api/axios';
import {
  Button, ConfirmDialog, DataTable, DateRangePicker, DropdownMenu, EmptyState, FilterBar, IconButton, PageHeader, SearchInput, StatusBadge, Tabs,
} from '../../../../components/ui';
import { useUrlState } from '../../../../hooks/useUrlState';
import { formatDateTime, formatPhone, formatRelative } from '../../../../lib/format';
import { PAGE_SIZE } from '../../../../lib/constants';
import SenderSelect from '../../common/SenderSelect';
import { useWindowStatus } from '../../common/useWindowStatus';
import ScheduledMessageForm, { WindowNotice } from '../components/ScheduledMessageForm';
import ScheduleTimeDialog from '../components/ScheduleTimeDialog';
import { SCHEDULED_STATUS_LABEL } from '../components/scheduleTime';

const TABS = [
  { value: 'SCHEDULED', label: 'Upcoming' },
  { value: 'SENT', label: 'Sent' },
  { value: 'FAILED', label: 'Failed' },
  { value: 'CANCELLED', label: 'Cancelled' },
  { value: 'ALL', label: 'All' },
];

const TYPE_ICON = { text: MessageSquareText, template: FileText, image: Image, video: Film, audio: Music, document: File };

function TypeIcon({ type }) {
  const Icon = TYPE_ICON[String(type || '').toLowerCase()] || MessageSquareText;
  return <Icon className="h-4 w-4 shrink-0 text-muted" aria-hidden />;
}

function RescheduleDialog({ item, onClose }) {
  const qc = useQueryClient();
  const freeForm = item && String(item.type).toLowerCase() !== 'template';
  const win = useWindowStatus(item?.sender?.id, item?.to || '');
  const m = useMutation({
    mutationFn: (iso) => scheduledApi.rescheduleScheduled(item.uid, iso),
    onSuccess: (res) => {
      toast.success(`Moved to ${formatDateTime(res?.scheduledAt)}`);
      qc.invalidateQueries({ queryKey: ['partner', 'scheduled'] });
      onClose();
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });
  return (
    <ScheduleTimeDialog
      open={Boolean(item)}
      onClose={onClose}
      title="Change time"
      description={item ? `Message to ${formatPhone(item.to)}` : undefined}
      initialIso={item?.scheduledAt}
      submitLabel="Save new time"
      loading={m.isPending}
      onSubmit={(iso) => m.mutate(iso)}
      notice={(iso) => <WindowNotice win={win} iso={iso} freeForm={freeForm} />}
    />
  );
}

export default function ScheduledPage() {
  const qc = useQueryClient();
  const [f, update] = useUrlState({ tab: 'SCHEDULED', senderId: '', search: '', from: '', to: '', page: 0 });
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState(null);
  const [rescheduling, setRescheduling] = useState(null);
  const [confirm, setConfirm] = useState(null); // { kind: 'cancel'|'send', item }

  const params = {
    status: f.tab === 'ALL' ? undefined : f.tab,
    senderId: f.senderId,
    waId: f.search.replace(/\D/g, '') || undefined,
    from: f.from,
    to: f.to,
    page: f.page,
    size: PAGE_SIZE,
  };
  const q = useQuery({
    queryKey: ['partner', 'scheduled', params],
    queryFn: () => scheduledApi.listScheduled(params),
    placeholderData: (p) => p,
    refetchInterval: f.tab === 'SCHEDULED' ? 30_000 : false,
  });

  const act = useMutation({
    mutationFn: ({ kind, item }) => (kind === 'cancel' ? scheduledApi.cancelScheduled(item.uid) : scheduledApi.sendScheduledNow(item.uid)),
    onSuccess: (_, { kind }) => {
      toast.success(kind === 'cancel' ? 'Scheduled message cancelled' : 'Sending now');
      setConfirm(null);
      qc.invalidateQueries({ queryKey: ['partner', 'scheduled'] });
      qc.invalidateQueries({ queryKey: ['partner', 'wallet'] });
    },
    onError: (e) => {
      setConfirm(null);
      toast.error(getErrorMessage(e));
    },
  });

  const actions = (r) =>
    r.status === 'SCHEDULED' ? (
      <DropdownMenu
        trigger={({ toggle }) => <IconButton icon={MoreVertical} label="Actions" onClick={toggle} />}
        items={[
          { label: 'Edit', onClick: () => { setEditing(r); setFormOpen(true); } },
          { label: 'Change time', onClick: () => setRescheduling(r) },
          { label: 'Send now', onClick: () => setConfirm({ kind: 'send', item: r }) },
          { label: 'Cancel', danger: true, onClick: () => setConfirm({ kind: 'cancel', item: r }) },
        ]}
      />
    ) : null;

  const columns = [
    {
      key: 'scheduledAt',
      header: 'When',
      primary: true,
      render: (r) => (
        <span>
          <span className="block font-medium text-ink">{formatDateTime(r.scheduledAt)}</span>
          <span className="text-xs text-muted">{formatRelative(r.scheduledAt)}</span>
        </span>
      ),
    },
    {
      key: 'to',
      header: 'To',
      render: (r) => (
        <span>
          <span className="block">{r.contactName || formatPhone(r.to)}</span>
          <span className="text-xs text-muted">{r.contactName ? `${formatPhone(r.to)} · ` : ''}from {r.sender?.displayPhoneNumber}</span>
        </span>
      ),
    },
    {
      key: 'preview',
      header: 'Message',
      render: (r) => (
        <span className="flex max-w-xs items-start gap-1.5">
          <TypeIcon type={r.type} />
          <span className="line-clamp-2 text-sm">{r.preview || '—'}</span>
        </span>
      ),
    },
    {
      key: 'status',
      header: 'Status',
      render: (r) => (
        <span>
          <StatusBadge status={r.status} label={SCHEDULED_STATUS_LABEL[r.status]} />
          {r.status === 'SCHEDULED' && r.windowOpenAtSchedule === false && <span className="mt-1 block text-xs text-amber-700 dark:text-amber-400">Chat window may be closed</span>}
          {r.error && <span className="mt-1 block text-xs text-red-600">{r.error}</span>}
        </span>
      ),
    },
    { key: 'actions', header: '', align: 'right', render: actions },
  ];

  return (
    <>
      <PageHeader
        title="Scheduled messages"
        subtitle="Messages, photos and videos that go out automatically at a time you choose."
        actions={<Button leftIcon={Plus} onClick={() => { setEditing(null); setFormOpen(true); }}>New scheduled message</Button>}
      />
      <Tabs tabs={TABS} value={f.tab} onChange={(v) => update({ tab: v })} className="mb-4" />
      <FilterBar className="mb-4">
        <SenderSelect includeAll label="Number" value={f.senderId ? Number(f.senderId) : ''} onChange={(v) => update({ senderId: v })} />
        <DateRangePicker label="Date" value={{ from: f.from, to: f.to }} onChange={(r) => update({ from: r.from, to: r.to })} allowClear />
        <SearchInput value={f.search} onSearch={(v) => update({ search: v })} placeholder="Search by number" />
      </FilterBar>
      <DataTable
        columns={columns}
        page={q.data}
        loading={q.isLoading}
        error={q.error}
        onRetry={q.refetch}
        rowKey={(r) => r.uid}
        onPageChange={(p) => update({ page: p })}
        empty={
          <EmptyState
            icon={CalendarClock}
            title={f.tab === 'SCHEDULED' ? 'Nothing scheduled' : 'No messages here'}
            text="Schedule a template, text, photo, video or document to go out later — for example a festival offer or a payment reminder."
            action={<Button size="sm" leftIcon={Plus} onClick={() => { setEditing(null); setFormOpen(true); }}>New scheduled message</Button>}
          />
        }
      />

      <ScheduledMessageForm open={formOpen} onClose={() => { setFormOpen(false); setEditing(null); }} item={editing} />
      <RescheduleDialog item={rescheduling} onClose={() => setRescheduling(null)} />
      <ConfirmDialog
        open={Boolean(confirm)}
        onClose={() => setConfirm(null)}
        onConfirm={() => act.mutate(confirm)}
        loading={act.isPending}
        danger={confirm?.kind === 'cancel'}
        title={confirm?.kind === 'cancel' ? 'Cancel this scheduled message?' : 'Send this message now?'}
        confirmLabel={confirm?.kind === 'cancel' ? 'Cancel message' : 'Send now'}
        cancelLabel="Keep"
        message={
          confirm?.kind === 'cancel'
            ? 'It will not be sent. You can schedule a new one any time.'
            : <>It goes to <strong>{formatPhone(confirm?.item?.to)}</strong> right away. The price is taken from your wallet now.</>
        }
      />
    </>
  );
}
