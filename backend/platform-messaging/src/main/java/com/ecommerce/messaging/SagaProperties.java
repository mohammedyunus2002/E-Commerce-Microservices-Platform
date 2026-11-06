package com.ecommerce.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.saga")
public class SagaProperties {

    /** When false, services keep their HTTP APIs and do not publish or consume saga events. */
    private boolean enabled = false;

    private Duration outboxDelay = Duration.ofSeconds(1);

    private Duration retryInitial = Duration.ofMillis(500);

    private Duration retryMaxElapsed = Duration.ofSeconds(30);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getOutboxDelay() {
        return outboxDelay;
    }

    public void setOutboxDelay(Duration outboxDelay) {
        this.outboxDelay = outboxDelay;
    }

    public Duration getRetryInitial() {
        return retryInitial;
    }

    public void setRetryInitial(Duration retryInitial) {
        this.retryInitial = retryInitial;
    }

    public Duration getRetryMaxElapsed() {
        return retryMaxElapsed;
    }

    public void setRetryMaxElapsed(Duration retryMaxElapsed) {
        this.retryMaxElapsed = retryMaxElapsed;
    }
}
