import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Button, Input, Modal } from '../../../../components/ui';
import { istLocalToIso, nowIstLocal } from '../../campaigns/components/schedule';
import { formatDateTime, formatRelative } from '../../../../lib/format';
import { isoToIstLocal, scheduleTimeSchema } from './scheduleTime';

const schema = z.object({ at: scheduleTimeSchema });

/**
 * Pick a date + time in India time (IST). onSubmit(isoUtc).
 * `notice(iso)` can render a warning for the chosen time (e.g. chat window closed).
 */
export default function ScheduleTimeDialog({ open, onClose, title = 'Choose date and time', description, initialIso, submitLabel = 'Save', onSubmit, loading, notice, children }) {
  const { register, handleSubmit, reset, watch, formState } = useForm({ resolver: zodResolver(schema), defaultValues: { at: '' } });
  useEffect(() => {
    if (open) reset({ at: initialIso ? isoToIstLocal(initialIso) : nowIstLocal(60) });
  }, [open, initialIso, reset]);
  const at = watch('at');
  const iso = istLocalToIso(at);
  return (
    <Modal
      open={open}
      onClose={onClose}
      title={title}
      description={description}
      size="sm"
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button loading={loading} onClick={handleSubmit((v) => onSubmit(istLocalToIso(v.at)))}>{submitLabel}</Button>
        </>
      }
    >
      <div className="space-y-3">
        {children}
        <Input
          type="datetime-local"
          label="Date and time (India time)"
          min={nowIstLocal(1)}
          max={nowIstLocal(90 * 24 * 60)}
          error={formState.errors.at?.message}
          hint={iso ? `${formatDateTime(iso)} · ${formatRelative(iso)}` : 'Between 1 minute and 90 days from now.'}
          {...register('at')}
        />
        {iso && notice?.(iso)}
      </div>
    </Modal>
  );
}
