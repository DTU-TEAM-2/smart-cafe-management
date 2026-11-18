package vn.codegym.smartcafe.config;

import java.io.IOException;
import java.util.List;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Consumer of the authentication module's signed bearer contract. */
final class HeaderTokenFilter extends OncePerRequestFilter {
    private static final List<String> AUTHENTICATED_RESOURCES = List.of(
            "/api/v1/home/account", "/api/v1/home/notifications", "/api/v1/auth/me",
            "/api/v1/news", "/api/v1/menu", "/api/v1/sales", "/api/v1/income",
            "/api/v1/bills", "/api/v1/statistics", "/api/v1/admin", "/api/v1/staff");
    private final BearerTokenAuthenticator authenticator;

    HeaderTokenFilter(BearerTokenAuthenticator authenticator) {
        this.authenticator = authenticator;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.equals("/api/v1/news/events") || AUTHENTICATED_RESOURCES.stream()
                .noneMatch(resource -> path.equals(resource) || path.startsWith(resource + "/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        var existing = SecurityContextHolder.getContext().getAuthentication();
        if (existing == null || !existing.isAuthenticated()) {
            try {
                var authentication = authenticator.authenticate(request.getHeader("Authorization"));
                if (authentication != null) SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (DataAccessException failure) {
                response.setStatus(503);
                response.setContentType("application/problem+json;charset=UTF-8");
                response.getWriter().write("{\"status\":503,\"title\":\"Service Unavailable\",\"detail\":\"Không thể xác minh tài khoản lúc này.\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
