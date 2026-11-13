package vn.codegym.smartcafe.config;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.savedrequest.NullRequestCache;
import vn.codegym.smartcafe.customerfeedback.FeedbackAdmissionFilter;

import vn.codegym.smartcafe.auth.TokenService;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JdbcClient jdbc,
            TokenService tokenService, Clock clock,
            @Value("${feedback.limits.per-source-per-minute:20}") int perSource,
            @Value("${feedback.limits.total-per-minute:120}") int total,
            @Value("${feedback.limits.concurrent-multipart:2}") int multipart,
            @Value("${feedback.limits.csrf-per-source-per-minute:12}") int csrfPerSource,
            @Value("${feedback.limits.csrf-total-per-minute:60}") int csrfTotal) throws Exception {
        var tokenFilter = new HeaderTokenFilter(jdbc, tokenService.getSecretKey());

        var feedbackPost = PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/v1/customer/feedback");
        var feedbackCsrf = PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/api/v1/customer/feedback/csrf");
        var customerCsrf = PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/api/v1/customer/csrf");
        org.springframework.security.web.util.matcher.RequestMatcher guestCsrf =
                request -> feedbackCsrf.matches(request) || customerCsrf.matches(request);
        return http
                .csrf(csrf -> csrf.ignoringRequestMatchers(
                        "/api/v1/auth/**",
                        "/api/v1/admin/**",
                        "/api/v1/staff/**"
                ))
                .sessionManagement(session -> session.requireExplicitAuthenticationStrategy(true))
                .securityContext(context -> context.securityContextRepository(new NullSecurityContextRepository()))
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) -> {
                            String path = request.getRequestURI();
                            if (path.startsWith("/api/v1/admin/") || path.startsWith("/api/v1/staff/")) {
                                response.setContentType("application/problem+json");
                                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                                response.getWriter().write("""
                                    {"type":"about:blank","title":"Unauthorized","status":401,"detail":"Xác thực không hợp lệ hoặc phiên đã hết hạn.","code":"UNAUTHORIZED"}
                                    """);
                                return;
                            }
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setContentType("application/problem+json");
                            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.getWriter().write("""
                                {"type":"about:blank","title":"Forbidden","status":403,"detail":"Bạn không có quyền truy cập chức năng này.","code":"FORBIDDEN"}
                                """);
                        })
                )
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(
                                "/actuator/health",
                                "/api/v1/auth/**"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/home/highlights").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/customer/tables/qr/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/customer/menu/**").permitAll()
                        .requestMatchers(guestCsrf, feedbackPost).permitAll()
                        // The controller/service verifies an existing guest token against session_clients.
                        .requestMatchers(HttpMethod.POST, "/api/v1/customer/sessions/*/orders").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/customer/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/customer/sessions/*/waiter-requests", "/api/v1/customer/sessions/*/bill-requests").permitAll()
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/customer/sessions/*/order-items/*").permitAll()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/customer/sessions/*/order-items/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/home/account").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/home/notifications").hasAnyRole("STAFF", "ADMIN")
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/staff/**").hasAnyRole("ADMIN", "STAFF")
                        .requestMatchers(HttpMethod.GET, "/api/v1/sales/csrf", "/api/v1/sales/tables", "/api/v1/sales/tables/*/services").hasAnyRole("STAFF", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/sales/tables/*/bill", "/api/v1/sales/tables/*/requests/*/acknowledge").hasAnyRole("STAFF", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/income", "/api/v1/bills/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/bills/*/payments", "/api/v1/sales/tables/*/reset").hasRole("ADMIN")
                        .anyRequest().denyAll())
                .addFilterBefore(new FeedbackAdmissionFilter(
                        feedbackPost, guestCsrf, clock, perSource, total, multipart, csrfPerSource, csrfTotal), CsrfFilter.class)
                .addFilterBefore(tokenFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
