package com.englishtranslation.backend.media;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;

/** Downloads must only come from Pixabay over https. */
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
}
