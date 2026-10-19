import { Link } from 'react-router-dom';
import { humanize } from '../../../../../lib/format';
import QualityBadge from '../../../../../components/whatsapp/QualityBadge';
import { Badge, Card, DateTime, InfoTip, KeyValue, StatusBadge } from '../../../../../components/ui';
import Checklist from '../Checklist';

/** Sender summary: identity, limits, assignment and setup checklist. */
export default function OverviewTab({ sender: s, checklist, admin }) {
  return (
    <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
      <Card title="Number" className="lg:col-span-2">
        <KeyValue
          items={[
            { label: 'Phone number', value: s.displayPhoneNumber },
            { label: 'Display name', value: s.verifiedName || '—' },
            { label: 'Name status', value: s.nameStatus ? <StatusBadge status={s.nameStatus} /> : '—' },
            { label: 'Pending new name', value: s.newNameStatus ? humanize(s.newNameStatus) : '—', hidden: !s.newNameStatus },
            { label: 'Quality', value: <QualityBadge rating={s.qualityRating} withInfo /> },
            { label: 'Messaging limit', value: <span className="inline-flex items-center gap-1">{s.messagingLimitTier ? humanize(s.messagingLimitTier) : '—'} <InfoTip term="MESSAGING_LIMIT" /></span> },
            { label: 'Throughput', value: s.throughputLevel ? `${humanize(s.throughputLevel)}${s.maxMps ? ` · our limit ${s.maxMps}/sec` : ''}` : '—' },
            { label: 'Meta status', value: s.metaStatus ? <StatusBadge status={s.metaStatus} /> : '—' },
            { label: 'Our status', value: s.isPaused ? <span><StatusBadge status="PAUSED" /> <span className="text-xs text-muted">{s.pauseReason}</span></span> : <StatusBadge status={s.status} /> },
            { label: 'Type', value: <Badge tone={s.senderType === 'PLATFORM_SENDER' ? 'brand' : 'blue'}>{s.senderType === 'PLATFORM_SENDER' ? 'Platform number' : 'Partner own number'}</Badge> },
            { label: 'WhatsApp Business app', value: s.isCoexistence ? 'Yes (coexistence)' : 'No' },
            { label: 'Daily cap', value: s.dailySendCap ? `${s.sentToday ?? 0} / ${s.dailySendCap} today` : `${s.sentToday ?? 0} today · no cap` },
            {
              label: 'Partner',
              value: s.partner ? (admin ? <Link className="text-brand-700 hover:underline dark:text-brand-300" to={`/admin/partners/${s.partner.id}`}>{s.partner.businessName}</Link> : s.partner.businessName) : 'Not assigned',
              hidden: !admin,
            },
            {
              label: 'WhatsApp account',
              value: s.waba ? (admin ? <Link className="text-brand-700 hover:underline dark:text-brand-300" to={`/admin/whatsapp/accounts/${s.waba.id}`}>{s.waba.name || s.waba.wabaId}</Link> : s.waba.name || s.waba.wabaId) : '—',
            },
            { label: 'Phone number ID', value: <span className="font-mono text-xs">{s.phoneNumberId}</span> },
            { label: 'Connected', value: <DateTime value={s.connectedAt} /> },
            { label: 'Last synced', value: <DateTime value={s.lastSyncedAt} mode="relative" /> },
          ]}
        />
      </Card>
      <Card title="What to do next">
        <Checklist checklist={checklist} />
      </Card>
    </div>
  );
}
