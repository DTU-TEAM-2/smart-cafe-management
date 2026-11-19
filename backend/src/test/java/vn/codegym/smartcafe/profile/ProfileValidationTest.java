package vn.codegym.smartcafe.profile;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class ProfileValidationTest {
    @ParameterizedTest
    @ValueSource(strings = {"id", "userId", "username", "role", "salary", "status", "email", "birthday", "gender",
            "passwordHash", "passwordChangedAt", "mustChangePassword", "authVersion", "emailVerifiedAt", "createdAt", "avatarMediaId", "unknown"})
    void rejectsEveryNonEditableField(String field) {
        assertThatThrownBy(() -> new ProfileModels.Patch(Map.of("version", "0", field, "changed")))
                .isInstanceOf(ProfileException.class);
    }

    @Test void preservesOmissionAndRejectsExplicitNullAndWrongTypes() {
        var patch = new ProfileModels.Patch(Map.of("version", "0", "name", "Nguyễn Thị Ánh"));
        assertThat(patch.value("name")).isEqualTo("Nguyễn Thị Ánh");
        assertThat(patch.value("phone")).isNull();
        var fields = new HashMap<String, Object>(Map.of("version", "0"));
        fields.put("phone", null);
        assertThatThrownBy(() -> new ProfileModels.Patch(fields)).isInstanceOf(ProfileException.class);
        for (Object version : new Object[]{0, -1, "-1", "1.5", "9223372036854775808", "01"}) {
            assertThatThrownBy(() -> new ProfileModels.Patch(Map.of("version", version, "name", "A")))
                    .isInstanceOf(ProfileException.class);
        }
        assertThatThrownBy(() -> new ProfileModels.Patch(Map.of("version", "0"))).isInstanceOf(ProfileException.class);
    }

    @Test void validatesDdlLengthsWithoutInventingNameOrPhoneFormats() {
        for (var field : Map.of("name", 160, "address", 255, "phone", 20).entrySet()) {
            assertThatCode(() -> new ProfileModels.Patch(Map.of("version", "0", field.getKey(), "a".repeat(field.getValue())))).doesNotThrowAnyException();
            for (Object value : new Object[]{"a".repeat(field.getValue() + 1), "", "   ", 123, true}) {
                assertThatThrownBy(() -> new ProfileModels.Patch(Map.of("version", "0", field.getKey(), value)))
                        .isInstanceOf(ProfileException.class);
            }
        }
    }
}
