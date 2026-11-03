import { useEffect } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Save, Sparkles } from 'lucide-react';
import toast from 'react-hot-toast';
import * as aiApi from '../../../../api/endpoints/ai';
import { getErrorMessage } from '../../../../api/axios';
import { Alert, Button, Card, Checkbox, Input, PhoneInput, RadioCards, Select, Switch, Textarea, isE164Digits } from '../../../../components/ui';
import { formatMoney } from '../../../../lib/format';
import TagInput from '../../common/TagInput';
import { senderLabel, useMySenders } from '../../common/useSenders';
import { assistantToForm, formToAssistant } from './aiHelpers';

const schema = z
  .object({
    enabled: z.boolean(),
    allNumbers: z.boolean(),
    senderIds: z.array(z.coerce.number()),
    businessName: z.string().trim().max(120, 'Up to 120 characters'),
    instructions: z.string().max(2000, 'Up to 2000 characters'),
    language: z.enum(['AUTO', 'ENGLISH', 'HINDI', 'HINGLISH']),
    tone: z.enum(['FRIENDLY', 'PROFESSIONAL', 'SHORT']),
    fallbackMessage: z.string().trim().max(1000, 'Up to 1000 characters'),
    handoverKeywords: z.array(z.string()),
    maxRepliesPerChatPerDay: z.coerce.number({ invalid_type_error: 'Enter a number' }).int('Whole number').min(1, 'At least 1').max(200, 'At most 200'),
    supportNumber: z.string().refine((v) => !v || isE164Digits(v), 'Enter a valid phone number'),
    allowedTopics: z.string().max(500, 'Up to 500 characters'),
    pauseOnFallback: z.boolean(),
    usePackages: z.boolean(),
  })
  .superRefine((v, ctx) => {
    if (v.enabled && !v.fallbackMessage && !v.supportNumber) ctx.addIssue({ path: ['fallbackMessage'], code: 'custom', message: 'Add a fallback message or a support number' });
    if (!v.allNumbers && !v.senderIds.length) ctx.addIssue({ path: ['senderIds'], code: 'custom', message: 'Pick at least one number' });
  });

const LANGUAGES = [
  { value: 'AUTO', label: 'Same as the customer' },
  { value: 'ENGLISH', label: 'English' },
  { value: 'HINDI', label: 'Hindi' },
  { value: 'HINGLISH', label: 'Hinglish' },
];
const TONES = [
  { value: 'FRIENDLY', label: 'Friendly' },
  { value: 'PROFESSIONAL', label: 'Professional' },
  { value: 'SHORT', label: 'Very short' },
];

