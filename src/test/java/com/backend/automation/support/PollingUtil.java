package com.backend.automation.support;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;

import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;

public final class PollingUtil {

    private PollingUtil() {
    }

    public static <T> PollResult<T> await(
            String description,
            long timeoutMs,
            long intervalMs,
            Supplier<T> observation,
            Predicate<T> condition,
            Consumer<T> assertion) {
        return await(
                description,
                timeoutMs,
                intervalMs,
                observation,
                condition,
                assertion,
                ignored -> 0,
                String::valueOf);
    }

    public static <T> PollResult<T> await(
            String description,
            long timeoutMs,
            long intervalMs,
            Supplier<T> observation,
            Predicate<T> condition,
            Consumer<T> assertion,
            ToLongFunction<T> retryDelayMs,
            Function<T, String> observationDescription) {
        long startedAt = System.nanoTime();
        AtomicReference<T> lastValue = new AtomicReference<>();
        AtomicInteger attempts = new AtomicInteger();
        try {
            Awaitility.await()
                    .alias(description)
                    .pollDelay(Duration.ZERO)
                    .pollInterval((pollCount, previousDuration) -> {
                        T value = lastValue.get();
                        long delayMs = value == null ? 0 : retryDelayMs.applyAsLong(value);
                        return Duration.ofMillis(delayMs > 0 ? delayMs : intervalMs);
                    })
                    .atMost(Duration.ofMillis(timeoutMs))
                    .untilAsserted(() -> {
                        T value = observation.get();
                        lastValue.set(value);
                        attempts.incrementAndGet();
                        if (!condition.test(value)) {
                            throw new AssertionError("Condition is not yet satisfied");
                        }
                        assertion.accept(value);
                    });
        } catch (ConditionTimeoutException exception) {
            String observed = lastValue.get() == null
                    ? "not observed"
                    : observationDescription.apply(lastValue.get());
            throw new PollingTimeoutException("Timed out waiting for " + description
                    + "; last observed value=" + observed
                    + ", timeout=" + timeoutMs + " ms"
                    + ", polling interval=" + intervalMs + " ms"
                    + ", attempts=" + attempts.get()
                    + ", elapsed=" + elapsedMs(startedAt) + " ms", exception);
        }
        return new PollResult<>(lastValue.get(), attempts.get(), elapsedMs(startedAt));
    }

    private static long elapsedMs(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }

    public record PollResult<T>(T value, int attempts, long elapsedMs) {
    }

    public static final class PollingTimeoutException extends IllegalStateException {
        public PollingTimeoutException(String message) {
            super(message);
        }

        public PollingTimeoutException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
