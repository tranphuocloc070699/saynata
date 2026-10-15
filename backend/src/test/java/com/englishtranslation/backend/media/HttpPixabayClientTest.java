package com.englishtranslation.backend.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Downloads must only come from Pixabay over https, and a 429 is tried again (TASK-63). */
class HttpPixabayClientTest {

    @Test
    void acceptsPixabayAndItsSubdomains() {
        assertThat(HttpPixabayClient.isPixabayAddress(URI.create("https://pixabay.com/get/x.jpg"))).isTrue();
        assertThat(HttpPixabayClient.isPixabayAddress(URI.create("https://cdn.pixabay.com/photo/x.jpg"))).isTrue();
    }

    @Test
    void rejectsLookAlikeHostsAndPlainHttp() {
        assertThat(HttpPixabayClient.isPixabayAddress(URI.create("https://evilpixabay.com/x.jpg"))).isFalse();
        assertThat(HttpPixabayClient.isPixabayAddress(URI.create("https://pixabay.com.evil.net/x.jpg"))).isFalse();
        assertThat(HttpPixabayClient.isPixabayAddress(URI.create("http://cdn.pixabay.com/x.jpg"))).isFalse();
        assertThat(HttpPixabayClient.isPixabayAddress(URI.create("file:///etc/passwd"))).isFalse();
    }

    // ---- 429 ----

    private static final String FILE = "https://cdn.pixabay.com/x.jpg";

    // The fake HTTP client answers from a list; the sleeper only writes down how long it was asked to wait.
    private final List<Long> pauses = new ArrayList<>();
    private final HttpClient http = mock(HttpClient.class);
    private final HttpPixabayClient client = new HttpPixabayClient(new MediaProperties("dir", "https://x", "key"),
            JsonMapper.builder().build(), http, pauses::add, 0);

    @SuppressWarnings("unchecked")
    private static HttpResponse<byte[]> answer(int status, byte[] body, Map<String, List<String>> headers) {
        HttpResponse<byte[]> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        when(response.headers()).thenReturn(HttpHeaders.of(headers, (a, b) -> true));
        return response;
    }

    @SafeVarargs
    private void answers(HttpResponse<byte[]>... responses) throws Exception {
        doReturn(responses[0], (Object[]) Arrays.copyOfRange(responses, 1, responses.length)).when(http).send(any(), any());
    }

    @Test
    void aDownloadThatGets429AndThen200Succeeds() throws Exception {
        answers(answer(429, new byte[0], Map.of()), answer(200, new byte[] {1, 2, 3}, Map.of()));

        assertThat(client.download(FILE)).containsExactly(1, 2, 3);
        verify(http, times(2)).send(any(), any());
        assertThat(pauses).containsExactly(1000L);
    }

    @Test
    void aDownloadThatGets429ThreeTimesGivesUpWithTheBusyException() throws Exception {
        answers(answer(429, new byte[0], Map.of()), answer(429, new byte[0], Map.of()), answer(429, new byte[0], Map.of()));

        assertThatThrownBy(() -> client.download(FILE)).isInstanceOf(PixabayBusyException.class);
        verify(http, times(3)).send(any(), any());
        assertThat(pauses).containsExactly(1000L, 2000L);
    }

    @Test
    void retryAfterIsRespected() throws Exception {
        answers(answer(429, new byte[0], Map.of("Retry-After", List.of("3"))), answer(200, new byte[] {9}, Map.of()));

        client.download(FILE);
        assertThat(pauses).containsExactly(3000L);
    }

    @Test
    void aRetryAfterLongerThanTheBudgetGivesUpAtOnce() throws Exception {
        answers(answer(429, new byte[0], Map.of("Retry-After", List.of("60"))), answer(200, new byte[] {9}, Map.of()));

        assertThatThrownBy(() -> client.download(FILE)).isInstanceOf(PixabayBusyException.class);
        verify(http, times(1)).send(any(), any());
        assertThat(pauses).isEmpty();
    }

    @Test
    void otherErrorsAreNotRetried() throws Exception {
        answers(answer(500, new byte[0], Map.of()));

        assertThatThrownBy(() -> client.download(FILE))
                .isInstanceOf(IllegalStateException.class).isNotInstanceOf(PixabayBusyException.class);
        verify(http, times(1)).send(any(), any());
    }
}
