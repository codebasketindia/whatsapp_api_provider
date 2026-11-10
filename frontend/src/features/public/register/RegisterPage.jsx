import { useState } from 'react';
import { useParams } from 'react-router-dom';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { CheckCircle2, ChevronDown, ChevronUp, Download, MessageCircle } from 'lucide-react';
import * as regApi from '../../../api/endpoints/registrations';
import { getErrorMessage, getFieldErrors } from '../../../api/axios';
import { Alert, Button, Checkbox, FileUpload, Input, PhoneInput, Spinner, isIndianMobile } from '../../../components/ui';
import { openCheckout } from '../../../lib/razorpay';
import { formatMoney } from '../../../lib/format';
import { passStatusText } from './registerHelpers';

const schema = z.object({
  name: z.string().trim().min(2, 'Enter your full name').max(80, 'Up to 80 letters').regex(/^[\p{L}\p{M} .'-]+$/u, 'Use letters only'),
  mobile: z.string().refine((v) => isIndianMobile(v), 'Enter your 10-digit WhatsApp number'),
  consent: z.literal(true, { errorMap: () => ({ message: 'Please agree to get your pass on WhatsApp' }) }),
});

const formLightStyles = `
  .register-form-light input,
  .register-form-light select,
  .register-form-light .field-base {
    background-color: #f8f6f0 !important;
    color: #1f2937 !important;
    border-color: #c5b88a !important;
  }
  .register-form-light input::placeholder {
    color: #9ca3af !important;
  }
  .register-form-light label {
    color: #0b5a2e !important;
    font-weight: 500 !important;
  }
  .register-form-light .bg-surface-2 {
    background-color: #e8e4d9 !important;
    color: #1f2937 !important;
  }
  .register-form-light .text-ink {
    color: #374151 !important;
  }
  .register-form-light .text-muted {
    color: #6b7280 !important;
  }
  .register-form-light .border-line {
    border-color: #c5b88a !important;
  }
  .register-form-light input[type="checkbox"] {
    background-color: white !important;
    border-color: #c5b88a !important;
  }
`;

function Shell({ children }) {
  return (
    <>
      <style>{formLightStyles}</style>
      <div className="min-h-screen bg-gradient-to-b from-[#0b5a2e] via-[#0e6b37] to-[#0b5a2e] px-4 py-6 sm:py-10">
        <div className="mx-auto w-full max-w-md overflow-hidden rounded-3xl border-4 border-[#d4af37] bg-[#fffdf5] shadow-2xl">{children}</div>
        <p className="mt-4 text-center text-xs text-white/70">Secured by CB WhatsApp Gateway</p>
      </div>
    </>
  );
}

/** Extract first paragraph as summary and rest as details */
function parseDescription(desc) {
  if (!desc) return { summary: '', details: '' };
  const lines = desc.split('\n').filter((l) => l.trim());
  // Find first empty line or after first paragraph
  const parts = desc.split(/\n\n+/);
  if (parts.length > 1) {
    return { summary: parts[0].trim(), details: parts.slice(1).join('\n\n').trim() };
  }
  // If no clear separation, show first 2 lines as summary
  if (lines.length > 3) {
    return { summary: lines.slice(0, 2).join('\n'), details: lines.slice(2).join('\n') };
  }
  return { summary: desc, details: '' };
}

function CollapsibleDescription({ description }) {
  const [expanded, setExpanded] = useState(false);
  const { summary, details } = parseDescription(description);

  if (!details) {
    return <p className="mt-3 whitespace-pre-line text-left text-sm text-white/90">{description}</p>;
  }

  return (
    <div className="mt-3">
      <p className="whitespace-pre-line text-sm text-white/90">{summary}</p>
      <div
        className={`overflow-hidden transition-all duration-300 ease-in-out ${expanded ? 'max-h-[1000px] opacity-100' : 'max-h-0 opacity-0'}`}
      >
        <div className="mx-auto mt-3 max-w-xs text-left text-sm text-white/90">
          {details.split('\n').filter((l) => l.trim()).map((line, i) => (
            <p key={i} className="py-0.5">{line}</p>
          ))}
        </div>
      </div>
      <button
        type="button"
        onClick={() => setExpanded(!expanded)}
        className="mt-3 inline-flex items-center gap-1 rounded-full bg-white/15 px-4 py-2 text-xs font-medium text-[#f3d77a] backdrop-blur transition-colors hover:bg-white/25"
      >
        {expanded ? (
          <>Hide details <ChevronUp className="h-3.5 w-3.5" /></>
        ) : (
          <>View full details <ChevronDown className="h-3.5 w-3.5" /></>
        )}
      </button>
    </div>
  );
}

/** PR1/PR2: the public link partners share — name + WhatsApp number → free pass on WhatsApp. */
export default function RegisterPage() {
  const { slug } = useParams();
  const form = useQuery({ queryKey: ['public', 'register', slug], queryFn: () => regApi.getPublicForm(slug), retry: false });
  const [photo, setPhoto] = useState(null);
  const [done, setDone] = useState(null);
  const [paying, setPaying] = useState(false);
  const { control, register, handleSubmit, setError, formState } = useForm({
    resolver: zodResolver(schema),
    defaultValues: { name: '', mobile: '', consent: false },
  });
  /** Paid links: Razorpay checkout in the partner's account; the server verifies before the pass is made. */
  const pay = async (res) => {
    const p = res.payment;
    setPaying(true);
    try {
      const out = await openCheckout({
        key: p.keyId,
        order_id: p.orderId,
        amount: p.amount,
        currency: p.currency || 'INR',
        name: p.name,
        description: p.description,
        prefill: { name: res.name, contact: res.mobile },
        theme: { color: '#0b5a2e' },
      });
      if (out.status !== 'success') {
        setError('root', { message: out.status === 'failed' ? `Payment failed: ${out.error?.description || 'please try again'}` : 'Payment was not completed. Tap the button to try again.' });
        return;
      }
      const verified = await regApi.verifyPayment(slug, {
        registrationUid: res.uid,
        razorpayOrderId: out.razorpay_order_id,
        razorpayPaymentId: out.razorpay_payment_id,
        razorpaySignature: out.razorpay_signature,
      });
      setDone(verified);
    } catch (e) {
      setError('root', { message: getErrorMessage(e) });
    } finally {
      setPaying(false);
    }
  };
  const submit = useMutation({
    mutationFn: (v) => regApi.register(slug, { name: v.name.trim(), mobile: v.mobile, consent: true, photo: photo || undefined, website: '' }),
    onSuccess: (res) => (res.paymentStatus === 'PENDING' && res.payment ? pay(res) : setDone(res)),
    onError: (e) => {
      const fields = getFieldErrors(e);
      Object.entries(fields).forEach(([k, msg]) => setError(k, { message: msg }));
      if (!Object.keys(fields).length) setError('root', { message: getErrorMessage(e) });
    },
  });

  if (form.isLoading) {
    return <Shell><div className="flex justify-center p-16"><Spinner /></div></Shell>;
  }
  if (form.isError) {
    return (
      <Shell>
        <div className="p-8 text-center">
          <h1 className="text-xl font-bold text-[#0b5a2e]">Link not found</h1>
          <p className="mt-2 text-sm text-gray-600">Please check the link you received.</p>
        </div>
      </Shell>
    );
  }
  const f = form.data;

  if (done) {
    const passSrc = regApi.publicUrl(done.passUrl);
    return (
      <Shell>
        <div className="p-5 text-center sm:p-6">
          <CheckCircle2 className="mx-auto h-12 w-12 text-green-600" aria-hidden />
          <h1 className="mt-2 text-2xl font-bold text-[#0b5a2e]">Registered!</h1>
          <p className="mt-1 text-sm text-gray-700">
            {done.successMessage || `Thank you ${done.name}. Your pass number is ${done.passNo}.`}
          </p>
          {done.paymentStatus === 'PAID' && done.amount && (
            <p className="mt-2 inline-block rounded-full bg-green-50 px-3 py-1 text-sm font-medium text-green-800">Payment of {formatMoney(done.amount)} received</p>
          )}
          <p className="mt-2 text-sm font-medium text-gray-800">{passStatusText(done.passStatus)}</p>
          {done.passUrl && (
            <>
              <img src={passSrc} alt={`Pass ${done.passNo}`} className="mx-auto mt-4 w-full max-w-xs rounded-xl border border-[#d4af37] shadow" />
              <div className="mt-4 flex flex-col gap-2">
                {done.whatsappLink && (
                  <a href={done.whatsappLink} target="_blank" rel="noopener noreferrer"
                     className="inline-flex min-h-tap items-center justify-center gap-2 rounded-xl bg-[#25D366] px-4 py-3 font-semibold text-white">
                    <MessageCircle className="h-5 w-5" aria-hidden /> Get my pass on WhatsApp
                  </a>
                )}
                <a href={`${passSrc}?download=true`} className="inline-flex min-h-tap items-center justify-center gap-2 rounded-xl border border-[#0b5a2e] px-4 py-3 font-semibold text-[#0b5a2e]">
                  <Download className="h-5 w-5" aria-hidden /> Download pass
                </a>
              </div>
            </>
          )}
        </div>
      </Shell>
    );
  }

  return (
    <Shell>
      <div className="bg-[#0b5a2e] px-5 py-6 text-center text-white">
        {f.businessName && <p className="text-xs uppercase tracking-widest text-[#f3d77a]">{f.businessName}</p>}
        <h1 className="mt-1 text-2xl font-extrabold leading-tight">{f.title}</h1>
        {f.description && <CollapsibleDescription description={f.description} />}
      </div>
      <div className="register-form-light p-5 sm:p-6">
        {!f.open ? (
          <Alert variant="warning" title={f.closedReason || 'Registrations are closed'} />
        ) : (
          <form className="space-y-4" noValidate onSubmit={handleSubmit((v) => submit.mutate(v))}>
            <Input label="Full name" required autoComplete="name" maxLength={80} placeholder="e.g. Mohammed Shahid" error={formState.errors.name?.message} {...register('name')} />
            <Controller
              name="mobile"
              control={control}
              render={({ field }) => (
                <PhoneInput label="WhatsApp mobile number" required value={field.value} onChange={field.onChange}
                            hint={f.feeAmount ? "Your pass is sent to this WhatsApp number after payment." : "Your free pass is sent to this WhatsApp number."} error={formState.errors.mobile?.message} />
              )}
            />
            {f.collectPhoto && (
              <FileUpload label="Your photo (optional)" value={photo} onChange={setPhoto} accept="image/jpeg,image/png" maxSizeMB={5}
                          hint="Printed on your pass. JPG or PNG, up to 5 MB." />
            )}
            <Controller
              name="consent"
              control={control}
              render={({ field }) => (
                <Checkbox label={f.consentText} checked={field.value} onChange={(e) => field.onChange(e.target.checked)}
                          error={formState.errors.consent?.message} />
              )}
            />
            {/* Honeypot: hidden from people, bots fill it in. */}
            <input type="text" name="website" tabIndex={-1} autoComplete="off" className="hidden" aria-hidden />
            {f.feeAmount && (
              <div className="flex items-center justify-between rounded-xl border border-[#d4af37] bg-[#fff8e1] px-4 py-3">
                <span className="text-sm font-medium text-gray-800">{f.feeLabel || 'Registration fee'}</span>
                <span className="text-lg font-bold text-[#0b5a2e]">{formatMoney(f.feeAmount)}</span>
              </div>
            )}
            {formState.errors.root && <Alert variant="error" title={formState.errors.root.message} />}
            <Button type="submit" className="w-full !bg-[#0b5a2e] hover:!bg-[#0e6b37]" size="lg" loading={submit.isPending || paying}>
              {f.feeAmount ? `Pay ${formatMoney(f.feeAmount)} & get my pass` : f.passEnabled ? 'Get my free pass' : 'Register'}
            </Button>
            {f.feeAmount && <p className="text-center text-xs text-gray-500">Secure payment by Razorpay (UPI, card, net banking). Your pass is created as soon as the payment is confirmed.</p>}
          </form>
        )}
      </div>
    </Shell>
  );
}
