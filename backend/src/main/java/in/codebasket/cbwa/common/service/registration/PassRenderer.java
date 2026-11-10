package in.codebasket.cbwa.common.service.registration;

import in.codebasket.cbwa.common.dto.PassLayout;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * Draws the registrant's name, mobile number and optional photo onto the pass design and returns a
 * JPEG (WhatsApp images must be JPEG/PNG under 5 MB). The design itself is used unchanged, so the pass
 * looks exactly like the artwork the partner uploaded.
 */
@Slf4j
@Component
public class PassRenderer {

    public static final String BUILT_IN_HARMAIN = "harmain";
    private static final Map<String, String> BUILT_IN = Map.of(BUILT_IN_HARMAIN, "pass-designs/harmain.jpg");

    private final Font baseFont;
    private final Font serifFont;

    public PassRenderer() {
        Font f;
        try (InputStream in = new ClassPathResource("fonts/DejaVuSans-Bold.ttf").getInputStream()) {
            f = Font.createFont(Font.TRUETYPE_FONT, in);
        } catch (Exception e) {
            log.warn("Bundled pass font not loaded, using SansSerif: {}", e.getMessage());
            f = new Font(Font.SANS_SERIF, Font.BOLD, 12);
        }
        this.baseFont = f;
        Font serif;
        try (InputStream in = new ClassPathResource("fonts/DejaVuSerif-Bold.ttf").getInputStream()) {
            serif = Font.createFont(Font.TRUETYPE_FONT, in);
        } catch (Exception e) {
            serif = new Font(Font.SERIF, Font.BOLD, 12);
        }
        this.serifFont = serif;
    }

    public static boolean isBuiltIn(String key) {
        return key != null && BUILT_IN.containsKey(key);
    }

    public byte[] builtInDesign(String key) {
        try (InputStream in = new ClassPathResource(BUILT_IN.get(key)).getInputStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Built-in pass design missing: " + key, e);
        }
    }

    /** qrText: content of the QR code (a verification link); null = no QR. */
    public record PassData(String name, String mobile, String passNo, byte[] photo, String qrText) {
        public PassData(String name, String mobile, String passNo, byte[] photo) {
            this(name, mobile, passNo, photo, null);
        }
    }

    /** What a generated (design-less) ticket shows. */
    public record TicketData(String businessName, String eventTitle, String passTitle, String name, String mobile,
                             String bookingNo, String passNo, String qrText, String supportLine) {
        public TicketData(String businessName, String eventTitle, String passTitle, String name, String mobile,
                          String bookingNo, String passNo, String qrText) {
            this(businessName, eventTitle, passTitle, name, mobile, bookingNo, passNo, qrText, null);
        }
    }

