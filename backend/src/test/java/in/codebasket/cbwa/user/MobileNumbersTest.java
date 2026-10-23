package in.codebasket.cbwa.user;

import in.codebasket.cbwa.common.exception.AppException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MobileNumbersTest {

    @Test
    void allIndianFormatsBecomeTheSameLoginId() {
        assertThat(MobileNumbers.normalise("9876543210", "mobile")).isEqualTo("919876543210");
        assertThat(MobileNumbers.normalise("+91 98765 43210", "mobile")).isEqualTo("919876543210");
        assertThat(MobileNumbers.normalise("09876543210", "mobile")).isEqualTo("919876543210");
        assertThat(MobileNumbers.normalise("919876543210", "mobile")).isEqualTo("919876543210");
        assertThat(MobileNumbers.normaliseOrNull("98000 01001")).isEqualTo("919800001001");
        assertThat(MobileNumbers.normaliseOrNull("97000 01001")).isEqualTo("919700001001");
    }

    @Test
    void invalidNumbersAreRejected() {
        assertThatThrownBy(() -> MobileNumbers.normalise("12345", "mobile")).isInstanceOf(AppException.class);
        assertThat(MobileNumbers.normaliseOrNull("abc")).isNull();
    }
}
