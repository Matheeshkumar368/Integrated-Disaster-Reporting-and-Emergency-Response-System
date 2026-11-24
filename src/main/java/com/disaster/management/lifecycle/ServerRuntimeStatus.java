package com.disaster.management.lifecycle;

/**
 * Explicit runtime states for the Integrated Disaster Management System.
 */
public enum ServerRuntimeStatus {
    STARTING,
    READY,
    DEGRADED,
    STOPPING,
    STOPPED,
    FAILED
}
