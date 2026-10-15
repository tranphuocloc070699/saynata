package com.englishtranslation.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** ResendEmailSender against a small fake HTTP server (no real call to Resend). */
class ResendEmailSenderTest {

    private static final String KEY = "re_secret_key_123";
    private static final String CODE = "482913";

    private HttpServer server;
    private final List<String> authHeaders = new CopyOnWriteArrayList<>();
    private final List<String> contentTypes = new CopyOnWriteArrayList<>();
    private final List<String> bodies = new CopyOnWriteArrayList<>();
    private final List<Integer> answers = new CopyOnWriteArrayList<>();
    private final AtomicInteger calls = new AtomicInteger();
    private final List<Long> sleeps = new ArrayList<>();
    private ListAppender<ILoggingEvent> logs;
    private Logger senderLogger;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/emails", exchange -> {
            int n = calls.getAndIncrement();
            authHeaders.add(exchange.getRequestHeaders().getFirst("Authorization"));
            contentTypes.add(exchange.getRequestHeaders().getFirst("Content-Type"));
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            int status = n < answers.size() ? answers.get(n) : 200;
            byte[] reply = "{\"id\":\"abc\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, reply.length);
            exchange.getResponseBody().write(reply);
            exchange.close();
        });
        server.start();
        senderLogger = (Logger) LoggerFactory.getLogger(ResendEmailSender.class);
        logs = new ListAppender<>();
        logs.start();
        senderLogger.addAppender(logs);
    }

    @AfterEach
    void stop() {
        server.stop(0);
        senderLogger.detachAppender(logs);
    }

    private ResendEmailSender sender() {
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/emails";
        return new ResendEmailSender(KEY, "login@loffy.me", url, sleeps::add);
    }

    private String allLogs() {
        return logs.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (a, b) -> a + "\n" + b);
    }

    @Test
    void sendsOnePostWithTheRightHeadersAndBody() throws Exception {
        sender().sendSignInCode("pat@example.com", CODE, 10);

        assertThat(calls.get()).isEqualTo(1);
        assertThat(authHeaders).containsExactly("Bearer " + KEY);
        assertThat(contentTypes.get(0)).startsWith("application/json");
        JsonNode body = JsonMapper.builder().build().readTree(bodies.get(0));
        assertThat(body.get("from").asString()).isEqualTo("login@loffy.me");
        assertThat(body.get("to")).hasSize(1);
        assertThat(body.get("to").get(0).asString()).isEqualTo("pat@example.com");
        assertThat(body.get("subject").asString()).isEqualTo(EmailSender.subject());
        assertThat(body.get("text").asString()).isEqualTo(EmailSender.body(CODE, 10));
        assertThat(body.get("html").asString()).contains(CODE);
        assertThat(sleeps).isEmpty();
        assertThat(allLogs()).doesNotContain(KEY).doesNotContain(CODE);
    }

    @Test
    void retriesOnceAfter429ThenSucceeds() {
        answers.add(429);

        sender().sendSignInCode("pat@example.com", CODE, 10);

        assertThat(calls.get()).isEqualTo(2);
        assertThat(sleeps).hasSize(1);
        assertThat(allLogs()).contains("429").doesNotContain(KEY).doesNotContain(CODE);
    }

    @Test
    void retriesOnceOn5xxAndThenGivesUp() {
        answers.add(503);
        answers.add(500);

        assertThatCode(() -> sender().sendSignInCode("pat@example.com", CODE, 10)).doesNotThrowAnyException();

        assertThat(calls.get()).isEqualTo(2);
        assertThat(allLogs()).contains("500").doesNotContain(KEY).doesNotContain(CODE);
    }

    @Test
    void doesNotRetryAClientError() {
        answers.add(403);

        assertThatCode(() -> sender().sendSignInCode("pat@example.com", CODE, 10)).doesNotThrowAnyException();

        assertThat(calls.get()).isEqualTo(1);
        assertThat(allLogs()).contains("403").doesNotContain(KEY).doesNotContain(CODE);
    }

    @Test
    void aNetworkFailureIsLoggedAndNotThrown() {
        int port = server.getAddress().getPort();
        server.stop(0);
        ResendEmailSender down = new ResendEmailSender(KEY, "login@loffy.me", "http://127.0.0.1:" + port + "/emails",
                sleeps::add);

        assertThatCode(() -> down.sendSignInCode("pat@example.com", CODE, 10)).doesNotThrowAnyException();

        assertThat(sleeps).hasSize(1);
        assertThat(allLogs()).contains("failed").doesNotContain(KEY).doesNotContain(CODE);
    }
}
