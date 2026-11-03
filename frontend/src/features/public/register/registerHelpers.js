/** What the person sees about their pass right after registering. */
export function passStatusText(status) {
  switch (status) {
    case 'SENT':
      return 'Your pass has been sent to your WhatsApp.';
    case 'PENDING':
      return 'Your pass is being sent to your WhatsApp. If it does not arrive in a minute, tap the green button below.';
    case 'WAITING_FOR_CHAT':
      return 'Tap the green button below and press Send in WhatsApp — your pass will arrive instantly.';
    case 'FAILED':
      return 'We could not send it on WhatsApp right now. Tap the green button below or download it here.';
    default:
      return '';
  }
}
