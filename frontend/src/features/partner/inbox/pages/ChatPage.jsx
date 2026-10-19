import { useEffect, useMemo, useRef, useState } from 'react';
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';
import { ArrowLeft, Clock, FileText, Send } from 'lucide-react';
import toast from 'react-hot-toast';
import * as messagesApi from '../../../../api/endpoints/messages';
import * as templatesApi from '../../../../api/endpoints/templates';
import { getErrorMessage } from '../../../../api/axios';
import { Alert, Avatar, Button, EmptyState, ErrorState, Modal, Select, Skeleton, Spinner, Textarea } from '../../../../components/ui';
import { buildTemplatePayload, templateValuesComplete } from '../../../../components/whatsapp/TemplateBuilder';
import { useIsDesktop } from '../../../../hooks/useMediaQuery';
import { formatPhone, humanize } from '../../../../lib/format';
import ConversationList from '../components/ConversationList';
import ChatBubble from '../components/ChatBubble';
import { useInboxStream } from '../components/useInboxStream';
import { useWindowStatus } from '../../common/useWindowStatus';
import { useMySenders } from '../../common/useSenders';
import TemplateFill from '../../common/TemplateFill';
import MessageComposer from '../../common/MessageComposer';
import { EMPTY_COMPOSER, composerToRequest, composerValid } from '../../common/composer';
import { LiveBadge } from './InboxPage';

function timeLeft(expiresAt) {
  if (!expiresAt) return '';
  const ms = new Date(expiresAt).getTime() - Date.now();
  if (ms <= 0) return 'closed';
  const h = Math.floor(ms / 3600000);
  const m = Math.floor((ms % 3600000) / 60000);
  return h ? `${h} h ${m} min left` : `${m} min left`;
}

function TemplateModal({ open, onClose, sender, waId, onSent }) {
  const [templateId, setTemplateId] = useState('');
  const [values, setValues] = useState({});
  const q = useQuery({
    queryKey: ['partner', 'templates', 'approved', sender?.waba?.id],
    queryFn: () => templatesApi.listMyTemplates({ status: 'APPROVED', wabaId: sender?.waba?.id, size: 100 }),
    enabled: open && Boolean(sender),
  });
  const list = q.data?.content || [];
  const t = list.find((x) => String(x.id) === String(templateId));
  const m = useMutation({
    mutationFn: () => messagesApi.replyToConversation(sender.id, waId, { type: 'template', template: buildTemplatePayload(t, values) }),
    onSuccess: () => {
      toast.success('Template sent');
      setTemplateId('');
      setValues({});
      onSent();
      onClose();
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });
  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Send a template"
      size="lg"
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button onClick={() => m.mutate()} loading={m.isPending} disabled={!t || !templateValuesComplete(t, values)}>Send</Button>
        </>
      }
    >
      {q.isLoading ? (
        <Skeleton className="h-11 w-full" />
      ) : !list.length ? (
        <EmptyState compact title="No approved templates" action={<Button size="sm" to="/partner/templates/new">Create template</Button>} />
      ) : (
        <div className="space-y-4">
          <Select label="Template" value={templateId} onChange={(e) => { setTemplateId(e.target.value); setValues({}); }} options={list.map((x) => ({ value: x.id, label: `${x.name} (${x.language}) · ${humanize(x.category)}` }))} placeholder="Choose a template" />
          {t && <TemplateFill template={t} senderId={sender.id} values={values} onChange={setValues} />}
        </div>
      )}
    </Modal>
  );
}

