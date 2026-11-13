package vn.codegym.smartcafe.config;

import jakarta.servlet.DispatcherType;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.savedrequest.NullRequestCache;
import vn.codegym.smartcafe.customerfeedback.FeedbackAdmissionFilter;

@Configuration
class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JdbcClient jdbc, Clock clock,
            @Value("${smartcafe.auth.token-secret:}") String secret,
            @Value("${feedback.limits.per-source-per-minute:20}") int perSource,
            @Value("${feedback.limits.total-per-minute:120}") int total,
            @Value("${feedback.limits.concurrent-multipart:2}") int multipart) throws Exception {
        var tokenFilter = new HeaderTokenFilter(jdbc, secret);
        var feedbackPost = PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/v1/customer/feedback");
        return http
                // Bearer authentication is per request. A polling GET must not run a login
                // session strategy that rotates the token issued for the next CSRF-protected POST.
                .sessionManagement(session -> session.requireExplicitAuthenticationStrategy(true))
                .securityContext(context -> context.securityContextRepository(new NullSecurityContextRepository()))
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.FORBIDDEN)))
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/home/highlights").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/customer/menu/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/customer/feedback/csrf").permitAll()
                        .requestMatchers(feedbackPost).permitAll()
                        // The controller/service verifies an existing guest token against session_clients.
                        .requestMatchers(HttpMethod.POST, "/api/v1/customer/sessions/*/orders").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/customer/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/customer/sessions/*/waiter-requests", "/api/v1/customer/sessions/*/bill-requests").permitAll()
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/customer/sessions/*/order-items/*").permitAll()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/customer/sessions/*/order-items/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/home/account").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/home/notifications").hasAnyRole("STAFF", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/sales/csrf", "/api/v1/sales/tables", "/api/v1/sales/tables/*/services").hasAnyRole("STAFF", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/sales/tables/*/bill", "/api/v1/sales/tables/*/requests/*/acknowledge").hasAnyRole("STAFF", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/income", "/api/v1/bills/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/bills/*/payments", "/api/v1/sales/tables/*/reset").hasRole("ADMIN")
                        .anyRequest().denyAll())
                .addFilterBefore(new FeedbackAdmissionFilter(feedbackPost, clock, perSource, total, multipart), CsrfFilter.class)
                .addFilterBefore(tokenFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
