package in.codebasket.cbwa.ai;

import java.util.List;

/** Calls the language model. Implemented by {@link AnthropicAiClient}; replaced by a mock in tests. */
public interface AiModelClient {

    AiResult complete(AiRequest request);

    /**
     * @param system  fixed rules + the partner's knowledge (cached by the provider between calls)
     * @param history earlier turns, oldest first
     * @param message the customer's new message
     */
    record AiRequest(String apiKey, String model, int maxTokens, String system, List<Turn> history, String message) {
    }

    record Turn(boolean customer, String text) {
    }

    record AiResult(String reply, List<Long> attachmentIds, boolean handover, String reason,
                    int inputTokens, int outputTokens, int cacheReadTokens, int cacheWriteTokens) {
    }

    /** The model could not be reached or returned an unusable answer. */
    class AiModelException extends RuntimeException {
        public AiModelException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
