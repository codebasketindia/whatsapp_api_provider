package in.codebasket.cbwa.ai;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiAiClientTest {

    static final WireMockServer API = new WireMockServer(WireMockConfiguration.options().dynamicPort());

    @BeforeAll
    static void start() {
        API.start();
    }

    @AfterAll
    static void stop() {
        API.stop();
    }

    @BeforeEach
    void reset() {
        API.resetAll();
    }

    OpenAiAiClient client() {
        return new OpenAiAiClient("http://localhost:" + API.port() + "/v1");
    }

    static AiModelClient.AiRequest request() {
        return new AiModelClient.AiRequest("OPENAI", "sk-test", "gpt-5-mini", 400, "low", "RULES", "CUSTOMER facts",
                List.of(new AiModelClient.Turn(false, "Hello! (business first - dropped)"), new AiModelClient.Turn(true, "hi"),
                        new AiModelClient.Turn(true, "anyone?"), new AiModelClient.Turn(false, "Yes, how can I help?")),
                "Umrah package price?");
    }

    @Test
    void sendsStrictJsonSchemaRequestAndParsesAnswer() {
        API.stubFor(post(urlEqualTo("/v1/chat/completions")).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withBody("""
                        {"choices":[{"message":{"role":"assistant","content":"{\\"reply\\":\\"Rs 85000 per person\\",\\"attachments\\":[\\"P7\\",\\"12\\"],\\"handover\\":false,\\"reason\\":\\"price in package\\"}"}}],
                         "usage":{"prompt_tokens":1500,"completion_tokens":40,"prompt_tokens_details":{"cached_tokens":1024}}}""")));

        AiModelClient.AiResult r = client().complete(request());

        assertThat(r.reply()).isEqualTo("Rs 85000 per person");
        assertThat(r.attachmentIds()).containsExactly(-7L, 12L);
        assertThat(r.handover()).isFalse();
        assertThat(r.inputTokens()).isEqualTo(476);
        assertThat(r.cacheReadTokens()).isEqualTo(1024);
        API.verify(postRequestedFor(urlEqualTo("/v1/chat/completions"))
                .withHeader("Authorization", equalTo("Bearer sk-test"))
                .withRequestBody(equalToJson("""
                        {"model":"gpt-5-mini","reasoning_effort":"low","max_completion_tokens":2000,
                         "messages":[{"role":"system","content":"RULES"},{"role":"system","content":"CUSTOMER facts"},
                                     {"role":"user","content":"hi\\nanyone?"},{"role":"assistant","content":"Yes, how can I help?"},
                                     {"role":"user","content":"Umrah package price?"}],
                         "response_format":{"type":"json_schema","json_schema":{"name":"whatsapp_reply","strict":true}}}""", true, true)));
    }

    @Test
    void unreadableAnswerIsAHandoverAndErrorsThrow() {
        API.stubFor(post(urlEqualTo("/v1/chat/completions")).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withBody("""
                        {"choices":[{"message":{"content":"{\\"reply\\": \\"cut off"}}],"usage":{"prompt_tokens":10,"completion_tokens":2000}}""")));
        assertThat(client().complete(request()).handover()).isTrue();

        API.stubFor(post(urlEqualTo("/v1/chat/completions")).willReturn(aResponse().withStatus(401)
                .withHeader("Content-Type", "application/json").withBody("{\"error\":{\"message\":\"Incorrect API key\"}}")));
        assertThatThrownBy(() -> client().complete(request())).isInstanceOf(AiModelClient.AiModelException.class)
                .hasMessageContaining("401").hasMessageContaining("Incorrect API key");
    }
}
