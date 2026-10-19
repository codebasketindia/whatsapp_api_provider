import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Plus, Trash2 } from 'lucide-react';
import { glossary } from '../../../../../lib/metaGlossary';
import { Button, Card, IconButton, Input, QueryState, Switch } from '../../../../../components/ui';
import { useApiMutation } from '../../../shared/useApiMutation';

const MAX_PROMPTS = 4;
const MAX_COMMANDS = 30;

/** Welcome message, ice breakers and commands (T17–T18). */
export default function WelcomeTab({ sender, tools, canManage }) {
  const key = [tools.scope, 'senders', sender.id, 'conversational'];
  const q = useQuery({ queryKey: key, queryFn: () => tools.getConversationalComponents(sender.id) });
  const [welcome, setWelcome] = useState(false);
  const [prompts, setPrompts] = useState([]);
  const [commands, setCommands] = useState([]);
  useEffect(() => {
    if (q.data) {
      setWelcome(Boolean(q.data.enableWelcomeMessage));
      setPrompts(q.data.prompts || []);
      setCommands((q.data.commands || []).map((c) => ({ name: c.name ?? c.command_name ?? '', description: c.description ?? c.command_description ?? '' })));
    }
  }, [q.data]);
  const errors = [];
  prompts.forEach((p, i) => { if (!p.trim()) errors.push(`Ice breaker ${i + 1} is empty`); if (p.length > 80) errors.push(`Ice breaker ${i + 1} is longer than 80 characters`); });
  commands.forEach((c, i) => {
    if (!/^[a-z0-9_]{1,32}$/i.test(c.name)) errors.push(`Command ${i + 1}: name must be 1–32 letters, numbers or _`);
    if (!c.description.trim() || c.description.length > 256) errors.push(`Command ${i + 1}: description is required (max 256)`);
  });
  const m = useApiMutation({
    mutationFn: () => tools.updateConversationalComponents(sender.id, { enableWelcomeMessage: welcome, prompts: prompts.map((p) => p.trim()), commands: commands.map((c) => ({ name: c.name.trim(), description: c.description.trim() })) }),
    success: 'Saved on WhatsApp',
    invalidate: [key],
  });
  return (
    <QueryState query={q}>
      {() => (
        <div className="space-y-4">
          <Card title="Welcome message">
            <Switch checked={welcome} disabled={!canManage} onChange={setWelcome} label="Notify me when a customer opens the chat for the first time" description={glossary('WELCOME_MESSAGE')} />
          </Card>
          <Card title="Ice breakers" subtitle={`${glossary('ICE_BREAKERS')} Max 80 characters each.`}>
            <div className="space-y-2">
              {prompts.map((p, i) => (
                <div key={i} className="flex items-end gap-2">
                  <Input className="flex-1" aria-label={`Ice breaker ${i + 1}`} maxLength={80} value={p} disabled={!canManage} onChange={(e) => setPrompts((ps) => ps.map((x, j) => (j === i ? e.target.value : x)))} placeholder="e.g. What are your opening hours?" />
                  {canManage && <IconButton icon={Trash2} label="Remove" onClick={() => setPrompts((ps) => ps.filter((_, j) => j !== i))} />}
                </div>
              ))}
              {canManage && prompts.length < MAX_PROMPTS && <Button size="sm" variant="secondary" leftIcon={Plus} onClick={() => setPrompts((ps) => [...ps, ''])}>Add ice breaker</Button>}
            </div>
          </Card>
          <Card title="Commands" subtitle={glossary('COMMANDS')}>
            <div className="space-y-3">
              {commands.map((c, i) => (
                <div key={i} className="grid grid-cols-1 gap-2 rounded-lg border border-line p-3 sm:grid-cols-[180px_1fr_auto] sm:items-end sm:border-0 sm:p-0">
                  <Input label={i === 0 ? 'Command' : undefined} aria-label="Command" prefix="/" maxLength={32} value={c.name} disabled={!canManage} onChange={(e) => setCommands((cs) => cs.map((x, j) => (j === i ? { ...x, name: e.target.value.replace(/\s/g, '') } : x)))} />
                  <Input label={i === 0 ? 'What it does' : undefined} aria-label="Description" maxLength={256} value={c.description} disabled={!canManage} onChange={(e) => setCommands((cs) => cs.map((x, j) => (j === i ? { ...x, description: e.target.value } : x)))} />
                  {canManage && <IconButton icon={Trash2} label="Remove command" onClick={() => setCommands((cs) => cs.filter((_, j) => j !== i))} />}
                </div>
              ))}
              {canManage && commands.length < MAX_COMMANDS && <Button size="sm" variant="secondary" leftIcon={Plus} onClick={() => setCommands((cs) => [...cs, { name: '', description: '' }])}>Add command</Button>}
            </div>
          </Card>
          {errors.length > 0 && <ul className="list-disc pl-5 text-sm text-red-600">{errors.map((e) => <li key={e}>{e}</li>)}</ul>}
          {canManage && (
            <div className="flex justify-end">
              <Button disabled={errors.length > 0} loading={m.isPending} onClick={() => m.mutate()}>Save</Button>
            </div>
          )}
        </div>
      )}
    </QueryState>
  );
}
