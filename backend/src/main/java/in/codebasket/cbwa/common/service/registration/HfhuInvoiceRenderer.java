package in.codebasket.cbwa.common.service.registration;

import in.codebasket.cbwa.common.entity.Partner;
import in.codebasket.cbwa.common.entity.Registration;
import in.codebasket.cbwa.common.entity.RegistrationForm;
import in.codebasket.cbwa.common.entity.RegistrationItem;
import org.springframework.core.io.ClassPathResource;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The HFHU invoice (forms with invoice style "HFHU"): green lattice frame, ivory panel, the HFHU banner, seller and
 * invoice boxes, "BILLED TO", a green-headed package table, totals, an "AMOUNT PAID" bar, payment box and footer.
 * Drawn as one 1240 x 1860 page image; {@link BookingDocuments} wraps it in a PDF.
 */
final class HfhuInvoiceRenderer {

    static final int W = 1240, H = 2100;
    private static final Color GREEN = new Color(0x0B4D2C), GREEN_D = new Color(0x0A3F24), GOLD = new Color(0xCFA43F),
            GOLD_L = new Color(0xE9CF85), IVORY = new Color(0xFCF7EA), INK = new Color(0x1B2420), GREY = new Color(0x4B5563),
            RED = new Color(0x9B1C1C);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.ENGLISH)
            .withZone(ZoneId.of("Asia/Kolkata"));

    private final Font serif, serifBold;

    HfhuInvoiceRenderer() {
        serif = font("fonts/DejaVuSerif.ttf", Font.SERIF, Font.PLAIN);
        serifBold = font("fonts/DejaVuSerif-Bold.ttf", Font.SERIF, Font.BOLD);
    }

    private static Font font(String path, String fallback, int style) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return Font.createFont(Font.TRUETYPE_FONT, in);
        } catch (Exception e) {
            return new Font(fallback, style, 12);
        }
    }

    private String rs(BigDecimal v) {
        NumberFormat nf = NumberFormat.getNumberInstance(new Locale("en", "IN"));
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);
        String sym = serif.canDisplay('₹') ? "₹" : "Rs.";
        return (v.signum() < 0 ? "−" : "") + sym + nf.format(v.abs());
    }

    BufferedImage render(Partner partner, RegistrationForm form, Registration r, List<RegistrationItem> items) {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            frame(g);
            int y = banner(g);
            String seller = form.getInvoiceName() != null ? form.getInvoiceName() : partner.getBusinessName();
            String contact = form.getInvoiceContact() != null ? form.getInvoiceContact()
                    : partner.getMobile() == null ? "" : "Phone: +" + partner.getMobile();

            // seller | invoice box
            int bx = 50, bw = W - 100;
            y += 18;
            box(g, bx, y, bw, 190, 26);
            int split = bx + (int) (bw * 0.6);
            g.setColor(GOLD_L);
            g.fillRect(split, y + 30, 2, 130);
            g.setColor(GREEN_D);
            fitCentre(g, serifBold, seller.toUpperCase(Locale.ROOT), bx + (split - bx) / 2, y + 78, split - bx - 50, 40f);
            ornament(g, bx + 70, split - 70, y + 108);
            g.setColor(INK);
            fitCentre(g, serif, contact, bx + (split - bx) / 2, y + 155, split - bx - 40, 28f);
            g.setColor(new Color(0xB8892C));
            fitCentre(g, serifBold, "INVOICE", split + (bx + bw - split) / 2, y + 60, bx + bw - split - 30, 44f);
            g.setColor(INK);
            int ry = y + 102;
            for (String line : new String[] {"Invoice No. " + r.getInvoiceNo(), "Booking No. " + r.getPassNo(),
                    "Date: " + DATE.format(r.getPaidAt() != null ? r.getPaidAt() : r.getCreatedAt())}) {
                fitLeft(g, serif, line, split + 34, ry, bx + bw - split - 50, 24f);
                ry += 34;
            }
            y += 190 + 26;

            // billed to
            box(g, bx, y, bw, 175, 26);
            tag(g, bx + 30, y - 6, "BILLED TO");
            g.setColor(INK);
            fitLeft(g, serifBold, r.getName(), bx + 55, y + 82, bw - 110, 40f);
            fitLeft(g, serifBold, RegistrationService.formatMobile(r.getPhone()), bx + 55, y + 122, bw - 110, 28f);
            fitLeft(g, serif, form.getTitle(), bx + 55, y + 158, bw - 110, 27f);
            y += 175 + 26;

            y = table(g, bx, y, bw, r, items);

            // totals
            BigDecimal mrp = BigDecimal.ZERO, total = BigDecimal.ZERO;
            for (RegistrationItem it : items) {
                mrp = mrp.add(it.getMrp());
                total = total.add(it.getPrice());
            }
            BigDecimal discount = r.getDiscountAmount() == null ? BigDecimal.ZERO : r.getDiscountAmount();
            total = total.subtract(discount);
            int tx = bx + (int) (bw * 0.52);
            y += 46;
            for (String[] row : new String[][] {{"Total MRP", rs(mrp)}, {"You Saved", rs(mrp.subtract(total))}}) {
                g.setColor(INK);
                fitLeft(g, serifBold, row[0], tx + 20, y, 260, 30f);
                rightText(g, serifBold.deriveFont(30f), row[1], bx + bw - 20, y);
                g.setColor(GOLD_L);
                g.fillRect(tx, y + 14, bx + bw - tx, 2);
                y += 54;
            }
            // amount paid bar
            y += 6;
            greenBar(g, bx, y, bw, 96);
            g.setColor(Color.WHITE);
            fitLeft(g, serifBold, "AMOUNT PAID", bx + 55, y + 64, 520, 48f);
            rightText(g, serifBold.deriveFont(52f), rs(total), bx + bw - 45, y + 66);
            y += 96 + 30;

            // payment box
            box(g, bx, y, bw, 130, 24);
            cardIcon(g, bx + 110, y + 65);
            g.setColor(GOLD_L);
            g.fillRect(bx + 265, y + 25, 2, 80);
            g.setColor(INK);
            String method = r.getRazorpayPaymentId() == null ? "Free registration" : "Online (Razorpay)";
            labelValue(g, bx + 300, y + 55, "Payment: ", method, bw - 330);
            if (r.getRazorpayPaymentId() != null) {
                labelValue(g, bx + 300, y + 100, "Payment ID: ", r.getRazorpayPaymentId(), bw - 330);
            }
            y += 130 + 40;
            g.setColor(GREY);
            fitLeft(g, serif, "No GST or other tax is charged on this invoice.", bx + 30, y, bw - 60, 23f);
            fitLeft(g, serif, "This is a computer-generated invoice and does not need a signature.", bx + 30, y + 34, bw - 60, 23f);

            footer(g, form, seller, Math.max(y + 70, H - 230));
        } finally {
            g.dispose();
        }
        return img;
    }

    // ------------------------------------------------------------------ parts

    private void frame(Graphics2D g) {
        g.setColor(new Color(0x0C5B31));
        g.fillRect(0, 0, W, H);
        g.setColor(new Color(0xD9B347));
        g.setStroke(new BasicStroke(2.4f));
        for (int x = 0; x < W; x += 44) {
            for (int y = 0; y < H; y += 44) {
                Path2D star = new Path2D.Double();
                star.moveTo(x + 22, y + 3);
                star.lineTo(x + 28, y + 16);
                star.lineTo(x + 41, y + 22);
                star.lineTo(x + 28, y + 28);
                star.lineTo(x + 22, y + 41);
                star.lineTo(x + 16, y + 28);
                star.lineTo(x + 3, y + 22);
                star.lineTo(x + 16, y + 16);
                star.closePath();
                g.draw(star);
            }
        }
        RoundRectangle2D panel = new RoundRectangle2D.Double(20, 20, W - 40, H - 40, 60, 60);
        g.setPaint(new GradientPaint(0, 0, new Color(0xFDF9EF), 0, H, new Color(0xF7EEDA)));
        g.fill(panel);
        g.setColor(GOLD);
        g.setStroke(new BasicStroke(6f));
        g.draw(panel);
        g.setColor(GOLD_L);
        g.setStroke(new BasicStroke(2f));
        g.draw(new RoundRectangle2D.Double(30, 30, W - 60, H - 60, 50, 50));
    }

    /** The HFHU banner across the top of the panel; returns the y below it. */
    private int banner(Graphics2D g) {
        try (InputStream in = new ClassPathResource("pass-designs/invoice-banner.jpg").getInputStream()) {
            BufferedImage b = javax.imageio.ImageIO.read(in);
            int w = W - 60, h = (int) Math.round(b.getHeight() * (double) w / b.getWidth());
            java.awt.Shape old = g.getClip();
            g.setClip(new RoundRectangle2D.Double(30, 30, W - 60, H - 60, 50, 50));
            g.drawImage(b, 30, 30, w, h, null);
            g.setClip(old);
            return 30 + h;
        } catch (IOException e) {
            return 60;
        }
    }

    private void box(Graphics2D g, int x, int y, int w, int h, int r) {
        RoundRectangle2D rr = new RoundRectangle2D.Double(x, y, w, h, r * 2, r * 2);
        g.setPaint(new GradientPaint(0, y, new Color(0xFFFCF4), 0, y + h, new Color(0xFBF2DE)));
        g.fill(rr);
        g.setColor(GOLD_L);
        g.setStroke(new BasicStroke(3f));
        g.draw(rr);
    }

    private void greenBar(Graphics2D g, int x, int y, int w, int h) {
        RoundRectangle2D rr = new RoundRectangle2D.Double(x, y, w, h, 46, 46);
        g.setPaint(new GradientPaint(0, y, new Color(0x147545), 0, y + h, GREEN_D));
        g.fill(rr);
        g.setColor(GOLD);
        g.setStroke(new BasicStroke(5f));
        g.draw(rr);
    }

    private void tag(Graphics2D g, int x, int y, String text) {
        Font f = serifBold.deriveFont(27f);
        int w = g.getFontMetrics(f).stringWidth(text) + 60;
        Path2D p = new Path2D.Double();
        p.moveTo(x, y);
        p.lineTo(x + w, y);
        p.lineTo(x + w - 18, y + 22);
        p.lineTo(x + w, y + 44);
        p.lineTo(x, y + 44);
        p.closePath();
        g.setColor(GREEN);
        g.fill(p);
        g.setColor(GOLD);
        g.setStroke(new BasicStroke(3f));
        g.draw(p);
        g.setColor(new Color(0xFBEFD0));
        g.setFont(f);
        g.drawString(text, x + 24, y + 32);
    }

    private void ornament(Graphics2D g, int x0, int x1, int y) {
        int mid = (x0 + x1) / 2;
        g.setColor(GOLD);
        g.fillRect(x0, y - 1, mid - x0 - 26, 3);
        g.fillRect(mid + 26, y - 1, x1 - mid - 26, 3);
        for (int dx : new int[] {-12, 12}) {
            Path2D d = new Path2D.Double();
            d.moveTo(mid + dx, y - 9);
            d.lineTo(mid + dx + 9, y);
            d.lineTo(mid + dx, y + 9);
            d.lineTo(mid + dx - 9, y);
            d.closePath();
            g.setStroke(new BasicStroke(2.5f));
            g.draw(d);
        }
    }

    /** Package table; returns the y of its bottom edge. */
    private int table(Graphics2D g, int x, int y, int w, Registration r, List<RegistrationItem> items) {
        int[] cols = {70, (int) (w * 0.43), (int) (w * 0.145), (int) (w * 0.15)};
        int last = w - cols[0] - cols[1] - cols[2] - cols[3];
        int[] cw = {cols[0], cols[1], cols[2], cols[3], last};
        String[] heads = {"#", "Package", "MRP", "Discount", "Amount"};
        List<String[]> rows = new ArrayList<>();
        int i = 1;
        for (RegistrationItem it : items) {
            BigDecimal disc = it.getMrp().subtract(it.getPrice());
            rows.add(new String[] {String.valueOf(i++), it.getName(), it.getPassNo() == null ? "" : it.getPassNo(), rs(it.getMrp()),
                    disc.signum() > 0 ? rs(disc.negate()) : "—", rs(it.getPrice())});
        }
        BigDecimal discount = r.getDiscountAmount() == null ? BigDecimal.ZERO : r.getDiscountAmount();
        if (discount.signum() > 0) {
            String label = r.getDiscountLabel() == null ? "" : r.getDiscountLabel();
            String sub = label.contains("(") && label.endsWith(")") ? label.substring(label.indexOf('(') + 1, label.length() - 1) : "";
            rows.add(new String[] {String.valueOf(i), "Combo Discount", sub, "—", rs(discount.negate()), rs(discount.negate())});
        }
        int headH = 64;
        int rowH = rows.size() > 7 ? 60 : 72;
        int h = headH + rowH * rows.size();
        RoundRectangle2D outline = new RoundRectangle2D.Double(x, y, w, h, 30, 30);
        g.setColor(new Color(0xFFFCF4));
        g.fill(outline);
        java.awt.Shape old = g.getClip();
        g.setClip(outline);
        g.setPaint(new GradientPaint(0, y, new Color(0x147545), 0, y + headH, GREEN_D));
        g.fillRect(x, y, w, headH);
        g.setClip(old);
        // header text
        int cx = x;
        for (int c = 0; c < 5; c++) {
            g.setColor(new Color(0xFBEFD0));
            Font f = serifBold.deriveFont(30f);
            if (c == 1) {
                g.setFont(f);
                g.drawString(heads[c], cx + 24, y + 43);
            } else {
                centreText(g, f, heads[c], cx + cw[c] / 2, y + 43);
            }
            cx += cw[c];
        }
        // rows
        int ry = y + headH;
        for (String[] row : rows) {
            g.setColor(GOLD_L);
            g.fillRect(x, ry, w, 2);
            cx = x;
            g.setColor(INK);
            centreText(g, serif.deriveFont(28f), row[0], cx + cw[0] / 2, ry + rowH / 2 + 10);
            cx += cw[0];
            boolean sub = !row[2].isEmpty();
            fitLeft(g, serifBold, row[1], cx + 22, ry + (sub ? rowH / 2 - 4 : rowH / 2 + 10), cw[1] - 34, 26f);
            if (sub) {
                g.setColor(GREY);
                fitLeft(g, serif, row[2], cx + 22, ry + rowH / 2 + 26, cw[1] - 34, 22f);
                g.setColor(INK);
            }
            cx += cw[1];
            rightText(g, serif.deriveFont(25f), row[3], cx + cw[2] - 16, ry + rowH / 2 + 10);
            cx += cw[2];
            if ("—".equals(row[4])) {
                centreText(g, serif.deriveFont(25f), row[4], cx + cw[3] / 2, ry + rowH / 2 + 10);
            } else {
                rightText(g, serif.deriveFont(25f), row[4], cx + cw[3] - 16, ry + rowH / 2 + 10);
            }
            cx += cw[3];
            rightText(g, serifBold.deriveFont(26f), row[5], cx + cw[4] - 18, ry + rowH / 2 + 10);
            ry += rowH;
        }
        // column lines + outline
        g.setColor(GOLD_L);
        cx = x;
        for (int c = 0; c < 4; c++) {
            cx += cw[c];
            g.fillRect(cx, y + headH, 2, h - headH);
        }
        g.setColor(GOLD);
        g.setStroke(new BasicStroke(4f));
        g.draw(outline);
        return y + h;
    }

    private void cardIcon(Graphics2D g, int cx, int cy) {
        g.setColor(GREEN);
        g.setStroke(new BasicStroke(5f));
        g.draw(new RoundRectangle2D.Double(cx - 62, cy - 38, 110, 72, 14, 14));
        g.fillRect(cx - 62, cy - 22, 110, 12);
        g.fillRect(cx - 50, cy + 12, 20, 6);
        g.fillRect(cx - 24, cy + 12, 16, 6);
        g.fillOval(cx + 18, cy + 8, 46, 46);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D tick = new Path2D.Double();
        tick.moveTo(cx + 30, cy + 31);
        tick.lineTo(cx + 39, cy + 40);
        tick.lineTo(cx + 54, cy + 22);
        g.draw(tick);
    }

    private void labelValue(Graphics2D g, int x, int y, String label, String value, int maxW) {
        Font b = serifBold.deriveFont(30f), n = serif.deriveFont(30f);
        g.setFont(b);
        g.drawString(label, x, y);
        int lw = g.getFontMetrics(b).stringWidth(label);
        fitLeft(g, serif, value, x + lw, y, maxW - lw, 30f);
    }

    private void footer(Graphics2D g, RegistrationForm form, String seller, int y) {
        g.setColor(GOLD_L);
        g.fillRect(70, y, W - 140, 2);
        g.setColor(GREEN_D);
        String name = seller.toUpperCase(Locale.ROOT).replace("PRIVATE LIMITED", "PVT LTD");
        Font f = serifBold.deriveFont(46f);
        int nw = g.getFontMetrics(f).stringWidth(name);
        centreText(g, f, name, W / 2, y + 72);
        ornament(g, W / 2 - nw / 2 - 160, W / 2 - nw / 2 - 20, y + 56);
        ornament(g, W / 2 + nw / 2 + 20, W / 2 + nw / 2 + 160, y + 56);
        if (form.getPassSupportLine() != null) {
            g.setColor(INK);
            centreText(g, serifBold.deriveFont(32f), form.getPassSupportLine().replaceAll("\\s*:\\s*", ": "), W / 2, y + 126);
        }
        g.setColor(INK);
        g.setFont(serif.deriveFont(Font.ITALIC, 24f));
        g.drawString("T&C Apply", 70, y + 140);
    }

    // ------------------------------------------------------------------ text helpers

    private void fitLeft(Graphics2D g, Font base, String text, int x, int y, int maxW, float size) {
        Font f = base.deriveFont(size);
        while (g.getFontMetrics(f).stringWidth(text) > maxW && f.getSize2D() > 12) {
            f = f.deriveFont(f.getSize2D() - 1);
        }
        g.setFont(f);
        g.drawString(text, x, y);
    }

    private void fitCentre(Graphics2D g, Font base, String text, int cx, int y, int maxW, float size) {
        Font f = base.deriveFont(size);
        while (g.getFontMetrics(f).stringWidth(text) > maxW && f.getSize2D() > 12) {
            f = f.deriveFont(f.getSize2D() - 1);
        }
        centreText(g, f, text, cx, y);
    }

    private static void centreText(Graphics2D g, Font f, String text, int cx, int y) {
        g.setFont(f);
        g.drawString(text, cx - g.getFontMetrics(f).stringWidth(text) / 2f, y);
    }

    private static void rightText(Graphics2D g, Font f, String text, int right, int y) {
        g.setFont(f);
        g.drawString(text, right - g.getFontMetrics(f).stringWidth(text), y);
    }
}
