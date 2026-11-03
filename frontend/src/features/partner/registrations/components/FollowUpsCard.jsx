import { useState } from 'react';
import { CalendarClock, Pencil, Plus, Trash2 } from 'lucide-react';
import { Button, Card, EmptyState, IconButton, Input, Modal, RadioCards, Select } from '../../../../components/ui';
import { formatDateTime } from '../../../../lib/format';
import { istLocalToIso, nowIstLocal } from '../../campaigns/components/schedule';
import { isoToIstLocal } from '../../scheduled/components/scheduleTime';
import MessageBuilder from './MessageBuilder';
import { UNITS, delayToMinutes, followUpTiming, messageSummary, minutesToDelay } from './regHelpers';

function FollowUpModal({ open, item, senderId, onClose, onSave }) {
  const d = minutesToDelay(item?.delayMinutes || 1440);
  const [label, setLabel] = useState(item?.label || '');
  const [when, setWhen] = useState(item?.sendAt ? 'date' : 'after');
  const [amount, setAmount] = useState(d.amount);
  const [unit, setUnit] = useState(d.unit);
  const [at, setAt] = useState(item?.sendAt ? isoToIstLocal(item.sendAt) : nowIstLocal(24 * 60));
  const [message, setMessage] = useState(item?.message || null);
  const minutes = delayToMinutes(amount, unit);
  const timingOk = when === 'date' ? Boolean(istLocalToIso(at)) : minutes >= 1 && minutes <= 90 * 1440;
  return (
    <Modal
      open={open}
      onClose={onClose}
      size="xl"
      title={item ? 'Edit follow-up message' : 'Add follow-up message'}
      description="Sent automatically to every person who registers, in the same WhatsApp chat."
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button disabled={!message || !timingOk}
                  onClick={() => onSave({ label: label.trim() || null, delayMinutes: when === 'after' ? minutes : null, sendAt: when === 'date' ? istLocalToIso(at) : null, message })}>
            Save
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        <Input label="Name (for you)" maxLength={60} value={label} placeholder="e.g. Reminder 1 day before" onChange={(e) => setLabel(e.target.value)} />
        <RadioCards name="fu-when" columns={2} value={when} onChange={setWhen}
                    options={[{ value: 'after', label: 'After they register' }, { value: 'date', label: 'On a fixed date & time' }]} />
        {when === 'after' ? (
          <div className="grid grid-cols-2 gap-3">
            <Input type="number" min={1} label="Send after" value={amount} onChange={(e) => setAmount(e.target.value)}
                   error={!timingOk ? 'Between 1 minute and 90 days' : undefined} />
            <Select label="Unit" value={unit} onChange={(e) => setUnit(e.target.value)} options={UNITS.map((u) => ({ value: u.value, label: u.label }))} />
          </div>
        ) : (
          <Input type="datetime-local" label="Send on (India time)" min={nowIstLocal(1)} value={at} onChange={(e) => setAt(e.target.value)}
                 hint="People who register after this time do not get it." />
        )}
        <MessageBuilder senderId={senderId} value={item?.message} onChange={setMessage} />
      </div>
    </Modal>
  );
}

/** Messages scheduled for every new registrant (welcome, reminders, photos, videos…). */
export default function FollowUpsCard({ value = [], onChange, senderId }) {
  const [editing, setEditing] = useState(null); // { index } | { index: -1 }
  const save = (fu) => {
    const next = [...value];
    if (editing.index >= 0) next[editing.index] = fu;
    else next.push(fu);
    onChange(next);
    setEditing(null);
  };
  return (
    <Card title="Follow-up messages" subtitle="Scheduled automatically for each person after they register."
          actions={<Button size="sm" leftIcon={Plus} disabled={!senderId || value.length >= 20} onClick={() => setEditing({ index: -1 })}>Add</Button>}>
      {!value.length ? (
        <EmptyState compact icon={CalendarClock} title="No follow-ups yet" text="e.g. a welcome video after 5 minutes, a reminder 1 day before the event." />
      ) : (
        <ul className="divide-y divide-line">
          {value.map((u, i) => (
            <li key={i} className="flex items-start gap-3 py-3">
              <div className="min-w-0 flex-1">
                <p className="font-medium text-ink">{u.label || `Message ${i + 1}`}</p>
                <p className="text-xs text-muted">{followUpTiming(u, formatDateTime)}</p>
                <p className="mt-1 line-clamp-2 text-sm text-muted">{messageSummary(u.message)}</p>
              </div>
              <IconButton icon={Pencil} label="Edit" onClick={() => setEditing({ index: i })} />
              <IconButton icon={Trash2} label="Delete" onClick={() => onChange(value.filter((_, j) => j !== i))} />
            </li>
          ))}
        </ul>
      )}
      {editing && (
        <FollowUpModal open item={editing.index >= 0 ? value[editing.index] : null} senderId={senderId}
                       onClose={() => setEditing(null)} onSave={save} />
      )}
    </Card>
  );
}
