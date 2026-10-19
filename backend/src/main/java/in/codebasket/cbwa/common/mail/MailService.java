package in.codebasket.cbwa.common.mail;

import in.codebasket.cbwa.common.settings.SettingKeys;
import in.codebasket.cbwa.common.settings.SettingsService;
import in.codebasket.cbwa.config.AppProperties;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Sends HTML emails using the SMTP settings saved by the admin (falls back to CBWA_MAIL_* env).
 * When no SMTP host is configured the email is skipped (and in dev mode the subject is logged).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private final SettingsService settings;
    private final AppProperties props;
    private final org.springframework.core.env.Environment env;

    @Async
    public void sendAsync(String to, String subject, String html) {
        send(to, subject, html);
    }

    /** Synchronous send; returns true when handed to the SMTP server. */
    public boolean send(String to, String subject, String html) {
        if (to == null || to.isBlank()) {
            return false;
        }
        JavaMailSenderImpl sender = buildSender();
        if (sender == null) {
            if (props.isDevMode()) {
                log.info("[DEV] SMTP not configured - email to {} skipped: {}", to, subject);
            } else {
                log.warn("SMTP not configured - email '{}' not sent", subject);
            }
            return false;
        }
        try {
            MimeMessage msg = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, false, StandardCharsets.UTF_8.name());
            String from = settings.getString(SettingKeys.SMTP, "from_email", props.getMail().getFrom());
            String fromName = settings.getString(SettingKeys.SMTP, "from_name", "CB WhatsApp Gateway");
            helper.setFrom(new InternetAddress(from, fromName, StandardCharsets.UTF_8.name()));
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            sender.send(msg);
            return true;
        } catch (Exception e) {
            log.warn("Email '{}' to {} failed: {}", subject, to, e.getMessage());
            return false;
        }
    }

    /** Used by the SMTP "Send test email" button; throws so the admin sees the real error. */
    public void sendTest(String to) throws Exception {
        JavaMailSenderImpl sender = buildSender();
        if (sender == null) {
            throw new IllegalStateException("SMTP host is not configured");
        }
        MimeMessage msg = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(msg, false, StandardCharsets.UTF_8.name());
        String from = settings.getString(SettingKeys.SMTP, "from_email", props.getMail().getFrom());
        helper.setFrom(new InternetAddress(from, settings.getString(SettingKeys.SMTP, "from_name", "CB WhatsApp Gateway"),
                StandardCharsets.UTF_8.name()));
        helper.setTo(to);
        helper.setSubject("Test email from CB WhatsApp Gateway");
        helper.setText(EmailTemplates.simple("SMTP is working", "<p>Your email settings are correct.</p>", null, null), true);
        sender.send(msg);
    }

    private JavaMailSenderImpl buildSender() {
        String host = settings.getString(SettingKeys.SMTP, "host", env.getProperty("spring.mail.host", ""));
        if (host == null || host.isBlank()) {
            return null;
        }
        JavaMailSenderImpl s = new JavaMailSenderImpl();
        s.setHost(host);
        s.setPort(settings.getInt(SettingKeys.SMTP, "port", Integer.parseInt(env.getProperty("spring.mail.port", "587"))));
        String user = settings.getString(SettingKeys.SMTP, "username", env.getProperty("spring.mail.username", ""));
        String pass = settings.getString(SettingKeys.SMTP, "password", env.getProperty("spring.mail.password", ""));
        if (user != null && !user.isBlank()) {
            s.setUsername(user);
            s.setPassword(pass);
        }
        Properties p = s.getJavaMailProperties();
        p.put("mail.smtp.auth", String.valueOf(user != null && !user.isBlank()));
        boolean starttls = settings.getBool(SettingKeys.SMTP, "starttls", true);
        p.put("mail.smtp.starttls.enable", String.valueOf(starttls));
        if (s.getPort() == 465) {
            p.put("mail.smtp.ssl.enable", "true");
        }
        p.put("mail.smtp.connectiontimeout", "10000");
        p.put("mail.smtp.timeout", "15000");
        p.put("mail.smtp.writetimeout", "15000");
        return s;
    }
}
