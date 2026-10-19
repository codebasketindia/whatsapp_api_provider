import { useParams } from 'react-router-dom';
import { Card, PageHeader } from '../../../../components/ui';
import MessageDetail from '../components/MessageDetail';

export default function MessageDetailPage() {
  const { uid } = useParams();
  return (
    <>
      <PageHeader title="Message details" backTo="/admin/messages" backLabel="Message logs" />
      <Card>
        <MessageDetail uid={uid} />
      </Card>
    </>
  );
}
