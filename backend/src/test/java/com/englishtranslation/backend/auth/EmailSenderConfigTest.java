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

    @Nested
    @SpringBootTest(properties = { "app.claude.api-key=test-key", "app.email.from=login@saynata.example",
            "app.email.resend-api-key=re_test", "app.email.region=ap-southeast-1" })
    class WithResendAndSesSettings {
        @Autowired
        EmailSender sender;
        @MockitoBean
        ClaudeService claude;

        @Test
        void resendWinsOverSes() {
            assertThat(sender).isInstanceOf(ResendEmailSender.class);
        }
    }

    @Nested
    @SpringBootTest(properties = { "app.claude.api-key=test-key", "app.email.from=",
            "app.email.ses-from=old@saynata.example" })
    class WithOnlyTheOldSesFromName {
        @Autowired
        EmailSender sender;
        @MockitoBean
        ClaudeService claude;

        @Test
        void sesFromEmailStillWorksAsAFallback() {
            assertThat(sender).isInstanceOf(SesEmailSender.class);
        }
    }

    @Test
    void chooseResendOverSesOverTheFake() {
        AuthConfig config = new AuthConfig();
        assertThat(config.emailSender(new EmailProperties("a@b.com", "r", "re_key", "")))
                .isInstanceOf(ResendEmailSender.class);
        assertThat(config.emailSender(new EmailProperties("a@b.com", "r", "", "")))
                .isInstanceOf(SesEmailSender.class);
        assertThat(config.emailSender(new EmailProperties("", "r", "", "old@b.com")))
                .isInstanceOf(SesEmailSender.class);
        assertThat(config.emailSender(new EmailProperties("", "", "", ""))).isInstanceOf(LoggingEmailSender.class);
        assertThat(config.emailSender(new EmailProperties(null, null, null, null)))
                .isInstanceOf(LoggingEmailSender.class);
        // A key without a from address cannot send: it falls back, it does not fail.
        assertThat(config.emailSender(new EmailProperties("", "", "re_key", "")))
                .isInstanceOf(LoggingEmailSender.class);
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
