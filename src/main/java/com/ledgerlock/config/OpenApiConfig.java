package com.ledgerlock.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI ledgerLockOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("LedgerLock API")
                        .version("1.0.0")
                        .description("LedgerLock is a demonstration system for educational purposes. No real currency is transacted."));
    }
}
