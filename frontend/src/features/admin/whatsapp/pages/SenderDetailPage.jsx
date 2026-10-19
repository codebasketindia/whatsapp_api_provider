import { useState } from 'react';
import { useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { MoreVertical, Plug, RefreshCw, Settings2, UserMinus, UserPlus } from 'lucide-react';
import * as metaApi from '../../../../api/endpoints/meta';
import { adminSenderTools as tools } from '../../../../api/endpoints/senderTools';
import { useAuth } from '../../../../auth/useAuth';
import { Button, DropdownMenu, ErrorState, IconButton, PageHeader, PageLoader, Tabs, useActiveTab } from '../../../../components/ui';
import QualityBadge from '../../../../components/whatsapp/QualityBadge';
import { useApiMutation } from '../../shared/useApiMutation';
import SenderAdminDialogs from '../components/SenderAdminDialogs';
import { senderStatusBadge, senderTypeBadge } from '../components/senderColumns';
import OverviewTab from '../components/tools/OverviewTab';
import ProfileTab from '../components/tools/ProfileTab';
import VerificationTab from '../components/tools/VerificationTab';
import HealthTab from '../components/tools/HealthTab';
import AnalyticsTab from '../components/tools/AnalyticsTab';
import QrCodesTab from '../components/tools/QrCodesTab';
import WelcomeTab from '../components/tools/WelcomeTab';
import BlockListTab from '../components/tools/BlockListTab';

const TABS = [
  { value: 'overview', label: 'Overview' },
  { value: 'profile', label: 'Profile' },
  { value: 'verification', label: 'Verification' },
  { value: 'health', label: 'Health' },
  { value: 'analytics', label: 'Analytics' },
  { value: 'qr', label: 'QR codes' },
  { value: 'welcome', label: 'Welcome & ice breakers' },
  { value: 'blocked', label: 'Block list' },
];

export default function SenderDetailPage() {
  const { id } = useParams();
  const { hasPermission } = useAuth();
  const canManage = hasPermission('WHATSAPP_MANAGE');
  const [dialog, setDialog] = useState(null);
  const tab = useActiveTab(TABS);
  const q = useQuery({ queryKey: ['admin', 'senders', 'detail', id], queryFn: () => metaApi.getSender(id) });
  const syncM = useApiMutation({ mutationFn: () => tools.sync(id), success: 'Refreshed from Meta', invalidate: [['admin', 'senders']] });

  if (q.isLoading) return <PageLoader />;
  if (q.isError) return <ErrorState error={q.error} onRetry={q.refetch} />;
  const data = q.data;
  const s = data.sender || data;
  const checklist = data.checklist || s.checklist;
  const connected = s.status !== 'DISCONNECTED';
  const menu = [
    { label: 'Sending settings', icon: Settings2, onClick: () => setDialog({ type: 'settings', sender: s }) },
    s.senderType === 'PLATFORM_SENDER' && !s.partner && { label: 'Assign to partner', icon: UserPlus, onClick: () => setDialog({ type: 'assign', sender: s }) },
    s.senderType === 'PLATFORM_SENDER' && s.partner && { label: 'Unassign partner', icon: UserMinus, onClick: () => setDialog({ type: 'unassign', sender: s }) },
    connected && { label: 'Disconnect number', icon: Plug, danger: true, onClick: () => setDialog({ type: 'disconnect', sender: s }) },
  ].filter(Boolean);
  const props = { sender: s, tools, canManage: canManage && connected, onChanged: q.refetch };

  return (
    <>
      <PageHeader
        title={s.displayPhoneNumber}
        subtitle={<span className="flex flex-wrap items-center gap-2"><span>{s.verifiedName || 'No display name'}</span>{senderTypeBadge(s)}{senderStatusBadge(s)}<QualityBadge rating={s.qualityRating} /></span>}
        backTo="/admin/whatsapp/senders"
        actions={
          canManage && (
            <>
              <Button variant="secondary" leftIcon={RefreshCw} loading={syncM.isPending} onClick={() => syncM.mutate()}>Sync</Button>
              <DropdownMenu trigger={({ toggle }) => <IconButton icon={MoreVertical} label="More actions" variant="secondary" onClick={toggle} />} items={menu} />
            </>
          )
        }
      />
      <Tabs tabs={TABS} urlParam="tab" className="mb-4" />
      {tab === 'overview' && <OverviewTab sender={s} checklist={checklist} admin />}
      {tab === 'profile' && <ProfileTab {...props} />}
      {tab === 'verification' && <VerificationTab {...props} />}
      {tab === 'health' && <HealthTab {...props} />}
      {tab === 'analytics' && <AnalyticsTab {...props} />}
      {tab === 'qr' && <QrCodesTab {...props} />}
      {tab === 'welcome' && <WelcomeTab {...props} />}
      {tab === 'blocked' && <BlockListTab {...props} />}
      <SenderAdminDialogs dialog={dialog} onClose={() => { setDialog(null); q.refetch(); }} />
    </>
  );
}
