import { useEffect, useRef } from 'react';
import { PartyPopper } from 'lucide-react';
import animationData from './comboCelebration.json';
import { rupees } from './bookingHelpers';

const COLORS = ['#0b5a33', '#13894f', '#e2b443', '#f7dc8c', '#d23b2f', '#ffffff'];

/** Confetti burst on a full-screen canvas (no library). Stops by itself after ~3 s. */
function Confetti() {
  const ref = useRef(null);
  useEffect(() => {
    const c = ref.current;
    const ctx = c?.getContext?.('2d');
    if (!ctx) return undefined;
    const dpr = Math.min(2, window.devicePixelRatio || 1);
    const w = window.innerWidth, h = window.innerHeight;
    c.width = w * dpr; c.height = h * dpr; ctx.scale(dpr, dpr);
    const parts = Array.from({ length: 160 }, (_, i) => {
      const fromLeft = i % 2 === 0;
      return {
        x: fromLeft ? -10 : w + 10, y: h * 0.62,
        vx: (fromLeft ? 1 : -1) * (3 + Math.random() * 7), vy: -(9 + Math.random() * 9),
        s: 5 + Math.random() * 6, r: Math.random() * Math.PI, vr: (Math.random() - 0.5) * 0.35,
        c: COLORS[i % COLORS.length], round: i % 4 === 0,
      };
    });
    let raf; const start = performance.now();
    const tick = (now) => {
      const t = now - start;
      ctx.clearRect(0, 0, w, h);
      ctx.globalAlpha = t > 2200 ? Math.max(0, 1 - (t - 2200) / 800) : 1;
      for (const p of parts) {
        p.vy += 0.32; p.vx *= 0.985; p.x += p.vx; p.y += p.vy; p.r += p.vr;
        ctx.save(); ctx.translate(p.x, p.y); ctx.rotate(p.r); ctx.fillStyle = p.c;
        if (p.round) { ctx.beginPath(); ctx.arc(0, 0, p.s / 2, 0, Math.PI * 2); ctx.fill(); }
        else ctx.fillRect(-p.s / 2, -p.s / 4, p.s, p.s / 2);
        ctx.restore();
      }
      if (t < 3000) raf = requestAnimationFrame(tick);
    };
    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
  }, []);
  return <canvas ref={ref} className="pointer-events-none fixed inset-0 z-50 h-full w-full" aria-hidden />;
}

function Burst() {
  const ref = useRef(null);
  useEffect(() => {
    let anim, alive = true;
    import('lottie-web/build/player/lottie_light').then(({ default: lottie }) => {
      if (!alive || !ref.current) return;
      anim = lottie.loadAnimation({ container: ref.current, renderer: 'svg', loop: false, autoplay: true, animationData });
    }).catch(() => {});
    return () => { alive = false; anim?.destroy(); };
  }, []);
  return <div ref={ref} className="mx-auto h-28 w-28" aria-hidden />;
}

/** "Congratulations" when the combo is picked: Lottie star burst + confetti, closes by itself. */
export default function ComboCelebration({ discount, onClose }) {
  const reduce = typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
  useEffect(() => {
    const t = setTimeout(onClose, 3200);
    return () => clearTimeout(t);
  }, [onClose]);
  return (
    <div className="fixed inset-0 z-40 flex items-center justify-center bg-black/30 px-6" onClick={onClose} role="status" aria-live="polite">
      {!reduce && <Confetti />}
      <div className="bk-sheet relative w-full max-w-xs rounded-3xl border-2 border-[#e2b443] bg-white p-5 text-center shadow-2xl">
        {reduce ? <PartyPopper className="mx-auto h-16 w-16 text-[#c9a24b]" aria-hidden /> : <Burst />}
        <p className="text-2xl font-extrabold text-[#0b4d2c]">Congratulations!</p>
        <p className="mt-1 break-words text-[15px] text-[#374151]">
          Combo select ho gaya{discount > 0 ? <> — aapko <b className="text-[#0b5a33]">{rupees(discount)}</b> ka Combo Discount mila</> : ''}.
        </p>
      </div>
    </div>
  );
}
