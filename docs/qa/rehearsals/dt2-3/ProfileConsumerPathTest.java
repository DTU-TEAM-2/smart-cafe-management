package vn.codegym.smartcafe.config;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.assertThat;

class ProfileConsumerPathTest {
    @ParameterizedTest @ValueSource(strings={"/api/v1/profiles","/api/v1/profiles/me","/api/v1/profiles/me/password"})
    void authenticatesProfileRootAndDescendants(String path) {
        assertThat(new HeaderTokenFilter(null).shouldNotFilter(new MockHttpServletRequest("GET",path))).isFalse();
    }
    @ParameterizedTest @ValueSource(strings={"/api/v1/profiles-malicious","/api/v1/profilesx/me","/api/v1/profile/me"})
    void doesNotRecognizeLookalikeNamespaces(String path) {
        assertThat(new HeaderTokenFilter(null).shouldNotFilter(new MockHttpServletRequest("GET",path))).isTrue();
    }
}
