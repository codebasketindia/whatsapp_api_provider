import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Check, ChevronDown, Download, FileText, Lock, MessageCircle, QrCode, ShieldCheck, Ticket } from 'lucide-react';
import * as regApi from '../../../api/endpoints/registrations';
import { getErrorMessage, getFieldErrors } from '../../../api/axios';
import { Alert, Button, Checkbox, FileUpload, Input, PhoneInput, Spinner, isIndianMobile } from '../../../components/ui';
import { openCheckout } from '../../../lib/razorpay';
import { passStatusText } from './registerHelpers';
import { computeTotals, discountPercent, optionalIds, rupees, toggleId } from './bookingHelpers';
import PackageIcon from './PackageIcon';
import SuccessAnimation from './SuccessAnimation';

const schema = z.object({
  name: z.string().trim().min(2, 'Enter your full name').max(80, 'Up to 80 letters').regex(/^[\p{L}\p{M} .'-]+$/u, 'Use letters only'),
  mobile: z.string().refine((v) => isIndianMobile(v), 'Enter your 10-digit WhatsApp number'),
  consent: z.literal(true, { errorMap: () => ({ message: 'Please accept to continue' }) }),
});

// Calm ivory / navy / gold palette; inputs get a light, readable look whatever the app theme is.
const STYLES = `
  .bk-page { background: radial-gradient(1200px 500px at 50% -10%, #efe6d2 0%, #f8f4ea 45%, #fbf8f1 100%); color: #1f2937; }
  .bk-card { background: #ffffff; border: 1px solid #ece3cf; box-shadow: 0 10px 30px -12px rgba(30,42,68,.18); }
  .bk-form input, .bk-form select, .bk-form .field-base { background:#fffdf8 !important; color:#1f2937 !important; border-color:#e2d6bb !important; }
  .bk-form label, .bk-form .label-base { color:#1e2a44 !important; }
  .bk-form .text-muted { color:#6b7280 !important; }
  .bk-form .bg-surface-2 { background:#f3ecdc !important; color:#1f2937 !important; }
  @keyframes bkUp { from { opacity:0; transform: translateY(14px) } to { opacity:1; transform:none } }
  .bk-up { animation: bkUp .55s cubic-bezier(.2,.8,.2,1) both; }
  .bk-pkg { transition: border-color .2s, box-shadow .2s, transform .2s; }
  .bk-pkg:hover { transform: translateY(-1px); }
  @media (prefers-reduced-motion: reduce) { .bk-up { animation: none } .bk-pkg { transition: none } }
`;

function Shell({ children }) {
  return (
    <div className="bk-page min-h-screen px-4 pb-28 pt-6 sm:pt-10">
      <style>{STYLES}</style>
      <div className="mx-auto w-full max-w-xl">{children}</div>
      <p className="mt-6 text-center text-xs text-[#8a7f6a]">Secure booking · Payments by Razorpay</p>
    </div>
  );
}

function Hero({ f }) {
  return (
    <header className="bk-up text-center">
      {f.businessName && <p className="text-xs font-semibold uppercase tracking-[0.25em] text-[#a8873d]">{f.businessName}</p>}
      <h1 className="mt-2 text-3xl font-extrabold leading-tight text-[#1e2a44] sm:text-4xl">{f.title}</h1>
      <div className="mx-auto mt-3 h-1 w-16 rounded-full bg-gradient-to-r from-[#c9a24b] to-[#e8cf8f]" />
    </header>
  );
}

function FullDetails({ f }) {
  const [open, setOpen] = useState(false);
  const cards = f.packages || [];
  if (!f.description && !cards.length) return null;
  const [summary, ...rest] = (f.description || '').split(/\n\n+/);
  return (
    <section className="bk-up mt-5" style={{ animationDelay: '80ms' }}>
      {summary && <p className="whitespace-pre-line text-center text-[15px] leading-relaxed text-[#4b5563]">{summary}</p>}
      <div className="mt-4 text-center">
        <button type="button" onClick={() => setOpen(!open)} aria-expanded={open}
                className="inline-flex min-h-tap items-center gap-1.5 rounded-full border border-[#e2d6bb] bg-white px-5 text-sm font-semibold text-[#1e2a44] shadow-sm transition hover:border-[#c9a24b]">
          {open ? 'Hide details' : 'View Full Details'}
          <ChevronDown className={`h-4 w-4 transition-transform duration-300 ${open ? 'rotate-180' : ''}`} aria-hidden />
        </button>
      </div>
      <div className={`grid transition-all duration-500 ease-out ${open ? 'mt-4 grid-rows-[1fr] opacity-100' : 'grid-rows-[0fr] opacity-0'}`}>
        <div className="overflow-hidden">
          {rest.length > 0 && <p className="mb-4 whitespace-pre-line text-sm leading-relaxed text-[#4b5563]">{rest.join('\n\n')}</p>}
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            {cards.map((p, i) => (
              <article key={p.id} className="bk-card bk-up rounded-2xl p-4" style={{ animationDelay: `${i * 70}ms` }}>
                {p.imageUrl && <img src={regApi.publicUrl(p.imageUrl)} alt="" className="mb-3 h-28 w-full rounded-xl object-cover" loading="lazy" />}
                <div className="flex items-start gap-3">
                  <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-[#f4ecd9] text-[#a8873d]">
                    <PackageIcon name={p.icon} className="h-6 w-6" />
                  </span>
                  <div className="min-w-0">
                    <h3 className="font-bold text-[#1e2a44]">{p.name}</h3>
                    {p.description && <p className="mt-1 text-sm text-[#5b6472]">{p.description}</p>}
                  </div>
                </div>
                {p.details && <p className="mt-3 whitespace-pre-line border-t border-[#f0e8d6] pt-3 text-xs leading-relaxed text-[#6b7280]">{p.details}</p>}
              </article>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}

function PackagePicker({ packages, selected, onToggle }) {
  return (
    <fieldset>
      <legend className="mb-2 text-sm font-semibold text-[#1e2a44]">Choose your passes</legend>
      <div className="space-y-2.5">
        {packages.map((p) => {
          const on = p.mandatory || selected.includes(p.id);
          const off = discountPercent(p.mrp, p.salePrice);
          return (
            <label key={p.id}
                   className={`bk-pkg flex cursor-pointer items-center gap-3 rounded-2xl border bg-white p-3.5 ${on ? 'border-[#c9a24b] shadow-[0_0_0_3px_rgba(201,162,75,.15)]' : 'border-[#ece3cf]'} ${p.mandatory ? 'cursor-default' : ''}`}>
              <input type="checkbox" className="sr-only" checked={on} disabled={p.mandatory} onChange={() => !p.mandatory && onToggle(p.id)} />
              <span className={`flex h-6 w-6 shrink-0 items-center justify-center rounded-md border-2 transition ${on ? 'border-[#1e2a44] bg-[#1e2a44] text-white' : 'border-[#cdbf9f] bg-white'}`}>
                {p.mandatory ? <Lock className="h-3.5 w-3.5" aria-hidden /> : on && <Check className="h-4 w-4" aria-hidden />}
              </span>
              <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-[#f4ecd9] text-[#a8873d]"><PackageIcon name={p.icon} className="h-5 w-5" /></span>
              <span className="min-w-0 flex-1">
                <span className="block font-semibold leading-snug text-[#1e2a44]">{p.name}</span>
                <span className="text-xs text-[#6b7280]">{p.mandatory ? 'Registration fee · required' : p.description}</span>
              </span>
              <span className="shrink-0 text-right">
                {Number(p.mrp) > Number(p.salePrice) && <span className="block text-xs text-[#9ca3af] line-through">{rupees(p.mrp)}</span>}
                <span className="block text-lg font-extrabold text-[#1e2a44]">{rupees(p.salePrice)}</span>
                {off > 0 && <span className="inline-block rounded-full bg-[#fdf3dc] px-2 py-0.5 text-[10px] font-bold text-[#a8873d]">{off}% OFF</span>}
              </span>
            </label>
          );
        })}
      </div>
    </fieldset>
  );
}

function Downloads({ done }) {
  const b = done.booking;
  const items = b?.items || [];
  return (
    <section className="bk-card bk-up mt-5 rounded-3xl p-5 text-left">
      <h2 className="flex items-center gap-2 text-lg font-bold text-[#1e2a44]"><Download className="h-5 w-5 text-[#a8873d]" aria-hidden /> Your downloads</h2>
      <p className="mt-1 text-sm text-[#6b7280]">Booking no. <b className="text-[#1e2a44]">{b.bookingNo}</b> · each pass has its own secure QR code, valid once at the entry.</p>
      <ul className="mt-4 space-y-2.5">
        {items.map((it) => (
          <li key={it.id} className="flex items-center gap-3 rounded-2xl border border-[#ece3cf] bg-[#fffdf8] p-3">
            <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-[#1e2a44] text-white"><QrCode className="h-5 w-5" aria-hidden /></span>
            <span className="min-w-0 flex-1">
              <span className="block font-semibold leading-snug text-[#1e2a44]">{it.name} Pass</span>
              <span className="block text-xs text-[#6b7280]">Pass ID {it.passNo}</span>
            </span>
            {it.passUrl && (
              <a href={`${regApi.publicUrl(it.passUrl)}?download=true`} className="inline-flex min-h-tap items-center gap-1 rounded-xl bg-[#1e2a44] px-3 text-sm font-semibold text-white">
                <Download className="h-4 w-4" aria-hidden /> Pass
              </a>
            )}
          </li>
        ))}
      </ul>
      <div className="mt-4 grid grid-cols-1 gap-2 sm:grid-cols-2">
        {b.passesPdfUrl && (
          <a href={regApi.publicUrl(b.passesPdfUrl)} className="inline-flex min-h-tap items-center justify-center gap-2 rounded-xl border border-[#1e2a44] px-4 font-semibold text-[#1e2a44]">
            <Ticket className="h-4 w-4" aria-hidden /> All passes (PDF)
          </a>
        )}
        {b.invoiceUrl && (
          <a href={regApi.publicUrl(b.invoiceUrl)} className="inline-flex min-h-tap items-center justify-center gap-2 rounded-xl border border-[#c9a24b] bg-[#fdf6e6] px-4 font-semibold text-[#7a5f22]">
            <FileText className="h-4 w-4" aria-hidden /> Invoice (PDF)
          </a>
        )}
      </div>
    </section>
  );
}

/** PR1/PR2/PR4: public booking page — details, passes to choose, payment, then passes and invoice to download. */
export default function RegisterPage() {
  const { slug } = useParams();
  const form = useQuery({ queryKey: ['public', 'register', slug], queryFn: () => regApi.getPublicForm(slug), retry: false });
  const [photo, setPhoto] = useState(null);
  const [selected, setSelected] = useState([]);
  const [done, setDone] = useState(null);
  const [paying, setPaying] = useState(false);
  const { control, register, handleSubmit, setError, formState } = useForm({
    resolver: zodResolver(schema),
    defaultValues: { name: '', mobile: '', consent: false },
  });
  const f = form.data;
  const packages = f?.packages || [];
  const totals = computeTotals(packages, selected);
  const amount = packages.length ? totals.total : Number(f?.feeAmount || 0);

  const pay = async (res) => {
    const p = res.payment;
    setPaying(true);
    try {
      const out = await openCheckout({
        key: p.keyId, order_id: p.orderId, amount: p.amount, currency: p.currency || 'INR', name: p.name, description: p.description,
        prefill: { name: res.name, contact: res.mobile }, theme: { color: '#1e2a44' },
      });
      if (out.status !== 'success') {
        setError('root', { message: out.status === 'failed' ? `Payment failed: ${out.error?.description || 'please try again'}` : 'Payment was not completed. Tap Pay to try again.' });
        return;
      }
      setDone(await regApi.verifyPayment(slug, {
        registrationUid: res.uid, razorpayOrderId: out.razorpay_order_id, razorpayPaymentId: out.razorpay_payment_id, razorpaySignature: out.razorpay_signature,
      }));
      window.scrollTo({ top: 0, behavior: 'smooth' });
    } catch (e) {
      setError('root', { message: getErrorMessage(e) });
    } finally {
      setPaying(false);
    }
  };
  const submit = useMutation({
    mutationFn: (v) => regApi.register(slug, {
      name: v.name.trim(), mobile: v.mobile, consent: true, photo: photo || undefined, website: '',
      packageIds: packages.length ? optionalIds(packages, selected).join(',') : undefined,
    }),
    onSuccess: (res) => (res.paymentStatus === 'PENDING' && res.payment ? pay(res) : setDone(res)),
    onError: (e) => {
      const fields = getFieldErrors(e);
      Object.entries(fields).forEach(([k, msg]) => setError(k === 'packageIds' ? 'root' : k, { message: msg }));
      if (!Object.keys(fields).length) setError('root', { message: getErrorMessage(e) });
    },
  });

  if (form.isLoading) return <Shell><div className="flex justify-center p-16"><Spinner /></div></Shell>;
  if (form.isError) {
    return <Shell><div className="bk-card rounded-3xl p-8 text-center"><h1 className="text-xl font-bold text-[#1e2a44]">Link not found</h1><p className="mt-2 text-sm text-gray-600">Please check the link you received.</p></div></Shell>;
  }

  if (done) {
    const single = done.passUrl && !done.booking;
    return (
      <Shell>
        <div className="bk-card bk-up rounded-3xl p-6 text-center">
          <SuccessAnimation />
          <h1 className="mt-1 text-2xl font-extrabold text-[#1e2a44]">{done.paymentStatus === 'PAID' ? 'Booking confirmed!' : 'Registered!'}</h1>
          <p className="mt-1 text-sm text-[#4b5563]">{done.successMessage || `Thank you ${done.name}. Your booking number is ${done.passNo}.`}</p>
          {done.paymentStatus === 'PAID' && done.amount && (
            <p className="mt-3 inline-flex items-center gap-1.5 rounded-full bg-[#eef6f1] px-3 py-1 text-sm font-semibold text-[#21614a]"><ShieldCheck className="h-4 w-4" aria-hidden /> Payment of {rupees(done.amount)} received</p>
          )}
          <p className="mt-3 text-sm text-[#374151]">{passStatusText(done.passStatus)}</p>
          {done.whatsappLink && (
            <a href={done.whatsappLink} target="_blank" rel="noopener noreferrer" className="mt-4 inline-flex min-h-tap w-full items-center justify-center gap-2 rounded-xl bg-[#25D366] px-4 py-3 font-semibold text-white">
              <MessageCircle className="h-5 w-5" aria-hidden /> Get my pass on WhatsApp
            </a>
          )}
          {single && (
            <>
              <img src={regApi.publicUrl(done.passUrl)} alt={`Pass ${done.passNo}`} className="mx-auto mt-4 w-full max-w-xs rounded-xl border border-[#e2d6bb] shadow" />
              <a href={`${regApi.publicUrl(done.passUrl)}?download=true`} className="mt-3 inline-flex min-h-tap w-full items-center justify-center gap-2 rounded-xl border border-[#1e2a44] px-4 py-3 font-semibold text-[#1e2a44]">
                <Download className="h-5 w-5" aria-hidden /> Download pass
              </a>
            </>
          )}
        </div>
        {done.booking && <Downloads done={done} />}
      </Shell>
    );
  }

  const legalLink = (kind, label) => <Link to={`/r/${slug}/${kind}`} target="_blank" className="font-semibold text-[#1e2a44] underline decoration-[#c9a24b] underline-offset-2">{label}</Link>;
  return (
    <Shell>
      <Hero f={f} />
      <FullDetails f={f} />
      <div className="bk-card bk-up bk-form mt-6 rounded-3xl p-5 sm:p-6" style={{ animationDelay: '160ms' }}>
        {!f.open ? (
          <Alert variant="warning" title={f.closedReason || 'Bookings are closed'} />
        ) : (
          <form id="bk-form" className="space-y-4" noValidate onSubmit={handleSubmit((v) => submit.mutate(v))}>
            <Input label="Full name" required autoComplete="name" maxLength={80} placeholder="As on your ID" error={formState.errors.name?.message} {...register('name')} />
            <Controller name="mobile" control={control} render={({ field }) => (
              <PhoneInput label="WhatsApp mobile number" required value={field.value} onChange={field.onChange}
                          hint="Your passes are sent to this WhatsApp number." error={formState.errors.mobile?.message} />
            )} />
            {f.collectPhoto && <FileUpload label="Your photo (optional)" value={photo} onChange={setPhoto} accept="image/jpeg,image/png" maxSizeMB={5} hint="JPG or PNG, up to 5 MB." />}
            {packages.length > 0 && <PackagePicker packages={packages} selected={selected} onToggle={(id) => setSelected((s) => toggleId(s, id))} />}
            {!packages.length && f.feeAmount && (
              <div className="flex items-center justify-between rounded-2xl border border-[#e2d6bb] bg-[#fffaf0] px-4 py-3">
                <span className="text-sm font-medium">{f.feeLabel || 'Registration fee'}</span><span className="text-lg font-bold text-[#1e2a44]">{rupees(f.feeAmount)}</span>
              </div>
            )}
            <Controller name="consent" control={control} render={({ field }) => (
              <Checkbox checked={field.value} onChange={(e) => field.onChange(e.target.checked)} error={formState.errors.consent?.message}
                        label={<span>{f.consentText}{(f.hasTerms || f.hasPrivacy) && <> — I accept the {f.hasTerms && legalLink('terms', 'Terms & Conditions')}{f.hasTerms && f.hasPrivacy && ' and '}{f.hasPrivacy && legalLink('privacy', 'Privacy Policy')}.</>}</span>} />
            )} />
            <input type="text" name="website" tabIndex={-1} autoComplete="off" className="hidden" aria-hidden />
            {formState.errors.root && <Alert variant="error" title={formState.errors.root.message} />}
            {packages.length > 0 && totals.savings > 0 && (
              <p className="text-center text-sm text-[#21614a]">You save <b>{rupees(totals.savings)}</b> on MRP {rupees(totals.mrp)}</p>
            )}
          </form>
        )}
      </div>
      {f.open && (
        <div className="fixed inset-x-0 bottom-0 z-20 border-t border-[#ece3cf] bg-white/95 px-4 py-3 backdrop-blur">
          <div className="mx-auto flex max-w-xl items-center gap-3">
            <div className="min-w-0 flex-1">
              <p className="text-xs text-[#6b7280]">Total {packages.length > 0 && `· ${totals.items.length} pass${totals.items.length > 1 ? 'es' : ''}`}</p>
              <p className="text-xl font-extrabold text-[#1e2a44]">{amount > 0 ? rupees(amount) : 'Free'}</p>
            </div>
            <Button type="submit" form="bk-form" size="lg" loading={submit.isPending || paying}
                    className="!rounded-xl !bg-[#1e2a44] px-6 hover:!bg-[#2b3a5c]">
              {amount > 0 ? `Pay ${rupees(amount)}` : 'Get my pass'}
            </Button>
          </div>
        </div>
      )}
    </Shell>
  );
}
