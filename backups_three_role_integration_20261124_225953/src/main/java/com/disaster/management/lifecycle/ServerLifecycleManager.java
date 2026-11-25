package com.disaster.management.lifecycle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ServerLifecycleManager
 * Manages deterministic server startup and shutdown lifecycles:
 * 1. Monitors the parent launcher process (Maven wrapper, console runner, or Windows EXE wrapper).
 * 2. Monitors the launcher input stream (EOF detection when the parent terminal closes).
 * 3. Registers JVM runtime shutdown hooks for graceful termination on Ctrl+C.
 * 4. Ensures port 8080 is released immediately with zero orphaned background processes in production.
 * 5. Safely bypasses System.exit and stdin monitoring during unit and integration test executions.
 */
@Component
public class ServerLifecycleManager implements ApplicationListener<ContextClosedEvent> {

    private static final Logger log = LoggerFactory.getLogger(ServerLifecycleManager.class);

    private final ApplicationContext applicationContext;
    private final ServerRuntimeStateHolder stateHolder;
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    @Value("${app.server.lifecycle.exit-jvm-on-shutdown:true}")
    private boolean exitJvmOnShutdown;

    @Value("${app.server.lifecycle.stdin-guardian:false}")
    private boolean stdinGuardianEnabled;

    @Value("${app.server.lifecycle.parent-guardian:false}")
    private boolean parentGuardianEnabled;

    public ServerLifecycleManager(ApplicationContext applicationContext, ServerRuntimeStateHolder stateHolder) {
        this.applicationContext = applicationContext;
        this.stateHolder = stateHolder;
    }

    /**
     * Determines whether the current execution is running within a test runner (JUnit, Surefire, Spring Test).
     */
    public static boolean isTestEnvironment() {
        if (Boolean.getBoolean("app.test.mode") ||
            System.getProperty("surefire.test.class.path") != null ||
            System.getProperty("sun.java.command", "").toLowerCase().contains("surefire") ||
            System.getProperty("sun.java.command", "").toLowerCase().contains("junit")) {
            return true;
        }

        for (StackTraceElement ste : Thread.currentThread().getStackTrace()) {
            String cn = ste.getClassName();
            if (cn.startsWith("org.junit.") ||
                cn.startsWith("org.apache.maven.surefire.") ||
                cn.startsWith("org.springframework.boot.test.") ||
                cn.startsWith("org.springframework.test.")) {
                return true;
            }
        }
        return false;
    }

    @PostConstruct
    public void init() {
        if (isTestEnvironment() || !exitJvmOnShutdown) {
            log.info("ServerLifecycleManager: Test environment or non-exiting mode detected. Guardians and System.exit disabled.");
            return;
        }

        log.info("ServerLifecycleManager active. Monitoring process tree and shutdown signals.");

        // 1. Monitor Parent Process (Maven wrapper or Windows EXE wrapper if enabled)
        if (parentGuardianEnabled) {
            try {
                Optional<ProcessHandle> parentOpt = ProcessHandle.current().parent();
                if (parentOpt.isPresent()) {
                    ProcessHandle parent = parentOpt.get();
                    long parentPid = parent.pid();
                    log.info("Linked to parent launcher process [PID: {}]. Auto-termination guardian active.", parentPid);

                    parent.onExit().thenAccept(ph -> {
                        if (!shuttingDown.get()) {
                            log.info("Parent launcher [PID: {}] terminated. Initiating graceful server shutdown...", parentPid);
                            initiateGracefulShutdown("Parent process exit");
                        }
                    });
                } else {
                    log.info("Running as root process. Standard JVM shutdown hooks will manage lifecycle.");
                }
            } catch (Exception e) {
                log.warn("Notice checking parent process handle: {}", e.getMessage());
            }
        }

        // 2. Monitor Standard Input for EOF / Pipe Broken (occurs when terminal closes or detaches)
        if (stdinGuardianEnabled) {
            Thread stdinMonitor = new Thread(() -> {
                try {
                    int b;
                    while ((b = System.in.read()) != -1) {
                        // Stdin stream active
                    }
                    if (!shuttingDown.get()) {
                        log.info("Launcher input stream reached EOF (terminal/console detached). Initiating graceful shutdown...");
                        initiateGracefulShutdown("Stdin EOF");
                    }
                } catch (Exception e) {
                    if (!shuttingDown.get()) {
                        log.info("Launcher input stream disconnected ({}). Initiating graceful shutdown...", e.getMessage());
                        initiateGracefulShutdown("Stdin disconnected");
                    }
                }
            }, "launcher-stdin-guardian");
            stdinMonitor.setDaemon(true);
            stdinMonitor.start();
        }

        // 3. Register JVM Shutdown Hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            shuttingDown.set(true);
            log.info("JVM shutdown signal received. Port and all server resources releasing gracefully...");
        }, "server-shutdown-hook"));
    }

    public void initiateGracefulShutdown(String reason) {
        if (shuttingDown.compareAndSet(false, true)) {
            stateHolder.setStatus(ServerRuntimeStatus.STOPPING);
            log.info("Graceful shutdown initiated (Reason: {}). Closing Spring ApplicationContext...", reason);

            if (isTestEnvironment() || !exitJvmOnShutdown) {
                log.info("Test environment or non-exiting mode detected. Skipping System.exit().");
                try {
                    SpringApplication.exit(applicationContext, () -> 0);
                } catch (Exception e) {
                    log.warn("Notice during test application context close: {}", e.getMessage());
                }
                stateHolder.setStatus(ServerRuntimeStatus.STOPPED);
                return;
            }

            new Thread(() -> {
                try {
                    int exitCode = SpringApplication.exit(applicationContext, () -> 0);
                    stateHolder.setStatus(ServerRuntimeStatus.STOPPED);
                    System.exit(exitCode);
                } catch (Exception e) {
                    log.warn("Notice during graceful application exit: {}", e.getMessage());
                    stateHolder.setStatus(ServerRuntimeStatus.STOPPED);
                    System.exit(0);
                }
            }, "graceful-exit-thread").start();
        }
    }

    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        stateHolder.setStatus(ServerRuntimeStatus.STOPPED);
        log.info("ContextClosedEvent: ApplicationContext terminated. Port released and HikariCP pool closed.");
    }

    @PreDestroy
    public void onDestroy() {
        log.info("ServerLifecycleManager destroyed cleanly.");
    }
}
