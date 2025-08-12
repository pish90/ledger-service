package com.fintech.ledger_service.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "ledger")
public class DatabaseConfig {
    private boolean enableOptimisticLocking = true;
    private int maxRetries = 3;
}
