package com.example.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AppConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    // NOTE: Do NOT define a custom ObjectMapper bean here.
    // Spring Boot auto-configures one with all required modules (Jackson, Hibernate, etc.)
    // A plain new ObjectMapper() breaks serialization of Lombok classes and lazy JPA proxies.
    // AiService gets the auto-configured one injected via @Autowired.
}
