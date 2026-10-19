import { Badge, StatusBadge } from '../../../../components/ui';
import QualityBadge from '../../../../components/whatsapp/QualityBadge';
import { humanize } from '../../../../lib/format';

export const senderTypeBadge = (s) => (
  <Badge tone={s.senderType === 'PLATFORM_SENDER' ? 'brand' : 'blue'}>{s.senderType === 'PLATFORM_SENDER' ? 'Platform' : 'Partner own'}</Badge>
);

export const senderStatusBadge = (s) => (s.isPaused ? <StatusBadge status="PAUSED" label="Paused" /> : <StatusBadge status={s.status} />);

/** Shared columns for sender lists. */
export function senderColumns({ showPartner = true, showWaba = false } = {}) {
  return [
    {
      key: 'number',
      header: 'Number',
      primary: true,
      render: (s) => (
        <span>
          <span className="block font-medium text-ink">{s.displayPhoneNumber}</span>
          <span className="block text-xs text-muted">{s.verifiedName || 'No display name'}{s.isCoexistence ? ' · WhatsApp Business app' : ''}</span>
        </span>
      ),
    },
    { key: 'type', header: 'Type', render: senderTypeBadge },
    { key: 'partner', header: 'Partner', hidden: !showPartner, render: (s) => s.partner?.businessName || <span className="text-muted">Not assigned</span> },
    { key: 'waba', header: 'WhatsApp account', hidden: !showWaba, hideOnMobile: true, render: (s) => s.waba?.name || s.waba?.wabaId },
    { key: 'quality', header: 'Quality', render: (s) => <QualityBadge rating={s.qualityRating} /> },
    { key: 'tier', header: 'Messaging limit', hideOnMobile: true, render: (s) => (s.messagingLimitTier ? humanize(s.messagingLimitTier) : '—') },
    { key: 'status', header: 'Status', render: senderStatusBadge },
  ].filter((c) => !c.hidden);
}
