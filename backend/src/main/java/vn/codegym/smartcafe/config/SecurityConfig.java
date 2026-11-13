package vn.codegym.smartcafe.config;

import jakarta.servlet.DispatcherType;
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
import org.springframework.security.web.savedrequest.NullRequestCache;

@Configuration
class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JdbcClient jdbc,
            @Value("${smartcafe.auth.token-secret:}") String secret) throws Exception {
        var tokenFilter = new HeaderTokenFilter(jdbc, secret);
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
                        .requestMatchers(HttpMethod.GET, "/api/v1/customer/tables/qr/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/customer/menu/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/home/account").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/home/notifications").hasAnyRole("STAFF", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/sales/csrf", "/api/v1/sales/tables", "/api/v1/sales/tables/*/services").hasAnyRole("STAFF", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/sales/tables/*/bill", "/api/v1/sales/tables/*/requests/*/acknowledge").hasAnyRole("STAFF", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/income", "/api/v1/bills/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/bills/*/payments", "/api/v1/sales/tables/*/reset").hasRole("ADMIN")
                        .anyRequest().denyAll())
                .addFilterBefore(tokenFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
