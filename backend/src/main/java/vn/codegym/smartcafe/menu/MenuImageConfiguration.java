package vn.codegym.smartcafe.menu;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(MenuImageConfiguration.Cloudinary.class)
class MenuImageConfiguration {
    @ConfigurationProperties("menu.images")
    record Cloudinary(String cloudName, String apiKey, String apiSecret) { }

    @Bean
    RestClient menuImageClient() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(20));
        return RestClient.builder().requestFactory(factory).build();
    }
}
