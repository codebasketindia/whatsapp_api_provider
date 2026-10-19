import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate, useParams } from 'react-router-dom';
import { Link2Off, RefreshCw, RotateCcw, Star } from 'lucide-react';
import toast from 'react-hot-toast';
import * as sendersApi from '../../../../api/endpoints/senders';
import { senderTools } from '../../../../api/endpoints/senderTools';
import { getErrorMessage } from '../../../../api/axios';
import {
  Alert, Badge, Button, Card, ConfirmDialog, ErrorState, InfoTip, KeyValue, PageHeader, PageLoader, StatusBadge, Tabs, useActiveTab,
} from '../../../../components/ui';
import QualityBadge from '../../../../components/whatsapp/QualityBadge';
import { formatDateTime, formatNumber, humanize } from '../../../../lib/format';
import { Checklist, StepsList } from '../components/signupUi';
import { signupFailureText, useEmbeddedSignup } from '../components/useEmbeddedSignup';
import ProfileTab from '../components/tabs/ProfileTab';
import VerificationTab from '../components/tabs/VerificationTab';
import HealthTab from '../components/tabs/HealthTab';
import QrCodesTab from '../components/tabs/QrCodesTab';
import ConversationTab from '../components/tabs/ConversationTab';
import BlockListTab from '../components/tabs/BlockListTab';

const tools = senderTools('partner');

