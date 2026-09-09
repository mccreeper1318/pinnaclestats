package io.github.mccreeper1318.pinnaclestats;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitHubRetryPolicyTest {
    @Test
    void permission403FailsImmediately() {
        assertFalse(GitHubRetryPolicy.isRetryable(
                403,
                Map.of("X-RateLimit-Remaining", List.of("42")),
                "{\"message\":\"Resource not accessible by integration\"}"
        ));
    }

    @Test
    void primaryRateLimit403IsRetryableWhenRemainingIsZero() {
        assertTrue(GitHubRetryPolicy.isRetryable(
                403,
                Map.of(
                        "X-RateLimit-Remaining", List.of("0"),
                        "X-RateLimit-Reset", List.of("1010")
                ),
                "{\"message\":\"API rate limit exceeded\"}"
        ));
    }

    @Test
    void secondaryRateLimit403IsRetryableWhenBodyIdentifiesRateLimit() {
        assertTrue(GitHubRetryPolicy.isRetryable(
                403,
                Map.of(),
                "{\"message\":\"You have exceeded a secondary rate limit.\"}"
        ));
    }

    @Test
    void retryAfterOn403IdentifiesRateLimit() {
        assertTrue(GitHubRetryPolicy.isRetryable(
                403,
                Map.of("Retry-After", List.of("5")),
                "{\"message\":\"Please wait before retrying\"}"
        ));
    }

    @Test
    void status429IsAlwaysRetryable() {
        assertTrue(GitHubRetryPolicy.isRetryable(429, Map.of(), ""));
    }

    @Test
    void retryAfterTakesPrecedenceOverReset() {
        long delay = GitHubRetryPolicy.retryDelayMillis(
                429,
                Map.of(
                        "Retry-After", List.of("7"),
                        "X-RateLimit-Reset", List.of("1100")
                ),
                "",
                1,
                1000
        );

        assertEquals(7000L, delay);
    }

    @Test
    void primaryRateLimitWaitsUntilResetWhenRetryAfterIsAbsent() {
        long delay = GitHubRetryPolicy.retryDelayMillis(
                403,
                Map.of(
                        "X-RateLimit-Remaining", List.of("0"),
                        "X-RateLimit-Reset", List.of("1010")
                ),
                "{\"message\":\"API rate limit exceeded\"}",
                1,
                1000
        );

        assertEquals(11_000L, delay);
    }

    @Test
    void resetDelayIsBounded() {
        long delay = GitHubRetryPolicy.retryDelayMillis(
                429,
                Map.of("X-RateLimit-Reset", List.of("5000")),
                "",
                1,
                1000
        );

        assertEquals(GitHubRetryPolicy.MAX_RETRY_DELAY_MILLIS, delay);
    }

    @Test
    void retryAfterDelayIsBounded() {
        long delay = GitHubRetryPolicy.retryDelayMillis(
                429,
                Map.of("Retry-After", List.of("120")),
                "",
                1,
                1000
        );

        assertEquals(GitHubRetryPolicy.MAX_RETRY_DELAY_MILLIS, delay);
    }

    @Test
    void malformedLimitHeadersFallBackToBoundedExponentialDelay() {
        long delay = GitHubRetryPolicy.retryDelayMillis(
                403,
                Map.of(
                        "X-RateLimit-Remaining", List.of("0"),
                        "X-RateLimit-Reset", List.of("not-a-number")
                ),
                "{\"message\":\"API rate limit exceeded\"}",
                2,
                1000
        );

        assertEquals(7000L, delay);
    }

    @Test
    void serverErrorsRemainRetryable() {
        assertTrue(GitHubRetryPolicy.isRetryable(503, Map.of(), ""));
        assertFalse(GitHubRetryPolicy.isRetryable(404, Map.of(), ""));
    }
}
