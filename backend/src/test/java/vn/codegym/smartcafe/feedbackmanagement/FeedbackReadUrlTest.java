package vn.codegym.smartcafe.feedbackmanagement;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class FeedbackReadUrlTest {
    @Test void onlyHttpsWithoutUserInfoCanBeDisplayed() {
        for (String url : new String[] {"javascript:alert(1)", "data:image/png;base64,abc", "file:///tmp/img", "gs://bucket/img", "https://user:password@example.invalid/a", "http://example.invalid/a", "//example.invalid/a", "https:///a"})
            assertThat(FeedbackReadService.displayUrl(url)).isNull();
        assertThat(FeedbackReadService.displayUrl(null)).isNull();
        assertThat(FeedbackReadService.displayUrl("https://example.invalid/a.png")).isEqualTo("https://example.invalid/a.png");
    }
}
