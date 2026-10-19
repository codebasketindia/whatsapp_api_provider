import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { ClipboardCheck, FileText, Plus, RefreshCw, Trash2 } from 'lucide-react';
import * as templatesApi from '../../../../api/endpoints/templates';
import { useAuth } from '../../../../auth/useAuth';
import { useUrlState } from '../../../../hooks/useUrlState';
import { TEMPLATE_CATEGORIES } from '../../../../lib/constants';
import { humanize } from '../../../../lib/format';
import QualityBadge from '../../../../components/whatsapp/QualityBadge';
import {
  Alert, Badge, Button, ConfirmDialog, DataTable, DateTime, Drawer, EmptyState, FilterBar, KeyValue, Modal, PageHeader, SearchInput, Select, StatusBadge,
} from '../../../../components/ui';
import { useApiMutation } from '../../shared/useApiMutation';
import TemplatePreviewCard from '../components/TemplatePreviewCard';
import { useAdminWabaOptions } from '../components/useAdminWabaOptions';

export const TEMPLATE_STATUSES = ['DRAFT', 'PENDING_REVIEW', 'INTERNAL_REJECTED', 'PENDING', 'APPROVED', 'REJECTED', 'PAUSED', 'DISABLED', 'IN_APPEAL', 'DELETED'];

