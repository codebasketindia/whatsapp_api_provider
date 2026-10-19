// Plain-language explanations of Meta / WhatsApp terms, shown in InfoTip tooltips.
export const META_GLOSSARY = {
  WABA: 'WhatsApp Business Account (WABA) — the account in Meta that holds your WhatsApp numbers and message templates.',
  PHONE_NUMBER_ID: 'Phone number ID — Meta’s internal ID for a WhatsApp number. Different from the number itself.',
  QUALITY_RATING:
    'Quality rating — how recipients rated your recent messages. Green = good, Yellow = warning, Red = low. Low quality can limit how many people you can message.',
  MESSAGING_LIMIT:
    'Messaging limit — how many different people your business can start conversations with in 24 hours. Meta raises it automatically as you send good quality messages.',
  THROUGHPUT: 'Throughput — how many messages per second this number can send.',
  TEMPLATE: 'Template — a pre-approved message format. You need an approved template to message someone who has not written to you in the last 24 hours.',
  TEMPLATE_CATEGORY:
    'Template category — Marketing (offers, promotions), Utility (order, account updates) or Authentication (one-time passwords). Meta prices each category differently.',
  MARKETING: 'Marketing — promotions, offers, newsletters, announcements.',
  UTILITY: 'Utility — updates about an order, booking, account or payment the customer already started.',
  AUTHENTICATION: 'Authentication — one-time passwords (OTP) for login or verification.',
  SERVICE: 'Service — free-form replies inside the 24-hour customer service window. Free from Meta.',
  WINDOW_24H:
    '24-hour window — when a customer messages you, you can reply with any message for 24 hours. After that you must use an approved template.',
  DISPLAY_NAME: 'Display name — the business name shown to customers in WhatsApp. Meta reviews it before it is shown.',
  NAME_STATUS: 'Display name status — Meta’s review result for your display name (Approved, Pending review or Declined).',
  TWO_STEP_PIN:
    'Two-step verification PIN — a 6-digit PIN that protects your number. You need it to register the number again on another platform. Keep it safe.',
  COEXISTENCE:
    'Coexistence — keep using the WhatsApp Business app on your phone while also sending messages through this platform with the same number.',
  EMBEDDED_SIGNUP: 'Embedded Signup — Meta’s secure pop-up that connects your own WhatsApp number to this platform.',
  BUSINESS_VERIFICATION: 'Business verification — Meta confirms your business is real. Needed for higher messaging limits.',
  PAYMENT_METHOD: 'Payment method — card or credit line in Meta used to pay Meta’s message charges for your own number.',
  OPT_OUT: 'Opt-out — the customer asked not to receive marketing messages. We will skip them in campaigns.',
  HOLD: 'On hold — the price of a message is held from your balance until delivery is confirmed. Failed messages are refunded.',
  ICE_BREAKERS: 'Ice breakers — up to 4 tappable questions shown when a customer opens a chat with you for the first time.',
  COMMANDS: 'Commands — shortcuts customers can type after “/” in the chat (for example /help).',
  WELCOME_MESSAGE: 'Welcome message — lets WhatsApp tell you when a customer opens your chat for the first time so you can greet them.',
  WEBHOOK: 'Webhook — a URL on your server where we send delivery updates and incoming messages automatically.',
  API_KEY: 'API key — the username your software uses to call our API. Keep the secret private.',
  TECH_PROVIDER: 'Tech Provider billing — Meta bills the business directly for message charges; we charge only a platform fee.',
  SOLUTION_PARTNER: 'Solution Partner billing — Meta charges go through our credit line and are deducted from your wallet.',
};

export function glossary(key) {
  return META_GLOSSARY[key] || '';
}
