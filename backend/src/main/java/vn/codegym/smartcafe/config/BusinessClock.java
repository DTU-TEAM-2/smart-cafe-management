package vn.codegym.smartcafe.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class BusinessClock {
    @Bean
    Clock utcClock() { return Clock.systemUTC(); }
}
