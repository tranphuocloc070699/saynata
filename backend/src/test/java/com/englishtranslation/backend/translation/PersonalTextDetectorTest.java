package com.englishtranslation.backend.translation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PersonalTextDetectorTest {

    private final PersonalTextDetector detector = new PersonalTextDetector();

    @ParameterizedTest
    @ValueSource(strings = {
            "Write to me at john.doe@example.com please",
            "Call me on 0912 345 678",
            "Call +84 912 345 678 now",
            "My number is (028) 3822-1234",
            "Phone: 028.3822.1234",
            "Visit https://example.org/page",
            "See http://foo for details",
            "Go to www.example today",
            "Open saynata.com and look",
            "Check name.vn for more",
            "My card is 123456",
            "Order 12345678 is late"
    })
    void personalTextIsDetected(String text) {
        assertThat(detector.looksPersonal(text)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "I have 2 cats",
            "We moved here in 2026",
            "It costs 12.50 dollars",
            "She is 25 years old and has 3 dogs",
            "Room 101 is on the second floor",
            "I love you",
            "Hello. How are you? I am fine.",
            "Call me at 12345"
    })
    void normalSentencesAreNotDetected(String text) {
        assertThat(detector.looksPersonal(text)).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void emptyTextIsNotPersonal(String text) {
        assertThat(detector.looksPersonal(text)).isFalse();
    }
}