export default function SetupTab({ assistant }) {
  const qc = useQueryClient();
  const senders = useMySenders();
  const { control, register, handleSubmit, reset, watch, formState } = useForm({ resolver: zodResolver(schema), defaultValues: assistantToForm(assistant) });
  useEffect(() => reset(assistantToForm(assistant)), [assistant, reset]);
  const enabled = watch('enabled');
  const allNumbers = watch('allNumbers');
  const errors = formState.errors;

  const save = useMutation({
    mutationFn: (v) => aiApi.updateAssistant(formToAssistant(v)),
    onSuccess: (data) => {
      toast.success(data?.enabled ? 'Saved. AI will reply to new customer messages.' : 'Saved');
      qc.setQueryData(['partner', 'ai', 'assistant'], data);
      qc.invalidateQueries({ queryKey: ['partner', 'inbox', 'conversations'] });
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });

  return (
    <form onSubmit={handleSubmit((v) => save.mutate(v))} className="space-y-4" noValidate>
      {!assistant.platformEnabled && (
        <Alert variant="warning" title="AI is not switched on by the admin yet">
          You can set everything up and add your information now. Replies start once the admin switches AI on.
        </Alert>
      )}
      <Alert variant="info" title="How it works">
        The AI answers <strong>only</strong> from your Knowledge, your Travel packages and the customer’s own bookings and passes — read
        fresh from the database for every reply. If it does not know, or the question is about something else, it politely shares your
        support number. It replies only within 24 hours of the customer’s message.
      </Alert>

      <Card>
        <Controller
          name="enabled"
          control={control}
          render={({ field }) => (
            <Switch
              checked={field.value}
              onChange={field.onChange}
              label="Reply to customers with AI"
              description={`Each AI reply costs ${formatMoney(assistant.feePerReply, { precise: true })} from your wallet.`}
            />
          )}
        />
      </Card>

      <Card title="Which numbers">
        <Controller
          name="allNumbers"
          control={control}
          render={({ field }) => (
            <RadioCards
              name="ai-numbers"
              columns={2}
              value={field.value ? 'all' : 'some'}
              onChange={(v) => field.onChange(v === 'all')}
              options={[
                { value: 'all', label: 'All my numbers' },
                { value: 'some', label: 'Only some numbers' },
              ]}
            />
          )}
        />
        {!allNumbers && (
          <Controller
            name="senderIds"
            control={control}
            render={({ field }) => (
              <div className="mt-3">
                {(senders.data || []).map((s) => (
                  <Checkbox
                    key={s.id}
                    label={senderLabel(s)}
                    checked={field.value.map(Number).includes(s.id)}
                    onChange={(e) => field.onChange(e.target.checked ? [...field.value, s.id] : field.value.filter((x) => Number(x) !== s.id))}
                  />
                ))}
                {errors.senderIds && <p className="mt-1 text-sm text-red-600">{errors.senderIds.message}</p>}
              </div>
            )}
          />
        )}
      </Card>

      <Card title="About your business">
        <div className="space-y-4">
          <Input label="Business name" maxLength={120} placeholder="e.g. Sharma Electronics" error={errors.businessName?.message} {...register('businessName')} />
          <Textarea
            label="Extra instructions for the AI (optional)"
            rows={3}
            maxLength={2000}
            showCount
            value={watch('instructions')}
            placeholder="e.g. Always share our shop timing. Do not promise home delivery."
            error={errors.instructions?.message}
            {...register('instructions')}
          />
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <Select label="Reply language" options={LANGUAGES} {...register('language')} />
            <Select label="Tone" options={TONES} {...register('tone')} />
          </div>
        </div>
      </Card>

      <Card title="What the AI may talk about">
        <div className="space-y-4">
          <Input
            label="Allowed topics"
            maxLength={500}
            placeholder="Umrah and Hajj packages, tours, bookings and our services"
            hint="Questions about anything else get your support number instead of an answer."
            error={errors.allowedTopics?.message}
            {...register('allowedTopics')}
          />
          <Controller
            name="usePackages"
            control={control}
            render={({ field }) => (
              <Switch checked={field.value} onChange={field.onChange} label="Use my travel packages and bookings"
                      description="Prices, dates and seats left are read live from Travel packages for every answer. Each customer only hears about their own bookings." />
            )}
          />
        </div>
      </Card>

      <Card title="When AI does not know">
        <div className="space-y-4">
          <Controller
            name="supportNumber"
            control={control}
            render={({ field }) => (
              <PhoneInput label="Customer support number" value={field.value} onChange={field.onChange} error={errors.supportNumber?.message}
                          hint="Shared politely whenever the AI cannot help (other topics, unknown answers, “talk to a person”)." />
            )}
          />
          <Textarea
            label="Fallback message"
            rows={2}
            maxLength={1000}
            required={enabled}
            value={watch('fallbackMessage')}
            placeholder="Thanks for your message! Our team will reply to you shortly."
            hint="Sent when the answer is not in your information. Then the chat is handed to you."
            error={errors.fallbackMessage?.message}
            {...register('fallbackMessage')}
          />
          <Controller
            name="handoverKeywords"
            control={control}
            render={({ field }) => (
              <TagInput
                label="Hand over to me when the customer says"
                value={field.value}
                onChange={field.onChange}
                suggestions={['agent', 'human', 'call me', 'complaint', 'manager']}
                transform={(s) => s.toLowerCase()}
                hint="AI stops replying in that chat and it shows “Needs you” in the inbox."
              />
            )}
          />
          <Controller
            name="pauseOnFallback"
            control={control}
            render={({ field }) => (
              <Switch checked={field.value} onChange={field.onChange} label="Stop AI in that chat after it hands over"
                      description="Off = the AI keeps answering the next questions in that chat." />
            )}
          />
          <Input
            type="number"
            inputMode="numeric"
            min={1}
            label="Max AI replies per chat per day"
            hint="Keeps costs low. After this, the chat is handed to you."
            error={errors.maxRepliesPerChatPerDay?.message}
            {...register('maxRepliesPerChatPerDay')}
          />
        </div>
      </Card>

      <div className="flex justify-end">
        <Button type="submit" leftIcon={enabled ? Sparkles : Save} loading={save.isPending}>Save</Button>
      </div>
    </form>
  );
}
