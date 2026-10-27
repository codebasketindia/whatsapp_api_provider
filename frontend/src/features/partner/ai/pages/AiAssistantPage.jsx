import { useQuery } from '@tanstack/react-query';
import * as aiApi from '../../../../api/endpoints/ai';
import { Badge, PageHeader, QueryState, Tabs, useActiveTab } from '../../../../components/ui';
import SetupTab from '../components/SetupTab';
import KnowledgeTab from '../components/KnowledgeTab';
import TestTab from '../components/TestTab';
import UsageTab from '../components/UsageTab';

const TABS = [
  { value: 'setup', label: 'Setup' },
  { value: 'knowledge', label: 'Knowledge' },
  { value: 'test', label: 'Test' },
  { value: 'usage', label: 'Usage' },
];

export default function AiAssistantPage() {
  const tab = useActiveTab(TABS);
  const q = useQuery({ queryKey: ['partner', 'ai', 'assistant'], queryFn: aiApi.getAssistant });
  const a = q.data;
  const on = a?.enabled && a?.platformEnabled;
  return (
    <>
      <PageHeader
        title="AI Assistant"
        subtitle="Let AI reply to customers on WhatsApp using only the information you give it."
        actions={a && <Badge tone={on ? 'green' : 'gray'} dot>{on ? 'Replying' : 'Off'}</Badge>}
      />
      <Tabs tabs={TABS} urlParam="tab" className="mb-5" />
      <QueryState query={q}>
        {(assistant) => (
          <>
            {tab === 'setup' && <SetupTab assistant={assistant} />}
            {tab === 'knowledge' && <KnowledgeTab />}
            {tab === 'test' && <TestTab assistant={assistant} />}
            {tab === 'usage' && <UsageTab />}
          </>
        )}
      </QueryState>
    </>
  );
}
