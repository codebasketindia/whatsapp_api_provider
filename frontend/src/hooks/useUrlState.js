import { useCallback, useMemo } from 'react';
import { useSearchParams } from 'react-router-dom';

/**
 * Keeps list filters in the URL (?page=2&status=PENDING) so back/refresh keep the view.
 * Empty values are removed. Changing any filter except page resets page to 0.
 */
export function useUrlState(defaults = {}) {
  const [params, setParams] = useSearchParams();
  const state = useMemo(() => {
    const out = { ...defaults };
    params.forEach((v, k) => {
      out[k] = v;
    });
    if (out.page !== undefined) out.page = Number(out.page) || 0;
    return out;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [params]);

  const update = useCallback(
    (patch) => {
      setParams(
        (prev) => {
          const next = new URLSearchParams(prev);
          Object.entries(patch).forEach(([k, v]) => {
            if (v === undefined || v === null || v === '') next.delete(k);
            else next.set(k, String(v));
          });
          if (!('page' in patch)) next.delete('page');
          return next;
        },
        { replace: true },
      );
    },
    [setParams],
  );
  return [state, update];
}
