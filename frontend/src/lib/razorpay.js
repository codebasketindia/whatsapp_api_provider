let scriptPromise = null;

export function loadRazorpay() {
  if (window.Razorpay) return Promise.resolve(window.Razorpay);
  if (!scriptPromise) {
    scriptPromise = new Promise((resolve, reject) => {
      const s = document.createElement('script');
      s.src = 'https://checkout.razorpay.com/v1/checkout.js';
      s.async = true;
      s.onload = () => resolve(window.Razorpay);
      s.onerror = () => {
        scriptPromise = null;
        reject(new Error('Could not load the payment page. Please check your internet connection.'));
      };
      document.body.appendChild(s);
    });
  }
  return scriptPromise;
}

/**
 * Opens Razorpay Checkout.
 * Resolves { status: 'success', razorpay_payment_id, razorpay_order_id, razorpay_signature }
 * or { status: 'dismissed' } / { status: 'failed', error } when the customer closes the window.
 * Razorpay lets the customer retry inside the same window after a failure, so a failure is
 * reported only if the window is closed afterwards.
 * The success callback is NOT proof of payment — always verify on the server.
 */
export function openCheckout(options) {
  return loadRazorpay().then(
    (Razorpay) =>
      new Promise((resolve) => {
        let settled = false;
        let lastError = null;
        const rzp = new Razorpay({
          ...options,
          handler: (resp) => {
            settled = true;
            resolve({ status: 'success', ...resp });
          },
          modal: {
            ...(options.modal || {}),
            ondismiss: () => {
              if (!settled) {
                settled = true;
                resolve(lastError ? { status: 'failed', error: lastError } : { status: 'dismissed' });
              }
            },
          },
        });
        rzp.on('payment.failed', (resp) => {
          lastError = resp?.error || { description: 'Payment failed' };
          if (options.onFailed) options.onFailed(lastError);
        });
        rzp.open();
      }),
  );
}
