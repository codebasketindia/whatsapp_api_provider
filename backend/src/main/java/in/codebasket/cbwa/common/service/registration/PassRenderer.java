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

    public PassRenderer() {
        Font f;
        try (InputStream in = new ClassPathResource("fonts/DejaVuSans-Bold.ttf").getInputStream()) {
            f = Font.createFont(Font.TRUETYPE_FONT, in);
        } catch (Exception e) {
            log.warn("Bundled pass font not loaded, using SansSerif: {}", e.getMessage());
            f = new Font(Font.SANS_SERIF, Font.BOLD, 12);
        }
        this.baseFont = f;
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

    public record PassData(String name, String mobile, String passNo, byte[] photo) {
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
            if (data.photo() != null && layout.photo() != null) {
                drawPhoto(g, data.photo(), layout.photo(), w, h);
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
