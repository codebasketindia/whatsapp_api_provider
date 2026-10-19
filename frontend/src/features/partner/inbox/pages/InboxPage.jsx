import { MessagesSquare, Wifi, WifiOff } from 'lucide-react';
import { EmptyState, PageHeader } from '../../../../components/ui';
import { useIsDesktop } from '../../../../hooks/useMediaQuery';
import ConversationList from '../components/ConversationList';
import { useInboxStream } from '../components/useInboxStream';

export function LiveBadge({ live }) {
  return (
    <span className="inline-flex items-center gap-1 text-xs text-muted" title={live ? 'Live updates on' : 'Refreshing every 10 seconds'}>
      {live ? <Wifi className="h-3.5 w-3.5 text-green-600" /> : <WifiOff className="h-3.5 w-3.5" />} {live ? 'Live' : 'Auto refresh'}
    </span>
  );
}

export default function InboxPage() {
  const { live } = useInboxStream();
  const desktop = useIsDesktop();
  return (
    <>
      <PageHeader title="Inbox" subtitle="Chats with your customers" actions={<LiveBadge live={live} />} />
      <div className="card grid grid-cols-1 h-[calc(100dvh-13rem)] min-h-[420px] overflow-hidden md:grid-cols-[340px_minmax(0,1fr)]">
        <ConversationList live={live} className="md:border-r md:border-line" />
        {desktop && (
          <div className="wa-wallpaper flex items-center justify-center">
            <EmptyState icon={MessagesSquare} title="Choose a chat" text="Replies are free within 24 hours of the customer's last message. After that, use a template." />
          </div>
        )}
      </div>
    </>
  );
}
