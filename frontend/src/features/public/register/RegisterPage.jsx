import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Check, ChevronRight, Download, FileText, ListChecks, MessageCircle, X } from 'lucide-react';
import * as regApi from '../../../api/endpoints/registrations';
import { getErrorMessage, getFieldErrors } from '../../../api/axios';
import { Alert, Button, Checkbox, FileUpload, Input, PhoneInput, Spinner, isIndianMobile } from '../../../components/ui';
import { openCheckout } from '../../../lib/razorpay';
import { passStatusText } from './registerHelpers';
import { comboApplies, computeTotals, optionalIds, rupees, toggleCombo, toggleId } from './bookingHelpers';
import PackageIcon from './PackageIcon';
import ComboCelebration from './ComboCelebration';

const schema = z.object({
  name: z.string().trim().min(2, 'Enter your full name').max(80, 'Up to 80 letters').regex(/^[\p{L}\p{M} .'-]+$/u, 'Use letters only'),
  mobile: z.string().refine((v) => isIndianMobile(v), 'Enter your 10-digit WhatsApp number'),
  consent: z.literal(true, { errorMap: () => ({ message: 'Please accept to continue' }) }),
});

// HFHU look (booking page, register form, confirmation): green lattice frame, ivory panel with a gold border, serif
// headings, gold "PASS n" hexagons. Every text wraps (no truncation) so it reads fully on phones.
const LATTICE = `url("data:image/svg+xml,${encodeURIComponent(
  '<svg xmlns="http://www.w3.org/2000/svg" width="44" height="44" viewBox="0 0 44 44"><rect width="44" height="44" fill="#0c5b31"/>'
  + '<g fill="none" stroke="#d9b347" stroke-width="2.2"><path d="M22 3 L28 16 L41 22 L28 28 L22 41 L16 28 L3 22 L16 16 Z"/>'
  + '<path d="M0 0 L8 8 M44 0 L36 8 M0 44 L8 36 M44 44 L36 36"/><rect x="15" y="15" width="14" height="14" transform="rotate(45 22 22)"/></g></svg>',
)}")`;
const STYLES = `
  @import url('https://fonts.googleapis.com/css2?family=Noto+Serif:ital,wght@0,600;0,700;0,800;1,600&display=swap');
  .hf-frame { background-color:#0c5b31; background-image:${LATTICE}; background-size:44px 44px; color:#1f2937; }
  .hf-panel { background: linear-gradient(180deg,#fdf9ef 0%,#f8f0dd 100%); border:3px solid #cfa43f; box-shadow: inset 0 0 0 3px #fff6dc, inset 0 0 0 5px #e7c873, 0 10px 30px -10px rgba(0,0,0,.45); }
  .hf-serif { font-family: 'Noto Serif', Georgia, 'Times New Roman', serif; }
  .hf-card { background: linear-gradient(180deg,#fffdf6,#fbf4e3); border:1.5px solid #e2c77a; box-shadow: 0 6px 14px -10px rgba(91,69,16,.55); }
  .hf-box { background:#fffaf0; border:2px solid #d9b65c; box-shadow: inset 0 0 0 2px #fff7e2; }
  .bk-gold { background: linear-gradient(180deg,#fbe39a 0%,#e4b444 100%); color:#3b2a05; box-shadow: inset 0 1px 0 rgba(255,255,255,.6), 0 2px 6px -2px rgba(91,69,16,.6); }
  .bk-green { background: linear-gradient(180deg,#127040 0%,#0a4a2b 100%); }
  .hf-btn { background: linear-gradient(180deg,#147545 0%,#0a4a2b 100%); border:3px solid #d9b347; box-shadow: inset 0 0 0 2px #0a4a2b, 0 8px 18px -8px rgba(10,74,43,.8); }
  .hf-hex { clip-path: polygon(50% 0, 100% 25%, 100% 75%, 50% 100%, 0 75%, 0 25%); background: linear-gradient(160deg,#f6d77d,#b8892c); }
  .hf-hex > span { clip-path: polygon(50% 0, 100% 25%, 100% 75%, 50% 100%, 0 75%, 0 25%); background: linear-gradient(180deg,#fffaf0,#f7ead0); }
  .bk-form input, .bk-form select, .bk-form .field-base { background:#fffdf7 !important; color:#1f2937 !important; border:1.5px solid #d9b65c !important; border-radius:12px !important; font-family:'Noto Serif', Georgia, serif; font-size:17px; }
  .bk-form label, .bk-form .label-base { color:#0b4d2c !important; font-family:'Noto Serif', Georgia, serif; font-weight:700; font-size:16px; }
  .bk-form .text-muted { color:#6b7280 !important; }
  .bk-form .text-ink { color:#1f2937 !important; }
  .bk-form .bg-surface-2 { background:#f6ecd4 !important; color:#1f2937 !important; font-weight:700; }
  .bk-strike { position: relative; display: inline-block; }
  .bk-strike::after { content: ''; position: absolute; left: -4%; right: -4%; top: 52%; height: 2px; background: #d23b2f; transform: rotate(-8deg); }
  @keyframes bkUp { from { opacity:0; transform: translateY(12px) } to { opacity:1; transform:none } }
  .bk-up { animation: bkUp .5s cubic-bezier(.2,.8,.2,1) both; }
  .bk-tap { transition: transform .15s, box-shadow .2s, border-color .2s; }
  .bk-tap:active { transform: scale(.985); }
  @keyframes bkSheet { from { transform: translateY(100%) } to { transform: none } }
  .bk-sheet { animation: bkSheet .3s cubic-bezier(.2,.8,.2,1) both; }
  @keyframes hfPop { 0% { transform: scale(.4); opacity:0 } 70% { transform: scale(1.1) } 100% { transform: scale(1); opacity:1 } }
  .hf-pop { animation: hfPop .6s cubic-bezier(.2,.8,.2,1) both; }
  @media (prefers-reduced-motion: reduce) { .bk-up, .bk-sheet, .hf-pop { animation: none } .bk-tap { transition: none } }
`;

const img = (u) => (u && u.startsWith('/api/') ? regApi.publicUrl(u) : u);
/** The register form and confirmation use the plain "HAJJ 2027" banner of the same artwork. */
const subBanner = (u) => (u && u.includes('banner-special-offer') ? u.replace('banner-special-offer', 'banner-hajj-2027') : u);

/** Green lattice frame around an ivory panel with a gold border, as in the HFHU artwork. */
function Shell({ children, banner, footer = true, supportLine }) {
  return (
    <div className="hf-frame min-h-screen px-1.5 py-1.5 sm:px-4 sm:py-4">
      <style>{STYLES}</style>
      <div className="hf-panel mx-auto w-full max-w-xl overflow-hidden rounded-[26px] pb-5">
        {banner && <img src={img(banner)} alt="" className="block h-auto w-full" />}
        {children}
        {footer && (
          <div className="hf-serif mt-5 px-4 text-center text-[13px] text-[#374151]">
            <Ornament><span className="px-1">Secure booking • Payments by Razorpay</span></Ornament>
            {supportLine && <p className="mt-1 break-words font-bold text-[#0b4d2c]">{supportLine.replace(/\s*:\s*/, ': ')}</p>}
          </div>
        )}
      </div>
    </div>
  );
}

/** ——◇ text ◇—— gold divider. */
function Ornament({ children, className = '' }) {
  return (
    <div className={`flex items-center justify-center gap-2 ${className}`}>
      <span className="h-[2px] min-w-[18px] flex-1 bg-gradient-to-r from-transparent to-[#c9a24b]" aria-hidden />
      <span className="h-2 w-2 shrink-0 rotate-45 border-2 border-[#c9a24b] bg-[#fff6dc]" aria-hidden />
      {children}
      {children && <span className="h-2 w-2 shrink-0 rotate-45 border-2 border-[#c9a24b] bg-[#fff6dc]" aria-hidden />}
      <span className="h-[2px] min-w-[18px] flex-1 bg-gradient-to-l from-transparent to-[#c9a24b]" aria-hidden />
    </div>
  );
}

/** Gold hexagon "PASS n". */
function PassHex({ n, size = 'md' }) {
  const s = size === 'xs' ? 'h-[42px] w-[36px]' : size === 'sm' ? 'h-[50px] w-[44px]' : 'h-[64px] w-[56px]';
  return (
    <span className={`hf-hex hf-serif inline-flex shrink-0 items-center justify-center p-[3px] ${s}`} aria-label={`Pass ${n}`}>
      <span className="flex h-full w-full flex-col items-center justify-center leading-none text-[#0b3d23]">
        <span className={size === 'md' ? 'text-[11px] font-bold' : size === 'sm' ? 'text-[9px] font-bold' : 'text-[7.5px] font-bold'}>PASS</span>
        <span className={size === 'md' ? 'text-[28px] font-extrabold' : size === 'sm' ? 'text-[20px] font-extrabold' : 'text-[17px] font-extrabold'}>{n}</span>
      </span>
    </span>
  );
}

function Hero({ f, onFullDetails }) {
  return (
    <header className="bk-up">
      {!f.heroImageUrl && (
        <div className="bk-green px-4 py-8 text-center text-white">
          {f.businessName && <p className="text-xs font-semibold uppercase tracking-[0.25em] text-[#f3d77a]">{f.businessName}</p>}
          <p className="hf-serif mt-1 text-2xl font-extrabold leading-tight">{f.title}</p>
        </div>
      )}
      <div className="px-3 pt-3 text-center">
        <h1 className="hf-serif whitespace-nowrap text-[clamp(18px,6.3vw,40px)] font-extrabold leading-tight tracking-[-0.01em] text-[#0b3d23]">{f.pageHeading || f.title}</h1>
        <Ornament className="mx-auto mt-1 max-w-sm" />
        {onFullDetails && (
          <button type="button" onClick={onFullDetails}
                  className="hf-serif bk-tap mx-auto mt-2 inline-flex min-h-tap items-center gap-2 rounded-full border-2 border-[#c9a24b] bg-[#fffaf0] px-5 text-[15px] font-bold text-[#0b4d2c] shadow-sm">
            <ListChecks className="h-5 w-5 text-[#a8873d]" aria-hidden /> Full Details
          </button>
        )}
      </div>
    </header>
  );
}

function PriceBlock({ p }) {
  const free = Number(p.salePrice) === 0;
  if (free) return <span className="hf-serif rounded-lg bg-gradient-to-b from-[#16804a] to-[#0a4a2b] px-2 py-1 text-[15px] font-extrabold tracking-wide text-white shadow min-[400px]:px-3 min-[400px]:text-[17px] sm:px-5 sm:text-[24px]">FREE</span>;
  return (
    <span className="hf-serif flex flex-col items-center gap-0.5">
      <span className="rounded-md bg-[#f4e3ad] px-1.5 text-[10px] font-bold text-[#5c4510] sm:text-[13px]">PAID</span>
      {Number(p.mrp) > Number(p.salePrice) && <span className="bk-strike text-[12px] font-bold text-[#5b4a2a] sm:text-[16px]">{rupees(p.mrp)}</span>}
      <span className="bk-gold rounded-lg px-1.5 py-0.5 text-[15px] font-extrabold leading-tight min-[400px]:text-[17px] sm:px-3 sm:text-[24px]">{rupees(p.salePrice)}</span>
    </span>
  );
}

function PassCard({ p, n, on, onToggle, onInfo, index }) {
  return (
    <div className={`hf-card bk-tap bk-up relative flex items-stretch rounded-2xl ${on ? 'ring-[3px] ring-[#0b5a33]' : ''}`} style={{ animationDelay: `${index * 60}ms` }}>
      <button type="button" onClick={onToggle} aria-pressed={on}
              className="grid min-w-0 flex-1 grid-cols-[54px_auto_minmax(0,1fr)_auto] items-center gap-1.5 py-1.5 pl-1.5 text-left min-[400px]:grid-cols-[72px_auto_minmax(0,1fr)_auto] sm:grid-cols-[120px_auto_minmax(0,1fr)_auto] sm:gap-3 sm:py-2.5 sm:pl-2.5">
        <span className="relative block">
          {p.imageUrl ? <img src={img(p.imageUrl)} alt="" className="aspect-[4/3] w-full rounded-lg object-cover sm:rounded-xl" loading="lazy" />
            : <span className="flex aspect-[4/3] w-full items-center justify-center rounded-lg bg-[#f4ecd9] text-[#a8873d]"><PackageIcon name={p.icon} className="h-7 w-7" /></span>}
          <span className={`absolute -left-1 -top-1 flex h-5 w-5 items-center justify-center rounded-full border-2 border-white shadow ${on ? 'bg-[#0b5a33] text-white' : 'bg-white/90 text-transparent'}`}>
            <Check className="h-3 w-3" aria-hidden />
          </span>
        </span>
        <span className="border-r border-[#e2c77a] pr-1.5 sm:hidden"><PassHex n={n} size="xs" /></span>
        <span className="hidden border-r border-[#e2c77a] pr-3 sm:block"><PassHex n={n} /></span>
        <span className="min-w-0">
          <span className="hf-serif block break-words text-[14px] font-extrabold leading-tight text-[#0b3d23] min-[400px]:text-[15.5px] sm:text-[20px]">{p.name}</span>
          {p.description && <span className="hf-serif mt-0.5 block break-words text-[11.5px] leading-snug text-[#374151] min-[400px]:text-[12.5px] sm:text-[14px]">{p.description}</span>}
        </span>
        <span className="flex items-center border-l border-[#e2c77a] pl-1.5 sm:pl-2"><PriceBlock p={p} /></span>
      </button>
      <button type="button" onClick={onInfo} aria-label={`${p.name}: full details`}
              className="flex w-7 shrink-0 items-center justify-center rounded-r-2xl text-[#0b3d23] sm:w-10">
        <ChevronRight className="h-5 w-5 sm:h-6 sm:w-6" aria-hidden />
      </button>
    </div>
  );
}

function ComboCard({ combo, on, onToggle }) {
  return (
    <button type="button" onClick={onToggle} aria-pressed={on}
            className={`hf-btn bk-tap bk-up grid w-full grid-cols-[minmax(0,1fr)_auto] items-center gap-3 rounded-2xl px-3 py-3 text-left text-white sm:px-5 ${on ? 'ring-4 ring-[#f3d27a]' : ''}`}>
      <span className="hf-serif min-w-0 border-r border-[#d9b347]/70 pr-3">
        <span className="block break-words text-[17px] font-extrabold leading-snug sm:text-[24px]">{combo.title}</span>
        {combo.subtitle && <span className="mt-0.5 block break-words text-[13px] leading-snug text-white/90 sm:text-[17px]">{combo.subtitle}</span>}
        <span className="mt-1 inline-flex items-center gap-1 text-[11px] font-semibold text-[#f7dc8c]">{on ? <><Check className="h-3.5 w-3.5" aria-hidden /> Combo selected</> : 'Tap to select all three'}</span>
      </span>
      <span className="bk-gold hf-serif flex flex-col items-center rounded-xl px-3 py-1.5">
        {combo.mrp && <span className="bk-strike text-[14px] font-bold sm:text-[18px]">{rupees(combo.mrp)}</span>}
        <span className="text-[22px] font-extrabold leading-tight sm:text-[30px]">{rupees(combo.price)}</span>
      </span>
    </button>
  );
}

function FeeBox({ p, note }) {
  const parts = (p.description || '').split(/\s*•\s*/).filter(Boolean);
  return (
    <div className="hf-box bk-up hf-serif rounded-2xl px-3 py-3 text-center">
      <p className="break-words text-[18px] font-extrabold leading-snug text-[#0b3d23] sm:text-[24px]">{rupees(p.salePrice)} {p.name}</p>
      {parts.length > 0 && (
        <p className="mt-1 flex flex-wrap items-center justify-center gap-x-2 gap-y-0.5 break-words text-[13px] text-[#374151] sm:text-[16px]">
          {parts.map((t, i) => (
            <span key={i} className="inline-flex items-center gap-2">{i > 0 && <span className="h-2 w-2 rotate-45 bg-[#e2b443]" aria-hidden />}{t}</span>
          ))}
        </p>
      )}
      {note && (
        <>
          <Ornament className="mx-auto mt-2 max-w-xs" />
          <p className="mt-1 break-words text-[13px] italic text-[#374151] sm:text-[15px]">{note}</p>
        </>
      )}
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

/** Stops grouped by round / day, in order. */
function groupRounds(stops) {
  const out = [];
  for (const x of stops || []) {
    const round = x.round || '';
    if (!out.length || out[out.length - 1].round !== round) out.push({ round, stops: [] });
    out[out.length - 1].stops.push(x);
  }
  return out;
}

/** "Full Details": one tab per pass; each tab shows the pass and its itinerary (from the database). */
function FullDetails({ packages, startId, onClose }) {
  const [active, setActive] = useState(startId ?? packages[0]?.id);
  const tabsRef = useRef(null);
  const panelRef = useRef(null);
  useEffect(() => {
    panelRef.current?.scrollTo?.({ top: 0 });
  }, [active]);
  useEffect(() => {
    const onKey = (e) => e.key === 'Escape' && onClose();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);
  const p = packages.find((x) => x.id === active) || packages[0];
  if (!p) return null;
  const rounds = groupRounds(p.itinerary);
  return (
    <div className="fixed inset-0 z-40 flex items-end justify-center bg-black/40 sm:items-center" onClick={onClose} role="dialog" aria-modal="true" aria-label="Full details">
      <div className="bk-sheet flex max-h-[92vh] w-full max-w-xl flex-col overflow-hidden rounded-t-3xl bg-[#fbf7ee] sm:rounded-3xl" onClick={(e) => e.stopPropagation()}>
        <div className="flex shrink-0 items-center justify-between gap-2 bg-[#0b4d2c] px-4 py-3 text-white">
          <h2 className="text-lg font-extrabold">Full Details</h2>
          <button type="button" onClick={onClose} className="flex h-10 w-10 items-center justify-center rounded-full bg-white/15" aria-label="Close"><X className="h-5 w-5" aria-hidden /></button>
        </div>
        <div ref={tabsRef} role="tablist" className="grid shrink-0 grid-cols-2 gap-2 border-b border-[#eadfc6] bg-white p-2 sm:grid-cols-4">
          {packages.map((x, i) => {
            const on = x.id === p.id;
            return (
              <button key={x.id} type="button" role="tab" data-tab={x.id} aria-selected={on} onClick={() => setActive(x.id)}
                      className={`hf-serif flex min-h-[56px] items-center gap-2 rounded-xl border-2 px-2 py-1.5 text-left ${on ? 'border-[#0b5a33] bg-[#0b5a33] text-white shadow' : 'border-[#e2c77a] bg-[#fffaf0] text-[#0b3d23]'}`}>
                <span className={`flex h-9 w-9 shrink-0 flex-col items-center justify-center rounded-lg leading-none ${on ? 'bg-white/15 text-[#f7dc8c]' : 'bg-[#f4e3ad] text-[#0b3d23]'}`}>
                  <span className="text-[8px] font-bold">PASS</span><span className="text-[17px] font-extrabold">{i + 1}</span>
                </span>
                <span className="min-w-0 break-words text-[12.5px] font-bold leading-tight">{x.name.replace(/\s*Pass$/i, '')}</span>
              </button>
            );
          })}
        </div>
        <div ref={panelRef} className="min-h-0 flex-1 overflow-y-auto p-4" role="tabpanel">
          {p.imageUrl && <img src={img(p.imageUrl)} alt="" className="mb-3 aspect-[16/9] w-full rounded-2xl object-cover" />}
          <div className="flex items-start justify-between gap-3">
            <div className="min-w-0">
              <h3 className="break-words text-xl font-extrabold text-[#0b4d2c]">{p.name}</h3>
              {p.description && <p className="mt-0.5 break-words text-sm text-[#374151]">{p.description}</p>}
            </div>
            <span className="shrink-0"><PriceBlock p={p} /></span>
          </div>
          <h4 className="mt-5 text-xs font-semibold uppercase tracking-wide text-[#8a7f6a]">Itinerary</h4>
          {rounds.length === 0 ? (
            <p className="mt-2 rounded-xl bg-white p-3 text-sm text-[#6b7280]">Itinerary jald hi update hogi.</p>
          ) : (
            <div className="mt-2 space-y-3">
              {rounds.map((g, gi) => (
                <section key={gi} className="rounded-2xl border border-[#eadfc6] bg-white p-3">
                  {g.round && <p className="mb-2 inline-block rounded-full bg-[#f4e3ad] px-3 py-1 text-xs font-bold text-[#5c4510]">{g.round}</p>}
                  <ol className="relative space-y-3 border-l-2 border-dashed border-[#c9a24b] pl-4">
                    {g.stops.map((x, i) => (
                      <li key={i} className="relative">
                        <span className="absolute -left-[23px] top-1 h-3 w-3 rounded-full border-2 border-white bg-[#0b5a33] shadow" aria-hidden />
                        <p className="break-words text-[15px] font-semibold text-[#1f2937]">
                          {x.place}{x.time && <span className="ml-2 whitespace-nowrap text-xs font-medium text-[#8a7f6a]">{x.time}</span>}
                        </p>
                        {x.note && <p className="break-words text-[13px] text-[#4b5563]">{x.note}</p>}
                      </li>
                    ))}
                  </ol>
                </section>
              ))}
            </div>
          )}
          {p.details && <p className="mt-4 whitespace-pre-line break-words text-[14px] leading-relaxed text-[#1f2937]">{p.details}</p>}
        </div>
      </div>
    </div>
  );
}

/** Pass number (1, 2, ...) of an item, from the order of the passes on the page (matched by the pass id's code). */
const passNumber = (cards, it, i) => {
  const code = (it.passNo || '').split('-').pop();
  const n = cards.findIndex((p) => p.code === code);
  return n >= 0 ? n + 1 : i + 1;
};

function SectionTitle({ children, sub }) {
  return (
    <div className="text-center">
      <Ornament><h2 className="hf-serif px-1 text-[21px] font-extrabold uppercase leading-tight tracking-wide text-[#0b3d23] sm:text-[28px]">{children}</h2></Ornament>
      {sub}
    </div>
  );
}

/** Totals of a paid booking (all its passes, combo discount, registration fee, total paid). */
function BookingSummary({ b }) {
  const all = b?.items || [];
  const passes = all.filter((it) => it.passNo);
  const fees = all.filter((it) => !it.passNo);
  const discount = Number(b.discountAmount || 0);
  const passTotal = passes.reduce((a, it) => a + Number(it.price || 0), 0) - discount;
  const money = (v) => (Number(v) > 0 ? rupees(v) : 'FREE');
  const row = 'flex items-start justify-between gap-3 border-b border-[#ecdcae] py-1.5';
  return (
    <section className="hf-box bk-up hf-serif mx-3 mt-4 rounded-3xl p-4 sm:mx-4">
      <SectionTitle sub={<span className="mt-1 inline-block rounded-full border-2 border-[#d9b65c] bg-[#fbecc0] px-4 py-0.5 text-[13px] font-extrabold text-[#0b3d23]">{passes.length} PASSES SELECTED</span>}>
        Booking Summary
      </SectionTitle>
      <ul className="mt-2 text-[15px] text-[#1f2937]">
        {passes.map((it) => (
          <li key={it.id} className={row}><span className="min-w-0 break-words">{it.name}</span><span className="shrink-0 font-bold">{money(it.price)}</span></li>
        ))}
        {discount > 0 && <li className={`${row} text-[#0b6b3a]`}><span className="min-w-0 break-words">Combo Discount</span><span className="shrink-0 font-bold">– {rupees(discount)}</span></li>}
        <li className={`${row} font-bold`}><span>Passes Total</span><span>{money(passTotal)}</span></li>
        {fees.map((it) => (
          <li key={it.id} className={row}><span className="min-w-0 break-words">{it.name}</span><span className="shrink-0 font-bold">{rupees(it.price)}</span></li>
        ))}
      </ul>
      <div className="hf-btn mt-3 flex items-center justify-between gap-3 rounded-2xl px-4 py-2.5 text-white">
        <span className="text-[19px] font-extrabold uppercase tracking-wide sm:text-[24px]">Total Paid</span>
        <span className="text-[22px] font-extrabold sm:text-[28px]">{money(b.total)}</span>
      </div>
    </section>
  );
}

function Downloads({ done, cards }) {
  const b = done.booking;
  const items = (b?.items || []).filter((it) => it.passNo);
  const invoices = b.invoices?.length ? b.invoices : b.invoiceUrl ? [{ invoiceNo: null, url: b.invoiceUrl }] : [];
  return (
    <section className="hf-box bk-up hf-serif mx-3 mt-4 rounded-3xl p-4 text-left sm:mx-4">
      <SectionTitle sub={<p className="mt-1 break-words text-[14px] text-[#374151]">Har pass ka apna QR code hai. Entry par wahi pass dikhayein.</p>}>Aapke Passes</SectionTitle>
      <ul className="mt-3 space-y-2">
        {items.map((it, i) => (
          <li key={it.id} className="hf-card flex items-center gap-3 rounded-2xl p-2 pr-3">
            <span className="border-r border-[#e2c77a] pr-2"><PassHex n={passNumber(cards, it, i)} size="sm" /></span>
            <span className="min-w-0 flex-1">
              <span className="block break-words text-[15.5px] font-extrabold leading-snug text-[#0b3d23]">{/pass$/i.test(it.name) ? it.name : `${it.name} Pass`}</span>
              <span className="block break-words text-[13px] text-[#4b5563]">{it.passNo}</span>
            </span>
            {it.passUrl && (
              <a href={`${regApi.publicUrl(it.passUrl)}?download=true`} aria-label={`Download ${it.name}`}
                 className="hf-btn inline-flex h-11 w-14 shrink-0 items-center justify-center rounded-xl text-white">
                <Download className="h-5 w-5" aria-hidden />
              </a>
            )}
          </li>
        ))}
      </ul>
      <div className="mt-3 grid grid-cols-1 gap-2 sm:grid-cols-2">
        {b.passesPdfUrl && (
          <a href={regApi.publicUrl(b.passesPdfUrl)} className="inline-flex min-h-[50px] items-center justify-center gap-2 rounded-2xl border-2 border-[#0b5a33] bg-white px-4 text-[17px] font-extrabold text-[#0b3d23]">
            <FileText className="h-5 w-5 text-[#0b5a33]" aria-hidden /> Sabhi Passes (PDF)
          </a>
        )}
        {invoices.map((inv, n, all) => (
          <a key={inv.url} href={regApi.publicUrl(inv.url)} className="inline-flex min-h-[50px] items-center justify-center gap-2 rounded-2xl border-2 border-[#d9b65c] bg-[#fdf3d8] px-4 text-center text-[17px] font-extrabold text-[#7a5f22]">
            <FileText className="h-5 w-5 shrink-0" aria-hidden /> {all.length > 1 ? `Invoice ${n + 1} (PDF)` : 'Invoice (PDF)'}
          </a>
        ))}
      </div>
    </section>
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
  const closeInfo = useCallback(() => setInfo(null), []);
  const [addOnNote, setAddOnNote] = useState(null);
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
    onSuccess: (res) => {
      if (res.paymentStatus === 'PENDING' && res.payment) {
        // Same number again: the passes join the existing booking and the registration fee is not charged twice.
        if (packages.length && Number(res.amount) !== Number(amount)) {
          setAddOnNote(`Is number ki booking pehle se hai — naye pass usi booking mein judenge. ${rupees(feeTotal)} registration dobara nahi lagega. Payable: ${rupees(res.amount)}`);
        }
        pay(res);
      } else {
        setDone(res);
        setSheet(false);
      }
    },
    onError: (e) => {
      const fields = getFieldErrors(e);
      if (fields.ownedPackageIds) {
        // Passes this number already has: take them off and let the person pay for the rest.
        const owned = String(fields.ownedPackageIds).split(',').map(Number);
        setSelected((cur) => (cur || []).filter((id) => !owned.includes(id)));
        delete fields.ownedPackageIds;
      }
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
      <Shell banner={subBanner(f.heroImageUrl)} supportLine={f.supportLine}>
        <section className="hf-box bk-up hf-serif mx-3 mt-3 rounded-3xl px-4 pb-5 pt-4 text-center sm:mx-4">
          <span className="hf-pop mx-auto flex h-[74px] w-[74px] items-center justify-center rounded-full border-4 border-[#d9b347] bg-gradient-to-b from-[#16804a] to-[#0a4a2b] shadow-lg">
            <Check className="h-10 w-10 text-white" strokeWidth={3.5} aria-hidden />
          </span>
          <h1 className="mt-2 text-[30px] font-extrabold leading-tight text-[#0b3d23] sm:text-[38px]">{done.paymentStatus === 'PAID' ? 'Booking Confirmed!' : 'Registered!'}</h1>
          <Ornament className="mx-auto max-w-sm" />
          <p className="mt-1 break-words text-[16px] text-[#1f2937]">{done.name}, aapki booking confirm ho gayi hai.</p>
          {done.passNo && (
            <p className="mt-2 inline-block rounded-full border-2 border-[#d9b65c] bg-[#fffaf0] px-4 py-1 text-[16px] text-[#1f2937]">Booking No. <b>{done.passNo}</b></p>
          )}
          {done.paymentStatus === 'PAID' && done.amount && (
            <p className="mx-auto mt-2 block w-fit rounded-full bg-gradient-to-b from-[#16804a] to-[#0a4a2b] px-5 py-1.5 text-[17px] font-extrabold text-white shadow">Payment received: {rupees(done.amount)}</p>
          )}
          <p className="mt-2 break-words text-[15px] text-[#374151]">Aapke passes WhatsApp par bheje ja rahe hain.<br />Agar na milein, neeche button par tap karein.</p>
          {done.whatsappLink && (
            <a href={done.whatsappLink} target="_blank" rel="noopener noreferrer"
               className="hf-btn mt-3 inline-flex min-h-[56px] w-full items-center justify-center gap-3 rounded-full px-4 text-[18px] font-extrabold text-white">
              <MessageCircle className="h-7 w-7" aria-hidden /> WhatsApp par passes paayein
            </a>
          )}
          {single && (
            <>
              <img src={regApi.publicUrl(done.passUrl)} alt={`Pass ${done.passNo}`} className="mx-auto mt-4 w-full max-w-xs rounded-xl border border-[#e2d6bb] shadow" />
              <a href={`${regApi.publicUrl(done.passUrl)}?download=true`} className="mt-3 inline-flex min-h-tap w-full items-center justify-center gap-2 rounded-xl border-2 border-[#0b5a33] px-4 py-3 font-bold text-[#0b5a33]">
                <Download className="h-5 w-5" aria-hidden /> Download pass
              </a>
            </>
          )}
        </section>
        {done.booking && <BookingSummary b={done.booking} />}
        {done.booking && <Downloads done={done} cards={cards} />}
      </Shell>
    );
  }

  const legalLink = (kind, label) => <Link to={`/r/${slug}/${kind}`} target="_blank" className="font-semibold text-[#0b4d2c] underline decoration-[#c9a24b] underline-offset-2">{label}</Link>;
  const comboOn = comboApplies(packages, sel, combo);
  const legal = (kind, label) => (f[kind === 'terms' ? 'hasTerms' : 'hasPrivacy'] ? legalLink(kind, label) : label);
  return (
    <Shell banner={f.heroImageUrl} footer={false}>
      <Hero f={f} onFullDetails={cards.length ? () => setInfo(cards[0]) : null} />
      <div className="space-y-2.5 px-2 pt-3 sm:px-4">
        {f.description && <Description text={f.description} />}
        {cards.map((p, i) => (
          <PassCard key={p.id} p={p} n={i + 1} index={i} on={sel.includes(p.id)} onToggle={() => setSelected(toggleId(sel, p.id))} onInfo={() => setInfo(p)} />
        ))}
        {combo && <ComboCard combo={combo} on={comboOn} onToggle={() => setSelected(toggleCombo(packages, sel, combo))} />}
        {fees.map((p, i) => <FeeBox key={p.id} p={p} note={i === fees.length - 1 ? f.pageNote : null} />)}
        {!packages.length && f.feeAmount && <FeeBox p={{ salePrice: f.feeAmount, name: f.feeLabel || 'Registration fee' }} note={f.pageNote} />}
        {!fees.length && packages.length > 0 && f.pageNote && <p className="hf-serif break-words px-2 text-center text-[14px] italic text-[#374151]">{f.pageNote}</p>}
        {!f.open ? (
          <Alert variant="warning" title={f.closedReason || 'Bookings are closed'} />
        ) : (
          <>
            {packages.length > 0 && (
              <p className="hf-serif text-center text-[14px] text-[#374151]">
                Selected Pass: <b className="text-[#0b3d23]">{picked.length}</b> · Total <b className="text-[#0b3d23]">{price(passTotal)}</b>
                {totals.comboDiscount > 0 && <> · Combo Discount <b className="text-[#0b6b3a]">{rupees(totals.comboDiscount)}</b></>}
              </p>
            )}
            <button type="button" onClick={() => setSheet(true)}
                    className="hf-btn hf-serif bk-tap flex min-h-[66px] w-full items-center justify-center gap-3 rounded-full text-[26px] font-extrabold text-white sm:text-[34px]">
              <span className="hidden text-[#f3d27a] min-[380px]:inline" aria-hidden>—◇</span>{f.ctaLabel || 'Register'}<span className="hidden text-[#f3d27a] min-[380px]:inline" aria-hidden>◇—</span>
            </button>
          </>
        )}
        <div className="hf-serif pt-1 text-center text-[14px] font-bold text-[#0b3d23]">
          <Ornament className="mx-auto max-w-xs">
            {f.hasTerms ? <Link to={`/r/${slug}/terms`} target="_blank" className="px-1 underline decoration-[#c9a24b] underline-offset-2">T&amp;C Apply</Link> : <span className="px-1">T&amp;C Apply</span>}
          </Ornament>
        </div>
      </div>

      {info && <FullDetails packages={cards} startId={info.id} onClose={closeInfo} />}
      {celebrate && <ComboCelebration discount={totals.comboDiscount} onClose={endCelebrate} />}

      {sheet && (
        <div className="fixed inset-0 z-40 overflow-y-auto" role="dialog" aria-modal="true" aria-label="Registration">
          <div className="bk-sheet bk-form">
            <Shell banner={subBanner(f.heroImageUrl)} footer={false}>
              <section className="hf-box hf-serif mx-3 mt-3 rounded-3xl p-4 sm:mx-4">
                <div className="relative">
                  <Ornament className="pr-12"><h2 className="px-1 text-[28px] font-extrabold leading-tight text-[#0b3d23] sm:text-[36px]">{f.ctaLabel || 'Register'}</h2></Ornament>
                  <button type="button" onClick={() => setSheet(false)} aria-label="Close"
                          className="absolute right-0 top-0 flex h-11 w-11 items-center justify-center rounded-full border-2 border-[#d9b65c] bg-[#fffaf0] text-[#0b3d23]"><X className="h-6 w-6" aria-hidden /></button>
                </div>
                <p className="mt-1 text-center text-[15px] text-[#1f2937]">Apni details check karke payment karein.</p>
                {packages.length > 0 && (
                  <div className="hf-box mt-3 rounded-2xl p-3">
                    <Ornament><span className="rounded-full border-2 border-[#d9b65c] bg-[#fbecc0] px-3 py-0.5 text-[15px] font-extrabold uppercase text-[#0b3d23]">Selected Passes ({picked.length})</span></Ornament>
                    <ul className="mt-2 text-[15px] text-[#1f2937]">
                      {picked.length === 0 && <li className="py-2 text-[#6b7280]">No pass selected</li>}
                      {picked.map((p) => (
                        <li key={p.id} className="flex items-start justify-between gap-3 border-b border-[#ecdcae] py-1.5">
                          <span className="min-w-0 break-words">{p.name}</span><span className="shrink-0 font-bold">{price(Number(p.salePrice))}</span>
                        </li>
                      ))}
                      {totals.comboDiscount > 0 && (
                        <li className="flex items-start justify-between gap-3 border-b border-[#ecdcae] py-1.5 text-[#0b6b3a]"><span className="min-w-0 break-words">Combo Discount</span><span className="shrink-0 font-bold">– {rupees(totals.comboDiscount)}</span></li>
                      )}
                      <li className="flex items-center justify-between gap-3 border-b border-[#ecdcae] py-1.5 font-bold"><span>Passes Total</span><span>{price(passTotal)}</span></li>
                      {feeItems.map((p) => (
                        <li key={p.id} className="flex items-start justify-between gap-3 py-1.5">
                          <span className="min-w-0 break-words">{p.name}</span><span className="shrink-0 font-bold">{rupees(p.salePrice)}</span>
                        </li>
                      ))}
                    </ul>
                    <div className="hf-btn mt-2 flex items-center justify-between gap-3 rounded-2xl px-4 py-2.5 text-white">
                      <span className="text-[18px] font-extrabold uppercase tracking-wide sm:text-[24px]">Total Payable</span>
                      <span className="text-[22px] font-extrabold sm:text-[28px]">{amount > 0 ? rupees(amount) : 'FREE'}</span>
                    </div>
                  </div>
                )}
                <form id="bk-form" className="mt-4 space-y-4" noValidate onSubmit={handleSubmit((v) => submit.mutate(v))}>
                  <Input label="Full name" required autoComplete="name" maxLength={80} placeholder="As on your ID" error={formState.errors.name?.message} {...register('name')} />
                  <Controller name="mobile" control={control} render={({ field }) => (
                    <PhoneInput label="WhatsApp mobile number" required value={field.value} onChange={field.onChange}
                                hint="Aapke passes isi WhatsApp number par bheje jayenge." error={formState.errors.mobile?.message} />
                  )} />
                  {f.collectPhoto && <FileUpload label="Your photo (optional)" value={photo} onChange={setPhoto} accept="image/jpeg,image/png" maxSizeMB={5} hint="JPG or PNG, up to 5 MB." />}
                  <Controller name="consent" control={control} render={({ field }) => (
                    <Checkbox checked={field.value} onChange={(e) => field.onChange(e.target.checked)} error={formState.errors.consent?.message}
                              label={<span className="hf-serif text-[15px] font-normal text-[#1f2937]">{f.consentText}
                                {(f.hasTerms || f.hasPrivacy) && <span className="mt-1 block">Main {legal('terms', 'Terms & Conditions')} aur {legal('privacy', 'Privacy Policy')} sweekar karta/karti hoon.</span>}</span>} />
                  )} />
                  <input type="text" name="website" tabIndex={-1} autoComplete="off" className="hidden" aria-hidden />
                  {addOnNote && <Alert variant="info" title={addOnNote} />}
                  {formState.errors.root && <Alert variant="error" title={formState.errors.root.message} />}
                  <Button type="submit" fullWidth size="lg" loading={submit.isPending || paying}
                          className="hf-btn hf-serif !min-h-[62px] !rounded-full !bg-transparent !text-[26px] !font-extrabold">
                    {amount > 0 ? `Pay ${rupees(amount)}` : 'Get my pass'}
                  </Button>
                </form>
                <div className="mt-4 text-center text-[14px] text-[#374151]">
                  <Ornament><span className="px-1">Secure payment • Razorpay</span></Ornament>
                  {f.supportLine && <p className="mt-1 break-words font-bold text-[#0b3d23]">{f.supportLine.replace(/\s*:\s*/, ': ')}</p>}
                </div>
              </section>
            </Shell>
          </div>
        </div>
      )}
    </Shell>
  );
}