export default function TemplatesListPage() {
  const { hasPermission } = useAuth();
  const canReview = hasPermission('TEMPLATE_REVIEW');
  const [state, update] = useUrlState({ page: 0, search: '', status: '', category: '', wabaId: '', partnerId: '' });
  const [selected, setSelected] = useState(null);
  const [del, setDel] = useState(null);
  const [syncOpen, setSyncOpen] = useState(false);
  const wabas = useAdminWabaOptions();
  const q = useQuery({ queryKey: ['admin', 'templates', state], queryFn: () => templatesApi.listTemplates({ ...state, size: 20 }), placeholderData: (p) => p });
  const delM = useApiMutation({
    mutationFn: (t) => templatesApi.deleteTemplateAdmin(t.id),
    success: 'Template deleted on WhatsApp',
    invalidate: [['admin', 'templates']],
    onSuccess: () => { setDel(null); setSelected(null); },
  });
  const columns = [
    {
      key: 'name',
      header: 'Template',
      primary: true,
      render: (t) => (
        <span>
          <span className="block font-medium text-ink">{t.name}</span>
          <span className="block text-xs text-muted">{t.language} · {humanize(t.category)}</span>
        </span>
      ),
    },
    { key: 'partner', header: 'Partner', render: (t) => t.partner?.businessName || <Badge tone="brand">Platform</Badge> },
    { key: 'status', header: 'Status', render: (t) => <StatusBadge status={t.status} /> },
    { key: 'quality', header: 'Quality', hideOnMobile: true, render: (t) => <QualityBadge rating={t.qualityScore} /> },
    { key: 'updated', header: 'Last sync', hideOnMobile: true, render: (t) => <DateTime value={t.lastSyncedAt || t.createdAt} mode="relative" /> },
  ];
  return (
    <>
      <PageHeader
        title="Templates"
        subtitle="Message templates of all partners and our own accounts."
        actions={
          canReview && (
            <>
              <Button variant="secondary" leftIcon={ClipboardCheck} to="/admin/templates/review">Review queue</Button>
              <Button variant="secondary" leftIcon={RefreshCw} onClick={() => setSyncOpen(true)}>Sync from Meta</Button>
              <Button leftIcon={Plus} to="/admin/templates/new">New template</Button>
            </>
          )
        }
      />
      <FilterBar>
        <SearchInput className="sm:flex-[2]" value={state.search} onSearch={(v) => update({ search: v })} placeholder="Search template name" />
        <Select aria-label="Status" value={state.status} onChange={(e) => update({ status: e.target.value })} placeholder="All statuses" options={TEMPLATE_STATUSES.map((s) => ({ value: s, label: humanize(s) }))} />
        <Select aria-label="Category" value={state.category} onChange={(e) => update({ category: e.target.value })} placeholder="All categories" options={TEMPLATE_CATEGORIES.map((c) => ({ value: c, label: humanize(c) }))} />
        <Select aria-label="WhatsApp account" value={state.wabaId} onChange={(e) => update({ wabaId: e.target.value })} placeholder="All accounts" options={wabas.options.map((o) => ({ value: String(o.value), label: o.label }))} />
      </FilterBar>
      <DataTable
        columns={columns}
        page={q.data}
        loading={q.isLoading}
        error={q.error}
        onRetry={q.refetch}
        onPageChange={(p) => update({ page: p })}
        onRowClick={setSelected}
        empty={<EmptyState icon={FileText} title="No templates found" text="Templates created by partners or synced from Meta appear here." />}
      />
      <Drawer
        open={Boolean(selected)}
        onClose={() => setSelected(null)}
        title={selected?.name}
        description={selected ? `${selected.language} · ${humanize(selected.category)}` : ''}
        width="md:w-[520px]"
        footer={canReview && selected && selected.status !== 'DELETED' && <Button variant="danger-outline" leftIcon={Trash2} onClick={() => setDel(selected)}>Delete</Button>}
      >
        {selected && (
          <div className="space-y-4">
            {selected.rejectedReason && <Alert variant="error" title="Rejected">{selected.rejectedReason}</Alert>}
            {selected.previousCategory && <Alert variant="warning">Meta changed the category from {humanize(selected.previousCategory)} to {humanize(selected.category)}.</Alert>}
            <TemplatePreviewCard template={selected} businessName={selected.partner?.businessName} />
            <KeyValue
              items={[
                { label: 'Status', value: <StatusBadge status={selected.status} /> },
                { label: 'Quality', value: <QualityBadge rating={selected.qualityScore} /> },
                { label: 'Partner', value: selected.partner?.businessName || 'Platform' },
                { label: 'WhatsApp account', value: selected.waba?.wabaId },
                { label: 'Variables', value: humanize(selected.parameterFormat) },
                { label: 'Library template', value: selected.libraryTemplateName, hidden: !selected.libraryTemplateName },
                { label: 'Submitted', value: <DateTime value={selected.submittedAt} /> },
                { label: 'Review note', value: selected.adminReviewNote, hidden: !selected.adminReviewNote },
              ]}
            />
          </div>
        )}
      </Drawer>
      <ConfirmDialog
        open={Boolean(del)}
        onClose={() => setDel(null)}
        danger
        title={`Delete "${del?.name}"?`}
        message="Meta deletes all languages of this template name. The same name cannot be reused for 30 days. Campaigns using it will stop."
        confirmWord="DELETE"
        confirmLabel="Delete template"
        loading={delM.isPending}
        onConfirm={() => delM.mutate(del)}
      />
      <SyncModal open={syncOpen} onClose={() => setSyncOpen(false)} options={wabas.options} />
    </>
  );
}

function SyncModal({ open, onClose, options }) {
  const [waba, setWaba] = useState('');
  const m = useApiMutation({
    mutationFn: () => templatesApi.syncTemplatesAdmin(Number(waba)),
    success: (r) => `Synced: ${r?.created ?? 0} new, ${r?.updated ?? 0} updated, ${r?.deleted ?? 0} removed`,
    invalidate: [['admin', 'templates']],
    onSuccess: onClose,
  });
  return (
    <Modal open={open} onClose={onClose} title="Sync templates from Meta" size="sm" footer={<><Button variant="secondary" onClick={onClose}>Cancel</Button><Button disabled={!waba} loading={m.isPending} onClick={() => m.mutate()}>Sync now</Button></>}>
      <Select label="WhatsApp account" value={waba} onChange={(e) => setWaba(e.target.value)} placeholder="Choose account" options={options.map((o) => ({ value: String(o.value), label: o.label }))} />
    </Modal>
  );
}
