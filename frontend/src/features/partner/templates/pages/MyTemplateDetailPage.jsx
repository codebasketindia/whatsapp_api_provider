import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate, useParams } from 'react-router-dom';
import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { Pencil, Send, Trash2, Upload } from 'lucide-react';
import toast from 'react-hot-toast';
import * as templatesApi from '../../../../api/endpoints/templates';
import { getErrorMessage } from '../../../../api/axios';
import { useAuth } from '../../../../auth/useAuth';
import {
  Alert, Badge, Button, Card, ConfirmDialog, DateRangePicker, EmptyState, ErrorState, KeyValue, PageHeader, PageLoader, Skeleton,
  StatCard, StatusBadge, WhatsAppPreview,
} from '../../../../components/ui';
import { templateToPreview } from '../../../../components/whatsapp/TemplateBuilder';
import { addDaysToDateParam, formatDateTime, formatNumber, humanize, toIstDateParam } from '../../../../lib/format';

const STATUS_HELP = {
  DRAFT: 'Not submitted yet.',
  PENDING_REVIEW: 'Our team is checking it before sending it to Meta.',
  INTERNAL_REJECTED: 'Our team asked for changes. Edit and submit again.',
  PENDING: 'Meta is reviewing it. This usually takes a few minutes, sometimes up to 24 hours.',
  APPROVED: 'Ready to use.',
  REJECTED: 'Meta did not approve it. Edit it to fix the reason below and submit again.',
  PAUSED: 'Meta paused it because customers reported or blocked it. It may come back automatically.',
  DISABLED: 'Meta disabled it after low quality. Create a new template.',
};

