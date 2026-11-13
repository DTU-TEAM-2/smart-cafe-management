package vn.codegym.smartcafe.config;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;

@Configuration
class MenuSecurity {
    @Bean
    @Order(1)
    SecurityFilterChain menuSecurityFilterChain(HttpSecurity http, BearerTokenAuthenticator authenticator) throws Exception {
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
                .addFilterBefore(new HeaderTokenFilter(authenticator), UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(errors -> errors.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .build();
    }
}
