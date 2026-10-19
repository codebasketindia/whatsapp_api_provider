import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Activity, BarChart3, Code2, KeyRound, LogIn, MoreVertical, Pencil, Smartphone, User, UserCheck, UserX, Wallet } from 'lucide-react';
import * as partnersApi from '../../../../api/endpoints/partners';
import { useAuth } from '../../../../auth/useAuth';
import {
  Button, DropdownMenu, ErrorState, IconButton, Money, PageHeader, PageLoader, StatusBadge, Tabs, useActiveTab,
} from '../../../../components/ui';
import { formatNumber } from '../../../../lib/format';
import ReasonDialog from '../../shared/ReasonDialog';
import { useApiMutation } from '../../shared/useApiMutation';
import ConfirmDialog from '../../../../components/ui/ConfirmDialog';
import ProfileTab from '../components/ProfileTab';
import WalletTab from '../components/WalletTab';
import SendersTab from '../components/SendersTab';
import UsageTab from '../components/UsageTab';
import ApiTab from '../components/ApiTab';
import ActivityTab from '../components/ActivityTab';

export default function PartnerDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { hasPermission, loginAsPartner } = useAuth();
  const [dialog, setDialog] = useState(null); // 'suspend' | 'activate' | 'loginAs' | 'reset'
  const q = useQuery({ queryKey: ['admin', 'partners', 'detail', id], queryFn: () => partnersApi.getPartner(id) });
  const p = q.data;

  const tabs = [
    { value: 'profile', label: 'Profile', icon: User },
    { value: 'wallet', label: 'Wallet', icon: Wallet, hidden: !hasPermission('WALLET_VIEW') },
    { value: 'senders', label: 'Senders', icon: Smartphone },
    { value: 'usage', label: 'Usage', icon: BarChart3 },
    { value: 'api', label: 'API', icon: Code2 },
    { value: 'activity', label: 'Activity', icon: Activity, hidden: !hasPermission('AUDIT_VIEW') },
  ];
  const tab = useActiveTab(tabs);

  const statusM = useApiMutation({
    mutationFn: ({ status, reason }) => partnersApi.setPartnerStatus(id, { status, reason }),
    success: (d) => (d.status === 'ACTIVE' ? 'Partner activated' : 'Partner suspended. Running campaigns were paused.'),
    invalidate: [['admin', 'partners']],
    onSuccess: () => setDialog(null),
  });
  const resetM = useApiMutation({
    mutationFn: () => partnersApi.resetPartnerPassword(id),
    success: 'A temporary password has been emailed to the partner',
    onSuccess: () => setDialog(null),
  });
  const loginAsM = useApiMutation({
    mutationFn: (reason) => loginAsPartner(Number(id), reason),
    success: 'You are now logged in as the partner',
    onSuccess: () => navigate('/partner', { replace: true }),
  });

  if (q.isLoading) return <PageLoader />;
  if (q.isError) return <ErrorState error={q.error} onRetry={q.refetch} />;

  const canManage = hasPermission('PARTNER_MANAGE');
  const menu = [
    { label: 'Edit details', icon: Pencil, onClick: () => navigate(`/admin/partners/${id}/edit`), hidden: !canManage },
    { label: 'Send new password', icon: KeyRound, onClick: () => setDialog('reset'), hidden: !canManage },
    p.status === 'ACTIVE'
      ? { label: 'Suspend partner', icon: UserX, danger: true, onClick: () => setDialog('suspend'), hidden: !canManage }
      : { label: 'Activate partner', icon: UserCheck, onClick: () => setDialog('activate'), hidden: !canManage },
  ].filter((m) => !m.hidden);

  return (
    <>
      <PageHeader
        title={p.businessName}
        subtitle={
          <span className="flex flex-wrap items-center gap-2">
            <span>{p.partnerCode}</span>
            <StatusBadge status={p.status} />
            <span className="text-muted">· Balance <Money value={p.wallet?.balance} /></span>
          </span>
        }
        backTo="/admin/partners"
        actions={
          <>
            {hasPermission('LOGIN_AS_PARTNER') && p.status === 'ACTIVE' && (
              <Button variant="secondary" leftIcon={LogIn} onClick={() => setDialog('loginAs')}>
                Log in as partner
              </Button>
            )}
            {menu.length > 0 && (
              <DropdownMenu trigger={({ toggle }) => <IconButton icon={MoreVertical} label="More actions" onClick={toggle} variant="secondary" />} items={menu} />
            )}
          </>
        }
      />
      {p.status === 'SUSPENDED' && p.suspendReason && (
        <div className="mb-4 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800 dark:border-red-500/30 dark:bg-red-500/10 dark:text-red-200">
          Suspended: {p.suspendReason}
        </div>
      )}
      {p.stats && (
        <div className="mb-4 grid grid-cols-2 gap-3 md:grid-cols-4">
          <MiniStat label="Senders" value={formatNumber(p.stats.senders)} />
          <MiniStat label="Messages today" value={formatNumber(p.stats.messagesToday)} />
          <MiniStat label="Messages this month" value={formatNumber(p.stats.messagesMonth)} />
          <MiniStat label="Spend this month" value={<Money value={p.stats.spendMonth} />} />
        </div>
      )}
      <Tabs tabs={tabs} urlParam="tab" className="mb-4" />
      {tab === 'profile' && <ProfileTab partner={p} />}
      {tab === 'wallet' && <WalletTab partner={p} />}
      {tab === 'senders' && <SendersTab partner={p} />}
      {tab === 'usage' && <UsageTab partner={p} />}
      {tab === 'api' && <ApiTab partner={p} />}
      {tab === 'activity' && <ActivityTab partner={p} />}

      <ReasonDialog
        open={dialog === 'suspend'}
        onClose={() => setDialog(null)}
        danger
        title={`Suspend ${p.businessName}?`}
        message="They will be logged out, cannot send messages or use the API, and running campaigns will be paused."
        confirmLabel="Suspend"
        placeholder="e.g. Payment overdue, policy violation"
        loading={statusM.isPending}
        onConfirm={(reason) => statusM.mutate({ status: 'SUSPENDED', reason })}
      />
      <ConfirmDialog
        open={dialog === 'activate'}
        onClose={() => setDialog(null)}
        title={`Activate ${p.businessName}?`}
        message="They can log in and send messages again. Paused campaigns stay paused until the partner resumes them."
        confirmLabel="Activate"
        loading={statusM.isPending}
        onConfirm={() => statusM.mutate({ status: 'ACTIVE', reason: 'Reactivated by admin' })}
      />
      <ConfirmDialog
        open={dialog === 'reset'}
        onClose={() => setDialog(null)}
        title="Send a new password?"
        message={`A temporary password will be emailed to ${p.email}. Their current sessions end and they must choose a new password after logging in.`}
        confirmLabel="Send password"
        loading={resetM.isPending}
        onConfirm={() => resetM.mutate()}
      />
      <ReasonDialog
        open={dialog === 'loginAs'}
        onClose={() => setDialog(null)}
        title={`Log in as ${p.businessName}?`}
        message="You will see the partner panel exactly as they do. Everything you do is recorded in the audit log. Use Exit in the purple banner to come back."
        confirmLabel="Log in as partner"
        label="Why do you need access?"
        placeholder="e.g. Helping with campaign setup (support ticket #123)"
        loading={loginAsM.isPending}
        onConfirm={(reason) => loginAsM.mutate(reason)}
      />
    </>
  );
}

function MiniStat({ label, value }) {
  return (
    <div className="card p-3">
      <p className="text-xs text-muted">{label}</p>
      <p className="mt-1 text-lg font-semibold text-ink">{value}</p>
    </div>
  );
}
