import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Check, ClipboardCheck, X } from 'lucide-react';
import * as templatesApi from '../../../../api/endpoints/templates';
import { humanize } from '../../../../lib/format';
import { Badge, Button, ConfirmDialog, DateTime, EmptyState, ErrorState, PageHeader, Pagination, Skeleton, Textarea } from '../../../../components/ui';
import ReasonDialog from '../../shared/ReasonDialog';
import { useApiMutation } from '../../shared/useApiMutation';
import TemplatePreviewCard from '../components/TemplatePreviewCard';

export default function TemplateReviewPage() {
  const [page, setPage] = useState(0);
  const [action, setAction] = useState(null);
  const [note, setNote] = useState('');
  const q = useQuery({ queryKey: ['admin', 'templates', 'review', page], queryFn: () => templatesApi.listTemplateReviewQueue({ page, size: 10 }), placeholderData: (p) => p });
  const inv = [['admin', 'templates']];
  const approveM = useApiMutation({
    mutationFn: (t) => templatesApi.approveTemplate(t.id, note.trim() || undefined),
    success: 'Approved and submitted to Meta. Meta usually decides within minutes.',
    invalidate: inv,
    onSuccess: () => { setAction(null); setNote(''); },
  });
  const rejectM = useApiMutation({ mutationFn: ({ t, reason }) => templatesApi.rejectTemplate(t.id, reason), success: 'Template returned to the partner', invalidate: inv, onSuccess: () => setAction(null) });
  const list = q.data?.content || [];
  return (
    <>
      <PageHeader title="Template review" subtitle="Templates partners want to use on our platform numbers. Check them before they go to Meta." backTo="/admin/templates" backLabel="Templates" />
      {q.isLoading ? (
        <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">{[1, 2].map((i) => <Skeleton key={i} className="h-80" />)}</div>
      ) : q.isError ? (
        <ErrorState error={q.error} onRetry={q.refetch} />
      ) : list.length === 0 ? (
        <div className="card"><EmptyState icon={ClipboardCheck} title="Nothing to review" text="New templates on platform numbers will wait here for your approval." /></div>
      ) : (
        <>
          <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
            {list.map((t) => (
              <div key={t.id} className="card flex flex-col p-4">
                <div className="mb-3 flex flex-wrap items-start justify-between gap-2">
                  <div>
                    <h3 className="font-semibold text-ink">{t.name}</h3>
                    <p className="text-sm text-muted">{t.partner?.businessName || 'Platform'} · {t.language} · <DateTime value={t.createdAt} mode="relative" /></p>
                  </div>
                  <Badge tone="blue">{humanize(t.category)}</Badge>
                </div>
                <TemplatePreviewCard template={t} businessName={t.partner?.businessName} />
                <ul className="mt-3 list-disc space-y-1 pl-5 text-xs text-muted">
                  <li>{t.category === 'MARKETING' ? 'Marketing: offers or promotions are fine; must allow opt-out.' : t.category === 'UTILITY' ? 'Utility: must be about an existing order/account, no promotion.' : 'Authentication: only a one-time code.'}</li>
                  <li>No misleading claims, no prohibited products, no harassment.</li>
                </ul>
                <div className="mt-auto flex gap-2 pt-4">
                  <Button className="flex-1" leftIcon={Check} onClick={() => setAction({ type: 'approve', t })}>Approve & submit</Button>
                  <Button className="flex-1" variant="danger-outline" leftIcon={X} onClick={() => setAction({ type: 'reject', t })}>Reject</Button>
                </div>
              </div>
            ))}
          </div>
          <Pagination page={q.data?.page} totalPages={q.data?.totalPages} totalElements={q.data?.totalElements} size={q.data?.size} onPageChange={setPage} className="mt-4" />
        </>
      )}
      <ConfirmDialog
        open={action?.type === 'approve'}
        onClose={() => setAction(null)}
        title={`Submit "${action?.t?.name}" to Meta?`}
        message="Meta reviews it next. The partner is notified when Meta approves or rejects it."
        confirmLabel="Approve & submit"
        loading={approveM.isPending}
        onConfirm={() => approveM.mutate(action.t)}
      >
        <Textarea label="Note (optional)" rows={2} maxLength={500} value={note} onChange={(e) => setNote(e.target.value)} />
      </ConfirmDialog>
      <ReasonDialog
        open={action?.type === 'reject'}
        onClose={() => setAction(null)}
        danger
        title={`Reject "${action?.t?.name}"?`}
        message="The partner sees your reason and can edit the template."
        confirmLabel="Reject"
        placeholder="e.g. Utility template contains a promotional offer"
        loading={rejectM.isPending}
        onConfirm={(reason) => rejectM.mutate({ t: action.t, reason })}
      />
    </>
  );
}