export default function ChatPage() {
  const { senderId: sid, waId } = useParams();
  const senderId = Number(sid);
  const qc = useQueryClient();
  const desktop = useIsDesktop();
  const { live } = useInboxStream();
  const senders = useMySenders();
  const sender = (senders.data || []).find((s) => s.id === senderId);
  const win = useWindowStatus(senderId, waId);
  const [text, setText] = useState('');
  const [advanced, setAdvanced] = useState(false);
  const [composer, setComposer] = useState(EMPTY_COMPOSER);
  const [templateOpen, setTemplateOpen] = useState(false);
  const bottomRef = useRef(null);

  const msgs = useInfiniteQuery({
    queryKey: ['partner', 'inbox', 'messages', senderId, waId],
    queryFn: ({ pageParam }) => messagesApi.listConversationMessages(senderId, waId, { beforeUid: pageParam, size: 30 }),
    initialPageParam: undefined,
    getNextPageParam: (last) => {
      if (!last?.hasMore || !last.items?.length) return undefined;
      const sorted = [...last.items].sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt));
      return sorted[0].uid;
    },
    refetchInterval: live ? false : 10_000,
  });
  const items = useMemo(() => {
    const all = (msgs.data?.pages || []).flatMap((p) => p.items || []);
    const seen = new Set();
    return all
      .filter((m) => (seen.has(m.uid) ? false : seen.add(m.uid)))
      .sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt));
  }, [msgs.data]);
  const lastUid = items[items.length - 1]?.uid;
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ block: 'end' });
  }, [lastUid]);

  const markRead = useMutation({ mutationFn: () => messagesApi.markConversationRead(senderId, waId, false) });
  useEffect(() => {
    if (lastUid && items[items.length - 1]?.direction === 'INBOUND') {
      markRead.mutate(undefined, { onSuccess: () => qc.invalidateQueries({ queryKey: ['partner', 'inbox', 'conversations'] }) });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [lastUid]);

  const refresh = () => {
    qc.invalidateQueries({ queryKey: ['partner', 'inbox'] });
    qc.invalidateQueries({ queryKey: ['partner', 'wallet'] });
  };
  const reply = useMutation({
    mutationFn: (body) => messagesApi.replyToConversation(senderId, waId, body),
    onSuccess: () => {
      setText('');
      setComposer(EMPTY_COMPOSER);
      setAdvanced(false);
      refresh();
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });
  const sendText = () => {
    if (!text.trim()) return;
    reply.mutate({ type: 'text', text: { body: text.trim(), previewUrl: true } });
  };

  const contactName = items.find((m) => m.contact?.name)?.contact?.name;
  const open = win.open;

  const chat = (
    <div className="flex min-h-0 flex-1 flex-col">
      <div className="flex items-center gap-3 border-b border-line bg-surface px-3 py-2">
        {!desktop && (
          <Link to="/partner/inbox" className="flex h-11 w-11 items-center justify-center rounded-lg hover:bg-surface-2" aria-label="Back to chats">
            <ArrowLeft className="h-5 w-5" />
          </Link>
        )}
        <Avatar name={contactName || waId} />
        <div className="min-w-0 flex-1">
          <p className="truncate font-semibold text-ink">{contactName || formatPhone(waId)}</p>
          <p className="truncate text-xs text-muted">
            {contactName ? `${formatPhone(waId)} · ` : ''}via {sender?.displayPhoneNumber || 'your number'}
          </p>
        </div>
        <span className={`inline-flex shrink-0 items-center gap-1 rounded-full px-2 py-1 text-xs ${open ? 'bg-green-100 text-green-800 dark:bg-green-500/15 dark:text-green-300' : 'bg-gray-100 text-gray-700 dark:bg-gray-500/15 dark:text-gray-300'}`}>
          <Clock className="h-3.5 w-3.5" /> {win.loading ? '…' : open ? timeLeft(win.expiresAt) : 'Window closed'}
        </span>
      </div>
      <div className="wa-wallpaper min-h-0 flex-1 space-y-2 overflow-y-auto p-3">
        {msgs.hasNextPage && (
          <div className="flex justify-center">
            <Button size="sm" variant="secondary" onClick={() => msgs.fetchNextPage()} loading={msgs.isFetchingNextPage}>Load older messages</Button>
          </div>
        )}
        {msgs.isLoading ? (
          <div className="flex justify-center py-10"><Spinner /></div>
        ) : msgs.isError ? (
          <ErrorState error={msgs.error} onRetry={msgs.refetch} />
        ) : !items.length ? (
          <EmptyState compact title="No messages yet" />
        ) : (
          items.map((m) => <ChatBubble key={m.uid} message={m} />)
        )}
        <div ref={bottomRef} />
      </div>
      <div className="border-t border-line bg-surface p-3 safe-bottom">
        {open ? (
          advanced ? (
            <div className="space-y-3">
              <MessageComposer senderId={senderId} value={composer} onChange={setComposer} compact />
              <div className="flex justify-end gap-2">
                <Button variant="secondary" onClick={() => setAdvanced(false)}>Back to text</Button>
                <Button leftIcon={Send} onClick={() => reply.mutate(composerToRequest(composer))} loading={reply.isPending} disabled={!composerValid(composer)}>Send</Button>
              </div>
            </div>
          ) : (
            <div className="flex items-end gap-2">
              <Textarea
                aria-label="Type a message"
                rows={1}
                className="flex-1"
                maxLength={4096}
                value={text}
                onChange={(e) => setText(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' && !e.shiftKey && desktop) {
                    e.preventDefault();
                    sendText();
                  }
                }}
                placeholder="Type a message"
              />
              <Button variant="ghost" onClick={() => setAdvanced(true)} aria-label="Attach file or buttons">More</Button>
              <Button leftIcon={Send} onClick={sendText} loading={reply.isPending} disabled={!text.trim()} aria-label="Send">
                {desktop ? 'Send' : ''}
              </Button>
            </div>
          )
        ) : (
          <Alert
            variant="info"
            title="The 24-hour window is closed"
            action={<Button size="sm" leftIcon={FileText} onClick={() => setTemplateOpen(true)} disabled={!sender}>Send template</Button>}
          >
            You can message this customer only with an approved template until they reply.
          </Alert>
        )}
      </div>
    </div>
  );

  return (
    <>
      {desktop && (
        <div className="mb-3 flex items-center justify-between">
          <h1 className="text-xl font-semibold text-ink">Inbox</h1>
          <LiveBadge live={live} />
        </div>
      )}
      <div className={desktop ? 'card grid h-[calc(100dvh-11rem)] min-h-[480px] overflow-hidden md:grid-cols-[340px_minmax(0,1fr)]' : 'card -mx-4 -mt-4 flex h-[calc(100dvh-8.5rem)] flex-col overflow-hidden rounded-none border-x-0 sm:mx-0 sm:mt-0 sm:rounded-xl sm:border-x'}>
        {desktop && <ConversationList live={live} className="border-r border-line" />}
        {chat}
      </div>
      <TemplateModal open={templateOpen} onClose={() => setTemplateOpen(false)} sender={sender} waId={waId} onSent={refresh} />
    </>
  );
}
