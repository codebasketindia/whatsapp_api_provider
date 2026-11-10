package in.codebasket.cbwa.registration;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import in.codebasket.cbwa.common.service.registration.PassRenderer;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** The green/gold Ziyarat card renders at 1800x3200 and its QR (with the logo on top) still scans. */
class ZiyaratCardTest {

    @Test
    void rendersAndQrScans() throws Exception {
        String link = "https://sms.codebasket.in/v/123.AbCdEfGhIjKlMnOpQrStUv";
        PassRenderer renderer = new PassRenderer();
        for (String title : new String[] {"Makkah–Madinah Khaas Ziyarat Pass", "Taif–Jeddah–Badr Ziyarat Pass", "A very long package name that needs three lines to fit"}) {
            byte[] jpg = renderer.renderZiyaratCard(new PassRenderer.TicketData("HFHU", "Ziyarat Passes for HAJJ 2027", title,
                    "Imran Shaikh", "+91 98059 92702", "HZ27-000004", "HZ27-000004-SMM", link, "Emergency Support : +966590550490"));
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(jpg));
            assertThat(img.getWidth()).isEqualTo(1800);
            assertThat(img.getHeight()).isEqualTo(3200);
            BufferedImage qr = img.getSubimage(1100, 1660, 600, 600);
            String read = new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(qr)))).getText();
            assertThat(read).isEqualTo(link);
            Files.createDirectories(Path.of("target/ziyarat"));
            Files.write(Path.of("target/ziyarat/" + title.length() + ".jpg"), jpg);
        }
    }
}
