import { PageHeader, Tabs, useActiveTab } from '../../../../components/ui';
import ApiKeysTab from '../components/ApiKeysTab';
import WebhookTab from '../components/WebhookTab';
import DeliveryLogsTab from '../components/DeliveryLogsTab';
import ApiDocsTab from '../components/ApiDocsTab';

const TABS = [
  { value: 'keys', label: 'API keys' },
  { value: 'webhook', label: 'Webhook' },
  { value: 'logs', label: 'Delivery logs' },
  { value: 'docs', label: 'API docs' },
];

export default function DeveloperPage() {
  const tab = useActiveTab(TABS);
  return (
    <>
      <PageHeader title="Developer API" subtitle="Send WhatsApp messages from your own website, app or software" />
      <Tabs tabs={TABS} urlParam="tab" className="mb-5" />
      {tab === 'keys' && <ApiKeysTab />}
      {tab === 'webhook' && <WebhookTab />}
      {tab === 'logs' && <DeliveryLogsTab />}
      {tab === 'docs' && <ApiDocsTab />}
    </>
  );
}
