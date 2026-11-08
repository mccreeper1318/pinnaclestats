package io.github.mccreeper1318.pinnaclestats;

import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitHubPublisherJsonLimitTest {
    @Test
    void cancelsGitHubResponseWhenBodyExceedsLimit() {
        HttpResponse.BodySubscriber<String> subscriber =
                GitHubPublisher.limitedUtf8BodyHandler(4).apply(null);
        AtomicBoolean cancelled = new AtomicBoolean();

        subscriber.onSubscribe(new Flow.Subscription() {
            @Override
            public void request(long n) {
            }

            @Override
            public void cancel() {
                cancelled.set(true);
            }
        });

        subscriber.onNext(List.of(ByteBuffer.wrap("12345".getBytes(StandardCharsets.UTF_8))));

        CompletionException ex = assertThrows(
                CompletionException.class,
                () -> subscriber.getBody().toCompletableFuture().join()
        );
        assertTrue(cancelled.get());
        assertTrue(ex.getCause().getMessage().contains("maximum size of 4 bytes"));
    }
}
