package com.disaster.management.lifecycle;

import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe holder for dynamic server runtime status and transition timestamps.
 */
@Component
public class ServerRuntimeStateHolder {

    private final AtomicReference<ServerRuntimeStatus> currentStatus = new AtomicReference<>(ServerRuntimeStatus.STARTING);
    private final Instant startTime = Instant.now();
    private volatile String failureReason = null;

    public ServerRuntimeStatus getStatus() {
        return currentStatus.get();
    }

    public void setStatus(ServerRuntimeStatus status) {
        this.currentStatus.set(status);
    }

    public void setFailed(String reason) {
        this.failureReason = reason;
        this.currentStatus.set(ServerRuntimeStatus.FAILED);
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public long getUptimeSeconds() {
        return Instant.now().getEpochSecond() - startTime.getEpochSecond();
    }
}
