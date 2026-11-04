package com.example.pms.tenant.services;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class InvitationMetricsService {

    private final MeterRegistry meterRegistry;

    public InvitationMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordDuration(String operation, long durationNanos) {
        meterRegistry.timer(
                "invitation.operation.duration",
                "operation", operation
        ).record(durationNanos, TimeUnit.NANOSECONDS);
    }

    public void recordLockWait(String operation, long durationNanos) {
        meterRegistry.timer(
                "invitation.lock.wait",
                "operation", operation
        ).record(durationNanos, TimeUnit.NANOSECONDS);
    }

    public void recordResult(String operation, String result) {
        meterRegistry.counter(
                "invitation.operation.total",
                "operation", operation,
                "result", result
        ).increment();
    }
}