package vn.codegym.smartcafe.config;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class HeaderTokenFilterCompatibilityTest {
    @Test
    void approvedMainModuleRoutesStillReceiveBearerAuthentication() throws Exception {
        var authenticator = mock(BearerTokenAuthenticator.class);
        var expected = UsernamePasswordAuthenticationToken.authenticated("current-staff", null,
                List.of(new SimpleGrantedAuthority("ROLE_STAFF")));
        when(authenticator.authenticate("Bearer isolated-unit-fixture")).thenReturn(expected);
        var filter = new HeaderTokenFilter(authenticator);
        for (String path : List.of("/api/v1/menu/categories", "/api/v1/menu", "/api/v1/sales/tables",
                "/api/v1/income", "/api/v1/bills/42", "/api/v1/auth/me", "/api/v1/admin/test", "/api/v1/staff/test")) {
            SecurityContextHolder.clearContext();
            try {
                var request = new MockHttpServletRequest("GET", "/cafe" + path);
                request.setContextPath("/cafe"); request.addHeader("Authorization", "Bearer isolated-unit-fixture");
                var observed = new AtomicReference<Authentication>();
                filter.doFilter(request, new MockHttpServletResponse(), (req, res) ->
                        observed.set(SecurityContextHolder.getContext().getAuthentication()));
                assertThat(observed.get()).as("Existing main chain caller %s", path).isSameAs(expected);
            } finally { SecurityContextHolder.clearContext(); }
        }
    }

    @Test
    void filterDoesNotCreateSessionWithoutCredentials() throws Exception {
        var authenticator = mock(BearerTokenAuthenticator.class);
        var filter = new HeaderTokenFilter(authenticator);
        SecurityContextHolder.clearContext();
        try {
            filter.doFilter(new MockHttpServletRequest("GET", "/api/v1/menu/categories"),
                    new MockHttpServletResponse(), (req, res) -> assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull());
            verify(authenticator).authenticate(null);
        } finally { SecurityContextHolder.clearContext(); }
    }
}
