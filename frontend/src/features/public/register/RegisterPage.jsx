import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Bus, Check, ChevronRight, Download, FileText, MessageCircle, QrCode, ShieldCheck, Ticket, X } from 'lucide-react';
import * as regApi from '../../../api/endpoints/registrations';
import { getErrorMessage, getFieldErrors } from '../../../api/axios';
import { Alert, Button, Checkbox, FileUpload, Input, PhoneInput, Spinner, isIndianMobile } from '../../../components/ui';
import { openCheckout } from '../../../lib/razorpay';
import { passStatusText } from './registerHelpers';
import { comboApplies, computeTotals, optionalIds, rupees, toggleCombo, toggleId } from './bookingHelpers';
import PackageIcon from './PackageIcon';
import SuccessAnimation from './SuccessAnimation';
import ComboCelebration from './ComboCelebration';

const schema = z.object({
  name: z.string().trim().min(2, 'Enter your full name').max(80, 'Up to 80 letters').regex(/^[\p{L}\p{M} .'-]+$/u, 'Use letters only'),
  mobile: z.string().refine((v) => isIndianMobile(v), 'Enter your 10-digit WhatsApp number'),
  consent: z.literal(true, { errorMap: () => ({ message: 'Please accept to continue' }) }),
});

// HFHU look: ivory page, deep green and gold. Every text wraps (no truncation) so it reads fully on phones.
const STYLES = `
  .bk-page { background: linear-gradient(180deg, #fbf7ee 0%, #f8f1e2 100%); color: #1f2937; }
  .bk-card { background: #ffffff; border: 1px solid #eadfc6; box-shadow: 0 8px 24px -14px rgba(11,77,44,.28); }
  .bk-gold { background: linear-gradient(180deg, #f7dc8c 0%, #e2b443 100%); color: #3b2a05; }
  .bk-green { background: linear-gradient(135deg, #0b5a33 0%, #0a4a2b 100%); }
  .bk-form input, .bk-form select, .bk-form .field-base { background:#fffdf8 !important; color:#1f2937 !important; border-color:#e2d6bb !important; }
  .bk-form label, .bk-form .label-base { color:#0b4d2c !important; }
  .bk-form .text-muted { color:#6b7280 !important; }
  .bk-form .text-ink { color:#1f2937 !important; }
  .bk-form .bg-surface-2 { background:#f3ecdc !important; color:#1f2937 !important; }
  .bk-strike { position: relative; display: inline-block; }
  .bk-strike::after { content: ''; position: absolute; left: -4%; right: -4%; top: 52%; height: 2px; background: #d23b2f; transform: rotate(-8deg); }
  @keyframes bkUp { from { opacity:0; transform: translateY(12px) } to { opacity:1; transform:none } }
  .bk-up { animation: bkUp .5s cubic-bezier(.2,.8,.2,1) both; }
  .bk-tap { transition: transform .15s, box-shadow .2s, border-color .2s; }
  .bk-tap:active { transform: scale(.985); }
  @keyframes bkSheet { from { transform: translateY(100%) } to { transform: none } }
  .bk-sheet { animation: bkSheet .3s cubic-bezier(.2,.8,.2,1) both; }
  @media (prefers-reduced-motion: reduce) { .bk-up, .bk-sheet { animation: none } .bk-tap { transition: none } }
`;

const img = (u) => (u && u.startsWith('/api/') ? regApi.publicUrl(u) : u);

function Shell({ children }) {
  return (
    <div className="bk-page min-h-screen pb-10">
      <style>{STYLES}</style>
      <div className="mx-auto w-full max-w-xl">{children}</div>
      <p className="mt-6 px-4 text-center text-xs text-[#8a7f6a]">Secure booking · Payments by Razorpay</p>
    </div>
  );
}

function Hero({ f }) {
  return (
    <header className="bk-up">
      {f.heroImageUrl ? (
        <img src={img(f.heroImageUrl)} alt={f.title} className="block h-auto w-full" />
      ) : (
        <div className="bk-green px-4 py-8 text-center text-white">
          {f.businessName && <p className="text-xs font-semibold uppercase tracking-[0.25em] text-[#f3d77a]">{f.businessName}</p>}
          <p className="mt-1 text-2xl font-extrabold leading-tight">{f.title}</p>
        </div>
      )}
      <div className="px-4 pt-4 text-center">
        <h1 className="break-words text-[28px] font-extrabold leading-tight text-[#0b4d2c] sm:text-4xl">{f.pageHeading || f.title}</h1>
        <div className="mx-auto mt-2 flex max-w-xs items-center gap-2" aria-hidden>
          <span className="h-px flex-1 bg-gradient-to-r from-transparent to-[#c9a24b]" />
          <span className="h-2.5 w-2.5 rotate-45 border border-[#c9a24b]" />
          <span className="h-px flex-1 bg-gradient-to-l from-transparent to-[#c9a24b]" />
        </div>
      </div>
    </header>
  );
}

function PriceBlock({ p }) {
  const free = Number(p.salePrice) === 0;
  if (free) return <span className="rounded-xl bg-[#0b5a33] px-3 py-2 text-base font-extrabold tracking-wide text-white shadow">FREE</span>;
  return (
    <span className="flex flex-col items-center gap-1">
      <span className="rounded-md bg-[#f4e3ad] px-2 text-[11px] font-bold text-[#5c4510]">PAID</span>
      {Number(p.mrp) > Number(p.salePrice) && <span className="bk-strike text-sm font-semibold text-[#5b4a2a]">{rupees(p.mrp)}</span>}
      <span className="bk-gold rounded-xl px-2.5 py-1 text-lg font-extrabold leading-none shadow-sm sm:text-xl">{rupees(p.salePrice)}</span>
    </span>
  );
}

function PassCard({ p, on, onToggle, onInfo, index }) {
  return (
    <div className={`bk-card bk-tap bk-up relative rounded-2xl p-2.5 ${on ? 'ring-2 ring-[#c9a24b]' : ''}`} style={{ animationDelay: `${index * 60}ms` }}>
      <button type="button" onClick={onToggle} aria-pressed={on} className="grid w-full grid-cols-[88px_minmax(0,1fr)_auto] items-center gap-2.5 text-left sm:grid-cols-[120px_minmax(0,1fr)_auto]">
        <span className="relative block">
          {p.imageUrl ? <img src={img(p.imageUrl)} alt="" className="aspect-[4/3] w-full rounded-xl object-cover" loading="lazy" />
            : <span className="flex aspect-[4/3] w-full items-center justify-center rounded-xl bg-[#f4ecd9] text-[#a8873d]"><PackageIcon name={p.icon} className="h-8 w-8" /></span>}
          <span className={`absolute -left-1 -top-1 flex h-6 w-6 items-center justify-center rounded-full border-2 border-white shadow ${on ? 'bg-[#0b5a33] text-white' : 'bg-white/90 text-transparent'}`}>
            <Check className="h-3.5 w-3.5" aria-hidden />
          </span>
        </span>
        <span className="min-w-0">
          <span className="block break-words text-[16px] font-extrabold leading-snug text-[#0b4d2c] sm:text-lg">{p.name}</span>
          {p.description && <span className="mt-0.5 block break-words text-[13px] leading-snug text-[#374151]">{p.description}</span>}
        </span>
        <span className="flex items-center gap-1 border-l border-[#eadfc6] pl-2">
          <PriceBlock p={p} />
        </span>
      </button>
      {p.details && (
        <button type="button" onClick={onInfo} className="mt-1.5 inline-flex min-h-[32px] items-center gap-1 px-1 text-xs font-semibold text-[#0b5a33]">
          View full details <ChevronRight className="h-3.5 w-3.5" aria-hidden />
        </button>
      )}
    </div>
  );
}

function ComboCard({ combo, on, onToggle }) {
  return (
    <button type="button" onClick={onToggle} aria-pressed={on}
            className={`bk-green bk-tap bk-up grid w-full grid-cols-[auto_minmax(0,1fr)_auto] items-center gap-3 rounded-2xl p-3 text-left text-white shadow-lg ${on ? 'ring-4 ring-[#e2b443]' : ''}`}>
      <span className="flex h-12 w-12 items-center justify-center border-r border-[#c9a24b]/60 pr-3"><Bus className="h-8 w-8" aria-hidden /></span>
      <span className="min-w-0">
        <span className="block break-words text-[16px] font-extrabold leading-snug sm:text-xl">{combo.title}</span>
        {combo.subtitle && <span className="mt-0.5 block break-words text-[13px] leading-snug text-white/90">{combo.subtitle}</span>}
        <span className="mt-1 inline-flex items-center gap-1 text-[11px] font-semibold text-[#f7dc8c]">{on ? <><Check className="h-3.5 w-3.5" aria-hidden /> Combo selected</> : 'Tap to select all three'}</span>
      </span>
      <span className="bk-gold flex flex-col items-center rounded-xl px-3 py-1.5">
        {combo.mrp && <span className="bk-strike text-sm font-bold">{rupees(combo.mrp)}</span>}
        <span className="text-xl font-extrabold leading-tight sm:text-2xl">{rupees(combo.price)}</span>
      </span>
    </button>
  );
}

function FeeBox({ p }) {
  return (
    <div className="bk-up rounded-2xl border-2 border-[#d9b65c] bg-[#fffdf7] px-4 py-3 text-center">
      <p className="break-words text-[17px] font-extrabold leading-snug text-[#0b4d2c] sm:text-xl">{rupees(p.salePrice)} {p.name}</p>
      {p.description && <p className="mt-0.5 break-words text-[13px] text-[#374151]">{p.description}</p>}
    </div>
  );
}

/** Full link description; **bold** is shown bold. Long text goes in a card, short text sits under the heading. */
function Description({ text }) {
  const long = text.length > 160 || text.split('\n').length > 3;
  const parts = text.split(/(\*\*[^*]+\*\*)/g).map((t, i) => (
    /^\*\*[^*]+\*\*$/.test(t) ? <b key={i} className="font-bold text-[#0b4d2c]">{t.slice(2, -2)}</b> : t
  ));
  return long ? (
    <div className="bk-card bk-up whitespace-pre-line break-words rounded-2xl p-4 text-left text-[14px] leading-relaxed text-[#374151]">{parts}</div>
  ) : (
    <p className="whitespace-pre-line break-words px-1 text-center text-[14px] leading-relaxed text-[#4b5563]">{parts}</p>
  );
}

function DetailsSheet({ p, onClose }) {
  if (!p) return null;
  return (
    <div className="fixed inset-0 z-40 flex items-end justify-center bg-black/40 sm:items-center" onClick={onClose} role="dialog" aria-modal="true" aria-label={p.name}>
      <div className="bk-sheet max-h-[85vh] w-full max-w-xl overflow-y-auto rounded-t-3xl bg-white p-5 sm:rounded-3xl" onClick={(e) => e.stopPropagation()}>
        {p.imageUrl && <img src={img(p.imageUrl)} alt="" className="mb-3 aspect-[16/9] w-full rounded-2xl object-cover" />}
        <h2 className="break-words text-xl font-extrabold text-[#0b4d2c]">{p.name}</h2>
        {p.description && <p className="mt-1 text-sm text-[#374151]">{p.description}</p>}
        <p className="mt-3 whitespace-pre-line break-words text-[15px] leading-relaxed text-[#1f2937]">{p.details}</p>
        <button type="button" onClick={onClose} className="mt-5 min-h-tap w-full rounded-xl bg-[#0b5a33] font-semibold text-white">Close</button>
      </div>
    </div>
  );
}

/** Booking summary after payment: passes, combo discount, registration fee, amount paid. */
function BookingSummary({ b }) {
  const all = b?.items || [];
  const passes = all.filter((it) => it.passNo);
  const fees = all.filter((it) => !it.passNo);
  const discount = Number(b.discountAmount || 0);
  const passTotal = passes.reduce((a, it) => a + Number(it.price || 0), 0) - discount;
  const money = (v) => (Number(v) > 0 ? rupees(v) : 'FREE');
  const row = 'flex items-start justify-between gap-3 py-2';
  return (
    <section className="bk-card bk-up mt-5 rounded-3xl p-5 text-left">
      <h2 className="text-lg font-bold text-[#0b4d2c]">Booking summary</h2>
      <ul className="mt-2 divide-y divide-[#f0e8d6] text-sm text-[#1f2937]">
        <li className="pb-1 pt-2 text-xs font-semibold uppercase tracking-wide text-[#8a7f6a]">Selected Pass ({passes.length})</li>
        {passes.map((it) => (
          <li key={it.id} className={row}><span className="min-w-0 break-words">{it.name}</span><span className="shrink-0 font-semibold">{money(it.price)}</span></li>
        ))}
        {discount > 0 && (
          <li className={`${row} text-[#0b5a33]`}><span className="min-w-0 break-words">Combo Discount</span><span className="shrink-0 font-semibold">− {rupees(discount)}</span></li>
        )}
        <li className={`${row} font-semibold`}><span>Passes total</span><span>{money(passTotal)}</span></li>
        {fees.map((it) => (
          <li key={it.id} className={row}><span className="min-w-0 break-words">{it.name}</span><span className="shrink-0 font-semibold">{rupees(it.price)}</span></li>
        ))}
        <li className="flex items-center justify-between py-2 text-base font-extrabold text-[#0b4d2c]"><span>Total paid</span><span>{money(b.total)}</span></li>
      </ul>
    </section>
  );
}

function Downloads({ done }) {
  const b = done.booking;
  const items = (b?.items || []).filter((it) => it.passNo);
  return (
    <>
    <BookingSummary b={b} />
    <section className="bk-card bk-up mt-5 rounded-3xl p-5 text-left">
      <h2 className="flex items-center gap-2 text-lg font-bold text-[#0b4d2c]"><Download className="h-5 w-5 text-[#a8873d]" aria-hidden /> Your downloads</h2>
      <p className="mt-1 break-words text-sm text-[#6b7280]">Booking no. <b className="text-[#0b4d2c]">{b.bookingNo}</b> · each pass has its own secure QR code, valid once at the entry.</p>
      <ul className="mt-4 space-y-2.5">
        {items.map((it) => (
          <li key={it.id} className="flex items-center gap-3 rounded-2xl border border-[#ece3cf] bg-[#fffdf8] p-3">
            <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-[#0b5a33] text-white"><QrCode className="h-5 w-5" aria-hidden /></span>
            <span className="min-w-0 flex-1">
              <span className="block font-semibold leading-snug text-[#0b4d2c] break-words">{/pass$/i.test(it.name) ? it.name : `${it.name} Pass`}</span>
              <span className="block break-words text-xs text-[#6b7280]">Pass ID {it.passNo}</span>
            </span>
            {it.passUrl && (
              <a href={`${regApi.publicUrl(it.passUrl)}?download=true`} className="inline-flex min-h-tap items-center gap-1 rounded-xl bg-[#0b5a33] px-3 text-sm font-semibold text-white">
                <Download className="h-4 w-4" aria-hidden /> Pass
              </a>
            )}
          </li>
        ))}
      </ul>
      <div className="mt-4 grid grid-cols-1 gap-2 sm:grid-cols-2">
        {b.passesPdfUrl && (
          <a href={regApi.publicUrl(b.passesPdfUrl)} className="inline-flex min-h-tap items-center justify-center gap-2 rounded-xl border border-[#0b5a33] px-4 font-semibold text-[#0b4d2c]">
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
    </>
  );
}

/** PR1/PR2/PR4: public booking page — choose passes, register, pay; then passes and invoice to download. */
export default function RegisterPage() {
  const { slug } = useParams();
  const form = useQuery({ queryKey: ['public', 'register', slug], queryFn: () => regApi.getPublicForm(slug), retry: false });
  const [photo, setPhoto] = useState(null);
  const [selected, setSelected] = useState(null);
  const [sheet, setSheet] = useState(false);
  const [info, setInfo] = useState(null);
  const [done, setDone] = useState(null);
  const [paying, setPaying] = useState(false);
  const { control, register, handleSubmit, setError, formState } = useForm({
    resolver: zodResolver(schema),
    defaultValues: { name: '', mobile: '', consent: false },
  });
  const f = form.data;
  const packages = f?.packages || [];
  const combo = f?.combo || null;
  const cards = packages.filter((p) => !p.mandatory);
  const fees = packages.filter((p) => p.mandatory);
  // Free passes start selected.
  useEffect(() => {
    if (f && selected === null) setSelected(cards.filter((p) => Number(p.salePrice) === 0).map((p) => p.id));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [f]);
  const sel = selected || [];
  const totals = computeTotals(packages, sel, combo);
  const amount = packages.length ? totals.total : Number(f?.feeAmount || 0);
  // Passes the person picked (the mandatory registration fee is shown on its own, at checkout).
  const picked = totals.items.filter((p) => !p.mandatory);
  const feeItems = totals.items.filter((p) => p.mandatory);
  const feeTotal = feeItems.reduce((a, p) => a + Number(p.salePrice || 0), 0);
  const passTotal = Math.max(0, amount - feeTotal);
  const price = (v) => (v > 0 ? rupees(v) : 'FREE');
  // Congratulations the moment the combo becomes complete (banner tap or the last of its passes).
  const comboNow = comboApplies(packages, sel, combo);
  const wasCombo = useRef(null);
  const [celebrate, setCelebrate] = useState(false);
  useEffect(() => {
    if (selected === null) return;
    if (wasCombo.current === false && comboNow) setCelebrate(true);
    wasCombo.current = comboNow;
  }, [comboNow, selected]);
  const endCelebrate = useCallback(() => setCelebrate(false), []);

  const pay = async (res) => {
    const p = res.payment;
    setPaying(true);
    try {
      const out = await openCheckout({
        key: p.keyId, order_id: p.orderId, amount: p.amount, currency: p.currency || 'INR', name: p.name, description: p.description,
        prefill: { name: res.name, contact: res.mobile }, theme: { color: '#0b5a33' },
      });
      if (out.status !== 'success') {
        setError('root', { message: out.status === 'failed' ? `Payment failed: ${out.error?.description || 'please try again'}` : 'Payment was not completed. Tap Pay to try again.' });
        return;
      }
      setDone(await regApi.verifyPayment(slug, {
        registrationUid: res.uid, razorpayOrderId: out.razorpay_order_id, razorpayPaymentId: out.razorpay_payment_id, razorpaySignature: out.razorpay_signature,
      }));
      setSheet(false);
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
      packageIds: packages.length ? optionalIds(packages, sel).join(',') : undefined,
    }),
    onSuccess: (res) => (res.paymentStatus === 'PENDING' && res.payment ? pay(res) : (setDone(res), setSheet(false))),
    onError: (e) => {
      const fields = getFieldErrors(e);
      Object.entries(fields).forEach(([k, msg]) => setError(k === 'packageIds' ? 'root' : k, { message: msg }));
      if (!Object.keys(fields).length) setError('root', { message: getErrorMessage(e) });
    },
  });

  if (form.isLoading) return <Shell><div className="flex justify-center p-16"><Spinner /></div></Shell>;
  if (form.isError) {
    return <Shell><div className="bk-card m-4 rounded-3xl p-8 text-center"><h1 className="text-xl font-bold text-[#0b4d2c]">Link not found</h1><p className="mt-2 text-sm text-gray-600">Please check the link you received.</p></div></Shell>;
  }

  if (done) {
    const single = done.passUrl && !done.booking;
    return (
      <Shell>
        {f.heroImageUrl && <img src={img(f.heroImageUrl)} alt={f.title} className="block h-auto w-full" />}
        <div className="px-4">
          <div className="bk-card bk-up mt-4 rounded-3xl p-6 text-center">
            <SuccessAnimation />
            <h1 className="mt-1 text-2xl font-extrabold text-[#0b4d2c]">{done.paymentStatus === 'PAID' ? 'Booking confirmed!' : 'Registered!'}</h1>
            <p className="mt-1 break-words text-sm text-[#4b5563]">{done.successMessage || `Thank you ${done.name}. Your booking number is ${done.passNo}.`}</p>
            {done.paymentStatus === 'PAID' && done.amount && (
              <p className="mt-3 inline-flex items-center gap-1.5 rounded-full bg-[#eaf5ee] px-3 py-1 text-sm font-semibold text-[#0b5a33]"><ShieldCheck className="h-4 w-4" aria-hidden /> Payment of {rupees(done.amount)} received</p>
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
                <a href={`${regApi.publicUrl(done.passUrl)}?download=true`} className="mt-3 inline-flex min-h-tap w-full items-center justify-center gap-2 rounded-xl border border-[#0b5a33] px-4 py-3 font-semibold text-[#0b5a33]">
                  <Download className="h-5 w-5" aria-hidden /> Download pass
                </a>
              </>
            )}
          </div>
          {done.booking && <Downloads done={done} />}
        </div>
      </Shell>
    );
  }

  const legalLink = (kind, label) => <Link to={`/r/${slug}/${kind}`} target="_blank" className="font-semibold text-[#0b4d2c] underline decoration-[#c9a24b] underline-offset-2">{label}</Link>;
  const comboOn = comboApplies(packages, sel, combo);
  return (
    <Shell>
      <Hero f={f} />
      <div className="space-y-3 px-3 pt-4 sm:px-4">
        {f.description && <Description text={f.description} />}
        {cards.map((p, i) => (
          <PassCard key={p.id} p={p} index={i} on={sel.includes(p.id)} onToggle={() => setSelected(toggleId(sel, p.id))} onInfo={() => setInfo(p)} />
        ))}
        {combo && <ComboCard combo={combo} on={comboOn} onToggle={() => setSelected(toggleCombo(packages, sel, combo))} />}
        {fees.map((p) => <FeeBox key={p.id} p={p} />)}
        {!packages.length && f.feeAmount && <FeeBox p={{ salePrice: f.feeAmount, name: f.feeLabel || 'Registration fee' }} />}
        {f.pageNote && <p className="break-words px-2 text-center text-[13px] text-[#374151]">{f.pageNote}</p>}
        {!f.open ? (
          <Alert variant="warning" title={f.closedReason || 'Bookings are closed'} />
        ) : (
          <>
            {packages.length > 0 && (
              <p className="text-center text-[13px] text-[#374151]">
                Selected Pass: <b className="text-[#0b4d2c]">{picked.length}</b> · Total <b className="text-[#0b4d2c]">{price(passTotal)}</b>
                {totals.comboDiscount > 0 && <> · Combo Discount <b className="text-[#0b5a33]">{rupees(totals.comboDiscount)}</b></>}
              </p>
            )}
            <button type="button" onClick={() => setSheet(true)}
                    className="bk-green bk-tap min-h-[60px] w-full rounded-2xl text-[22px] font-extrabold text-white shadow-lg">
              {f.ctaLabel || 'Register'}
            </button>
          </>
        )}
      </div>

      <DetailsSheet p={info} onClose={() => setInfo(null)} />
      {celebrate && <ComboCelebration discount={totals.comboDiscount} onClose={endCelebrate} />}

      {sheet && (
        <div className="fixed inset-0 z-40 flex items-end justify-center bg-black/40 sm:items-center" onClick={() => setSheet(false)} role="dialog" aria-modal="true" aria-label="Registration">
          <div className="bk-sheet bk-form max-h-[92vh] w-full max-w-xl overflow-y-auto rounded-t-3xl bg-white p-5 sm:rounded-3xl" onClick={(e) => e.stopPropagation()}>
            <div className="mb-3 flex items-center justify-between gap-2">
              <h2 className="text-xl font-extrabold text-[#0b4d2c]">{f.ctaLabel || 'Register'}</h2>
              <button type="button" onClick={() => setSheet(false)} className="flex h-10 w-10 items-center justify-center rounded-full bg-[#f3ecdc]" aria-label="Close"><X className="h-5 w-5" aria-hidden /></button>
            </div>
            {packages.length > 0 && (
              <ul className="mb-4 divide-y divide-[#f0e8d6] rounded-2xl border border-[#eadfc6] bg-[#fffdf7] px-3 text-sm">
                <li className="pb-1 pt-2 text-xs font-semibold uppercase tracking-wide text-[#8a7f6a]">Selected Pass ({picked.length})</li>
                {picked.length === 0 && <li className="py-2 text-[#6b7280]">No pass selected</li>}
                {picked.map((p) => (
                  <li key={p.id} className="flex items-start justify-between gap-3 py-2">
                    <span className="min-w-0 break-words">{p.name}</span>
                    <span className="shrink-0 font-semibold">{price(Number(p.salePrice))}</span>
                  </li>
                ))}
                {totals.comboDiscount > 0 && (
                  <li className="flex items-start justify-between gap-3 py-2 text-[#0b5a33]"><span className="min-w-0 break-words">Combo Discount</span><span className="shrink-0 font-semibold">− {rupees(totals.comboDiscount)}</span></li>
                )}
                <li className="flex items-center justify-between gap-3 py-2 font-semibold"><span>Passes total</span><span>{price(passTotal)}</span></li>
                {feeItems.map((p) => (
                  <li key={p.id} className="flex items-start justify-between gap-3 py-2">
                    <span className="min-w-0 break-words">{p.name}</span>
                    <span className="shrink-0 font-semibold">{rupees(p.salePrice)}</span>
                  </li>
                ))}
                <li className="flex items-center justify-between py-2 text-base font-extrabold text-[#0b4d2c]"><span>Total payable</span><span>{amount > 0 ? rupees(amount) : 'Free'}</span></li>
              </ul>
            )}
            <form id="bk-form" className="space-y-4" noValidate onSubmit={handleSubmit((v) => submit.mutate(v))}>
              <Input label="Full name" required autoComplete="name" maxLength={80} placeholder="As on your ID" error={formState.errors.name?.message} {...register('name')} />
              <Controller name="mobile" control={control} render={({ field }) => (
                <PhoneInput label="WhatsApp mobile number" required value={field.value} onChange={field.onChange}
                            hint="Your passes are sent to this WhatsApp number." error={formState.errors.mobile?.message} />
              )} />
              {f.collectPhoto && <FileUpload label="Your photo (optional)" value={photo} onChange={setPhoto} accept="image/jpeg,image/png" maxSizeMB={5} hint="JPG or PNG, up to 5 MB." />}
              <Controller name="consent" control={control} render={({ field }) => (
                <Checkbox checked={field.value} onChange={(e) => field.onChange(e.target.checked)} error={formState.errors.consent?.message}
                          label={<span className="text-[#1f2937]">{f.consentText}{(f.hasTerms || f.hasPrivacy) && <> — I accept the {f.hasTerms && legalLink('terms', 'Terms & Conditions')}{f.hasTerms && f.hasPrivacy && ' and '}{f.hasPrivacy && legalLink('privacy', 'Privacy Policy')}.</>}</span>} />
              )} />
              <input type="text" name="website" tabIndex={-1} autoComplete="off" className="hidden" aria-hidden />
              {formState.errors.root && <Alert variant="error" title={formState.errors.root.message} />}
              <Button type="submit" fullWidth size="lg" loading={submit.isPending || paying} className="!rounded-xl !bg-[#0b5a33] hover:!bg-[#0a4a2b]">
                {amount > 0 ? `Pay ${rupees(amount)}` : 'Get my pass'}
              </Button>
            </form>
          </div>
        </div>
      )}
    </Shell>
  );
}
