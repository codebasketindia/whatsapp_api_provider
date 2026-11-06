package in.codebasket.cbwa.common.service.ai;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Sends each request to the provider named in it (OPENAI or ANTHROPIC). */
@Component
public class AiModelRouter implements AiModelClient {

    private final Map<String, Provider> providers;

    public AiModelRouter(List<Provider> providers) {
        this.providers = providers.stream().collect(Collectors.toMap(p -> p.name().toUpperCase(Locale.ROOT), Function.identity()));
    }

    @Override
    public AiResult complete(AiRequest request) {
        Provider p = providers.get(request.provider() == null ? "" : request.provider().toUpperCase(Locale.ROOT));
        if (p == null) {
            throw new AiModelException("Unknown AI provider " + request.provider(), null);
        }
        return p.complete(request);
    }
}
