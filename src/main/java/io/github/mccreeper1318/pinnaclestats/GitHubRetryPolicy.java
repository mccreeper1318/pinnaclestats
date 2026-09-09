package io.github.mccreeper1318.pinnaclestats;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

final class GitHubRetryPolicy {
    static final long MAX_RETRY_DELAY_MILLIS = 30_000L;

    private GitHubRetryPolicy() {
    }

    static boolean isRetryable(int statusCode, Map<String, List<String>> headers, String body) {
        if (isRateLimited(statusCode, headers, body)) {
            return true;
        }
        return statusCode == 500 || statusCode == 502 || statusCode == 503 || statusCode == 504;
    }

    static boolean isRateLimited(int statusCode, Map<String, List<String>> headers, String body) {
        if (statusCode == 429) {
            return true;
        }
        if (statusCode != 403) {
            return false;
        }

        if (firstHeader(headers, "Retry-After").isPresent()) {
            return true;
        }
        if (firstHeader(headers, "X-RateLimit-Remaining")
                .map(String::trim)
                .filter("0"::equals)
                .isPresent()) {
            return true;
        }

        String normalizedBody = body == null ? "" : body.toLowerCase(Locale.ROOT);
        return normalizedBody.contains("rate limit");
    }

    static long retryDelayMillis(
            int statusCode,
            Map<String, List<String>> headers,
            String body,
            int attempt,
            long nowEpochSeconds
    ) {
        Optional<Long> retryAfterSeconds = parsePositiveLong(firstHeader(headers, "Retry-After"));
        if (retryAfterSeconds.isPresent()) {
            return capSeconds(retryAfterSeconds.get());
        }

        if (isRateLimited(statusCode, headers, body)) {
            Optional<Long> resetEpochSeconds = parsePositiveLong(firstHeader(headers, "X-RateLimit-Reset"));
            if (resetEpochSeconds.isPresent()) {
                long waitSeconds = resetEpochSeconds.get() - nowEpochSeconds;
                if (waitSeconds <= 0) {
                    waitSeconds = 1;
                } else {
                    // Avoid waking at the exact reset boundary.
                    waitSeconds++;
                }
                return capSeconds(waitSeconds);
            }
        }

        return defaultRetryDelayMillis(attempt);
    }

    static long defaultRetryDelayMillis(int attempt) {
        return switch (attempt) {
            case 1 -> 3000L;
            case 2 -> 7000L;
            default -> 15000L;
        };
    }

    private static long capSeconds(long seconds) {
        if (seconds >= MAX_RETRY_DELAY_MILLIS / 1000L) {
            return MAX_RETRY_DELAY_MILLIS;
        }
        return Math.max(1000L, seconds * 1000L);
    }

    private static Optional<Long> parsePositiveLong(Optional<String> value) {
        if (value.isEmpty()) {
            return Optional.empty();
        }
        try {
            long parsed = Long.parseLong(value.get().trim());
            return parsed > 0 ? Optional.of(parsed) : Optional.empty();
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private static Optional<String> firstHeader(Map<String, List<String>> headers, String name) {
        if (headers == null || headers.isEmpty()) {
            return Optional.empty();
        }
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (!entry.getKey().equalsIgnoreCase(name) || entry.getValue() == null || entry.getValue().isEmpty()) {
                continue;
            }
            String value = entry.getValue().getFirst();
            if (value != null && !value.isBlank()) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }
}
