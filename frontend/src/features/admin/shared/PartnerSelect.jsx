import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Building2, Check, X } from 'lucide-react';
import * as partnersApi from '../../../api/endpoints/partners';
import { cn } from '../../../lib/cn';
import { FormField, SearchInput, Spinner } from '../../../components/ui';

/**
 * Searchable partner picker. value: { id, businessName } | null; onChange(partner | null).
 */
export default function PartnerSelect({ value, onChange, label = 'Partner', required, error, hint, statusFilter = 'ACTIVE', allowClear = true }) {
  const [search, setSearch] = useState('');
  const q = useQuery({
    queryKey: ['admin', 'partners', 'picker', search, statusFilter],
    queryFn: () => partnersApi.listPartners({ search, status: statusFilter, size: 8, page: 0 }),
    enabled: !value,
    placeholderData: (p) => p,
  });
  const results = q.data?.content || [];
  return (
    <FormField label={label} required={required} error={error} hint={hint}>
      {value ? (
        <div className="flex min-h-tap items-center justify-between gap-2 rounded-lg border border-line bg-surface px-3">
          <span className="flex min-w-0 items-center gap-2 text-sm">
            <Building2 className="h-4 w-4 shrink-0 text-muted" aria-hidden />
            <span className="truncate font-medium text-ink">{value.businessName}</span>
            {value.partnerCode && <span className="text-xs text-muted">{value.partnerCode}</span>}
          </span>
          {allowClear && (
            <button type="button" className="inline-flex h-9 w-9 items-center justify-center rounded-lg text-muted hover:bg-surface-2" onClick={() => onChange(null)} aria-label="Change partner">
              <X className="h-4 w-4" />
            </button>
          )}
        </div>
      ) : (
        <div className="space-y-2">
          <SearchInput value={search} onSearch={setSearch} placeholder="Search partner by name, code or email" />
          <div className="max-h-64 overflow-y-auto rounded-lg border border-line">
            {q.isLoading ? (
              <div className="flex justify-center p-4"><Spinner /></div>
            ) : results.length === 0 ? (
              <p className="p-4 text-center text-sm text-muted">No partners found.</p>
            ) : (
              results.map((p) => (
                <button
                  key={p.id}
                  type="button"
                  onClick={() => onChange(p)}
                  className={cn('flex min-h-tap w-full items-center justify-between gap-2 border-b border-line px-3 py-2 text-left text-sm last:border-0 hover:bg-surface-2')}
                >
                  <span className="min-w-0">
                    <span className="block truncate font-medium text-ink">{p.businessName}</span>
                    <span className="block truncate text-xs text-muted">{p.partnerCode} · {p.email}</span>
                  </span>
                  <Check className="h-4 w-4 shrink-0 text-transparent" aria-hidden />
                </button>
              ))
            )}
          </div>
        </div>
      )}
    </FormField>
  );
}
