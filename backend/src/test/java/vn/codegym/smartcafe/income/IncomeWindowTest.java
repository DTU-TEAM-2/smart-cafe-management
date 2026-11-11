package vn.codegym.smartcafe.income;

import java.time.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class IncomeWindowTest {
    private IncomeService service(String instant) { return new IncomeService(null,Clock.fixed(Instant.parse(instant),ZoneOffset.UTC)); }
    @Test void vietnamMidnightAndMondayBoundaries() {
        var service=service("2026-11-08T17:00:00Z");
        var today=service.window("today",null,null);
        assertThat(today.from()).isEqualTo(LocalDate.of(2026,11,9));
        assertThat(today.start().toInstant()).isEqualTo(Instant.parse("2026-11-08T17:00:00Z"));
        assertThat(service.window("in_week",null,null).to()).isEqualTo(LocalDate.of(2026,11,15));
    }
    @Test void leapYearAndMonthAreInclusiveCalendarDatesWithExclusiveSqlEnd() {
        var service=service("2024-02-29T12:00:00Z");
        assertThat(service.window("in_month",null,null).to()).isEqualTo(LocalDate.of(2024,2,29));
        assertThat(service.window("in_year",null,null).endExclusive().toInstant()).isEqualTo(Instant.parse("2024-12-31T17:00:00Z"));
    }
    @Test void ambiguousOrIncompleteRangesAreRejected() {
        var service=service("2026-11-11T00:00:00Z");
        assertThatThrownBy(()->service.window("today",LocalDate.of(2026,1,1),LocalDate.of(2026,1,2))).hasMessageContaining("400");
        assertThatThrownBy(()->service.window(null,LocalDate.of(2026,1,1),null)).hasMessageContaining("400");
    }
}
