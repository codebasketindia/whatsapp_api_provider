package in.codebasket.cbwa.common.mail;

import org.springframework.web.util.HtmlUtils;

/** Minimal, mobile-friendly HTML email layout. Dynamic text must be escaped by callers via esc(). */
public final class EmailTemplates {

    private EmailTemplates() {
    }

    public static String esc(String s) {
        return s == null ? "" : HtmlUtils.htmlEscape(s);
    }

    public static String simple(String title, String bodyHtml, String ctaText, String ctaUrl) {
        String cta = ctaText == null || ctaUrl == null ? "" :
                "<p style=\"margin:24px 0\"><a href=\"" + esc(ctaUrl) + "\" style=\"background:#25D366;color:#fff;"
                        + "padding:12px 20px;border-radius:8px;text-decoration:none;font-weight:600\">" + esc(ctaText) + "</a></p>";
        return "<!doctype html><html><body style=\"margin:0;background:#f4f5f7;font-family:Arial,Helvetica,sans-serif\">"
                + "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"><tr><td align=\"center\" style=\"padding:24px 12px\">"
                + "<table width=\"100%\" style=\"max-width:560px;background:#ffffff;border-radius:12px;padding:24px\">"
                + "<tr><td><div style=\"font-size:13px;color:#128C7E;font-weight:700;letter-spacing:.5px\">CB WHATSAPP GATEWAY</div>"
                + "<h2 style=\"color:#111827;margin:12px 0\">" + esc(title) + "</h2>"
                + "<div style=\"color:#374151;font-size:15px;line-height:1.6\">" + bodyHtml + "</div>" + cta
                + "<p style=\"color:#9ca3af;font-size:12px;margin-top:32px\">This is an automated message. Please do not reply.</p>"
                + "</td></tr></table></td></tr></table></body></html>";
    }

    public static String otp(String title, String intro, String otp, int minutes) {
        return simple(title, "<p>" + esc(intro) + "</p><p style=\"font-size:28px;letter-spacing:6px;font-weight:700;color:#111827\">"
                + esc(otp) + "</p><p>This code expires in " + minutes + " minutes. Never share it with anyone.</p>", null, null);
    }
}
