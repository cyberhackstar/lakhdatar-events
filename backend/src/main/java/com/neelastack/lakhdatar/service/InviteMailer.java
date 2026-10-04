package com.neelastack.lakhdatar.service;

import java.time.Instant;

/** Sends team invitations. Disabled (and the password flow is used instead) when SMTP is not configured. */
public interface InviteMailer {
    boolean isEnabled();
    String inviteLink(String rawToken);
    void sendInvite(String toEmail, String name, String organizerName, String link, Instant expiresAt);
}
