import { useEffect, useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Badge, Button, Card, ConfirmDialog, ErrorState, Input, PasswordInput, RadioCards, Select, Skeleton, Switch, Textarea } from '../../../../components/ui';
import * as settingsApi from '../../../../api/endpoints/settings';
import { useApiMutation } from '../../shared/useApiMutation';
import { SETTINGS_FIELDS, buildSchema, toBody, toFormValues } from './settingsSchema';

/** Generic form for one settings group (ST1/ST2). `extra(data)` renders group-specific tools below. */
export default function SettingsGroupForm({ group, title, subtitle, extra, confirmMessage }) {
  const fields = SETTINGS_FIELDS[group];
  const schema = useMemo(() => buildSchema(fields), [fields]);
  const q = useQuery({ queryKey: ['admin', 'settings', group], queryFn: () => settingsApi.getSettings(group) });
  const [pending, setPending] = useState(null);
  const { register, handleSubmit, reset, watch, setValue, formState: { errors, isDirty } } = useForm({ resolver: zodResolver(schema), defaultValues: toFormValues(fields, {}) });
  useEffect(() => {
    if (q.data) reset(toFormValues(fields, q.data));
  }, [q.data, reset, fields]);
  const save = useApiMutation({
    mutationFn: (body) => settingsApi.updateSettings(group, body),
    success: 'Settings saved',
    invalidate: [['admin', 'settings', group]],
    onSuccess: () => setPending(null),
  });
  const submit = handleSubmit((v) => {
    const body = toBody(fields, v, q.data);
    if (Object.keys(body).length === 0) return;
    if (confirmMessage) setPending(body);
    else save.mutate(body);
  });
  if (q.isLoading) return <Skeleton className="h-64" />;
  if (q.isError) return <ErrorState error={q.error} onRetry={q.refetch} />;
  const data = q.data || {};
  return (
    <>
      <Card title={title} subtitle={subtitle}>
        <form onSubmit={submit} noValidate className="space-y-4">
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
            {fields.map((f) => {
              const wide = ['radio', 'textarea', 'switch'].includes(f.type) ? 'md:col-span-2' : '';
              if (f.type === 'switch') {
                return <Switch key={f.key} className={wide} checked={watch(f.key)} onChange={(v) => setValue(f.key, v, { shouldDirty: true })} label={f.label} description={f.hint} />;
              }
              if (f.type === 'radio') {
                return (
                  <div key={f.key} className={wide}>
                    <RadioCards label={f.label} name={f.key} columns={f.options.length > 2 ? 3 : 2} value={watch(f.key)} onChange={(v) => setValue(f.key, v, { shouldDirty: true })} options={f.options} />
                  </div>
                );
              }
              if (f.type === 'select') {
                return <Select key={f.key} label={f.label} hint={f.hint} placeholder="Choose" options={f.options} error={errors[f.key]} {...register(f.key)} />;
              }
              if (f.type === 'textarea') {
                return <Textarea key={f.key} className={wide} label={f.label} rows={3} error={errors[f.key]} {...register(f.key)} />;
              }
              if (f.type === 'secret') {
                const set = data[`${f.key}Set`];
                return (
                  <PasswordInput
                    key={f.key}
                    label={f.label}
                    autoComplete="off"
                    placeholder={set ? 'Saved — type to replace' : 'Not set'}
                    hint={set ? <span className="inline-flex items-center gap-1"><Badge tone="green">Saved</Badge> Leave empty to keep it.</span> : 'Stored encrypted. Never shown again.'}
                    {...register(f.key)}
                  />
                );
              }
              return (
                <Input
                  key={f.key}
                  label={f.label}
                  required={f.required}
                  type={f.type === 'email' ? 'email' : 'text'}
                  inputMode={f.type === 'number' ? 'decimal' : undefined}
                  placeholder={f.placeholder}
                  hint={f.hint}
                  error={errors[f.key]}
                  {...register(f.key)}
                />
              );
            })}
          </div>
          <div className="flex justify-end">
            <Button type="submit" disabled={!isDirty} loading={save.isPending}>Save</Button>
          </div>
        </form>
      </Card>
      {extra?.(data)}
      <ConfirmDialog
        open={Boolean(pending)}
        onClose={() => setPending(null)}
        title="Save these settings?"
        message={confirmMessage}
        confirmLabel="Save"
        loading={save.isPending}
        onConfirm={() => save.mutate(pending)}
      />
    </>
  );
}
