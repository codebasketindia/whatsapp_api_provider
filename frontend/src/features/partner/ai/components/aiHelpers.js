/** Max history turns sent to the AI test (AI8); the server uses only the last few anyway. */
export const TEST_HISTORY_LIMIT = 10;
export const KNOWLEDGE_MAX = 4000;

/**
 * Playground chat → AI8 history: [{ role: customer|business, text }], oldest first.
 * Skips failed/pending bubbles and empty text; keeps only the last `limit` turns.
 */
export function buildAiHistory(chat = [], limit = TEST_HISTORY_LIMIT) {
  return chat
    .filter((m) => !m.error && !m.pending && (m.role === 'customer' || m.role === 'business'))
    .map((m) => ({ role: m.role, text: String(m.text || '').trim() }))
    .filter((m) => m.text)
    .slice(-limit);
}

/** First lines of a knowledge item for the list. */
export function knowledgeSnippet(content = '', max = 140) {
  const flat = String(content).replace(/\s+/g, ' ').trim();
  return flat.length > max ? `${flat.slice(0, max - 1)}…` : flat;
}

/** AiAssistantDto → setup form values. */
export function assistantToForm(a = {}) {
  return {
    enabled: Boolean(a.enabled),
    allNumbers: a.senderIds == null,
    senderIds: a.senderIds || [],
    businessName: a.businessName || '',
    instructions: a.instructions || '',
    language: a.language || 'AUTO',
    tone: a.tone || 'FRIENDLY',
    fallbackMessage: a.fallbackMessage || '',
    handoverKeywords: a.handoverKeywords || [],
    maxRepliesPerChatPerDay: a.maxRepliesPerChatPerDay ?? 10,
  };
}

/** Setup form values → AI2 body (editable fields only). */
export function formToAssistant(v) {
  return {
    enabled: v.enabled,
    senderIds: v.allNumbers ? null : v.senderIds.map(Number),
    businessName: v.businessName.trim(),
    instructions: v.instructions.trim(),
    language: v.language,
    tone: v.tone,
    fallbackMessage: v.fallbackMessage.trim(),
    handoverKeywords: v.handoverKeywords,
    maxRepliesPerChatPerDay: Number(v.maxRepliesPerChatPerDay),
  };
}

/** Is AI answering this conversation? (assistant on, number covered, chat not paused) */
export function aiActiveFor(assistant, senderId, conv = {}) {
  if (!assistant?.enabled || !assistant.platformEnabled) return false;
  if (conv.aiPaused) return false;
  return assistant.senderIds == null || assistant.senderIds.map(Number).includes(Number(senderId));
}

export const OUTCOME_LABEL = {
  REPLIED: 'AI replied',
  FALLBACK: 'Fallback sent',
  HANDOVER: 'Handed to you',
  SKIPPED: 'Skipped',
  ERROR: 'Error',
};
