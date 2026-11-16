package vn.codegym.smartcafe.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import vn.codegym.smartcafe.auth.dto.LoginRequest;
import vn.codegym.smartcafe.auth.dto.LoginResponse;
import vn.codegym.smartcafe.auth.dto.UserDto;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AuthRepository authRepository;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;

    public AuthService(AuthRepository authRepository, TokenService tokenService, PasswordEncoder passwordEncoder) {
        this.authRepository = authRepository;
        this.tokenService = tokenService;
        this.passwordEncoder = passwordEncoder;
    }

    public LoginResponse login(LoginRequest request) {
        String username = request.username().trim();
        UserRecord user = authRepository.findUserByUsername(username)
                .or(() -> authRepository.findUserByEmail(username))
                .orElseThrow(() -> new InvalidCredentialsException("Tài khoản hoặc mật khẩu không chính xác."));

        if (!"ACTIVE".equalsIgnoreCase(user.status())) {
            log.warn("Login attempt for non-active user: {}", username);
            throw new InvalidCredentialsException("Tài khoản hoặc mật khẩu không chính xác.");
        }

        if (!passwordEncoder.matches(request.password(), user.passwordHash())) {
            log.warn("Password mismatch for user: {}", username);
            throw new InvalidCredentialsException("Tài khoản hoặc mật khẩu không chính xác.");
        }

        String token = tokenService.generateToken(user.id(), user.username(), user.role(), user.authVersion());
        UserDto userDto = new UserDto(
                user.id(),
                user.username(),
                user.fullName(),
                user.email(),
                user.phone(),
                user.role()
        );
        log.info("Successful login for user '{}' with role '{}'", user.username(), user.role());
        return new LoginResponse(token, userDto);
    }

    public UserDto getCurrentUser(Long userId) {
        UserRecord user = authRepository.findUserById(userId)
                .orElseThrow(() -> new InvalidCredentialsException("Tài khoản không tồn tại hoặc đã bị khóa."));

        if (!"ACTIVE".equalsIgnoreCase(user.status())) {
            throw new InvalidCredentialsException("Tài khoản không tồn tại hoặc đã bị khóa.");
        }

        return new UserDto(
                user.id(),
                user.username(),
                user.fullName(),
                user.email(),
                user.phone(),
                user.role()
        );
    }
}
