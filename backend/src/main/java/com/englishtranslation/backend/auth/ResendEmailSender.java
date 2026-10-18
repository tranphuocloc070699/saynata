package com.englishtranslation.backend.auth;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.LongConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

/**
 * Sends the code with Resend (https://resend.com/docs/api-reference/emails/send-email): one HTTPS POST.
 * A 2xx answer is success. On 429 or 5xx (or a network error) we wait a moment and try once more.
 * A failure is only logged, never thrown, so the endpoint still answers 204. The log never has the API key
 * or the code.
 */
public class ResendEmailSender implements EmailSender {

    static final String DEFAULT_URL = "https://api.resend.com/emails";
    private static final Logger log = LoggerFactory.getLogger(ResendEmailSender.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final long RETRY_PAUSE_MILLIS = 1000;

    private final String apiKey;
    private final String fromAddress;
    private final URI url;
    private final HttpClient http;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final LongConsumer sleeper;

    public ResendEmailSender(String apiKey, String fromAddress) {
        this(apiKey, fromAddress, DEFAULT_URL, ResendEmailSender::sleep);
    }

    /** For tests: another address (a fake server) and a sleeper that does not really wait. */
    ResendEmailSender(String apiKey, String fromAddress, String url, LongConsumer sleeper) {
        this.apiKey = apiKey;
        this.fromAddress = fromAddress;
        this.url = URI.create(url);
        this.http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        this.sleeper = sleeper;
    }

    @Override
    public void sendSignInCode(String toEmail, String code, int validMinutes) {
        send(toEmail, EmailSender.subject(), EmailSender.body(code, validMinutes), html(code, validMinutes),
                "sign-in code");
    }

    @Override
    public void sendSignInLink(String toEmail, String link, int validMinutes) {
        send(toEmail, EmailSender.linkSubject(), EmailSender.linkBody(link, validMinutes),
                EmailSender.linkHtml(link, validMinutes), "sign-in link");
    }

    /** {@code what} names the email in the log ("sign-in code"); the log never has the code or the link. */
    private void send(String toEmail, String subject, String text, String html, String what) {
        try {
            String json = jsonMapper.writeValueAsString(Map.of(
                    "from", fromAddress,
                    "to", List.of(toEmail),
                    "subject", subject,
                    "text", text,
                    "html", html));
            HttpRequest request = HttpRequest.newBuilder(url)
                    .timeout(TIMEOUT)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            if (trySend(request, true, what)) {
                return;
            }
            sleeper.accept(RETRY_PAUSE_MILLIS);
            trySend(request, false, what);
        } catch (Exception e) {
            // Only the exception type: its message could hold the request.
            log.error("Resend: could not send the {} ({})", what, e.getClass().getSimpleName());
        }
    }

    /** True when we are done (success, or an error that a second try cannot fix). False = try again. */
    private boolean trySend(HttpRequest request, boolean mayRetry, String what) throws InterruptedException {
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                return true;
            }
            boolean retry = mayRetry && (status == 429 || status >= 500);
            log.error("Resend: sending the {} failed with HTTP {}{}", what, status,
                    retry ? " (trying once more)" : "");
            return !retry;
        } catch (IOException e) {
            log.error("Resend: sending the {} failed ({}){}", what, e.getClass().getSimpleName(),
                    mayRetry ? " (trying once more)" : "");
            return !mayRetry;
        }
    }

    private static String html(String code, int validMinutes) {
        return "<p>Your Saynata sign-in code is <strong>" + code + "</strong>.</p>"
                + "<p>It works for " + validMinutes + " minutes. You can use it once.</p>"
                + "<p>If you did not ask for this code, you can ignore this email.</p>";
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
