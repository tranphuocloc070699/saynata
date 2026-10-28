package com.saynata.backend.common;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;

/**
 * Single-flight: many callers ask for the same key at the same moment, but the work runs once.
 * The first caller (the "winner") does the work. The others wait and get the same result
 * (or the same failure). The key is removed when the work ends, so a later call starts fresh.
 *
 * Works inside ONE application instance only. With several instances, the unique index
 * in the database is still the last guard.
 */
public class SingleFlight<T> {

    private final ConcurrentHashMap<String, CompletableFuture<T>> inFlight = new ConcurrentHashMap<>();
    private final Duration waitTimeout;

    public SingleFlight(Duration waitTimeout) {
        this.waitTimeout = waitTimeout;
    }

    public T run(String key, Supplier<T> work) {
        CompletableFuture<T> mine = new CompletableFuture<>();
        CompletableFuture<T> running = inFlight.putIfAbsent(key, mine); // atomic: only one caller wins
        if (running != null) {
            return await(running);
        }
        try {
            T result = work.get();
            mine.complete(result);
            return result;
        } catch (RuntimeException e) {
            mine.completeExceptionally(e); // the waiters get the same failure
            throw e;
        } finally {
            inFlight.remove(key, mine); // always, or this key would be stuck forever
        }
    }

    private T await(CompletableFuture<T> running) {
        try {
            return running.get(waitTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            throw new IllegalStateException(e.getCause());
        } catch (TimeoutException e) {
            throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "lookup_timeout", "This is taking too long. Please try again.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "interrupted", "Request was interrupted.");
        }
    }
}
