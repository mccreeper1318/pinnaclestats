package io.github.mccreeper1318.pinnaclestats;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatsApiServerTest {
    @Test
    void configuresFiniteHttpTimeoutsWhenUnsetOrDisabled() {
        String requestKey = "sun.net.httpserver.maxReqTime";
        String responseKey = "sun.net.httpserver.maxRspTime";
        String previousRequest = System.getProperty(requestKey);
        String previousResponse = System.getProperty(responseKey);
        try {
            System.clearProperty(requestKey);
            System.setProperty(responseKey, "-1");

            StatsApiServer.configureHttpTimeouts();

            assertEquals("10", System.getProperty(requestKey));
            assertEquals("10", System.getProperty(responseKey));
        } finally {
            restoreProperty(requestKey, previousRequest);
            restoreProperty(responseKey, previousResponse);
        }
    }

    @Test
    void preservesPositiveHttpTimeoutOverrides() {
        String requestKey = "sun.net.httpserver.maxReqTime";
        String responseKey = "sun.net.httpserver.maxRspTime";
        String previousRequest = System.getProperty(requestKey);
        String previousResponse = System.getProperty(responseKey);
        try {
            System.setProperty(requestKey, "25");
            System.setProperty(responseKey, "30");

            StatsApiServer.configureHttpTimeouts();

            assertEquals("25", System.getProperty(requestKey));
            assertEquals("30", System.getProperty(responseKey));
        } finally {
            restoreProperty(requestKey, previousRequest);
            restoreProperty(responseKey, previousResponse);
        }
    }

    @Test
    void apiExecutorHasBoundedDaemonWorkers() throws Exception {
        ThreadPoolExecutor executor = StatsApiServer.createApiExecutor();
        try {
            assertEquals(4, executor.getCorePoolSize());
            assertEquals(4, executor.getMaximumPoolSize());
            assertInstanceOf(ArrayBlockingQueue.class, executor.getQueue());
            assertEquals(64, executor.getQueue().remainingCapacity());

            Thread worker = executor.submit(Thread::currentThread).get(5, TimeUnit.SECONDS);
            assertTrue(worker.isDaemon());
            assertTrue(worker.getName().startsWith("PinnacleStats-API-"));
        } finally {
            executor.shutdownNow();
        }

        assertTrue(executor.isShutdown());
    }

    @Test
    void saturatedExecutorRejectsOverflowWithoutRunningOnSubmittingThread() throws Exception {
        ThreadPoolExecutor executor = StatsApiServer.createApiExecutor();
        CountDownLatch releaseWorkers = new CountDownLatch(1);
        AtomicBoolean overflowRan = new AtomicBoolean(false);

        try {
            for (int i = 0; i < 4; i++) {
                executor.execute(() -> {
                    try {
                        releaseWorkers.await();
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                });
            }

            for (int i = 0; i < 64; i++) {
                executor.execute(() -> {
                });
            }

            assertThrows(RejectedExecutionException.class, () ->
                    executor.execute(() -> overflowRan.set(true))
            );
            assertFalse(overflowRan.get());
        } finally {
            releaseWorkers.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void handlerClosesExchangeOnEarlyReturn() throws Exception {
        StatsApiServer api = new StatsApiServer(null, testSettings(), null);
        TestExchange exchange = new TestExchange("OPTIONS", URI.create("/api/health"));

        api.handle(exchange);

        assertEquals(204, exchange.responseCode);
        assertTrue(exchange.closed);
    }

    private void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    private PluginSettings testSettings() {
        return new PluginSettings(
                true,
                "127.0.0.1",
                1042,
                List.of(),
                60,
                "world",
                "world/players/stats",
                15,
                true,
                true,
                5,
                5,
                5,
                true,
                true,
                Map.of(),
                true,
                "plugins/PinnacleStats/export",
                true,
                false,
                false,
                "",
                "",
                "main",
                "assets/player-stats",
                "",
                "Update PinnacleStats player data",
                "PinnacleStats",
                "pinnaclestats@users.noreply.github.com"
        );
    }

    private static final class TestExchange extends HttpExchange {
        private final String method;
        private final URI uri;
        private final Headers requestHeaders = new Headers();
        private final Headers responseHeaders = new Headers();
        private final ByteArrayOutputStream responseBody = new ByteArrayOutputStream();
        private boolean closed;
        private int responseCode = -1;

        private TestExchange(String method, URI uri) {
            this.method = method;
            this.uri = uri;
        }

        @Override
        public Headers getRequestHeaders() {
            return requestHeaders;
        }

        @Override
        public Headers getResponseHeaders() {
            return responseHeaders;
        }

        @Override
        public URI getRequestURI() {
            return uri;
        }

        @Override
        public String getRequestMethod() {
            return method;
        }

        @Override
        public HttpContext getHttpContext() {
            return null;
        }

        @Override
        public void close() {
            closed = true;
        }

        @Override
        public InputStream getRequestBody() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public OutputStream getResponseBody() {
            return responseBody;
        }

        @Override
        public void sendResponseHeaders(int rCode, long responseLength) {
            responseCode = rCode;
        }

        @Override
        public InetSocketAddress getRemoteAddress() {
            return new InetSocketAddress("127.0.0.1", 12345);
        }

        @Override
        public int getResponseCode() {
            return responseCode;
        }

        @Override
        public InetSocketAddress getLocalAddress() {
            return new InetSocketAddress("127.0.0.1", 1042);
        }

        @Override
        public String getProtocol() {
            return "HTTP/1.1";
        }

        @Override
        public Object getAttribute(String name) {
            return null;
        }

        @Override
        public void setAttribute(String name, Object value) {
        }

        @Override
        public void setStreams(InputStream i, OutputStream o) {
        }

        @Override
        public HttpPrincipal getPrincipal() {
            return null;
        }
    }
}