export default function MyTemplateDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const qc = useQueryClient();
  const { user } = useAuth();
  const [delOpen, setDelOpen] = useState(false);
  const [range, setRange] = useState(() => {
    const to = toIstDateParam();
    return { from: addDaysToDateParam(to, -29), to };
  });
  const q = useQuery({ queryKey: ['partner', 'templates', 'detail', id], queryFn: () => templatesApi.getMyTemplate(id) });
  const approved = q.data?.status === 'APPROVED';
  const a = useQuery({
    queryKey: ['partner', 'templates', 'analytics', id, range],
    queryFn: () => templatesApi.getMyTemplateAnalytics(id, range),
    enabled: Boolean(q.data) && q.data.status !== 'DRAFT',
    placeholderData: (p) => p,
  });
  const submit = useMutation({
    mutationFn: () => templatesApi.submitMyTemplate(id),
    onSuccess: () => {
      toast.success('Submitted for approval');
      qc.invalidateQueries({ queryKey: ['partner', 'templates'] });
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });
  const del = useMutation({
    mutationFn: () => templatesApi.deleteMyTemplate(id),
    onSuccess: () => {
      toast.success('Template deleted');
      qc.invalidateQueries({ queryKey: ['partner', 'templates'] });
      navigate('/partner/templates');
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });
  const preview = useMemo(() => (q.data ? templateToPreview(q.data) : null), [q.data]);

  if (q.isLoading) return <PageLoader />;
  if (q.isError) return <ErrorState error={q.error} onRetry={q.refetch} />;
  const t = q.data;
  const totals = a.data?.totals || {};
  const canEdit = ['DRAFT', 'APPROVED', 'REJECTED', 'PAUSED', 'INTERNAL_REJECTED'].includes(t.status);
  const canSubmit = ['DRAFT', 'INTERNAL_REJECTED'].includes(t.status);
  return (
    <>
      <PageHeader
        title={t.name}
        subtitle={`${humanize(t.category)} · ${t.language}`}
        backTo="/partner/templates"
        backLabel="Templates"
        actions={
          <>
            {approved && <Button leftIcon={Send} to={`/partner/send?templateId=${t.id}`}>Send</Button>}
            {canSubmit && <Button leftIcon={Upload} onClick={() => submit.mutate()} loading={submit.isPending}>Submit for approval</Button>}
            {canEdit && <Button variant="secondary" leftIcon={Pencil} to={`/partner/templates/${t.id}/edit`}>Edit</Button>}
            <Button variant="danger-outline" leftIcon={Trash2} onClick={() => setDelOpen(true)}>Delete</Button>
          </>
        }
      />
      <div className="grid grid-cols-1 gap-5 lg:grid-cols-[minmax(0,1fr)_360px]">
        <div className="space-y-5">
          <Card>
            <div className="flex flex-wrap items-center gap-2">
              <StatusBadge status={t.status} />
              {t.qualityScore && t.qualityScore !== 'UNKNOWN' && <Badge tone={{ GREEN: 'green', YELLOW: 'amber', RED: 'red' }[t.qualityScore] || 'gray'} dot>Quality: {humanize(t.qualityScore)}</Badge>}
            </div>
            <p className="mt-2 text-sm text-muted">{STATUS_HELP[t.status]}</p>
            {t.rejectedReason && (
              <Alert variant="error" className="mt-3" title="Reason">{t.rejectedReason}</Alert>
            )}
            {t.adminReviewNote && t.status !== 'INTERNAL_REJECTED' && <Alert variant="info" className="mt-3" title="Note from our team">{t.adminReviewNote}</Alert>}
            {t.previousCategory && (
              <Alert variant="warning" className="mt-3">Meta changed the category from {humanize(t.previousCategory)} to {humanize(t.category)}. This can change the price.</Alert>
            )}
            <KeyValue
              className="mt-4"
              columns={3}
              items={[
                { label: 'WhatsApp account', value: t.waba?.wabaId },
                { label: 'Variables', value: humanize(t.parameterFormat) },
                { label: 'Created', value: formatDateTime(t.createdAt) },
                { label: 'Submitted', value: formatDateTime(t.submittedAt) },
                { label: 'Last update from Meta', value: formatDateTime(t.lastSyncedAt) },
                { label: 'Meta template ID', value: t.metaTemplateId || '—' },
              ]}
            />
          </Card>
          {t.status !== 'DRAFT' && (
            <Card title="Performance">
              <DateRangePicker value={range} onChange={setRange} className="mb-4" />
              <div className="mb-4 grid grid-cols-2 gap-3 sm:grid-cols-4">
                <StatCard label="Sent" value={formatNumber(totals.sent)} loading={a.isLoading} />
                <StatCard label="Delivered" tone="green" value={formatNumber(totals.delivered)} loading={a.isLoading} />
                <StatCard label="Read" tone="blue" value={formatNumber(totals.read)} loading={a.isLoading} />
                <StatCard label="Button clicks" tone="amber" value={formatNumber(totals.clicked)} loading={a.isLoading} />
              </div>
              {a.isError ? (
                <ErrorState error={a.error} onRetry={a.refetch} />
              ) : a.isLoading ? (
                <Skeleton className="h-56 w-full" />
              ) : !a.data?.daily?.length ? (
                <EmptyState compact title="No data for these dates" />
              ) : (
                <div className="h-56">
                  <ResponsiveContainer width="100%" height="100%">
                    <BarChart data={a.data.daily} margin={{ left: -20, right: 8 }}>
                      <CartesianGrid strokeDasharray="3 3" stroke="rgb(var(--line))" />
                      <XAxis dataKey="date" fontSize={12} tickFormatter={(v) => String(v).slice(5)} />
                      <YAxis allowDecimals={false} fontSize={12} />
                      <Tooltip />
                      <Legend />
                      <Bar dataKey="sent" name="Sent" fill="#94a3b8" />
                      <Bar dataKey="delivered" name="Delivered" fill="#25D366" />
                      <Bar dataKey="read" name="Read" fill="#0ea5e9" />
                    </BarChart>
                  </ResponsiveContainer>
                </div>
              )}
              {a.data?.buttons?.length > 0 && (
                <ul className="mt-4 divide-y divide-line text-sm">
                  {a.data.buttons.map((b, i) => (
                    <li key={i} className="flex justify-between py-2"><span>{b.text || b.button || `Button ${i + 1}`}</span><span className="font-medium">{formatNumber(b.clicked ?? b.count)}</span></li>
                  ))}
                </ul>
              )}
            </Card>
          )}
        </div>
        <div className="lg:sticky lg:top-4 lg:self-start">
          <p className="mb-2 text-sm font-medium text-muted">Preview</p>
          <WhatsAppPreview {...preview} businessName={user?.partner?.businessName || 'Your business'} />
        </div>
      </div>
      <ConfirmDialog
        open={delOpen}
        onClose={() => setDelOpen(false)}
        onConfirm={() => del.mutate()}
        loading={del.isPending}
        danger
        title="Delete this template?"
        message="It is deleted in Meta in every language. Campaigns using it will stop. You cannot reuse the same name for 30 days."
        confirmWord="DELETE"
        confirmLabel="Delete template"
      />
    </>
  );
}
