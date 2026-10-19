// Browser storage can be unavailable (private mode, blocked site data); every access is guarded.
function safe(store) {
  return {
    get(key) {
      try {
        return store()?.getItem(key) ?? null;
      } catch {
        return null;
      }
    },
    set(key, value) {
      try {
        if (value === null || value === undefined) store()?.removeItem(key);
        else store()?.setItem(key, value);
      } catch {
        /* storage unavailable: value lives only in memory for this tab */
      }
    },
    remove(key) {
      try {
        store()?.removeItem(key);
      } catch {
        /* ignore */
      }
    },
  };
}

export const local = safe(() => window.localStorage);
export const session = safe(() => window.sessionStorage);