    public byte[] render(byte[] design, PassLayout layout, PassData data) {
        BufferedImage src = read(design);
        BufferedImage img = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(src, 0, 0, null);
            Color color = color(layout.textColor());
            int w = img.getWidth();
            int h = img.getHeight();
            boolean qrInPhotoBox = data.qrText() != null && layout.qr() != null && layout.qr().equals(layout.photo());
            if (data.photo() != null && layout.photo() != null && !qrInPhotoBox) {
                drawPhoto(g, data.photo(), layout.photo(), w, h);
            }
            if (data.qrText() != null && layout.qr() != null) {
                PassLayout.Box b = layout.qr();
                int bx = (int) Math.round(b.x() * w), by = (int) Math.round(b.y() * h);
                int bw = (int) Math.round(b.w() * w), bh = (int) Math.round(b.h() * h);
                int side = Math.min(bw, bh);
                g.setColor(Color.WHITE);
                g.fillRect(bx, by, bw, bh);
                g.drawImage(qr(data.qrText(), side), bx + (bw - side) / 2, by + (bh - side) / 2, null);
            }
            text(g, data.name(), layout.name(), w, h, color);
            text(g, data.mobile(), layout.mobile(), w, h, color);
            if (layout.passNo() != null) {
                text(g, data.passNo(), layout.passNo(), w, h, color);
            }
        } finally {
            g.dispose();
        }
        return jpeg(img);
    }

    /** Text left-aligned in the box, vertically centred, shrunk until it fits the width. */
    private void text(Graphics2D g, String value, PassLayout.Box box, int w, int h, Color color) {
        if (value == null || value.isBlank() || box == null) {
            return;
        }
        int bx = (int) Math.round(box.x() * w);
        int by = (int) Math.round(box.y() * h);
        int bw = (int) Math.round(box.w() * w);
        int bh = (int) Math.round(box.h() * h);
        int pad = Math.max(6, (int) (bh * 0.35));
        float size = bh * 0.56f;
        Font font = fontFor(value).deriveFont(size);
        FontMetrics fm = g.getFontMetrics(font);
        while (fm.stringWidth(value) > bw - 2 * pad && size > 10) {
            size -= 1f;
            font = font.deriveFont(size);
            fm = g.getFontMetrics(font);
        }
        g.setFont(font);
        g.setColor(color);
        int baseline = by + (bh - fm.getHeight()) / 2 + fm.getAscent();
        g.drawString(value, bx + pad, baseline);
    }

    /** Bundled font for Latin text; a system font for scripts it cannot draw (e.g. Hindi names). */
    private Font fontFor(String value) {
        if (baseFont.canDisplayUpTo(value) == -1) {
            return baseFont;
        }
        Font sys = new Font(Font.SANS_SERIF, Font.BOLD, 12);
        return sys.canDisplayUpTo(value) == -1 ? sys : baseFont;
    }

    /** Photo cropped to fill the frame (centre crop), with slightly rounded corners. */
    private void drawPhoto(Graphics2D g, byte[] photo, PassLayout.Box box, int w, int h) {
        BufferedImage p;
        try {
            p = read(photo);
        } catch (RuntimeException e) {
            log.info("Pass photo could not be read: {}", e.getMessage());
            return;
        }
        int bx = (int) Math.round(box.x() * w);
        int by = (int) Math.round(box.y() * h);
        int bw = (int) Math.round(box.w() * w);
        int bh = (int) Math.round(box.h() * h);
        double scale = Math.max((double) bw / p.getWidth(), (double) bh / p.getHeight());
        int sw = (int) Math.round(bw / scale);
        int sh = (int) Math.round(bh / scale);
        int sx = (p.getWidth() - sw) / 2;
        int sy = Math.max(0, (p.getHeight() - sh) / 3); // keep faces (upper part) in view
        var oldClip = g.getClip();
        g.setClip(new RoundRectangle2D.Double(bx, by, bw, bh, 8, 8));
        g.drawImage(p, bx, by, bx + bw, by + bh, sx, sy, sx + sw, sy + sh, null);
        g.setClip(oldClip);
    }

    private static Color color(String hex) {
        try {
            return hex == null || hex.isBlank() ? new Color(0x0B5A2E) : Color.decode(hex);
        } catch (NumberFormatException e) {
            return new Color(0x0B5A2E);
        }
    }

    static BufferedImage read(byte[] bytes) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
            if (img == null) {
                throw new IllegalArgumentException("Not a JPG or PNG image");
            }
            return img;
        } catch (IOException | RuntimeException e) {
            throw e instanceof IllegalArgumentException iae ? iae : new IllegalArgumentException("Image could not be read", e);
        }
    }

    /** A clean ticket for packages without their own design: header, details, QR. 1600 x 900 px. */
    public byte[] renderTicket(TicketData t) {
        int w = 1600, h = 900;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Color navy = new Color(0x1E2A44), gold = new Color(0xC9A24B), ivory = new Color(0xFBF8F1), ink = new Color(0x1F2937);
            g.setColor(ivory);
            g.fillRect(0, 0, w, h);
            g.setColor(navy);
            g.fillRect(0, 0, w, 210);
            g.setColor(gold);
            g.fillRect(0, 210, w, 8);
            g.setColor(new Color(0xE9DFC6));
            g.fillRoundRect(40, 250, w - 80, h - 290, 36, 36);
            g.setColor(Color.WHITE);
            g.fillRoundRect(44, 254, w - 88, h - 298, 32, 32);
            g.setColor(gold);
            g.setFont(fontFor(nz(t.businessName())).deriveFont(30f));
            g.drawString(nz(t.businessName()).toUpperCase(), 70, 70);
            g.setColor(Color.WHITE);
            fit(g, nz(t.passTitle()), 70, 150, 1020, 64f);
            g.setColor(new Color(0xD7DCE6));
            g.setFont(fontFor(nz(t.eventTitle())).deriveFont(28f));
            g.drawString(nz(t.eventTitle()), 70, 192);
            int y = 330;
            for (String[] row : new String[][] {{"Name", t.name()}, {"Mobile", t.mobile()}, {"Booking no.", t.bookingNo()}, {"Pass ID", t.passNo()}}) {
                g.setColor(new Color(0x6B7280));
                g.setFont(fontFor(row[0]).deriveFont(26f));
                g.drawString(row[0], 90, y);
                g.setColor(ink);
                fit(g, nz(row[1]), 90, y + 48, 900, 42f);
                y += 125;
            }
            int side = 470;
            g.drawImage(qr(t.qrText(), side), w - side - 90, 300, null);
            g.setColor(new Color(0x6B7280));
            g.setFont(baseFont.deriveFont(24f));
            String hint = "Show this QR code at the entry. Valid once.";
            g.drawString(hint, w - side - 90 + (side - g.getFontMetrics().stringWidth(hint)) / 2, 300 + side + 45);
        } finally {
            g.dispose();
        }
        return jpeg(img);
    }

    /**
     * Green / gold portrait Ziyarat card (900 x 1600 artwork, drawn at 2x): pass title in the arch, link title in the
     * gold ribbon, the four details on the left and the QR code (with the Kaaba mark in the middle) on the right.
     */
    public byte[] renderZiyaratCard(TicketData t) {
        int s = 2;
        BufferedImage bg = image(resource("pass-designs/ziyarat-card.jpg"));
        BufferedImage img = new BufferedImage(bg.getWidth() * s, bg.getHeight() * s, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g.drawImage(bg, 0, 0, img.getWidth(), img.getHeight(), null);
            g.scale(s, s);

            // pass title: up to two centred lines in the green arch (y 552-690)
            String title = nz(t.passTitle());
            Font tf = serifFont;
            java.util.List<String> lines = java.util.List.of(title);
            for (float size = 56; size >= 28; size -= 2) {
                tf = (canShow(serifFont, title) ? serifFont : fontFor(title)).deriveFont(size);
                lines = wrap(g, title, tf, 660);
                if (lines.size() <= 2) {
                    break;
                }
            }
            var fm = g.getFontMetrics(tf);
            float lh = tf.getSize2D() * 1.12f;
            float top = 621 - lh * lines.size() / 2f + fm.getAscent() - (fm.getAscent() - fm.getDescent()) * 0.08f;
            g.setFont(tf);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                float x = 450 - fm.stringWidth(line) / 2f, y = top + i * lh;
                g.setColor(new Color(0, 0, 0, 110));
                g.drawString(line, x + 2, y + 3);
                g.setColor(new Color(0xFBF1D6));
                g.drawString(line, x, y);
            }

            // link title in the gold ribbon
            g.setColor(new Color(0x0B3D23));
            centre(g, nz(t.eventTitle()), 450, 738, 470, 30f);

            // details
            g.setColor(new Color(0x14213D));
            int[] baselines = {884, 987, 1089, 1190};
            String[] values = {t.name(), t.mobile(), t.bookingNo(), t.passNo()};
            for (int i = 0; i < 4; i++) {
                fit(g, nz(values[i]), 152, baselines[i], 330, 31f);
            }

            // QR with the Kaaba mark
            int side = 262, qx = 560, qy = 843;
            g.drawImage(qr(t.qrText(), side * s, com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.H), qx, qy, side, side, null);
            BufferedImage logo = image(resource("pass-designs/ziyarat-qr-logo.png"));
            int ls = 62, lx = qx + (side - ls) / 2, ly = qy + (side - ls) / 2;
            g.setColor(Color.WHITE);
            g.fillOval(lx - 4, ly - 4, ls + 8, ls + 8);
            g.drawImage(logo, lx, ly, ls, ls, null);

            // small support line at the very bottom, under the HAJJ 2027 seal
            if (t.supportLine() != null && !t.supportLine().isBlank()) {
                g.setColor(new Color(0, 0, 0, 90));
                centre(g, t.supportLine().trim(), 451, 1575, 760, 20f);
                g.setColor(new Color(0xF3DC9A));
                centre(g, t.supportLine().trim(), 450, 1574, 760, 20f);
            }
        } finally {
            g.dispose();
        }
        return jpeg(img);
    }

    private static byte[] resource(String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Pass artwork missing: " + path, e);
        }
    }

    private static BufferedImage image(byte[] bytes) {
        try {
            return javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean canShow(Font f, String text) {
        return f.canDisplayUpTo(text) == -1;
    }

    private static java.util.List<String> wrap(Graphics2D g, String text, Font f, int maxW) {
        var fm = g.getFontMetrics(f);
        java.util.List<String> out = new java.util.ArrayList<>();
        String line = "";
        for (String word : text.trim().split("\\s+")) {
            String next = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && fm.stringWidth(next) > maxW) {
                out.add(line);
                line = word;
            } else {
                line = next;
            }
        }
        if (!line.isEmpty()) {
            out.add(line);
        }
        return out;
    }

    private void centre(Graphics2D g, String text, int cx, int y, int maxW, float size) {
        Font f = fontFor(text).deriveFont(size);
        while (g.getFontMetrics(f).stringWidth(text) > maxW && size > 14) {
            size -= 1;
            f = f.deriveFont(size);
        }
        g.setFont(f);
        g.drawString(text, cx - g.getFontMetrics(f).stringWidth(text) / 2f, y);
    }

    private void fit(Graphics2D g, String text, int x, int y, int maxW, float size) {
        Font f = fontFor(text).deriveFont(size);
        while (g.getFontMetrics(f).stringWidth(text) > maxW && size > 14) {
            size -= 2;
            f = f.deriveFont(size);
        }
        g.setFont(f);
        g.drawString(text, x, y);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    /** QR code with high error correction, black on white. */
    static BufferedImage qr(String text, int size) {
        return qr(text, size, com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.Q);
    }

    static BufferedImage qr(String text, int size, com.google.zxing.qrcode.decoder.ErrorCorrectionLevel level) {
        try {
            var hints = new java.util.EnumMap<com.google.zxing.EncodeHintType, Object>(com.google.zxing.EncodeHintType.class);
            hints.put(com.google.zxing.EncodeHintType.ERROR_CORRECTION, level);
            hints.put(com.google.zxing.EncodeHintType.MARGIN, 1);
            var matrix = new com.google.zxing.qrcode.QRCodeWriter().encode(text, com.google.zxing.BarcodeFormat.QR_CODE, size, size, hints);
            return com.google.zxing.client.j2se.MatrixToImageWriter.toBufferedImage(matrix);
        } catch (com.google.zxing.WriterException e) {
            throw new IllegalStateException("QR code could not be created", e);
        }
    }

    private static byte[] jpeg(BufferedImage img) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            ImageWriteParam p = writer.getDefaultWriteParam();
            p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            p.setCompressionQuality(0.92f);
            writer.write(null, new IIOImage(img, null, null), p);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }
}
