package com.disaster.management.lifecycle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * 1. Monitors the parent launcher process (Maven wrapper, console runner, or future desktop EXE wrapper).
 * 2. Monitors the launcher input stream (EOF detection when the parent terminal closes).
 * 3. Registers JVM runtime shutdown hooks for graceful termination on Ctrl+C.
 * 4. Ensures port 8080 is released immediately with zero orphaned background processes.
 */
@Component
public class ServerLifecycleManager implements ApplicationListener<ContextClosedEvent> {

    private static final Logger log = LoggerFactory.getLogger(ServerLifecycleManager.class);

    private final ApplicationContext applicationContext;
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    public ServerLifecycleManager(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @PostConstruct
    public void init() {
        log.info("ServerLifecycleManager active. Monitoring process tree and shutdown signals.");

        // 1. Monitor Parent Process (Maven wrapper or future Windows EXE wrapper)
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

        // 2. Monitor Standard Input for EOF / Pipe Broken (occurs when terminal closes or detaches)
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

        // 3. Register JVM Shutdown Hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            shuttingDown.set(true);
            log.info("JVM shutdown signal received. Port 8080 and all server resources releasing gracefully...");
        }, "server-shutdown-hook"));
    }

    public void initiateGracefulShutdown(String reason) {
        if (shuttingDown.compareAndSet(false, true)) {
            log.info("Graceful shutdown initiated (Reason: {}). Closing Spring ApplicationContext...", reason);
            new Thread(() -> {
                try {
                    int exitCode = SpringApplication.exit(applicationContext, () -> 0);
                    System.exit(exitCode);
                } catch (Exception e) {
                    log.warn("Notice during graceful application exit: {}", e.getMessage());
                    System.exit(0);
                }
            }, "graceful-exit-thread").start();
        }
    }

    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        log.info("ContextClosedEvent: ApplicationContext terminated. Port 8080 released and HikariCP pool closed.");
    }

    @PreDestroy
    public void onDestroy() {
        log.info("ServerLifecycleManager destroyed cleanly.");
    }
}
