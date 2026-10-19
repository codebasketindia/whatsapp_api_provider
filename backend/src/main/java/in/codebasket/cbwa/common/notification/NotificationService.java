package in.codebasket.cbwa.common.notification;

import in.codebasket.cbwa.common.mail.EmailTemplates;
import in.codebasket.cbwa.common.mail.MailService;
import in.codebasket.cbwa.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;

/**
 * In-app notifications (bell) + optional email. Emails are sent only after the surrounding
 * transaction commits so users are never told about something that was rolled back.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    public static final String INFO = "INFO";
    public static final String SUCCESS = "SUCCESS";
    public static final String WARNING = "WARNING";
    public static final String ERROR = "ERROR";

    private final NotificationRepository repository;
    private final MailService mailService;
    private final AppProperties props;

    @Transactional
    public void notifyPartner(Long partnerId, String type, String severity, String title, String body, String link,
                              String emailTo) {
        Notification n = base("PARTNER", type, severity, title, body, link);
        n.setPartnerId(partnerId);
        repository.save(n);
        email(emailTo, title, body, link);
    }

    @Transactional
    public void notifyAdmins(String type, String severity, String title, String body, String link) {
        Notification n = base("ADMIN", type, severity, title, body, link);
        repository.save(n);
    }

    @Transactional
    public void notifyAdminUser(Long userId, String type, String severity, String title, String body, String link) {
        Notification n = base("ADMIN", type, severity, title, body, link);
        n.setUserId(userId);
        repository.save(n);
    }

    /** Email only (no in-app row), sent after commit. */
    public void email(String to, String title, String body, String link) {
        if (to == null || to.isBlank()) {
            return;
        }
        String url = link == null ? null : props.getFrontendUrl() + link;
        String html = EmailTemplates.simple(title, "<p>" + EmailTemplates.esc(body) + "</p>", url == null ? null : "Open dashboard", url);
        afterCommit(() -> mailService.sendAsync(to, title, html));
    }

    public static void afterCommit(Runnable r) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    r.run();
                }
            });
        } else {
            r.run();
        }
    }

    private Notification base(String audience, String type, String severity, String title, String body, String link) {
        Notification n = new Notification();
        n.setAudience(audience);
        n.setType(type);
        n.setSeverity(severity);
        n.setTitle(title.length() > 150 ? title.substring(0, 150) : title);
        n.setBody(body != null && body.length() > 1000 ? body.substring(0, 1000) : body);
        n.setLink(link);
        n.setCreatedAt(Instant.now());
        return n;
    }
}
