package vn.codegym.smartcafe.config;

import java.net.URI;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.http.HttpStatus;

@Configuration
class MenuSecurity {
    @Bean
    JwtDecoder menuJwtDecoder(@Value("${menu.security.issuer:}") String issuer,
            @Value("${menu.security.audience:}") String audience) {
        if (issuer.isBlank() || audience.isBlank()) {
            return token -> { throw new BadJwtException("JWT issuer/audience not configured"); };
        }
        URI endpoint = URI.create(issuer);
        if (!"https".equals(endpoint.getScheme()) || endpoint.getHost() == null || endpoint.getUserInfo() != null) {
            throw new IllegalArgumentException("JWT issuer must use HTTPS without user information");
        }
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(issuer).build();
        OAuth2TokenValidator<Jwt> audienceAndExpiry = jwt ->
                jwt.getExpiresAt() != null && jwt.getAudience().contains(audience)
                        ? OAuth2TokenValidatorResult.success()
                        : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Audience or expiry is invalid", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer), audienceAndExpiry));
        return decoder;
    }

    @Bean
    JwtAuthenticationConverter menuRoles() {
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            // Do not grant authorities from an unsigned request header or payload.
            Object roles = jwt.getClaims().get("roles");
            return roles instanceof List<?> values ? values.stream().filter(String.class::isInstance)
                    .map(String.class::cast).filter(role -> role.equals("ADMIN") || role.equals("STAFF"))
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .map(org.springframework.security.core.GrantedAuthority.class::cast).toList() : List.of();
        });
        return converter;
    }

    @Bean
    SecurityFilterChain menuSecurityFilterChain(HttpSecurity http, JwtAuthenticationConverter menuRoles) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .requestMatchers("/api/v1/menu/**").hasRole("ADMIN")
                        .anyRequest().denyAll())
                .csrf(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.NEVER))
                .oauth2ResourceServer(resource -> resource.jwt(jwt -> jwt.jwtAuthenticationConverter(menuRoles)))
                .exceptionHandling(errors -> errors.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .build();
    }
}
