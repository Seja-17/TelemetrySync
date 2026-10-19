package com.fordprep.telemetry.service;

import com.fordprep.telemetry.model.MonitoredService;
import com.fordprep.telemetry.repository.MonitoredServiceRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class MonitorEngine {

    private static final Logger log = LoggerFactory.getLogger(MonitorEngine.class);

    private static final Duration TIMEOUT = Duration.ofSeconds(3);
    private static final String STATUS_UP = "UP";
    private static final String STATUS_DOWN = "DOWN";

    /** Total tries per service: the first attempt plus one retry. */
    private static final int MAX_ATTEMPTS = 2;
    private static final long RETRY_BACKOFF_MS = 500;

    /** At most this many services are probed at the same moment (bounded, so no thread explosion). */
    private static final int PARALLELISM = 10;

    /** Safety net only: each probe is already limited to about 7 seconds by the timeouts above. */
    private static final long MAX_WAIT_SECONDS = 60;

    /** Result of ONE attempt. Tells the retry logic whether trying again could help. */
    private enum Outcome { UP, DOWN_RETRYABLE, DOWN_FINAL }

    private final MonitoredServiceRepository repository;

    /** One shared, thread-safe client: reuses connections instead of building one per request. */
    private final HttpClient httpClient;

    /** Fixed-size pool of worker threads that run the probes in parallel. */
    private final ExecutorService executor;

    public MonitorEngine(MonitoredServiceRepository repository) {
        this.repository = repository;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();

        AtomicInteger counter = new AtomicInteger(1);
        this.executor = Executors.newFixedThreadPool(PARALLELISM, task -> {
            Thread thread = new Thread(task, "health-check-" + counter.getAndIncrement());
            thread.setDaemon(true); // never blocks the app from shutting down
            return thread;
        });
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    /**
     * Autonomous loop: runs 5s after startup, then every 60s.
     * Wrapped in try/catch so an unexpected error never kills the schedule.
     */
    @Scheduled(fixedRate = 60000, initialDelay = 5000)
    public void scheduledHealthCheck() {
        log.info("Scheduled health check cycle STARTED");
        long startNanos = System.nanoTime();
        try {
            checkAllServices();
        } catch (Exception ex) {
            log.error("Scheduled health check cycle FAILED unexpectedly", ex);
        } finally {
            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
            log.info("Scheduled health check cycle FINISHED in {} ms", elapsedMs);
        }
    }

    /**
     * Checks every registered service in parallel.
     * Synchronized so a manual /ping-all and the scheduler never run overlapping sweeps.
     */
    public synchronized void checkAllServices() {
        List<MonitoredService> services = repository.findAll();
        log.info("Checking {} service(s) in parallel (max {} at a time)", services.size(), PARALLELISM);

        // Phase 1: start every probe. The slow network work happens on the worker threads.
        List<Future<String>> futures = new ArrayList<>();
        for (MonitoredService service : services) {
            String name = service.getName();
            String url = service.getUrl();
            futures.add(executor.submit(() -> probeWithRetry(name, url)));
        }

        // Phase 2: collect the results and save them from THIS thread only,
        // so database writes never happen concurrently.
        for (int i = 0; i < services.size(); i++) {
            MonitoredService service = services.get(i);
            String newStatus = awaitResult(futures.get(i), service.getName());
            String oldStatus = service.getStatus();

            service.setStatus(newStatus);
            service.setLastChecked(LocalDateTime.now());
            repository.save(service);

            logStatusChange(service, oldStatus, newStatus);
        }
    }

    /** Waits for one probe's result. Never throws: any failure maps to DOWN. */
    private String awaitResult(Future<String> future, String name) {
        try {
            return future.get(MAX_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt(); // restore interrupt flag
            future.cancel(true);
            log.warn("Waiting for '{}' was interrupted", name);
            return STATUS_DOWN;
        } catch (ExecutionException | TimeoutException ex) {
            future.cancel(true);
            log.error("Probe for '{}' failed unexpectedly", name, ex);
            return STATUS_DOWN;
        }
    }

    /** Simple alert trail: a WARN when a service goes down, an INFO when it recovers. */
    private void logStatusChange(MonitoredService service, String oldStatus, String newStatus) {
        if (newStatus.equals(oldStatus)) {
            log.info("Service '{}' ({}) is still {}", service.getName(), service.getUrl(), newStatus);
        } else if (STATUS_DOWN.equals(newStatus)) {
            log.warn("ALERT: service '{}' ({}) changed {} -> DOWN",
                    service.getName(), service.getUrl(), oldStatus);
        } else if (STATUS_DOWN.equals(oldStatus)) {
            log.info("RECOVERED: service '{}' ({}) is back UP", service.getName(), service.getUrl());
        } else {
            log.info("Service '{}' ({}) is {} (was {})",
                    service.getName(), service.getUrl(), newStatus, oldStatus);
        }
    }

    /** Runs on a worker thread. Tries up to MAX_ATTEMPTS times, but only retries temporary failures. */
    private String probeWithRetry(String name, String url) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            Outcome outcome = probeOnce(name, url);

            if (outcome == Outcome.UP) {
                return STATUS_UP;
            }
            if (outcome == Outcome.DOWN_FINAL || attempt == MAX_ATTEMPTS) {
                return STATUS_DOWN;
            }

            log.info("Retrying '{}' (attempt {} of {}) after a short pause", name, attempt + 1, MAX_ATTEMPTS);
            try {
                Thread.sleep(RETRY_BACKOFF_MS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt(); // restore interrupt flag
                return STATUS_DOWN;
            }
        }
        return STATUS_DOWN;
    }

    /** One attempt. Never throws: every failure becomes an Outcome. */
    private Outcome probeOnce(String name, String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(TIMEOUT)
                    .GET()
                    .build();

            // discarding() drops the body immediately, saving memory
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            int code = response.statusCode();

            if (code >= 200 && code < 400) {
                return Outcome.UP;
            }
            log.warn("Service '{}' returned HTTP {}", name, code);
            // 5xx = server-side trouble that may be temporary. 4xx = our request is wrong, retrying won't help.
            return code >= 500 ? Outcome.DOWN_RETRYABLE : Outcome.DOWN_FINAL;

        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt(); // restore interrupt flag
            log.warn("Check for '{}' was interrupted", name);
            return Outcome.DOWN_FINAL;
        } catch (IOException ex) { // includes HttpTimeoutException and ConnectException
            log.warn("Network failure for '{}': {}", name, ex.toString());
            return Outcome.DOWN_RETRYABLE;
        } catch (IllegalArgumentException ex) { // malformed URL / unsupported scheme
            log.warn("Invalid URL for '{}': {}", name, ex.getMessage());
            return Outcome.DOWN_FINAL;
        } catch (Exception ex) { // last-resort safety net
            log.error("Unexpected error checking '{}'", name, ex);
            return Outcome.DOWN_FINAL;
        }
    }
}