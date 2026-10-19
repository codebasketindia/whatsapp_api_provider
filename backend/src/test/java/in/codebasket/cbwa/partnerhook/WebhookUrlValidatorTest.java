package in.codebasket.cbwa.partnerhook;

import in.codebasket.cbwa.common.exception.AppException;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebhookUrlValidatorTest {

    private final WebhookUrlValidator strict = new WebhookUrlValidator(false);

    @Test
    void rejectsHttpAndPrivateTargets() {
        assertThatThrownBy(() -> strict.validate("http://example.com/hook")).isInstanceOf(AppException.class);
        for (String url : new String[]{"https://127.0.0.1/x", "https://10.1.2.3/x", "https://172.16.5.4/x", "https://192.168.1.1/x",
                "https://169.254.169.254/latest/meta-data", "https://[::1]/x", "https://[fd00::1]/x", "https://localhost/x",
                "https://user:pass@8.8.8.8/x", "ftp://8.8.8.8/x"}) {
            assertThatThrownBy(() -> strict.validate(url)).as(url).isInstanceOf(AppException.class);
        }
    }

    @Test
    void acceptsPublicHttpsIpAndDetectsRanges() throws Exception {
        assertThat(strict.validate("https://8.8.8.8/hook")).isEqualTo("https://8.8.8.8/hook");
        assertThat(WebhookUrlValidator.isPrivate(InetAddress.getByName("100.64.0.1"))).isTrue();
        assertThat(WebhookUrlValidator.isPrivate(InetAddress.getByName("::ffff:10.0.0.1"))).isTrue();
        assertThat(WebhookUrlValidator.isPrivate(InetAddress.getByName("1.1.1.1"))).isFalse();
        assertThat(new WebhookUrlValidator(true).validate("http://localhost:8089/hook")).isNotBlank();
    }
}
