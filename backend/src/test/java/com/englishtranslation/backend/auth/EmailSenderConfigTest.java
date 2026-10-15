package com.englishtranslation.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.englishtranslation.backend.claude.ClaudeService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Deploy safety: the backend must start with no AWS settings at all (it then uses the logging fake), and with
 * SES settings it must still start without touching AWS (the client is built on the first send).
 */
class EmailSenderConfigTest {

    @Nested
    @SpringBootTest(properties = "app.claude.api-key=test-key")
    class WithoutAwsSettings {
        @Autowired
        EmailSender sender;
        @MockitoBean
        ClaudeService claude;

        @Test
        void usesTheLoggingFakeAndDoesNotFail() {
            assertThat(sender).isInstanceOf(LoggingEmailSender.class);
            sender.sendSignInCode("a@example.com", "123456", 10); // only logs
        }
    }

    @Nested
    @SpringBootTest(properties = { "app.claude.api-key=test-key", "app.email.from=login@saynata.example",
            "app.email.region=ap-southeast-1" })
    class WithSesSettings {
        @Autowired
        EmailSender sender;
        @MockitoBean
        ClaudeService claude;

        @Test
        void usesSesButDoesNotCallAwsAtStartUp() {
            assertThat(sender).isInstanceOf(SesEmailSender.class);
        }
    }

    @Test
    void theEmailIsShortAndPlain() {
        assertThat(EmailSender.subject()).isEqualTo("Your Saynata sign-in code");
        assertThat(EmailSender.body("123456", 10))
                .contains("Your Saynata sign-in code is 123456.")
                .contains("It works for 10 minutes.")
                .contains("If you did not ask for this code, you can ignore this email.");
    }

    @Test
    void emailsAreNormalised() {
        assertThat(Emails.normalize("  Pat.Smith@Example.COM ")).isEqualTo("pat.smith@example.com");
        for (String bad : new String[] { "", "   ", "no-at-sign", "a@b", "a@@b.com", "a b@c.com", "a@b..com",
                "a@b.com\nBcc: x@y.com", "x".repeat(250) + "@b.com" }) {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> Emails.normalize(bad))
                    .as(bad).isInstanceOf(InvalidEmailException.class);
        }
    }
}
