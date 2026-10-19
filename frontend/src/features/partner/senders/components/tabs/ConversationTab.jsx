import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Plus, Trash2 } from 'lucide-react';
import toast from 'react-hot-toast';
import { getErrorMessage } from '../../../../../api/axios';
import { Button, Card, ErrorState, IconButton, InfoTip, Input, PageLoader, Switch } from '../../../../../components/ui';

/** Welcome message, ice breakers and commands (T17–T18). */
export default function ConversationTab({ tools, sender, readOnly }) {
  const qc = useQueryClient();
  const key = [tools.scope, 'senders', sender.id, 'conversational'];
  const q = useQuery({ queryKey: key, queryFn: () => tools.getConversationalComponents(sender.id) });
  const [form, setForm] = useState(null);
  useEffect(() => {
    if (q.data) {
      setForm({
        enableWelcomeMessage: Boolean(q.data.enableWelcomeMessage),
        prompts: q.data.prompts || [],
        commands: (q.data.commands || []).map((c) => ({ name: c.name ?? c.command_name ?? '', description: c.description ?? c.command_description ?? '' })),
      });
    }
  }, [q.data]);
  const save = useMutation({
    mutationFn: () =>
      tools.updateConversationalComponents(sender.id, {
        enableWelcomeMessage: form.enableWelcomeMessage,
        prompts: form.prompts.map((p) => p.trim()).filter(Boolean),
        commands: form.commands.filter((c) => c.name.trim()).map((c) => ({ name: c.name.trim().replace(/^\//, ''), description: c.description.trim() })),
      }),
    onSuccess: (data) => {
      qc.setQueryData(key, data);
      toast.success('Saved to WhatsApp');
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });
  if (q.isLoading || (!form && !q.isError)) return <PageLoader />;
  if (q.isError) return <ErrorState error={q.error} onRetry={q.refetch} />;
  const set = (patch) => setForm((f) => ({ ...f, ...patch }));
  const promptErr = form.prompts.some((p) => p.length > 80);
  const cmdErr = form.commands.some((c) => c.name.length > 32 || c.description.length > 256 || (c.name && !/^\/?[\w-]+$/.test(c.name)));

  return (
    <fieldset disabled={readOnly} className="space-y-5">
      <Card>
        <Switch
          checked={form.enableWelcomeMessage}
          onChange={(v) => set({ enableWelcomeMessage: v })}
          label={<span className="inline-flex items-center gap-1">Welcome message events <InfoTip term="WELCOME_MESSAGE" /></span>}
          description="We get notified when someone opens a chat with you for the first time"
        />
      </Card>
      <Card title={<span className="inline-flex items-center gap-1">Ice breakers <InfoTip term="ICE_BREAKERS" /></span>} subtitle="Up to 4 questions, 80 characters each">
        <div className="space-y-2">
          {form.prompts.map((p, i) => (
            <div key={i} className="flex gap-2">
              <Input aria-label={`Ice breaker ${i + 1}`} maxLength={80} value={p} onChange={(e) => set({ prompts: form.prompts.map((x, j) => (j === i ? e.target.value : x)) })} placeholder="What are your opening hours?" />
              <IconButton icon={Trash2} label="Remove" variant="danger" onClick={() => set({ prompts: form.prompts.filter((_, j) => j !== i) })} />
            </div>
          ))}
        </div>
        {form.prompts.length < 4 && (
          <Button size="sm" variant="link" leftIcon={Plus} className="mt-2" onClick={() => set({ prompts: [...form.prompts, ''] })}>Add ice breaker</Button>
        )}
      </Card>
      <Card title={<span className="inline-flex items-center gap-1">Commands <InfoTip term="COMMANDS" /></span>} subtitle="Up to 30 commands customers can type after /">
        <div className="space-y-3">
          {form.commands.map((c, i) => (
            <div key={i} className="grid grid-cols-1 gap-2 sm:grid-cols-[180px_minmax(0,1fr)_auto]">
              <Input aria-label="Command" prefix="/" maxLength={32} value={c.name.replace(/^\//, '')} onChange={(e) => set({ commands: form.commands.map((x, j) => (j === i ? { ...x, name: e.target.value } : x)) })} placeholder="help" />
              <Input aria-label="Description" maxLength={256} value={c.description} onChange={(e) => set({ commands: form.commands.map((x, j) => (j === i ? { ...x, description: e.target.value } : x)) })} placeholder="Get help from our team" />
              <IconButton icon={Trash2} label="Remove" variant="danger" onClick={() => set({ commands: form.commands.filter((_, j) => j !== i) })} />
            </div>
          ))}
        </div>
        {form.commands.length < 30 && (
          <Button size="sm" variant="link" leftIcon={Plus} className="mt-2" onClick={() => set({ commands: [...form.commands, { name: '', description: '' }] })}>Add command</Button>
        )}
      </Card>
      {(promptErr || cmdErr) && <p className="text-sm text-red-600">Check the lengths: questions up to 80 characters, commands up to 32 letters (no spaces).</p>}
      {!readOnly && (
        <div className="flex justify-end">
          <Button onClick={() => save.mutate()} loading={save.isPending} disabled={promptErr || cmdErr}>Save</Button>
        </div>
      )}
    </fieldset>
  );
}
