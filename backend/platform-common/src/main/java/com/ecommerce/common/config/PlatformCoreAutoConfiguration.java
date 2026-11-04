package com.ecommerce.common.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

/**
 * Beans every service needs whether or not it serves HTTP, e.g. in non-web test contexts or
 * message-only workers.
 */
@AutoConfiguration
public class PlatformCoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
