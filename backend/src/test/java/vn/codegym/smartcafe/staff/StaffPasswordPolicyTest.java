package vn.codegym.smartcafe.staff;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class StaffPasswordPolicyTest {

    @Test
    void generatedPasswordAlwaysCompliesWithPolicyOverManyIterations() {
        for (int i = 0; i < 1000; i++) {
            String pwd = StaffService.generateRandomPassword();
            assertThat(pwd).hasSize(12);
            assertThat(pwd).matches(".*[A-Z].*");
            assertThat(pwd).matches(".*[a-z].*");
            assertThat(pwd).matches(".*[0-9].*");
            assertThat(pwd).matches(".*[!@#$%^&*].*");
        }
    }

    @Test
    void escapeLikeEscapesSpecialWildcardsCorrectly() {
        assertThat(StaffService.escapeLike("normal")).isEqualTo("normal");
        assertThat(StaffService.escapeLike("100%")).isEqualTo("100!%");
        assertThat(StaffService.escapeLike("user_name")).isEqualTo("user!_name");
        assertThat(StaffService.escapeLike("alert!%_")).isEqualTo("alert!!!%!_");
        assertThat(StaffService.escapeLike("")).isEmpty();
        assertThat(StaffService.escapeLike(null)).isEmpty();
    }
}
