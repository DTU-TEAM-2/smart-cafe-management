package vn.codegym.smartcafe.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.codegym.smartcafe.auth.AuthRepository;
import vn.codegym.smartcafe.auth.TokenService;
import vn.codegym.smartcafe.auth.UserRecord;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;

@Component
public class AuthTokenFilter extends OncePerRequestFilter {

    private final TokenService tokenService;
    private final AuthRepository authRepository;

    public AuthTokenFilter(TokenService tokenService, AuthRepository authRepository) {
        this.tokenService = tokenService;
        this.authRepository = authRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String token = authHeader.substring(7).trim();
            TokenService.TokenClaims claims = tokenService.parseToken(token);

            if (claims != null) {
                Optional<UserRecord> userOpt = authRepository.findUserById(claims.userId());
                if (userOpt.isPresent()) {
                    UserRecord user = userOpt.get();
                    boolean usernameMatches = claims.username() == null
                            || claims.username().isBlank()
                            || claims.username().equals(user.username());

                    if ("ACTIVE".equalsIgnoreCase(user.status())
                            && user.authVersion() == claims.authVersion()
                            && usernameMatches) {
                        String role = user.role();
                        String roleName = role.startsWith("ROLE_") ? role : "ROLE_" + role;
                        SimpleGrantedAuthority authority = new SimpleGrantedAuthority(roleName);
                        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                                user,
                                null,
                                Collections.singletonList(authority)
                        );
                        SecurityContextHolder.getContext().setAuthentication(authToken);
                    }
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
