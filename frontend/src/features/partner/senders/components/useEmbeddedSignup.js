import { useCallback, useState } from 'react';
import * as sendersApi from '../../../../api/endpoints/senders';
import { launchEmbeddedSignup } from '../../../../lib/fbSdk';

export const FEATURE_TYPE = { NEW: '', EXISTING: '', COEXISTENCE: 'whatsapp_business_app_onboarding' };

/**
 * Runs Meta Embedded Signup and returns the session info + code.
 * Logs CANCEL / ERROR to the backend (E6) so support can help.
 */
export function useEmbeddedSignup() {
  const [running, setRunning] = useState(false);
  const run = useCallback(async (config, mode) => {
    setRunning(true);
    try {
      const { code, session } = await launchEmbeddedSignup({
        appId: config.appId,
        graphApiVersion: config.graphApiVersion,
        configId: config.configId,
        featureType: FEATURE_TYPE[mode] ?? '',
        extras: config.extras || {},
      });
      if (!code || !session || session.type !== 'FINISH') {
        const info = session || {};
        await sendersApi
          .logEmbeddedSignupEvent({
            event: info.type === 'ERROR' ? 'ERROR' : 'CANCEL',
            currentStep: info.currentStep || null,
            errorMessage: info.errorMessage || null,
            errorCode: info.errorCode || null,
            sessionId: info.sessionId || null,
          })
          .catch(() => {});
        return { ok: false, session: info };
      }
      return { ok: true, code, session };
    } catch (err) {
      const info = err.session || {};
      await sendersApi
        .logEmbeddedSignupEvent({ event: 'CANCEL', currentStep: info.currentStep || null, errorMessage: err.message, sessionId: info.sessionId || null })
        .catch(() => {});
      return { ok: false, session: info, error: err };
    } finally {
      setRunning(false);
    }
  }, []);
  return { run, running };
}

/** Friendly text for an unfinished signup. */
export function signupFailureText(result) {
  if (result?.error?.message?.includes('Could not load Facebook')) return result.error.message;
  const s = result?.session || {};
  if (s.type === 'ERROR') return s.errorMessage ? `Meta reported a problem: ${s.errorMessage}` : 'Meta reported a problem. Please try again.';
  if (s.currentStep) return `The Facebook window was closed at step "${String(s.currentStep).replace(/_/g, ' ').toLowerCase()}". You can start again.`;
  return 'The Facebook window was closed before finishing. You can start again.';
}
