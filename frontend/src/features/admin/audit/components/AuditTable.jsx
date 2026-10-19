import { useState } from 'react';
import { ScrollText } from 'lucide-react';
import { humanize } from '../../../../lib/format';
import { Badge, DataTable, DateTime, Drawer, EmptyState, KeyValue } from '../../../../components/ui';
import JsonDiff from './JsonDiff';

/** Audit entries table with a details drawer (old/new value diff). page = PageResponse. */
export default function AuditTable({ query, onPageChange, showPartner = false }) {
  const [selected, setSelected] = useState(null);
  const columns = [
    { key: 'createdAt', header: 'When', render: (a) => <DateTime value={a.createdAt} /> },
    {
      key: 'action',
      header: 'Action',
      primary: true,
      render: (a) => (
        <span>
          <span className="block font-medium text-ink">{humanize(a.action)}</span>
          {a.entityType && <span className="block text-xs text-muted">{humanize(a.entityType)} {a.entityId ? `#${a.entityId}` : ''}</span>}
        </span>
      ),
    },
    {
      key: 'actor',
      header: 'By',
      render: (a) => (
        <span className="text-sm">
          {a.actor ? a.actor.name : <Badge tone="gray">{humanize(a.actorType)}</Badge>}
          {a.impersonator && <span className="block text-xs text-purple-600 dark:text-purple-300">as partner, by {a.impersonator.name}</span>}
        </span>
      ),
    },
    { key: 'partnerId', header: 'Partner', hideOnMobile: true, hidden: !showPartner, render: (a) => (a.partnerId ? `#${a.partnerId}` : '—') },
    { key: 'ip', header: 'IP address', hideOnMobile: true, render: (a) => a.ip || '—' },
  ].filter((c) => !c.hidden);

  return (
    <>
      <DataTable
        columns={columns}
        page={query.data}
        loading={query.isLoading}
        error={query.error}
        onRetry={query.refetch}
        onPageChange={onPageChange}
        onRowClick={setSelected}
        empty={<EmptyState icon={ScrollText} title="No activity found" text="Changes and important actions will appear here." />}
      />
      <Drawer open={Boolean(selected)} onClose={() => setSelected(null)} title={selected ? humanize(selected.action) : ''} width="md:w-[560px]">
        {selected && (
          <div className="space-y-5">
            <KeyValue
              items={[
                { label: 'When', value: <DateTime value={selected.createdAt} /> },
                { label: 'By', value: selected.actor ? `${selected.actor.name} (${selected.actor.email})` : humanize(selected.actorType) },
                { label: 'Impersonated by', value: selected.impersonator?.name, hidden: !selected.impersonator },
                { label: 'Record', value: selected.entityType ? `${humanize(selected.entityType)} ${selected.entityId || ''}` : '—' },
                { label: 'Partner', value: selected.partnerId ? `#${selected.partnerId}` : '—' },
                { label: 'IP address', value: selected.ip || '—' },
                { label: 'Device', value: selected.userAgent || '—' },
              ]}
            />
            <JsonDiff before={selected.oldValue} after={selected.newValue} />
          </div>
        )}
      </Drawer>
    </>
  );
}