function Overview({ sender, checklist }) {
  const qc = useQueryClient();
  const navigate = useNavigate();
  const own = sender.senderType === 'PARTNER_OWN_SENDER';
  const tokenBad = ['INVALID', 'EXPIRED'].includes(sender.waba?.tokenStatus);
  const [disconnectOpen, setDisconnectOpen] = useState(false);
  const [reconnectSteps, setReconnectSteps] = useState(null);
  const { run, running } = useEmbeddedSignup();
  const cfg = useQuery({ queryKey: ['partner', 'embedded-signup', 'config'], queryFn: sendersApi.getEmbeddedSignupConfig, enabled: own, retry: false });

  const refresh = () => qc.invalidateQueries({ queryKey: ['partner', 'senders'] });
  const makeDefault = useMutation({
    mutationFn: () => sendersApi.setDefaultSender(sender.id),
    onSuccess: () => {
      toast.success('This is now your default number');
      refresh();
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });
  const sync = useMutation({
    mutationFn: () => tools.sync(sender.id),
    onSuccess: () => {
      toast.success('Latest details loaded from Meta');
      refresh();
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });
  const disconnect = useMutation({
    mutationFn: () => sendersApi.disconnectMySender(sender.id),
    onSuccess: () => {
      toast.success('Number disconnected. Your message history is kept.');
      setDisconnectOpen(false);
      refresh();
      navigate('/partner/senders');
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });
  const reconnect = useMutation({
    mutationFn: (body) => sendersApi.reconnectSender(sender.id, body),
    onSuccess: (data) => {
      setReconnectSteps(data?.steps || []);
      toast.success('Number reconnected');
      refresh();
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });
  const startReconnect = async () => {
    if (!cfg.data) {
      toast.error('Reconnect is not available right now');
      return;
    }
    const r = await run(cfg.data, sender.isCoexistence ? 'COEXISTENCE' : 'EXISTING');
    if (!r.ok) {
      toast.error(signupFailureText(r));
      return;
    }
    reconnect.mutate({ code: r.code, wabaId: r.session.wabaId, phoneNumberId: r.session.phoneNumberId, businessId: r.session.businessId });
  };

  return (
    <div className="space-y-5">
      {tokenBad && (
        <Alert
          variant="error"
          title="Access to this number has expired"
          action={own && <Button size="sm" leftIcon={RotateCcw} onClick={startReconnect} loading={running || reconnect.isPending}>Reconnect</Button>}
        >
          {own ? 'Reconnect with Facebook to continue sending messages.' : 'We are fixing this. Please contact support if it continues.'}
        </Alert>
      )}
      {sender.isPaused && (
        <Alert variant="warning" title="Sending is paused">{sender.pauseReason || 'This number is paused.'}</Alert>
      )}
      {reconnectSteps && <Card title="Reconnect result"><StepsList steps={reconnectSteps} /></Card>}
      <Card
        title="Overview"
        actions={
          <>
            <Button size="sm" variant="secondary" leftIcon={RefreshCw} onClick={() => sync.mutate()} loading={sync.isPending}>Refresh from Meta</Button>
            {!sender.isDefault && sender.status === 'CONNECTED' && (
              <Button size="sm" variant="secondary" leftIcon={Star} onClick={() => makeDefault.mutate()} loading={makeDefault.isPending}>Make default</Button>
            )}
          </>
        }
      >
        <KeyValue
          columns={3}
          items={[
            { label: 'Display name', value: sender.verifiedName },
            { label: 'Phone number', value: sender.displayPhoneNumber },
            { label: 'Status', value: <StatusBadge status={sender.isPaused ? 'PAUSED' : sender.status} /> },
            { label: 'Quality', value: <QualityBadge rating={sender.qualityRating} /> },
            { label: 'Messaging limit', value: sender.messagingLimitTier ? humanize(sender.messagingLimitTier) : '—' },
            { label: 'Display name review', value: <StatusBadge status={sender.nameStatus || 'UNKNOWN'} /> },
            { label: 'Type', value: own ? 'My own number' : 'Provided by us' },
            { label: 'Sent today', value: sender.dailySendCap ? `${formatNumber(sender.sentToday)} of ${formatNumber(sender.dailySendCap)}` : formatNumber(sender.sentToday) },
            { label: 'Connected on', value: formatDateTime(sender.connectedAt) },
            { label: 'WhatsApp Business Account', value: sender.waba?.name || sender.waba?.wabaId },
            { label: 'Phone number ID', value: <span className="font-mono text-xs">{sender.phoneNumberId}</span> },
            { label: 'Last refreshed', value: formatDateTime(sender.lastSyncedAt) },
          ]}
        />
        <p className="mt-4 flex flex-wrap items-center gap-3 text-xs text-muted">
          <span className="inline-flex items-center gap-1">Quality <InfoTip term="QUALITY_RATING" /></span>
          <span className="inline-flex items-center gap-1">Messaging limit <InfoTip term="MESSAGING_LIMIT" /></span>
          <span className="inline-flex items-center gap-1">Display name <InfoTip term="DISPLAY_NAME" /></span>
          {sender.isCoexistence && <Badge tone="blue">Also used in WhatsApp Business App</Badge>}
        </p>
      </Card>
      {checklist?.items?.length > 0 && (
        <Card title="What to do next">
          <Checklist checklist={checklist} />
        </Card>
      )}
      {own && (
        <Card title="Connection">
          <div className="flex flex-wrap gap-2">
            <Button variant="secondary" leftIcon={RotateCcw} onClick={startReconnect} loading={running || reconnect.isPending}>Reconnect with Facebook</Button>
            <Button variant="danger-outline" leftIcon={Link2Off} onClick={() => setDisconnectOpen(true)}>Disconnect number</Button>
          </div>
          <p className="mt-2 text-xs text-muted">Disconnecting stops sending and receiving here. Your message history stays available.</p>
        </Card>
      )}
      <ConfirmDialog
        open={disconnectOpen}
        onClose={() => setDisconnectOpen(false)}
        onConfirm={() => disconnect.mutate()}
        loading={disconnect.isPending}
        danger
        title="Disconnect this number?"
        message="Messages, campaigns and the inbox stop working for this number."
        confirmWord="DISCONNECT"
        confirmLabel="Disconnect"
      />
    </div>
  );
}

export default function MySenderDetailPage() {
  const { id } = useParams();
  const q = useQuery({ queryKey: ['partner', 'senders', id], queryFn: () => sendersApi.getMySender(id) });
  const raw = q.data;
  const sender = raw?.sender || raw;
  const checklist = raw?.checklist;
  const own = sender?.senderType === 'PARTNER_OWN_SENDER';
  const tabs = [
    { value: 'overview', label: 'Overview' },
    { value: 'profile', label: 'Business profile' },
    { value: 'verification', label: 'Verification & PIN', hidden: !own },
    { value: 'health', label: 'Health' },
    { value: 'qr', label: 'QR codes' },
    { value: 'welcome', label: 'Welcome & ice breakers' },
    { value: 'blocked', label: 'Block list' },
  ];
  const tab = useActiveTab(tabs);
  if (q.isLoading) return <PageLoader />;
  if (q.isError) return <ErrorState error={q.error} onRetry={q.refetch} />;
  const disconnected = sender.status === 'DISCONNECTED';
  return (
    <>
      <PageHeader
        title={sender.verifiedName || sender.displayPhoneNumber}
        subtitle={sender.displayPhoneNumber}
        backTo="/partner/senders"
        backLabel="My numbers"
      />
      {disconnected && <Alert variant="info" className="mb-4">This number is disconnected. You can still see its history.</Alert>}
      <Tabs tabs={tabs} urlParam="tab" className="mb-5" />
      {tab === 'overview' && <Overview sender={sender} checklist={checklist} />}
      {tab === 'profile' && <ProfileTab tools={tools} sender={sender} readOnly={disconnected} />}
      {tab === 'verification' && own && <VerificationTab tools={tools} sender={sender} />}
      {tab === 'health' && <HealthTab tools={tools} sender={sender} />}
      {tab === 'qr' && <QrCodesTab tools={tools} sender={sender} readOnly={disconnected} />}
      {tab === 'welcome' && <ConversationTab tools={tools} sender={sender} readOnly={disconnected} />}
      {tab === 'blocked' && <BlockListTab tools={tools} sender={sender} readOnly={disconnected} />}
    </>
  );
}
