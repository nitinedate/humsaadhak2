package org.humsaadhak.notifications.service;

import org.humsaadhak.notifications.client.MailGateway;

import java.util.List;
import java.util.Map;
import org.humsaadhak.documents.api.ShareEvents.ShareDecided;
import org.humsaadhak.documents.api.ShareEvents.ShareRequested;
import org.humsaadhak.giving.api.InstalmentDue;
import org.humsaadhak.identity.api.InvitationLinks;
import org.humsaadhak.identity.api.PasswordResetRequested;
import org.humsaadhak.identity.api.UserActivated;
import org.humsaadhak.identity.api.UserDirectory;
import org.humsaadhak.matching.api.MatchesConfirmed;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/** Every email the platform sends. Runs after commit; Spring Modulith retries failures. */
@Component
public class NotificationListeners {
    private final MailGateway mail;
    private final TemplateEngine templates;
    private final InvitationLinks links;
    private final UserDirectory directory;

    public NotificationListeners(MailGateway mail, TemplateEngine templates, InvitationLinks links, UserDirectory directory) {
        this.mail = mail; this.templates = templates; this.links = links; this.directory = directory;
    }

    @ApplicationModuleListener
    public void on(UserActivated e) {
        send(List.of(e.email()), "Welcome to HumSaadhak — set your password", "mail/invitation",
                Map.of("name", e.fullName(), "link", links.issueInvitationLink(e.userId())));
    }

    @ApplicationModuleListener
    public void on(PasswordResetRequested e) {
        send(List.of(e.email()), "Reset your HumSaadhak password", "mail/password-reset",
                Map.of("name", e.fullName(), "link", links.issuePasswordResetLink(e.userId())));
    }

    @ApplicationModuleListener
    public void on(MatchesConfirmed e) {
        toOrg(e.donorOrgId(), "Your matches are ready (" + e.batchRefNo() + ")", "mail/matches",
                Map.of("count", e.matchIdsInOrder().size(), "batch", e.batchRefNo()));
    }

    @ApplicationModuleListener
    public void on(ShareRequested e) {
        toOrg(e.ownerOrgId(), "A donor asked to see your documents", "mail/share-request", Map.of("needId", e.needId()));
    }

    @ApplicationModuleListener
    public void on(ShareDecided e) {
        toOrg(e.donorOrgId(), e.approved() ? "Documents shared with you" : "Your document request was declined",
                "mail/share-decided", Map.of("approved", e.approved(), "needId", e.needId()));
    }

    @ApplicationModuleListener
    public void on(InstalmentDue e) {
        toOrg(e.donorOrgId(), "Your next instalment is due on " + e.dueDate(), "mail/instalment-due",
                Map.of("amount", e.amount(), "due", e.dueDate()));
    }

    private void toOrg(long orgId, String subject, String template, Map<String, Object> vars) {
        List<String> to = directory.activeUsersOfOrg(orgId).stream().map(UserDirectory.Recipient::email).toList();
        if (!to.isEmpty()) send(to, subject, template, vars);
    }

    private void send(List<String> to, String subject, String template, Map<String, Object> vars) {
        Context ctx = new Context();
        ctx.setVariables(vars);
        mail.send(new MailGateway.Mail(to, subject, templates.process(template, ctx)));
    }
}
