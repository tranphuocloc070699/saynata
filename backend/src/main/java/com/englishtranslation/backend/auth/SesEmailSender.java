package com.englishtranslation.backend.auth;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

/**
 * Sends the code with AWS SES v2. The credentials come from the standard AWS places (AWS_ACCESS_KEY_ID and
 * AWS_SECRET_ACCESS_KEY in the environment). The client is built on the first send, not at start-up, so a missing
 * or wrong AWS setting can never stop the backend from starting.
 */
public class SesEmailSender implements EmailSender {

    private final String fromAddress;
    private final String region;
    private volatile SesV2Client client;

    public SesEmailSender(String fromAddress, String region) {
        this.fromAddress = fromAddress;
        this.region = region;
    }

    @Override
    public void sendSignInCode(String toEmail, String code, int validMinutes) {
        send(toEmail, EmailSender.subject(), EmailSender.body(code, validMinutes));
    }

    @Override
    public void sendSignInLink(String toEmail, String link, int validMinutes) {
        send(toEmail, EmailSender.linkSubject(), EmailSender.linkBody(link, validMinutes));
    }

    private void send(String toEmail, String subject, String text) {
        SendEmailRequest request = SendEmailRequest.builder()
                .fromEmailAddress(fromAddress)
                .destination(Destination.builder().toAddresses(toEmail).build())
                .content(EmailContent.builder()
                        .simple(Message.builder()
                                .subject(utf8(subject))
                                .body(Body.builder().text(utf8(text)).build())
                                .build())
                        .build())
                .build();
        client().sendEmail(request);
    }

    private static Content utf8(String text) {
        return Content.builder().data(text).charset("UTF-8").build();
    }

    private SesV2Client client() {
        SesV2Client current = client;
        if (current == null) {
            synchronized (this) {
                if (client == null) {
                    var builder = SesV2Client.builder();
                    if (region != null && !region.isBlank()) {
                        builder.region(Region.of(region));
                    }
                    client = builder.build();
                }
                current = client;
            }
        }
        return current;
    }
}
