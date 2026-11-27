package vn.codegym.smartcafe.staff;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class StaffTestDatabaseGuardTest {
    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "jdbc:mysql://localhost:3306/smart_cafe",
            "jdbc:mysql://localhost:3306/smart_cafe?connectionTimeZone=UTC&name=staff_test",
            "jdbc:mysql://localhost:3306/staff_test_backup",
            "jdbc:mysql://localhost:3306?name=/staff_test"
    })
    void rejectsInvalidUrlBeforeContextRefresh(String url) {
        try (var context = new GenericApplicationContext()) {
            context.setEnvironment(new MockEnvironment().withProperty("spring.datasource.url", url));

            assertThatThrownBy(() -> new StaffTestDatabaseGuard().initialize(context))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("MySQL *_test URL");
        }
    }

    @Test
    void acceptsDedicatedTestUrlWithConnectionOptions() {
        try (var context = new GenericApplicationContext()) {
            context.setEnvironment(new MockEnvironment().withProperty("spring.datasource.url",
                    "jdbc:mysql://localhost:3306/smart_cafe_ci_test?connectionTimeZone=UTC"));

            new StaffTestDatabaseGuard().initialize(context);
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"smart_cafe", "staff_test_backup"})
    void rejectsUnsafeCatalogBeforeAnyFixtureWrite(String catalog) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject("SELECT DATABASE()", String.class)).thenReturn(catalog);
        var staffTests = new StaffControllerTest();
        ReflectionTestUtils.setField(staffTests, "jdbc", jdbc);

        assertThatThrownBy(staffTests::setUp)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("active MySQL *_test database");
        verify(jdbc).queryForObject("SELECT DATABASE()", String.class);
        verifyNoMoreInteractions(jdbc);
    }
}
