package vn.codegym.smartcafe.profile;

import java.time.Clock;
import java.util.Set;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import vn.codegym.smartcafe.auth.*;
import vn.codegym.smartcafe.home.AccountReader;
import static org.springframework.http.HttpStatus.*;

@Service
public class ProfileService {
    private final AccountReader accounts;
    private final ProfileRepository profiles;
    private final AuthRepository auth;
    private final PasswordEncoder encoder;
    private final TokenService tokens;
    private final Clock clock;
    private final TransactionTemplate transaction;

    ProfileService(AccountReader accounts, ProfileRepository profiles, AuthRepository auth,
            PasswordEncoder encoder, TokenService tokens, Clock clock, PlatformTransactionManager manager) {
        this.accounts = accounts;
        this.profiles = profiles;
        this.auth = auth;
        this.encoder = encoder;
        this.tokens = tokens;
        this.clock = clock;
        this.transaction = new TransactionTemplate(manager);
    }

    @Transactional(readOnly = true)
    public ProfileModels.Profile get(UserRecord principal) { return response(current(principal)); }

    @Transactional
    public ProfileModels.Profile update(UserRecord principal, ProfileModels.Patch patch) {
        requirePrincipal(principal);
        profiles.lock(principal.id());
        var account = current(principal);
        if (profiles.updateUser(principal.id(), account.authVersion(), patch) != 1) {
            throw new ProfileException(CONFLICT, "PROFILE_CONFLICT", "Thông tin đã thay đổi. Vui lòng tải lại hồ sơ.");
        }
        if (patch.value("address") != null && profiles.updateAddress(principal.id(), patch.value("address")) != 1) {
            throw new ProfileException(CONFLICT, "PROFILE_INCOMPLETE", "Chưa có hồ sơ nhân sự. Liên hệ quản lý.");
        }
        return response(current(principal));
    }

    public ProfileModels.PasswordChanged changePassword(UserRecord principal, ProfileModels.PasswordChange request) {
        requirePrincipal(principal);
        if (!request.newPassword.equals(request.confirmNewPassword)) {
            throw new ProfileException(BAD_REQUEST, "PASSWORD_MISMATCH", "Xác nhận mật khẩu không khớp.");
        }
        if (!PasswordPolicy.isValid(request.newPassword)) {
            throw new ProfileException(BAD_REQUEST, "INVALID_PASSWORD", "Mật khẩu mới không đáp ứng chính sách mật khẩu.");
        }
        current(principal);
        var verified = auth.findUserById(principal.id()).orElseThrow(ProfileService::unauthorized);
        if (verified.authVersion() != principal.authVersion()) throw unauthorized();
        if (!encoder.matches(request.currentPassword, verified.passwordHash())) {
            throw new ProfileException(BAD_REQUEST, "CURRENT_PASSWORD_INCORRECT", "Mật khẩu hiện tại không chính xác.");
        }
        if (encoder.matches(request.newPassword, verified.passwordHash())) {
            throw new ProfileException(BAD_REQUEST, "PASSWORD_REUSED", "Mật khẩu mới không được trùng mật khẩu hiện tại.");
        }
        String newHash = encoder.encode(request.newPassword);
        UserRecord changed = transaction.execute(status -> {
            profiles.lock(principal.id());
            current(principal);
            var user = auth.findUserById(principal.id()).orElseThrow(ProfileService::unauthorized);
            // Hashing runs without a connection/lock; reject an intervening credential change under the lock.
            if (user.authVersion() != verified.authVersion() || !user.passwordHash().equals(verified.passwordHash())
                    || auth.updateUserPassword(user.id(), newHash, principal.authVersion()) != 1) {
                throw new ProfileException(CONFLICT, "PASSWORD_CONFLICT", "Thông tin xác thực đã thay đổi. Vui lòng đăng nhập lại.");
            }
            return auth.findUserById(user.id()).orElseThrow(ProfileService::unauthorized);
        });
        // execute returns only after commit; a failed commit must never issue a replacement token.
        String token = tokens.generateToken(changed.id(), changed.username(), changed.role(), changed.authVersion());
        var expiration = PasswordExpiration.at(changed.passwordChangedAt(), changed.mustChangePassword(), clock.instant());
        return new ProfileModels.PasswordChanged(token, expiration.passwordChangedAt(),
                expiration.passwordExpiresAt(), expiration.mustChangePassword());
    }

    private AccountReader.Account current(UserRecord principal) {
        requirePrincipal(principal);
        var account = accounts.byId(principal.id()).orElseThrow(ProfileService::unauthorized);
        if (account.authVersion() != principal.authVersion()) throw unauthorized();
        if (!Set.of("ADMIN", "STAFF").contains(account.home().role())) {
            throw new ProfileException(FORBIDDEN, "FORBIDDEN", "Bạn không có quyền truy cập hồ sơ nhân sự.");
        }
        return account;
    }

    private ProfileModels.Profile response(AccountReader.Account account) {
        return new ProfileModels.Profile(account.home(), PasswordExpiration.at(account.passwordChangedAt(),
                account.mustChangePassword(), clock.instant()), true, Long.toString(account.version()));
    }

    private static void requirePrincipal(UserRecord principal) { if (principal == null) throw unauthorized(); }
    private static ProfileException unauthorized() {
        return new ProfileException(UNAUTHORIZED, "UNAUTHORIZED", "Xác thực không hợp lệ hoặc phiên đã hết hạn.");
    }
}
