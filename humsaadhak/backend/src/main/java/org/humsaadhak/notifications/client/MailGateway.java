package org.humsaadhak.notifications.client;

import java.util.List;

public interface MailGateway {
    record Mail(List<String> to, String subject, String html) {}
    void send(Mail mail);
}
