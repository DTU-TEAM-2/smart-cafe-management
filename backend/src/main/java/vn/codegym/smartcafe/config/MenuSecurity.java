package vn.codegym.smartcafe.config;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;
import vn.codegym.smartcafe.auth.TokenService;

@Configuration
class MenuSecurity {
    @Bean
    @Order(1)
    SecurityFilterChain menuSecurityFilterChain(HttpSecurity http, JdbcClient jdbc,
            TokenService tokenService) throws Exception {
        return http.securityMatcher("/api/v1/menu/**", "/actuator/health")
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .requestMatchers("/api/v1/menu/**").hasRole("ADMIN")
                        .anyRequest().denyAll())
                // Bearer is verified per request; the HTTP session only retains CSRF state.
                // Do not treat each HeaderTokenFilter result as a new session login.
                .sessionManagement(session -> session.requireExplicitAuthenticationStrategy(true))
                .securityContext(context -> context.securityContextRepository(new NullSecurityContextRepository()))
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .addFilterBefore(new HeaderTokenFilter(jdbc, tokenService.getSecretKey()), UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(errors -> errors.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .build();
    }
}
