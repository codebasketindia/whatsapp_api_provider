package in.codebasket.cbwa.ai;

import java.util.List;

/**
 * Calls the language model chosen in Settings → AI (OpenAI or Anthropic). Implemented by
 * {@link AiModelRouter}; replaced by a mock in tests.
 */
public interface AiModelClient {

    AiResult complete(AiRequest request);

    /**
     * @param system  fixed rules + the partner's knowledge and packages (same for every customer, so it is cacheable)
     * @param context facts about this customer only (their passes and bookings), read from the database now
     * @param history earlier turns, oldest first
     * @param message the customer's new message
     */
    record AiRequest(String provider, String apiKey, String model, int maxTokens, String reasoningEffort, String system,
                     String context, List<Turn> history, String message) {
    }

    record Turn(boolean customer, String text) {
    }

    /** attachmentIds: knowledge item ids, or negative travel package ids. */
    record AiResult(String reply, List<Long> attachmentIds, boolean handover, String reason,
                    int inputTokens, int outputTokens, int cacheReadTokens, int cacheWriteTokens) {
    }

    /** One model vendor. */
    interface Provider {
        String name();

        AiResult complete(AiRequest request);
    }

    /** The model could not be reached or returned an unusable answer. */
    class AiModelException extends RuntimeException {
        public AiModelException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** The JSON every provider must return. */
    static AiResult parse(com.fasterxml.jackson.databind.ObjectMapper json, String text, int in, int out, int cr, int cw) {
        try {
            com.fasterxml.jackson.databind.JsonNode n = json.readTree(text);
            List<Long> ids = new java.util.ArrayList<>();
            // Knowledge items are referred to as "12" / "K12", travel packages as "P5" (returned as -5).
            n.path("attachments").forEach(a -> {
                String v = a.asText("").trim().toUpperCase(java.util.Locale.ROOT);
                boolean pkg = v.startsWith("P");
                String digits = v.replaceAll("[^0-9]", "");
                if (!digits.isEmpty() && digits.length() < 15) {
                    long id = Long.parseLong(digits);
                    ids.add(pkg ? -id : id);
                }
            });
            return new AiResult(n.path("reply").asText(""), ids, n.path("handover").asBoolean(false), n.path("reason").asText(null),
                    in, out, cr, cw);
        } catch (Exception e) {
            // Unparseable (e.g. cut off at the token limit): hand the chat to a human instead of guessing.
            return new AiResult("", List.of(), true, "Answer could not be read", in, out, cr, cw);
        }
    }

    /** The APIs need user/assistant turns alternating and starting with the user; merge or drop the rest. */
    static List<Turn> alternate(List<Turn> history, String message) {
        List<Turn> all = new java.util.ArrayList<>(history == null ? List.of() : history);
        all.add(new Turn(true, message));
        List<Turn> out = new java.util.ArrayList<>();
        for (Turn t : all) {
            if (t.text() == null || t.text().isBlank() || (out.isEmpty() && !t.customer())) {
                continue;
            }
            Turn last = out.isEmpty() ? null : out.get(out.size() - 1);
            if (last != null && last.customer() == t.customer()) {
                out.set(out.size() - 1, new Turn(t.customer(), last.text() + "\n" + t.text()));
            } else {
                out.add(t);
            }
        }
        return out;
    }
}
