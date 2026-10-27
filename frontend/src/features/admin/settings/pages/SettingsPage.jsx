import { useState } from 'react';
import { Send, Upload, Zap } from 'lucide-react';
import * as settingsApi from '../../../../api/endpoints/settings';
import { Alert, Button, Card, CopyButton, FileUpload, Input, Modal, PageHeader, Tabs, useActiveTab } from '../../../../components/ui';
import { useApiMutation } from '../../shared/useApiMutation';
import SettingsGroupForm from '../components/SettingsGroupForm';

const TABS = [
  { value: 'company', label: 'Company' },
  { value: 'payment-gateway', label: 'Payment gateway' },
  { value: 'gst', label: 'GST & invoice' },
  { value: 'smtp', label: 'Email (SMTP)' },
  { value: 'charging', label: 'Charging' },
  { value: 'wallet', label: 'Wallet' },
  { value: 'rate-limit', label: 'API limits' },
  { value: 'security', label: 'Security' },
  { value: 'ai', label: 'AI' },
  { value: 'retention', label: 'Data retention' },
];

export default function SettingsPage() {
  const tab = useActiveTab(TABS);
  return (
    <>
      <PageHeader title="Settings" subtitle="Platform-wide settings. Every change is recorded in the audit log." />
      <Tabs tabs={TABS} urlParam="tab" className="mb-4" />
      <div className="space-y-4">
        {tab === 'company' && <SettingsGroupForm group="company" title="Company profile" subtitle="Shown on invoices, emails and the login page." extra={(d) => <LogoCard logoUrl={d.logoUrl} />} />}
        {tab === 'payment-gateway' && (
          <SettingsGroupForm
            group="payment-gateway"
            title="Payment gateway"
            subtitle="Lets partners add money with UPI, cards or net banking."
            confirmMessage="Wrong keys stop online payments. Use “Test connection” after saving."
            extra={() => <GatewayTools />}
          />
        )}
        {tab === 'gst' && <SettingsGroupForm group="gst" title="GST & invoices" subtitle="GST invoices are generated automatically as PDF with your logo." />}
        {tab === 'smtp' && <SettingsGroupForm group="smtp" title="Email server" subtitle="Used for login codes, alerts and invoices." extra={() => <SmtpTest />} />}
        {tab === 'charging' && (
          <SettingsGroupForm
            group="charging"
            title="Charging rules"
            subtitle="How partner wallets are charged for messages."
            confirmMessage="The new rule applies to messages sent from now on."
          />
        )}
        {tab === 'wallet' && <SettingsGroupForm group="wallet" title="Wallet" />}
        {tab === 'rate-limit' && <SettingsGroupForm group="rate-limit" title="API & login limits" subtitle="Protects the platform from too many requests." />}
        {tab === 'security' && <SettingsGroupForm group="security" title="Security" subtitle="Login protection for all users." confirmMessage="Security changes apply to the next login of every user." />}
        {tab === 'ai' && (
          <SettingsGroupForm
            group="ai"
            title="AI Assistant"
            subtitle="Partners can let AI answer customers from their own information. Each AI reply is charged from the partner wallet."
            confirmMessage="AI changes apply to the next customer message."
          />
        )}
        {tab === 'retention' && <SettingsGroupForm group="retention" title="Data retention" subtitle="Old data is cleaned up every night." />}
      </div>
    </>
  );
}

function LogoCard({ logoUrl }) {
  const [file, setFile] = useState(null);
  const m = useApiMutation({ mutationFn: () => settingsApi.uploadCompanyLogo(file), success: 'Logo updated', invalidate: [['admin', 'settings', 'company'], ['public', 'branding']], onSuccess: () => setFile(null) });
  const src = logoUrl ? `${import.meta.env.VITE_API_BASE_URL || '/api'}${logoUrl.replace(/^\/api/, '')}` : null;
  return (
    <Card title="Logo" subtitle="PNG or JPG up to 1 MB. Used on invoices, emails and the login page.">
      <div className="flex flex-col gap-4 md:flex-row md:items-start">
        <div className="flex h-24 w-48 items-center justify-center rounded-lg border border-line bg-white p-2">
          {src ? <img src={src} alt="Company logo" className="max-h-full max-w-full object-contain" /> : <span className="text-sm text-gray-500">No logo</span>}
        </div>
        <div className="flex-1">
          <FileUpload value={file} onChange={setFile} accept="image/png,image/jpeg" maxSizeMB={1} />
          <Button className="mt-3" leftIcon={Upload} disabled={!file} loading={m.isPending} onClick={() => m.mutate()}>Upload logo</Button>
        </div>
      </div>
    </Card>
  );
}

function GatewayTools() {
  const [result, setResult] = useState(null);
  const webhookUrl = `${window.location.origin}/api/webhooks/payments/razorpay`;
  const m = useApiMutation({ mutationFn: settingsApi.testPaymentGateway, onSuccess: setResult, onError: () => setResult(null) });
  return (
    <Card title="Razorpay setup" subtitle="In Razorpay Dashboard → Settings → Webhooks, add this URL with events payment.captured, payment.failed and order.paid.">
      <span className="label-base">Webhook URL</span>
      <div className="flex items-center gap-2 rounded-lg border border-line bg-surface-2 px-3 py-2">
        <code className="min-w-0 flex-1 break-all text-sm">{webhookUrl}</code>
        <CopyButton value={webhookUrl} />
      </div>
      <Button className="mt-4" variant="secondary" leftIcon={Zap} loading={m.isPending} onClick={() => m.mutate()}>Test connection</Button>
      {result && (
        <Alert className="mt-3" variant={result.ok ? 'success' : 'error'} title={result.ok ? 'Connected to Razorpay' : 'Connection failed'}>
          {result.message}
        </Alert>
      )}
    </Card>
  );
}

function SmtpTest() {
  const [open, setOpen] = useState(false);
  const [to, setTo] = useState('');
  const m = useApiMutation({ mutationFn: () => settingsApi.testSmtp(to.trim()), success: `Test email sent to ${to}`, onSuccess: () => setOpen(false) });
  const valid = /^\S+@\S+\.\S+$/.test(to.trim());
  return (
    <Card title="Test email">
      <p className="mb-3 text-sm text-muted">Save the settings first, then send a test email to check they work.</p>
      <Button variant="secondary" leftIcon={Send} onClick={() => setOpen(true)}>Send test email</Button>
      <Modal open={open} onClose={() => setOpen(false)} title="Send a test email" size="sm" footer={<><Button variant="secondary" onClick={() => setOpen(false)}>Cancel</Button><Button disabled={!valid} loading={m.isPending} onClick={() => m.mutate()}>Send</Button></>}>
        <Input label="Send to" type="email" value={to} onChange={(e) => setTo(e.target.value)} />
      </Modal>
    </Card>
  );
}
