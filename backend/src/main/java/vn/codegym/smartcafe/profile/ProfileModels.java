package vn.codegym.smartcafe.profile;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import vn.codegym.smartcafe.auth.PasswordExpiration;
import vn.codegym.smartcafe.home.HomeAccount;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

public final class ProfileModels {
    private ProfileModels() {}

    public record Profile(@JsonUnwrapped HomeAccount account, @JsonUnwrapped PasswordExpiration password,
                          boolean canUpdateProfile, String version) {}

    public record PasswordChanged(String token, Instant passwordChangedAt, Instant passwordExpiresAt,
                                  boolean mustChangePassword) {}

    /** Delegating constructors retain presence and enforce JSON types, including explicit null. */
    public static final class Patch {
        final Map<String, Object> fields;
        final long version;

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public Patch(Map<String, Object> fields) {
            allow(fields, Set.of("name", "address", "phone", "version"));
            if (fields.size() < 2 || !(fields.get("version") instanceof String value)
                    || !value.matches("0|[1-9][0-9]{0,18}")) throw invalid();
            try { this.version = Long.parseLong(value); } catch (NumberFormatException ex) { throw invalid(); }
            for (String field : Set.of("name", "address", "phone")) {
                if (fields.containsKey(field)) text(fields, field, switch (field) {
                    case "name" -> 160; case "address" -> 255; default -> 20;
                });
            }
            this.fields = Map.copyOf(fields);
        }

        String value(String field) { return (String) fields.get(field); }
    }

    public static final class PasswordChange {
        final String currentPassword;
        final String newPassword;
        final String confirmNewPassword;

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public PasswordChange(Map<String, Object> fields) {
            allow(fields, Set.of("currentPassword", "newPassword", "confirmNewPassword"));
            currentPassword = password(fields, "currentPassword");
            newPassword = password(fields, "newPassword");
            confirmNewPassword = password(fields, "confirmNewPassword");
        }
    }

    private static String password(Map<String, Object> fields, String key) {
        if (!(fields.get(key) instanceof String value) || value.isEmpty() || value.length() > 256) throw invalid();
        return value;
    }

    private static void text(Map<String, Object> fields, String key, int max) {
        if (!(fields.get(key) instanceof String value)
                || value.codePoints().allMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c))
                || value.codePointCount(0, value.length()) > max) throw invalid();
    }

    private static void allow(Map<String, Object> fields, Set<String> allowed) {
        if (fields == null || !allowed.containsAll(fields.keySet())) throw invalid();
    }

    private static ProfileException invalid() {
        return new ProfileException(BAD_REQUEST, "INVALID_PROFILE_REQUEST", "Dữ liệu yêu cầu không hợp lệ.");
    }
}
