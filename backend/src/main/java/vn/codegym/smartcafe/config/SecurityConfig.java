package vn.codegym.smartcafe.config;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final AuthTokenFilter authTokenFilter;

    public SecurityConfig(AuthTokenFilter authTokenFilter) {
        this.authTokenFilter = authTokenFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JdbcClient jdbc,
            @Value("${smartcafe.auth.token-secret:}") String secret) throws Exception {
        var headerTokenFilter = new HeaderTokenFilter(jdbc, secret);

        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex
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
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.getWriter().write("""
                        {"type":"about:blank","title":"Forbidden","status":403,"detail":"Bạn không có quyền truy cập chức năng này.","code":"FORBIDDEN"}
                        """);
                })
            )
            .authorizeHttpRequests(auth -> auth
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers(
                    "/actuator/health",
                    "/api/v1/auth/**"
                ).permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/home/highlights").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/home/account").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/home/notifications").hasAnyRole("STAFF", "ADMIN")
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/staff/**").hasAnyRole("ADMIN", "STAFF")
                .anyRequest().authenticated()
            )
            .addFilterBefore(authTokenFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(headerTokenFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
