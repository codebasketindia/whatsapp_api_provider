import { useState } from 'react';
import { useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import toast from 'react-hot-toast';
import { BadgeIndianRupee, Download, ExternalLink, Image, Megaphone, MoreVertical, Pencil, RefreshCw, Send, Share2 } from 'lucide-react';
import * as regApi from '../../../../api/endpoints/registrations';
import { getErrorMessage } from '../../../../api/axios';
import {
  Badge, Button, Card, CopyButton, DataTable, DateTime, DropdownMenu, IconButton, Input, Modal, PageHeader, QueryState, SearchInput, Select, StatCard, StatusBadge,
} from '../../../../components/ui';
import { formatMoney, formatNumber, formatPhone } from '../../../../lib/format';
import { istLocalToIso, nowIstLocal } from '../../campaigns/components/schedule';
import MessageBuilder from '../components/MessageBuilder';
import { PASS_STATUS_LABEL, PAYMENT_STATUS_LABEL } from '../components/regHelpers';

function BroadcastModal({ form, open, onClose }) {
  const [at, setAt] = useState(nowIstLocal(10));
  const [message, setMessage] = useState(null);
  const send = useMutation({
    mutationFn: () => regApi.broadcast(form.id, { scheduledAt: istLocalToIso(at), message }),
    onSuccess: (r) => {
      toast.success(`Scheduled for ${formatNumber(r.scheduled)} people${r.skipped ? ` (${r.skipped} skipped)` : ''}`);
      onClose();
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });
  return (
    <Modal open={open} onClose={onClose} size="xl" title="Message everyone registered"
           description={`Schedules one message for all ${formatNumber(form.registrationsCount)} people on this link. Money is taken when each one is sent.`}
           footer={<><Button variant="secondary" onClick={onClose}>Cancel</Button><Button loading={send.isPending} disabled={!message} onClick={() => send.mutate()}>Schedule</Button></>}>
      <div className="space-y-4">
        <Input type="datetime-local" label="Send on (India time)" min={nowIstLocal(1)} value={at} onChange={(e) => setAt(e.target.value)} />
        <MessageBuilder senderId={form.sender?.id} onChange={setMessage} />
      </div>
    </Modal>
  );
}

function PassModal({ row, onClose }) {
  if (!row) return null;
  const src = regApi.publicUrl(row.passUrl);
  return (
    <Modal open onClose={onClose} title={`Pass ${row.passNo}`} size="md"
           footer={<Button leftIcon={Download} href={`${src}?download=true`}>Download</Button>}>
      <img src={src} alt={`Pass ${row.passNo}`} className="mx-auto w-full max-w-sm rounded-lg border border-line" />
    </Modal>
  );
}

/** RF3 + RG1: one registration link with its registrations. */
export default function RegistrationDetailPage() {
  const { id } = useParams();
  const qc = useQueryClient();
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);
  const [broadcast, setBroadcast] = useState(false);
  const [viewing, setViewing] = useState(null);
  const form = useQuery({ queryKey: ['partner', 'registration-forms', id], queryFn: () => regApi.getForm(id) });
  const regs = useQuery({
    queryKey: ['partner', 'registrations', id, search, status, page],
    queryFn: () => regApi.listRegistrations({ formId: id, search, passStatus: status, page, size: 20 }),
    placeholderData: (p) => p,
  });
  const refresh = () => qc.invalidateQueries({ queryKey: ['partner', 'registrations', id] });
  const resend = useMutation({
    mutationFn: (r) => regApi.resendPass(r.uid),
    onSuccess: (res) => {
      toast(res.message, { icon: res.data?.passStatus === 'SENT' ? '✅' : '⚠️' });
      refresh();
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });
  const check = useMutation({
    mutationFn: (r) => regApi.checkPayment(r.uid),
    onSuccess: (res) => {
      toast(res.message, { icon: res.data?.paymentStatus === 'PAID' ? '✅' : 'ℹ️' });
      refresh();
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });
  const regen = useMutation({
    mutationFn: (r) => regApi.regeneratePass(r.uid),
    onSuccess: () => {
      toast.success('Pass created again with the current design');
      refresh();
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });

  return (
    <QueryState query={form}>
      {(f) => (
        <>
          <PageHeader
            title={f.title}
            backTo="/partner/registrations"
            actions={
              <>
                <Button variant="secondary" leftIcon={Pencil} to={`/partner/registrations/${f.id}/edit`}>Edit</Button>
                <Button variant="secondary" leftIcon={Megaphone} disabled={!f.registrationsCount} onClick={() => setBroadcast(true)}>Message all</Button>
              </>
            }
          />
          <div className="mb-5 grid grid-cols-1 gap-4 md:grid-cols-[minmax(0,2fr)_minmax(0,1fr)]">
            <Card title="Share this link">
              <div className="flex min-w-0 items-center gap-2 rounded-lg bg-surface-2 px-3 py-2 text-sm">
                <span className="min-w-0 flex-1 truncate font-medium">{f.publicUrl}</span>
                <CopyButton value={f.publicUrl} toastMessage="Link copied" />
              </div>
              <div className="mt-3 flex flex-wrap gap-2">
                <Button size="sm" variant="secondary" leftIcon={ExternalLink} href={f.publicUrl}>Open</Button>
                <Button size="sm" variant="secondary" leftIcon={Share2} href={`https://wa.me/?text=${encodeURIComponent(`${f.title}\nRegister here: ${f.publicUrl}`)}`}>Share on WhatsApp</Button>
                <Button size="sm" variant="secondary" leftIcon={Download} onClick={() => regApi.exportRegistrations(f.id).catch((e) => toast.error(getErrorMessage(e)))}>Export CSV</Button>
              </div>
              <p className="mt-3 text-xs text-muted">
                {f.passEnabled ? (f.passTemplateId ? 'Passes are sent instantly with your approved template.' : 'No pass template: the pass is sent when the person taps “Get my pass on WhatsApp”.') : 'Passes are switched off.'}
                {f.feeAmount ? ` Fee ${formatMoney(f.feeAmount)} — the pass is created only after payment.` : ''}
                {f.followUps?.length ? ` ${f.followUps.length} follow-up message(s) are scheduled for each person.` : ''}
              </p>
            </Card>
            <StatCard label="Registered" value={formatNumber(f.registrationsCount)} hint={f.maxRegistrations ? `Limit ${formatNumber(f.maxRegistrations)}` : 'No limit'} />
          </div>
          <div className="mb-3 flex flex-col gap-3 sm:flex-row">
            <SearchInput className="flex-1" value={search} onSearch={(s) => { setSearch(s); setPage(0); }} placeholder="Search name, mobile or pass no." />
            <Select className="sm:w-60" value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }}
                    options={Object.entries(PASS_STATUS_LABEL).map(([value, label]) => ({ value, label }))} placeholder="All pass statuses" />
          </div>
          <DataTable
            page={regs.data}
            loading={regs.isLoading}
            error={regs.error}
            onRetry={regs.refetch}
            onPageChange={setPage}
            rowKey={(r) => r.uid}
            empty={{ title: 'No registrations yet', text: 'Share the link to start.' }}
            columns={[
              { key: 'passNo', header: 'Pass no.', primary: true, render: (r) => <span className="whitespace-nowrap font-mono text-sm">{r.passNo || '—'}</span> },
              { key: 'name', header: 'Name' },
              { key: 'phone', header: 'WhatsApp', render: (r) => formatPhone(r.phone) },
              { key: 'passStatus', header: 'Pass', render: (r) => (
                <span title={r.passError || undefined}><StatusBadge status={r.passStatus} label={PASS_STATUS_LABEL[r.passStatus]} /></span>
              ) },
              ...(f.feeAmount || regs.data?.content?.some((r) => r.paymentStatus !== 'NOT_REQUIRED') ? [{
                key: 'payment', header: 'Payment', render: (r) => (
                  <span title={r.paymentId || undefined} className="whitespace-nowrap">
                    <StatusBadge status={r.paymentStatus === 'PAID' ? 'PAID' : r.paymentStatus} label={`${PAYMENT_STATUS_LABEL[r.paymentStatus]}${r.amount ? ` · ${formatMoney(r.amount)}` : ''}`} />
                  </span>
                ),
              }] : []),
              { key: 'createdAt', header: 'Registered', hideOnMobile: true, render: (r) => <DateTime value={r.createdAt} /> },
              { key: 'actions', header: '', align: 'right', render: (r) => (
                <DropdownMenu
                  trigger={({ toggle }) => <IconButton icon={MoreVertical} label={`Actions for ${r.name}`} onClick={toggle} />}
                  items={[
                    (r.paymentStatus === 'PENDING' || r.paymentStatus === 'FAILED') && { label: 'Check payment', icon: BadgeIndianRupee, onClick: () => check.mutate(r) },
                    r.passUrl && { label: 'View pass', icon: Image, onClick: () => setViewing(r) },
                    r.passUrl && { label: 'Send pass again', icon: Send, onClick: () => resend.mutate(r) },
                    r.passNo && { label: 'Create pass again', icon: RefreshCw, onClick: () => regen.mutate(r) },
                  ].filter(Boolean)}
                />
              ) },
            ]}
          />
          {f.status !== 'ACTIVE' && <p className="mt-3 text-sm text-muted"><Badge tone="yellow">Not open</Badge> New people cannot register right now.</p>}
          {broadcast && <BroadcastModal form={f} open onClose={() => setBroadcast(false)} />}
          <PassModal row={viewing} onClose={() => setViewing(null)} />
        </>
      )}
    </QueryState>
  );
}
