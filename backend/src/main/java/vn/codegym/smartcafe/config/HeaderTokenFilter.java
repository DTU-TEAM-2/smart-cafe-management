package vn.codegym.smartcafe.config;

import java.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Consumer of the authentication module's signed bearer contract. */
final class HeaderTokenFilter extends OncePerRequestFilter {
    private final BearerTokenAuthenticator authenticator;

    HeaderTokenFilter(BearerTokenAuthenticator authenticator) {
        this.authenticator = authenticator;
    }

    // Keep the constructor used by the approved MenuSecurity chain on main.
    HeaderTokenFilter(JdbcClient jdbc, String secret) {
        this(new BearerTokenAuthenticator(jdbc, secret));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.equals("/api/v1/news/events") || (!path.equals("/api/v1/home/account")
                && !path.equals("/api/v1/home/notifications") && !path.equals("/api/v1/news")
                && !path.startsWith("/api/v1/news/") && !path.equals("/api/v1/menu")
                && !path.startsWith("/api/v1/menu/") && !path.startsWith("/api/v1/sales/")
                && !path.equals("/api/v1/income") && !path.startsWith("/api/v1/bills/"));
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
