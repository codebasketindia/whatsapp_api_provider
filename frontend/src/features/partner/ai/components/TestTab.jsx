import { useEffect, useRef, useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { Bot, Eraser, Send, UserRound } from 'lucide-react';
import { getErrorCode, getErrorMessage } from '../../../../api/axios';
import * as aiApi from '../../../../api/endpoints/ai';
import { Alert, Button, Card, Textarea } from '../../../../components/ui';
import { useIsDesktop } from '../../../../hooks/useMediaQuery';
import { cn } from '../../../../lib/cn';
import { formatNumber } from '../../../../lib/format';
import KnowledgeMedia from './KnowledgeMedia';
import { buildAiHistory } from './aiHelpers';

let seq = 0;
const nextId = () => {
  seq += 1;
  return seq;
};

function Bubble({ m }) {
  const out = m.role === 'business';
  return (
    <div className={cn('flex', out ? 'justify-end' : 'justify-start')}>
      <div
        className={cn(
          'max-w-[85%] rounded-lg px-2.5 pb-1.5 pt-2 text-[14px] leading-[19px] shadow-sm sm:max-w-[70%]',
          out ? 'rounded-tr-none bg-[#d9fdd3] text-[#111b21] dark:bg-[#005c4b] dark:text-[#e9edef]' : 'rounded-tl-none bg-white text-[#111b21] dark:bg-[#202c33] dark:text-[#e9edef]',
          m.error && 'ring-1 ring-red-400',
        )}
      >
        {out && <p className="mb-1 flex items-center gap-1 text-[11px] font-semibold uppercase tracking-wide opacity-60"><Bot className="h-3 w-3" aria-hidden /> AI</p>}
        {m.pending ? <span className="inline-block animate-pulse">typing…</span> : <p className="whitespace-pre-wrap break-words">{m.text}</p>}
        {(m.attachments || []).filter((a) => a.media).map((a) => (
          <div key={a.id} className="mt-1.5">
            <KnowledgeMedia media={a.media} />
            <p className="mt-0.5 text-[11px] opacity-60">From “{a.title}”</p>
          </div>
        ))}
        {m.handover && (
          <p className="mt-1.5 inline-flex items-center gap-1 rounded-full bg-amber-100 px-2 py-0.5 text-[11px] font-medium text-amber-900 dark:bg-amber-500/20 dark:text-amber-200">
            <UserRound className="h-3 w-3" aria-hidden /> Handed over to human{m.reason ? ` · ${m.reason}` : ''}
          </p>
        )}
        {m.tokens != null && <p className="mt-0.5 text-right text-[11px] opacity-50">{formatNumber(m.tokens)} tokens</p>}
        {m.error && <p className="mt-1 text-xs text-red-600">{m.error}</p>}
      </div>
    </div>
  );
}

/** Chat playground for AI8: try questions as a customer and see what the AI would reply. Free. */
export default function TestTab({ assistant }) {
  const desktop = useIsDesktop();
  const [chat, setChat] = useState([]);
  const [text, setText] = useState('');
  const bottomRef = useRef(null);
  useEffect(() => bottomRef.current?.scrollIntoView({ block: 'end' }), [chat.length]);

  const ask = useMutation({
    mutationFn: ({ message, history }) => aiApi.testAssistant({ message, history }),
    onSuccess: (res, { pendingId }) => {
      setChat((c) =>
        c.map((m) =>
          m.id === pendingId
            ? { id: m.id, role: 'business', text: res.reply || '', attachments: res.attachments || [], handover: res.handover, reason: res.reason, tokens: (res.inputTokens || 0) + (res.outputTokens || 0) }
            : m,
        ),
      );
    },
    onError: (e, { pendingId, customerId }) => {
      const msg = getErrorCode(e) === 'AI_NOT_AVAILABLE' ? 'AI is not available right now. The admin may not have switched it on.' : getErrorMessage(e);
      setChat((c) => c.filter((m) => m.id !== pendingId).map((m) => (m.id === customerId ? { ...m, error: msg } : m)));
    },
  });

  const send = () => {
    const message = text.trim();
    if (!message || ask.isPending) return;
    const history = buildAiHistory(chat);
    const customerId = nextId();
    const pendingId = nextId();
    setChat((c) => [...c, { id: customerId, role: 'customer', text: message }, { id: pendingId, role: 'business', pending: true }]);
    setText('');
    ask.mutate({ message, history, pendingId, customerId });
  };

  return (
    <div className="grid grid-cols-1 gap-4 lg:grid-cols-[minmax(0,1fr)_280px]">
      <div className="card flex h-[calc(100dvh-16rem)] min-h-[420px] flex-col overflow-hidden">
        <div className="flex items-center justify-between border-b border-line px-3 py-2">
          <p className="text-sm font-medium text-ink">Chat as a customer</p>
          <Button size="sm" variant="ghost" leftIcon={Eraser} onClick={() => setChat([])} disabled={!chat.length || ask.isPending}>Clear chat</Button>
        </div>
        <div className="wa-wallpaper min-h-0 flex-1 space-y-2 overflow-y-auto p-3">
          {!chat.length && (
            <p className="mx-auto mt-8 max-w-xs rounded-lg bg-white/80 p-3 text-center text-sm text-gray-700 dark:bg-black/40 dark:text-gray-200">
              Type a question your customers usually ask, like “What is the price?” or “Where is your shop?”
            </p>
          )}
          {chat.map((m) => <Bubble key={m.id} m={m} />)}
          <div ref={bottomRef} />
        </div>
        <div className="flex items-end gap-2 border-t border-line p-3">
          <Textarea
            aria-label="Customer message"
            rows={1}
            className="flex-1"
            maxLength={1000}
            value={text}
            onChange={(e) => setText(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && !e.shiftKey && desktop) {
                e.preventDefault();
                send();
              }
            }}
            placeholder="Type as the customer"
          />
          <Button leftIcon={Send} onClick={send} loading={ask.isPending} disabled={!text.trim()} aria-label="Send">{desktop ? 'Send' : ''}</Button>
        </div>
      </div>
      <div className="space-y-3">
        <Alert variant="success" title="Tests are free">You can try up to 100 test messages a day. Nothing is sent to WhatsApp and nothing is charged.</Alert>
        {!assistant.platformEnabled && <Alert variant="warning">AI is not switched on by the admin yet, so tests may not work.</Alert>}
        <Card title="Tips" bodyClassName="text-sm text-muted space-y-2">
          <p>Ask something that is <strong>not</strong> in your information — the AI should send your fallback message and hand the chat to you.</p>
          <p>Try a handover word like “{assistant.handoverKeywords?.[0] || 'agent'}”.</p>
          <p>If an answer is wrong, fix the text in the Knowledge tab and test again.</p>
        </Card>
      </div>
    </div>
  );
}
