package vn.codegym.smartcafe.config;

import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy;
import org.springframework.http.HttpStatus;

@Configuration
class MenuSecurity {
    @Bean
    @Order(1)
    SecurityFilterChain menuSecurityFilterChain(HttpSecurity http, JdbcClient jdbc,
            @Value("${smartcafe.auth.token-secret:}") String secret) throws Exception {
        return http.securityMatcher("/api/v1/menu/**", "/actuator/health")
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .requestMatchers("/api/v1/menu/**").hasRole("ADMIN")
                        .anyRequest().denyAll())
                // Bearer is verified on each request; the session only retains CSRF state.
                .csrf(csrf -> csrf.sessionAuthenticationStrategy(new NullAuthenticatedSessionStrategy()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new HeaderTokenFilter(jdbc, secret), UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(errors -> errors.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .build();
    }
}
