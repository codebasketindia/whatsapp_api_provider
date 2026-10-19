import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Archive, ArchiveRestore, Plus, Star, Trash2 } from 'lucide-react';
import * as ratePlansApi from '../../../../api/endpoints/ratePlans';
import { humanize } from '../../../../lib/format';
import { glossary } from '../../../../lib/metaGlossary';
import {
  Alert, Badge, Button, Card, ConfirmDialog, ErrorState, IconButton, InfoTip, Input, PageHeader, PageLoader, Switch, Textarea,
} from '../../../../components/ui';
import PartnerSelect from '../../shared/PartnerSelect';
import { useApiMutation } from '../../shared/useApiMutation';
import { ALL_COUNTRIES, GRID_CATEGORIES, gridToItems, itemsToGrid, sortRows, validateGrid } from '../components/ratePlanGrid';

const FEE_RE = /^\d+(\.\d{1,4})?$/;
const TERM = { MARKETING: 'MARKETING', UTILITY: 'UTILITY', AUTHENTICATION: 'AUTHENTICATION' };

export default function RatePlanBuilderPage() {
  const { id } = useParams();
  const isNew = id === 'new';
  const navigate = useNavigate();
  const q = useQuery({ queryKey: ['admin', 'rate-plans', 'detail', id], queryFn: () => ratePlansApi.getRatePlan(id), enabled: !isNew });

  const [form, setForm] = useState(null);
  const [rows, setRows] = useState([]);
  const [newCountry, setNewCountry] = useState('');
  const [confirm, setConfirm] = useState(null);
  const [touched, setTouched] = useState(false);

  useEffect(() => {
    if (isNew) {
      setForm({ name: '', description: '', isDefault: false, partner: null, sessionMessageFee: '0', platformFeePerMessage: '0', platformFeeMonthlyPerNumber: '0' });
      setRows(itemsToGrid([]));
    } else if (q.data) {
      const p = q.data;
      setForm({
        name: p.name, description: p.description || '', isDefault: p.isDefault,
        partner: p.partnerId ? { id: p.partnerId, businessName: p.partnerName || `Partner #${p.partnerId}` } : null,
        sessionMessageFee: String(Number(p.sessionMessageFee ?? 0)),
        platformFeePerMessage: String(Number(p.platformFeePerMessage ?? 0)),
        platformFeeMonthlyPerNumber: String(Number(p.platformFeeMonthlyPerNumber ?? 0)),
      });
      setRows(itemsToGrid(p.items));
    }
  }, [isNew, q.data]);

  const gridErrors = useMemo(() => validateGrid(rows), [rows]);
  const feeErrors = form
    ? ['sessionMessageFee', 'platformFeePerMessage', 'platformFeeMonthlyPerNumber'].filter((k) => !FEE_RE.test(String(form[k]).trim()))
    : [];
  const nameError = form && form.name.trim().length < 2 ? 'Enter a plan name' : null;
  const valid = form && !nameError && gridErrors.length === 0 && feeErrors.length === 0;

  const save = useApiMutation({
    mutationFn: () => {
      const body = {
        name: form.name.trim(),
        description: form.description.trim() || null,
        isDefault: form.isDefault,
        partnerId: form.partner?.id ?? null,
        sessionMessageFee: form.sessionMessageFee.trim(),
        platformFeePerMessage: form.platformFeePerMessage.trim(),
        platformFeeMonthlyPerNumber: form.platformFeeMonthlyPerNumber.trim(),
        items: gridToItems(rows),
      };
      return isNew ? ratePlansApi.createRatePlan(body) : ratePlansApi.updateRatePlan(id, body);
    },
    success: isNew ? 'Rate plan created' : 'Rate plan saved. New prices apply to the next messages.',
    invalidate: [['admin', 'rate-plans']],
    onSuccess: (p) => {
      setConfirm(null);
      if (isNew) navigate(`/admin/pricing/rate-plans/${p.id}`, { replace: true });
    },
  });
  const statusM = useApiMutation({
    mutationFn: (status) => ratePlansApi.setRatePlanStatus(id, status),
    success: (p) => (p.status === 'ARCHIVED' ? 'Plan archived' : 'Plan restored'),
    invalidate: [['admin', 'rate-plans']],
    onSuccess: () => setConfirm(null),
  });

  if (!isNew && q.isLoading) return <PageLoader />;
  if (!isNew && q.isError) return <ErrorState error={q.error} onRetry={q.refetch} />;
  if (!form) return <PageLoader />;

  const setPrice = (country, cat, v) =>
    setRows((rs) => rs.map((r) => (r.countryCode === country ? { ...r, prices: { ...r.prices, [cat]: v.replace(/[^\d.]/g, '') } } : r)));
  const addCountry = () => {
    const c = newCountry.trim().toUpperCase();
    if (!c) return;
    setRows((rs) => sortRows([...rs, { countryCode: c, prices: {} }]));
    setNewCountry('');
  };
  const hasAll = rows.some((r) => r.countryCode === ALL_COUNTRIES);
  const plan = q.data;

  return (
    <>
      <PageHeader
        title={isNew ? 'New rate plan' : form.name || 'Rate plan'}
        subtitle={isNew ? 'Set prices per message category and country.' : <span className="flex flex-wrap gap-2">{plan?.isDefault && <Badge tone="brand">Default plan</Badge>}{plan?.status === 'ARCHIVED' && <Badge tone="gray">Archived</Badge>}{plan?.partnerCount != null && <span>{plan.partnerCount} partners</span>}</span>}
        backTo="/admin/pricing/rate-plans"
        actions={
          !isNew && (
            plan?.status === 'ARCHIVED' ? (
              <Button variant="secondary" leftIcon={ArchiveRestore} onClick={() => setConfirm('restore')}>Restore</Button>
            ) : (
              <Button variant="secondary" leftIcon={Archive} disabled={plan?.isDefault} onClick={() => setConfirm('archive')}>Archive</Button>
            )
          )
        }
      />
      <div className="space-y-4 pb-24 md:pb-4">
        <Card title="Plan details">
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
            <Input label="Plan name" required value={form.name} error={touched && nameError} onChange={(e) => setForm({ ...form, name: e.target.value })} />
            <Textarea label="Description" rows={2} maxLength={255} value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
          </div>
          <Switch
            className="mt-2"
            checked={form.isDefault}
            disabled={plan?.isDefault}
            onChange={(v) => setForm({ ...form, isDefault: v, partner: v ? null : form.partner })}
            label={<span className="inline-flex items-center gap-1"><Star className="h-4 w-4" /> Default plan</span>}
            description="New partners get this plan. Only one plan can be the default."
          />
          {!form.isDefault && (
            <div className="mt-4 md:w-1/2">
              <PartnerSelect
                label="Custom plan for one partner (optional)"
                value={form.partner}
                onChange={(p) => setForm({ ...form, partner: p })}
                hint="Leave empty to make the plan available to any partner."
              />
            </div>
          )}
        </Card>

        <Card
          title="Template message prices (per message, ₹)"
          subtitle="Leave a cell empty to block that category for that country. “All other countries” is used when a country has no row."
        >
          <div className="-mx-4 overflow-x-auto px-4">
            <table className="min-w-[640px] text-sm">
              <thead>
                <tr className="text-left text-muted">
                  <th className="py-2 pr-3 font-medium">Country</th>
                  {GRID_CATEGORIES.map((c) => (
                    <th key={c} className="px-2 py-2 font-medium">
                      <span className="inline-flex items-center gap-1">{humanize(c)}{TERM[c] && <InfoTip term={TERM[c]} />}</span>
                    </th>
                  ))}
                  <th className="sr-only">Remove</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((r) => (
                  <tr key={r.countryCode} className="border-t border-line">
                    <td className="py-2 pr-3 font-medium text-ink">
                      {r.countryCode === ALL_COUNTRIES ? 'All other countries' : r.countryCode === 'IN' ? 'India (+91)' : r.countryCode}
                    </td>
                    {GRID_CATEGORIES.map((c) => (
                      <td key={c} className="px-2 py-2">
                        <input
                          aria-label={`${r.countryCode} ${c} price`}
                          inputMode="decimal"
                          className="field-base w-28 text-right"
                          placeholder="—"
                          value={r.prices[c] ?? ''}
                          onChange={(e) => setPrice(r.countryCode, c, e.target.value)}
                        />
                      </td>
                    ))}
                    <td className="py-2 pl-2 text-right">
                      {r.countryCode !== 'IN' && (
                        <IconButton icon={Trash2} label={`Remove ${r.countryCode}`} onClick={() => setRows((rs) => rs.filter((x) => x.countryCode !== r.countryCode))} />
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="mt-4 flex flex-col gap-2 sm:flex-row sm:items-end">
            <Input
              label="Add country"
              placeholder="2-letter code, e.g. AE"
              maxLength={2}
              value={newCountry}
              onChange={(e) => setNewCountry(e.target.value.toUpperCase().replace(/[^A-Z]/g, ''))}
              className="sm:w-56"
            />
            <Button variant="secondary" leftIcon={Plus} disabled={newCountry.length !== 2 || rows.some((r) => r.countryCode === newCountry)} onClick={addCountry}>
              Add country
            </Button>
            {!hasAll && (
              <Button variant="ghost" leftIcon={Plus} onClick={() => setRows((rs) => sortRows([...rs, { countryCode: ALL_COUNTRIES, prices: {} }]))}>
                Add “all other countries”
              </Button>
            )}
          </div>
          {gridErrors.length > 0 && (
            <Alert variant="error" className="mt-4" title="Please fix these prices">
              <ul className="list-disc pl-5">{gridErrors.map((e) => <li key={e}>{e}</li>)}</ul>
            </Alert>
          )}
        </Card>

        <Card title="Other fees (₹)">
          <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
            <Input
              label="Service message fee"
              info={glossary('SERVICE')}
              inputMode="decimal"
              hint="Free-form replies inside the 24-hour window. Meta does not charge for these."
              value={form.sessionMessageFee}
              error={feeErrors.includes('sessionMessageFee') && 'Enter a valid amount'}
              onChange={(e) => setForm({ ...form, sessionMessageFee: e.target.value })}
            />
            <Input
              label="Platform fee per message"
              info={glossary('TECH_PROVIDER')}
              inputMode="decimal"
              hint="Charged instead of the prices above for partner-own numbers when Meta bills the partner directly."
              value={form.platformFeePerMessage}
              error={feeErrors.includes('platformFeePerMessage') && 'Enter a valid amount'}
              onChange={(e) => setForm({ ...form, platformFeePerMessage: e.target.value })}
            />
            <Input
              label="Monthly fee per own number"
              inputMode="decimal"
              hint="Deducted on the 1st of every month for each connected partner-own number."
              value={form.platformFeeMonthlyPerNumber}
              error={feeErrors.includes('platformFeeMonthlyPerNumber') && 'Enter a valid amount'}
              onChange={(e) => setForm({ ...form, platformFeeMonthlyPerNumber: e.target.value })}
            />
          </div>
        </Card>
        <div className="fixed inset-x-0 bottom-0 z-20 flex gap-3 border-t border-line bg-surface p-3 safe-bottom md:static md:justify-end md:border-0 md:bg-transparent md:p-0">
          <Button variant="secondary" className="flex-1 md:flex-none" to="/admin/pricing/rate-plans">Cancel</Button>
          <Button
            className="flex-1 md:flex-none"
            loading={save.isPending}
            onClick={() => {
              setTouched(true);
              if (valid) setConfirm('save');
            }}
          >
            {isNew ? 'Create plan' : 'Save prices'}
          </Button>
        </div>
      </div>
      <ConfirmDialog
        open={confirm === 'save'}
        onClose={() => setConfirm(null)}
        title={isNew ? 'Create this rate plan?' : 'Save new prices?'}
        message={
          isNew
            ? 'You can assign it to partners from the partner page.'
            : `Partners on this plan (${plan?.partnerCount ?? 0}) will be charged the new prices for messages sent from now on.`
        }
        confirmLabel={isNew ? 'Create plan' : 'Save prices'}
        loading={save.isPending}
        onConfirm={() => save.mutate()}
      />
      <ConfirmDialog
        open={confirm === 'archive' || confirm === 'restore'}
        onClose={() => setConfirm(null)}
        danger={confirm === 'archive'}
        title={confirm === 'archive' ? 'Archive this plan?' : 'Restore this plan?'}
        message={confirm === 'archive' ? 'Archived plans cannot be assigned. Plans that partners still use cannot be archived.' : 'The plan can be assigned to partners again.'}
        confirmLabel={confirm === 'archive' ? 'Archive' : 'Restore'}
        loading={statusM.isPending}
        onConfirm={() => statusM.mutate(confirm === 'archive' ? 'ARCHIVED' : 'ACTIVE')}
      />
    </>
  );
}
