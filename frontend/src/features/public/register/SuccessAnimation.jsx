import { useEffect, useRef } from 'react';
import { CheckCircle2 } from 'lucide-react';
import animationData from './successAnimation.json';

/** Small Lottie check mark (light SVG player, loaded only on the success screen). Static icon when motion is reduced. */
export default function SuccessAnimation({ size = 112 }) {
  const ref = useRef(null);
  const reduce = typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
  useEffect(() => {
    if (reduce || !ref.current) return undefined;
    let anim;
    let alive = true;
    import('lottie-web/build/player/lottie_light').then(({ default: lottie }) => {
      if (!alive || !ref.current) return;
      anim = lottie.loadAnimation({ container: ref.current, renderer: 'svg', loop: false, autoplay: true, animationData });
    }).catch(() => {});
    return () => {
      alive = false;
      anim?.destroy();
    };
  }, [reduce]);
  if (reduce) return <CheckCircle2 className="mx-auto text-[#C9A24B]" style={{ width: size, height: size }} aria-hidden />;
  return <div ref={ref} className="mx-auto" style={{ width: size, height: size }} aria-hidden />;
}
