package com.neelastack.lakhdatar.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * SMTP implementation. Active only when a JavaMailSender exists (spring.mail.host set) AND
 * MAIL_FROM and PUBLIC_BASE_URL are provided. The link and token are never logged.
 */
@Component
public class SmtpInviteMailer implements InviteMailer {
    private final ObjectProvider<JavaMailSender> sender;
    private final String baseUrl;
    private final String from;

    public SmtpInviteMailer(ObjectProvider<JavaMailSender> sender,
                            @Value("${app.public-base-url:}") String baseUrl,
                            @Value("${app.mail.from:}") String from) {
        this.sender = sender;
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
        this.from = from == null ? "" : from.trim();
    }

    @Override public boolean isEnabled() {
        return sender.getIfAvailable() != null && !baseUrl.isBlank() && !from.isBlank();
    }

    @Override public String inviteLink(String rawToken) {
        return baseUrl + "/accept-invite?token=" + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
    }

    @Override public void sendInvite(String toEmail, String name, String organizerName, String link, Instant expiresAt) {
        JavaMailSender s = sender.getIfAvailable();
        if (s == null) throw new IllegalStateException("Mail is not configured");
        SimpleMailMessage m = new SimpleMailMessage();
        m.setFrom(from);
        m.setTo(toEmail);
        m.setSubject("You have been invited to " + organizerName + " on Neelastack Events");
        m.setText("Hi " + name + ",\n\n"
                + organizerName + " added you to their event team. Set your password to get started:\n\n"
                + link + "\n\n"
                + "This link works once and expires at " + expiresAt + ". If you were not expecting this, ignore this email.\n");
        s.send(m);
    }
}
