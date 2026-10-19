import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import * as partnersApi from '../../../../api/endpoints/partners';
import * as ratePlansApi from '../../../../api/endpoints/ratePlans';
import { useAuth } from '../../../../auth/useAuth';
import { formatPhone } from '../../../../lib/format';
import { Button, Card, ConfirmDialog, DateTime, InfoTip, KeyValue, Select, Switch } from '../../../../components/ui';
import { useApiMutation } from '../../shared/useApiMutation';

export default function ProfileTab({ partner: p }) {
  const { hasPermission } = useAuth();
  const canManage = hasPermission('PARTNER_MANAGE');
  const canPricing = hasPermission('PRICING_MANAGE');
  const [planId, setPlanId] = useState('');
  const [confirm, setConfirm] = useState(null);
  const plansQ = useQuery({
    queryKey: ['admin', 'rate-plans', 'active'],
    queryFn: () => ratePlansApi.listRatePlans({ status: 'ACTIVE', includeCustom: true, size: 100 }),
    enabled: canPricing,
  });
  const plans = (plansQ.data?.content || []).filter((pl) => !pl.isCustom || pl.partnerId == null || pl.partnerId === p.id);
  const inv = [['admin', 'partners']];

  const planM = useApiMutation({
    mutationFn: (ratePlanId) => partnersApi.setPartnerRatePlan(p.id, ratePlanId),
    success: 'Rate plan changed. New prices apply to the next messages.',
    invalidate: inv,
    onSuccess: () => { setConfirm(null); setPlanId(''); },
  });
  const ownM = useApiMutation({
    mutationFn: (allowed) => partnersApi.setPartnerOwnNumber(p.id, allowed),
    success: (d) => (d.allowOwnNumber ? 'Partner can now connect their own number' : 'Connecting own numbers is now blocked'),
    invalidate: inv,
    onSuccess: () => setConfirm(null),
  });
  const kycM = useApiMutation({
    mutationFn: (v) => partnersApi.setPartnerKyc(p.id, v),
    success: (d) => (d.kycVerified ? 'KYC marked as verified' : 'KYC marked as not verified'),
    invalidate: inv,
  });

  const a = p.address || {};
  const address = [a.line1, a.line2, a.city, a.state, a.pincode].filter(Boolean).join(', ');
  return (
    <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
      <Card title="Business details" className="lg:col-span-2">
        <KeyValue
          items={[
            { label: 'Business name', value: p.businessName },
            { label: 'Partner code', value: p.partnerCode },
            { label: 'Contact person', value: p.contactName },
            { label: 'Email', value: p.email },
            { label: 'Mobile', value: formatPhone(p.mobile) },
            { label: 'Website', value: p.website ? <a className="text-brand-700 hover:underline dark:text-brand-300" href={p.website} target="_blank" rel="noreferrer">{p.website}</a> : '—' },
            { label: 'GSTIN', value: p.gstin || '—' },
            { label: 'PAN', value: p.pan || '—' },
            { label: 'Address', value: address || '—' },
            { label: 'GST state code', value: a.stateCode || '—' },
            { label: 'Joined', value: <DateTime value={p.createdAt} /> },
            { label: 'Low balance alert', value: p.settings?.lowBalanceThreshold != null ? `₹${p.settings.lowBalanceThreshold}` : 'Platform default' },
          ]}
        />
      </Card>
      <div className="space-y-4">
        <Card title="Rate plan" subtitle="Prices the partner pays per message.">
          <p className="mb-3 text-sm">
            Current: <span className="font-medium text-ink">{p.ratePlan?.name || 'Not assigned'}</span>
          </p>
          {canPricing && (
            <div className="flex flex-col gap-2 sm:flex-row sm:items-end">
              <Select
                className="flex-1"
                aria-label="New rate plan"
                value={planId}
                onChange={(e) => setPlanId(e.target.value)}
                placeholder="Choose another plan"
                options={plans.filter((pl) => pl.id !== p.ratePlan?.id).map((pl) => ({ value: String(pl.id), label: pl.name + (pl.isCustom ? ' (custom)' : '') }))}
              />
              <Button variant="secondary" disabled={!planId} onClick={() => setConfirm('plan')}>Change</Button>
            </div>
          )}
        </Card>
        <Card title="Access">
          <Switch
            checked={p.allowOwnNumber}
            disabled={!canManage || ownM.isPending}
            onChange={(v) => setConfirm(v ? 'ownOn' : 'ownOff')}
            label={<span className="inline-flex items-center gap-1">Connect own number <InfoTip term="EMBEDDED_SIGNUP" /></span>}
            description="Allow Meta Embedded Signup for this partner."
          />
          <Switch
            className="mt-2"
            checked={p.kycVerified}
            disabled={!canManage || kycM.isPending}
            onChange={(v) => kycM.mutate(v)}
            label="KYC verified"
            description="Tick after checking business documents."
          />
        </Card>
      </div>
      <ConfirmDialog
        open={confirm === 'plan'}
        onClose={() => setConfirm(null)}
        title="Change rate plan?"
        message={`New prices from "${plans.find((pl) => String(pl.id) === planId)?.name}" will apply to messages sent from now on. Messages already sent are not repriced.`}
        confirmLabel="Change plan"
        loading={planM.isPending}
        onConfirm={() => planM.mutate(Number(planId))}
      />
      <ConfirmDialog
        open={confirm === 'ownOn' || confirm === 'ownOff'}
        onClose={() => setConfirm(null)}
        danger={confirm === 'ownOff'}
        title={confirm === 'ownOn' ? 'Allow own WhatsApp numbers?' : 'Block own WhatsApp numbers?'}
        message={
          confirm === 'ownOn'
            ? 'The partner will see a "Connect WhatsApp Number" button and can connect their own number through Meta.'
            : 'The partner cannot connect new numbers. Numbers already connected keep working.'
        }
        confirmLabel={confirm === 'ownOn' ? 'Allow' : 'Block'}
        loading={ownM.isPending}
        onConfirm={() => ownM.mutate(confirm === 'ownOn')}
      />
    </div>
  );
}
