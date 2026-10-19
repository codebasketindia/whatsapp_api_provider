import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import toast from 'react-hot-toast';
import * as developerApi from '../../../../api/endpoints/developer';
import { getErrorMessage } from '../../../../api/axios';
import { Button, DataTable, DateRangePicker, DateTime, Drawer, EmptyState, FilterBar, KeyValue, Select, StatusBadge } from '../../../../components/ui';
import { useUrlState } from '../../../../hooks/useUrlState';
import { EVENTS } from './WebhookTab';

export default function DeliveryLogsTab() {
  const qc = useQueryClient();
  const [f, update] = useUrlState({ page: 0, lstatus: '', eventType: '', from: '', to: '' });
  const params = { page: f.page, size: 20, status: f.lstatus, eventType: f.eventType, from: f.from, to: f.to };
  const q = useQuery({ queryKey: ['partner', 'webhook-logs', params], queryFn: () => developerApi.listWebhookLogs(params), placeholderData: (p) => p });
  const [open, setOpen] = useState(null);
  const retry = useMutation({
    mutationFn: (id) => developerApi.retryWebhookLog(id),
    onSuccess: () => {
      toast.success('Queued for another attempt');
      qc.invalidateQueries({ queryKey: ['partner', 'webhook-logs'] });
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });
  const columns = [
    { key: 'createdAt', header: 'Time', render: (r) => <DateTime value={r.createdAt} /> },
    { key: 'eventType', header: 'Event', primary: true, render: (r) => <span className="font-mono text-sm">{r.eventType}</span> },
    { key: 'status', header: 'Status', render: (r) => <StatusBadge status={r.status} /> },
    { key: 'responseCode', header: 'HTTP', render: (r) => r.responseCode ?? '—' },
    { key: 'attempt', header: 'Attempts', render: (r) => r.attempt },
    {
      key: 'retry',
      header: '',
      align: 'right',
      hideOnMobile: true,
      render: (r) => ['FAILED', 'DEAD'].includes(r.status) && (
        <Button size="sm" variant="secondary" onClick={(e) => { e.stopPropagation(); retry.mutate(r.id); }} loading={retry.isPending && retry.variables === r.id}>Retry</Button>
      ),
    },
  ];
  return (
    <>
      <FilterBar>
        <Select aria-label="Status" value={f.lstatus} onChange={(e) => update({ lstatus: e.target.value })} options={['PENDING', 'SUCCESS', 'FAILED', 'DEAD'].map((s) => ({ value: s, label: s.charAt(0) + s.slice(1).toLowerCase() }))} placeholder="All statuses" />
        <Select aria-label="Event" value={f.eventType} onChange={(e) => update({ eventType: e.target.value })} options={[{ value: 'test.ping', label: 'Test ping' }, ...EVENTS]} placeholder="All events" />
        <DateRangePicker value={{ from: f.from, to: f.to }} onChange={(v) => update(v)} allowClear />
      </FilterBar>
      <DataTable
        columns={columns}
        page={q.data}
        loading={q.isLoading}
        error={q.error}
        onRetry={q.refetch}
        onPageChange={(p) => update({ page: p })}
        onRowClick={setOpen}
        mobileActions={(r) => ['FAILED', 'DEAD'].includes(r.status) && <Button size="sm" variant="secondary" onClick={() => retry.mutate(r.id)}>Retry</Button>}
        empty={<EmptyState compact title="No deliveries yet" text="Events we send to your webhook appear here." />}
      />
      <Drawer open={Boolean(open)} onClose={() => setOpen(null)} title="Delivery details">
        {open && (
          <div className="space-y-4">
            <KeyValue
              columns={1}
              items={[
                { label: 'Event', value: open.eventType },
                { label: 'Event ID', value: <span className="break-all font-mono text-xs">{open.eventId}</span> },
                { label: 'Status', value: <StatusBadge status={open.status} /> },
                { label: 'HTTP response', value: open.responseCode ?? '—' },
                { label: 'Time taken', value: open.durationMs != null ? `${open.durationMs} ms` : '—' },
                { label: 'Next retry', value: <DateTime value={open.nextRetryAt} /> },
              ]}
            />
            {open.payload && (
              <div>
                <p className="label-base">Payload</p>
                <pre className="max-h-72 overflow-auto rounded-lg bg-surface-2 p-3 text-xs">{typeof open.payload === 'string' ? open.payload : JSON.stringify(open.payload, null, 2)}</pre>
              </div>
            )}
            {open.responseBody && (
              <div>
                <p className="label-base">Your server replied</p>
                <pre className="max-h-48 overflow-auto whitespace-pre-wrap rounded-lg bg-surface-2 p-3 text-xs">{open.responseBody}</pre>
              </div>
            )}
          </div>
        )}
      </Drawer>
    </>
  );
}
