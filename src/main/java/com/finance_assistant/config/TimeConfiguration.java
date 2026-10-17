package com.finance_assistant.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfiguration {
    @Bean
    public Clock ledgerClock() {
        return Clock.system(ZoneId.of("Asia/Kuala_Lumpur"));
    }
}
