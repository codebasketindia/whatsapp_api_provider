// Facebook JS SDK loader + WhatsApp Embedded Signup helpers.
let sdkPromise = null;
let initializedAppId = null;

const FB_ORIGINS = ['https://www.facebook.com', 'https://web.facebook.com'];

export function loadFacebookSdk({ appId, graphApiVersion }) {
  if (!appId) return Promise.reject(new Error('Meta App ID is not configured'));
  if (!sdkPromise) {
    sdkPromise = new Promise((resolve, reject) => {
      if (window.FB) {
        resolve(window.FB);
        return;
      }
      window.fbAsyncInit = () => resolve(window.FB);
      const script = document.createElement('script');
      script.id = 'facebook-jssdk';
      script.async = true;
      script.defer = true;
      script.crossOrigin = 'anonymous';
      script.src = 'https://connect.facebook.net/en_US/sdk.js';
      script.onerror = () => {
        sdkPromise = null;
        reject(new Error('Could not load Facebook. Check your internet connection or ad-blocker.'));
      };
      document.body.appendChild(script);
    });
  }
  return sdkPromise.then((FB) => {
    if (initializedAppId !== appId) {
      FB.init({ appId, autoLogAppEvents: true, xfbml: false, version: graphApiVersion || 'v25.0' });
      initializedAppId = appId;
    }
    return FB;
  });
}

/**
 * Parses a window "message" event from Embedded Signup.
 * Returns null when it is not an Embedded Signup event.
 */
export function parseEmbeddedSignupMessage(event) {
  if (!FB_ORIGINS.includes(event.origin)) return null;
  let payload = event.data;
  if (typeof payload === 'string') {
    try {
      payload = JSON.parse(payload);
    } catch {
      return null;
    }
  }
  if (!payload || payload.type !== 'WA_EMBEDDED_SIGNUP') return null;
  const data = payload.data || {};
  const evt = payload.event;
  if (evt === 'FINISH' || evt === 'FINISH_ONLY_WABA' || evt === 'FINISH_WHATSAPP_BUSINESS_APP_ONBOARDING') {
    return {
      type: 'FINISH',
      event: evt,
      wabaId: data.waba_id,
      phoneNumberId: data.phone_number_id,
      businessId: data.business_id,
    };
  }
  if (evt === 'CANCEL') {
    return { type: 'CANCEL', event: evt, currentStep: data.current_step, errorMessage: data.error_message, sessionId: data.session_id };
  }
  if (evt === 'ERROR') {
    return {
      type: 'ERROR',
      event: evt,
      errorMessage: data.error_message,
      errorCode: data.error_code,
      sessionId: data.session_id,
      currentStep: data.current_step,
    };
  }
  return { type: 'OTHER', event: evt, data };
}

/**
 * Launches Embedded Signup. Resolves with { code, session } where session is the
 * WA_EMBEDDED_SIGNUP FINISH info (or cancel/error info if the user stopped).
 * The code expires in ~30 seconds, so send it to the backend immediately.
 */
export function launchEmbeddedSignup({ appId, graphApiVersion, configId, featureType = '', extras = {} }) {
  return loadFacebookSdk({ appId, graphApiVersion }).then(
    (FB) =>
      new Promise((resolve, reject) => {
        let session = null;
        const onMessage = (event) => {
          const parsed = parseEmbeddedSignupMessage(event);
          if (parsed) session = parsed;
        };
        window.addEventListener('message', onMessage);
        FB.login(
          (response) => {
            // Session info message can arrive just after the login callback.
            setTimeout(() => {
              window.removeEventListener('message', onMessage);
              const code = response?.authResponse?.code;
              if (code) resolve({ code, session });
              else if (session && session.type !== 'FINISH') resolve({ code: null, session });
              else reject(Object.assign(new Error('Signup was closed before finishing.'), { session }));
            }, 400);
          },
          {
            config_id: configId,
            response_type: 'code',
            override_default_response_type: true,
            extras: { setup: {}, featureType, sessionInfoVersion: '3', ...extras },
          },
        );
      }),
  );
}
